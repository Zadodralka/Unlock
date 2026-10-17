#!/usr/bin/env bash
# Собирает ядро Xray (libXray) в Frameworks/LibXray.xcframework.
#
# Нужно: macOS, Xcode (с командными утилитами), Go (brew install go), git.
# Нужную версию Go скрипт скачает сам (GOTOOLCHAIN=auto).
#
# Собираются только iPhone (arm64) и симулятор на Apple Silicon (arm64):
# tvOS и macOS приложению не нужны, а их SDK часто не установлены.
set -euo pipefail

LIBXRAY_VERSION="${LIBXRAY_VERSION:-v26.10.10}"
MIN_IOS="16.0"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$ROOT/build/libxray"
SRC="$WORK/src"
OUT="$ROOT/Frameworks/LibXray.xcframework"

export GOTOOLCHAIN=auto
export CGO_ENABLED=1

mkdir -p "$WORK"
if [ ! -d "$SRC/.git" ]; then
  git clone --depth 1 --branch "$LIBXRAY_VERSION" https://github.com/XTLS/libXray "$SRC"
else
  git -C "$SRC" fetch --depth 1 origin "refs/tags/$LIBXRAY_VERSION:refs/tags/$LIBXRAY_VERSION"
  git -C "$SRC" checkout -q "$LIBXRAY_VERSION"
fi

# build_slice <sdk> <arch> — статическая библиотека c-archive для одного среза
build_slice() {
  local sdk="$1" arch="$2"
  local dir="$WORK/$sdk-$arch"
  local sdk_path flags
  sdk_path="$(xcrun --sdk "$sdk" --show-sdk-path)"
  flags="-isysroot $sdk_path -m$sdk-version-min=$MIN_IOS -arch $arch"
  mkdir -p "$dir"
  echo "==> libXray: $sdk/$arch"
  (
    cd "$SRC"
    GOOS=ios GOARCH=arm64 \
    CC="$(xcrun --sdk "$sdk" --find clang)" \
    CXX="$(xcrun --sdk "$sdk" --find clang++)" \
    CGO_CFLAGS="$flags" CGO_CXXFLAGS="$flags" \
    CGO_LDFLAGS="$flags -Wl,-Bsymbolic-functions" \
      go build -trimpath -ldflags "-s -w -buildid=" -buildmode=c-archive \
        -o "$dir/libXray.a" ./cgo_bridge
  )
}

# make_framework <sdk> <arch> — заворачивает .a и заголовок в статический
# LibXray.framework, чтобы Swift мог делать `import LibXray`
make_framework() {
  local sdk="$1" arch="$2"
  local dir="$WORK/$sdk-$arch"
  local fw="$dir/LibXray.framework"
  rm -rf "$fw"
  mkdir -p "$fw/Headers" "$fw/Modules"
  cp "$dir/libXray.a" "$fw/LibXray"
  cp "$dir/libXray.h" "$fw/Headers/libXray.h"
  cat > "$fw/Modules/module.modulemap" <<'EOF'
framework module LibXray {
  umbrella header "libXray.h"
  export *
  module * { export * }
}
EOF
  cat > "$fw/Info.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleExecutable</key><string>LibXray</string>
  <key>CFBundleIdentifier</key><string>com.github.xtls.libxray</string>
  <key>CFBundleName</key><string>LibXray</string>
  <key>CFBundlePackageType</key><string>FMWK</string>
  <key>CFBundleShortVersionString</key><string>${LIBXRAY_VERSION#v}</string>
  <key>CFBundleVersion</key><string>1</string>
  <key>MinimumOSVersion</key><string>$MIN_IOS</string>
</dict>
</plist>
EOF
}

build_slice iphoneos arm64
make_framework iphoneos arm64
args=(-framework "$WORK/iphoneos-arm64/LibXray.framework")

if [ "${SKIP_SIMULATOR:-0}" != "1" ]; then
  build_slice iphonesimulator arm64
  make_framework iphonesimulator arm64
  args+=(-framework "$WORK/iphonesimulator-arm64/LibXray.framework")
fi

rm -rf "$OUT"
xcodebuild -create-xcframework "${args[@]}" -output "$OUT"
echo "==> Готово: $OUT"
