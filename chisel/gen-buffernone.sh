#!/bin/bash

#ocmdepth="1024 2048 4096 8192 16384 32768"
issuew="2 4 8 16 32"

for i in $issuew; do
  echo "Spawning issueWindow $i"
  sbt "run inst BufferNone --issueWindow $i"
done

wait
echo "Verilog generation complete"
cd ..
