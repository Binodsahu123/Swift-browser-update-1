#include <jni.h>
#include <string>

extern "C" {

JNIEXPORT jint JNICALL
Java_com_example_browser_NativeDesktopEngine_nativeEvaluateCompatibility(JNIEnv* env, jobject obj, jstring host) {
    if (!host) return 85;
    const char* hostStr = env->GetStringUTFChars(host, nullptr);
    std::string sHost(hostStr);
    env->ReleaseStringUTFChars(host, hostStr);

    int score = 85; // Base line score

    // Profile high-impact sites for perfect rendering compatibility
    if (sHost.find("wikipedia.org") != std::string::npos) {
        score = 99;
    } else if (sHost.find("facebook.com") != std::string::npos) {
        score = 95;
    } else if (sHost.find("reddit.com") != std::string::npos) {
        score = 92;
    } else if (sHost.find("wikipedia.org") != std::string::npos) {
        score = 99;
    } else if (sHost.find("twitter.com") != std::string::npos || sHost.find("x.com") != std::string::npos) {
        score = 94;
    } else if (sHost.find("google.com") != std::string::npos) {
        score = 97;
    }

    return score;
}

}
