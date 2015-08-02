#include <stdlib.h>
#include <iostream>
#include <string.h>
#include <string>
#include "xil_cache.h"
#include "SparseMatrix.h"
#include "SoftwareSpMV.h"
#include "malloc_aligned.h"
#include "sdcard.h"
#include "devcfg.h"

using namespace std;

unsigned int matrixMetaBase = 0x08000100;
unsigned int accBase = 0x40000000;
unsigned int resBase = 0x43c00000;

#define HWSPMV_NEWCACHE

// use compile-time defines to decide which type of HW SpMV to use
#if defined(HWSPMV_BUFFERALL)
#include "HardwareSpMV.h"
#define HWSPMV	HardwareSPMV
static const char * hwSpMVIDString = "BufferAll";
#elif defined(HWSPMV_BUFFERNONE)
#include "HardwareSpMVBufferNone.h"
#define HWSPMV HardwareSpMVBufferNone
static const char * hwSpMVIDString = "BufferNone";
#elif defined(HWSPMV_BUFFERSEL)
#include "HardwareSpMVBufferSel.h"
#define HWSPMV HardwareSpMVBufferSel
static const char * hwSpMVIDString = "BufferSel";
#elif defined(HWSPMV_NEWCACHE)
#include "HardwareSpMVNewCache.h"
#define HWSPMV HardwareSpMVNewCache
static const char * hwSpMVIDString = "NewCache";
#endif

static string loadedMatrixName;

// load named matrix from SD card
// note that we assume a certain directory structure and file naming convention
void loadSparseMatrixFromSDCard(string name) {
	if (loadedMatrixName == name)
		return;

	string baseDir = name + "/";
	readFromSDCard((baseDir + name + "-meta.bin").c_str(), matrixMetaBase);
	CompressedSparseMetadata *md = (CompressedSparseMetadata *) matrixMetaBase;
	readFromSDCard((baseDir + name + "-indptr.bin").c_str(), md->indPtrBase);
	readFromSDCard((baseDir + name + "-inds.bin").c_str(), md->indBase);
	readFromSDCard((baseDir + name + "-data.bin").c_str(), md->nzDataBase);
	loadedMatrixName = name;
}

// interactive version for loading matrices from SD card
void loadSparseMatrixFromSDCard() {
	cout << "Enter matrix name to load, q to exit: " << endl;
	string name;
	cin >> name;
	if (name == "q")
		exit(0);
	loadSparseMatrixFromSDCard(name);
}

int main(int argc, char *argv[]) {
	loadedMatrixName = "";
	Xil_DCacheDisable();
	mount();	// mount the sd card

	//selectBitfile();

	cout << "SpMVAccel-" << hwSpMVIDString << endl;
	cout << "=====================================" << endl;

	while (1) {
		loadSparseMatrixFromSDCard();

		SparseMatrix * A = SparseMatrix::fromMemory(matrixMetaBase);

		A->printSummary();

		SpMVData *x = (SpMVData *) malloc_aligned(64,
				sizeof(SpMVData) * A->getCols());
		SpMVData *y = (SpMVData *) malloc_aligned(64,
				sizeof(SpMVData) * A->getRows());

		for (SpMVIndex i = 0; i < A->getCols(); i++) {
			x[i] = (SpMVData) 1;
		}
		for (SpMVIndex i = 0; i < A->getRows(); i++) {
			y[i] = (SpMVData) 0;
		}

		cout << "Signature: " << hex << *(volatile unsigned int *)accBase << dec << endl;

		SpMV * spmv = new HWSPMV(accBase, resBase, A, x, y);
		SoftwareSpMV check(A, x);

		spmv->exec();
		check.exec();

		SpMVData * goldenY = check.getY();
		int res = memcmp(goldenY, y, sizeof(SpMVData) * A->getRows());

		cout << "Memcmp result = " << res << endl;

		if (res != 0) {
			unsigned int diffs = 0;
			for (SpMVIndex i = 0; i < A->getRows(); i++) {
				if (y[i] != goldenY[i])
					diffs++;
			}
			cout << "A total of " << diffs << " result elements are different."
					<< endl;
		}
	}
	unmount(); // unmount the sd card

	return 0;
}
