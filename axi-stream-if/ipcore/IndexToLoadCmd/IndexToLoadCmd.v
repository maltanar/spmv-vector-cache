module IndexToLoadCmd(
    output io_indexIn_ready,
    input  io_indexIn_valid,
    input [31:0] io_indexIn_bits,
    input  io_cmdOut_ready,
    output io_cmdOut_valid,
    output[71:0] io_cmdOut_bits
);

  wire[71:0] T0;
  wire[71:0] T1;
  wire[63:0] T2;
  wire T3;


  assign io_cmdOut_bits = T0;
  assign T0 = T3 ? T1 : 72'h0;
  assign T1 = {8'h0, T2};
  assign T2 = {io_indexIn_bits, 32'h8};
  assign T3 = io_indexIn_valid & io_cmdOut_ready;
  assign io_cmdOut_valid = T3;
  assign io_indexIn_ready = T3;
endmodule

