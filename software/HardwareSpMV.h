#ifndef HARDWARESPMV_H_
#define HARDWARESPMV_H_

#include "SpMV.h"

class HardwareSpMV: public SpMV {
public:
	HardwareSpMV(unsigned int aBase, unsigned int aReset, SparseMatrix * A,
			SpMVData *x, SpMVData *y);
	virtual ~HardwareSpMV();

protected:
	volatile unsigned int * m_accelBase;
	volatile unsigned int * m_resetBase;
};

#endif /* HARDWARESPMV_H_ */
