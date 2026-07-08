#include "ch32fun.h"
#include "tof.h"
#include "gpio.h"
#include <stdio.h>

int main(void) {
    SystemInit();
    gpio_init();

    Delay_Ms(50);
    printf("tof_mount starting\n");

    if (tof_init()) {
        printf("tof init failed\n");
        while (1);
    }

    uint16_t id;
    tof_get_sensor_id(&id);
    printf("sensor id: 0x%04X\n", id);

    tof_start_ranging();

    while (1) {
        uint8_t ready = 0;
        while (!ready) {
            tof_check_data_ready(&ready);
            Delay_Ms(1);
        }

        uint16_t dist;
        uint8_t status;
        tof_get_distance(&dist);
        tof_get_range_status(&status);
        tof_clear_interrupt();

        printf("dist=%u mm  status=%u\r\n", dist, status);
    }
}
