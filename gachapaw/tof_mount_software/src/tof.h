#ifndef _TOF_H
#define _TOF_H

#include <stdint.h>

#define TOF_I2C_ADDR 0x29

#define TOF_DISTANCE_MODE_SHORT 1
#define TOF_DISTANCE_MODE_LONG  2

#define TOF_RANGE_STATUS_OK              0
#define TOF_RANGE_STATUS_SIGMA_FAIL      1
#define TOF_RANGE_STATUS_SIGNAL_FAIL     2
#define TOF_RANGE_STATUS_OUT_OF_BOUNDS   3
#define TOF_RANGE_STATUS_WRAP            7

int8_t tof_init(void);
int8_t tof_start_ranging(void);
int8_t tof_stop_ranging(void);
int8_t tof_clear_interrupt(void);
int8_t tof_check_data_ready(uint8_t *ready);
int8_t tof_get_distance(uint16_t *distance_mm);
int8_t tof_get_range_status(uint8_t *status);
int8_t tof_set_distance_mode(uint16_t mode);
int8_t tof_set_timing_budget_ms(uint16_t budget_ms);
int8_t tof_set_inter_measurement_ms(uint16_t period_ms);
int8_t tof_get_sensor_id(uint16_t *id);

#endif
