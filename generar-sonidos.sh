#!/usr/bin/env bash
# generar-sonidos.sh — genera los sonidos de cuenco/gong con ffmpeg (sin copyright)
# Uso: ./generar-sonidos.sh   (escribe en app/src/main/res/raw/)
# Notas: sin sudo, sin internet; requiere ffmpeg.
set -euo pipefail
fail(){ echo "[error] $*" >&2; exit 1; }
command -v ffmpeg >/dev/null || fail "Falta ffmpeg"
OUT="$(dirname "$0")/app/src/main/res/raw"
mkdir -p "$OUT"

# parcial(frecuencia, amplitud, decaimiento): senoide con envolvente exponencial
# Bong final: grave y largo (parciales inarmónicos de cuenco), 9 s
ffmpeg -y -loglevel error \
 -f lavfi -i "aevalsrc='0.50*sin(2*PI*196*t)*exp(-t/2.6)+0.30*sin(2*PI*196*2.71*t)*exp(-t/1.6)+0.18*sin(2*PI*196*5.4*t)*exp(-t/0.9)+0.10*sin(2*PI*196*8.9*t)*exp(-t/0.5)+0.12*sin(2*PI*198*t)*exp(-t/2.2)':d=9:s=44100" \
 -af "afade=t=in:d=0.005,volume=1.4,alimiter=limit=0.95" -ac 1 "$OUT/bong_final.wav" || fail "bong_final"

# Bong suave: más agudo, más corto y más bajo, 4,5 s
ffmpeg -y -loglevel error \
 -f lavfi -i "aevalsrc='0.45*sin(2*PI*528*t)*exp(-t/1.3)+0.20*sin(2*PI*528*2.76*t)*exp(-t/0.8)+0.08*sin(2*PI*528*5.4*t)*exp(-t/0.4)':d=4.5:s=44100" \
 -af "afade=t=in:d=0.02,volume=0.55,afade=t=out:st=4:d=0.5" -ac 1 "$OUT/bong_suave.wav" || fail "bong_suave"
echo "[ok] sonidos en $OUT"
