#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <cstring>
#include <cerrno>
#include <string>
#include <sstream>
#include <vector>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <poll.h>
#include <sys/eventfd.h>
#include <linux/gpio.h>
#include <linux/spi/spidev.h>
#include <mutex>
#include "external/liquidcrystal-i2c/LiquidCrystal_I2C.h"

#define LOG_TAG "GPIO_NATIVE"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

static const bool IS_VERBOSE = false;

static int g_cancel_fd = -1;
static int g_chip_fd = -1;
static int g_spi_fd = -1;
static LiquidCrystal_I2C* g_lcd = nullptr;
static std::mutex g_init_mutex;

extern "C"
JNIEXPORT void JNICALL
Java_org_pawprint_gachapaw_service_GpioManager_nativeInit(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_init_mutex);
    LOGD("enter nativeInit");

    if (g_chip_fd < 0) {
        g_chip_fd = open("/dev/gpiochip0", O_RDWR);
        if (g_chip_fd < 0) {
            LOGE("nativeInit: Failed to open /dev/gpiochip0: %s", strerror(errno));
        }
    }

    if (g_spi_fd < 0) {
        g_spi_fd = open("/dev/spidev0.0", O_RDWR);
        if (g_spi_fd >= 0) {
            uint32_t speed = 3200000;
            uint8_t mode = SPI_MODE_0;
            ioctl(g_spi_fd, SPI_IOC_WR_MODE, &mode);
            ioctl(g_spi_fd, SPI_IOC_WR_MAX_SPEED_HZ, &speed);
            LOGI("nativeInit: SPI initialized at 3.2MHz");
        } else {
            LOGE("nativeInit: Failed to open SPI: %s", strerror(errno));
        }
    }

    if (g_lcd == nullptr) {
        g_lcd = new LiquidCrystal_I2C("/dev/i2c-1", 0x27, 2, 1, 0, 4, 5, 6, 7, 3, POSITIVE);
        g_lcd->begin(20, 4);
        g_lcd->clear();
        LOGI("nativeInit: LCD initialized 20x4");
    }
}

int toggle_gpio_pin(int chip_fd, int line_num, int value) {
    if (chip_fd < 0) return -1;
    // Use the modern v2 request structure
    struct gpio_v2_line_request req{};
    memset(&req, 0, sizeof(req));

    req.num_lines = 1;
    req.offsets[0] = line_num;
    req.config.flags = GPIO_V2_LINE_FLAG_OUTPUT;
    strncpy(req.consumer, "GachaPawV2", sizeof(req.consumer) - 1);

    req.config.num_attrs = 1;
    req.config.attrs[0].attr.id = GPIO_V2_LINE_ATTR_ID_OUTPUT_VALUES;
    req.config.attrs[0].mask = 1ULL << 0;
    req.config.attrs[0].attr.values = (value ? 1ULL : 0ULL);

    if (ioctl(chip_fd, GPIO_V2_GET_LINE_IOCTL, &req) < 0) {
        int err = errno;
        LOGE("V2 ioctl failed! Error: %d (%s)", err, strerror(err));
        return err;
    }
    close(req.fd);
    return 0;
}

