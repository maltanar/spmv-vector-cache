source sources.xdc

synth_design -top AXIAccelWrapper -part "xc7z020clg484-1" -mode out_of_context -fanout_limit 500 -resource_sharing off
write_edif AXIAccelWrapper.edif
exit
