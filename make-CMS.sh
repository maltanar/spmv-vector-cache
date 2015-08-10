#!/bin/bash

vpath="/home/maltanar/sandbox/spmv-vector-cache/chisel/verilogOutput"
ocmdepth="1024 2048 4096 8192 16384 32768"
iw="6 8 10"

echo "Creating projects..."

for d in $ocmdepth; do
  for i in $iw; do
    prj="NewCache-cms-$d-$i"
    ./createSynth.sh "$prj" "$vpath/$prj/AXIAccelWrapper.v" > "logOutput/ProjGen-$prj.log"
  done
done

echo "Projects created, running synthesis..."

cnt=0
limit=2

for d in $ocmdepth; do
  for i in $iw; do
    prj="NewCache-cms-$d-$i"
    echo "Launching $prj synthesis"
    ./runSynth.sh $prj > "logOutput/ProjSynth-$prj-log" &
    cnt=$((cnt+1))
    if [ "$cnt" -ge "$limit" ]; then
      echo "Job limit reached, waiting for completion..."
      wait
      cnt=$((cnt-1))
    fi  
  done
done

wait
echo "All commands finished"
