if {$argc != 2} {
  puts "Expected: <prj file> <bitfile>"
  exit
}

open_project [lindex $argv 0]
reset_run synth_1
reset_run impl_1
launch_runs synth_1
wait_on_run synth_1
launch_runs impl_1
wait_on_run impl_1
open_run impl_1
write_bitstream -force [lindex $argv 1]
exit
