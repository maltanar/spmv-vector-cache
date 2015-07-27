#!/bin/sh

ACCEL_VARIANT=$1

CHISEL_SRC_DIR="/home/maltanar/sandbox/spmv-vector-cache/chisel"
COMP_SYNTH_DIR="/home/maltanar/sandbox/spmv-vector-cache/vivado-setup/$ACCEL_VARIANT/comp"
SYS_SYNTH_DIR="/home/maltanar/sandbox/spmv-vector-cache/vivado-setup/$ACCEL_VARIANT/sys"
LOG_DIR="/home/maltanar/sandbox/spmv-vector-cache/logOutput"

ORIG_DIR="$(pwd)"

mkdir -p $LOG_DIR

echo "Generating Verilog and register driver..."
cd $CHISEL_SRC_DIR
sbt "run inst SpMVAccel-$ACCEL_VARIANT" > $LOG_DIR/gen-verilog.log
sbt "run driver SpMVAccel-$ACCEL_VARIANT" > $LOG_DIR/gen-regdriver.log
mv -f "driverOutput/SpMVAccelerator${ACCEL_VARIANT}Driver.hpp" "../software/"

echo "Running component synthesis..."
cd $COMP_SYNTH_DIR
./run-synth.sh > $LOG_DIR/synth-comp.log

echo "Running system synthesis..."
cd $SYS_SYNTH_DIR
./synth-and-update.sh $ACCEL_VARIANT > $LOG_DIR/synth-sys.log

cd $ORIG_DIR
echo "$0 finished"
