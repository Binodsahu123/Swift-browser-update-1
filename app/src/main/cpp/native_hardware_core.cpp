#include <jni.h>
#include <string>
#include <android/log.h>
#include <aaudio/AAudio.h>
#include <vector>

#define LOG_TAG "CustomBrowserCore"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global Hardware Capture and JNI caching references
AAudioStream *audioStream = nullptr;
jobject globalBridgeRef = nullptr;
JavaVM *cachedJVM = nullptr;
jclass cachedBridgeClass = nullptr;
jmethodID midOnPcmBufferAvailable = nullptr;

/**
 * High-performance System JNI Initialization.
 * Runs instantly upon System.loadLibrary("native_media_bridge").
 * Resolves and caches classes/method references under compiled memory to avoid classloading failures in background threads.
 */
jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    cachedJVM = vm;
    JNIEnv* env = nullptr;
    
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        LOGE("Core JNI Error: Failed to retrieve JNI Env context during JNI_OnLoad.");
        return JNI_ERR;
    }

    // Locate the NativeBridge coordinator using the precise package path
    jclass localClassRef = env->FindClass("com/swift/browser/browser/NativeBridge");
    if (localClassRef == nullptr) {
        LOGE("Core JNI Error: Could not resolve class path 'com/swift/browser/browser/NativeBridge'.");
        return JNI_ERR;
    }
    
    // Cache the class reference in clean memory as a global reference
    cachedBridgeClass = reinterpret_cast<jclass>(env->NewGlobalRef(localClassRef));
    
    // Resolve the internal raw frame callback method footprint
    midOnPcmBufferAvailable = env->GetMethodID(cachedBridgeClass, "onRawPcmBufferAvailable", "([B)V");
    if (midOnPcmBufferAvailable == nullptr) {
        LOGE("Core JNI Error: Could not locate method 'onRawPcmBufferAvailable' with signature '([B)V'.");
        return JNI_ERR;
    }

    LOGI("Core WebRTC/Audio JNI: Fully registered mapping targets successfully under OnLoad JNI.");
    return JNI_VERSION_1_6;
}

// Asynchronous AAudio hardware input callback processing PCM frames on independent realtime threads
aaudio_data_callback_result_t aaudioDataCallback(
    AAudioStream *stream, 
    void *userData, 
    void *audioData, 
    int32_t numFrames
) {
    if (numFrames <= 0 || audioData == nullptr || cachedJVM == nullptr) {
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }

    JNIEnv *env = nullptr;
    bool isAttached = false;
    
    // Check if the current thread is attached to our JVM environment
    jint envStatus = cachedJVM->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (envStatus == JNI_EDETACHED) {
        if (cachedJVM->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            isAttached = true;
        } else {
            LOGE("Hardware Core: Thread Attachment failed.");
            return AAUDIO_CALLBACK_RESULT_CONTINUE;
        }
    }

    if (env != nullptr && globalBridgeRef != nullptr && midOnPcmBufferAvailable != nullptr) {
        // Compute byte size (16-bit Mono = 2 bytes per sample frame)
        int32_t byteLength = numFrames * sizeof(int16_t);
        jbyteArray byteArray = env->NewByteArray(byteLength);
        
        if (byteArray != nullptr) {
            // Un-truncated low-level memory block transfer safely mirroring sound fields into JVM
            env->SetByteArrayRegion(byteArray, 0, byteLength, static_cast<const jbyte*>(audioData));
            
            // Forward PCM frame directly up into Kotlin's NativeBridge stream proxy
            env->CallVoidMethod(globalBridgeRef, midOnPcmBufferAvailable, byteArray);
            // Reclaim local ref storage space immediately
            env->DeleteLocalRef(byteArray);
        }
    }

    if (isAttached) {
        cachedJVM->DetachCurrentThread();
    }

    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_example_browser_NativeBridge_startNativeAudioCapture(JNIEnv *env, jobject thiz) {
    LOGI("Hardware Core: Initiating raw physical input direct AAudio interface...");
    
    if (audioStream != nullptr) {
        LOGI("Hardware Core: AAudio capture already active.");
        return;
    }

    // Persist NativeBridge instance reference globally so background threads can invoke callbacks safely
    globalBridgeRef = env->NewGlobalRef(thiz);

    AAudioStreamBuilder *builder = nullptr;
    aaudio_result_t result = AAudio_createStreamBuilder(&builder);
    
    if (result == AAUDIO_OK && builder != nullptr) {
        // Standard high priority parameters for un-degraded input
        AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_INPUT);
        AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
        AAudioStreamBuilder_setSampleRate(builder, 16000); // Perfect 16KHz voice sampling
        AAudioStreamBuilder_setChannelCount(builder, 1);    // Mono Recording track
        AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
        AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
#if __ANDROID_API__ >= 28
        AAudioStreamBuilder_setInputPreset(builder, AAUDIO_INPUT_PRESET_VOICE_RECOGNITION);
#endif
        AAudioStreamBuilder_setDataCallback(builder, aaudioDataCallback, nullptr);

        result = AAudioStreamBuilder_openStream(builder, &audioStream);
        if (result == AAUDIO_OK && audioStream != nullptr) {
            result = AAudioStream_requestStart(audioStream);
            if (result == AAUDIO_OK) {
                LOGI("Hardware Core: Direct physical AAudio recorders successfully started and listening!");
            } else {
                LOGE("Hardware Core: Failed starting stream capture. Error code: %d", result);
                AAudioStream_close(audioStream);
                audioStream = nullptr;
            }
        } else {
            LOGE("Hardware Core: Failed establishing stream connection interface. Error code: %d", result);
            audioStream = nullptr;
        }
        
        AAudioStreamBuilder_delete(builder);
    } else {
        LOGE("Hardware Core: Stream builder execution faulted. Error code: %d", result);
    }
}

JNIEXPORT void JNICALL
Java_com_example_browser_NativeBridge_stopNativeAudioCapture(JNIEnv *env, jobject thiz) {
    LOGI("Hardware Core: Halting and disarming physical microphone feeds...");
    
    if (audioStream != nullptr) {
        AAudioStream_requestStop(audioStream);
        AAudioStream_close(audioStream);
        audioStream = nullptr;
        LOGI("Hardware Core: Successfully released and cleaned up AAudio stream.");
    }
    
    if (globalBridgeRef != nullptr) {
        env->DeleteGlobalRef(globalBridgeRef);
        globalBridgeRef = nullptr;
    }
}

}
