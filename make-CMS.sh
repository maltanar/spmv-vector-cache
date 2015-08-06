#!/bin/bash

vpath="/home/maltanar/sandbox/spmv-vector-cache/chisel/verilogOutput"
ocmdepth="1024 2048 4096 8192 16384 32768"
#ocmdepth="1024 2048"

for d in $ocmdepth; do
  prj="NewCache-cms-$d-6"
  ./createSynth.sh "$prj" "$vpath/$prj/AXIAccelWrapper.v" > "logOutput/ProjGen-$prj.log"
done

echo "Projects created, running synthesis..."

cnt=0
limit=2

for d in $ocmdepth; do
  prj="NewCache-cms-$d-6"
  echo "Launching $prj synthesis"
  ./runSynth.sh $prj > "logOutput/ProjSynth-$prj-log" &
  cnt=$((cnt+1))
  if [ "$cnt" -ge "$limit" ]; then
    echo "Job limit reached, waiting for completion..."
    wait
    cnt=$((cnt-1))
  fi
done

wait
echo "All commands finished"
