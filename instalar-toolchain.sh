#!/usr/bin/env bash
# instalar-toolchain.sh — instala JDK 21, Gradle y Android SDK SIN root en /datos/android-sdk
# Uso: ./instalar-toolchain.sh
# Notas: sin sudo; necesita internet (~2-3 GB); idempotente.
set -euo pipefail

BASE=/datos/android-sdk
JDK_DIR=$BASE/jdk-21
GRADLE_VER=8.10.2
GRADLE_DIR=$BASE/gradle-$GRADLE_VER
SDK=$BASE/sdk
CMDLINE_ZIP=commandlinetools-linux-11076708_latest.zip

info(){ printf '\033[32m[ok]\033[0m %s\n' "$*"; }
skip(){ printf '\033[33m[skip]\033[0m %s\n' "$*"; }
warn(){ printf '\033[33m[aviso]\033[0m %s\n' "$*"; }
fail(){ printf '\033[31m[error]\033[0m %s\n' "$*" >&2; exit 1; }
step(){ printf '\n== %s ==\n' "$*"; }
need_cmd(){ command -v "$1" >/dev/null 2>&1 || fail "Falta el comando: $1"; }

for c in curl tar unzip; do need_cmd "$c"; done
mkdir -p "$BASE" "$SDK" || fail "No puedo crear $BASE"

step "JDK 21 (Temurin)"
if [ -x "$JDK_DIR/bin/javac" ]; then skip "JDK ya instalado"; else
  curl -fL --retry 3 -o "$BASE/jdk.tgz" \
    "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse" \
    || fail "Descarga del JDK fallida"
  mkdir -p "$JDK_DIR"
  tar -xzf "$BASE/jdk.tgz" -C "$JDK_DIR" --strip-components=1 \
    || fail "No pude extraer el JDK"
  rm -f "$BASE/jdk.tgz"; info "JDK instalado"
fi

step "Gradle $GRADLE_VER"
if [ -x "$GRADLE_DIR/bin/gradle" ]; then skip "Gradle ya instalado"; else
  curl -fL --retry 3 -o "$BASE/gradle.zip" \
    "https://services.gradle.org/distributions/gradle-$GRADLE_VER-bin.zip" \
    || fail "Descarga de Gradle fallida"
  unzip -q -o "$BASE/gradle.zip" -d "$BASE" || fail "No pude extraer Gradle"
  rm -f "$BASE/gradle.zip"; info "Gradle instalado"
fi

step "Android cmdline-tools"
SDKM=$SDK/cmdline-tools/latest/bin/sdkmanager
if [ -x "$SDKM" ]; then skip "cmdline-tools ya instaladas"; else
  curl -fL --retry 3 -o "$BASE/cmd.zip" "https://dl.google.com/android/repository/$CMDLINE_ZIP" \
    || fail "Descarga de cmdline-tools fallida"
  rm -rf "$BASE/tmp-cmd"; mkdir -p "$BASE/tmp-cmd" "$SDK/cmdline-tools"
  unzip -q "$BASE/cmd.zip" -d "$BASE/tmp-cmd" || fail "No pude extraer cmdline-tools"
  mv "$BASE/tmp-cmd/cmdline-tools" "$SDK/cmdline-tools/latest" || fail "No pude mover cmdline-tools"
  rm -rf "$BASE/tmp-cmd" "$BASE/cmd.zip"; info "cmdline-tools instaladas"
fi

step "Paquetes del SDK"
export JAVA_HOME=$JDK_DIR
export PATH=$JAVA_HOME/bin:$PATH
yes | "$SDKM" --sdk_root="$SDK" --licenses >/dev/null 2>&1 || true
for p in "platform-tools" "platforms;android-35" "build-tools;35.0.0"; do
  d=$SDK/${p//;//}
  [ "$p" = platform-tools ] && d=$SDK/platform-tools
  if [ -d "$d" ]; then skip "$p"; else
    "$SDKM" --sdk_root="$SDK" "$p" || fail "Fallo instalando $p"; info "$p"
  fi
done

step "Entorno"
cat > "$BASE/env.sh" <<ENV
export JAVA_HOME=$JDK_DIR
export ANDROID_HOME=$SDK
export ANDROID_SDK_ROOT=$SDK
export PATH=\$JAVA_HOME/bin:$GRADLE_DIR/bin:$SDK/platform-tools:\$PATH
ENV
info "Escrito $BASE/env.sh (haz: source $BASE/env.sh)"
