set rootDir "/home/maltanar/sandbox/spmv-vector-cache/vivado-setup"
set projName "BufferAll"
set tclBlockDesign "$rootDir/bd/WrapperBlockDesign-1Port.tcl"
set compNetlist "$rootDir/$projName/comp/AXIAccelWrapper.edif"

set ipRepo "$rootDir/ip"
set projPath "$rootDir/$projName/sys"

file mkdir $projPath

# create the project
create_project $projName $projPath -part xc7z020clg484-1
set_property board_part em.avnet.com:zed:part0:1.2 [current_project]

set_property ip_repo_paths $ipRepo [current_project]
update_ip_catalog

# create the block design
source $tclBlockDesign
set blockDesign [get_bd_designs]

# create wrapper Verilog for block design
set bdFile [get_files *$blockDesign.bd]
set wrapperFile [make_wrapper -top -files $bdFile]

# add wrapper Verilog to the project
add_files -norecurse $wrapperFile

# add component sources to the project
source /home/maltanar/Dropbox/PhD/ip-repo/ZynqWrappers/BRAMNetlist.xdc
source /home/maltanar/Dropbox/PhD/ip-repo/ZynqWrappers/QueueNetlist.xdc
source /home/maltanar/Dropbox/PhD/ip-repo/ZynqWrappers/DPNetlist.xdc
add_files -norecurse $compNetlist

# set manual compile order, process component netlist first
update_compile_order -fileset sources_1
set_property source_mgmt_mode DisplayOnly [current_project]
reorder_files -front $compNetlist

close_project

# copy utility scripts to project root
file copy "$rootDir/utils/blockingSynth.tcl" "$projPath/blockingSynth.tcl"
file copy "$rootDir/utils/synth-and-update.sh" "$projPath/synth-and-update.sh"
file copy "$rootDir/utils/update-only.sh" "$projPath/update-only.sh"

