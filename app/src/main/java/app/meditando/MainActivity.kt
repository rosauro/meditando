package app.meditando

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

private const val NOTIF_PERM = "android.permission.POST_NOTIFICATIONS"
private const val REQ_NOTIF = 1

class MainActivity : Activity() {

    private val BG = Ui.BG
    private val TEXT = Ui.TEXT
    private val MUTED = Ui.MUTED
    private val GOLD = Ui.GOLD
    private val GOLD_DIM = Ui.GOLD_DIM
    private val SURFACE = Ui.SURFACE
    private val SURFACE2 = Ui.SURFACE2
    private val THEME = android.R.style.Theme_Material_Dialog_Alert
    private val MAX_MIN = 600
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("cfg", MODE_PRIVATE) }
    private val db by lazy { Db(this) }
    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 500)
        }
    }

    // Estado del formulario (se guarda en SharedPreferences "cfg")
    private var minutes = 20
    /** Campanas sin textos; si hay textos, se usa labels.size (ver bellCount()). */
    private var bells = 0
    private var prep = false
    private var labels: List<String> = emptyList()
    private var selectedName: String? = null
    /** Texto del campo «Nombre»; es la fuente de verdad del nombre de la meditación. */
    private var nameText = ""
    private lateinit var etName: EditText

    // Vista A: configuración
    private lateinit var configView: ScrollView
    /** Contenedor de la vista A: configView + barra inferior (la barra solo existe en el inicio). */
    private lateinit var configWrap: LinearLayout
    private lateinit var presetsBox: LinearLayout
    private lateinit var ring: RingTimerView
    private lateinit var chipsBox: LinearLayout
    private lateinit var btnBellMinus: TextView
    private lateinit var btnBellPlus: TextView
    private lateinit var tvBellsN: TextView
    private lateinit var tvBellsSub: TextView
    private lateinit var btnLabels: TextView
    private lateinit var swPrep: Switch
    private lateinit var btnSave: TextView
    /** Contenedor del selector «Aviso al sonar» del formulario. */
    private lateinit var formModeBox: LinearLayout

    // Vista B: en curso
    private lateinit var runView: LinearLayout
    private lateinit var tvPhase: TextView
    private lateinit var runRing: RingTimerView
    private lateinit var tvRunLabel: TextView
    private lateinit var tvRunSub: TextView
    /** Contenedor del selector «Aviso» de la vista en curso. */
    private lateinit var runModeBox: LinearLayout

    private var inRun = false
    /** elapsedRealtime en el que se pulsó «Comenzar»; 0 si no hay arranque pendiente. */
    private var startAt = 0L
    /** Clave del plan de la sesión para recalcular las campanas del anillo solo al cambiar. */
    private var ticksKey = -1L
    /** Último contador de campanas visto, para lanzar la onda una sola vez por campana. */
    private var lastBellCounter = -1

    /** Idioma con el que se creó la pantalla: si cambia en Ajustes, al volver se recrea. */
    private var shownLang = ""

    override fun attachBaseContext(base: Context) {
        shownLang = Lang.get(base)
        super.attachBaseContext(Lang.wrap(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()

        val root = FrameLayout(this).apply { setBackgroundColor(BG) }
        Ui.page(this, root)
        configView = buildConfig()
        runView = buildRun()
        configWrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        configWrap.addView(configView, LinearLayout.LayoutParams(MATCH, 0, 1f))
        configWrap.addView(Ui.primaryButton(this, getString(R.string.main_start)) { startFromForm() }, Ui.lp(this, t = 8))
        configWrap.addView(Ui.tabBar(this, 0), Ui.lp(this, t = 8))
        root.addView(configWrap, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(runView, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)

        loadCfg()
        refreshForm()
        // Si ya hay una sesión en curso, se muestra directamente la vista de sesión
        switchTo(SessionState.running, animate = false)
        firstRun()
    }

    override fun onResume() {
        super.onResume()
        if (Lang.get(this) != shownLang) { recreate(); return }
        fillModeSelectors()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIF) showBatteryDialog()
    }

    // ---------- Vista A: inicio ----------

    private fun buildConfig(): ScrollView {
        val col = column()

        // Cabecera: título y accesos
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(Ui.text(this, getString(R.string.main_title), 28f, TEXT, 0), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        col.addView(head, Ui.lp(this, t = 8))

        // Preselecciones: tarjetas horizontales
        col.addView(Ui.text(this, getString(R.string.main_presets_header), 12f, MUTED, 2).apply { letterSpacing = 0.12f },
            Ui.lp(this, t = 28, b = 10))
        presetsBox = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        col.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(presetsBox)
        })

        // Nombre de la meditación (visible siempre: al crear una nueva se escribe aquí)
        etName = EditText(this).apply {
            hint = getString(R.string.main_name_hint); setSingleLine(true); textSize = 20f; typeface = Ui.font(0)
            setTextColor(TEXT); setHintTextColor(MUTED)
            background = Ui.shape(this@MainActivity, SURFACE, 16)
            setPadding(dp(18), dp(14), dp(18), dp(14))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            onChanged { nameText = text.toString() }
        }
        col.addView(etName, Ui.lp(this, t = 20))

        // Anillo: toque = teclear minutos
        ring = RingTimerView(this).apply { setOnClickListener { askMinutes() } }
        col.addView(ring, LinearLayout.LayoutParams(dp(250), dp(250)).apply {
            topMargin = dp(28)
            gravity = Gravity.CENTER_HORIZONTAL
        })

        // Botones −  +  para los minutos
        val minRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        minRow.addView(stepButton("−") { minutes = (minutes - 1).coerceAtLeast(1); refreshForm() }, roundLp())
        minRow.addView(stepButton("+") { minutes = (minutes + 1).coerceAtMost(MAX_MIN); refreshForm() }, roundLp())
        col.addView(minRow, Ui.lp(this, t = 16))

        // Atajos de minutos
        chipsBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        col.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            addView(chipsBox)
        }, Ui.lp(this, t = 12))

        // Tarjeta: campanas intermedias
        val card = Ui.card(this)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val txt = column()
        txt.addView(Ui.text(this, getString(R.string.main_bells_title), 16f, TEXT, 2))
        tvBellsSub = Ui.text(this, "", 13f, MUTED)
        txt.addView(tvBellsSub, Ui.lp(this, t = 4))
        row.addView(txt, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        btnBellMinus = stepButton("−") {
            if (bells > 0) { bells--; refreshForm() }
        }
        tvBellsN = Ui.text(this, "0", 20f, TEXT, 2, true)
        btnBellPlus = stepButton("+") {
            if (bells < Schedule.MAX_BELLS) { bells++; refreshForm() }
        }
        row.addView(btnBellMinus, roundLp())
        row.addView(tvBellsN, LinearLayout.LayoutParams(dp(36), dp(48)))
        row.addView(btnBellPlus, roundLp())
        card.addView(row)

        btnLabels = Ui.secondaryButton(this, "") {
            openLabelsEditor(labels) { list ->
                labels = list
                if (list.isNotEmpty()) bells = list.size
                refreshForm()
            }
        }
        card.addView(btnLabels, Ui.lp(this, t = 16))
        col.addView(card, Ui.lp(this, t = 28))

        // Preparación de 10 s
        val prepRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        prepRow.addView(Ui.text(this, getString(R.string.main_prep_label), 17f, TEXT), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        swPrep = Switch(this)
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked))
        swPrep.thumbTintList = ColorStateList(states, intArrayOf(GOLD, MUTED))
        swPrep.trackTintList = ColorStateList(states, intArrayOf(GOLD, SURFACE2))
        swPrep.setOnCheckedChangeListener { _, on -> prep = on; saveCfg() }
        prepRow.addView(swPrep)
        col.addView(prepRow, Ui.lp(this, t = 28))

        // Aviso al sonar (bongs / vibración / ambos)
        col.addView(Ui.text(this, getString(R.string.main_mode_header), 13f, MUTED, 2).apply { letterSpacing = 0.12f },
            Ui.lp(this, t = 28, b = 10))
        formModeBox = column()
        col.addView(formModeBox, Ui.lp(this))

        // Pie (el botón «Comenzar» va fijo en configWrap)
        btnSave = Ui.secondaryButton(this, getString(R.string.main_save_as_preset)) { saveAsPreset() }
        col.addView(btnSave, Ui.lp(this, t = 24, b = 24))

        return ScrollView(this).apply { addView(col) }
    }

    /** Vuelve a pintar el formulario a partir del estado (anillo, campanas, chips, textos) y lo guarda. */
    private fun refreshForm() {
        // Con una preselección elegida el nombre ya se ve en la tarjeta: se oculta el campo
        etName.visibility = if (selectedName == null) View.VISIBLE else View.GONE
        btnSave.text = if (selectedName != null) getString(R.string.main_save_changes) else getString(R.string.main_save_as_preset)
        if (etName.text.toString() != nameText) etName.setText(nameText)
        val n = bellCount()

        ring.centerText = minutes.toString()
        ring.subText = getString(R.string.main_minutes_unit)
        ring.ticks = Schedule.tickFractions(n)
        ring.setProgress(0f, false)

        chipsBox.removeAllViews()
        for (m in listOf(5, 10, 20, 30, 45, 60)) {
            chipsBox.addView(Ui.chip(this, "$m", m == minutes) { minutes = m; refreshForm() },
                Ui.lp(this, w = Ui.WRAP, r = 8))
        }

        val editable = labels.isEmpty()
        tvBellsN.text = n.toString()
        val every = minutes.toDouble() / (n + 1)
        val everyTxt = if (every == Math.floor(every)) every.toInt().toString() else "%.1f".format(every)
        val base = if (n == 0) getString(R.string.main_only_final_sub) else getString(R.string.main_every_min, everyTxt)
        tvBellsSub.text = if (labels.isNotEmpty()) "$base\n${getString(R.string.main_defined_by_labels)}" else base
        val canMinus = editable && bells > 0
        val canPlus = editable && bells < Schedule.MAX_BELLS
        btnBellMinus.isEnabled = canMinus
        btnBellMinus.alpha = if (canMinus) 1f else 0.35f
        btnBellPlus.isEnabled = canPlus
        btnBellPlus.alpha = if (canPlus) 1f else 0.35f

        btnLabels.text = if (labels.isEmpty()) getString(R.string.main_add_labels) else getString(R.string.main_edit_labels, labels.size)
        swPrep.isChecked = prep
        saveCfg()
    }

    private fun reloadPresets() {
        presetsBox.removeAllViews()
        for (p in db.presets()) presetsBox.addView(presetCard(p), Ui.lp(this, w = dp(150), r = 10))
        presetsBox.addView(newCard(), Ui.lp(this, w = dp(150), r = 10))
    }

    private fun presetCard(p: Preset): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = cardBg(p.name == selectedName)
        c.setPadding(dp(14), dp(14), dp(14), dp(14))
        c.addView(Ui.text(this, p.name, 16f, TEXT, 2).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        c.addView(Ui.text(this, getString(R.string.main_preset_summary, p.minutes, bellsText(p.bellCount)), 13f, MUTED), Ui.lp(this, t = 6))
        Ui.press(c)
        c.setOnClickListener { selectPreset(p) }
        c.setOnLongClickListener { presetMenu(p); true }
        return c
    }

    /** Tarjeta «+ Nueva»: deja el formulario como está y quita la selección. */
    private fun newCard(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER
        c.background = cardBg(false)
        c.minimumHeight = dp(76)
        c.setPadding(dp(14), dp(14), dp(14), dp(14))
        c.addView(Ui.text(this, getString(R.string.main_new_preset), 16f, GOLD, 2, true))
        Ui.press(c)
        c.setOnClickListener {
            // Hoja en blanco: sin nombre ni textos, y se pide el nombre
            selectedName = null
            nameText = ""
            labels = emptyList()
            bells = 0
            refreshForm()
            reloadPresets()
            etName.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(etName, 0)
        }
        return c
    }

    private fun cardBg(selected: Boolean) = Ui.shape(this, SURFACE, 18).apply {
        if (selected) setStroke(Ui.dpf(this@MainActivity, 1.5f).toInt(), GOLD)
    }

    /** Carga una preselección en el formulario y la marca como seleccionada. */
    private fun selectPreset(p: Preset) {
        minutes = p.minutes
        bells = p.bellCount
        prep = p.prep
        labels = p.labels
        selectedName = p.name
        nameText = p.name
        refreshForm()
        reloadPresets()
    }

    /** Pulsación larga sobre una preselección. */
    private fun presetMenu(p: Preset) {
        AlertDialog.Builder(this, THEME)
            .setTitle(p.name)
            .setItems(arrayOf<CharSequence>(getString(R.string.main_menu_edit_labels), getString(R.string.main_delete))) { _, which ->
                if (which == 0) {
                    editPresetLabels(p)
                } else {
                    db.deletePreset(p.name)
                    if (selectedName == p.name) selectedName = null
                    refreshForm()
                    reloadPresets()
                }
            }
            .show()
    }

    private fun editPresetLabels(p: Preset) {
        openLabelsEditor(p.labels) { list ->
            val updated = Preset(p.name, p.minutes, if (list.isNotEmpty()) list.size else p.bells, p.prep, list)
            db.savePreset(updated)
            selectPreset(updated)
        }
    }

    private fun saveAsPreset() {
        val name = nameText.trim()
        if (name.isEmpty()) {
            toast(getString(R.string.main_name_required))
            etName.requestFocus()
            return
        }
        // Mismo nombre = actualiza la preselección existente
        db.savePreset(Preset(name, minutes, bellCount(), prep, labels))
        selectedName = name
        refreshForm()
        reloadPresets()
        toast(getString(R.string.main_saved))
    }

    /** Diálogo para teclear los minutos (tocando el anillo). */
    private fun askMinutes() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            hint = getString(R.string.main_minutes_hint, MAX_MIN)
            setText(minutes.toString())
            setTextColor(TEXT)
            setHintTextColor(MUTED)
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        AlertDialog.Builder(this, THEME)
            .setTitle(getString(R.string.main_minutes_title))
            .setView(input)
            .setPositiveButton(getString(R.string.main_accept)) { _, _ ->
                val v = input.text.toString().trim().toIntOrNull()
                if (v != null && v in 1..MAX_MIN) {
                    minutes = v
                    refreshForm()
                } else {
                    toast(getString(R.string.main_minutes_range, MAX_MIN))
                }
            }
            .setNegativeButton(getString(R.string.main_cancel), null)
            .show()
    }

    /** Editor de textos de las campanas: uno por línea, o separados por «;». */
    private fun openLabelsEditor(initial: List<String>, onResult: (List<String>) -> Unit) {
        val input = EditText(this).apply {
            hint = getString(R.string.main_labels_hint)
            setText(Labels.join(initial))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 8
            gravity = Gravity.TOP or Gravity.START
            setTextColor(TEXT)
            setHintTextColor(MUTED)
        }
        val counter = Ui.text(this, "", 13f, MUTED)
        fun updateCounter() {
            val n = Labels.parse(input.text.toString()).size
            counter.text = when (n) {
                0 -> getString(R.string.main_no_labels)
                else -> resources.getQuantityString(R.plurals.main_labels_summary, n, n, n - 1)
            }
        }
        input.onChanged { updateCounter() }
        updateCounter()

        val col = column()
        col.setPadding(dp(20), dp(8), dp(20), dp(8))
        col.addView(input, Ui.lp(this))
        col.addView(counter, Ui.lp(this, t = 10))

        AlertDialog.Builder(this, THEME)
            .setTitle(getString(R.string.main_labels_title))
            .setView(col)
            .setPositiveButton(getString(R.string.main_save)) { _, _ -> onResult(Labels.parse(input.text.toString())) }
            .setNeutralButton(getString(R.string.main_labels_clear)) { _, _ -> onResult(emptyList()) }
            .setNegativeButton(getString(R.string.main_cancel), null)
            .show()
    }

    /** Número de campanas: la de los textos si los hay, si no el stepper. */
    private fun bellCount(): Int = if (labels.isNotEmpty()) (labels.size - 1).coerceAtLeast(0) else bells

    private fun loadCfg() {
        minutes = prefs.getInt("min", 20).coerceIn(1, MAX_MIN)
        bells = prefs.getInt("bells", 0).coerceIn(0, Schedule.MAX_BELLS)
        prep = prefs.getBoolean("prep", false)
        labels = Labels.parse(prefs.getString("labels", "") ?: "")
        selectedName = prefs.getString("selectedName", "")?.takeIf { it.isNotEmpty() }
        nameText = prefs.getString("nameText", selectedName ?: "") ?: ""
    }

    private fun saveCfg() {
        prefs.edit()
            .putInt("min", minutes)
            .putInt("bells", bells)
            .putBoolean("prep", prep)
            .putString("labels", Labels.join(labels))
            .putString("selectedName", selectedName ?: "")
            .putString("nameText", nameText)
            .apply()
    }

    private fun startFromForm() {
        if (minutes !in 1..MAX_MIN) {
            toast(getString(R.string.main_minutes_range, MAX_MIN))
            return
        }
        if (bellCount() > Schedule.MAX_BELLS) {
            toast(resources.getQuantityString(R.plurals.main_max_bells, Schedule.MAX_BELLS, Schedule.MAX_BELLS))
            return
        }
        saveCfg()
        // Aviso si el volumen multimedia está a cero y el modo usa sonido
        val am = getSystemService(android.media.AudioManager::class.java)
        if (app.meditando.Settings(this).sound && am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) == 0) {
            toast(getString(R.string.main_no_volume_warning))
        }
        MeditationService.start(this, minutes, bellCount(), prep, nameText.trim().ifEmpty { selectedName ?: "" }, labels)
        startAt = SystemClock.elapsedRealtime()
        showRun()
    }

    // ---------- Vista B: en curso ----------

    private fun buildRun(): LinearLayout {
        val ringSize = minOf(resources.displayMetrics.widthPixels - dp(40), dp(320))
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        tvPhase = Ui.text(this, "", 13f, MUTED, 2, true).apply { letterSpacing = 0.2f }
        runRing = RingTimerView(this)
        tvRunLabel = Ui.text(this, "", 26f, TEXT, 0, true).apply {
            maxLines = 4
            ellipsize = TextUtils.TruncateAt.END
        }
        tvRunSub = Ui.text(this, "", 14f, MUTED, 1, true)

        col.addView(spacer(1f))
        col.addView(tvPhase)
        col.addView(runRing, LinearLayout.LayoutParams(ringSize, ringSize).apply { topMargin = dp(24) })
        col.addView(tvRunLabel, Ui.lp(this, t = 32))
        col.addView(tvRunSub, Ui.lp(this, t = 16))
        col.addView(spacer(1f))
        runModeBox = column()
        col.addView(runModeBox, Ui.lp(this, t = 24))
        col.addView(Ui.secondaryButton(this, getString(R.string.main_stop)) {
            MeditationService.stop(this)
            startAt = 0L
        }, Ui.lp(this, b = 24))
        return col
    }

    /** Refresco de la vista B con el estado de SessionState. */
    private fun updateRun(now: Long) {
        val ph = SessionState.phase

        // Campanas del anillo: se calculan una vez por sesión
        val key = SessionState.plannedSec.toLong() * 1000 + SessionState.totalBells
        if (key != ticksKey) {
            ticksKey = key
            val sec = SessionState.plannedSec
            runRing.ticks = if (sec >= 60 && SessionState.totalBells > 0) {
                Schedule.tickFractions(SessionState.totalBells)
            } else {
                emptyList()
            }
        }

        setBreathing(ph == SessionState.MEDITATING)
        tvPhase.text = when (ph) {
            SessionState.PREP -> getString(R.string.main_phase_prep)
            SessionState.MEDITATING -> getString(R.string.main_phase_meditating)
            else -> getString(R.string.main_phase_end)
        }

        when (ph) {
            SessionState.PREP -> {
                runRing.centerText = Stats.clock(secs(SessionState.prepEnd - now))
                runRing.subText = getString(R.string.main_prep_sub)
                runRing.setProgress(0f, false)
            }
            SessionState.MEDITATING -> {
                val total = (SessionState.medEnd - SessionState.medStart).coerceAtLeast(1L)
                runRing.centerText = Stats.clock(secs(SessionState.medEnd - now))
                runRing.subText = SessionState.presetName
                runRing.setProgress((now - SessionState.medStart).toFloat() / total, false)
            }
            else -> {
                runRing.centerText = "00:00"
                runRing.subText = ""
                runRing.setProgress(1f, false)
            }
        }

        // Texto del tramo actual; durante la preparación se anuncia el primero
        if (SessionState.labels.isNotEmpty() && ph == SessionState.PREP) {
            showLabel(getString(R.string.main_first_label, SessionState.labels[0]), MUTED)
        } else {
            showLabel(SessionState.currentLabel, TEXT)
        }

        // Siguiente campana
        val next = SessionState.nextBellAt
        tvRunSub.text = when {
            ph == SessionState.FINISHING -> ""
            next > 0L -> {
                val wait = Stats.clock(secs(next - now))
                val name = SessionState.labels.getOrNull(SessionState.bellsDone + 1)
                if (name != null) getString(R.string.main_next_named, name, wait) else getString(R.string.main_next_bell, wait)
            }
            else -> getString(R.string.main_run_only_final)
        }

        // Onda al sonar cada campana
        if (SessionState.bellCounter != lastBellCounter) {
            if (lastBellCounter != -1) runRing.ripple()
            lastBellCounter = SessionState.bellCounter
        }
    }

    /**
     * Reconstruye los selectores de «Aviso» (formulario y vista en curso) con el modo guardado en Settings.
     * Se llama en onResume, así se refleja un cambio hecho en Ajustes.
     */
    private fun fillModeSelectors() {
        // Nombre completo: android.provider.Settings está importado en este fichero
        val s = app.meditando.Settings(this)
        formModeBox.removeAllViews()
        formModeBox.addView(Ui.modeSelector(this, s), Ui.lp(this))
        runModeBox.removeAllViews()
        runModeBox.addView(Ui.text(this, getString(R.string.main_mode_label), 13f, MUTED, 2, true).apply { letterSpacing = 0.12f },
            Ui.lp(this, b = 10))
        runModeBox.addView(Ui.modeSelector(this, s), Ui.lp(this))
    }

    /** Vista de arranque: el servicio aún no ha publicado su estado. */
    private fun showStarting() {
        tvPhase.text = getString(R.string.main_phase_starting)
        setBreathing(false)
        runRing.centerText = "--:--"
        runRing.subText = ""
        runRing.setProgress(0f, false)
        tvRunSub.text = ""
        showLabel("", MUTED)
    }

    /** Cambia el texto con un fundido; solo anima si el texto cambia. */
    private fun showLabel(text: String, color: Int) {
        if (tvRunLabel.text.toString() != text) {
            tvRunLabel.text = text
            tvRunLabel.alpha = 0f
            tvRunLabel.animate().alpha(1f).setDuration(300).start()
        }
        tvRunLabel.setTextColor(color)
    }

    /** La respiración se reinicia al asignarla, así que solo se toca si cambia. */
    private fun setBreathing(on: Boolean) {
        if (runRing.breathing != on) runRing.breathing = on
    }

    private fun secs(ms: Long): Long = (ms + 999) / 1000

    /** Refresco cada 500 ms mientras la actividad está en primer plano. */
    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        when {
            SessionState.running -> {
                startAt = 0L
                if (!inRun) showRun()
                updateRun(now)
            }
            // Recién pulsado «Comenzar»: el servicio aún no ha publicado su estado
            startAt != 0L && now - startAt < 3000L -> {
                if (!inRun) showRun()
                showStarting()
            }
            else -> {
                if (startAt != 0L) toast(getString(R.string.main_session_not_started))
                startAt = 0L
                if (inRun) showConfig()
            }
        }
    }

    private fun showRun() = switchTo(run = true, animate = true)

    private fun showConfig() = switchTo(run = false, animate = true)

    /** Alterna entre la vista A y la B con un fundido de 250 ms. */
    private fun switchTo(run: Boolean, animate: Boolean) {
        inRun = run
        val show = if (run) runView else configWrap
        val hide = if (run) configWrap else runView
        if (!run) reloadPresets()
        show.animate().cancel()
        hide.animate().cancel()

        if (!animate) {
            show.alpha = 1f
            show.visibility = View.VISIBLE
            hide.alpha = 1f
            hide.visibility = View.GONE
            return
        }
        show.alpha = 0f
        show.visibility = View.VISIBLE
        show.animate().alpha(1f).setDuration(250).start()
        hide.animate().alpha(0f).setDuration(250).withEndAction {
            // Solo se oculta si no se ha vuelto a cambiar de vista entretanto
            if (inRun == run) hide.visibility = View.GONE
        }.start()
    }

    // ---------- Primer arranque ----------

    private fun firstRun() {
        if (prefs.getBoolean("asked", false)) return
        prefs.edit().putBoolean("asked", true).apply()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(NOTIF_PERM) != PackageManager.PERMISSION_GRANTED
        ) {
            // La explicación de batería se muestra al responder al permiso
            requestPermissions(arrayOf(NOTIF_PERM), REQ_NOTIF)
        } else {
            showBatteryDialog()
        }
    }

    private fun showBatteryDialog() {
        AlertDialog.Builder(this, THEME)
            .setTitle(getString(R.string.main_battery_title))
            .setMessage(getString(R.string.main_battery_message))
            .setPositiveButton(getString(R.string.main_open_settings)) { _, _ -> openBatterySettings() }
            .setNegativeButton(getString(R.string.main_not_now), null)
            .show()
    }

    private fun openBatterySettings() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            toast(getString(R.string.main_settings_failed))
        }
    }

    // ---------- Utilidades de UI ----------

    private fun dp(v: Int): Int = Ui.dp(this, v)

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun spacer(weight: Float) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(Ui.MATCH, 0, weight)
    }

    /** Botón circular de 48 dp para «−» y «+». */
    private fun stepButton(label: String, onClick: () -> Unit): TextView =
        Ui.text(this, label, 22f, TEXT, 1, true).apply {
            background = Ui.shape(this@MainActivity, SURFACE2, 24)
            Ui.press(this)
            setOnClickListener { onClick() }
        }

    private fun roundLp() = LinearLayout.LayoutParams(dp(48), dp(48)).apply { setMargins(dp(6), 0, dp(6), 0) }

    private fun bellsText(n: Int): String =
        if (n == 0) getString(R.string.main_bells_only_final)
        else resources.getQuantityString(R.plurals.main_bells, n, n)

    private fun EditText.onChanged(f: () -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                f()
            }
        })
    }
}
