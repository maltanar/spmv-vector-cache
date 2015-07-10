#ifndef SPMV_H_
#define SPMV_H_

#include "SparseMatrix.h"

class SpMV {
public:
	SpMV(SparseMatrix * A, SpMVData *x, SpMVData *y);

	virtual bool exec() = 0;

	virtual ~SpMV();

	SparseMatrix* getA() const {
		return m_A;
	}

	SpMVData* getX() const {
		return m_x;
	}

	SpMVData* getY() const {
		return m_y;
	}

protected:
	SparseMatrix * m_A;
	SpMVData * m_x;
	SpMVData * m_y;
};

#endif /* SPMV_H_ */
