source /home/maltanar/Dropbox/PhD/ip-repo/ZynqWrappers/BRAMNetlist.xdc
source /home/maltanar/Dropbox/PhD/ip-repo/ZynqWrappers/QueueNetlist.xdc
source /home/maltanar/Dropbox/PhD/ip-repo/ZynqWrappers/DPNetlist.xdc

read_edif /home/maltanar/sandbox/spmv-vector-cache/vivado-setup/BufferAll/comp/AXIAccelWrapper.edif

update_compile_order -fileset sources_1
set_property source_mgmt_mode DisplayOnly [current_project]
reorder_files -front /home/maltanar/sandbox/spmv-vector-cache/vivado-setup/BufferAll/comp/AXIAccelWrapper.edif

