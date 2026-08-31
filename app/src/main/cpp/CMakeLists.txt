cmake_minimum_required(VERSION 3.22.1)

project("native_media_bridge")

# Log configuration information
message(STATUS "Configuring Native Media Bridge NDK low-latency pipeline...")

find_library(log-lib log)
find_library(android-lib android)
find_library(aaudio-lib aaudio)

add_library(
    native_media_bridge
    SHARED
    native-media-bridge.cpp
    native_hardware_core.cpp
    NativeUrlRewriteEngine.cpp
    NativeViewportEngine.cpp
    NativeCompatibilityScorer.cpp
    NativeDesktopModeEngine.cpp
)

target_link_libraries(
    native_media_bridge
    ${log-lib}
    ${android-lib}
    ${aaudio-lib}
    OpenSLES
)
