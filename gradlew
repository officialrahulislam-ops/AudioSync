#!/usr/bin/env bash
set -e

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ -d "gradle/wrapper" ]; then
    if [ -f "gradle/wrapper/gradle" ]; then
        "gradle/wrapper/gradle" "$@"
        exit $?
    fi
fi

if [ -f "gradlew.bat" ]; then
    echo "Run on Windows with gradlew.bat"
    exit 1
fi

echo "Error: Gradle not found. Please ensure gradle/wrapper/gradle exists."
exit 1
