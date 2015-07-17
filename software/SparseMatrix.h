#ifndef SPARSEMATRIX_H_
#define SPARSEMATRIX_H_

typedef unsigned int SpMVIndex;
typedef unsigned long long SpMVData;

class SparseMatrix {
public:
	SparseMatrix();
	virtual ~SparseMatrix();

	void printSummary();
	bool isSquare();

	static SparseMatrix* fromMemory(unsigned int metaDataBase);

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
	typedef struct {
		unsigned int numRows;
		unsigned int numCols;
		unsigned int numNZ;
		unsigned int startingRow;
		unsigned int indPtrBase;
		unsigned int indBase;
		unsigned int nzDataBase;
	} CompressedSparseMetadata;

	unsigned int m_rows;
	unsigned int m_cols;
	unsigned int m_nz;
	SpMVIndex * m_indPtrs;
	SpMVIndex * m_inds;
	SpMVData * m_nzData;
};

#endif /* SPARSEMATRIX_H_ */
