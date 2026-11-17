#!/usr/bin/env bash
# publicar.sh — compila, firma y publica una release de Meditando en GitHub
# Uso: ./publicar.sh X.Y[.Z]      (p. ej. ./publicar.sh 1.2)
# Notas: necesita internet, `gh` autenticado, keystore.properties (clave de firma) y el
#        Android SDK (ANDROID_HOME o /datos/android-sdk/env.sh). Sin sudo.
# Pasos encadenados: se detiene al primer fallo (set -e).
set -euo pipefail

info(){ printf '\033[32m[ok]\033[0m %s\n' "$*"; }
skip(){ printf '\033[33m[skip]\033[0m %s\n' "$*"; }
fail(){ printf '\033[31m[error]\033[0m %s\n' "$*" >&2; exit 1; }
step(){ printf '\n== %s ==\n' "$*"; }
need_cmd(){ command -v "$1" >/dev/null 2>&1 || fail "Falta el comando: $1"; }

cd "$(dirname "$0")"
VERSION="${1:-}"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+(\.[0-9]+)?$ ]] || fail "Uso: $0 X.Y[.Z]  (p. ej. 1.2)"
TAG="v$VERSION"
GRADLE_FILE=app/build.gradle.kts

step "Comprobaciones"
need_cmd git; need_cmd gh; need_cmd sha256sum
[ -f keystore.properties ] || fail "Falta keystore.properties (clave de firma, fuera del repositorio)"
if [ -f /datos/android-sdk/env.sh ]; then
  # shellcheck source=/dev/null
  source /datos/android-sdk/env.sh
  skip "Entorno cargado de /datos/android-sdk/env.sh"
fi
[ -n "${ANDROID_HOME:-}" ] || fail "ANDROID_HOME no está definido"
gh auth status >/dev/null 2>&1 || fail "gh no está autenticado (gh auth login)"
if git rev-parse "$TAG" >/dev/null 2>&1; then fail "La etiqueta $TAG ya existe"; fi
[ -z "$(git status --porcelain)" ] || fail "Hay cambios sin commitear: confirma antes de publicar"
info "Todo listo para publicar $TAG"

step "Versión"
CUR_NAME=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' "$GRADLE_FILE")
CUR_CODE=$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' "$GRADLE_FILE")
[ -n "$CUR_NAME" ] && [ -n "$CUR_CODE" ] || fail "No pude leer la versión de $GRADLE_FILE"
if [ "$CUR_NAME" = "$VERSION" ]; then
  skip "versionName ya es $VERSION (versionCode $CUR_CODE)"
else
  NEW_CODE=$((CUR_CODE + 1))
  sed -i "s/versionName = \".*\"/versionName = \"$VERSION\"/; s/versionCode = [0-9]*/versionCode = $NEW_CODE/" "$GRADLE_FILE" \
    || fail "No pude actualizar la versión"
  info "versionName $VERSION, versionCode $NEW_CODE"
fi

step "Tests y compilación firmada"
./gradlew --no-daemon -q testDebugUnitTest assembleRelease || fail "Fallo al compilar o en los tests"
APK_SRC=app/build/outputs/apk/release/app-release.apk
[ -f "$APK_SRC" ] || fail "No se generó $APK_SRC (¿keystore.properties válido?)"
"$ANDROID_HOME"/build-tools/35.0.0/apksigner verify "$APK_SRC" || fail "La firma del APK no es válida"
OUT=release
mkdir -p "$OUT"
APK="$OUT/meditando-$TAG.apk"
cp -f "$APK_SRC" "$APK"
( cd "$OUT" && sha256sum "meditando-$TAG.apk" > "meditando-$TAG.apk.sha256" ) || fail "No pude calcular el SHA-256"
info "APK firmado y verificado: $APK"

step "Commit, etiqueta y release en GitHub"
if [ -n "$(git status --porcelain "$GRADLE_FILE")" ]; then
  git add "$GRADLE_FILE"
  git commit -q -m "Versión $VERSION" || fail "No pude hacer commit"
fi
git tag "$TAG" || fail "No pude crear la etiqueta"
git push origin HEAD "$TAG" || fail "Fallo al hacer push"
gh release create "$TAG" "$APK" "$APK.sha256" --title "Meditando $VERSION" --generate-notes \
  || fail "Fallo al crear la release"
info "Release $TAG publicada"
