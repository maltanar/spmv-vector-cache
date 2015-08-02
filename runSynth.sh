#! /bin/bash

PRJ_NAME=$1

PRJS_ROOT="/home/maltanar/sandbox/spmv-vector-cache/proj"
PRJ_ROOT="$PRJS_ROOT/$PRJ_NAME"
UTILS_ROOT="$PRJS_ROOT/utils"

vivado -mode batch -source "$UTILS_ROOT/blockingSynth.tcl" -tclargs "$PRJ_ROOT/sys/$PRJ_NAME.xpr" "$PRJ_ROOT/$PRJ_NAME.bit"

# use this command to generate bitstreams for loading directly from the SD card:

# write_cfgmem -format BIN -disablebitswap -size 128 -interface SMAPx32 -loadbit "up 0x0 cache1024.bit" -file cache1024A.bin

# in ISE it looked like this:
# promgen -b -w -p bin -data_width 32 -u 0 bitfile.bit -o bitfile.bin
# further reference:
#http://forums.xilinx.com/t5/Design-Tools-Others/write-cfgmem-output-promgen-output/td-p/506095
