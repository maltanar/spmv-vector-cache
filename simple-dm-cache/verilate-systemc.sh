#!/bin/sh

SRC_DIR="/home/maltanar/sandbox/spmv-vector-cache/simple-dm-cache"
WORK_DIR="/home/maltanar/sandbox/build-verilator"
TARGET_DIR="/home/maltanar/sandbox/spmv-cache-systemc"

export SYSTEMC_INCLUDE="/home/maltanar/systemc/include"
export SYSTEMC_LIBDIR="/home/maltanar/systemc/lib-linux64"

mkdir -p $WORK_DIR
mkdir -p $TARGET_DIR
cd $SRC_DIR
sbt "run --targetDir $WORK_DIR --backend v"
cd $WORK_DIR
verilator --sc SimpleDMVectorCache.v +define+SYNTHESIS+1 -Wno-lint
cp obj_dir/*.cpp $TARGET_DIR/
cp obj_dir/*.h $TARGET_DIR/
cd $SRC_DIR
rm -rf $WORK_DIR
