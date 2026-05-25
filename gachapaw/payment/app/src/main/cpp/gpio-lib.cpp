#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <cstring>
#include <cerrno>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <linux/gpio.h>
#include <linux/spi/spidev.h>

#define LOG_TAG "GPIO_NATIVE"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

int toggle_gpio_pin(int chip_fd, int line_num, int value) {
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
    return 0;
}
// Helper to encode one byte of data into 4 bytes of data in encoded_data
void encode_neopixel_byte(uint8_t data, uint8_t* encoded_data) {
    // For each byte, we need to expand the data into 4 bytes to encode the neopixel timings
    // 0 = 1000 |-___| 0x08
    // 1 = 1110 |---_| 0x0E
    // 8 bits -> 4 bytes
    for (int i = 0; i < 4; i++) {
        uint8_t spi_byte = 0;
        // two bits -> one byte
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
Java_com_pawprint_gachapaw_service_GpioManager_setGpioState(JNIEnv *env, jobject thiz, jint pin,
                                                    jboolean state) {
    LOGD("enter setGpioState");
    int chip_fd = open("/dev/gpiochip0", O_RDWR);
    if (chip_fd < 0) {
        int err = errno;
        LOGE("Failed to open /dev/gpiochip0: %d (%s)", err, strerror(err));
        return;
    }
    int res = toggle_gpio_pin(chip_fd, pin, state ? 1 : 0);
    close(chip_fd);
    LOGI("Successfully called GPIO pin %d with state %d, res: %d", pin, state, res);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_pawprint_gachapaw_service_GpioManager_setNeopixelColor(JNIEnv *env, jobject thiz,
                                                        jint argbColor) {
    LOGD("enter setNeopixelColor");
    uint8_t a = (argbColor >> 24) & 0xFF;
    uint8_t r = (argbColor >> 16) & 0xFF;
    uint8_t g = (argbColor >> 8) & 0xFF;
    uint8_t b = (argbColor & 0xFF);

    float brightness = static_cast<float>(a) / 255.0f;

    auto finalG = static_cast<uint8_t>(static_cast<float>(g) * brightness);
    auto finalR = static_cast<uint8_t>(static_cast<float>(r) * brightness);
    auto finalB = static_cast<uint8_t>(static_cast<float>(b) * brightness);
    LOGD("RGB Sequence: [R %d, G %d, B %d]", finalR, finalG, finalB);

    // NeoPixel expects GRB
    uint8_t colorData[3] = { finalG, finalR, finalB };
    uint8_t spi_data[12];
    for(int i = 0; i < 3; i++) {
        encode_neopixel_byte(colorData[i], &spi_data[i * 4]);
    }
    // 3. Open SPI Device
    int spi_fd = open("/dev/spidev0.0", O_RDWR);
    if (spi_fd < 0) {
        LOGE("Failed to open SPI: %s", strerror(errno));
        return;
    }

    // Configure SPI (3.2 MHz is optimal for 4-bit encoding), 4 * 800kHz
    uint32_t speed = 3200000;
    uint8_t mode = SPI_MODE_0;
    ioctl(spi_fd, SPI_IOC_WR_MODE, &mode);
    ioctl(spi_fd, SPI_IOC_WR_MAX_SPEED_HZ, &speed);

    if (write(spi_fd, spi_data, sizeof(spi_data)) < 0) {
        LOGE("SPI Write Failed: %s", strerror(errno));
    }
    close(spi_fd);
}
