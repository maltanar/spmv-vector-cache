// Verilated -*- SystemC -*-
// DESCRIPTION: Verilator output: Design implementation internals
// See VChiselModule.h for the primary calling header

#include "VChiselModule.h"     // For This
#include "VChiselModule__Syms.h"

//--------------------
// STATIC VARIABLES


//--------------------

VL_SC_CTOR_IMP(VChiselModule)
#if (SYSTEMC_VERSION>20011000)
    : clk("clk"), reset("reset"), io_streamInput_ready("io_streamInput_ready"), 
      io_streamInput_valid("io_streamInput_valid"), 
      io_streamInput_bits("io_streamInput_bits"), io_in("io_in"), 
      io_out("io_out")
#endif
 {
    VChiselModule__Syms* __restrict vlSymsp = __VlSymsp = new VChiselModule__Syms(this, name());
    VChiselModule* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Sensitivities on all clocks and combo inputs
    SC_METHOD(eval);
    sensitive << clk;
    sensitive << reset;
    sensitive << io_streamInput_valid;
    sensitive << io_streamInput_bits;
    
    // Reset internal values
    
    // Reset structure values
    __Vcellinp__v__io_streamInput_bits = VL_RAND_RESET_I(32);
    __Vcellinp__v__io_streamInput_valid = VL_RAND_RESET_I(1);
    __Vcellinp__v__reset = VL_RAND_RESET_I(1);
    __Vcellinp__v__clk = VL_RAND_RESET_I(1);
    v__DOT__regExample = VL_RAND_RESET_I(32);
    __Vclklast__TOP____Vcellinp__v__clk = VL_RAND_RESET_I(1);
}

void VChiselModule::__Vconfigure(VChiselModule__Syms* vlSymsp, bool first) {
    if (0 && first) {}  // Prevent unused
    this->__VlSymsp = vlSymsp;
}

VChiselModule::~VChiselModule() {
    delete __VlSymsp; __VlSymsp=NULL;
}

//--------------------


void VChiselModule::eval() {
    VChiselModule__Syms* __restrict vlSymsp = this->__VlSymsp; // Setup global symbol table
    VChiselModule* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Initialize
    if (VL_UNLIKELY(!vlSymsp->__Vm_didInit)) _eval_initial_loop(vlSymsp);
    // Evaluate till stable
    VL_DEBUG_IF(VL_PRINTF("\n----TOP Evaluate VChiselModule::eval\n"); );
    int __VclockLoop = 0;
    IData __Vchange=1;
    while (VL_LIKELY(__Vchange)) {
	VL_DEBUG_IF(VL_PRINTF(" Clock loop\n"););
	vlSymsp->__Vm_activity = true;
	_eval(vlSymsp);
	__Vchange = _change_request(vlSymsp);
	if (++__VclockLoop > 100) vl_fatal(__FILE__,__LINE__,__FILE__,"Verilated model didn't converge");
    }
}

void VChiselModule::_eval_initial_loop(VChiselModule__Syms* __restrict vlSymsp) {
    vlSymsp->__Vm_didInit = true;
    _eval_initial(vlSymsp);
    vlSymsp->__Vm_activity = true;
    int __VclockLoop = 0;
    IData __Vchange=1;
    while (VL_LIKELY(__Vchange)) {
	_eval_settle(vlSymsp);
	_eval(vlSymsp);
	__Vchange = _change_request(vlSymsp);
	if (++__VclockLoop > 100) vl_fatal(__FILE__,__LINE__,__FILE__,"Verilated model didn't DC converge");
    }
}

//--------------------
// Internal Methods

void VChiselModule::_combo__TOP__1(VChiselModule__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VChiselModule::_combo__TOP__1\n"); );
    VChiselModule* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    VL_ASSIGN_ISI(1,vlTOPp->__Vcellinp__v__clk, vlTOPp->clk);
}

