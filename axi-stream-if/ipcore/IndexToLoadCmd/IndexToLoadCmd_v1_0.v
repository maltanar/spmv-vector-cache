
`timescale 1 ns / 1 ps

	module IndexToLoadCmd_v1_0 #
	(
		// Users to add parameters here

		// User parameters ends
		// Do not modify the parameters beyond this line


		// Parameters of Axi Slave Bus Interface S00_AXIS
		parameter integer C_S00_AXIS_TDATA_WIDTH	= 32,

		// Parameters of Axi Master Bus Interface M00_AXIS
		parameter integer C_M00_AXIS_TDATA_WIDTH	= 72
	)
	(
		// Users to add ports here

		// User ports ends
		// Do not modify the ports beyond this line


		// Ports of Axi Slave Bus Interface S00_AXIS
		input wire  s00_axis_aclk,
		input wire  s00_axis_aresetn,
		output wire  s00_axis_tready,
		input wire [C_S00_AXIS_TDATA_WIDTH-1 : 0] s00_axis_tdata,
		input wire  s00_axis_tvalid,

		// Ports of Axi Master Bus Interface M00_AXIS
		input wire  m00_axis_aclk,
		input wire  m00_axis_aresetn,
		output wire  m00_axis_tvalid,
		output wire [C_M00_AXIS_TDATA_WIDTH-1 : 0] m00_axis_tdata,
		input wire  m00_axis_tready
	);

	// Add user logic here
	IndexToLoadCmd converterInst (
        .io_indexIn_ready(s00_axis_tready),
        .io_indexIn_valid(s00_axis_tvalid),
        .io_indexIn_bits(s00_axis_tdata),
        .io_cmdOut_ready(m00_axis_tready),
        .io_cmdOut_valid(m00_axis_tvalid),
        .io_cmdOut_bits(m00_axis_tdata)
    );

	// User logic ends

	endmodule
