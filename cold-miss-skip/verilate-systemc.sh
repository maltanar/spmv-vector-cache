#!/bin/sh

SRC_DIR="/home/maltanar/sandbox/spmv-vector-cache/cold-miss-skip"
WORK_DIR="/home/maltanar/sandbox/build-verilator"
TARGET_DIR="/home/maltanar/sandbox/spmvaccsim/vector-cache-src"
VERILATOR_DIR="/usr/share/verilator/include"

export SYSTEMC_INCLUDE="/home/maltanar/systemc/include"
export SYSTEMC_LIBDIR="/home/maltanar/systemc/lib-linux64"

mkdir -p $WORK_DIR
mkdir -p $TARGET_DIR
cd $SRC_DIR
sbt "run --targetDir $WORK_DIR --backend v"
cd $WORK_DIR
verilator --sc ColdMissSkipVectorCache.v +define+SYNTHESIS+1 -Wno-lint
cp -f obj_dir/*.cpp $TARGET_DIR/
cp -f obj_dir/*.h $TARGET_DIR/
cp -f $VERILATOR_DIR/verilated.cpp $TARGET_DIR

cd $SRC_DIR
rm -rf $WORK_DIR
