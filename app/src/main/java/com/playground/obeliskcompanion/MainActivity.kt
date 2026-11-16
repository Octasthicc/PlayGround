package com.playground.obeliskcompanion

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
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
import androidx.appcompat.widget.SwitchCompat
import android.widget.TextView
import java.io.File
import kotlin.math.min

class MainActivity : AppCompatActivity() {
    private lateinit var image: ImageView
    private lateinit var imageHint: TextView
    private lateinit var statusView: TextView
    private lateinit var screenView: TextView
    private lateinit var ocrView: TextView
    private lateinit var freebieSwitch: SwitchCompat
    private lateinit var prestigeSwitch: SwitchCompat
    private lateinit var manualSwitch: SwitchCompat
    private lateinit var cooldownInput: EditText
    private lateinit var multiplierInput: EditText
    private lateinit var minLevelInput: EditText
    private lateinit var minMinutesInput: EditText
    private lateinit var selectedPointView: TextView
    private var selectedPoint = "freebie_menu"
    private var selectedPointLabel = ""

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

        root.addView(text(getString(R.string.screen_title), 24f, true))
        root.addView(text(getString(R.string.screen_subtitle), 15f))
        root.addView(text(getString(R.string.unofficial_notice), 12f))

        root.addView(section(getString(R.string.section_connection)))
        root.addView(text(getString(R.string.connection_privacy), 14f))
        root.addView(button(getString(R.string.open_accessibility_settings)) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(button(getString(R.string.open_game)) {
            val launch = packageManager.getLaunchIntentForPackage(CompanionState.GAME_PACKAGE)
            if (launch != null) startActivity(launch)
            else statusView.text = getString(R.string.game_not_installed)
        })

        root.addView(section(getString(R.string.section_automation)))
        freebieSwitch = SwitchCompat(this).apply { text = getString(R.string.switch_freebies) }
        prestigeSwitch = SwitchCompat(this).apply { text = getString(R.string.switch_prestige) }
        manualSwitch = SwitchCompat(this).apply { text = getString(R.string.switch_manual) }
        root.addView(freebieSwitch)
        root.addView(prestigeSwitch)
        root.addView(manualSwitch)
        freebieSwitch.setOnCheckedChangeListener { _, checked -> CompanionState.setBool("freebies_enabled", checked) }
        prestigeSwitch.setOnCheckedChangeListener { _, checked -> CompanionState.setBool("prestige_enabled", checked) }
        manualSwitch.setOnCheckedChangeListener { _, checked -> CompanionState.setBool("manual_mode", checked) }
        root.addView(text(getString(R.string.prestige_safety_note), 13f))

        root.addView(section(getString(R.string.section_prestige_strategy)))
        root.addView(text(getString(R.string.prestige_strategy_info), 14f))
        multiplierInput = input(getString(R.string.input_pp_multiplier))
        minLevelInput = input(getString(R.string.input_minimum_level))
        minMinutesInput = input(getString(R.string.input_minimum_minutes))
        root.addView(multiplierInput)
        root.addView(minLevelInput)
        root.addView(minMinutesInput)
        root.addView(button(getString(R.string.save_strategy_settings)) { saveSettings() })

        root.addView(section(getString(R.string.section_calibration)))
        root.addView(text(getString(R.string.calibration_instructions), 14f))
        val pointNames = listOf(
            "freebie_menu" to R.string.point_label_freebie_menu,
            "freebie_claim" to R.string.point_label_freebie_claim,
            "freebie_close" to R.string.point_label_freebie_close,
            "prestige_menu" to R.string.point_label_prestige_menu,
            "prestige_continue" to R.string.point_label_prestige_continue,
            "prestige_final_confirm" to R.string.point_label_prestige_final_confirm
        )
        pointNames.forEach { (key, labelResource) ->
            val label = getString(labelResource)
            root.addView(button(getString(R.string.set_point_button, label)) {
                selectedPoint = key
                selectedPointLabel = label
                selectedPointView.text = getString(R.string.selected_point_prompt, label)
            })
        }
        selectedPointLabel = getString(R.string.point_label_freebie_menu)
        selectedPointView = text(getString(R.string.selected_point_initial), 13f, true)
        root.addView(selectedPointView)
        imageHint = text(getString(R.string.screenshot_missing), 13f)
        root.addView(imageHint)
        image = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFF20252B.toInt())
            minimumHeight = dp(260)
            contentDescription = getString(R.string.screenshot_accessibility_description)
            setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    calibrateFromTap(event.x, event.y)
                    view.performClick()
                }
                true
            }
        }
        root.addView(image, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(520)))

        root.addView(section(getString(R.string.section_live_status)))
        statusView = text(getString(R.string.waiting_for_game), 15f, true)
        screenView = text(getString(R.string.screen_detection_placeholder), 13f)
        ocrView = text(getString(R.string.recognized_text_placeholder), 12f)
        root.addView(statusView)
        root.addView(screenView)
        root.addView(ocrView)
        root.addView(button(getString(R.string.refresh_status)) { refresh() })

        root.addView(section(getString(R.string.section_safety)))
        root.addView(text(getString(R.string.safety_limitations), 13f))
        setContentView(scroll)

        freebieSwitch.isChecked = CompanionState.bool("freebies_enabled")
        prestigeSwitch.isChecked = CompanionState.bool("prestige_enabled")
        manualSwitch.isChecked = CompanionState.bool("manual_mode", true)
        cooldownInput = input(getString(R.string.input_freebie_interval))
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
        selectedPointView.text = getString(
            R.string.calibration_saved_coordinates,
            selectedPointLabel,
            x * 100,
            y * 100
        )
        CompanionState.status(getString(R.string.calibration_saved_status, selectedPointLabel))
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
        statusView.text = getString(R.string.settings_saved, cooldown, multiplier, minimumLevel)
    }

    private fun refresh() {
        if (!::statusView.isInitialized) return
        statusView.text = CompanionState.status()
        val detectedScreen = CompanionState.text("detected_screen", "")
        val screenSize = CompanionState.text("screen_size", "")
        screenView.text = if (detectedScreen.isBlank()) getString(R.string.screen_detection_placeholder)
            else getString(R.string.screen_detection, detectedScreen, screenSize)
        ocrView.text = getString(
            R.string.ocr_text,
            CompanionState.text("last_ocr", getString(R.string.ocr_none_yet)).take(700)
        )
        val screenshot = File(filesDir, "last_game_screen.png")
        if (screenshot.exists()) {
            val bitmap = BitmapFactory.decodeFile(screenshot.absolutePath)
            image.setImageBitmap(bitmap)
            imageHint.text = getString(R.string.screenshot_available, screenshot.length() / 1024)
        } else {
            imageHint.text = getString(R.string.screenshot_missing)
        }
        val advice = CompanionState.prestigeAdvice()
        if (CompanionState.observedLevel() >= 20) {
            statusView.text = getString(R.string.status_with_advice, statusView.text, advice.message)
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
