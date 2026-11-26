package dev.ehssan.kidsguard

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Full-screen PIN pad: unlock, verify, or choose a new PIN. Fingerprint / face is offered on unlock. */
class LockView(private val act: Activity) : FrameLayout(act) {

    private var step = 0 // 0 verify, 1 choose new, 2 confirm new
    private var first = ""
    private val entry = StringBuilder()
    private var onOk: (() -> Unit)? = null
    private var onCancel: (() -> Unit)? = null
    private var allowBio = false
    private var cancellable = false
    private var bioActive = false

    private val titleView = TextView(act)
    private val dotsView = TextView(act)
    private val msgView = TextView(act)
    private val bioKey: TextView
    private val cancelView = TextView(act)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun circle(color: Int): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(color)
        return d
    }

    private fun key(label: String, onClick: () -> Unit): TextView {
        val t = TextView(act)
        t.text = label
        t.textSize = 26f
        t.gravity = Gravity.CENTER
        t.setTextColor(Color.WHITE)
        t.background = circle(Color.rgb(36, 41, 54))
        t.setOnClickListener { onClick() }
        return t
    }

    init {
        setBackgroundColor(Color.rgb(14, 17, 24))
        isClickable = true
        isFocusable = true
        visibility = GONE

        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        addView(col, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val glyph = TextView(act)
        glyph.text = "🔒"
        glyph.textSize = 40f
        glyph.gravity = Gravity.CENTER
        col.addView(glyph)

        titleView.textSize = 20f
        titleView.typeface = Typeface.DEFAULT_BOLD
        titleView.setTextColor(Color.WHITE)
        titleView.gravity = Gravity.CENTER
        titleView.setPadding(0, dp(10), 0, dp(6))
        col.addView(titleView)

        dotsView.textSize = 28f
        dotsView.setTextColor(Color.rgb(120, 170, 255))
        dotsView.gravity = Gravity.CENTER
        dotsView.setPadding(0, dp(6), 0, dp(6))
        col.addView(dotsView)

        msgView.textSize = 14f
        msgView.setTextColor(Color.rgb(255, 140, 140))
        msgView.gravity = Gravity.CENTER
        msgView.minHeight = dp(24)
        col.addView(msgView)

        val rows = arrayOf("123", "456", "789")
        for (r in rows) {
            val row = LinearLayout(act)
            row.orientation = LinearLayout.HORIZONTAL
            for (ch in r) {
                val k = key(ch.toString()) { addDigit(ch) }
                row.addView(k, rowParams())
            }
            col.addView(row)
        }

        val last = LinearLayout(act)
        last.orientation = LinearLayout.HORIZONTAL
        bioKey = key("👆") { promptBio() }
        last.addView(bioKey, rowParams())
        last.addView(key("0") { addDigit('0') }, rowParams())
        last.addView(key("⌫") { backspace() }, rowParams())
        col.addView(last)

        val submit = TextView(act)
        submit.text = "تأكيد"
        submit.textSize = 18f
        submit.typeface = Typeface.DEFAULT_BOLD
        submit.gravity = Gravity.CENTER
        submit.setTextColor(Color.WHITE)
        val sb = GradientDrawable()
        sb.setColor(Color.rgb(79, 124, 255))
        sb.cornerRadius = dp(14).toFloat()
        submit.background = sb
        submit.setOnClickListener { submit() }
        val sp = LinearLayout.LayoutParams(dp(3 * 80 + 24), dp(52))
        sp.topMargin = dp(14)
        col.addView(submit, sp)

        cancelView.text = "إلغاء"
        cancelView.textSize = 15f
        cancelView.gravity = Gravity.CENTER
        cancelView.setTextColor(Color.rgb(170, 178, 192))
        cancelView.setPadding(dp(20), dp(14), dp(20), dp(14))
        cancelView.setOnClickListener {
            val cb = onCancel
            close()
            cb?.invoke()
        }
        col.addView(cancelView)
    }

    private fun rowParams(): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(dp(72), dp(72))
        p.setMargins(dp(4), dp(4), dp(4), dp(4))
        return p
    }

    // ---------- entry points ----------

    fun showUnlock(
        title: String,
        withBio: Boolean,
        canCancel: Boolean,
        ok: () -> Unit,
        cancel: (() -> Unit)?,
        autoBio: Boolean
    ) {
        step = 0
        first = ""
        entry.setLength(0)
        titleView.text = title
        msgView.text = ""
        allowBio = withBio && Bio.available(act)
        cancellable = canCancel
        onOk = ok
        onCancel = cancel
        refresh()
        visibility = VISIBLE
        bringToFront()
        if (autoBio) postDelayed({ maybeAutoBio() }, 250)
    }

    fun showSetup(canCancel: Boolean, ok: () -> Unit, cancel: (() -> Unit)?) {
        step = 1
        first = ""
        entry.setLength(0)
        titleView.text = "اختر رمز قفل جديد"
        msgView.text = "٤ أرقام على الأقل"
        msgView.setTextColor(Color.rgb(170, 178, 192))
        allowBio = false
        cancellable = canCancel
        onOk = ok
        onCancel = cancel
        refresh()
        visibility = VISIBLE
        bringToFront()
    }

    val isShowing: Boolean get() = visibility == VISIBLE

    fun maybeAutoBio() {
        if (visibility == VISIBLE && step == 0 && allowBio && !bioActive) promptBio()
    }

    // ---------- internals ----------

    private fun close() {
        visibility = GONE
        entry.setLength(0)
        onOk = null
        onCancel = null
    }

    private fun refresh() {
        dotsView.text = if (entry.isEmpty()) "· · · ·" else entry.map { "●" }.joinToString(" ")
        bioKey.visibility = if (allowBio && step == 0) VISIBLE else INVISIBLE
        cancelView.visibility = if (cancellable) VISIBLE else INVISIBLE
    }

    private fun addDigit(c: Char) {
        if (entry.length >= 12) return
        entry.append(c)
        msgView.text = ""
        refresh()
    }

    private fun backspace() {
        if (entry.isNotEmpty()) entry.setLength(entry.length - 1)
        refresh()
    }

    private fun fail(message: String) {
        msgView.setTextColor(Color.rgb(255, 140, 140))
        msgView.text = message
        entry.setLength(0)
        refresh()
    }

    private fun succeed() {
        val cb = onOk
        close()
        cb?.invoke()
    }

    private fun submit() {
        val pin = entry.toString()
        when (step) {
            0 -> {
                val wait = Pin.lockoutSeconds(act)
                if (wait > 0) {
                    fail("حاول بعد $wait ثانية")
                    return
                }
                if (Pin.verify(act, pin)) succeed() else fail("الرمز غير صحيح")
            }

            1 -> {
                if (pin.length < 4) {
                    fail("٤ أرقام على الأقل")
                    return
                }
                first = pin
                entry.setLength(0)
                step = 2
                titleView.text = "أعد إدخال الرمز"
                msgView.text = ""
                refresh()
            }

            2 -> {
                if (pin == first) {
                    Pin.set(act, pin)
                    succeed()
                } else {
                    step = 1
                    first = ""
                    titleView.text = "اختر رمز قفل جديد"
                    fail("الرمزان غير متطابقين")
                }
            }
        }
    }

    private fun promptBio() {
        if (!allowBio || bioActive) return
        bioActive = true
        Bio.prompt(
            act, titleView.text.toString(),
            {
                bioActive = false
                if (visibility == VISIBLE && step == 0) succeed()
            },
            { bioActive = false }
        )
    }
}
