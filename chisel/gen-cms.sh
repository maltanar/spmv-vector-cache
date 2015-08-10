#!/bin/bash

ocmdepth="1024 2048 4096 8192 16384 32768"
iw="6 8 10"

for d in $ocmdepth; do
  for i in $iw; do
    echo "Spawning ocmDepth $d $i"
    sbt "run inst NewCache --ocmDepth $d --issueWindow $i --enableCMS"
  done
done

wait
echo "Verilog generation complete"
cd ..
