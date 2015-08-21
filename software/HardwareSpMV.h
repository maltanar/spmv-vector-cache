#ifndef HARDWARESPMV_H_
#define HARDWARESPMV_H_

#include <string>
#include <vector>
#include "SpMV.h"

class HardwareSpMV: public SpMV {
public:
	HardwareSpMV(unsigned int aBase, unsigned int aReset, SparseMatrix * A,
			SpMVData *x, SpMVData *y);
	virtual ~HardwareSpMV();

	void resetAccelerator();
	void compareGolden(SpMVData * golden);

	virtual unsigned int statInt(std::string name);
	virtual std::vector<std::string> statKeys();

	virtual void setThresholds(unsigned int colPtr, unsigned int rowInd, unsigned int nzData, unsigned int inpVec);

protected:
	volatile unsigned int * m_accelBase;
	volatile unsigned int * m_resetBase;
	int m_diffFromGolden;
	unsigned int m_thres_colPtr;
	unsigned int m_thres_rowInd;
	unsigned int m_thres_nzData;
	unsigned int m_thres_inpVec;

	virtual void init();
	virtual void write();
	virtual void regular();
	virtual void setThresholdRegisters() = 0;
	virtual void setupRegs();
};

#endif /* HARDWARESPMV_H_ */
