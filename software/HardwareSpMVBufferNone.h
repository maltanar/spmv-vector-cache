#ifndef HARDWARESPMVBUFFERNONE_H_
#define HARDWARESPMVBUFFERNONE_H_

#include "HardwareSpMV.h"
#include "SpMVAcceleratorBufferNoneDriver.hpp"

class HardwareSpMVBufferNone: public HardwareSpMV {
public:
	HardwareSpMVBufferNone(unsigned int aBase, unsigned int aReset,
			SparseMatrix * A, SpMVData *x, SpMVData *y);
	virtual ~HardwareSpMVBufferNone();

	virtual bool exec();
	void printAllFIFOLevels();
	void printAllStatistics();

	virtual unsigned int statInt(std::string name);
	virtual std::vector<std::string> statKeys();

protected:
	SpMVAcceleratorBufferNoneDriver * m_acc;

	virtual void setupRegs();

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

	// statistics
	void updateStatistics();
	unsigned int m_totalCycles;
	unsigned int m_activeCycles;
	unsigned int m_hazardStalls;
	unsigned int m_capacityStalls;

	virtual void setThresholdRegisters();
};

#endif /* HARDWARESPMVBUFFERNONE_H_ */
