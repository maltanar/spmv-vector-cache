/******************************************************************************
 *
 * (c) Copyright 2011-2012 Xilinx, Inc. All rights reserved.
 *
 * This file contains confidential and proprietary information of Xilinx, Inc.
 * and is protected under U.S. and international copyright and other
 * intellectual property laws.
 *
 * DISCLAIMER
 * This disclaimer is not a license and does not grant any rights to the
 * materials distributed herewith. Except as otherwise provided in a valid
 * license issued to you by Xilinx, and to the maximum extent permitted by
 * applicable law: (1) THESE MATERIALS ARE MADE AVAILABLE "AS IS" AND WITH ALL
 * FAULTS, AND XILINX HEREBY DISCLAIMS ALL WARRANTIES AND CONDITIONS, EXPRESS,
 * IMPLIED, OR STATUTORY, INCLUDING BUT NOT LIMITED TO WARRANTIES OF
 * MERCHANTABILITY, NON-INFRINGEMENT, OR FITNESS FOR ANY PARTICULAR PURPOSE;
 * and (2) Xilinx shall not be liable (whether in contract or tort, including
 * negligence, or under any other theory of liability) for any loss or damage
 * of any kind or nature related to, arising under or in connection with these
 * materials, including for any direct, or any indirect, special, incidental,
 * or consequential loss or damage (including loss of data, profits, goodwill,
 * or any type of loss or damage suffered as a result of any action brought by
 * a third party) even if such damage or loss was reasonably foreseeable or
 * Xilinx had been advised of the possibility of the same.
 *
 * CRITICAL APPLICATIONS
 * Xilinx products are not designed or intended to be fail-safe, or for use in
 * any application requiring fail-safe performance, such as life-support or
 * safety devices or systems, Class III medical devices, nuclear facilities,
 * applications related to the deployment of airbags, or any other applications
 * that could lead to death, personal injury, or severe property or
 * environmental damage (individually and collectively, "Critical
 * Applications"). Customer assumes the sole risk and liability of any use of
 * Xilinx products in Critical Applications, subject only to applicable laws
 * and regulations governing limitations on product liability.
 *
 * THIS COPYRIGHT NOTICE AND DISCLAIMER MUST BE RETAINED AS PART OF THIS FILE
 * AT ALL TIMES.
 ******************************************************************************/

#include <stdlib.h>
#include <assert.h>
#include "devcfg.h"
#include "xil_printf.h"
#include "sdcard.h"
#include "malloc_aligned.h"
// Pieced together from code in Xilinx app notes and FSBL sources code

/*
 * SLCR registers
 */
#define SLCR_LOCK	   0xF8000004 /**< SLCR Write Protection Lock */
#define SLCR_UNLOCK	   0xF8000008 /**< SLCR Write Protection Unlock */
#define FPGA_RST_CTRL  0xF8000240 /**< FPGA Software Reset Control */
#define LVL_SHFTR_EN   0xF8000900 /**< FPGA Level Shifters Enable */

#define SLCR_LOCK_VAL	0x767B
#define SLCR_UNLOCK_VAL	0xDF0D

#include <iostream>
#include <string>
using namespace std;

static XDcfg * cfg = 0;

void selectBitfile(string fileName) {
	if(fileName == "s") return;
	string bfn = ("binfiles/" + fileName + ".bin");
	//cout << "full path:" << bfn << endl;
	unsigned int sz = getFileSize(bfn.c_str());
	//cout << "reading bitfile from sd card, bytes: " << sz << endl;
	void * bitfileMem = malloc_aligned(64, sz);
	readFromSDCard(bfn.c_str(), (unsigned int) bitfileMem);
	if (cfg == 0) {
		//cout << "initializing xdcfg" << endl;
		cfg = XDcfg_Initialize(XPAR_XDCFG_0_DEVICE_ID);
	}

	//cout << "attempting to upload bitstream" << endl;

	int ret = XDcfg_TransferBitfile(cfg, (u32) bitfileMem, sz >> 2);

	if (ret == XST_SUCCESS) {
		//cout << "Success!" << endl;
		}
	else
		cout << "Bitfile upload failed with code " << ret << endl;

	free_aligned(bitfileMem);
}

