#!/bin/bash

ocmdepth="1024 2048 4096 8192 16384 32768 65536"

for d in $ocmdepth; do
  echo "Spawning ocmDepth $d"
  sbt "run inst BufferAll --ocmDepth $d"
done

wait
echo "Verilog generation complete"
cd ..
