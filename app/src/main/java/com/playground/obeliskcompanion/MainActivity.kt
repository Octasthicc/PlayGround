package com.playground.obeliskcompanion

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.io.File
import kotlin.math.min

class MainActivity : Activity() {
    private lateinit var image: ImageView
    private lateinit var imageHint: TextView
    private lateinit var statusView: TextView
    private lateinit var ocrView: TextView
    private lateinit var freebieSwitch: Switch
    private lateinit var prestigeSwitch: Switch
    private lateinit var manualSwitch: Switch
    private lateinit var cooldownInput: EditText
    private lateinit var multiplierInput: EditText
    private lateinit var minLevelInput: EditText
    private lateinit var minMinutesInput: EditText
    private lateinit var selectedPointView: TextView
    private var selectedPoint = "freebie_menu"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CompanionState.init(this)
        buildUi()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::statusView.isInitialized) refresh()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(22))
            setBackgroundColor(0xFFF4F6F8.toInt())
        }
        val scroll = ScrollView(this)
        scroll.addView(root)

        root.addView(text("OBELISK COMPANION", 24f, true))
        root.addView(text("On-device helper for Idle Obelisk Miner", 15f))
        root.addView(text("Unofficial community companion · not affiliated with Checkbox Entertainment.", 12f))

        root.addView(section("Connection"))
        root.addView(text("The accessibility service reads the visible game screen using on-device OCR. It only acts while Idle Obelisk Miner is foregrounded. No screenshots leave this device.", 14f))
        root.addView(button("Open accessibility settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(button("Open Idle Obelisk Miner") {
            val launch = packageManager.getLaunchIntentForPackage(CompanionState.GAME_PACKAGE)
            if (launch != null) startActivity(launch)
            else statusView.text = "Game not installed. Install the official game first."
        })

        root.addView(section("Automation"))
        freebieSwitch = Switch(this).apply { text = "Auto-collect Freebie Packs" }
        prestigeSwitch = Switch(this).apply { text = "Auto-prestige when measured efficiency declines" }
        manualSwitch = Switch(this).apply { text = "Manual mode (recommend only; never confirm prestige)" }
        root.addView(freebieSwitch)
        root.addView(prestigeSwitch)
        root.addView(manualSwitch)
        freebieSwitch.setOnCheckedChangeListener { _, checked -> CompanionState.setBool("freebies_enabled", checked) }
        prestigeSwitch.setOnCheckedChangeListener { _, checked -> CompanionState.setBool("prestige_enabled", checked) }
        manualSwitch.setOnCheckedChangeListener { _, checked -> CompanionState.setBool("manual_mode", checked) }
        root.addView(text("Auto-prestige is off by default. Manual mode is on by default. Prestige confirmation can reset game progress; auto mode requires a separately calibrated point for the prestige screen's continue action and the final irreversible confirmation. Check the preview before enabling it.", 13f))

        root.addView(section("Prestige strategy"))
        root.addView(text("The advisor uses the game's published prestige-points formula and your observed time per level. It compares current PP/hour with the next five-level estimate, accounting for slowing level-ups. It needs at least three measured level-ups after level 20; it does not read or assume hidden XP values.", 14f))
        multiplierInput = input("Prestige point multiplier (default 1.0)")
        minLevelInput = input("Minimum prestige level (at least 20)")
        minMinutesInput = input("Minimum minutes after reaching level 20 (default 5)")
        root.addView(multiplierInput)
        root.addView(minLevelInput)
        root.addView(minMinutesInput)
        root.addView(button("Save strategy settings") { saveSettings() })

        root.addView(section("Screen calibration"))
        root.addView(text("With the game open, the service keeps a private recent screenshot. Return here, choose a point below, then tap that location on the screenshot. Calibrate the relevant points for your device and game layout. Coordinates are normalized, so rotation/resizing may require recalibration.", 14f))
        val pointNames = listOf(
            "freebie_menu" to "Freebie icon/menu",
            "freebie_claim" to "Claim button",
            "freebie_close" to "Close Freebie window",
            "prestige_menu" to "Prestige menu/button",
            "prestige_continue" to "Prestige screen continue button",
            "prestige_final_confirm" to "Final irreversible prestige confirmation"
        )
        pointNames.forEach { (key, label) ->
            root.addView(button("Set point: $label") {
                selectedPoint = key
                selectedPointView.text = "Selected point: $label — tap it in the screenshot below"
            })
        }
        selectedPointView = text("Selected point: Freebie icon/menu — tap it in the screenshot below", 13f, true)
        root.addView(selectedPointView)
        imageHint = text("No game screenshot yet. Open the game and leave the companion service enabled for a few seconds.", 13f)
        root.addView(imageHint)
        image = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFF20252B.toInt())
            minimumHeight = dp(260)
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) calibrateFromTap(event.x, event.y)
                true
            }
        }
        root.addView(image, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(520)))

        root.addView(section("Live status"))
        statusView = text("Waiting for the game screen.", 15f, true)
        ocrView = text("Recognized text will appear here when the game is foregrounded.", 12f)
        root.addView(statusView)
        root.addView(ocrView)
        root.addView(button("Refresh status and screenshot") { refresh() })

        root.addView(section("Safety and limitations"))
        root.addView(text("This app uses Android Accessibility only after you explicitly enable its service. It does not access game files, send data, or bypass the game. ADB-free on-device automation requires Android 11 or newer. OCR and fixed screen coordinates can fail when the layout, language, or game version changes; automatic actions remain disabled until you turn them on.", 13f))
        setContentView(scroll)

        freebieSwitch.isChecked = CompanionState.bool("freebies_enabled")
        prestigeSwitch.isChecked = CompanionState.bool("prestige_enabled")
        manualSwitch.isChecked = CompanionState.bool("manual_mode", true)
        cooldownInput = input("Freebie scan interval in minutes (default 10)")
        // Insert cooldown before strategy controls using the current vertical layout.
        val parent = root
        val automationIndex = parent.indexOfChild(prestigeSwitch)
        parent.addView(cooldownInput, automationIndex + 1)
        cooldownInput.setText(CompanionState.text("freebie_interval_minutes", "10"))
        multiplierInput.setText(CompanionState.text("pp_multiplier", "1.0"))
        minLevelInput.setText(CompanionState.text("minimum_prestige_level", "20"))
        minMinutesInput.setText(CompanionState.text("minimum_run_minutes", "5"))
    }

    private fun calibrateFromTap(tapX: Float, tapY: Float) {
        val drawable: Drawable = image.drawable ?: return
        val sourceWidth = drawable.intrinsicWidth.toFloat()
        val sourceHeight = drawable.intrinsicHeight.toFloat()
        if (sourceWidth <= 0f || sourceHeight <= 0f) return
        val scale = min(image.width / sourceWidth, image.height / sourceHeight)
        val shownWidth = sourceWidth * scale
        val shownHeight = sourceHeight * scale
        val left = (image.width - shownWidth) / 2f
        val top = (image.height - shownHeight) / 2f
        if (tapX !in left..(left + shownWidth) || tapY !in top..(top + shownHeight)) return
        val x = ((tapX - left) / shownWidth).coerceIn(0f, 1f)
        val y = ((tapY - top) / shownHeight).coerceIn(0f, 1f)
        CompanionState.setPoint(selectedPoint, TapPoint(x, y))
        selectedPointView.text = "Saved $selectedPoint at ${"%.1f".format(x * 100)}%, ${"%.1f".format(y * 100)}%."
        CompanionState.status("Calibration saved: $selectedPoint.")
    }

    private fun saveSettings() {
        val cooldown = cooldownInput.text.toString().toIntOrNull()?.coerceIn(1, 1440) ?: 10
        val multiplier = multiplierInput.text.toString().toFloatOrNull()?.coerceIn(0.01f, 1000f) ?: 1f
        val minimumLevel = minLevelInput.text.toString().toIntOrNull()?.coerceIn(20, 999) ?: 20
        val minutes = minMinutesInput.text.toString().toIntOrNull()?.coerceIn(1, 10080) ?: 5
        CompanionState.setText("freebie_interval_minutes", cooldown.toString())
        CompanionState.setText("pp_multiplier", multiplier.toString())
        CompanionState.setText("minimum_prestige_level", minimumLevel.toString())
        CompanionState.setText("minimum_run_minutes", minutes.toString())
        statusView.text = "Saved. Freebie interval: $cooldown min · PP multiplier: $multiplier · minimum prestige level: $minimumLevel."
    }

    private fun refresh() {
        if (!::statusView.isInitialized) return
        statusView.text = CompanionState.status()
        ocrView.text = "Screen text: ${CompanionState.text("last_ocr", "(none yet)").take(700)}"
        val screenshot = File(filesDir, "last_game_screen.png")
        if (screenshot.exists()) {
            val bitmap = BitmapFactory.decodeFile(screenshot.absolutePath)
            image.setImageBitmap(bitmap)
            imageHint.text = "Latest private screenshot · ${screenshot.length() / 1024} KB. Tap to save the selected coordinate."
        } else {
            imageHint.text = "No game screenshot yet. Open the game and leave the companion service enabled for a few seconds."
        }
        val advice = CompanionState.prestigeAdvice()
        if (CompanionState.observedLevel() >= 20) {
            statusView.text = "${statusView.text}\n${advice.message}"
        }
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun section(label: String) = TextView(this).apply {
        text = label
        textSize = 19f
        setTextColor(0xFF17212B.toInt())
        setPadding(0, dp(18), 0, dp(8))
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun text(label: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = label
        textSize = size
        setTextColor(0xFF26323D.toInt())
        setPadding(0, dp(5), 0, dp(5))
        if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun input(hintText: String) = EditText(this).apply {
        hint = hintText
        textSize = 15f
        setSingleLine(true)
        inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

}
