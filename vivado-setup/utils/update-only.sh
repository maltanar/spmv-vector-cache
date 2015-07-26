#!/bin/bash

VIVADO_DIR=/home/maltanar/Xilinx/Vivado/2014.4
SYSNAME=procsys
PRJ=$1

if [ ! -e $PRJ.xpr ]; then
  echo "File $PRJ.xpr not found"
  exit -1
fi

SDK_FILES="${SYSNAME}_wrapper.bit"

for SDKFILE in $SDK_FILES; do
  mv -f "$PRJ.sdk/${SYSNAME}_wrapper_hw_platform_0/$SDKFILE" "$PRJ.sdk/${SYSNAME}_wrapper_hw_platform_0/$SDKFILE.bak"
  cp "$PRJ.runs/impl_1/$SDKFILE" "$PRJ.sdk/${SYSNAME}_wrapper_hw_platform_0/$SDKFILE"
done


