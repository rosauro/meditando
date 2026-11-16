# Meditando (app Android)
Temporizador de meditación: duración, campanas intermedias suaves y bong final; presets e historial. Sin red ni anuncios.
- Entorno: `source /datos/android-sdk/env.sh` (JDK 21, Gradle, SDK en /datos/android-sdk; se instala con `./instalar-toolchain.sh`, sin root).
- Compilar y probar: `gradle --no-daemon testDebugUnitTest assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
- Sonidos: `./generar-sonidos.sh` (ffmpeg) → `app/src/main/res/raw/`.
- Kotlin sin dependencias externas; UI por código; SQLite nativo (`Db.kt`). Lógica pura y testeada en `Schedule.kt` y `Stats.kt`.
- Servicio en primer plano: `MeditationService.kt`; estado compartido en `SessionState` (Db.kt).
