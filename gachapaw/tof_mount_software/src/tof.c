#include "ch32fun.h"
#include <stdio.h>

#define CH32V003_I2C_IMPLEMENTATION
#include "ch32v003_i2c.h"
#include "tof.h"

#define VL53L1X_ADDRESS 0x29

// Pin Definitions
#define PIN_XSHUT PC3

// Register Addresses
#define SOFT_RESET                                  0x0000
#define PAD_I2C_HV__EXTSUP_CONFIG                   0x002E
#define OSC_MEASURED__FAST_OSC__FREQUENCY           0x0006
#define RESULT__OSC_CALIBRATE_VAL                   0x00DE
#define DSS_CONFIG__TARGET_TOTAL_RATE_MCPS          0x0024
#define GPIO__TIO_HV_STATUS                         0x0031
#define SIGMA_ESTIMATOR__EFFECTIVE_PULSE_WIDTH_NS   0x0036
#define SIGMA_ESTIMATOR__EFFECTIVE_AMBIENT_WIDTH_NS 0x0037
#define ALGO__CROSSTALK_COMPENSATION_VALID_HEIGHT_MM 0x0039
#define ALGO__RANGE_IGNORE_VALID_HEIGHT_MM          0x003E
#define ALGO__RANGE_MIN_CLIP                        0x003F
#define ALGO__CONSISTENCY_CHECK__TOLERANCE           0x0040
#define SYSTEM__THRESH_RATE_HIGH                    0x0050
#define SYSTEM__THRESH_RATE_LOW                     0x0052
#define DSS_CONFIG__APERTURE_ATTENUATION            0x0057
#define RANGE_CONFIG__SIGMA_THRESH                  0x0064
#define RANGE_CONFIG__MIN_COUNT_RATE_RTN_LIMIT_MCPS 0x0066
#define SYSTEM__GROUPED_PARAMETER_HOLD_0            0x0071
#define SYSTEM__GROUPED_PARAMETER_HOLD_1            0x007C
#define SD_CONFIG__QUANTIFIER                       0x007E
#define SYSTEM__GROUPED_PARAMETER_HOLD              0x0082
#define SYSTEM__SEED_CONFIG                         0x0077
#define SYSTEM__SEQUENCE_CONFIG                     0x0081
#define DSS_CONFIG__MANUAL_EFFECTIVE_SPADS_SELECT   0x0054
#define DSS_CONFIG__ROI_MODE_CONTROL                0x004F
#define ALGO__PART_TO_PART_RANGE_OFFSET_MM          0x001E
#define MM_CONFIG__OUTER_OFFSET_MM                  0x0022
#define RANGE_CONFIG__VCSEL_PERIOD_A                0x0060
#define RANGE_CONFIG__VCSEL_PERIOD_B                0x0063
#define RANGE_CONFIG__VALID_PHASE_HIGH              0x0069
#define SD_CONFIG__WOI_SD0                          0x0078
#define SD_CONFIG__WOI_SD1                          0x0079
#define SD_CONFIG__INITIAL_PHASE_SD0                0x007A
#define SD_CONFIG__INITIAL_PHASE_SD1                0x007B
#define PHASECAL_CONFIG__TIMEOUT_MACROP             0x004B
#define MM_CONFIG__TIMEOUT_MACROP_A                 0x005A
#define RANGE_CONFIG__TIMEOUT_MACROP_A              0x005E
#define MM_CONFIG__TIMEOUT_MACROP_B                 0x005C
#define RANGE_CONFIG__TIMEOUT_MACROP_B              0x0061
#define SYSTEM__INTERMEASUREMENT_PERIOD             0x006C
#define SYSTEM__INTERRUPT_CLEAR                     0x0086
#define SYSTEM__MODE_START                          0x0087
#define PHASECAL_CONFIG__OVERRIDE                   0x004D
#define CAL_CONFIG__VCSEL_START                     0x0047
#define PHASECAL_RESULT__VCSEL_START                0x00D8
#define VHV_CONFIG__INIT                            0x000B
#define VHV_CONFIG__TIMEOUT_MACROP_LOOP_BOUND       0x0008