void VChiselModule::_sequent__TOP__3(VChiselModule__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VChiselModule::_sequent__TOP__3\n"); );
    VChiselModule* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Variables
    VL_SIG(__Vdly__v__DOT__regExample,31,0);
    // Body
    __Vdly__v__DOT__regExample = vlTOPp->v__DOT__regExample;
    // ALWAYS at ChiselModule.v:20
    if (vlTOPp->__Vcellinp__v__reset) {
	__Vdly__v__DOT__regExample = 0;
    } else {
	if (vlTOPp->__Vcellinp__v__io_streamInput_valid) {
	    __Vdly__v__DOT__regExample = (vlTOPp->v__DOT__regExample 
					  + vlTOPp->__Vcellinp__v__io_streamInput_bits);
	}
    }
    vlTOPp->v__DOT__regExample = __Vdly__v__DOT__regExample;
    VL_ASSIGN_SII(32,vlTOPp->io_out, vlTOPp->v__DOT__regExample);
}

void VChiselModule::_combo__TOP__4(VChiselModule__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VChiselModule::_combo__TOP__4\n"); );
    VChiselModule* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    VL_ASSIGN_ISI(1,vlTOPp->__Vcellinp__v__reset, vlTOPp->reset);
    VL_ASSIGN_ISI(32,vlTOPp->__Vcellinp__v__io_streamInput_bits, vlTOPp->io_streamInput_bits);
    VL_ASSIGN_ISI(1,vlTOPp->__Vcellinp__v__io_streamInput_valid, vlTOPp->io_streamInput_valid);
}

void VChiselModule::_settle__TOP__5(VChiselModule__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VChiselModule::_settle__TOP__5\n"); );
    VChiselModule* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    VL_ASSIGN_ISI(1,vlTOPp->__Vcellinp__v__reset, vlTOPp->reset);
    VL_ASSIGN_ISI(32,vlTOPp->__Vcellinp__v__io_streamInput_bits, vlTOPp->io_streamInput_bits);
    VL_ASSIGN_ISI(1,vlTOPp->__Vcellinp__v__io_streamInput_valid, vlTOPp->io_streamInput_valid);
    VL_ASSIGN_SII(32,vlTOPp->io_out, vlTOPp->v__DOT__regExample);
    VL_ASSIGN_SII(1,vlTOPp->io_streamInput_ready, vlTOPp->__Vcellinp__v__io_streamInput_valid);
}

void VChiselModule::_combo__TOP__6(VChiselModule__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VChiselModule::_combo__TOP__6\n"); );
    VChiselModule* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    VL_ASSIGN_SII(1,vlTOPp->io_streamInput_ready, vlTOPp->__Vcellinp__v__io_streamInput_valid);
}

void VChiselModule::_eval(VChiselModule__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VChiselModule::_eval\n"); );
    VChiselModule* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    vlTOPp->_combo__TOP__1(vlSymsp);
    if (((IData)(vlTOPp->__Vcellinp__v__clk) & (~ (IData)(vlTOPp->__Vclklast__TOP____Vcellinp__v__clk)))) {
	vlTOPp->_sequent__TOP__3(vlSymsp);
    }
    vlTOPp->_combo__TOP__4(vlSymsp);
    vlTOPp->_combo__TOP__6(vlSymsp);
    // Final
    vlTOPp->__Vclklast__TOP____Vcellinp__v__clk = vlTOPp->__Vcellinp__v__clk;
}

void VChiselModule::_eval_initial(VChiselModule__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VChiselModule::_eval_initial\n"); );
    VChiselModule* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
}

void VChiselModule::final() {
    VL_DEBUG_IF(VL_PRINTF("    VChiselModule::final\n"); );
    // Variables
    VChiselModule__Syms* __restrict vlSymsp = this->__VlSymsp;
    VChiselModule* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
}

void VChiselModule::_eval_settle(VChiselModule__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VChiselModule::_eval_settle\n"); );
    VChiselModule* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    vlTOPp->_combo__TOP__1(vlSymsp);
    vlTOPp->_settle__TOP__5(vlSymsp);
}

IData VChiselModule::_change_request(VChiselModule__Syms* __restrict vlSymsp) {
    VL_DEBUG_IF(VL_PRINTF("    VChiselModule::_change_request\n"); );
    VChiselModule* __restrict vlTOPp VL_ATTR_UNUSED = vlSymsp->TOPp;
    // Body
    // Change detection
    IData __req = false;  // Logically a bool
    return __req;
}
