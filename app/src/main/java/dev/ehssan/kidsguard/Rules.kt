package dev.ehssan.kidsguard

import android.content.Context
import java.util.Calendar

/** mode: 0 allowed, 1 always blocked, 2 allowed only between [from] and [to] (minutes since midnight). */
class Rule(val mode: Int, val from: Int, val to: Int, val allowUntil: Long)

object Rules {
    private fun sp(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("kg_rules", Context.MODE_PRIVATE)

    private fun cfg(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("kg_cfg", Context.MODE_PRIVATE)

    private val DEFAULT = Rule(0, 16 * 60, 18 * 60, 0L)

    fun get(ctx: Context, pkg: String): Rule {
        val s = sp(ctx).getString(pkg, null) ?: return DEFAULT
        return try {
            val p = s.split('|')
            Rule(p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].toLong())
        } catch (e: Exception) {
            DEFAULT
        }
    }

    fun set(ctx: Context, pkg: String, r: Rule) {
        val e = sp(ctx).edit()
        if (r.mode == 0 && r.allowUntil <= System.currentTimeMillis()) {
            e.remove(pkg)
        } else {
            e.putString(pkg, "${r.mode}|${r.from}|${r.to}|${r.allowUntil}")
        }
        e.apply()
    }

    fun inWindow(minute: Int, from: Int, to: Int): Boolean {
        if (from == to) return true
        return if (from < to) minute in from until to else (minute >= from || minute < to)
    }

    fun blockedNow(ctx: Context, pkg: String): Boolean {
        if (sp(ctx).getString(pkg, null) == null) return false
        val r = get(ctx, pkg)
        if (r.allowUntil > System.currentTimeMillis()) return false
        return when (r.mode) {
            1 -> true
            2 -> {
                val c = Calendar.getInstance()
                val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
                !inWindow(m, r.from, r.to)
            }
            else -> false
        }
    }

    fun time(min: Int) = String.format("%02d:%02d", min / 60, min % 60)

    // ----- settings protection -----

    fun protect(ctx: Context) = cfg(ctx).getBoolean("protect", true)
    fun setProtect(ctx: Context, v: Boolean) = cfg(ctx).edit().putBoolean("protect", v).apply()
    fun settingsOpenUntil(ctx: Context) = cfg(ctx).getLong("settings_until", 0L)
    fun openSettingsFor(ctx: Context, minutes: Int) =
        cfg(ctx).edit().putLong("settings_until", System.currentTimeMillis() + minutes * 60_000L).apply()
}
