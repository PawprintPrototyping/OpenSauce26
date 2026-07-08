#include "ch32fun.h"
#include "tof.h"

// I2C configuration

#define I2C_CLKRATE  400000
#define I2C_PRERATE  2000000
#define I2C_TIMEOUT  100000

#define I2C_EVT_MASTER_MODE_SELECT              ((uint32_t)0x00030001)
#define I2C_EVT_MASTER_TX_MODE_SELECTED         ((uint32_t)0x00070082)
#define I2C_EVT_MASTER_RX_MODE_SELECTED         ((uint32_t)0x00030002)
#define I2C_EVT_MASTER_BYTE_TRANSMITTED         ((uint32_t)0x00070084)

static uint8_t i2c_addr = TOF_I2C_ADDR;

static inline uint8_t i2c_chk_evt(uint32_t mask) {
    uint32_t status = I2C1->STAR1 | ((uint32_t)I2C1->STAR2 << 16);
    return (status & mask) == mask;
}

static void i2c_setup(void) {
    funGpioInitAll();
    funPinMode(PC1, GPIO_CFGLR_OUT_10Mhz_AF_OD);
    funPinMode(PC2, GPIO_CFGLR_OUT_10Mhz_AF_OD);

    RCC->APB1PCENR |= RCC_APB1Periph_I2C1;

    RCC->APB1PRSTR |= RCC_APB1Periph_I2C1;
    RCC->APB1PRSTR &= ~RCC_APB1Periph_I2C1;

    uint16_t tempreg = I2C1->CTLR2;
    tempreg &= ~I2C_CTLR2_FREQ;
    tempreg |= (FUNCONF_SYSTEM_CORE_CLOCK / I2C_PRERATE) & I2C_CTLR2_FREQ;
    I2C1->CTLR2 = tempreg;

    tempreg = (FUNCONF_SYSTEM_CORE_CLOCK / (3 * I2C_CLKRATE)) & I2C_CKCFGR_CCR;
    tempreg |= I2C_CKCFGR_FS;
    I2C1->CKCFGR = tempreg;

    I2C1->CTLR1 |= I2C_CTLR1_PE;
    I2C1->CTLR1 |= I2C_CTLR1_ACK;
}

static int8_t i2c_write(uint16_t reg, const uint8_t *data, uint16_t len) {
    int32_t timeout;

    timeout = I2C_TIMEOUT;
    while ((I2C1->STAR2 & I2C_STAR2_BUSY) && (timeout--));
    if (timeout < 0) return -1;

    I2C1->CTLR1 |= I2C_CTLR1_START;

    timeout = I2C_TIMEOUT;
    while (!i2c_chk_evt(I2C_EVT_MASTER_MODE_SELECT) && (timeout--));
    if (timeout < 0) return -1;

    I2C1->DATAR = i2c_addr << 1;

    timeout = I2C_TIMEOUT;
    while (!i2c_chk_evt(I2C_EVT_MASTER_TX_MODE_SELECTED) && (timeout--));
    if (timeout < 0) return -1;

    // 16-bit register address, MSB first
    timeout = I2C_TIMEOUT;
    while (!(I2C1->STAR1 & I2C_STAR1_TXE) && (timeout--));
    if (timeout < 0) return -1;
    I2C1->DATAR = (reg >> 8) & 0xFF;

    timeout = I2C_TIMEOUT;
    while (!(I2C1->STAR1 & I2C_STAR1_TXE) && (timeout--));
    if (timeout < 0) return -1;
    I2C1->DATAR = reg & 0xFF;

    for (uint16_t i = 0; i < len; i++) {
        timeout = I2C_TIMEOUT;
        while (!(I2C1->STAR1 & I2C_STAR1_TXE) && (timeout--));
        if (timeout < 0) return -1;
        I2C1->DATAR = data[i];
    }

    timeout = I2C_TIMEOUT;
    while (!i2c_chk_evt(I2C_EVT_MASTER_BYTE_TRANSMITTED) && (timeout--));
    if (timeout < 0) return -1;

    I2C1->CTLR1 |= I2C_CTLR1_STOP;
    return 0;
}

