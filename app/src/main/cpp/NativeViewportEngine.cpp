#include <jni.h>
#include <algorithm>

extern "C" {

JNIEXPORT jfloat JNICALL
Java_com_example_browser_NativeDesktopEngine_nativeCalculateScale(JNIEnv* env, jobject obj, jint containerWidth, jint containerHeight) {
    if (containerWidth <= 0) return 0.25f;
    
    // Core layout bounds calculation: maps target desktop density (1280px) to screen
    float targetWidth = 1280.0f;
    float calculatedScale = (float)containerWidth / targetWidth;
    
    // Clamp between standard comfortable viewport zoom levels (0.1f to 2.0f)
    return std::max(0.1f, std::min(calculatedScale, 2.0f));
}

}
