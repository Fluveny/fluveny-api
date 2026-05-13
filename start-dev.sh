#!/bin/bash

# Navega para o diretório do script (fluveny-api)
cd "$(dirname "$0")"

echo "=== Iniciando Fluveny API em modo DEV ==="

# Carrega as variáveis de ambiente do .env
if [ -f .env ]; then
  set -a
  source .env
  set +a
  echo "✅ Variáveis de ambiente carregadas do arquivo .env"
else
  echo "❌ Arquivo .env não encontrado!"
  exit 1
fi

# Define o JAVA_HOME para o JDK instalado pelo IntelliJ (para evitar erro de compilação por falta do javac)
export JAVA_HOME="$HOME/.jdks/openjdk-25.0.2"
export PATH="$JAVA_HOME/bin:$PATH"

# Define o profile do Spring Boot para dev
export SPRING_PROFILES_ACTIVE=dev

# Verifica se a porta 8080 já está em uso para evitar conflitos
if lsof -Pi :8080 -sTCP:LISTEN -t >/dev/null ; then
    echo "⚠️  A porta 8080 já está em uso. Derrubando processo antigo..."
    kill -9 $(lsof -Pi :8080 -sTCP:LISTEN -t)
    sleep 2
fi

# Executa o Spring Boot em background usando o Maven Wrapper
echo "🚀 Iniciando a aplicação com Maven em background..."
nohup ./mvnw spring-boot:run > api.log 2>&1 &

PID=$!

echo "✅ API rodando em background! (PID: $PID)"
echo "📜 Você pode acompanhar os logs usando: tail -f api.log"
echo "🛑 Para parar a API futuramente, rode: kill $PID"