static int8_t i2c_read(uint16_t reg, uint8_t *data, uint16_t len) {
    int32_t timeout;

    // Phase 1: write register address
    timeout = I2C_TIMEOUT;
    while ((I2C1->STAR2 & I2C_STAR2_BUSY) && (timeout--));
    if (timeout < 0) return -1;

    I2C1->CTLR1 |= I2C_CTLR1_START;

    timeout = I2C_TIMEOUT;
    while (!i2c_chk_evt(I2C_EVT_MASTER_MODE_SELECT) && (timeout--));
    if (timeout < 0) return -1;

    I2C1->DATAR = i2c_addr << 1;

    timeout = I2C_TIMEOUT;
    while (!i2c_chk_evt(I2C_EVT_MASTER_TX_MODE_SELECTED) && (timeout--));
    if (timeout < 0) return -1;

    timeout = I2C_TIMEOUT;
    while (!(I2C1->STAR1 & I2C_STAR1_TXE) && (timeout--));
    if (timeout < 0) return -1;
    I2C1->DATAR = (reg >> 8) & 0xFF;

    timeout = I2C_TIMEOUT;
    while (!(I2C1->STAR1 & I2C_STAR1_TXE) && (timeout--));
    if (timeout < 0) return -1;
    I2C1->DATAR = reg & 0xFF;

    timeout = I2C_TIMEOUT;
    while (!i2c_chk_evt(I2C_EVT_MASTER_BYTE_TRANSMITTED) && (timeout--));
    if (timeout < 0) return -1;

    // Phase 2: repeated start, read data
    I2C1->CTLR1 |= I2C_CTLR1_START;

    timeout = I2C_TIMEOUT;
    while (!i2c_chk_evt(I2C_EVT_MASTER_MODE_SELECT) && (timeout--));
    if (timeout < 0) return -1;

    I2C1->DATAR = (i2c_addr << 1) | 1;

    timeout = I2C_TIMEOUT;
    while (!i2c_chk_evt(I2C_EVT_MASTER_RX_MODE_SELECTED) && (timeout--));
    if (timeout < 0) return -1;

    for (uint16_t i = 0; i < len; i++) {
        if (i == len - 1) {
            I2C1->CTLR1 &= ~I2C_CTLR1_ACK;
            I2C1->CTLR1 |= I2C_CTLR1_STOP;
        }
        timeout = I2C_TIMEOUT;
        while (!(I2C1->STAR1 & I2C_STAR1_RXNE) && (timeout--));
        if (timeout < 0) return -1;
        data[i] = I2C1->DATAR;
    }

    I2C1->CTLR1 |= I2C_CTLR1_ACK;
    return 0;
}

// Register access helpers

static int8_t tof_wr_byte(uint16_t reg, uint8_t val) {
    return i2c_write(reg, &val, 1);
}

static int8_t tof_wr_word(uint16_t reg, uint16_t val) {
    uint8_t buf[2] = { val >> 8, val & 0xFF };
    return i2c_write(reg, buf, 2);
}

static int8_t tof_wr_dword(uint16_t reg, uint32_t val) {
    uint8_t buf[4] = {
        (val >> 24) & 0xFF,
        (val >> 16) & 0xFF,
        (val >>  8) & 0xFF,
        val & 0xFF
    };
    return i2c_write(reg, buf, 4);
}

static int8_t tof_rd_byte(uint16_t reg, uint8_t *val) {
    return i2c_read(reg, val, 1);
}

static int8_t tof_rd_word(uint16_t reg, uint16_t *val) {
    uint8_t buf[2];
    int8_t s = i2c_read(reg, buf, 2);
    if (!s) *val = ((uint16_t)buf[0] << 8) | buf[1];
    return s;
}

// VL53L1X register1s

#define VL53L1X_VHV_CONFIG_TIMEOUT_MACROP_LOOP_BOUND  0x0008
#define VL53L1X_FIRMWARE_SYSTEM_STATUS                0x00E5
#define VL53L1X_IDENTIFICATION_MODEL_ID               0x010F
#define VL53L1X_RESULT_OSC_CALIBRATE_VAL              0x00DE
#define VL53L1X_SYSTEM_INTERMEASUREMENT_PERIOD        0x006C
#define VL53L1X_RESULT_RANGE_STATUS                   0x0089
#define VL53L1X_RESULT_FINAL_RANGE_MM_SD0             0x0096

#define GPIO_HV_MUX_CTRL                0x0030
#define GPIO_TIO_HV_STATUS              0x0031
#define SYSTEM_INTERRUPT_CONFIG_GPIO     0x0046
#define PHASECAL_CONFIG_TIMEOUT_MACROP   0x004B
#define RANGE_CONFIG_TIMEOUT_MACROP_A_HI 0x005E
#define RANGE_CONFIG_VCSEL_PERIOD_A      0x0060
#define RANGE_CONFIG_TIMEOUT_MACROP_B_HI 0x0061
#define RANGE_CONFIG_VCSEL_PERIOD_B      0x0063
#define RANGE_CONFIG_VALID_PHASE_HIGH    0x0069
#define SD_CONFIG_WOI_SD0                0x0078
#define SD_CONFIG_INITIAL_PHASE_SD0      0x007A
#define SYSTEM_INTERRUPT_CLEAR           0x0086
#define SYSTEM_MODE_START                0x0087

