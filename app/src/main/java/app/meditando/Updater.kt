package app.meditando

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Actualizaciones desde las «Releases» de GitHub (sin Google Play).
 * Busca la última release, descarga el APK, verifica su SHA-256 y lo instala con PackageInstaller.
 * Android exige además que el APK esté firmado con la misma clave que el instalado.
 */
object Updater {
    const val REPO = "rosauro/meditando"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    private const val CHANNEL = "updates"
    const val DAY_MS = 24L * 3600 * 1000

    data class Release(val version: String, val apkUrl: String, val shaUrl: String?, val notes: String)

    /** Última release más nueva que la instalada (null si no hay o aún no se ha comprobado). */
    @Volatile var available: Release? = null
    @Volatile var busy = false
    @Volatile var lastError: String? = null
    /** true si la instalación la ha pedido el usuario con la app a la vista (se abre la confirmación directamente). */
    @Volatile var userInitiated = false

    // ---------- Lógica pura (con tests) ----------

    fun parseVersion(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    fun isNewer(remote: String, local: String): Boolean {
        val a = parseVersion(remote)
        val b = parseVersion(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun parseRelease(json: String): Release? {
        val o = JSONObject(json)
        val tag = o.optString("tag_name")
        if (tag.isEmpty() || o.optBoolean("draft") || o.optBoolean("prerelease")) return null
        val assets = o.optJSONArray("assets") ?: return null
        var apk: String? = null
        var sha: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name")
            val url = a.optString("browser_download_url")
            if (name.endsWith(".apk.sha256")) sha = url else if (name.endsWith(".apk")) apk = url
        }
        return apk?.let { Release(tag.removePrefix("v"), it, sha, o.optString("body")) }
    }

    /** Extrae el hash de un fichero .sha256 («<hash>  nombre.apk»). */
    fun parseSha(text: String): String? =
        text.trim().split(Regex("\\s+")).firstOrNull()?.lowercase()?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }

    // ---------- Red (llamar fuera del hilo principal) ----------

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "Meditando-Updater")
        setRequestProperty("Accept", "application/vnd.github+json")
    }

    private fun readText(url: String): String {
        val c = open(url)
        try {
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().readText()
        } finally { c.disconnect() }
    }

    fun currentVersion(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"

    /** Consulta GitHub; actualiza [available]. Lanza excepción si no hay red. */
    fun check(ctx: Context): Release? {
        val c = open(API)
        val rel = try {
            if (c.responseCode == 404) null // aún no hay releases
            else if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
            else parseRelease(c.inputStream.bufferedReader().readText())
        } finally { c.disconnect() }
        Settings(ctx).lastCheck = System.currentTimeMillis()
        available = rel?.takeIf { isNewer(it.version, currentVersion(ctx)) }
        return available
    }

    /** Descarga el APK a la caché y verifica su SHA-256 (obligatorio: sin hash publicado no se instala). */
    fun download(ctx: Context, rel: Release): File {
        val expected = rel.shaUrl?.let { parseSha(readText(it)) } ?: throw IOException("sin hash SHA-256")
        val dir = File(ctx.cacheDir, "update").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, "meditando-${rel.version}.apk")
        val md = MessageDigest.getInstance("SHA-256")
        val c = open(rel.apkUrl)
        try {
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
            c.inputStream.use { inp -> file.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) { val n = inp.read(buf); if (n < 0) break; md.update(buf, 0, n); out.write(buf, 0, n) }
            } }
        } finally { c.disconnect() }
        val got = md.digest().joinToString("") { "%02x".format(it) }
        if (got != expected) { file.delete(); throw IOException("SHA-256 no coincide") }
        return file
    }

    /** ¿Hay una sesión de meditación en curso (o recuperable)? Instalar mataría el proceso. */
    fun sessionActive(ctx: Context): Boolean =
        SessionState.running || ctx.getSharedPreferences("session", Context.MODE_PRIVATE).getBoolean("active", false)

    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    /** Instala el APK con PackageInstaller; en Android 12+ intenta hacerlo sin pedir confirmación. */
    fun install(ctx: Context, apk: File) {
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            apk.inputStream().use { inp -> s.openWrite("meditando.apk", 0, apk.length()).use { out ->
                inp.copyTo(out); s.fsync(out)
            } }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (android.os.Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(ctx, id, Intent(ctx, InstallReceiver::class.java), flags)
            s.commit(pending.intentSender)
        }
    }

    /** Comprobación en segundo plano (tarea periódica o al abrir): instala sola si se puede. */
    fun backgroundCheck(ctx: Context) {
        val settings = Settings(ctx)
        if (!settings.autoUpdate || busy) return
        busy = true
        try {
            val rel = check(ctx) ?: return
            if (!sessionActive(ctx) && canInstall(ctx)) install(ctx, download(ctx, rel))
            else notify(ctx, R.string.updater_available_title, R.string.updater_available_text, rel.version,
                PendingIntent.getActivity(ctx, 10, Intent(ctx, SettingsActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } catch (e: Exception) {
            lastError = e.message
        } finally { busy = false }
    }

    fun notify(ctx: Context, title: Int, text: Int, version: String, pending: PendingIntent) {
        val lc = Lang.wrap(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, lc.getString(R.string.updater_channel), NotificationManager.IMPORTANCE_DEFAULT))
        nm.notify(2, Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(lc.getString(title, version))
            .setContentText(lc.getString(text, version))
            .setContentIntent(pending).setAutoCancel(true).build())
    }
}

/** Recibe el resultado del instalador: si hace falta confirmación, la abre o la avisa con una notificación. */
class InstallReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (Updater.userInitiated) {
                    ctx.startActivity(confirm)
                } else {
                    val pi = PendingIntent.getActivity(ctx, 11, confirm, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    Updater.notify(ctx, R.string.updater_ready_title, R.string.updater_ready_text,
                        Updater.available?.version ?: "", pi)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Updater.lastError = null
            else -> Updater.lastError = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        }
    }
}
