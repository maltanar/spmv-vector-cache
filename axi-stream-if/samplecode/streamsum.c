/*
 * Copyright (c) 2009-2012 Xilinx, Inc.  All rights reserved.
 *
 * Xilinx, Inc.
 * XILINX IS PROVIDING THIS DESIGN, CODE, OR INFORMATION "AS IS" AS A
 * COURTESY TO YOU.  BY PROVIDING THIS DESIGN, CODE, OR INFORMATION AS
 * ONE POSSIBLE   IMPLEMENTATION OF THIS FEATURE, APPLICATION OR
 * STANDARD, XILINX IS MAKING NO REPRESENTATION THAT THIS IMPLEMENTATION
 * IS FREE FROM ANY CLAIMS OF INFRINGEMENT, AND YOU ARE RESPONSIBLE
 * FOR OBTAINING ANY RIGHTS YOU MAY REQUIRE FOR YOUR IMPLEMENTATION.
 * XILINX EXPRESSLY DISCLAIMS ANY WARRANTY WHATSOEVER WITH RESPECT TO
 * THE ADEQUACY OF THE IMPLEMENTATION, INCLUDING BUT NOT LIMITED TO
 * ANY WARRANTIES OR REPRESENTATIONS THAT THIS IMPLEMENTATION IS FREE
 * FROM CLAIMS OF INFRINGEMENT, IMPLIED WARRANTIES OF MERCHANTABILITY
 * AND FITNESS FOR A PARTICULAR PURPOSE.
 *
 */

/*
 * helloworld.c: simple test application
 *
 * This application configures UART 16550 to baud rate 9600.
 * PS7 UART (Zynq) is not initialized by this application, since
 * bootrom/bsp configures it to baud rate 115200
 *
 * ------------------------------------------------
 * | UART TYPE   BAUD RATE                        |
 * ------------------------------------------------
 *   uartns550   9600
 *   uartlite    Configurable only in HW design
 *   ps7_uart    115200 (configured by bootrom/bsp)
 */

#include <stdio.h>
#include "platform.h"
#include "xaxidma.h"

#define DMA_DEV_ID		XPAR_AXIDMA_0_DEVICE_ID
#define DDR_BASE_ADDR	(0x10000000)
#define OCM_OFFSET		(0x00000000)

XAxiDma AxiDma;

volatile unsigned int * streamSumBase = (volatile unsigned int *) 0x43c00000;
volatile unsigned int * ramBufferBase = (volatile unsigned int *) DDR_BASE_ADDR;

#define	REG_ACCEL_ID	0
#define	REG_STREAM_SUM	1
#define REG_ELEM_CNT	2

void printStreamSumBaseStatus()
{
	xil_printf("StreamSum accelerator ID: %x\n", streamSumBase[REG_ACCEL_ID]);
	xil_printf("Current stream sum: %d\n", streamSumBase[REG_STREAM_SUM]);
	xil_printf("Current element count: %d\n", streamSumBase[REG_ELEM_CNT]);
}

void doSomeDMA(u16 DeviceId, unsigned int wordCount)
{
	XAxiDma_Config *CfgPtr;
	int Status;

	/* Initialize the XAxiDma device.
	 */
	CfgPtr = XAxiDma_LookupConfig(DeviceId);
	if (!CfgPtr) {
		xil_printf("No config found for %d\r\n", DeviceId);
		return XST_FAILURE;
	}

	Status = XAxiDma_CfgInitialize(&AxiDma, CfgPtr);
	if (Status != XST_SUCCESS) {
		xil_printf("Initialization failed %d\r\n", Status);
		return XST_FAILURE;
	}

	if(XAxiDma_HasSg(&AxiDma)){
		xil_printf("Device configured as SG mode \r\n");
		return XST_FAILURE;
	}

	/* Disable interrupts, we use polling mode
	 */
	XAxiDma_IntrDisable(&AxiDma, XAXIDMA_IRQ_ALL_MASK,
						XAXIDMA_DEVICE_TO_DMA);
	XAxiDma_IntrDisable(&AxiDma, XAXIDMA_IRQ_ALL_MASK,
						XAXIDMA_DMA_TO_DEVICE);

	// put some values into the DDR for the sum accelerator to pick up
	unsigned int i;

	memset(ramBufferBase,0,8192);
	/*for(i = 0; i < wordCount; i++)
		ramBufferBase[i] = i+1;

	for(i = 0; i < wordCount; i++)
		if ( ramBufferBase[i] != i+1)
			xil_printf("memcheck error!\n");
		else
			xil_printf("memcheck OK\n");
			*/

	xil_printf("DMA has MM2S: %d, has S2MM: %d, chan max transfer %d\n", AxiDma.HasMm2S, AxiDma.HasS2Mm, AxiDma.TxBdRing.MaxTransferLen);

	xil_printf("Data width: %d\n", AxiDma.TxBdRing.DataWidth);

	Xil_DCacheFlush();


	unsigned int prev = streamSumBase[REG_STREAM_SUM];
	Status = XAxiDma_SimpleTransfer(&AxiDma, (u32) (ramBufferBase+OCM_OFFSET), wordCount, XAXIDMA_DMA_TO_DEVICE);
	if( Status == XST_SUCCESS)
		xil_printf("Device to DMA OK!\n");
	else
		xil_printf("Device to DMA failed: %d\n", Status);

	while(XAxiDma_Busy(&AxiDma, XAXIDMA_DMA_TO_DEVICE));


	unsigned int curr = streamSumBase[REG_STREAM_SUM];

		xil_printf("curr-prev = %d (%x)\n", curr-prev, curr-prev);
}

int main()
{
    init_platform();
    //Xil_DCacheDisable();

    xil_printf("Hello World\n\r");

    while(1)
    {
        printStreamSumBaseStatus();
    	unsigned int wc = 0;
    	printf("Bytecount: ");
    	scanf("%d", &wc);

    	doSomeDMA(DMA_DEV_ID, wc);
    }







    return 0;
}
