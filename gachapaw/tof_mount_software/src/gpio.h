#ifndef _GPIO_H
#define _GPIO_H

#include <stdint.h>

void gpio_init(void);

void relay_on(void);
void relay_off(void);
void relay_set(uint8_t on);

void led_on(void);
void led_off(void);
void led_set(uint8_t on);
void led_toggle(void);

uint8_t button1_read(void);
uint8_t button2_read(void);

uint16_t photodiode_read(void);

uint8_t pay_read(void);

#endif
