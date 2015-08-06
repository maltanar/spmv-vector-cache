#ifndef HARDWARESPMVNEWCACHE_H_
#define HARDWARESPMVNEWCACHE_H_

#include "HardwareSpMV.h"
#include "SpMVAcceleratorNewCacheDriver.hpp"

class HardwareSpMVNewCache: public HardwareSpMV {
public:
	HardwareSpMVNewCache(unsigned int aBase, unsigned int aReset,
			SparseMatrix * A, SpMVData *x, SpMVData *y);
	virtual ~HardwareSpMVNewCache();

	virtual bool exec();
	void printAllFIFOLevels();
	void printAllStatistics();

	virtual unsigned int statInt(std::string name);
	virtual std::vector<std::string> statKeys();

protected:
	SpMVAcceleratorNewCacheDriver * m_acc;

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
		frontendMaskDoneInit = 4,
		frontendSupportCMS = 8
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
	unsigned int m_readMisses;
	unsigned int m_conflictMisses;
	unsigned int m_hazardStalls;

#define PROFILER_STATES 8
	unsigned int m_stateCounts[PROFILER_STATES];

};
#endif /* HARDWARESPMVBUFFERSEL_H_ */
