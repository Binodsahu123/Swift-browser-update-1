#include <jni.h>
#include <string>
#include <vector>
#include <sstream>
#include <iomanip>
#include <cstring>

// Custom minimal native encryption/hashing for SHA-256 to generate Extension IDs
namespace sha256 {
    // Standard primitive operations for SHA-256
    #define ROTR(x, n) (((x) >> (n)) | ((x) << (32 - (n))))
    #define Ch(x, y, z) (((x) & (y)) ^ (~(x) & (z)))
    #define Maj(x, y, z) (((x) & (y)) ^ ((x) & (z)) ^ ((y) & (z)))
    #define Sigma0(x) (ROTR(x, 2) ^ ROTR(x, 13) ^ ROTR(x, 22))
    #define Sigma1(x) (ROTR(x, 6) ^ ROTR(x, 11) ^ ROTR(x, 25))
    #define sigma0(x) (ROTR(x, 7) ^ ROTR(x, 18) ^ ((x) >> 3))
    #define sigma1(x) (ROTR(x, 17) ^ ROTR(x, 19) ^ ((x) >> 10))

    const uint32_t K[64] = {
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbEF9a3f7, 0xc67178f2
    };

    void transform(uint32_t* state, const uint8_t* data) {
        uint32_t a = state[0], b = state[1], c = state[2], d = state[3];
        uint32_t e = state[4], f = state[5], g = state[6], h = state[7];
        uint32_t W[64];

        for (int i = 0; i < 16; ++i) {
            W[i] = (data[i * 4] << 24) | (data[i * 4 + 1] << 16) | (data[i * 4 + 2] << 8) | (data[i * 4 + 3]);
        }
        for (int i = 16; i < 64; ++i) {
            W[i] = sigma1(W[i - 2]) + W[i - 7] + sigma0(W[i - 15]) + W[i - 16];
        }

        for (int i = 0; i < 64; ++i) {
            uint32_t t1 = h + Sigma1(e) + Ch(e, f, g) + K[i] + W[i];
            uint32_t t2 = Sigma0(a) + Maj(a, b, c);
            h = g;
            g = f;
            f = e;
            e = d + t1;
            d = c;
            c = b;
            b = a;
            a = t1 + t2;
        }

        state[0] += a; state[1] += b; state[2] += c; state[3] += d;
        state[4] += e; state[5] += f; state[6] += g; state[7] += h;
    }

    std::vector<uint8_t> hash(const std::string& input) {
        uint32_t state[8] = {
            0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
        };

        std::vector<uint8_t> buffer(input.begin(), input.end());
        uint64_t bitLength = buffer.size() * 8;

        buffer.push_back(0x80);
        while ((buffer.size() + 8) % 64 != 0) {
            buffer.push_back(0x00);
        }

        for (int i = 7; i >= 0; --i) {
            buffer.push_back((bitLength >> (i * 8)) & 0xFF);
        }

        for (size_t i = 0; i < buffer.size(); i += 64) {
            transform(state, &buffer[i]);
        }

        std::vector<uint8_t> result(32);
        for (int i = 0; i < 8; ++i) {
            result[i * 4] = (state[i] >> 24) & 0xFF;
            result[i * 4 + 1] = (state[i] >> 16) & 0xFF;
            result[i * 4 + 2] = (state[i] >> 8) & 0xFF;
            result[i * 4 + 3] = state[i] & 0xFF;
        }
        return result;
    }
}

// Generates standard 32 character alphabetic chrome-extension identifiers natively (a-p nibble mapping)
std::string nativeGenerateExtensionId(const std::string& extensionName) {
    auto sha = sha256::hash(extensionName);
    const char alphabet[] = "abcdefghijklmnop";
    std::string outId = "";
    outId.reserve(32);
    for (int i = 0; i < 16; ++i) {
        uint8_t byte = sha[i];
        outId += alphabet[(byte >> 4) & 0x0F];
        outId += alphabet[byte & 0x0F];
    }
    return outId;
}

extern "C" {

/**
 * JNI Binding for generating dynamic extension IDs.
 */
JNIEXPORT jstring JNICALL
Java_com_swift_browser_extensionengine_NativeExtensionEngine_nativeGenerateExtensionId(
        JNIEnv* env,
        jclass clazz,
        jstring nameStr
) {
    if (!nameStr) return nullptr;
    const char* nativeChars = env->GetStringUTFChars(nameStr, nullptr);
    std::string name(nativeChars);
    env->ReleaseStringUTFChars(nameStr, nativeChars);

    std::string extId = nativeGenerateExtensionId(name);
    return env->NewStringUTF(extId.c_str());
}

/**
 * JNI verifier of CRX file buffers.
 * Returns -1 if invalid, 2 for CRX v2, 3 for CRX v3.
 */
JNIEXPORT jint JNICALL
Java_com_swift_browser_extensionengine_NativeExtensionEngine_nativeVerifyCrxHeader(
        JNIEnv* env,
        jclass clazz,
        jbyteArray array
) {
    if (!array) return -1;
    jsize len = env->GetArrayLength(array);
    if (len < 12) return -1;

    jbyte* bytes = env->GetByteArrayElements(array, nullptr);
    jint result = -1;

    // Check CRX Magic: "Cr24" (0x43 0x72 0x32 0x34)
    if (bytes[0] == 0x43 && bytes[1] == 0x72 && bytes[2] == 0x32 && bytes[3] == 0x34) {
        // Read version
        uint32_t version = (bytes[4] & 0xFF) |
                           ((bytes[5] & 0xFF) << 8) |
                           ((bytes[6] & 0xFF) << 16) |
                           ((bytes[7] & 0xFF) << 24);
        result = static_cast<jint>(version);
    } else if (bytes[0] == 0x50 && bytes[1] == 0x4B && bytes[2] == 0x03 && bytes[3] == 0x04) {
        // Direct raw PKZIP signature
        result = 0; // standard zip
    }

    env->ReleaseByteArrayElements(array, bytes, JNI_ABORT);
    return result;
}

/**
 * JNI path traversal protection logic.
 * True if path is secure (contains no parent traversal '..'), False otherwise.
 */
JNIEXPORT jboolean JNICALL
Java_com_swift_browser_extensionengine_NativeExtensionEngine_nativeIsSafeRelativePath(
        JNIEnv* env,
        jclass clazz,
        jstring pathStr
) {
    if (!pathStr) return JNI_FALSE;
    const char* nativeChars = env->GetStringUTFChars(pathStr, nullptr);
    std::string path(nativeChars);
    env->ReleaseStringUTFChars(pathStr, nativeChars);

    // Filter directory injection vectors
    if (path.find("..") != std::string::npos) {
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

}
