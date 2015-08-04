set src [lindex $argv 0]
set dest [lindex $argv 1]

write_cfgmem -format BIN -disablebitswap -size 128 -interface SMAPx32 -loadbit "up 0x0 $src" -file $dest
