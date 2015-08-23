#ifndef SOFTWARESPMV_H_
#define SOFTWARESPMV_H_

#include "SpMV.h"

class SoftwareSpMV: public SpMV {
public:
	SoftwareSpMV(SparseMatrix * A, SpMVData *x = 0, SpMVData *y = 0);
	virtual ~SoftwareSpMV();

	void measurePreprocessingTimes();

	virtual bool exec();

	virtual unsigned int statInt(std::string name);
	virtual std::vector<std::string> statKeys();

protected:
	bool m_allocX, m_allocY;
	unsigned int m_execTime;
	unsigned int m_cmsTime;
	unsigned int m_maxAliveTime;
	unsigned int m_maxColSpanTime;
};

#endif /* SOFTWARESPMV_H_ */