// Global State
static uint16_t fast_osc_frequency = 0;
static uint16_t osc_calibrate_val = 0;
static uint8_t saved_vhv_init = 0;
static uint8_t saved_vhv_timeout = 0;
static bool calibrated = false;

struct VL53L1X_Results {
    uint8_t range_status;
    uint8_t stream_count;
    uint16_t dss_actual_effective_spads_sd0;
    uint16_t ambient_count_rate_mcps_sd0;
    uint16_t final_crosstalk_corrected_range_mm_sd0;
    uint16_t peak_signal_count_rate_crosstalk_corrected_mcps_sd0;
} results;

// Register Write Helpers
static void vl53l1x_write_reg8(uint16_t reg, uint8_t val) {
    i2c_write(VL53L1X_ADDRESS, reg, I2C_REGADDR_2B, &val, 1);
}

static void vl53l1x_write_reg16(uint16_t reg, uint16_t val) {
    uint8_t buf[2];
    buf[0] = (val >> 8) & 0xFF;
    buf[1] = val & 0xFF;
    i2c_write(VL53L1X_ADDRESS, reg, I2C_REGADDR_2B, buf, 2);
}

static void vl53l1x_write_reg32(uint16_t reg, uint32_t val) {
    uint8_t buf[4];
    buf[0] = (val >> 24) & 0xFF;
    buf[1] = (val >> 16) & 0xFF;
    buf[2] = (val >> 8) & 0xFF;
    buf[3] = val & 0xFF;
    i2c_write(VL53L1X_ADDRESS, reg, I2C_REGADDR_2B, buf, 4);
}

// Register Read Helpers
static uint8_t vl53l1x_read_reg8(uint16_t reg) {
    uint8_t val = 0;
    i2c_read(VL53L1X_ADDRESS, reg, I2C_REGADDR_2B, &val, 1);
    return val;
}

static uint16_t vl53l1x_read_reg16(uint16_t reg) {
    uint8_t buf[2] = {0, 0};
    i2c_read(VL53L1X_ADDRESS, reg, I2C_REGADDR_2B, buf, 2);
    return ((uint16_t)buf[0] << 8) | buf[1];
}


static uint16_t encodeTimeout(uint32_t timeout_mclks) {
    if (timeout_mclks == 0) return 0;
    uint32_t ls_byte = timeout_mclks - 1;
    uint16_t ms_byte = 0;
    while ((ls_byte & 0xFFFFFF00) > 0) {
        ls_byte >>= 1;
        ms_byte++;
    }
    return (ms_byte << 8) | (ls_byte & 0xFF);
}

static uint32_t timeoutMicrosecondsToMclks(uint32_t timeout_us, uint32_t macro_period_us) {
    return (((uint32_t)timeout_us << 12) + (macro_period_us >> 1)) / macro_period_us;
}

static uint32_t calcMacroPeriod(uint8_t vcsel_period) {
    uint32_t pll_period_us = ((uint32_t)0x01 << 30) / fast_osc_frequency;
    uint8_t vcsel_period_pclks = (vcsel_period + 1) << 1;
    uint32_t macro_period_us = (uint32_t)2304 * pll_period_us;
    macro_period_us >>= 6;
    macro_period_us *= vcsel_period_pclks;
    macro_period_us >>= 6;
    return macro_period_us;
}

