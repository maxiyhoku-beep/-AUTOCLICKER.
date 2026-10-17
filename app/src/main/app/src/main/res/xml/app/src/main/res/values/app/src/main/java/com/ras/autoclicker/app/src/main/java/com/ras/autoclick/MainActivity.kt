package com.ras.autoclick

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MainActivity : ComponentActivity() {
    companion object { var inst: MainActivity? = null }
    private val prefs by lazy { getSharedPreferences("ras", MODE_PRIVATE) }
    private val pick = registerForActivityResult(ActivityResultContracts.GetContent()) { it?.let(::readCard) }
    private val POS = setOf("GK","CB","LB","RB","CDM","CM","CAM","LM","RM","LW","RW","ST","CF","LWB","RWB")

    override fun onCreate(b: Bundle?) {
        super.onCreate(b); inst = this
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        if (prefs.contains("lang")) { T.es = prefs.getString("lang", "es") == "es"; menu() } else language()
    }
    override fun onDestroy() { if (inst === this) inst = null; super.onDestroy() }

    private fun screen(): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(60, (resources.displayMetrics.heightPixels * 0.30).toInt(), 60, 60)
        }
        val root = FrameLayout(this)
        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.fondo); scaleType = ImageView.ScaleType.CENTER_CROP
        }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(ScrollView(this).apply { addView(col) })
        setContentView(root); return col
    }
    private fun logo(c: LinearLayout) = c.addView(ImageView(this).apply { setImageResource(R.drawable.logo) },
        LinearLayout.LayoutParams(380, 380).apply { setMargins(0, 0, 0, 30) })
    private fun label(c: LinearLayout, s: String) = c.addView(TextView(this).apply {
        text = s; setTextColor(0xFFFFD700.toInt()); textSize = 18f; typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER; setPadding(0, 10, 0, 20)
    })
    private fun btn(c: LinearLayout, s: String, f: () -> Unit) = c.addView(Button(this).apply {
        text = s; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.BLACK); isAllCaps = false
        background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(0xFFB8860B.toInt(), 0xFFFFD700.toInt(), 0xFFB8860B.toInt())).apply { cornerRadius = 30f }
        setOnClickListener { f() }
    }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(0, 14, 0, 14) })

    private fun language() {
        val c = screen(); logo(c); label(c, "Elegí tu idioma / Choose your language")
        btn(c, "🇺🇸  ENGLISH") { setLang(false) }
        btn(c, "🇦🇷  ESPAÑOL") { setLang(true) }
    }
    private fun setLang(es: Boolean) {
        T.es = es; prefs.edit().putString("lang", if (es) "es" else "en").apply(); menu()
    }
    private fun menu() {
        val c = screen(); logo(c)
        btn(c, T.t("open")) { openPanel() }
        btn(c, T.t("channels")) { channels() }
        btn(c, T.t("search")) { search() }
        btn(c, "🇺🇸 / 🇦🇷") { language() }
    }
    private fun openPanel() {
        val s = ClickService.inst
        if (s == null) {
            Toast.makeText(this, T.t("needacc"), Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } else { s.showPanel(); moveTaskToBack(true) }
    }
    private fun channels() {
        val c = screen(); label(c, T.t("channels"))
        listOf(
            "FELCOR" to "[youtube.com](https://youtube.com/@felcor?si=h24n3RTS_8o2Ljlu)",
            "comer305" to "[youtube.com](https://youtube.com/@comer305?si=XJDEP0JpTCxKhfww)",
            "DYLANFUT" to "[youtube.com](https://youtube.com/@dylanfut2322?si=ujxpJ9Wj_I8IDr5c)",
            "FRYKKO" to "[youtube.com](https://youtube.com/@frykko57?si=QygSlJB8722jEyET)"
        ).forEach { (n, u) ->
            btn(c, if (T.es) "CANAL $n" else "$n CHANNEL") { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
        }
        btn(c, T.t("back")) { menu() }
    }
    private fun targets() = prefs.getStringSet("targets", emptySet())!!.toMutableSet()
    private fun search() {
        val c = screen(); label(c, T.t("search")); label(c, T.t("searchinfo"))
        val t = targets()
        t.forEach { n -> btn(c, "✖  $n") {
            prefs.edit().putStringSet("targets", targets().apply { remove(n) }).apply(); search() } }
        if (t.size < 3) btn(c, T.t("add")) { pick.launch("image/*") }
        btn(c, T.t("back")) { menu() }
    }
    private fun readCard(u: Uri) {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(InputImage.fromFilePath(this, u))
            .addOnSuccessListener { r ->
                val guess = r.textBlocks.flatMap { it.lines }
                    .filter { val n = T.norm(it.text); n.length in 3..25 && n !in POS && !n.contains("PAC") }
                    .maxByOrNull { it.boundingBox?.height() ?: 0 }?.text ?: ""
                askName(guess)
            }
            .addOnFailureListener { askName("") }
    }
    private fun askName(g: String) {
        val e = EditText(this).apply { setText(g) }
        AlertDialog.Builder(this).setTitle(T.t("confirm")).setView(e)
            .setPositiveButton("OK") { _, _ ->
                val n = T.norm(e.text.toString())
                if (n.length >= 3) prefs.edit().putStringSet("targets", targets().apply { add(n) }).apply()
                search()
            }.setNegativeButton("✖", null).show()
    }
}
