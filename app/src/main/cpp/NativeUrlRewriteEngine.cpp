#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "NativeUrlRewriteEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_example_browser_NativeDesktopEngine_nativeRewriteUrl(JNIEnv* env, jobject obj, jstring url, jboolean toDesktop) {
    if (!url) return nullptr;
    // URLs do not require host rewrites; Desktop UA and viewport handle desktop rendering cleanly without redirect loops.
    return url;
}

}
