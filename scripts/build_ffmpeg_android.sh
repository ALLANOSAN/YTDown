#!/bin/bash
# Script para baixar o FFmpeg estático pré-compilado para Android arm64-v8a
# e instalá-lo no diretório jniLibs
#
# Uso: bash scripts/build_ffmpeg_android.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
JNILIBS_DIR="$PROJECT_DIR/android/app/src/main/jniLibs"

echo "=========================================="
echo "  FFmpeg para Android - Fast Setup"
echo "=========================================="
echo ""

# URL do binário pré-compilado
URL="https://github.com/Tyrrrz/FFmpegBin/releases/latest/download/ffmpeg-android-arm64.zip"
WORKDIR="/tmp/ffmpeg-android-setup"

rm -rf "$WORKDIR"
mkdir -p "$WORKDIR"
cd "$WORKDIR"

echo "📥 Baixando FFmpeg estático (arm64-v8a) do GitHub..."
curl -L -o ffmpeg.zip "$URL"

echo "📦 Extraindo..."
unzip -q ffmpeg.zip

if [ ! -f "ffmpeg" ]; then
    echo "❌ Erro: binário 'ffmpeg' não encontrado no zip."
    exit 1
fi

# Copiar para jniLibs com o nome especial exigido pelo SELinux
echo "🚚 Instalando no projeto Android..."
mkdir -p "$JNILIBS_DIR/arm64-v8a"

echo "🚚 Removendo binários antigos fprobe para evitar conflitos com placeholders (fakes) de execuções anteriores"
rm -f "$JNILIBS_DIR/arm64-v8a/libffprobe_exe.so"

cp ffmpeg "$JNILIBS_DIR/arm64-v8a/libffmpeg_exe.so"

if [ -f "ffprobe" ]; then
    cp ffprobe "$JNILIBS_DIR/arm64-v8a/libffprobe_exe.so"
    chmod 755 "$JNILIBS_DIR/arm64-v8a/libffprobe_exe.so"
    echo "✅ ffprobe instalado como libffprobe_exe.so"
else
    # Não instalamos um ffprobe falso. Se yt-dlp precisar, ele usará ffmpeg como fallback
    # ou pulará a etapa de ffprobe.
    echo "⚠️ ffprobe não encontrado. Não será instalado um placeholder."
fi
chmod +x "$JNILIBS_DIR/arm64-v8a/libffmpeg_exe.so"
chmod 755 "$JNILIBS_DIR/arm64-v8a/libffmpeg_exe.so"

# Atualiza o asset que o BinaryOrchestrator extrai em runtime.
ASSETS_BINARIES_DIR="$PROJECT_DIR/android/app/src/main/assets/binaries"
mkdir -p "$ASSETS_BINARIES_DIR"
cp ffmpeg "$ASSETS_BINARIES_DIR/ffmpeg"
chmod +x "$ASSETS_BINARIES_DIR/ffmpeg"
chmod 755 "$ASSETS_BINARIES_DIR/ffmpeg"

# Instala libc++_shared.so para que o ffmpeg dinâmico consiga resolver dependências.
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/home/allanosan/android-sdk}}"
NDK_ROOT=""
if [ -d "$SDK_ROOT/ndk" ]; then
    NDK_ROOT=$(find "$SDK_ROOT/ndk" -maxdepth 1 -mindepth 1 -type d | sort -V | tail -n 1 || true)
fi
if [ -z "$NDK_ROOT" ] && [ -d "/home/allanosan/android-sdk/ndk" ]; then
    NDK_ROOT=$(find "/home/allanosan/android-sdk/ndk" -maxdepth 1 -mindepth 1 -type d | sort -V | tail -n 1 || true)
fi
LIBCXX_PATH=""
if [ -n "$NDK_ROOT" ]; then
    LIBCXX_PATH="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so"
fi
if [ -n "$LIBCXX_PATH" ] && [ -f "$LIBCXX_PATH" ]; then
    cp "$LIBCXX_PATH" "$JNILIBS_DIR/arm64-v8a/libc++_shared.so"
    chmod 755 "$JNILIBS_DIR/arm64-v8a/libc++_shared.so"
    cp "$LIBCXX_PATH" "$ASSETS_BINARIES_DIR/libc++_shared.so"
    chmod 755 "$ASSETS_BINARIES_DIR/libc++_shared.so"
    echo "🚚 Instalado libc++_shared.so de $LIBCXX_PATH"
else
    echo "⚠️ libc++_shared.so não encontrado no NDK. Verifique ANDROID_SDK_ROOT ou ANDROID_HOME." >&2
fi

echo ""
echo "✅ Instalado em:"
echo "   $JNILIBS_DIR/arm64-v8a/libffmpeg_exe.so"
echo "   $ASSETS_BINARIES_DIR/ffmpeg"
if [ -f "$JNILIBS_DIR/arm64-v8a/libc++_shared.so" ]; then
    echo "   $JNILIBS_DIR/arm64-v8a/libc++_shared.so"
    echo "   $ASSETS_BINARIES_DIR/libc++_shared.so"
fi

echo ""
echo "=========================================="
echo "  ✅ PRONTO! Agora execute './build.sh debug install' e teste no celular!"
echo "=========================================="

# Limpeza
rm -rf "$WORKDIR"
