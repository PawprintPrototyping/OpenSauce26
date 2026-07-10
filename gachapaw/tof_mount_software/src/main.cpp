#include <Arduino.h>
#include <Wire.h>
#include <VL53L1X.h>

// --- Debug Macro Configuration ---
#ifdef DEBUG_ENABLE
  #define DEBUG_BEGIN(baud)    Serial.begin(baud)
  #define DEBUG_PRINT(x)       Serial.print(x)
  #define DEBUG_PRINTLN(x)     Serial.println(x)
#else
  #define DEBUG_BEGIN(baud)
  #define DEBUG_PRINT(x)
  #define DEBUG_PRINTLN(x)
#endif

// Hardware Pin Configuration
constexpr uint8_t PIN_PAY      = PC0; // Input from RPi -  successful payment when toggled high
constexpr uint8_t PIN_SDA      = PC1; 
constexpr uint8_t PIN_SCL      = PC2; 
constexpr uint8_t PIN_XSHUT    = PC3; 
constexpr uint8_t PIN_LED_L    = PC4; // Active Low LED
constexpr uint8_t PIN_TRIGGER  = PC5; // BT1 - Simulates PIN_PAY trigger
constexpr uint8_t PIN_MANUAL   = PC6; // BT2 - Manual Override Button for solenoid
constexpr uint8_t PIN_SOLENOID = PC7; // solenoid output

// System Constants
constexpr uint16_t I2C_SPEED_KHZ          = 400000;
constexpr uint16_t DETECTION_THRESHOLD_MM = 30; 
constexpr uint32_t DEBOUNCE_DELAY_MS      = 30;
constexpr uint8_t  CALIBRATION_SAMPLES    = 10;
constexpr uint16_t TOF_TIMING_BUDGET_US   = 20000; 

VL53L1X sensor;

enum class SystemState {
    INIT,
    CALIBRATING,
    WAIT_FOR_TRIGGER,
    WAIT_FOR_BALL,
    ERROR
};

SystemState currentState = SystemState::INIT;
uint16_t baselineDistanceMm = 0;

// Timing and State Tracking
uint32_t lastTriggerTime = 0;
uint32_t lastManualDebounceTime = 0;
bool lastManualBtnState = HIGH;
bool manualBtnHandled = false;

void setup() {
    DEBUG_BEGIN(115200);
    DEBUG_PRINTLN("SYS: Booting...");

    pinMode(PIN_PAY, INPUT);
    pinMode(PIN_TRIGGER, INPUT_PULLUP);
    pinMode(PIN_MANUAL, INPUT_PULLUP);
    
    pinMode(PIN_SOLENOID, OUTPUT);
    digitalWrite(PIN_SOLENOID, LOW); 
    
    pinMode(PIN_LED_L, OUTPUT);
    digitalWrite(PIN_LED_L, HIGH); // LED OFF

    // 1. Hardware Reset the VL53L1X
    DEBUG_PRINTLN("SYS: Resetting ToF...");
    pinMode(PIN_XSHUT, OUTPUT);
    digitalWrite(PIN_XSHUT, LOW);  
    delay(10);
    digitalWrite(PIN_XSHUT, HIGH); 
    delay(10);                     

    // 2. Initialize I2C 
    Wire.begin();
    Wire.setClock(I2C_SPEED_KHZ);

    // 3. Initialize Sensor
    sensor.setTimeout(500);
    if (!sensor.init()) {
        DEBUG_PRINTLN("ERR: ToF Init Failed!");
        currentState = SystemState::ERROR;
        return;
    }

    sensor.setDistanceMode(VL53L1X::Short);
    sensor.setMeasurementTimingBudget(TOF_TIMING_BUDGET_US);
    sensor.startContinuous(TOF_TIMING_BUDGET_US / 1000); 

    DEBUG_PRINTLN("SYS: Calibration started.");
    currentState = SystemState::CALIBRATING;
}

void loop() {
    // --- Global Manual Override Evaluation ---
    bool currentBtnState = digitalRead(PIN_MANUAL);
    if (currentBtnState != lastManualBtnState) {
        lastManualDebounceTime = millis();
    }

    if ((millis() - lastManualDebounceTime) > DEBOUNCE_DELAY_MS) {
        if (currentBtnState == LOW && !manualBtnHandled) {
            bool isSolenoidOn = digitalRead(PIN_SOLENOID);
            digitalWrite(PIN_SOLENOID, !isSolenoidOn);
            
            DEBUG_PRINT("EVT: Solenoid Manual ");
            DEBUG_PRINTLN(!isSolenoidOn ? "ON" : "OFF");
            
            manualBtnHandled = true;
        } else if (currentBtnState == HIGH) {
            manualBtnHandled = false;
        }
    }
    lastManualBtnState = currentBtnState;

    // --- Core State Machine ---
    switch (currentState) {
        case SystemState::INIT:
            break;

        case SystemState::CALIBRATING: {
            uint32_t sum = 0;
            uint8_t validSamples = 0;

            for (uint8_t i = 0; i < CALIBRATION_SAMPLES; i++) {
                sensor.read();
                if (!sensor.timeoutOccurred()) {
                    sum += sensor.ranging_data.range_mm;
                    validSamples++;
                }
            }

            if (validSamples > (CALIBRATION_SAMPLES / 2)) {
                baselineDistanceMm = sum / validSamples;
                
                DEBUG_PRINT("SYS: Calibration OK. Baseline: ");
                DEBUG_PRINT(baselineDistanceMm);
                DEBUG_PRINTLN(" mm");
                
                currentState = SystemState::WAIT_FOR_TRIGGER;
            } else {
                DEBUG_PRINTLN("ERR: Calibration failed.");
                currentState = SystemState::ERROR;
            }
            break;
        }

        case SystemState::WAIT_FOR_TRIGGER: {
            if (digitalRead(PIN_TRIGGER) == LOW || digitalRead(PIN_PAY) == HIGH) {
                if (lastTriggerTime == 0) { 
                    lastTriggerTime = millis();
                } else if ((millis() - lastTriggerTime) > DEBOUNCE_DELAY_MS) {
                    digitalWrite(PIN_LED_L, LOW); // LED ON
                    digitalWrite(PIN_SOLENOID, HIGH); 
                    lastTriggerTime = 0;
                    
                    DEBUG_PRINTLN("EVT: Trigger Armed.");
                    currentState = SystemState::WAIT_FOR_BALL;
                }
            } else {
                lastTriggerTime = 0;
            }
            break;
        }

        case SystemState::WAIT_FOR_BALL: {
            sensor.read();
            if (!sensor.timeoutOccurred()) {
                uint16_t currentDistance = sensor.ranging_data.range_mm;
                
                if (currentDistance < (baselineDistanceMm - DETECTION_THRESHOLD_MM)) {
                    digitalWrite(PIN_LED_L, HIGH); // LED OFF
                    digitalWrite(PIN_SOLENOID, LOW); 
                    
                    DEBUG_PRINT("EVT: Ball Detected! Dist: ");
                    DEBUG_PRINT(currentDistance);
                    DEBUG_PRINTLN(" mm");
                    
                    currentState = SystemState::WAIT_FOR_TRIGGER;
                }
            }
            break;
        }

        case SystemState::ERROR:
            digitalWrite(PIN_LED_L, !digitalRead(PIN_LED_L));
            delay(250); 
            break;
    }
}