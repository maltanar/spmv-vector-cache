
#ifndef HARDWARESPMVBUFFERNONE_H_
#define HARDWARESPMVBUFFERNONE_H_

#include "SpMV.h"
#include "SpMVAcceleratorBufferNoneDriver.hpp"

class HardwareSpMVBufferNone: public SpMV {
public:
	HardwareSpMVBufferNone(unsigned int aBase, unsigned int aReset, SparseMatrix * A, SpMVData *x, SpMVData *y);
	virtual ~HardwareSpMVBufferNone();

	virtual bool exec();
	void printAllFIFOLevels();

protected:
	volatile unsigned int * m_accelBase;
	volatile unsigned int * m_resetBase;

	SpMVAcceleratorBufferNoneDriver * m_acc;

	void resetAccelerator();
	void setupRegs();

	// helpers for reading status
	typedef enum {
		backendMaskDoneRegular = 1
	} BackendStatMask;
	bool readBackendStatus(BackendStatMask mask);

	typedef enum {
		frontendMaskDoneRegular = 1
	} FrontendStatMask;
	bool readFrontendStatus(FrontendStatMask mask);

	// TODO add nonblocking versions
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
};

#endif /* HARDWARESPMVBUFFERNONE_H_ */
