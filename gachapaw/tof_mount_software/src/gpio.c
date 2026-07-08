#include "ch32fun.h"
#include "gpio.h"

// PD3 = ADC channel 4
#define PHOTODIODE_ADC_CH 4

void gpio_init(void) {
    funGpioInitAll();

    // PC7: relay output (push-pull)
    funPinMode(PC7, GPIO_CFGLR_OUT_10Mhz_PP);
    GPIOC->BCR = (1 << 7);

    // PC4: LED output (push-pull)
    funPinMode(PC4, GPIO_CFGLR_OUT_10Mhz_PP);
    GPIOC->BCR = (1 << 4);

    // PC5: button 1, input with pull-up
    funPinMode(PC5, GPIO_CFGLR_IN_PUPD);
    GPIOC->BSHR = (1 << 5);

    // PC6: button 2, input with pull-up
    funPinMode(PC6, GPIO_CFGLR_IN_PUPD);
    GPIOC->BSHR = (1 << 6);

    // PC0: pay pin, floating input (external pull-up via BSS138 level translator)
    funPinMode(PC0, GPIO_CFGLR_IN_FLOAT);

    // PD3: photodiode, analog input
    funPinMode(PD3, GPIO_CFGLR_IN_ANALOG);

    // ADC setup
    RCC->APB2PCENR |= RCC_APB2Periph_ADC1;
    RCC->CFGR0 = (RCC->CFGR0 & ~RCC_ADCPRE) | RCC_ADCPRE_DIV6;

    ADC1->CTLR2 |= ADC_ADON;
    Delay_Ms(1);

    // Calibrate
    ADC1->CTLR2 |= ADC_RSTCAL;
    while (ADC1->CTLR2 & ADC_RSTCAL);
    ADC1->CTLR2 |= ADC_CAL;
    while (ADC1->CTLR2 & ADC_CAL);

    // 241 cycle sample time for channel 4
    ADC1->SAMPTR2 = (ADC1->SAMPTR2 & ~(7 << (3 * PHOTODIODE_ADC_CH)))
                    | (7 << (3 * PHOTODIODE_ADC_CH));
}

void relay_on(void)          { GPIOC->BSHR = (1 << 7); }
void relay_off(void)         { GPIOC->BCR  = (1 << 7); }
void relay_set(uint8_t on)   { if (on) relay_on(); else relay_off(); }

void led_on(void)            { GPIOC->BSHR = (1 << 4); }
void led_off(void)           { GPIOC->BCR  = (1 << 4); }
void led_set(uint8_t on)     { if (on) led_on(); else led_off(); }
void led_toggle(void)        { GPIOC->OUTDR ^= (1 << 4); }

uint8_t button1_read(void)   { return !(GPIOC->INDR & (1 << 5)); }
uint8_t button2_read(void)   { return !(GPIOC->INDR & (1 << 6)); }

uint8_t pay_read(void)       { return !(GPIOC->INDR & (1 << 0)); }

uint16_t photodiode_read(void) {
    ADC1->RSQR3 = PHOTODIODE_ADC_CH;
    ADC1->CTLR2 |= ADC_SWSTART | ADC_EXTTRIG | ADC_EXTSEL;
    while (!(ADC1->STATR & ADC_EOC));
    ADC1->STATR &= ~ADC_EOC;
    return ADC1->RDATAR;
}
