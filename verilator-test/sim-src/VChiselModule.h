// Verilated -*- SystemC -*-
// DESCRIPTION: Verilator output: Primary design header
//
// This header should be included by all source files instantiating the design.
// The class here is then constructed to instantiate the design.
// See the Verilator manual for examples.

#ifndef _VChiselModule_H_
#define _VChiselModule_H_

#include "systemc.h"
#include "verilated_sc.h"
#include "verilated.h"
class VChiselModule__Syms;

//----------

SC_MODULE(VChiselModule) {
  public:
    // CELLS
    // Public to allow access to /*verilator_public*/ items;
    // otherwise the application code can consider these internals.
    
    // PORTS
    // The application code writes and reads these signals to
    // propagate new values into/out from the Verilated model.
    sc_in<bool>	clk;
    sc_in<bool>	reset;
    sc_out<bool>	io_streamInput_ready;
    sc_in<bool>	io_streamInput_valid;
    sc_in<uint32_t>	io_streamInput_bits;
    sc_in<uint32_t>	io_in;
    sc_out<uint32_t>	io_out;
    
    // LOCAL SIGNALS
    // Internals; generally not touched by application code
    VL_SIG(v__DOT__regExample,31,0);
    
    // LOCAL VARIABLES
    // Internals; generally not touched by application code
    VL_SIG8(__Vcellinp__v__clk,0,0);
    VL_SIG8(__Vcellinp__v__io_streamInput_valid,0,0);
    VL_SIG8(__Vcellinp__v__reset,0,0);
    VL_SIG8(__Vclklast__TOP____Vcellinp__v__clk,0,0);
    VL_SIG(__Vcellinp__v__io_streamInput_bits,31,0);
    
    // INTERNAL VARIABLES
    // Internals; generally not touched by application code
    //char	__VpadToAlign52[4];
    VChiselModule__Syms*	__VlSymsp;		// Symbol table
    
    // PARAMETERS
    // Parameters marked /*verilator public*/ for use by application code
    
    // CONSTRUCTORS
  private:
    VChiselModule& operator= (const VChiselModule&);	///< Copying not allowed
    VChiselModule(const VChiselModule&);	///< Copying not allowed
  public:
    SC_CTOR(VChiselModule);
    virtual ~VChiselModule();
    
    // USER METHODS
    
    // API METHODS
  private:
    void eval();
  public:
    void final();
    
    // INTERNAL METHODS
  private:
    static void _eval_initial_loop(VChiselModule__Syms* __restrict vlSymsp);
  public:
    void __Vconfigure(VChiselModule__Syms* symsp, bool first);
  private:
    static IData	_change_request(VChiselModule__Syms* __restrict vlSymsp);
  public:
    static void	_combo__TOP__1(VChiselModule__Syms* __restrict vlSymsp);
    static void	_combo__TOP__4(VChiselModule__Syms* __restrict vlSymsp);
    static void	_combo__TOP__6(VChiselModule__Syms* __restrict vlSymsp);
    static void	_eval(VChiselModule__Syms* __restrict vlSymsp);
    static void	_eval_initial(VChiselModule__Syms* __restrict vlSymsp);
    static void	_eval_settle(VChiselModule__Syms* __restrict vlSymsp);
    static void	_sequent__TOP__3(VChiselModule__Syms* __restrict vlSymsp);
    static void	_settle__TOP__5(VChiselModule__Syms* __restrict vlSymsp);
} VL_ATTR_ALIGNED(128);

#endif  /*guard*/
