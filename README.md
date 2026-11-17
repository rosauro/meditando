# Meditando

**Español** · [English](#english)

Temporizador de meditación sencillo para Android. Sin anuncios, sin cuentas, sin donativos.

- Eliges la **duración**, cuántas **campanas intermedias** quieres (suaves) y suena un **bong final**.
- Cada campana puede llevar un **texto** (por ejemplo, el mudra o el mantra del tramo): pega la lista, una por línea o separadas por `;`, y la app calcula cuántas campanas necesita.
- **Aviso**: solo bongs, solo vibración o ambos; volumen independiente para el bong final y las campanas.
- **Preselecciones** (por ejemplo «Fijación 5 min», «Relajación 20 min», «Kuji-in 60 min con 9 campanas») e **historial** semanal y mensual.
- Funciona con la pantalla apagada (servicio en primer plano, alarmas exactas y recuperación si el sistema cierra la app).
- Interfaz en **español** (por defecto) o **inglés** (Ajustes → Idioma).

## Instalar

1. Descarga el último `meditando-vX.Y.apk` desde [Releases](../../releases/latest).
2. Ábrelo en el móvil y permite «instalar apps desconocidas» al navegador o gestor de archivos que uses.
3. En Ajustes → *Fiabilidad* deja en verde batería, notificaciones y alarmas exactas. En Xiaomi, Oppo, Samsung… activa también el inicio automático y no la cierres desde recientes.

Requiere Android 8.0 o superior.

## Actualizaciones

Fuera de Google Play Android no permite actualizar del todo en silencio. Meditando consulta cada 24 h las *Releases* de este repositorio (se puede desactivar en Ajustes → Actualizaciones), descarga el APK, **verifica su SHA-256** y lo instala. La primera vez pedirá una confirmación; en Android 12+ las siguientes pueden hacerse sin pedirla, según el móvil. Nunca se instala durante una meditación. También puedes usar [Obtainium](https://github.com/ImranR98/Obtainium) apuntando a este repositorio.

## Permisos

| Permiso | Para qué |
|---|---|
| Servicio en primer plano, notificaciones, wake lock | Mantener el temporizador con la pantalla apagada |
| Alarmas exactas | Que cada campana suene a su hora |
| Vibración | Aviso por vibración |
| Internet | Solo para buscar actualizaciones en GitHub |
| Instalar paquetes | Instalar las actualizaciones |
| Inicio del sistema | Reprogramar la búsqueda de actualizaciones tras reiniciar |

Todos los datos (preselecciones, historial) se quedan en el móvil. No hay telemetría.

## Compilar

Necesitas JDK 17+ y el Android SDK (`platforms;android-35`, `build-tools;35.0.0`). Sin root, `./instalar-toolchain.sh` los deja en `/datos/android-sdk` (edita la ruta si quieres otra).

```bash
export ANDROID_HOME=/ruta/al/sdk
./gradlew testDebugUnitTest assembleDebug   # app/build/outputs/apk/debug/app-debug.apk
```

Para publicar una versión propia hace falta tu propia clave de firma (`keystore.properties`, ver `app/build.gradle.kts`) y `./publicar.sh X.Y`. **Un APK solo se actualiza sobre otro firmado con la misma clave**: guarda bien tu `.jks`.

Los sonidos se generan con `./generar-sonidos.sh` (ffmpeg): síntesis propia, sin copyright.

## Licencia

[GPL-3.0](LICENSE).

---

## English

A simple meditation timer for Android. No ads, no accounts, no donations.

- Pick the **duration**, how many soft **interval bells** you want, and a **final bong** rings at the end.
- Each bell can carry a **text** (e.g. the mudra or mantra for that segment): paste the list, one per line or separated by `;`, and the app works out how many bells it needs.
- **Alert mode**: bongs only, vibration only or both; separate volume for the final bong and the interval bells.
- **Presets** and a weekly/monthly **history**.
- Works with the screen off (foreground service, exact alarms, recovery if the system kills the app).
- Interface in **Spanish** (default) or **English** (Settings → Language).

### Install

1. Download the latest `meditando-vX.Y.apk` from [Releases](../../releases/latest).
2. Open it on your phone and allow "install unknown apps" for your browser or file manager.
3. In Settings → *Reliability*, turn battery, notifications and exact alarms green. On Xiaomi, Oppo, Samsung… also enable autostart and don't swipe the app away from recents.

Requires Android 8.0+.

### Updates

Outside Google Play, Android doesn't allow fully silent updates. Meditando checks this repository's *Releases* every 24 h (can be turned off in Settings → Updates), downloads the APK, **verifies its SHA-256** and installs it. The first time it asks for a confirmation; on Android 12+ later updates may skip it, depending on the phone. It never installs during a meditation. You can also use [Obtainium](https://github.com/ImranR98/Obtainium) pointed at this repo.

### Privacy

Everything (presets, history) stays on your phone. No telemetry. The only network access is checking GitHub for updates.

### Build

JDK 17+ and the Android SDK are required; then `./gradlew testDebugUnitTest assembleDebug`. To publish your own build you need your own signing key (`keystore.properties`) and `./publicar.sh X.Y`. **An APK can only update one signed with the same key.** Sounds are synthesised with `./generar-sonidos.sh` (ffmpeg), no copyright.

### License

[GPL-3.0](LICENSE).
