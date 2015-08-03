#include <stdlib.h>
#include <iostream>
#include <string.h>
#include <string>
#include <vector>
#include "xil_cache.h"
#include "SparseMatrix.h"
#include "SoftwareSpMV.h"
#include "malloc_aligned.h"
#include "sdcard.h"
#include "devcfg.h"
#include "HWSpMVFactory.h"

using namespace std;

unsigned int matrixMetaBase = 0x08000100;
unsigned int accBase = 0x40000000;
unsigned int resBase = 0x43c00000;

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

void printResults(HardwareSpMV * spmv, vector<string> keys) {
	for(vector<string>::iterator it = keys.begin(); it != keys.end(); ++it) {
		if(*it == "matrix") cout << loadedMatrixName << ", ";
		else cout << spmv->statInt(*it) << ", ";
	}
	cout << endl;
}

int main(int argc, char *argv[]) {
	loadedMatrixName = "";
	Xil_DCacheDisable();
	mount();	// mount the sd card

	//selectBitfile();
	string hwSpMVIDString = HWSpMVFactory::name(accBase);

	cout << "SpMVAccel-" << hwSpMVIDString << endl;
	cout << "=====================================" << endl;

	while (1) {
		loadSparseMatrixFromSDCard();

		SparseMatrix * A = SparseMatrix::fromMemory(matrixMetaBase);
		A->setName(loadedMatrixName);
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

		HardwareSpMV * spmv = HWSpMVFactory::make(accBase, resBase, A, x, y);
		SoftwareSpMV check(A, x);

		spmv->exec();
		check.exec();

		spmv->compareGolden(check.getY());

		vector<string> keys;
		keys.push_back("matrix");
		keys.push_back("rows");
		keys.push_back("cols");
		keys.push_back("nz");
		keys.push_back("totalCycles");
		keys.push_back("activeCycles");
		keys.push_back("ocmDepth");
		keys.push_back("issueWindow");

		printResults(spmv, keys);

	}
	unmount(); // unmount the sd card

	return 0;
}
