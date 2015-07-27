#ifndef HARDWARESPMVBUFFERSEL_H_
#define HARDWARESPMVBUFFERSEL_H_

#include "SpMV.h"
#include "SpMVAcceleratorBufferSelDriver.hpp"

class HardwareSpMVBufferSel: public SpMV {
public:
	HardwareSpMVBufferSel(unsigned int aBase, unsigned int aReset,
			SparseMatrix * A, SpMVData *x, SpMVData *y);
	virtual ~HardwareSpMVBufferSel();

	virtual bool exec();
	void printAllFIFOLevels();
	void printAllStatistics();

protected:
	volatile unsigned int * m_accelBase;
	volatile unsigned int * m_resetBase;

	SpMVAcceleratorBufferSelDriver * m_acc;

	void resetAccelerator();
	void setupRegs();

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
	unsigned int m_hazardStallsOCM;
	unsigned int m_hazardStallsDDR;
	unsigned int m_capacityStallsOCM;
	unsigned int m_capacityStallsDDR;
};
#endif /* HARDWARESPMVBUFFERSEL_H_ */
