#!/bin/bash

mv -f AXIAccelWrapper.edif AXIAccelWrapper.edif.bak
vivado -mode tcl -source synth.tcl
