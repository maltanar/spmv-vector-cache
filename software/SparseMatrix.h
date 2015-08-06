#ifndef SPARSEMATRIX_H_
#define SPARSEMATRIX_H_

#include <string>
typedef unsigned int SpMVIndex;
typedef double SpMVData;

typedef struct {
	unsigned int numRows;
	unsigned int numCols;
	unsigned int numNZ;
	unsigned int startingRow;
	unsigned int indPtrBase;
	unsigned int indBase;
	unsigned int nzDataBase;
} CompressedSparseMetadata;

class SparseMatrix {
public:
	SparseMatrix();
	virtual ~SparseMatrix();

	void printSummary();
	bool isSquare();

	void setName(std::string name) {m_name = name;};
	std::string getName() {return m_name;};

	static SparseMatrix* fromMemory(unsigned int metaDataBase);

	void markRowStarts();

	unsigned int getCols() const {
		return m_cols;
	}

	SpMVIndex* getIndPtrs() const {
		return m_indPtrs;
	}

	SpMVIndex* getInds() const {
		return m_inds;
	}

	unsigned int getNz() const {
		return m_nz;
	}

	SpMVData* getNzData() const {
		return m_nzData;
	}

	unsigned int getRows() const {
		return m_rows;
	}

protected:
	unsigned int m_rows;
	unsigned int m_cols;
	unsigned int m_nz;
	SpMVIndex * m_indPtrs;
	SpMVIndex * m_inds;
	SpMVData * m_nzData;
	std::string m_name;

	bool m_rowStartsMarked;
};

#endif /* SPARSEMATRIX_H_ */
