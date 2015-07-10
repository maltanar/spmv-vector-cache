#ifndef SOFTWARESPMV_H_
#define SOFTWARESPMV_H_

#include "SpMV.h"

class SoftwareSpMV: public SpMV {
public:
	SoftwareSpMV(SparseMatrix * A, SpMVData *x = 0, SpMVData *y = 0);
	virtual ~SoftwareSpMV();

	virtual bool exec();

protected:
	bool m_allocX, m_allocY;
};

#endif /* SOFTWARESPMV_H_ */
