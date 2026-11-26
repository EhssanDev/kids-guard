package dev.ehssan.kidsguard

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class GuardService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastPkg: String? = null
    private var lastKick = 0L
    private var ticks = 0

    private val ignore = setOf("android", "com.android.systemui")

    // Screens a child could use to switch the guard off or uninstall it.
    private val tamper = setOf(
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.packageinstaller"
    )

    private val tick = object : Runnable {
        override fun run() {
            lastPkg?.let { check(it) }
            ticks++
            if (ticks % 5 == 0) NetBlock.sync(this@GuardService)
            handler.postDelayed(this, 4000)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 4000)
        NetBlock.sync(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg in ignore) return
        lastPkg = pkg
        check(pkg)
    }

    private fun check(pkg: String) {
        val now = System.currentTimeMillis()
        var block = Rules.blockedNow(this, pkg)
        if (!block && pkg in tamper && Rules.protect(this) && now > Rules.settingsOpenUntil(this)) {
            block = true
        }
        if (block && now - lastKick > 700) {
            lastKick = now
            performGlobalAction(GLOBAL_ACTION_HOME)
            Toast.makeText(this, "هذا التطبيق غير مسموح الآن", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }
}
