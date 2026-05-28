cmake_minimum_required(VERSION 3.22.1)

# Name your library (e.g., "native-lib")
project("gpio-lib")

# Add the C++ source file we are about to create
add_library(gpio-lib SHARED gpio-lib.cpp)

# Locate Android's logging library so you can debug in Logcat
find_library(log-lib log)

# Link your library with the logging library
target_link_libraries(gpio-lib ${log-lib})

add_library(liquidcrystal-i2c STATIC
        external/liquidcrystal-i2c/LiquidCrystal_I2C.cpp
        external/liquidcrystal-i2c/I2CIO.cpp
        external/liquidcrystal-i2c/LCD.cpp
)
# Ensure headers are found
target_include_directories(liquidcrystal-i2c PUBLIC external/liquidcrystal-i2c)

target_link_libraries(gpio-lib liquidcrystal-i2c ${log-lib})
