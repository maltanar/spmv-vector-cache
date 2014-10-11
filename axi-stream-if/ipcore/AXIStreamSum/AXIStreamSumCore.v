module AXIStreamSumCore(input clk, input reset,
    output io_streamInput_ready,
    input  io_streamInput_valid,
    input [31:0] io_streamInput_bits,
    input [3:0] io_keep,
    output[31:0] io_accelID,
    output[31:0] io_streamSum,
    output[31:0] io_elementCnt
);

  reg [31:0] elementCnt;
  wire[31:0] T15;
  wire[31:0] T0;
  wire[31:0] T1;
  reg [31:0] streamSum;
  wire[31:0] T16;
  wire[31:0] T2;
  wire[31:0] T3;
  wire[31:0] T4;
  wire[31:0] mask;
  wire[15:0] T5;
  wire[7:0] T6;
  wire[7:0] T17;
  wire T7;
  wire[7:0] T8;
  wire[7:0] T18;
  wire T9;
  wire[15:0] T10;
  wire[7:0] T11;
  wire[7:0] T19;
  wire T12;
  wire[7:0] T13;
  wire[7:0] T20;
  wire T14;

  assign io_elementCnt = elementCnt;
  assign T15 = reset ? 32'h0 : T0;
  assign T0 = io_streamInput_valid ? T1 : elementCnt;
  assign T1 = elementCnt + 32'h1;
  assign io_streamSum = streamSum;
  assign T16 = reset ? 32'h0 : T2;
  assign T2 = io_streamInput_valid ? T3 : streamSum;
  assign T3 = streamSum + T4;
  assign T4 = io_streamInput_bits & mask;
  assign mask = {T10, T5};
  assign T5 = {T8, T6};
  assign T6 = 8'h0 - T17;
  assign T17 = {7'h0, T7};
  assign T7 = io_keep[1'h0:1'h0];
  assign T8 = 8'h0 - T18;
  assign T18 = {7'h0, T9};
  assign T9 = io_keep[1'h1:1'h1];
  assign T10 = {T13, T11};
  assign T11 = 8'h0 - T19;
  assign T19 = {7'h0, T12};
  assign T12 = io_keep[2'h2:2'h2];
  assign T13 = 8'h0 - T20;
  assign T20 = {7'h0, T14};
  assign T14 = io_keep[2'h3:2'h3];
  assign io_accelID = 32'hdeadbeef;
  assign io_streamInput_ready = io_streamInput_valid;

  always @(posedge clk) begin
    if(reset) begin
      elementCnt <= 32'h0;
    end else if(io_streamInput_valid) begin
      elementCnt <= T1;
    end
    if(reset) begin
      streamSum <= 32'h0;
    end else if(io_streamInput_valid) begin
      streamSum <= T3;
    end
  end
endmodule