void selectBitfile() {
	cout << "enter bitfile name: " << endl;
	string bfn;
	cin >> bfn;
	selectBitfile(bfn);
}

XDcfg *XDcfg_Initialize(u16 DeviceId) {
	XDcfg *Instance = (XDcfg *) malloc(sizeof(XDcfg));
	XDcfg_Config *Config = XDcfg_LookupConfig(DeviceId);
	int t = XDcfg_CfgInitialize(Instance, Config, Config->BaseAddr);

	assert(t == XST_SUCCESS);

	// Enable
	XDcfg_EnablePCAP(Instance);
	// select PCAP interface for partial reconfiguration
	XDcfg_SetControlRegister(Instance, XDCFG_CTRL_PCAP_PR_MASK);

	// TODO make sure loopback is disabled
	// XDcfg_SetMiscControlRegister(Instance, XDCFG_MCTRL_PCAP_LPBK_MASK);

	return Instance;
}

typedef volatile unsigned int * devreg;

void unlock_slcr() {
	devreg unlock = (devreg) SLCR_UNLOCK;
	*unlock = SLCR_UNLOCK_VAL;
}

void lock_slcr() {
	devreg lock = (devreg) SLCR_LOCK;
	*lock = SLCR_LOCK_VAL;
}

void resetLogic(int enable) {
	devreg reset = (devreg) FPGA_RST_CTRL;
	if (enable)
		*reset = *reset | 0x0000000F;
	else
		*reset = *reset & ~(0x0000000F);
}

int XDcfg_TransferBitfile(XDcfg *Instance, u32 StartAddress, u32 WordLength) {
	int Status;
	volatile u32 IntrStsReg = 0;
	devreg levelshifters = (devreg) LVL_SHFTR_EN;

	// unlock system control registers
	unlock_slcr();
	// PS->PL shifters only
	*levelshifters = 0xA;

	u32 PcapReg;
	u32 PcapCtrlRegVal;

	PcapReg = XDcfg_ReadReg(Instance->Config.BaseAddr, XDCFG_CTRL_OFFSET);

	/*
	 * Setting PCFG_PROG_B signal to high
	 */
	XDcfg_WriteReg(Instance->Config.BaseAddr, XDCFG_CTRL_OFFSET,
			(PcapReg | XDCFG_CTRL_PCFG_PROG_B_MASK));

	/*
	 * Setting PCFG_PROG_B signal to low
	 */
	XDcfg_WriteReg(Instance->Config.BaseAddr, XDCFG_CTRL_OFFSET,
			(PcapReg & ~XDCFG_CTRL_PCFG_PROG_B_MASK));

	/*
	 * Polling the PCAP_INIT status for Reset
	 */
	while (XDcfg_GetStatusRegister(Instance) &
	XDCFG_STATUS_PCFG_INIT_MASK)
		;

	/*
	 * Setting PCFG_PROG_B signal to high
	 */
	XDcfg_WriteReg(Instance->Config.BaseAddr, XDCFG_CTRL_OFFSET,
			(PcapReg | XDCFG_CTRL_PCFG_PROG_B_MASK));

	/*
	 * Polling the PCAP_INIT status for Set
	 */
	while (!(XDcfg_GetStatusRegister(Instance) &
	XDCFG_STATUS_PCFG_INIT_MASK))
		;

	/***************************************************************/

	// Clear DMA and PCAP Done Interrupts
	XDcfg_IntrClear(Instance, XDCFG_IXR_D_P_DONE_MASK);

	// Transfer bitstream from DDR into fabric in non secure mode
	Status = XDcfg_Transfer(Instance, (u32 *) StartAddress, WordLength,
			(u32 *) XDCFG_DMA_INVALID_ADDRESS, 0, XDCFG_NON_SECURE_PCAP_WRITE);
	if (Status != XST_SUCCESS)
		return Status;

	// Poll PCAP Done Interrupt
	while ((IntrStsReg & XDCFG_IXR_D_P_DONE_MASK) != XDCFG_IXR_D_P_DONE_MASK)
		IntrStsReg = XDcfg_IntrGetStatus(Instance);

	// enable reset
	resetLogic(1);
	// enable level shifters
	*levelshifters = 0xF;
	// disable reset
	resetLogic(0);
	// lock system control registers
	lock_slcr();

	return XST_SUCCESS;
}
