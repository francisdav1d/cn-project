#!/bin/sh
# Compiles and starts the simulator.  ./run.sh               -> GUI
#                                     ./run.sh --experiment  -> headless comparison
cd "$(dirname "$0")" || exit 1
mkdir -p out
javac -encoding UTF-8 -d out -sourcepath src src/arq/Main.java || exit 1
java -cp out arq.Main "$@"
