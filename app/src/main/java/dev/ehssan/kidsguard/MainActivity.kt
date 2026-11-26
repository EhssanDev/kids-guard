package dev.ehssan.kidsguard

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class AppItem(val pkg: String, val label: String, val icon: Drawable?, val game: Boolean)

class MainActivity : Activity() {

    companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    private val bg = Color.rgb(18, 20, 26)
    private val card = Color.rgb(30, 34, 44)
    private val chipBg = Color.rgb(44, 49, 62)
    private val textMain = Color.rgb(237, 239, 244)
    private val textSub = Color.rgb(154, 163, 178)
    private val accent = Color.rgb(37, 130, 235)
    private val green = Color.rgb(52, 211, 153)
    private val red = Color.rgb(248, 113, 113)
    private val orange = Color.rgb(245, 166, 35)

    private lateinit var list: ListView
    private lateinit var statusBox: LinearLayout
    private lateinit var lock: LockView
    private lateinit var adapter: AppAdapter
    private val chipViews = ArrayList<TextView>()

    private val all = ArrayList<AppItem>()
    private var query = ""
    private var filter = 0
    private var locked = true
    private var skipLock = false
    private var sheet: Dialog? = null

    // ---------- helpers ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun rounded(color: Int, r: Int): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(color)
        d.cornerRadius = dp(r).toFloat()
        return d
    }

    private fun lin(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

    private fun label(text: String, sp: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = sp
        t.setTextColor(color)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        return t
    }

    private fun chip(text: String, color: Int = chipBg, onClick: () -> Unit): TextView {
        val t = label(text, 13f, Color.WHITE, true)
        t.gravity = Gravity.CENTER
        t.background = rounded(color, 12)
        t.setPadding(dp(14), dp(9), dp(14), dp(9))
        t.setOnClickListener { onClick() }
        return t
    }

    private fun button(text: String, color: Int = accent, onClick: () -> Unit): TextView {
        val t = chip(text, color, onClick)
        t.textSize = 14f
        return t
    }

    private fun cardBox(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = rounded(card, 18)
        c.setPadding(dp(16), dp(14), dp(16), dp(14))
        return c
    }

    // ---------- lifecycle ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        list = ListView(this)
        list.setBackgroundColor(bg)
        list.divider = null
        list.setPadding(dp(12), 0, dp(12), dp(20))
        list.clipToPadding = false

        statusBox = LinearLayout(this)
        statusBox.orientation = LinearLayout.VERTICAL
        list.addHeaderView(statusBox, null, false)
        list.addHeaderView(buildControls(), null, false)

        adapter = AppAdapter()
        list.adapter = adapter

        lock = LockView(this)
        val root = FrameLayout(this)
        root.addView(list, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(lock, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)

        refreshStatus()
        loadApps()
        showLock()
    }

    override fun onPause() {
        super.onPause()
        if (!skipLock) lockNow()
    }

    override fun onResume() {
        super.onResume()
        skipLock = false
        if (locked && !lock.isShowing) showLock()
        refreshStatus()
        adapter.notifyDataSetChanged()
    }

    private fun showLock() {
        locked = true
        if (!Pin.isSet(this)) {
            lock.showSetup(false, { locked = false }, null)
        } else {
            lock.showUnlock("رمز ولي الأمر", false, false, { locked = false }, null, false)
        }
    }

    private fun lockNow() {
        if (locked) return
        sheet?.dismiss()
        showLock()
    }

    private fun openSystem(i: Intent) {
        Rules.openSettingsFor(this, 5)
        skipLock = true
        try {
            startActivity(i)
        } catch (e: Exception) {
            skipLock = false
            toast("تعذر فتح الإعدادات")
        }
    }

    // ---------- status & protection ----------

    private fun serviceEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(this, GuardService::class.java)
        return s.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun adminActive(): Boolean {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(ComponentName(this, AdminReceiver::class.java))
    }

    private fun refreshStatus() {
        statusBox.removeAllViews()
        val svc = serviceEnabled()
        val adm = adminActive()

        val title = label("Kids Guard", 26f, Color.WHITE, true)
        title.setPadding(dp(4), dp(18), 0, dp(2))
        statusBox.addView(title)
        val sub = label("التطبيقات المحظورة تُغلق تلقائياً عند فتحها", 13f, textSub)
        sub.setPadding(dp(4), 0, 0, dp(14))
        statusBox.addView(sub)

        val c = cardBox()
        val head = label(if (svc) "الحماية تعمل" else "الحماية متوقفة", 17f, if (svc) green else red, true)
        c.addView(head)
        if (!svc) {
            c.addView(
                label("فعّل خدمة Kids Guard من إعدادات إمكانية الوصول ليبدأ الحظر.", 13f, textSub)
                    .also { it.setPadding(0, dp(4), 0, dp(10)) }
            )
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.addView(button("تفعيل الخدمة") {
                openSystem(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })
            row.addView(button("إعداد مقيَّد؟", chipBg) {
                openSystem(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                )
            }, lin(WRAP, WRAP).also { it.marginStart = dp(8) })
            c.addView(row)
            c.addView(
                label(
                    "إذا ظهر \"إعداد مقيَّد\": افتح معلومات التطبيق، ثم ⋮ في الأعلى، ثم \"السماح بالإعدادات المقيّدة\"، وارجع للتفعيل.",
                    12f, textSub
                ).also { it.setPadding(0, dp(10), 0, 0) }
            )
        }
        statusBox.addView(c, lin(MATCH, WRAP).also { it.bottomMargin = dp(10) })

        val c2 = cardBox()
        c2.addView(label(if (adm) "منع الحذف: مفعّل" else "منع الحذف: غير مفعّل", 16f, if (adm) green else orange, true))
        if (!adm) {
            c2.addView(
                label("يصعّب حذف التطبيق على الطفل حتى يُلغى هذا الخيار من الإعدادات.", 13f, textSub)
                    .also { it.setPadding(0, dp(4), 0, dp(10)) }
            )
            c2.addView(button("تفعيل منع الحذف") {
                val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(this, AdminReceiver::class.java))
                i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "لمنع حذف Kids Guard بسهولة")
                openSystem(i)
            })
        }
        statusBox.addView(c2, lin(MATCH, WRAP).also { it.bottomMargin = dp(10) })

        val net = Rules.netBlock(this)
        val running = net && NetBlockService.active
        val c5 = cardBox()
        val nr = LinearLayout(this)
        nr.orientation = LinearLayout.HORIZONTAL
        nr.gravity = Gravity.CENTER_VERTICAL
        val ncol = LinearLayout(this)
        ncol.orientation = LinearLayout.VERTICAL
        ncol.addView(label("قطع الإنترنت عن التطبيقات المحظورة", 16f, textMain, true))
        ncol.addView(
            label(
                if (running) "يعمل الآن" else if (net) "مفعّل، بانتظار التشغيل" else "متوقف",
                12f, if (running) green else textSub
            )
        )
        ncol.addView(label("يستخدم VPN محلياً على الجوال فقط، ولا يرسل أي بيانات لأي جهة.", 12f, textSub))
        nr.addView(ncol, lin(0, WRAP, 1f))
        val nsw = Switch(this)
        nsw.isChecked = net
        nsw.setOnCheckedChangeListener { _, v -> setNetBlock(v) }
        nr.addView(nsw)
        c5.addView(nr)
        statusBox.addView(c5, lin(MATCH, WRAP).also { it.bottomMargin = dp(10) })

        val c3 = cardBox()
        val sw = Switch(this)
        sw.isChecked = Rules.protect(this)
        sw.setOnCheckedChangeListener { _, v -> Rules.setProtect(this, v) }
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(label("حماية الإعدادات والتثبيت", 16f, textMain, true))
        col.addView(label("تمنع الطفل من فتح الإعدادات أو حذف التطبيق. يشمل ذلك الواي فاي أيضاً.", 12f, textSub))
        r.addView(col, lin(0, WRAP, 1f))
        r.addView(sw)
        c3.addView(r)
        c3.addView(button("فتح الإعدادات لمدة 5 دقائق", chipBg) {
            openSystem(Intent(Settings.ACTION_SETTINGS))
        }, lin(WRAP, WRAP).also { it.topMargin = dp(10) })
        statusBox.addView(c3, lin(MATCH, WRAP).also { it.bottomMargin = dp(10) })

        val c4 = cardBox()
        c4.addView(label("تغيير رمز ولي الأمر", 16f, textMain, true))
        c4.setOnClickListener { changePin() }
        statusBox.addView(c4, lin(MATCH, WRAP).also { it.bottomMargin = dp(6) })
    }

    private fun setNetBlock(v: Boolean) {
        if (!v) {
            Rules.setNet(this, false)
            stopService(Intent(this, NetBlockService::class.java))
            list.postDelayed({ refreshStatus() }, 300)
            return
        }
        val i = VpnService.prepare(this)
        if (i == null) {
            Rules.setNet(this, true)
            NetBlock.sync(this)
            refreshStatus()
            list.postDelayed({ refreshStatus() }, 1200)
        } else {
            skipLock = true
            @Suppress("DEPRECATION")
            startActivityForResult(i, 7)
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 7) {
            if (resultCode == RESULT_OK) {
                Rules.setNet(this, true)
                NetBlock.sync(this)
                toast("تم تفعيل قطع الإنترنت")
                list.postDelayed({ refreshStatus() }, 1200)
            } else {
                toast("لم يتم منح إذن VPN")
            }
            refreshStatus()
        }
    }

    private fun changePin() {
        lock.showUnlock("الرمز الحالي", false, true, {
            lock.showSetup(true, { toast("تم تغيير الرمز") }, null)
        }, null, false)
    }

    // ---------- app list controls ----------

    private fun buildControls(): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(0, dp(10), 0, dp(6))

        val head = label("التطبيقات", 18f, Color.WHITE, true)
        head.setPadding(dp(4), 0, 0, dp(10))
        box.addView(head)

        val search = EditText(this)
        search.setSingleLine(true)
        search.hint = "ابحث عن تطبيق"
        search.textSize = 15f
        search.setTextColor(textMain)
        search.setHintTextColor(textSub)
        search.background = rounded(card, 16)
        search.setPadding(dp(16), 0, dp(16), 0)
        search.inputType = InputType.TYPE_CLASS_TEXT
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim()?.lowercase() ?: ""
                adapter.applyFilter()
            }
        })
        box.addView(search, lin(MATCH, dp(46)))

        val chips = LinearLayout(this)
        chips.orientation = LinearLayout.HORIZONTAL
        chips.setPadding(0, dp(10), 0, dp(6))
        val names = listOf("الكل", "المقيّدة", "الألعاب")
        chipViews.clear()
        for ((i, n) in names.withIndex()) {
            val c = chip(n) {
                filter = i
                updateChips()
                adapter.applyFilter()
            }
            chipViews.add(c)
            chips.addView(c, lin(WRAP, WRAP).also { it.marginEnd = dp(8) })
        }
        box.addView(chips)
        updateChips()

        box.addView(button("حظر كل الألعاب", Color.rgb(190, 60, 60)) { blockAllGames() }, lin(WRAP, WRAP))
        return box
    }

    private fun updateChips() {
        for ((i, c) in chipViews.withIndex()) {
            c.background = rounded(if (i == filter) accent else chipBg, 12)
        }
    }

    private fun blockAllGames() {
        var n = 0
        for (a in all) {
            if (a.game && Rules.get(this, a.pkg).mode != 1) {
                val r = Rules.get(this, a.pkg)
                Rules.set(this, a.pkg, Rule(1, r.from, r.to, r.allowUntil))
                n++
            }
        }
        toast(if (n > 0) "تم حظر $n لعبة" else "لا توجد ألعاب جديدة للحظر")
        adapter.applyFilter()
    }

    @Suppress("DEPRECATION")
    private fun loadApps() {
        Thread {
            val pm = packageManager
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val seen = HashSet<String>()
            val out = ArrayList<AppItem>()
            for (ri in pm.queryIntentActivities(i, 0)) {
                val p = ri.activityInfo.packageName
                if (p == packageName || !seen.add(p)) continue
                val ai = ri.activityInfo.applicationInfo
                val game = ai.category == ApplicationInfo.CATEGORY_GAME ||
                    (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                out.add(AppItem(p, ri.loadLabel(pm).toString(), ri.loadIcon(pm), game))
            }
            out.sortBy { it.label.lowercase() }
            runOnUiThread {
                all.clear()
                all.addAll(out)
                adapter.applyFilter()
            }
        }.start()
    }

    // ---------- rule description ----------

    private fun stateText(pkg: String): Pair<String, Int> {
        val r = Rules.get(this, pkg)
        val now = System.currentTimeMillis()
        val temp = r.allowUntil > now
        val base: Pair<String, Int> = when (r.mode) {
            1 -> "محظور دائماً" to red
            2 -> "مسموح من ${Rules.time(r.from)} إلى ${Rules.time(r.to)}" to orange
            else -> "مسموح" to textSub
        }
        if (temp) {
            val end = java.util.Calendar.getInstance()
            end.timeInMillis = r.allowUntil
            val t = Rules.time(end.get(java.util.Calendar.HOUR_OF_DAY) * 60 + end.get(java.util.Calendar.MINUTE))
            return (base.first + " · سماح مؤقت حتى " + t) to green
        }
        return base
    }

    // ---------- adapter ----------

    inner class AppAdapter : BaseAdapter() {
        private var shown: List<AppItem> = emptyList()

        fun applyFilter() {
            val now = System.currentTimeMillis()
            shown = all.filter { a ->
                val matches = query.isEmpty() || a.label.lowercase().contains(query)
                val ok = when (filter) {
                    1 -> Rules.get(this@MainActivity, a.pkg).let { it.mode != 0 || it.allowUntil > now }
                    2 -> a.game
                    else -> true
                }
                matches && ok
            }
            notifyDataSetChanged()
        }

        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val a = shown[position]
            val row = LinearLayout(this@MainActivity)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.background = rounded(card, 16)
            row.setPadding(dp(12), dp(10), dp(14), dp(10))
            val iv = ImageView(this@MainActivity)
            iv.setImageDrawable(a.icon)
            row.addView(iv, lin(dp(44), dp(44)))
            val col = LinearLayout(this@MainActivity)
            col.orientation = LinearLayout.VERTICAL
            col.setPadding(dp(12), 0, dp(8), 0)
            val name = label(a.label, 15f, Color.WHITE, true)
            name.maxLines = 1
            name.ellipsize = TextUtils.TruncateAt.END
            col.addView(name)
            val st = stateText(a.pkg)
            val sub = label(st.first, 12f, st.second)
            sub.maxLines = 1
            sub.ellipsize = TextUtils.TruncateAt.END
            col.addView(sub)
            row.addView(col, lin(0, WRAP, 1f))
            row.setOnClickListener { showRule(a) }
            val wrap = FrameLayout(this@MainActivity)
            wrap.setPadding(0, 0, 0, dp(8))
            wrap.addView(row, FrameLayout.LayoutParams(MATCH, WRAP))
            return wrap
        }
    }

    // ---------- rule sheet ----------

    private fun showRule(a: AppItem) {
        sheet?.dismiss()
        val d = Dialog(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
        val wrap = FrameLayout(this)
        val g = GradientDrawable()
        g.setColor(Color.rgb(28, 31, 40))
        val rr = dp(24).toFloat()
        g.cornerRadii = floatArrayOf(rr, rr, rr, rr, 0f, 0f, 0f, 0f)
        wrap.background = g
        wrap.setPadding(dp(18), dp(16), dp(18), dp(22))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val iv = ImageView(this)
        iv.setImageDrawable(a.icon)
        head.addView(iv, lin(dp(40), dp(40)))
        val nm = label(a.label, 18f, Color.WHITE, true)
        nm.setPadding(dp(12), 0, 0, 0)
        nm.maxLines = 1
        nm.ellipsize = TextUtils.TruncateAt.END
        head.addView(nm, lin(0, WRAP, 1f))
        col.addView(head)

        val cur = Rules.get(this, a.pkg)
        fun option(text: String, selected: Boolean, onClick: () -> Unit): View {
            val t = label(text, 15f, Color.WHITE, selected)
            t.setPadding(dp(14), dp(13), dp(14), dp(13))
            val bgd = GradientDrawable()
            bgd.setColor(if (selected) Color.argb(60, 37, 130, 235) else chipBg)
            bgd.cornerRadius = dp(14).toFloat()
            if (selected) bgd.setStroke(dp(2), accent)
            t.background = bgd
            t.setOnClickListener { onClick() }
            val lp = lin(MATCH, WRAP)
            lp.topMargin = dp(8)
            t.layoutParams = lp
            return t
        }

        val done = {
            d.dismiss()
            adapter.applyFilter()
        }

        col.addView(option("مسموح دائماً", cur.mode == 0) {
            Rules.set(this, a.pkg, Rule(0, cur.from, cur.to, cur.allowUntil))
            done()
        }, (col.childCount).let { lin(MATCH, WRAP).also { p -> p.topMargin = dp(14) } })
        col.addView(option("محظور دائماً", cur.mode == 1) {
            Rules.set(this, a.pkg, Rule(1, cur.from, cur.to, cur.allowUntil))
            done()
        })
        col.addView(
            option(
                if (cur.mode == 2) "مسموح من ${Rules.time(cur.from)} إلى ${Rules.time(cur.to)} (تعديل)" else "مسموح في وقت محدد فقط...",
                cur.mode == 2
            ) {
                d.dismiss()
                pickSchedule(a, cur)
            }
        )

        val tl = label("سماح مؤقت", 13f, textSub, true)
        tl.setPadding(dp(4), dp(18), 0, dp(6))
        col.addView(tl)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        for ((txt, mins) in listOf("15 د" to 15, "30 د" to 30, "ساعة" to 60, "ساعتان" to 120)) {
            row.addView(chip(txt) {
                Rules.set(this, a.pkg, Rule(cur.mode, cur.from, cur.to, System.currentTimeMillis() + mins * 60_000L))
                toast("مسموح لمدة $txt")
                done()
            }, lin(WRAP, WRAP).also { it.marginEnd = dp(8) })
        }
        col.addView(row)
        if (cur.allowUntil > System.currentTimeMillis()) {
            col.addView(chip("إلغاء السماح المؤقت", Color.rgb(190, 60, 60)) {
                Rules.set(this, a.pkg, Rule(cur.mode, cur.from, cur.to, 0L))
                done()
            }, lin(WRAP, WRAP).also { it.topMargin = dp(10) })
        }

        wrap.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
        d.setContentView(wrap)
        d.window?.let { w ->
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setLayout(MATCH, WRAP)
            w.setGravity(Gravity.BOTTOM)
            w.decorView.setPadding(0, 0, 0, 0)
        }
        d.setOnDismissListener { if (sheet === d) sheet = null }
        sheet = d
        d.show()
    }

    private fun pickSchedule(a: AppItem, cur: Rule) {
        val first = TimePickerDialog(this, { _, h, m ->
            val from = h * 60 + m
            val second = TimePickerDialog(this, { _, h2, m2 ->
                val to = h2 * 60 + m2
                if (to == from) {
                    toast("وقت البداية والنهاية متطابقان")
                } else {
                    Rules.set(this, a.pkg, Rule(2, from, to, cur.allowUntil))
                    adapter.applyFilter()
                }
            }, cur.to / 60, cur.to % 60, true)
            second.setTitle("مسموح حتى الساعة")
            second.show()
        }, cur.from / 60, cur.from % 60, true)
        first.setTitle("مسموح من الساعة")
        first.show()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (lock.isShowing) {
            moveTaskToBack(true)
            return
        }
        super.onBackPressed()
    }
}
