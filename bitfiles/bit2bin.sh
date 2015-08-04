#!/bin/bash

SRC_DIR=/home/maltanar/sandbox/spmv-vector-cache/bitfiles
DEST_DIR=/home/maltanar/sandbox/spmv-vector-cache/binfiles

mkdir -p $DEST_DIR

BITFILES=$(ls $SRC_DIR/*.bit)

for b in $BITFILES; do
  base=$(basename $b)
  t="$DEST_DIR/${base%.*}.bin"
  vivado -mode batch -source "bit2bin.tcl" -tclargs "$b" "$t"
done

