package app.meditando

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** [labels]: texto de cada tramo (el 1.º suena al empezar). N textos = N-1 campanas intermedias + bong final. */
data class Preset(
    val name: String,
    val minutes: Int,
    val bells: Int,
    val prep: Boolean,
    val labels: List<String> = emptyList(),
) {
    val bellCount get() = if (labels.isNotEmpty()) (labels.size - 1).coerceAtLeast(0) else bells
}

/** Estado compartido de la sesión en curso (lo escribe MeditationService, lo lee la UI). */
object SessionState {
    const val IDLE = 0
    const val PREP = 1
    const val MEDITATING = 2
    const val FINISHING = 3

    @Volatile var phase = IDLE
    /** SystemClock.elapsedRealtime() en ms */
    @Volatile var prepEnd = 0L
    @Volatile var medStart = 0L
    @Volatile var medEnd = 0L
    /** Duración planificada de la meditación, en segundos */
    @Volatile var plannedSec = 0
    /** Próxima campana intermedia (elapsedRealtime ms); 0 si no quedan */
    @Volatile var nextBellAt = 0L
    @Volatile var bellsLeft = 0
    @Volatile var totalBells = 0
    /** Campanas intermedias ya sonadas */
    @Volatile var bellsDone = 0
    @Volatile var labels: List<String> = emptyList()
    /** Texto de la última campana sonada ("" antes de la primera) */
    @Volatile var currentLabel = ""
    /** Incrementa con cada campana: la UI lo usa para disparar la animación */
    @Volatile var bellCounter = 0
    @Volatile var presetName = ""
    val running get() = phase != IDLE
}

class Db(ctx: Context) : SQLiteOpenHelper(ctx, "meditando.db", null, 2) {

    companion object {
        val KUJI_IN = Preset("Kuji-in", 60, 9, false,
            listOf("Rin", "Kyo", "Toh", "Sha", "Kai", "Jin", "Retzu", "Zai", "Zen"))
        val KAMA_CHAKRA = Preset("Kama Chakra", 60, 9, false, listOf(
            "Om Dhïm Klïm Shanti Karuna Kama",
            "Vishwa Shakti Avaham",
            "Tat Pranamami Sada Shivalingam",
            "Dhïm Ra Ra",
            "Klïm Ra Ra",
            "Om Aïm Klïm Hrïm SatSundara R'shiNaya",
            "Om naga naga naga Nam nam nam Rupa rupa rupa Raksha",
            "Om ParamAditi Araga Swasthya",
            "Om nama kundalini Shakti"))
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE presets(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE NOT NULL, minutes INTEGER NOT NULL, bells INTEGER NOT NULL, prep INTEGER NOT NULL, labels TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE sessions(id INTEGER PRIMARY KEY AUTOINCREMENT, start_ms INTEGER NOT NULL, planned_sec INTEGER NOT NULL, actual_sec INTEGER NOT NULL, completed INTEGER NOT NULL, preset TEXT NOT NULL)")
        listOf(Preset("Fijación", 5, 0, false), Preset("Relajación consciente", 20, 0, false), KUJI_IN, KAMA_CHAKRA)
            .forEach { db.insert("presets", null, cv(it)) }
    }

    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {
        if (o < 2) {
            db.execSQL("ALTER TABLE presets ADD COLUMN labels TEXT NOT NULL DEFAULT ''")
            db.update("presets", cv(KUJI_IN), "name=?", arrayOf(KUJI_IN.name))
            db.insertWithOnConflict("presets", null, cv(KAMA_CHAKRA), SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    private fun cv(p: Preset) = ContentValues().apply {
        put("name", p.name); put("minutes", p.minutes); put("bells", p.bellCount)
        put("prep", if (p.prep) 1 else 0); put("labels", Labels.join(p.labels))
    }

    fun presets(): List<Preset> = readableDatabase.rawQuery("SELECT name,minutes,bells,prep,labels FROM presets ORDER BY id", null).use { c ->
        buildList { while (c.moveToNext()) add(Preset(c.getString(0), c.getInt(1), c.getInt(2), c.getInt(3) == 1, Labels.parse(c.getString(4)))) }
    }

    /** Crea o actualiza (por nombre) un preset. */
    fun savePreset(p: Preset) {
        val n = writableDatabase.update("presets", cv(p), "name=?", arrayOf(p.name))
        if (n == 0) writableDatabase.insert("presets", null, cv(p))
    }

    fun deletePreset(name: String) { writableDatabase.delete("presets", "name=?", arrayOf(name)) }

    fun addSession(r: SessionRec) {
        writableDatabase.insert("sessions", null, ContentValues().apply {
            put("start_ms", r.startMs); put("planned_sec", r.plannedSec); put("actual_sec", r.actualSec)
            put("completed", if (r.completed) 1 else 0); put("preset", r.preset)
        })
    }

    /** Más recientes primero. */
    fun sessions(): List<SessionRec> = readableDatabase.rawQuery(
        "SELECT id,start_ms,planned_sec,actual_sec,completed,preset FROM sessions ORDER BY start_ms DESC", null).use { c ->
        buildList { while (c.moveToNext()) add(SessionRec(c.getLong(0), c.getLong(1), c.getInt(2), c.getInt(3), c.getInt(4) == 1, c.getString(5))) }
    }

    fun deleteSession(id: Long) { writableDatabase.delete("sessions", "id=?", arrayOf(id.toString())) }
}
