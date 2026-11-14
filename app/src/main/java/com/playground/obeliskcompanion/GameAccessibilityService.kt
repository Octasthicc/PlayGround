package com.playground.obeliskcompanion

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.hardware.HardwareBuffer
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executor

class GameAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val recognizerDelegate = lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val recognizer by recognizerDelegate
    private val executor = Executor { command -> handler.post(command) }
    private var scanInProgress = false
    private var lastScreenshotAt = 0L
    private var lastFreebieActionAt = 0L
    private var freebiePhase = FreebiePhase.IDLE
    private var prestigePhase = PrestigePhase.IDLE
    private var lastPrestigePromptAt = 0L

    private enum class FreebiePhase { IDLE, OPENING, CLAIMED }
    private enum class PrestigePhase { IDLE, MENU_OPENING, CONFIRMATION_OPENING }

    private val scanLoop = object : Runnable {
        override fun run() {
            if (!isGameForeground()) {
                handler.postDelayed(this, SCAN_INTERVAL_MS)
                return
            }
            requestScreenshot()
            handler.postDelayed(this, SCAN_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        CompanionState.init(this)
        CompanionState.status("Accessibility service connected. Waiting for Idle Obelisk Miner.")
        handler.removeCallbacks(scanLoop)
        handler.post(scanLoop)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() == CompanionState.GAME_PACKAGE && !scanInProgress) {
            val now = System.currentTimeMillis()
            if (now - lastScreenshotAt > EVENT_SCREENSHOT_THROTTLE_MS) requestScreenshot()
        }
    }

    override fun onInterrupt() {
        scanInProgress = false
        CompanionState.status("Accessibility service interrupted.")
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (recognizerDelegate.isInitialized()) recognizer.close()
        super.onDestroy()
    }

    private fun isGameForeground(): Boolean {
        val root = rootInActiveWindow ?: return false
        return root.packageName?.toString() == CompanionState.GAME_PACKAGE
    }

    private fun requestScreenshot() {
        if (!isGameForeground() || scanInProgress) return
        scanInProgress = true
        lastScreenshotAt = System.currentTimeMillis()
        takeScreenshot(Display.DEFAULT_DISPLAY, executor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                var bitmap: Bitmap? = null
                try {
                    val buffer: HardwareBuffer = screenshot.hardwareBuffer
                    try {
                        val hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                        bitmap = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        hardwareBitmap?.recycle()
                    } finally {
                        buffer.close()
                    }
                    val frame = bitmap
                    if (frame == null || !isGameForeground()) {
                        frame?.recycle()
                        scanInProgress = false
                        return
                    }
                    saveScreenshot(frame)
                    val image = InputImage.fromBitmap(frame, 0)
                    recognizer.process(image)
                        .addOnSuccessListener(executor) { result ->
                            val fullText = result.text.take(MAX_OCR_CHARS)
                            CompanionState.setText("last_ocr", fullText)
                            processScreenText(fullText, frame.width, frame.height)
                        }
                        .addOnFailureListener(executor) {
                            CompanionState.status("Could not read the game screen. Check the game language/layout and calibration.")
                        }
                        .addOnCompleteListener(executor) {
                            frame.recycle()
                            scanInProgress = false
                        }
                } catch (_: Exception) {
                    bitmap?.recycle()
                    scanInProgress = false
                    CompanionState.status("Screen capture failed. Verify Android accessibility screenshot permission.")
                }
            }

            override fun onFailure(errorCode: Int) {
                scanInProgress = false
                CompanionState.status("Screenshot unavailable (code $errorCode). Keep the game in the foreground.")
            }
        })
    }

    private fun saveScreenshot(bitmap: Bitmap) {
        try {
            FileOutputStream(File(filesDir, "last_game_screen.png")).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 85, out)
            }
        } catch (_: Exception) {
            CompanionState.status("Could not save the private screenshot preview.")
        }
    }

    private fun processScreenText(text: String, width: Int, height: Int) {
        if (!isGameForeground()) return
        val level = extractLevel(text)
        if (level != null) CompanionState.observeLevel(level)

        if (CompanionState.observedLevel() >= 20) {
            CompanionState.status(CompanionState.prestigeAdvice().message)
        } else if (text.isNotBlank()) {
            CompanionState.status("Game screen recognized. ${text.lineSequence().take(3).joinToString(" · ").take(150)}")
        } else {
            CompanionState.status("Game foreground detected; no readable text found yet.")
        }

        processFreebies(text, width, height)
        processPrestige(text, width, height)
    }

    private fun extractLevel(text: String): Int? {
        val patterns = listOf(
            Regex("(?i)\\blevel\\s*[:#-]?\\s*(\\d{1,3})\\b"),
            Regex("(?i)\\blv\\.?\\s*(\\d{1,3})\\b")
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..999 }
        }
    }

    private fun processFreebies(text: String, width: Int, height: Int) {
        if (!CompanionState.bool("freebies_enabled")) {
            freebiePhase = FreebiePhase.IDLE
            return
        }
        val now = System.currentTimeMillis()
        val lower = text.lowercase()
        val packContext = "freebie" in lower || "freebies" in lower || "freebie pack" in lower
        val claimContext = listOf("claim", "collect", "open pack", "free pack").any { it in lower }
        when (freebiePhase) {
            FreebiePhase.IDLE -> {
                val interval = CompanionState.text("freebie_interval_minutes", "10").toLongOrNull()?.coerceIn(1, 1440) ?: 10L
                val due = now - lastFreebieActionAt >= interval * 60_000L
                val menu = CompanionState.point("freebie_menu")
                if (due && menu.isSet && isGameForeground()) {
                    freebiePhase = FreebiePhase.OPENING
                    lastFreebieActionAt = now
                    CompanionState.status("Freebie check due; opening the calibrated Freebie Packs area.")
                    tap(menu, width, height)
                }
            }
            FreebiePhase.OPENING -> {
                if (packContext && claimContext && now - lastFreebieActionAt >= ACTION_SETTLE_MS) {
                    val claim = CompanionState.point("freebie_claim")
                    if (claim.isSet && isGameForeground()) {
                        freebiePhase = FreebiePhase.CLAIMED
                        lastFreebieActionAt = now
                        CompanionState.status("Freebie pack detected; tapping the calibrated claim control.")
                        tap(claim, width, height)
                    }
                }
                if (freebiePhase == FreebiePhase.OPENING && now - lastFreebieActionAt > FREEBIE_FLOW_TIMEOUT_MS) {
                    freebiePhase = FreebiePhase.IDLE
                    lastFreebieActionAt = now
                    CompanionState.status("Freebie check timed out without a clear claim label or calibrated claim point; no claim was tapped.")
                }
            }
            FreebiePhase.CLAIMED -> {
                val success = listOf("claimed", "collected", "already claimed").any { it in lower }
                if (success || now - lastFreebieActionAt > ACTION_SETTLE_MS * 3) {
                    val close = CompanionState.point("freebie_close")
                    if (success && close.isSet && isGameForeground()) tap(close, width, height)
                    freebiePhase = FreebiePhase.IDLE
                    CompanionState.status(if (success) "Freebie claim acknowledged by screen text." else "Freebie claim attempted; waiting until the next scan interval.")
                }
            }
        }
    }

    private fun processPrestige(text: String, width: Int, height: Int) {
        val enabled = CompanionState.bool("prestige_enabled") && !CompanionState.bool("manual_mode", true)
        if (!enabled) {
            prestigePhase = PrestigePhase.IDLE
            return
        }
        val now = System.currentTimeMillis()
        val advice = CompanionState.prestigeAdvice(now)
        val lower = text.lowercase()
        when (prestigePhase) {
            PrestigePhase.IDLE -> {
                val menu = CompanionState.point("prestige_menu")
                if (advice.shouldPrestige && menu.isSet && isGameForeground() && now - lastPrestigePromptAt > PRESTIGE_RETRY_MS) {
                    prestigePhase = PrestigePhase.MENU_OPENING
                    lastPrestigePromptAt = now
                    CompanionState.status("Efficiency threshold reached; opening the calibrated prestige menu for confirmation checks.")
                    tap(menu, width, height)
                }
            }
            PrestigePhase.MENU_OPENING -> {
                val menuRecognized = now - lastPrestigePromptAt >= ACTION_SETTLE_MS && "prestige" in lower &&
                    listOf("confirm", "reset", "gain").any { it in lower }
                if (menuRecognized) {
                    val continuePoint = CompanionState.point("prestige_continue")
                    if (continuePoint.isSet && isGameForeground()) {
                        prestigePhase = PrestigePhase.CONFIRMATION_OPENING
                        lastPrestigePromptAt = now
                        CompanionState.status("Prestige screen recognized; checking for an explicit irreversible confirmation prompt.")
                        tap(continuePoint, width, height)
                    }
                }
                if (prestigePhase == PrestigePhase.MENU_OPENING && now - lastPrestigePromptAt > PRESTIGE_FLOW_TIMEOUT_MS) {
                    prestigePhase = PrestigePhase.IDLE
                    CompanionState.status("Prestige screen or calibrated continue point was not confidently recognized; no confirmation was tapped.")
                }
            }
            PrestigePhase.CONFIRMATION_OPENING -> {
                val explicitConfirmation = "prestige" in lower &&
                    ("are you sure" in lower || "confirm prestige" in lower || "reset your" in lower)
                if (explicitConfirmation && now - lastPrestigePromptAt >= ACTION_SETTLE_MS) {
                    // A separately calibrated final point is required before any irreversible action.
                    val finalConfirm = CompanionState.point("prestige_final_confirm")
                    if (finalConfirm.isSet && isGameForeground()) {
                        prestigePhase = PrestigePhase.IDLE
                        lastPrestigePromptAt = now
                        CompanionState.status("Explicit prestige confirmation recognized; completing the separately calibrated final tap.")
                        tap(finalConfirm, width, height)
                    }
                }
                if (prestigePhase == PrestigePhase.CONFIRMATION_OPENING && now - lastPrestigePromptAt > PRESTIGE_FLOW_TIMEOUT_MS) {
                    prestigePhase = PrestigePhase.IDLE
                    CompanionState.status("No explicit final prestige confirmation or calibrated final point found; progress was not reset.")
                }
            }
        }
    }

    private fun tap(point: TapPoint, screenWidth: Int, screenHeight: Int) {
        if (!isGameForeground()) return
        val x = (point.x * screenWidth).coerceIn(1f, (screenWidth - 1).coerceAtLeast(1).toFloat())
        val y = (point.y * screenHeight).coerceIn(1f, (screenHeight - 1).coerceAtLeast(1).toFloat())
        val path = android.graphics.Path().apply { moveTo(x, y) }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                super.onCompleted(gestureDescription)
            }

            override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                super.onCancelled(gestureDescription)
                CompanionState.status("Configured tap was cancelled by Android.")
            }
        }, handler)
    }

    companion object {
        private const val SCAN_INTERVAL_MS = 6_000L
        private const val EVENT_SCREENSHOT_THROTTLE_MS = 3_000L
        private const val ACTION_SETTLE_MS = 6_000L
        private const val FREEBIE_FLOW_TIMEOUT_MS = 45_000L
        private const val PRESTIGE_FLOW_TIMEOUT_MS = 30_000L
        private const val PRESTIGE_RETRY_MS = 30 * 60_000L
        private const val MAX_OCR_CHARS = 4_000
    }
}
