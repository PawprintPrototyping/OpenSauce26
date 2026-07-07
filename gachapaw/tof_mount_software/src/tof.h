#ifndef TOF_H
#define TOF_H

#include <stdint.h>
#include <stdbool.h>

// Initialize the TOF sensor and I2C bus.
// Returns true on success, false on failure (e.g. sensor not found).
bool tof_init(void);

// Start ranging measurements.
void tof_start_ranging(void);

// Stop ranging measurements.
void tof_stop_ranging(void);

// Check if a new measurement is ready.
bool tof_data_ready(void);

// Read the distance in mm.
// If blocking is true, it waits for data to be ready.
// If blocking is false, it returns the last read distance (or 0 if not ready).
uint16_t tof_read_distance(bool blocking);

#endif // TOF_H