// 91 bytes written to registers 0x2D..0x87 during init
static const uint8_t vl53l1x_default_config[] = {
    0x00, 0x00, 0x00, 0x01, 0x02, 0x00, 0x02, 0x08,
    0x00, 0x08, 0x10, 0x01, 0x01, 0x00, 0x00, 0x00,
    0x00, 0xff, 0x00, 0x0F, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x20, 0x0b, 0x00, 0x00, 0x02, 0x0a, 0x21,
    0x00, 0x00, 0x05, 0x00, 0x00, 0x00, 0x00, 0xc8,
    0x00, 0x00, 0x38, 0xff, 0x01, 0x00, 0x08, 0x00,
    0x00, 0x01, 0xcc, 0x0f, 0x01, 0xf1, 0x0d, 0x01,
    0x68, 0x00, 0x80, 0x08, 0xb8, 0x00, 0x00, 0x00,
    0x00, 0x0f, 0x89, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x01, 0x0f, 0x0d, 0x0e, 0x0e, 0x00,
    0x00, 0x02, 0xc7, 0xff, 0x9B, 0x00, 0x00, 0x00,
    0x01, 0x00, 0x00
};

int8_t tof_get_sensor_id(uint16_t *id) {
    return tof_rd_word(VL53L1X_IDENTIFICATION_MODEL_ID, id);
}

int8_t tof_start_ranging(void) {
    return tof_wr_byte(SYSTEM_MODE_START, 0x40);
}

int8_t tof_stop_ranging(void) {
    return tof_wr_byte(SYSTEM_MODE_START, 0x00);
}

int8_t tof_clear_interrupt(void) {
    return tof_wr_byte(SYSTEM_INTERRUPT_CLEAR, 0x01);
}

int8_t tof_check_data_ready(uint8_t *ready) {
    uint8_t gpio_hv, polarity;
    int8_t s;

    s = tof_rd_byte(GPIO_HV_MUX_CTRL, &gpio_hv);
    if (s) return s;
    polarity = !((gpio_hv >> 4) & 1);

    s = tof_rd_byte(GPIO_TIO_HV_STATUS, &gpio_hv);
    if (s) return s;

    *ready = ((gpio_hv & 1) == polarity) ? 1 : 0;
    return 0;
}

int8_t tof_get_distance(uint16_t *distance_mm) {
    return tof_rd_word(VL53L1X_RESULT_FINAL_RANGE_MM_SD0, distance_mm);
}

int8_t tof_get_range_status(uint8_t *status) {
    uint8_t raw;
    int8_t s = tof_rd_byte(VL53L1X_RESULT_RANGE_STATUS, &raw);
    if (s) return s;

    raw &= 0x1F;
    switch (raw) {
        case 9:  *status = 0; break;
        case 6:  *status = 1; break;
        case 4:  *status = 2; break;
        case 8:  *status = 3; break;
        case 5:  *status = 4; break;
        case 3:  *status = 5; break;
        case 19: *status = 6; break;
        case 7:  *status = 7; break;
        case 12: *status = 9; break;
        case 18: *status = 10; break;
        case 22: *status = 11; break;
        case 23: *status = 12; break;
        case 13: *status = 13; break;
        default: *status = 255; break;
    }
    return 0;
}

int8_t tof_set_distance_mode(uint16_t mode) {
    int8_t s;
    switch (mode) {
        case 1:
            s = tof_wr_byte(PHASECAL_CONFIG_TIMEOUT_MACROP, 0x14);
            if (s) return s;
            s = tof_wr_byte(RANGE_CONFIG_VCSEL_PERIOD_A, 0x07);
            if (s) return s;
            s = tof_wr_byte(RANGE_CONFIG_VCSEL_PERIOD_B, 0x05);
            if (s) return s;
            s = tof_wr_byte(RANGE_CONFIG_VALID_PHASE_HIGH, 0x38);
            if (s) return s;
            s = tof_wr_word(SD_CONFIG_WOI_SD0, 0x0705);
            if (s) return s;
            s = tof_wr_word(SD_CONFIG_INITIAL_PHASE_SD0, 0x0606);
            break;
        case 2:
            s = tof_wr_byte(PHASECAL_CONFIG_TIMEOUT_MACROP, 0x0A);
            if (s) return s;
            s = tof_wr_byte(RANGE_CONFIG_VCSEL_PERIOD_A, 0x0F);
            if (s) return s;
            s = tof_wr_byte(RANGE_CONFIG_VCSEL_PERIOD_B, 0x0D);
            if (s) return s;
            s = tof_wr_byte(RANGE_CONFIG_VALID_PHASE_HIGH, 0xB8);
            if (s) return s;
            s = tof_wr_word(SD_CONFIG_WOI_SD0, 0x0F0D);
            if (s) return s;
            s = tof_wr_word(SD_CONFIG_INITIAL_PHASE_SD0, 0x0E0E);
            break;
        default:
            return -1;
    }
    return s;
}

