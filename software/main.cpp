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
#include "timer.h"

using namespace std;

unsigned int matrixMetaBase = 0x08000100;
unsigned int accBase = 0x40000000;
unsigned int resBase = 0x43c00000;
unsigned int ocmBase = 0x00000040;

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

void printResults(SpMV * spmv, vector<string> keys) {
	for (vector<string>::iterator it = keys.begin(); it != keys.end(); ++it) {
		if (*it == "matrix")
			cout << loadedMatrixName << ",";
		else if (*it == "accType")
			cout << HWSpMVFactory::name(accBase) << ",";
		else
			cout << spmv->statInt(*it) << ",";
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

 medium matrices (around 64K, useful for BufferAll testing)
 2D_54019_highK
 mark3jac140
 cant
 pdb1HYS
 conf5_4-8x8-05
 q

 other matrices (small row/col count, < 32K):
 li
 rim
 bcsstm36
 c-52
 q
 */

void benchmarkSW(vector<string> ms) {
	Xil_DCacheEnable();
	bool keysBuilt = false;
	vector<string> keys;
	for (vector<string>::iterator m = ms.begin(); m != ms.end(); ++m) {
		loadSparseMatrixFromSDCard(*m);

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

		SoftwareSpMV * spmv = new SoftwareSpMV(A, x, y);

		// generate + print stat keys on the first run
		if (!keysBuilt) {
			keys = spmv->statKeys();
			keys.push_back("matrix");

			printKeys (keys);
			keysBuilt = true;
		}

		spmv->exec();
		spmv->measurePreprocessingTimes();
		printResults(spmv, keys);

		free_aligned(x);
		free_aligned(y);
	}
	Xil_DCacheDisable();
}

int main(int argc, char *argv[]) {
	TimerSetup();
	loadedMatrixName = "";
	const bool yInOCM = false;
	Xil_DCacheDisable();
	mount();	// mount the sd card

	vector<string> confs;
	string conf;

	cout
			<< "Enter list of configurations (bitfiles)" << endl <<
			"s to keep current, sw for software, q to finalize: "
			<< endl;
	cin >> conf;
	while (conf != "q") {
		confs.push_back(conf);
		cin >> conf;
	}

	bool CMS;
	cout << "Cold miss skip (0 to disable, 1 to enable): " << endl;
	cin >> CMS;

	//selectBitfile();

	//string hwSpMVIDString = HWSpMVFactory::name(accBase);

	//cout << "SpMVAccel-" << hwSpMVIDString << endl;
	//cout << "=====================================" << endl;

	vector<string> ms;
	string m;

	cout << "Enter list of matrices, q to finalize: " << endl;
	cin >> m;
	while (m != "q") {
		ms.push_back(m);
		cin >> m;
	}

	cout << "Benchmarking " << confs.size() << "x" << ms.size()
			<< " confs x matrices..." << endl;
	cout << "============================================================="
			<< endl;

	bool keysBuilt = false;
	vector<string> keys;

	for (vector<string>::iterator cf = confs.begin(); cf != confs.end(); ++cf) {
		if(*cf == "sw") {
			benchmarkSW(ms);
			break;
		} else
			selectBitfile(*cf);


		for (vector<string>::iterator it = ms.begin(); it != ms.end(); ++it) {
			loadSparseMatrixFromSDCard(*it);

			SparseMatrix * A = SparseMatrix::fromMemory(matrixMetaBase);
			A->setName(loadedMatrixName);
			//A->printSummary();

			SpMVData *x = (SpMVData *) malloc_aligned(64,
					sizeof(SpMVData) * A->getCols());
			SpMVData *y = (SpMVData *) (
					yInOCM ?
							(void *) ocmBase :
							malloc_aligned(64, sizeof(SpMVData) * A->getRows()));

			for (SpMVIndex i = 0; i < A->getCols(); i++) {
				x[i] = (SpMVData) 1;
			}
			for (SpMVIndex i = 0; i < A->getRows(); i++) {
				y[i] = (SpMVData) 0;
			}

			// execute SpMV in software for correctness check
			SoftwareSpMV * check = new SoftwareSpMV(A, x);
			check->exec();

			if (CMS)
				A->markRowStarts();

			HardwareSpMV * spmv = HWSpMVFactory::make(accBase, resBase, A, x,
					y);
			spmv->setThresholds(256, 512, 512, 256);
			// generate + print stat keys on the first run
			if (!keysBuilt) {
				keys = spmv->statKeys();
				keys.push_back("accType");
				keys.push_back("matrix");

				printKeys(keys);
				keysBuilt = true;
			}

			spmv->exec();
			// verify hw result against sw result
			spmv->compareGolden(check->getY());
			printResults(spmv, keys);

			delete spmv;
			delete check;
			free_aligned(x);
			if (!yInOCM)
				free_aligned(y);
		}
	}

	cout << "============================================================="
			<< endl;
	cout << "Benchmarking complete" << endl;
	unmount(); // unmount the sd card

	return 0;
}
