# Meditando (app Android)
Temporizador de meditación: duración, campanas intermedias con texto opcional, bong final; presets, historial, ajustes, actualizador desde GitHub Releases. Español por defecto + inglés.
- Entorno: `ANDROID_HOME` apuntando al SDK; en el equipo del autor `source /datos/android-sdk/env.sh` (se instala con `./instalar-toolchain.sh`, sin root).
- Compilar y probar: `./gradlew --no-daemon testDebugUnitTest assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
- Publicar: `./publicar.sh X.Y` (requiere `keystore.properties` y `gh`). La clave de firma NO está en el repo: si se pierde, nadie puede actualizar.
- Textos: `res/values/strings_*.xml` (es) y `res/values-en/strings_*.xml` (en) con las mismas claves (`StringsTest` lo comprueba). Nada de literales en Kotlin.
- Kotlin sin dependencias externas; UI por código (`Ui.kt`, `RingTimerView.kt`); SQLite nativo (`Db.kt`).
- Servicio en primer plano `MeditationService.kt` (persiste el plan y lo recupera tras un cierre); estado en `SessionState` (Db.kt). Idioma: `Lang.wrap` en `attachBaseContext`.
- Sonidos: `./generar-sonidos.sh` (ffmpeg) → `res/raw/`.
