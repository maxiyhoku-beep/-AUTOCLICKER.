package com.ras.autoclick

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class ClickService : AccessibilityService() {
    companion object { var inst: ClickService? = null }
    private lateinit var wm: WindowManager
    private val h = Handler(Looper.getMainLooper())
    private val rec by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private var panel: LinearLayout? = null
    private val markers = mutableMapOf<String, View>()
    private val extras = mutableListOf<View>()
    private lateinit var stats: TextView
    private lateinit var bPlay: Button; private lateinit var bHide: Button
    private lateinit var bAll: Button; private lateinit var bDup: Button
    private var running = false; private var hidden = false; private var gen = 0
    private var packs = 0; private var acc = 0L; private var since = 0L; private var wait = 700L
    private val found = mutableSetOf<String>()
    private val prefs get() = getSharedPreferences("ras", Context.MODE_PRIVATE)
    private var mode: Int
        get() = prefs.getInt("mode", 0)
        set(v) { prefs.edit().putInt("mode", v).apply() }

    override fun onServiceConnected() { inst = this; wm = getSystemService(WINDOW_SERVICE) as WindowManager }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { inst = null; super.onDestroy() }

    private fun lp(w: Int = WindowManager.LayoutParams.WRAP_CONTENT, hh: Int = w) = WindowManager.LayoutParams(
        w, hh, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun gold(on: Boolean) = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
        if (on) intArrayOf(0xFFFFE066.toInt(), 0xFFFFD700.toInt()) else intArrayOf(0xFF5A4410.toInt(), 0xFF8B6914.toInt())
    ).apply { cornerRadius = 18f }

    private fun b(p: LinearLayout, txt: String = "", f: () -> Unit) = Button(this).apply {
        text = txt; textSize = 12f; setTextColor(Color.BLACK); background = gold(true)
        minHeight = 0; minimumHeight = 0; setPadding(20, 12, 20, 12); setOnClickListener { f() }
        p.addView(this, LinearLayout.LayoutParams(330, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 6, 0, 6) })
    }

    fun showPanel() {
        if (panel != null) return
        T.es = prefs.getString("lang", "es") == "es"
        packs = 0; acc = 0; found.clear(); hidden = false
        val p = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16)
            background = GradientDrawable().apply {
                setColor(0xEE000000.toInt()); setStroke(3, 0xFFFFD700.toInt()); cornerRadius = 24f }
        }
        val params = lp().apply { x = 20; y = 200 }
        val handle = TextView(this).apply {
            text = "≡ 𝑹𝑨𝑺//"; setTextColor(0xFFFFD700.toInt()); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; setPadding(0, 6, 0, 10)
        }
        var dx = 0f; var dy = 0f
        handle.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dx = params.x - e.rawX; dy = params.y - e.rawY }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (e.rawX + dx).toInt(); params.y = (e.rawY + dy).toInt(); wm.updateViewLayout(p, params) }
            }
            true
        }
        p.addView(handle)
        stats = TextView(this).apply { setTextColor(Color.WHITE); textSize = 12f }
        p.addView(stats)
        bPlay = b(p) { toggle() }
        bHide = b(p) { hidden = !hidden; refresh() }
        bAll = b(p) { mode = if (mode == 1) 0 else 1; refresh() }
        bDup = b(p) { mode = if (mode == 2) 0 else 2; refresh() }
        b(p, T.t("close")) { closeAll() }
        extras.clear(); extras += listOf(stats, bAll, bDup)
        wm.addView(p, params); panel = p
        refresh(); tick()
    }

    private fun refresh() {
        bPlay.text = T.t(if (running) "pause" else "play")
        bHide.text = T.t(if (hidden) "show" else "hide")
        bAll.text = T.t("sellall"); bDup.text = T.t("selldup")
        bAll.background = gold(mode == 1); bDup.background = gold(mode == 2)
        extras.forEach { it.visibility = if (hidden) View.GONE else View.VISIBLE }
        markers.values.forEach { it.visibility = if (hidden) View.GONE else View.VISIBLE }
    }

    private fun tick() {
        if (panel == null) return
        val t = (acc + if (running) SystemClock.elapsedRealtime() - since else 0) / 1000
        stats.text = "${T.t("packs")}: $packs\n${T.t("time")}: %02d:%02d:%02d".format(t / 3600, t / 60 % 60, t % 60)
        h.postDelayed({ tick() }, 1000)
    }

    private fun toggle() {
        running = !running
        if (running) { since = SystemClock.elapsedRealtime(); gen++; loop(gen) }
        else acc += SystemClock.elapsedRealtime() - since
        refresh()
    }

    private fun loop(g: Int) {
        if (!running || g != gen) return
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(r: ScreenshotResult) {
                val hw = Bitmap.wrapHardwareBuffer(r.hardwareBuffer, r.colorSpace)
                val bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                hw?.recycle(); r.hardwareBuffer.close()
                if (bmp == null) { next(g); return }
                rec.process(InputImage.fromBitmap(bmp, 0))
                    .addOnSuccessListener { wait = 700L; if (running && g == gen) analyze(it, bmp.height) }
                    .addOnCompleteListener { bmp.recycle(); next(g) }
            }
            override fun onFailure(code: Int) { next(g) }
        })
    }
    private fun next(g: Int) { h.postDelayed({ loop(g) }, wait) }

    private fun analyze(t: Text, H: Int) {
        val pr = Rect()
        panel?.let { val l = IntArray(2); it.getLocationOnScreen(l); pr.set(l[0], l[1], l[0] + it.width, l[1] + it.height) }
        val blocks = t.textBlocks.mapNotNull { bl ->
            bl.boundingBox?.takeIf { !Rect.intersects(it, pr) }?.let { bl.text to it } }

        // BONUS: toca el botón que dice OK
        blocks.firstOrNull { T.norm(it.first) == "OK" }?.let { tap("ok", it.second); return }

        // Botones de la franja inferior
        val band = blocks.filter {
            val c = it.second.exactCenterY() / H
            c > 0.67f && c < 0.80f && it.second.height() < H * 0.07f
        }.sortedBy { it.second.left }

        when (band.size) {
            1 -> { checkCards(blocks); if (running) tap("skip", band[0].second) }
            3 -> { checkCards(blocks)
                   if (running) { tap("sell", band[when (mode) { 1 -> 0; 2 -> 1; else -> 2 }].second); packs++ } }
            2 -> tap("next", band[1].second)
        }
    }

    private fun tap(key: String, r: Rect) {
        val x = r.exactCenterX(); val y = r.exactCenterY()
        dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(Path().apply { moveTo(x, y) }, 0, 60)).build(), null, null)
        marker(key, x, y); wait = 1200L
    }

    private fun marker(key: String, x: Float, y: Float) {
        val size = 60
        val v = markers.getOrPut(key) {
            View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setStroke(5, 0xFFFFD700.toInt()); setColor(0x33FFD700) }
                wm.addView(this, lp(size, size).apply { flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE })
            }
        }
        val p = v.layoutParams as WindowManager.LayoutParams
        p.x = (x - size / 2).toInt(); p.y = (y - size / 2).toInt(); wm.updateViewLayout(v, p)
        v.visibility = if (hidden) View.GONE else View.VISIBLE
    }

    private fun checkCards(all: List<Pair<String, Rect>>) {
        val targets = prefs.getStringSet("targets", emptySet())!!
        if (targets.isEmpty()) return
        val seen = all.flatMap { it.first.lines() }.map { T.norm(it) }
        for (tg in targets) if (tg !in found && seen.any { it == tg || it.contains(tg) }) {
            found += tg; notifyFound(tg)
        }
        if (found.containsAll(targets)) closeAll()
    }

    private fun notifyFound(name: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("ras", "RAS", NotificationManager.IMPORTANCE_HIGH))
        nm.notify(name.hashCode(), Notification.Builder(this, "ras")
            .setSmallIcon(android.R.drawable.star_big_on)
            .setContentTitle(T.t("found")).setContentText(name).setAutoCancel(true).build())
    }

    fun closeAll() {
        running = false; gen++
        panel?.let { wm.removeView(it) }; panel = null
        markers.values.forEach { wm.removeView(it) }; markers.clear()
        MainActivity.inst?.finishAndRemoveTask()
    }
}
