#!/usr/bin/env bash
# Builds Realms of Chaos for Fabric and Forge (Minecraft 26.3) and packs dist/*.zip
# Needs JDK 25 (set JAVA_HOME). Forge's tooling additionally wants a JDK 8 installed locally.
set -euo pipefail
cd "$(dirname "$0")"
VERSION="$(tr -d '[:space:]' < VERSION)"
MC="26.3"
python3 tools/generate.py
(cd fabric && ./gradlew --no-daemon build)
(cd forge  && ./gradlew --no-daemon build)
OUT="dist/RealmsOfChaos-${VERSION}-mc${MC}"
rm -rf "$OUT" && mkdir -p "$OUT"
cp fabric/build/libs/realms-of-chaos-fabric-${VERSION}+mc${MC}.jar "$OUT/"
cp forge/build/libs/realms-of-chaos-forge-${VERSION}+mc${MC}.jar "$OUT/"
cp README.md LICENSE "$OUT/"
(cd dist && rm -f "RealmsOfChaos-${VERSION}-mc${MC}.zip" && zip -qr "RealmsOfChaos-${VERSION}-mc${MC}.zip" "RealmsOfChaos-${VERSION}-mc${MC}")
rm -rf "$OUT"
echo "Built dist/RealmsOfChaos-${VERSION}-mc${MC}.zip"
