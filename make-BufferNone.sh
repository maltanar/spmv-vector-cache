#!/bin/bash

vpath="/home/maltanar/sandbox/spmv-vector-cache/chisel/verilogOutput"
#ocmdepth="1024 2048 4096 8192 16384 32768"
#ocmdepth="1024 2048"
iw="2 4 8 16 32"

for i in $iw; do
  prj="BufferNone-1024-$i"
  ./createSynth.sh "$prj" "$vpath/$prj/AXIAccelWrapper.v" > "logOutput/ProjGen-$prj.log"
done

echo "Projects created, running synthesis..."

cnt=0
limit=2

for i in $iw; do
  prj="BufferNone-1024-$i"
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
