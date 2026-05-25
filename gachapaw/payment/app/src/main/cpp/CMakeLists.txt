cmake_minimum_required(VERSION 3.22.1)

# Name your library (e.g., "native-lib")
project("gpio-lib")

# Add the C++ source file we are about to create
add_library(gpio-lib SHARED gpio-lib.cpp)

# Locate Android's logging library so you can debug in Logcat
find_library(log-lib log)

# Link your library with the logging library
target_link_libraries(gpio-lib ${log-lib})