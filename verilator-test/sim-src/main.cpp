#include <QCoreApplication>

#include "VChiselModule.h"
#include "fifoinbreakout.h"

using namespace std;

class ChiselModuleWrapper : public sc_module
{
public:
    ChiselModuleWrapper(sc_module_name nm) : sc_module(nm), uut("uut"), adp("adp")
    {
        uut.clk(clk);
        uut.reset(reset);

        adp.bindSignalInterface(uut.io_streamInput_valid, uut.io_streamInput_ready, uut.io_streamInput_bits);
        adp.bindFIFOInput(inputStream);
        //adp.fifoInput.bind(inputStream);

        uut.io_out.bind(sumOutput);
        uut.io_in.bind(constInSignal);


        constInSignal = 5;
    }

    sc_in_clk clk;
    sc_in<bool> reset;
    sc_fifo_in<uint32_t> inputStream;
    sc_out<uint32_t> sumOutput;
    sc_signal<uint32_t> constInSignal;


protected:
    VChiselModule uut;
    FIFOInBreakout<uint32_t> adp;
};


class Tester : public sc_module
{
    SC_HAS_PROCESS(Tester);

public:
    sc_in_clk clk;
    sc_fifo<uint32_t> inputFIFO;
    sc_signal<uint32_t> outputSignal;
    sc_signal<bool> reset;
    bool done;

    Tester(sc_module_name nm) : sc_module(nm), mod("mod"), inputFIFO(16)
    {
        mod.clk(clk);
        mod.reset(reset);
        mod.inputStream(inputFIFO);

        mod.sumOutput(outputSignal);

        SC_CTHREAD(pushInput, clk.pos());
        SC_CTHREAD(monitorOutput, clk.pos());

        done = false;
        reset = false;
    }

    void pushInput()
    {
        uint32_t n = 0;
        while(!done)
        {
            wait();
            inputFIFO.write(n);
            cout << "Pushed " << n << " at time " << sc_time_stamp() << endl;
            n++;
        }
    }


    void monitorOutput()
    {
        while(!done)
        {
            wait();
            cout << "Output = " << outputSignal << " at time " << sc_time_stamp() << endl;
        }
    }

protected:
    ChiselModuleWrapper mod;
};



int sc_main(int argc, char *argv[])
{
    sc_clock clk("clk", sc_time(10, SC_NS));
    Tester t("t");

    t.clk(clk);

    sc_start(100, SC_NS);
    return 0;
}
