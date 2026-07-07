#include "ch32fun.h"
#include <stdio.h>
#include "tof.h"

// Pin Definitions
#define PIN_SOLENOID PC7
#define PIN_LED      PC4
#define PIN_PAY_L    PC0
#define PIN_BT1      PC5
#define PIN_BT2      PC6

// Constants
#define TOY_DETECTION_THRESHOLD_MM 30   // Distance drop in mm to detect toy
#define SOLENOID_TIMEOUT_MS        5000 // 5 seconds safety timeout

void init_gpio(void) {
    // 1. Configure Solenoid pin as push-pull output
    funPinMode(PIN_SOLENOID, GPIO_CFGLR_OUT_10Mhz_PP);
    funDigitalWrite(PIN_SOLENOID, FUN_LOW); // Start disengaged

    // 2. Configure LED pin as push-pull output (Active Low)
    funPinMode(PIN_LED, GPIO_CFGLR_OUT_10Mhz_PP);
    funDigitalWrite(PIN_LED, FUN_HIGH); // Start OFF

    // 3. Configure PAY_L pin as floating input (external pull-up is present)
    funPinMode(PIN_PAY_L, GPIO_CFGLR_IN_FLOAT);

    // 4. Configure buttons as inputs with internal pull-ups
    funPinMode(PIN_BT1, GPIO_CFGLR_IN_PUPD);
    funDigitalWrite(PIN_BT1, FUN_HIGH); // Enable pull-up

    funPinMode(PIN_BT2, GPIO_CFGLR_IN_PUPD);
    funDigitalWrite(PIN_BT2, FUN_HIGH); // Enable pull-up
}

// Simple LED helper
void set_led(bool on) {
    // Active low LED
    funDigitalWrite(PIN_LED, on ? FUN_LOW : FUN_HIGH);
}

// Simple Solenoid helper
void set_solenoid(bool engaged) {
    funDigitalWrite(PIN_SOLENOID, engaged ? FUN_HIGH : FUN_LOW);
}

// Perform gacha release cycle
void execute_release_cycle(void) {
    printf("--- Gacha release cycle started ---\n");
    
    // 1. Turn status LED ON
    set_led(true);
    
    // 2. Engage solenoid
    set_solenoid(true);
    printf("Solenoid engaged.\n");
    
    // 3. Start TOF ranging
    tof_start_ranging();
    
    // 4. Calibrate baseline distance (average of first 3 valid readings)
    printf("Calibrating baseline...\n");
    uint32_t baseline_sum = 0;
    uint8_t baseline_count = 0;
    uint32_t start_time = SysTick->CNT;
    
    // Wait up to 500ms to get baseline readings
    while (baseline_count < 3) {
        IWDG->CTLR = 0xAAAA; // Pet watchdog
        Delay_Ms(50);
        uint16_t dist = tof_read_distance(false);
        if (dist > 0 && dist < 2000) { // Valid range check
            baseline_sum += dist;
            baseline_count++;
            printf("Baseline sample %d: %d mm\n", baseline_count, dist);
        }
        
        // Timeout if sensor is not giving readings
        uint32_t elapsed = (SysTick->CNT - start_time) / (FUNCONF_SYSTEM_CORE_CLOCK / 1000);
        if (elapsed > 1000) {
            printf("Baseline calibration timeout! Using fallback baseline.\n");
            break;
        }
    }
    
    uint16_t baseline = (baseline_count > 0) ? (baseline_sum / baseline_count) : 150; // Fallback to 150mm
    printf("Baseline established: %d mm\n", baseline);
    printf("Waiting for toy drop (detection threshold: < %d mm)...\n", baseline - TOY_DETECTION_THRESHOLD_MM);
    
    // 5. Ranging loop waiting for toy detection or safety timeout
    start_time = SysTick->CNT;
    bool toy_detected = false;
    uint32_t elapsed_ms = 0;
    
    while (elapsed_ms < SOLENOID_TIMEOUT_MS) {
        IWDG->CTLR = 0xAAAA; // Pet watchdog
        Delay_Ms(10); // Check frequently
        
        if (tof_data_ready()) {
            uint16_t dist = tof_read_distance(false);
            if (dist > 0) {
                printf("Distance: %d mm\n", dist);
                // Detect drop in distance
                if (dist < (baseline - TOY_DETECTION_THRESHOLD_MM)) {
                    printf("Toy detected! Distance dropped to %d mm\n", dist);
                    toy_detected = true;
                    break;
                }
            }
        }
        
        elapsed_ms = (SysTick->CNT - start_time) / (FUNCONF_SYSTEM_CORE_CLOCK / 1000);
    }
    
    if (toy_detected) {
        printf("Toy dropped successfully!\n");
    } else {
        printf("Solenoid safety timeout reached without detecting toy!\n");
    }
    
    // 6. Disengage solenoid
    set_solenoid(false);
    printf("Solenoid disengaged.\n");
    
    // 7. Stop TOF ranging
    tof_stop_ranging();
    
    // 8. Turn LED OFF
    set_led(false);
    
    printf("--- Gacha release cycle finished ---\n\n");
}

int main(void) {
    // Enable system configuration and debug print
    SystemInit();
    
    // Initialize GPIO pins
    init_gpio();
    
    printf("Solenoid Controller Board Initializing...\n");
    
    // Initialize IWDG (Independent Watchdog)
    // 128kHz LSI clock, Prescaler 256 -> 500Hz clock.
    // Reload 4095 -> 8.19 seconds timeout.
    IWDG->CTLR = 0x5555; // Enable write access
    IWDG->PSCR = 0x06;   // Prescaler 256
    IWDG->RLDR = 0xFFF;  // Max reload
    IWDG->CTLR = 0xCCCC; // Start watchdog
    IWDG->CTLR = 0xAAAA; // Pet watchdog
    
    // Initialize TOF sensor
    bool tof_ok = tof_init();
    if (!tof_ok) {
        printf("CRITICAL ERROR: TOF Sensor Initialization Failed!\n");
        // Rapid blink LED to indicate failure
        while (1) {
            IWDG->CTLR = 0xAAAA; // Pet watchdog
            set_led(true);
            Delay_Ms(100);
            set_led(false);
            Delay_Ms(100);
        }
    }
    
    printf("TOF Sensor Initialized Successfully!\n");
    
    // Indicate success with LED on for 1s
    set_led(true);
    Delay_Ms(1000);
    set_led(false);
    
    printf("System Ready. Waiting for trigger...\n");
    
    while (1) {
        IWDG->CTLR = 0xAAAA; // Pet the watchdog
        
        // Check if PAY_L is triggered (active low)
        bool pay_triggered = (funDigitalRead(PIN_PAY_L) == FUN_LOW);
        
        // Check if BT1/SW1 is pressed (active low)
        bool bt1_pressed = (funDigitalRead(PIN_BT1) == FUN_LOW);
        
        if (pay_triggered || bt1_pressed) {
            // Run the release cycle
            execute_release_cycle();
            
            // Wait for trigger to be released to prevent immediate re-triggering
            while (funDigitalRead(PIN_PAY_L) == FUN_LOW || funDigitalRead(PIN_BT1) == FUN_LOW) {
                IWDG->CTLR = 0xAAAA; // Pet the watchdog
                Delay_Ms(50);
            }
            printf("Trigger released. Re-armed.\n");
        }
        
        Delay_Ms(10);
    }
    
    return 0;
}