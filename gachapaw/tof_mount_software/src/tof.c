#include "ch32fun.h"

#define TOF_ADDRESS 0x52

void tof_init_i2c(){
    funPinMode(PC1, GPIO_CFGLR_OUT_10Mhz_AF_OD); // SDA
    funPinMode(PC2, GPIO_CFGLR_OUT_10Mhz_AF_OD); // SCL

    // Most of this is copy pasted from the i2c_slave.h
    // Enable I2C1
    RCC->APB1PCENR |= RCC_APB1Periph_I2C1;

    // Reset I2C1 to init all regs
    RCC->APB1PRSTR |= RCC_APB1Periph_I2C1;
    RCC->APB1PRSTR &= ~RCC_APB1Periph_I2C1;

    I2C1->CTLR1 |= I2C_CTLR1_SWRST;
    I2C1->CTLR1 &= ~I2C_CTLR1_SWRST;


    // Set module clock frequency
    uint32_t prerate = 2000000; // I2C Logic clock rate, must be higher than the bus clock rate
    I2C1->CTLR2 |= (FUNCONF_SYSTEM_CORE_CLOCK/prerate) & I2C_CTLR2_FREQ;

    // Skipping all the interrupt stuff cause it probably doesn't matter

    // Clock config
    uint32_t clockrate = 1000000; // I2C Bus clock rate, must be lower than the logic clock rate
    I2C1->CKCFGR = ((FUNCONF_SYSTEM_CORE_CLOCK/(3*clockrate))&I2C_CKCFGR_CCR) | I2C_CKCFGR_FS; // Fast mode 33% duty cycle
    

    // Set I2C address
    I2C1->OADDR1 = TOF_ADDRESS << 1;
    I2C1->OADDR2 = 0;

    // Enable I2C
    I2C1->CTLR1 |= I2C_CTLR1_PE;

    // Acknowledge bytes when they are received
    I2C1->CTLR1 |= I2C_CTLR1_ACK;
}