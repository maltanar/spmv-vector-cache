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

protected:
	volatile unsigned int * m_accelBase;
	volatile unsigned int * m_resetBase;
	int m_diffFromGolden;
};

#endif /* HARDWARESPMV_H_ */
