
#ifndef HARDWARESPMV_H_
#define HARDWARESPMV_H_

#include "SpMV.h"
#include "SpMVAcceleratorDriver.hpp"

class HardwareSpMV: public SpMV {
public:
	HardwareSpMV(unsigned int aBase, unsigned int aReset, SparseMatrix * A, SpMVData *x, SpMVData *y);
	virtual ~HardwareSpMV();

	virtual bool exec();

protected:
	volatile unsigned int * m_accelBase;
	volatile unsigned int * m_resetBase;

	SpMVAcceleratorDriver * m_acc;

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
};

#endif /* HARDWARESPMV_H_ */
