#!/bin/bash
# Script para build com secrets locais
# Uso: ./build.sh [debug|release] [install]

set -e

BUILD_TYPE=${1:-debug}
INSTALL=${2:-false}
SECRETS_FILE=".secrets.json"

if [ ! -f "$SECRETS_FILE" ]; then
  echo "❌ Erro: Arquivo $SECRETS_FILE não encontrado!"
  echo "📋 Copie .secrets.example.json para .secrets.json e preencha suas chaves:"
  echo "   cp .secrets.example.json .secrets.json"
  exit 1
fi

echo "🔐 Lendo secrets de $SECRETS_FILE..."
echo "🚀 Iniciando build $BUILD_TYPE..."

if [ "$BUILD_TYPE" = "release" ]; then
  flutter build apk --dart-define-from-file=$SECRETS_FILE 2>&1 | grep -v "Gradle build failed" || true
fi

if [ "$BUILD_TYPE" != "release" ]; then
  flutter build apk --debug --dart-define-from-file=$SECRETS_FILE 2>&1 | grep -v "Gradle build failed" || true
fi

# Verifica se o APK foi gerado (apesar da mensagem de warning)
APK_PATH="build/app/outputs/flutter-apk/app-${BUILD_TYPE}.apk"
if [ -f "$APK_PATH" ]; then
  echo "✅ Build concluído com sucesso!"
  echo "📦 APK: $APK_PATH"
  
  # Instala automaticamente se solicitado
  if [ "$INSTALL" = "install" ] || [ "$INSTALL" = "-i" ]; then
    echo "📱 Instalando no dispositivo..."
    adb install -r "$APK_PATH"
    echo "✅ Instalação concluída!"
    exit 0
  fi

  echo "💡 Para instalar: ./build.sh debug install"
  exit 0
fi

echo "⚠️  Aviso: APK não encontrado em $APK_PATH"
echo "   Verifique os logs acima para erros reais."
exit 1
