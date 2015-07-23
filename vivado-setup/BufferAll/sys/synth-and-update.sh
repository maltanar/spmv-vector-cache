#!/bin/bash

VIVADO_DIR=/home/maltanar/Xilinx/Vivado/2014.4
SYSNAME=procsys
PRJ=$1

if [ ! -e $PRJ.xpr ]; then
  echo "File $PRJ.xpr not found"
  exit -1
fi

source "$VIVADO_DIR/settings64.sh"

time vivado -mode tcl -source blockingSynth.tcl -tclargs "$PRJ.xpr"

cat "$PRJ.runs/impl_1/${SYSNAME}_wrapper_timing_summary_routed.rpt" | grep "Design Timing Summary" -A 20

./update-only.sh $PRJ