// Distance and timing budget configuration
static void vl53l1x_set_distance_mode_long_50ms(void) {
    // 1. Long range settings
    vl53l1x_write_reg8(RANGE_CONFIG__VCSEL_PERIOD_A, 0x0F);
    vl53l1x_write_reg8(RANGE_CONFIG__VCSEL_PERIOD_B, 0x0D);
    vl53l1x_write_reg8(RANGE_CONFIG__VALID_PHASE_HIGH, 0xB8);

    vl53l1x_write_reg8(SD_CONFIG__WOI_SD0, 0x0F);
    vl53l1x_write_reg8(SD_CONFIG__WOI_SD1, 0x0D);
    vl53l1x_write_reg8(SD_CONFIG__INITIAL_PHASE_SD0, 14);
    vl53l1x_write_reg8(SD_CONFIG__INITIAL_PHASE_SD1, 14);
    
    // 2. Timing budget settings (50ms = 50000us)
    uint32_t budget_us = 50000;
    uint32_t range_config_timeout_us = budget_us - 1910; // Subtract TimingGuard
    range_config_timeout_us /= 2;
    
    uint32_t macro_period_us = calcMacroPeriod(0x0F); // VCSEL_PERIOD_A
    
    // Phase timeout
    uint32_t phasecal_timeout_mclks = timeoutMicrosecondsToMclks(1000, macro_period_us);
    if (phasecal_timeout_mclks > 0xFF) phasecal_timeout_mclks = 0xFF;
    vl53l1x_write_reg8(PHASECAL_CONFIG__TIMEOUT_MACROP, phasecal_timeout_mclks);
    
    // MM Timing A timeout
    vl53l1x_write_reg16(MM_CONFIG__TIMEOUT_MACROP_A, encodeTimeout(timeoutMicrosecondsToMclks(1, macro_period_us)));
    
    // Range Timing A timeout
    vl53l1x_write_reg16(RANGE_CONFIG__TIMEOUT_MACROP_A, encodeTimeout(timeoutMicrosecondsToMclks(range_config_timeout_us, macro_period_us)));
    
    macro_period_us = calcMacroPeriod(0x0D); // VCSEL_PERIOD_B
    
    // MM Timing B timeout
    vl53l1x_write_reg16(MM_CONFIG__TIMEOUT_MACROP_B, encodeTimeout(timeoutMicrosecondsToMclks(1, macro_period_us)));
    
    // Range Timing B timeout
    vl53l1x_write_reg16(RANGE_CONFIG__TIMEOUT_MACROP_B, encodeTimeout(timeoutMicrosecondsToMclks(range_config_timeout_us, macro_period_us)));
}

// Low Power Auto manual calibration setup
static void vl53l1x_setup_manual_calibration(void) {
    saved_vhv_init = vl53l1x_read_reg8(VHV_CONFIG__INIT);
    saved_vhv_timeout = vl53l1x_read_reg8(VHV_CONFIG__TIMEOUT_MACROP_LOOP_BOUND);
    
    // Disable VHV init
    vl53l1x_write_reg8(VHV_CONFIG__INIT, saved_vhv_init & 0x7F);
    
    // Set loop bound
    vl53l1x_write_reg8(VHV_CONFIG__TIMEOUT_MACROP_LOOP_BOUND, (saved_vhv_timeout & 0x03) + (3 << 2));
    
    // Override phasecal
    vl53l1x_write_reg8(PHASECAL_CONFIG__OVERRIDE, 0x01);
    vl53l1x_write_reg8(CAL_CONFIG__VCSEL_START, vl53l1x_read_reg8(PHASECAL_RESULT__VCSEL_START));
}

// Dynamic SPAD Selection
static void vl53l1x_update_dss(void) {
    uint16_t spadCount = results.dss_actual_effective_spads_sd0;
    if (spadCount != 0) {
        uint32_t totalRatePerSpad = (uint32_t)results.peak_signal_count_rate_crosstalk_corrected_mcps_sd0 +
                                    results.ambient_count_rate_mcps_sd0;
        if (totalRatePerSpad > 0xFFFF) totalRatePerSpad = 0xFFFF;
        totalRatePerSpad <<= 16;
        totalRatePerSpad /= spadCount;
        if (totalRatePerSpad != 0) {
            uint32_t requiredSpads = ((uint32_t)0x0A00 << 16) / totalRatePerSpad;
            if (requiredSpads > 0xFFFF) requiredSpads = 0xFFFF;
            vl53l1x_write_reg16(DSS_CONFIG__MANUAL_EFFECTIVE_SPADS_SELECT, requiredSpads);
            return;
        }
    }
    vl53l1x_write_reg16(DSS_CONFIG__MANUAL_EFFECTIVE_SPADS_SELECT, 0x8000);
}

// API implementation