// Helper to encode one byte of data into 4 bytes of data in encoded_data
void encode_neopixel_byte(uint8_t data, uint8_t* encoded_data) {
    for (int i = 0; i < 4; i++) {
        uint8_t spi_byte = 0;
        for (int bit = 0; bit < 2; bit++) {
            int shift = 7 - (i * 2 + bit);
            bool is_high = (data >> shift) & 0x01;
            uint8_t pattern = is_high ? 0x0E /*1*/ : 0x08 /*0*/;
            spi_byte |= (pattern << (4 * (1 - bit)));
        }
        encoded_data[i] = spi_byte;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_org_pawprint_gachapaw_service_GpioManager_setGpioState(JNIEnv *env, jobject thiz, jint pin,
                                                    jboolean state) {
    if (g_chip_fd < 0) {
        LOGE("setGpioState: GPIO chip not initialized");
        return;
    }
    toggle_gpio_pin(g_chip_fd, pin, state ? 1 : 0);
}

extern "C"
JNIEXPORT void JNICALL
Java_org_pawprint_gachapaw_service_GpioManager_setNeopixelColor(JNIEnv *env, jobject thiz,
                                                        jint argbColor) {
    if (g_spi_fd < 0) return;
    if (IS_VERBOSE) LOGD("enter setNeopixelColor");
    return;

    uint8_t a = (argbColor >> 24) & 0xFF;
    uint8_t r = (argbColor >> 16) & 0xFF;
    uint8_t g = (argbColor >> 8) & 0xFF;
    uint8_t b = (argbColor & 0xFF);

    float brightness = static_cast<float>(a) / 255.0f;

    auto finalG = static_cast<uint8_t>(static_cast<float>(g) * brightness);
    auto finalR = static_cast<uint8_t>(static_cast<float>(r) * brightness);
    auto finalB = static_cast<uint8_t>(static_cast<float>(b) * brightness);

    // NeoPixel expects GRB
    uint8_t colorData[3] = { finalG, finalR, finalB };
    uint8_t spi_data[12];
    for(int i = 0; i < 3; i++) {
        encode_neopixel_byte(colorData[i], &spi_data[i * 4]);
    }

    if (write(g_spi_fd, spi_data, sizeof(spi_data)) < 0) {
        LOGE("SPI Write Failed: %s", strerror(errno));
    }
}

extern "C"
JNIEXPORT int JNICALL
Java_org_pawprint_gachapaw_service_GpioManager_waitForGpio(JNIEnv *env, jobject thiz, jint pin,
                                                           jboolean expected_state) {
    if (g_chip_fd < 0) {
        LOGE("waitForGpio: GPIO chip not initialized");
        return -1;
    }

    g_cancel_fd = eventfd(0, EFD_CLOEXEC);
    if (g_cancel_fd < 0) {
        LOGE("waitForGpio: Failed to create eventfd");
        return -1;
    }

    struct gpio_v2_line_request req{};
    memset(&req, 0, sizeof(req));
    req.num_lines = 1;
    req.offsets[0] = pin;
    req.config.flags = GPIO_V2_LINE_FLAG_INPUT |
                       (expected_state ? GPIO_V2_LINE_FLAG_EDGE_RISING : GPIO_V2_LINE_FLAG_EDGE_FALLING);
    strncpy(req.consumer, "GachaPawWait", sizeof(req.consumer) - 1);

    req.config.num_attrs = 1;
    req.config.attrs[0].attr.id = GPIO_V2_LINE_ATTR_ID_DEBOUNCE;
    req.config.attrs[0].mask = 1ULL << 0;
    req.config.attrs[0].attr.debounce_period_us = 50 * 1000;

    if (ioctl(g_chip_fd, GPIO_V2_GET_LINE_IOCTL, &req) < 0) {
        int err = errno;
        LOGE("waitForGpio: V2 ioctl failed: %d (%s)", err, strerror(err));
        close(g_cancel_fd);
        g_cancel_fd = -1;
        return -err;
    }

    struct pollfd fds[2];
    fds[0].fd = req.fd;
    fds[0].events = POLLIN;
    fds[1].fd = g_cancel_fd;
    fds[1].events = POLLIN;

    int poll_res = poll(fds, 2, -1);
    int result = 0;

    if (poll_res < 0) {
        result = -errno;
        LOGE("waitForGpio: poll failed: %s", strerror(errno));
    } else if (fds[1].revents & POLLIN) {
        result = -2; // Cancelled
        LOGI("waitForGpio: cancelled");
    } else if (fds[0].revents & POLLIN) {
        struct gpio_v2_line_event event{};
        read(req.fd, &event, sizeof(event));
        LOGI("pin %d moved to state %d", pin, expected_state);
        result = 0;
    }

    close(req.fd);
    close(g_cancel_fd);
    g_cancel_fd = -1;

    return result;
}

extern "C"
JNIEXPORT void JNICALL
Java_org_pawprint_gachapaw_service_GpioManager_cancelWaitForGpio(JNIEnv *env, jobject thiz) {
    if (g_cancel_fd >= 0) {
        uint64_t val = 1;
        write(g_cancel_fd, &val, sizeof(val));
    }
}

void printWrappedText(LiquidCrystal_I2C &lcd, const std::string &text) {
    std::stringstream ss(text);
    std::string segment;
    int currentRow = 0;

    while (std::getline(ss, segment, '\n') && currentRow < 4) {
        std::stringstream lineStream(segment);
        std::string word;
        std::string currentLine;

        while (lineStream >> word) {
            int spaceNeeded = currentLine.empty() ? 0 : 1;

            if (currentLine.length() + spaceNeeded + word.length() > 20) {
                if (currentRow < 4) {
                    lcd.setCursor(0, currentRow);
                    lcd.print(currentLine.c_str());
                    currentRow++;
                    currentLine = word;
                } else {
                    break;
                }
            } else {
                if (!currentLine.empty()) {
                    currentLine += " ";
                }
                currentLine += word;
            }
        }

        if (!currentLine.empty() && currentRow < 4) {
            lcd.setCursor(0, currentRow);
            lcd.print(currentLine.c_str());
            currentRow++;
        } else if (currentLine.empty() && currentRow < 4) {
            currentRow++;
        }
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_org_pawprint_gachapaw_service_GpioManager_updateLcdText(JNIEnv *env, jobject thiz,
                                                             jstring text) {
    if (g_lcd == nullptr) {
        LOGE("updateLcdText: LCD not initialized");
        return;
    }
    const char *nativeString = env->GetStringUTFChars(text, nullptr);
    LOGD("enter updateLcdText: %s", nativeString);

    g_lcd->clear();
    printWrappedText(*g_lcd, std::string(nativeString));

    env->ReleaseStringUTFChars(text, nativeString);
    LOGI("LCD update complete");
}
