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

if [ ! -e $SYS_SYNTH_DIR ]; then
  echo "System synthesis project not found, creating..."
  cd $SYS_SYNTH_DIR/..
  vivado -mode batch -source setup-project.tcl > $LOG_DIR/create-project.log
fi
echo "Running system synthesis..."
cd $SYS_SYNTH_DIR
./synth-and-update.sh $ACCEL_VARIANT > $LOG_DIR/synth-sys.log

cd $ORIG_DIR
# print the last 50 lines of synthesis log (succcess/fail, timing summary)
cat $LOG_DIR/synth-sys.log | tail -n 50

echo "$0 finished"
