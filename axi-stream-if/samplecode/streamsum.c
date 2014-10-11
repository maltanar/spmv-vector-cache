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

typedef unsigned long int u64_t;

#define DMA_DEV_ID		XPAR_AXIDMA_0_DEVICE_ID
#define DDR_BASE_ADDR	(0x10000000)
#define DATA_OFFSET		(0x00001000)

XAxiDma AxiDma;

volatile u64_t * streamSumBase = (volatile u64_t *) 0x43c00000;
volatile unsigned int * indexBuffer = (volatile unsigned int *) DDR_BASE_ADDR;
volatile u64_t * dataBuffer = (volatile u64_t *) (DDR_BASE_ADDR + DATA_OFFSET);

#define	REG_ACCEL_ID	(0 << 1)
#define	REG_STREAM_SUM	(1 << 1)
#define REG_ELEM_CNT	(2 << 1)

void printStreamSumBaseStatus()
{
	printf("StreamSum accelerator ID: %lx\n", streamSumBase[REG_ACCEL_ID]);
	printf("Current stream sum: %lu\n", streamSumBase[REG_STREAM_SUM]);
	printf("Current element count: %lu\n", streamSumBase[REG_ELEM_CNT]);
}

int initDMA(u16 DeviceId)
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

	return XST_SUCCESS;

}

void doSomeDMA(unsigned int wordCount)
{
	// put some values into the DDR for the sum accelerator to pick up
	unsigned int i;
	int Status;

	if(wordCount >= DATA_OFFSET/4)
	{
		xil_printf("Too many elements! Max: %d \n", DATA_OFFSET/4);
		return;
	}


	unsigned int checksum = 0;
	for(i = 0; i < wordCount; i++)
	{
		indexBuffer[i] = (unsigned int) &(dataBuffer[i]);
		dataBuffer[i] = i+1;
		checksum += i;
		/*printf("indexBuffer[i] = %x\n", indexBuffer[i]);
		printf("dataBuffer[i] = %x\n", dataBuffer[i]);*/
	}
/*
	for(i = 0; i < wordCount; i++)
		if ( indexBuffer[i] == &(dataBuffer[i]))
			xil_printf("memcheck error!\n");
		else
			xil_printf("memcheck OK\n");
			*/

	/*xil_printf("DMA has MM2S: %d, has S2MM: %d, chan max transfer %d\n", AxiDma.HasMm2S, AxiDma.HasS2Mm, AxiDma.TxBdRing.MaxTransferLen);

	xil_printf("Data width: %d\n", AxiDma.TxBdRing.DataWidth);*/

	Xil_DCacheFlush();

	u64_t prev= streamSumBase[REG_STREAM_SUM];
	Status = XAxiDma_SimpleTransfer(&AxiDma, (u32) (indexBuffer), wordCount*4, XAXIDMA_DMA_TO_DEVICE);

	if( Status == XST_SUCCESS)
		xil_printf("Device to DMA OK!\n");
	else
		xil_printf("Device to DMA failed: %d\n", Status);

	while(XAxiDma_Busy(&AxiDma, XAXIDMA_DMA_TO_DEVICE));
	u64_t curr = streamSumBase[REG_STREAM_SUM];

	xil_printf("Sum should be %d, delta is %d\n", checksum,curr-prev);

}

int main()
{
    init_platform();
    //Xil_DCacheDisable();

    initDMA(DMA_DEV_ID);

    xil_printf("Hello World\n\r");

    while(1)
    {
        printStreamSumBaseStatus();
    	unsigned int wc = 0;
    	printf("Bytecount: ");
    	scanf("%d", &wc);

    	if(wc != 0)
    		doSomeDMA(wc);
    }

    return 0;
}

