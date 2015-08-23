#ifndef SOFTWARESPMV_H_
#define SOFTWARESPMV_H_

#include "SpMV.h"

class SoftwareSpMV: public SpMV {
public:
	SoftwareSpMV(SparseMatrix * A, SpMVData *x = 0, SpMVData *y = 0);
	virtual ~SoftwareSpMV();

	virtual bool exec();

	virtual unsigned int statInt(std::string name);
	virtual std::vector<std::string> statKeys();

protected:
	bool m_allocX, m_allocY;
	unsigned int m_execTime;
};

#endif /* SOFTWARESPMV_H_ */
