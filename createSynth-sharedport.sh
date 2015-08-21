#! /bin/bash

PROJ_NAME=$1
PATH_TO_COMP_VERILOG=$2

PRJS_ROOT="/home/maltanar/sandbox/spmv-vector-cache/proj"
UTILS_ROOT="$PRJS_ROOT/utils"
IP_ROOT="$PRJS_ROOT/ip"
WRAPPERS_ROOT="/home/maltanar/Dropbox/PhD/ip-repo/ZynqWrappers"
WRAPPERS="BRAMWrappers QueueWrappers DPWrappers"

PRJ_ROOT="$PRJS_ROOT/$PROJ_NAME"
SYS_ROOT="$PRJ_ROOT/sys"

if [ ! -e $SYS_ROOT ]; then
  # create system synthesis project
  mkdir -p $SYS_ROOT

  # call tcl script to create Vivado project
  vivado -mode batch -source "$UTILS_ROOT/setup-project.tcl" -tclargs "$PROJ_NAME" "$SYS_ROOT" "$IP_ROOT" "$PATH_TO_COMP_VERILOG" "WrapperBlockDesign-2Port-Shared.tcl"
fi
