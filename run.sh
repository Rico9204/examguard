#!/bin/sh
# 컴파일 후 실행 (macOS / Linux)
cd "$(dirname "$0")" || exit 1
mkdir -p out
javac -encoding UTF-8 -d out $(find src -name "*.java") || exit 1
java -cp out examguard.Main