int8_t tof_set_timing_budget_ms(uint16_t budget_ms) {
    uint16_t dm;
    uint8_t tmp;
    int8_t s;

    s = tof_rd_byte(PHASECAL_CONFIG_TIMEOUT_MACROP, &tmp);
    if (s) return s;
    dm = (tmp == 0x14) ? 1 : 2;

    uint16_t a, b;
    if (dm == 1) {
        switch (budget_ms) {
            case 15:  a = 0x001D; b = 0x0027; break;
            case 20:  a = 0x0051; b = 0x006E; break;
            case 33:  a = 0x00D6; b = 0x006E; break;
            case 50:  a = 0x01AE; b = 0x01E8; break;
            case 100: a = 0x02E1; b = 0x0388; break;
            case 200: a = 0x03E1; b = 0x0496; break;
            case 500: a = 0x0591; b = 0x05C1; break;
            default: return -1;
        }
    } else {
        switch (budget_ms) {
            case 20:  a = 0x001E; b = 0x0022; break;
            case 33:  a = 0x0060; b = 0x006E; break;
            case 50:  a = 0x00AD; b = 0x00C6; break;
            case 100: a = 0x01CC; b = 0x01EA; break;
            case 200: a = 0x02D9; b = 0x02F8; break;
            case 500: a = 0x048F; b = 0x04A4; break;
            default: return -1;
        }
    }

    s = tof_wr_word(RANGE_CONFIG_TIMEOUT_MACROP_A_HI, a);
    if (s) return s;
    return tof_wr_word(RANGE_CONFIG_TIMEOUT_MACROP_B_HI, b);
}

int8_t tof_set_inter_measurement_ms(uint16_t period_ms) {
    uint16_t osc;
    int8_t s = tof_rd_word(VL53L1X_RESULT_OSC_CALIBRATE_VAL, &osc);
    if (s) return s;
    osc &= 0x3FF;
    uint32_t val = (uint32_t)((float)osc * (float)period_ms * 1.075f);
    return tof_wr_dword(VL53L1X_SYSTEM_INTERMEASUREMENT_PERIOD, val);
}

int8_t tof_init(void) {
    int8_t s;
    uint8_t boot = 0;

    i2c_setup();
    Delay_Ms(2);

    // Wait for sensor to boot
    for (int i = 0; i < 1000 && !boot; i++) {
        s = tof_rd_byte(VL53L1X_FIRMWARE_SYSTEM_STATUS, &boot);
        if (s) return s;
        Delay_Ms(2);
    }
    if (!boot) return -1;

    // Write default configuration blob (registers 0x2D to 0x87)
    for (uint8_t addr = 0x2D; addr <= 0x87; addr++) {
        s = tof_wr_byte(addr, vl53l1x_default_config[addr - 0x2D]);
        if (s) return s;
    }

    // Do one ranging cycle to validate
    s = tof_start_ranging();
    if (s) return s;

    uint8_t ready = 0;
    for (int i = 0; i < 1000 && !ready; i++) {
        s = tof_check_data_ready(&ready);
        if (s) return s;
        Delay_Ms(1);
    }

    s = tof_clear_interrupt();
    if (s) return s;
    s = tof_stop_ranging();
    if (s) return s;

    // VHV config: two bounds
    s = tof_wr_byte(VL53L1X_VHV_CONFIG_TIMEOUT_MACROP_LOOP_BOUND, 0x09);
    if (s) return s;
    s = tof_wr_byte(0x0B, 0x00);
    if (s) return s;

    // Default: long distance mode, 100ms timing budget, 100ms inter-measurement
    s = tof_set_distance_mode(TOF_DISTANCE_MODE_LONG);
    if (s) return s;
    s = tof_set_timing_budget_ms(100);
    if (s) return s;
    s = tof_set_inter_measurement_ms(100);

    return s;
}
