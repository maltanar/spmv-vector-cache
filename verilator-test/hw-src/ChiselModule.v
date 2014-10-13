module ChiselModule(input clk, input reset,
    output io_streamInput_ready,
    input  io_streamInput_valid,
    input [31:0] io_streamInput_bits,
    input [31:0] io_in,
    output[31:0] io_out
);

  reg [31:0] regExample;
  wire[31:0] T2;
  wire[31:0] T0;
  wire[31:0] T1;

  assign io_out = regExample;
  assign T2 = reset ? 32'h0 : T0;
  assign T0 = io_streamInput_valid ? T1 : regExample;
  assign T1 = regExample + io_streamInput_bits;
  assign io_streamInput_ready = io_streamInput_valid;

  always @(posedge clk) begin
    if(reset) begin
      regExample <= 32'h0;
    end else if(io_streamInput_valid) begin
      regExample <= T1;
    end
  end
endmodule

