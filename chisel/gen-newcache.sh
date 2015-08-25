#!/bin/bash

ocmdepth="1024 2048 4096 8192 16384 32768"
mlp="1 2 4 8 16"

for d in $ocmdepth; do
  for m in $mlp; do
    echo "Spawning ocmDepth $d mlp $m"
    sbt "run inst NewCache --enableCMS --enableNB --ocmDepth $d --maxMiss $m --issueWindow $m"
  done
done

wait
echo "Verilog generation complete"

