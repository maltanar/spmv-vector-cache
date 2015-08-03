#!/bin/sh

TARGET="/home/maltanar/sandbox/spmv-vector-cache/sdk/spmvaccel/procsys_wrapper.bit"
BITFILE=$1

if [ ! -e $BITFILE ]; then
  echo "Bitfile not found: $BITFILE"
  exit -1
fi

echo "Switching to bitfile: $BITFILE"
cp -f $BITFILE $TARGET