bool tof_init(void) {
    // 1. Initialize hardware pin for XSHUT
    funPinMode(PIN_XSHUT, GPIO_CFGLR_OUT_10Mhz_PP);
    
    // 2. Perform sensor reset via XSHUT pin
    funDigitalWrite(PIN_XSHUT, FUN_LOW);
    Delay_Ms(10);
    funDigitalWrite(PIN_XSHUT, FUN_HIGH);
    Delay_Ms(10);
    
    // 3. Initialize I2C Master
    i2c_init();
    
    // 4. Verify sensor model ID (should be 0xEACC)
    uint16_t model_id = vl53l1x_read_reg16(0x010F);
    if (model_id != 0xEACC) {
        printf("TOF Init Failed: Model ID mismatch! Expected 0xEACC, got 0x%04X\n", model_id);
        return false;
    }
    
    // 5. Software Reset
    vl53l1x_write_reg8(SOFT_RESET, 0x00);
    Delay_Us(100);
    vl53l1x_write_reg8(SOFT_RESET, 0x01);
    Delay_Ms(2);
    
    // 6. Poll for boot completion
    uint32_t timeout = 5000;
    while ((vl53l1x_read_reg8(0x00E5) & 0x01) == 0) {
        Delay_Us(100);
        if (--timeout == 0) {
            printf("TOF Init Failed: Sensor boot timeout!\n");
            return false;
        }
    }
    
    // 7. Enable 2.8V mode
    vl53l1x_write_reg8(PAD_I2C_HV__EXTSUP_CONFIG, vl53l1x_read_reg8(PAD_I2C_HV__EXTSUP_CONFIG) | 0x01);
    
    // 8. Read oscillator calibration data
    fast_osc_frequency = vl53l1x_read_reg16(OSC_MEASURED__FAST_OSC__FREQUENCY);
    osc_calibrate_val = vl53l1x_read_reg16(RESULT__OSC_CALIBRATE_VAL);
    
    // 9. Static config
    vl53l1x_write_reg16(DSS_CONFIG__TARGET_TOTAL_RATE_MCPS, 0x0A00);
    vl53l1x_write_reg8(GPIO__TIO_HV_STATUS, 0x02);
    vl53l1x_write_reg8(SIGMA_ESTIMATOR__EFFECTIVE_PULSE_WIDTH_NS, 8);
    vl53l1x_write_reg8(SIGMA_ESTIMATOR__EFFECTIVE_AMBIENT_WIDTH_NS, 16);
    vl53l1x_write_reg8(ALGO__CROSSTALK_COMPENSATION_VALID_HEIGHT_MM, 0x01);
    vl53l1x_write_reg8(ALGO__RANGE_IGNORE_VALID_HEIGHT_MM, 0xFF);
    vl53l1x_write_reg8(ALGO__RANGE_MIN_CLIP, 0);
    vl53l1x_write_reg8(ALGO__CONSISTENCY_CHECK__TOLERANCE, 2);
    
    // 10. General config
    vl53l1x_write_reg16(SYSTEM__THRESH_RATE_HIGH, 0x0000);
    vl53l1x_write_reg16(SYSTEM__THRESH_RATE_LOW, 0x0000);
    vl53l1x_write_reg8(DSS_CONFIG__APERTURE_ATTENUATION, 0x38);
    
    // 11. Timing config
    vl53l1x_write_reg16(RANGE_CONFIG__SIGMA_THRESH, 360);
    vl53l1x_write_reg16(RANGE_CONFIG__MIN_COUNT_RATE_RTN_LIMIT_MCPS, 192);
    
    // 12. Dynamic config
    vl53l1x_write_reg8(SYSTEM__GROUPED_PARAMETER_HOLD_0, 0x01);
    vl53l1x_write_reg8(SYSTEM__GROUPED_PARAMETER_HOLD_1, 0x01);
    vl53l1x_write_reg8(SD_CONFIG__QUANTIFIER, 2);
    
    vl53l1x_write_reg8(SYSTEM__GROUPED_PARAMETER_HOLD, 0x00);
    vl53l1x_write_reg8(SYSTEM__SEED_CONFIG, 1);
    
    // 13. Mode and SPAD config
    vl53l1x_write_reg8(SYSTEM__SEQUENCE_CONFIG, 0x8B); // VHV, PHASECAL, DSS1, RANGE
    vl53l1x_write_reg16(DSS_CONFIG__MANUAL_EFFECTIVE_SPADS_SELECT, 200 << 8);
    vl53l1x_write_reg8(DSS_CONFIG__ROI_MODE_CONTROL, 2);
    
    // 14. Configure distance mode and timing budget (Long, 50ms)
    vl53l1x_set_distance_mode_long_50ms();
    
    // 15. Outer offset range config
    vl53l1x_write_reg16(ALGO__PART_TO_PART_RANGE_OFFSET_MM, vl53l1x_read_reg16(MM_CONFIG__OUTER_OFFSET_MM) * 4);
    
    calibrated = false;
    return true;
}

