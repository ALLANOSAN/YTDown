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

echo "✅ Binário extraído com sucesso:"
file ffmpeg || echo "Ferramenta 'file' não instalada, mas o arquivo existe."
ls -lh ffmpeg

# Copiar para jniLibs com o nome especial exigido pelo SELinux
echo "🚚 Instalando no projeto Android..."
mkdir -p "$JNILIBS_DIR/arm64-v8a"
cp ffmpeg "$JNILIBS_DIR/arm64-v8a/libffmpeg_exe.so"
chmod +x "$JNILIBS_DIR/arm64-v8a/libffmpeg_exe.so"

echo ""
echo "✅ Instalado em:"
echo "   $JNILIBS_DIR/arm64-v8a/libffmpeg_exe.so"

echo ""
echo "=========================================="
echo "  ✅ PRONTO! Agora faça 'flutter build apk' e teste no celular!"
echo "=========================================="

# Limpeza
rm -rf "$WORKDIR"
