#!/usr/bin/env bash
# Собирает ядро Xray (libXray) в app/libs/libXray.aar через gomobile.
#
# Нужно: Go, git, Android SDK и NDK (переменные ANDROID_HOME и ANDROID_NDK_HOME).
# Обычно это делает GitHub Actions; локально скрипт нужен только для сборки без CI.
set -euo pipefail

LIBXRAY_VERSION="${LIBXRAY_VERSION:-v26.10.10}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/build/libxray-src"
OUT="$ROOT/app/libs"

export GOTOOLCHAIN=auto
export PATH="$(go env GOPATH)/bin:$PATH"

if [ ! -d "$SRC/.git" ]; then
  git clone --depth 1 --branch "$LIBXRAY_VERSION" https://github.com/XTLS/libXray "$SRC"
fi
mkdir -p "$OUT"
cd "$SRC"

# gomobile той же версии, что x/mobile в go.mod libXray
go install golang.org/x/mobile/cmd/gomobile golang.org/x/mobile/cmd/gobind
gomobile init

# Только ARM: этого достаточно для телефонов и вдвое быстрее полной сборки.
# max-page-size=16384 — требование Android 15 к нативным библиотекам.
gomobile bind -target android/arm64,android/arm -androidapi 21 -trimpath \
  -ldflags="-checklinkname=0 -s -w -buildid= -extldflags=-Wl,-z,max-page-size=16384" \
  -o "$OUT/libXray.aar" .

echo "==> Готово: $OUT/libXray.aar"
