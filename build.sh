#!/bin/bash
set -e

# VoxelPort - Linux Build Script
# Usage: ./build.sh

# Ensure Java 17+ is installed
if ! type javac > /dev/null 2>&1; then
    echo "Error: javac not found. Install JDK 17+ (e.g., sudo apt install openjdk-17-jdk)"
    exit 1
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD="$ROOT/build"
CLASSES="$BUILD/classes"
INPUT="$BUILD/input"
DIST="$ROOT/dist"
JAR="$INPUT/VoxelPort.jar"

echo "Cleaning old build files..."
rm -rf "$BUILD" "$DIST"
mkdir -p "$CLASSES" "$INPUT" "$DIST"

echo "Compiling Java sources..."
find "$ROOT/src/main/java" -name "*.java" > sources.txt
javac --release 17 -encoding UTF-8 -d "$CLASSES" @sources.txt
rm sources.txt

echo "Creating JAR..."
jar --create --file "$JAR" --main-class org.localm.LocalMJava -C "$CLASSES" .

echo "Building runtime image (jlink)..."
RUNTIME="$BUILD/runtime"
jlink --add-modules java.desktop,java.net.http,java.logging,jdk.crypto.ec,jdk.zipfs --output "$RUNTIME" --strip-debug --no-header-files --no-man-pages

# Note: jpackage is OS-specific. On Linux it creates .deb, .rpm or app-image.
# For a server, the JAR is usually enough.
echo "Creating app image..."
jpackage \
  --type app-image \
  --name "VoxelPort" \
  --input "$INPUT" \
  --main-jar "VoxelPort.jar" \
  --main-class "org.localm.LocalMJava" \
  --runtime-image "$RUNTIME" \
  --dest "$DIST" \
  --app-version "0.1.0"

# Copy binary folder (instructions will be provided for Linux binaries)
mkdir -p "$DIST/VoxelPort/bin"
if [ -d "$ROOT/bin" ]; then
    cp -r "$ROOT/bin/"* "$DIST/VoxelPort/bin/"
fi

echo "------------------------------------------------"
echo "Build Complete!"
echo "To run on Ubuntu Server (Headless):"
echo "cd '$DIST/VoxelPort'"
echo "./bin/java -jar VoxelPort.jar --list"
echo "./bin/java -jar VoxelPort.jar --start \"ServerName\""
echo "------------------------------------------------"
