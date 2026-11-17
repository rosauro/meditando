package app.meditando

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test fun comparaVersiones() {
        assertTrue(Updater.isNewer("1.2", "1.1"))
        assertTrue(Updater.isNewer("v1.10", "1.9"))
        assertTrue(Updater.isNewer("2.0", "1.9.9"))
        assertTrue(Updater.isNewer("1.1.1", "1.1"))
        assertFalse(Updater.isNewer("1.1", "1.1"))
        assertFalse(Updater.isNewer("1.1.0", "1.1"))
        assertFalse(Updater.isNewer("1.0", "1.1"))
    }

    @Test fun parseaLaRelease() {
        val json = """{"tag_name":"v1.2","draft":false,"prerelease":false,"body":"notas",
            "assets":[{"name":"app-release.apk.sha256","browser_download_url":"https://x/s"},
                      {"name":"app-release.apk","browser_download_url":"https://x/a"}]}"""
        val r = Updater.parseRelease(json)!!
        assertEquals("1.2", r.version)
        assertEquals("https://x/a", r.apkUrl)
        assertEquals("https://x/s", r.shaUrl)
    }

    @Test fun ignoraBorradoresYPrereleases() {
        assertNull(Updater.parseRelease("""{"tag_name":"v9","draft":true,"assets":[{"name":"a.apk","browser_download_url":"u"}]}"""))
        assertNull(Updater.parseRelease("""{"tag_name":"v9","prerelease":true,"assets":[{"name":"a.apk","browser_download_url":"u"}]}"""))
        assertNull(Updater.parseRelease("""{"tag_name":"v9","assets":[]}"""))
    }

    @Test fun parseaElHash() {
        val h = "a".repeat(64)
        assertEquals(h, Updater.parseSha("$h  app-release.apk\n"))
        assertNull(Updater.parseSha("no-es-un-hash"))
    }
}
