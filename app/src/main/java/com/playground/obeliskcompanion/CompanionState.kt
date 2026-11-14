package com.playground.obeliskcompanion

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.pow

internal data class TapPoint(val x: Float, val y: Float) {
    val isSet: Boolean get() = x in 0f..1f && y in 0f..1f && (x != 0f || y != 0f)
}

internal data class PrestigeAdvice(
    val shouldPrestige: Boolean,
    val message: String,
    val currentPoints: Double = 0.0,
    val currentPointsPerHour: Double = 0.0,
    val nextWindowPointsPerHour: Double = 0.0
)

internal object CompanionState {
    const val GAME_PACKAGE = "com.checkbox.minershminer"
    private const val PREFS = "companion_state"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    private fun p(): SharedPreferences = prefs
    fun bool(key: String, default: Boolean = false) = p().getBoolean(key, default)
    fun setBool(key: String, value: Boolean) { p().edit().putBoolean(key, value).apply() }
    fun text(key: String, default: String = "") = p().getString(key, default) ?: default
    fun setText(key: String, value: String) { p().edit().putString(key, value).apply() }
    fun number(key: String, default: Float = 0f) = p().getFloat(key, default)
    fun setNumber(key: String, value: Float) { p().edit().putFloat(key, value).apply() }

    fun point(name: String): TapPoint = TapPoint(number("$name.x"), number("$name.y"))
    fun setPoint(name: String, point: TapPoint) {
        p().edit().putFloat("$name.x", point.x).putFloat("$name.y", point.y).apply()
    }

    fun status(message: String) = setText("status", message)
    fun status(): String = text("status", "Waiting for the game screen.")

    /** Tracks observed level changes; it never invents XP values. */
    @Synchronized
    fun observeLevel(level: Int, now: Long = System.currentTimeMillis()) {
        if (level !in 1..999) return
        val oldLevel = p().getInt("observed_level", -1)
        val oldAt = p().getLong("observed_level_at", 0L)
        val editor = p().edit()
        if (oldLevel >= 20 && level <= 10) {
            editor.putString("level_seconds", "{}")
                .putLong("run_level20_at", 0L)
                .putLong("run_started_at", now)
        }
        if (level >= 20 && p().getLong("run_level20_at", 0L) == 0L) {
            editor.putLong("run_level20_at", now)
                .putLong("run_started_at", if (oldLevel in 1 until 20) oldAt else now)
        }
        if (oldLevel in 1..998 && level > oldLevel && oldAt > 0L) {
            val perLevelSeconds = ((now - oldAt).coerceAtLeast(1L) / (level - oldLevel)) / 1000.0
            val history = try { JSONObject(p().getString("level_seconds", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
            for (crossed in (oldLevel + 1)..level) history.put(crossed.toString(), perLevelSeconds)
            editor.putString("level_seconds", history.toString())
        }
        editor.putInt("observed_level", level).putLong("observed_level_at", now).apply()
    }

    fun observedLevel(): Int = p().getInt("observed_level", -1)

    /**
     * Estimates the stopping point from observed seconds per level. Prestige points follow the
     * published level formula; the PP multiplier is user-configurable. The estimate needs several
     * measured levels and intentionally refuses to auto-act on sparse data.
     */
    @Synchronized
    fun prestigeAdvice(now: Long = System.currentTimeMillis()): PrestigeAdvice {
        val level = observedLevel()
        if (level < 20) return PrestigeAdvice(false, "Prestige is locked until level 20 (detected: ${level.coerceAtLeast(0)}).")
        val start = p().getLong("run_started_at", 0L)
        val level20At = p().getLong("run_level20_at", 0L)
        if (start <= 0L || now <= start || level20At <= 0L) return PrestigeAdvice(false, "Collecting level-time samples before optimizing prestige.")
        val elapsedSeconds = (now - start) / 1000.0
        val timeAtPrestigeLevel = (now - level20At) / 1000.0
        val multiplier = p().getFloat("pp_multiplier", 1f).toDouble().coerceAtLeast(0.01)
        val currentPoints = points(level, multiplier)
        if (elapsedSeconds < 60.0) return PrestigeAdvice(false, "Run data is too new; keep measuring for at least one minute.", currentPoints)

        val history = try { JSONObject(p().getString("level_seconds", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        val recent = (max(20, level - 5)..level).mapNotNull { n ->
            if (history.has(n.toString())) history.optDouble(n.toString()).takeIf { it > 0.0 } else null
        }
        if (recent.size < 3) return PrestigeAdvice(false, "Need 3 measured level-ups after level 20; have ${recent.size}.", currentPoints)

        val ordered = recent.sorted()
        val median = ordered[ordered.size / 2]
        val firstHalf = recent.take(recent.size / 2).average()
        val secondHalf = recent.drop(recent.size / 2).average()
        val growth = (secondHalf / firstHalf).coerceIn(1.0, 2.5)
        var forecastSeconds = 0.0
        for (step in 1..5) forecastSeconds += median * growth.pow(step / 5.0)

        val currentRate = currentPoints * 3600.0 / elapsedSeconds
        // A prestige at the forecast level awards the full run total, over the full run duration.
        // Comparing only incremental points to future time would bias the advisor toward stopping.
        val nextPoints = points(level + 5, multiplier)
        val nextRate = if (forecastSeconds > 0) nextPoints * 3600.0 / (elapsedSeconds + forecastSeconds) else 0.0
        val shouldStop = level >= p().getInt("minimum_prestige_level", 20) &&
            timeAtPrestigeLevel >= p().getInt("minimum_run_minutes", 5) * 60.0 && nextRate < currentRate
        val detail = "Lv $level · ~${format(currentPoints)} PP · ${format(currentRate)}/h now; next 5 levels ~${format(nextRate)}/h."
        return PrestigeAdvice(shouldStop, if (shouldStop) "Best measured PP/time is behind you. $detail" else "Still improving the estimated PP/time rate. $detail", currentPoints, currentRate, nextRate)
    }

    fun points(level: Int, multiplier: Double): Double {
        val safeLevel = level.coerceAtLeast(0)
        val base = 12.0 * 1.084.pow((safeLevel - 10).toDouble())
        val post200 = if (safeLevel > 200) 1.0 + 0.05 * (safeLevel - 200) else 1.0
        return base * post200 * multiplier
    }

    private fun format(value: Double): String = when {
        !value.isFinite() -> "—"
        value >= 1_000_000 -> "%.1fM".format(value / 1_000_000)
        value >= 1_000 -> "%.1fk".format(value / 1_000)
        else -> "%.0f".format(value)
    }
}
