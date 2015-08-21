#ifndef HARDWARESPMVBUFFERALL_H_
#define HARDWARESPMVBUFFERALL_H_

#include "HardwareSpMV.h"
#include "SpMVAcceleratorBufferAllDriver.hpp"

class HardwareSpMVBufferAll: public HardwareSpMV {
public:
	HardwareSpMVBufferAll(unsigned int aBase, unsigned int aReset,
			SparseMatrix * A, SpMVData *x, SpMVData *y);
	virtual ~HardwareSpMVBufferAll();

	virtual bool exec();
	void printAllFIFOLevels();

	virtual unsigned int statInt(std::string name);
	virtual std::vector<std::string> statKeys();

protected:
	SpMVAcceleratorBufferAllDriver * m_acc;

	virtual void setupRegs();

	// helpers for reading status
	typedef enum {
		backendMaskDoneRegular = 1,
		backendMaskDoneWrite = 2,
		backendMaskHasDecErr = 4
	} BackendStatMask;
	bool readBackendStatus(BackendStatMask mask);

	typedef enum {
		frontendMaskDoneRegular = 1,
		frontendMaskDoneWrite = 2,
		frontendMaskDoneInit = 4
	} FrontendStatMask;
	bool readFrontendStatus(FrontendStatMask mask);

	// TODO add nonblocking versions
	void init();
	void write();
	void regular();

	// TODO add profiling support

	// FIFO data counts, can be useful for debugging freezes
	typedef enum {
		fifoColPtr = 1,
		fifoRowInd = 2,
		fifoNZData = 3,
		fifoInpVec = 4,
		fifoOutVec = 5
	} SpMVFIFONum;
	volatile unsigned short getFIFOLevel(SpMVFIFONum num);

	// statistics
	void updateStatistics();
	unsigned int m_totalCycles;
	unsigned int m_activeCycles;
	unsigned int m_hazardStalls;


	virtual void setThresholdRegisters();
};

#endif /* HARDWARESPMVBUFFERALL_H_ */
