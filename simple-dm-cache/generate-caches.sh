#!/bin/sh

SRC_DIR="/home/maltanar/sandbox/spmv-vector-cache/simple-dm-cache"
WORK_DIR="/home/maltanar/sandbox/build-verilator"
TARGET_DIR="/home/maltanar/sandbox/spmv-vector-cache/generated-systemc/baseline"
VERILATOR_DIR="/usr/share/verilator/include"

CACHE_DEPTHS="1024 2048 4096 8192 16384 32768 65536 131072"

export SYSTEMC_INCLUDE="/home/maltanar/systemc/include"
export SYSTEMC_LIBDIR="/home/maltanar/systemc/lib-linux64"


for depth in $CACHE_DEPTHS; do
  currentTargetDir="$TARGET_DIR/depth-$depth"
  echo "Now generating cache of depth $depth under $currentTargetDir"
  cd $SRC_DIR
  mkdir -p $WORK_DIR
  sbt "run --depth $depth --targetDir $WORK_DIR --backend v"
  cd $WORK_DIR
  verilator --sc SimpleDMVectorCache.v +define+SYNTHESIS+1 -Wno-lint
  mkdir -p $currentTargetDir
  cp -f obj_dir/*.cpp $currentTargetDir/
  cp -f obj_dir/*.h $currentTargetDir/
  cd $SRC_DIR
  rm -rf $WORK_DIR
done