void tof_start_ranging(void) {
    calibrated = false;
    // Set measurement period (using 50ms * calibration factor)
    vl53l1x_write_reg32(SYSTEM__INTERMEASUREMENT_PERIOD, 50 * osc_calibrate_val);
    
    // Clear any pending interrupt
    vl53l1x_write_reg8(SYSTEM__INTERRUPT_CLEAR, 0x01);
    
    // Start continuous ranging in timed mode
    vl53l1x_write_reg8(SYSTEM__MODE_START, 0x40);
}

void tof_stop_ranging(void) {
    // Abort ongoing ranging
    vl53l1x_write_reg8(SYSTEM__MODE_START, 0x80);
    calibrated = false;
    
    // Restore VHV configs and remove phasecal override
    if (saved_vhv_init != 0) {
        vl53l1x_write_reg8(VHV_CONFIG__INIT, saved_vhv_init);
    }
    if (saved_vhv_timeout != 0) {
        vl53l1x_write_reg8(VHV_CONFIG__TIMEOUT_MACROP_LOOP_BOUND, saved_vhv_timeout);
    }
    vl53l1x_write_reg8(PHASECAL_CONFIG__OVERRIDE, 0x00);
}

bool tof_data_ready(void) {
    // Active low output on GPIO status indicates data is ready
    return (vl53l1x_read_reg8(GPIO__TIO_HV_STATUS) & 0x01) == 0;
}

uint16_t tof_read_distance(bool blocking) {
    if (blocking) {
        uint32_t timeout = 100000;
        while (!tof_data_ready()) {
            Delay_Us(10);
            if (--timeout == 0) {
                printf("TOF: Read timeout waiting for data ready!\n");
                return 0;
            }
        }
    } else {
        if (!tof_data_ready()) {
            return 0;
        }
    }
    
    // Read the 17 bytes of results from RESULT__RANGE_STATUS
    uint8_t buf[17];
    i2c_read(VL53L1X_ADDRESS, 0x0089, I2C_REGADDR_2B, buf, 17);
    
    results.range_status = buf[0];
    results.stream_count = buf[2];
    results.dss_actual_effective_spads_sd0 = ((uint16_t)buf[3] << 8) | buf[4];
    results.ambient_count_rate_mcps_sd0 = ((uint16_t)buf[7] << 8) | buf[8];
    results.final_crosstalk_corrected_range_mm_sd0 = ((uint16_t)buf[13] << 8) | buf[14];
    results.peak_signal_count_rate_crosstalk_corrected_mcps_sd0 = ((uint16_t)buf[15] << 8) | buf[16];
    
    // Perform manual calibration on the first range
    if (!calibrated) {
        vl53l1x_setup_manual_calibration();
        calibrated = true;
    }
    
    // Update DSS
    vl53l1x_update_dss();
    
    // Get corrected distance
    uint16_t raw_range = results.final_crosstalk_corrected_range_mm_sd0;
    uint16_t range_mm = ((uint32_t)raw_range * 2011 + 0x0400) / 0x0800;
    
    // Clear interrupt to trigger next measurement
    vl53l1x_write_reg8(SYSTEM__INTERRUPT_CLEAR, 0x01);
    
    return range_mm;
}