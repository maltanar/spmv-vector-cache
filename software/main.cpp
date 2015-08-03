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

void printKeys(vector<string> keys) {
	for (vector<string>::iterator it = keys.begin(); it != keys.end(); ++it) {
		cout << *it << ",";
	}
	cout << endl;
}

void printResults(HardwareSpMV * spmv, vector<string> keys) {
	for (vector<string>::iterator it = keys.begin(); it != keys.end(); ++it) {
		if (*it == "matrix")
			cout << loadedMatrixName << ", ";
		else if (*it == "accType")
			cout << HWSpMVFactory::name(accBase) << ", ";
		else
			cout << spmv->statInt(*it) << ", ";
	}
	cout << endl;
}

/*
 S. Williams matrix suite:
cant
conf5_4-8x8-05
consph
cop20k_A
mac_econ_fwd500
mc2depi
pdb1HYS
pwtk
rma10
scircuit
shipsec1
webbase-1M
q

 */

int main(int argc, char *argv[]) {
	loadedMatrixName = "";
	Xil_DCacheDisable();
	mount();	// mount the sd card

	//selectBitfile();
	string hwSpMVIDString = HWSpMVFactory::name(accBase);

	cout << "SpMVAccel-" << hwSpMVIDString << endl;
	cout << "=====================================" << endl;

	vector<string> ms;
	string m;

	cout << "Enter list of matrices, q to finalize: " << endl;
	cin >> m;
	while (m != "q") {
		ms.push_back(m);
		cin >> m;
	}

	cout << "Benchmarking " << ms.size() << " matrices..." << endl;
	cout << "=============================================================" << endl;

	bool keysBuilt = false;
	vector<string> keys;

	for (vector<string>::iterator it = ms.begin(); it != ms.end(); ++it) {
		loadSparseMatrixFromSDCard(*it);

		SparseMatrix * A = SparseMatrix::fromMemory(matrixMetaBase);
		A->setName(loadedMatrixName);
		//A->printSummary();

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

		HardwareSpMV * spmv = HWSpMVFactory::make(accBase, resBase, A, x, y);
		// generate + print stat keys on the first run
		if(!keysBuilt) {
			keys = spmv->statKeys();
			keys.push_back("accType");
			keys.push_back("matrix");

			printKeys(keys);
			keysBuilt = true;
		}

		SoftwareSpMV * check = new SoftwareSpMV(A, x);

		spmv->exec();
		check->exec();

		spmv->compareGolden(check->getY());

		printResults(spmv, keys);

		delete spmv;
		delete check;
		free_aligned(x);
		free_aligned(y);

	}
	cout << "=============================================================" << endl;
	cout << "Benchmarking complete" << endl;
	unmount(); // unmount the sd card

	return 0;
}
