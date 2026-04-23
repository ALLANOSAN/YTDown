#!/bin/bash
# Script para build nativo Android com suporte a comparação (Side-by-Side) e Secrets
# Uso: ./build.sh [debug|release] [install]

set -e

BUILD_TYPE=${1:-debug}
INSTALL=${2:-false}
SECRETS_FILE=".secrets.json"

# Validação do arquivo de secrets (Obrigatório para o LastFM)
if [ ! -f "$SECRETS_FILE" ]; then
  echo "❌ Erro: Arquivo $SECRETS_FILE não encontrado!"
  echo "📋 Copie .secrets.example.json para .secrets.json e preencha suas chaves (LastFM, etc):"
  echo "   cp .secrets.example.json .secrets.json"
  exit 1
fi

echo "🔐 Lendo secrets de $SECRETS_FILE..."
echo "🚀 Iniciando build nativo ($BUILD_TYPE)..."

# Entra na pasta android para rodar o gradle
cd android

if [ "$BUILD_TYPE" = "release" ]; then
  ./gradlew assembleRelease
  APK_PATH="app/build/outputs/apk/release/app-release.apk"
else
  ./gradlew assembleDebug
  APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
fi

cd ..

# Verifica se o APK foi gerado
if [ -f "android/$APK_PATH" ]; then
  echo "✅ Build nativo concluído com sucesso!"
  echo "📦 APK: android/$APK_PATH"
  
  # Instala automaticamente se solicitado
  if [ "$INSTALL" = "install" ] || [ "$INSTALL" = "-i" ]; then
    echo "📱 Instalando versão nativa (Package ID com sufixo .native)..."
    adb install -r "android/$APK_PATH"
    echo "✅ Instalação concluída! Procure pelo app com '-native' no nome."
    exit 0
  fi

  echo "💡 Para instalar: ./build.sh debug install"
  exit 0
fi

echo "⚠️  Aviso: APK não encontrado em android/$APK_PATH"
echo "   Verifique os logs acima para erros de compilação."
exit 1
