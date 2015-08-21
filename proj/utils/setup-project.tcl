if {$argc != 5} {
  puts "Expected: <project name> <project path> <ip path> <comp verilog> <block design name>"
  exit
}

set projName [lindex $argv 0]
set projPath [lindex $argv 1]
set ipPath [lindex $argv 2]
set compV [lindex $argv 3]
set tclBlockDesign [lindex $argv 4]
set tclBlockDesign "$ipPath/$tclBlockDesign"

# create the project
create_project $projName $projPath -part xc7z020clg484-1
set_property board_part em.avnet.com:zed:part0:1.2 [current_project]

set_property ip_repo_paths $ipPath [current_project]
update_ip_catalog

# create the block design
source $tclBlockDesign
set blockDesign [get_bd_designs]

# create wrapper Verilog for block design
set bdFile [get_files *$blockDesign.bd]
set wrapperFile [make_wrapper -top -files $bdFile]

# set manual compile order
# update_compile_order -fileset sources_1
set_property source_mgmt_mode DisplayOnly [current_project]

# add component sources to the project
source "$ipPath/comps.xdc"
add_files -norecurse $compV

# add wrapper Verilog to the project
add_files -norecurse $wrapperFile

reorder_files -back $wrapperFile

# set procsys_wrapper as top module
set_property top procsys_wrapper [current_fileset]

close_project
