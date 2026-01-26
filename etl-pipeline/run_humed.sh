#!/bin/bash
# run_humed.sh - build a spustenie ETL pipeline pre HUMED feed (lokálne, docker DB)

set -e  # skript skončí pri chybe

echo "🔹 Build ETL projektu..."
mvn clean package -DskipTests

echo "🔹 Spustenie manuálneho HUMED syncu..."
java -jar target/etl-pipeline-1.0.0-SNAPSHOT.jar --sync-humed

# Ak by si chcel spustiť scheduler daemon mode, odkomentuj:
# echo "🔹 Spustenie ETL daemon (scheduler)..."
# java -jar target/etl-pipeline-1.0.0-SNAPSHOT.jar

