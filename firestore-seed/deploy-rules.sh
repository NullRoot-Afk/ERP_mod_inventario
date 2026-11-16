#!/bin/sh
# Publica firestore.rules en el proyecto real usando la clave de servicio.
# Uso (desde cualquier carpeta):  sh firestore-seed/deploy-rules.sh
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export GOOGLE_APPLICATION_CREDENTIALS="$ROOT/firestore-seed/service-account.json"
cd "$ROOT"
firestore-tests/node_modules/.bin/firebase deploy --only firestore:rules --project proyecto-inventario-25dbd
