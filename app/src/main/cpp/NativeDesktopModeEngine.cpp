#include <jni.h>
#include <android/log.h>

#define LOG_TAG "NativeDesktopModeEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_browser_NativeDesktopEngine_nativeIsEngineOnline(JNIEnv* env, jobject obj) {
    LOGI("Native Desktop Engine handshake active & online");
    return JNI_TRUE;
}

}
