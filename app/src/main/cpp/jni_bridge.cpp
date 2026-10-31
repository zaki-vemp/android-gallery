#include "exif_parser.h"
#include "image_decode.h"
#include "pixel_ops.h"

#include <android/bitmap.h>
#include <jni.h>
#include <cstdio>
#include <cstring>
#include <vector>

extern "C" JNIEXPORT jobject JNICALL
Java_com_gallery_android_nativeengine_NativeMediaEngine_decodeThumbnail(
        JNIEnv* env,
        jclass,
        jint fd,
        jint maxSize
) {
    return decode_thumbnail_from_fd(env, fd, maxSize);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_gallery_android_nativeengine_NativeMediaEngine_parseExif(
        JNIEnv* env,
        jclass,
        jbyteArray bytes
) {
    if (!bytes) return nullptr;
    const jsize length = env->GetArrayLength(bytes);
    if (length <= 0) return nullptr;
    std::vector<uint8_t> buffer((size_t) length);
    env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte*>(buffer.data()));

    NativeExif exif{};
    parse_exif(buffer.data(), buffer.size(), &exif);

    jclass string_class = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(12, string_class, nullptr);
    auto set = [&](int index, const char* value) {
        if (!value || !value[0]) return;
        env->SetObjectArrayElement(result, index, env->NewStringUTF(value));
    };
    set(0, exif.make);
    set(1, exif.model);
    set(2, exif.aperture);
    set(3, exif.focal);
    set(4, exif.iso);
    set(5, exif.exposure);
    set(6, exif.flash);
    char wh[32];
    if (exif.width > 0) {
        snprintf(wh, sizeof(wh), "%d", exif.width);
        set(7, wh);
    }
    if (exif.height > 0) {
        snprintf(wh, sizeof(wh), "%d", exif.height);
        set(8, wh);
    }
    set(9, exif.datetime);
    if (exif.has_gps) {
        char gps[48];
        snprintf(gps, sizeof(gps), "%.7f", exif.latitude);
        set(10, gps);
        snprintf(gps, sizeof(gps), "%.7f", exif.longitude);
        set(11, gps);
    }
    return result;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_gallery_android_nativeengine_NativeMediaEngine_buildTimeline(
        JNIEnv* env,
        jclass,
        jlongArray dates,
        jint zoneOffsetSeconds
) {
    if (!dates) return env->NewIntArray(0);
    const jsize count = env->GetArrayLength(dates);
    jlong* raw = env->GetLongArrayElements(dates, nullptr);
    const std::vector<int> packed = build_timeline_boundaries(
            reinterpret_cast<const int64_t*>(raw),
            count,
            zoneOffsetSeconds
    );
    env->ReleaseLongArrayElements(dates, raw, JNI_ABORT);
    jintArray result = env->NewIntArray((jsize) packed.size());
    if (!packed.empty()) {
        env->SetIntArrayRegion(result, 0, (jsize) packed.size(), packed.data());
    }
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_gallery_android_nativeengine_NativeMediaEngine_applyColorMatrix(
        JNIEnv* env,
        jclass,
        jobject bitmap,
        jfloatArray matrix
) {
    if (!bitmap || !matrix || env->GetArrayLength(matrix) < 20) return JNI_FALSE;
    AndroidBitmapInfo info{};
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) return JNI_FALSE;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return JNI_FALSE;
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) return JNI_FALSE;
    float m[20];
    env->GetFloatArrayRegion(matrix, 0, 20, m);
    apply_color_matrix_rgba(
            static_cast<uint8_t*>(pixels),
            (int) info.width,
            (int) info.height,
            (int) info.stride,
            m
    );
    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_gallery_android_nativeengine_NativeMediaEngine_blitCover(
        JNIEnv* env,
        jclass,
        jobject src,
        jobject dst,
        jint left,
        jint top,
        jint right,
        jint bottom
) {
    if (!src || !dst) return JNI_FALSE;
    AndroidBitmapInfo src_info{};
    AndroidBitmapInfo dst_info{};
    if (AndroidBitmap_getInfo(env, src, &src_info) != ANDROID_BITMAP_RESULT_SUCCESS) return JNI_FALSE;
    if (AndroidBitmap_getInfo(env, dst, &dst_info) != ANDROID_BITMAP_RESULT_SUCCESS) return JNI_FALSE;
    if (src_info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return JNI_FALSE;
    if (dst_info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return JNI_FALSE;
    void* src_pixels = nullptr;
    void* dst_pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, src, &src_pixels) != ANDROID_BITMAP_RESULT_SUCCESS) return JNI_FALSE;
    if (AndroidBitmap_lockPixels(env, dst, &dst_pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        AndroidBitmap_unlockPixels(env, src);
        return JNI_FALSE;
    }
    blit_cover_rgba(
            static_cast<const uint8_t*>(src_pixels),
            (int) src_info.width,
            (int) src_info.height,
            (int) src_info.stride,
            static_cast<uint8_t*>(dst_pixels),
            (int) dst_info.stride,
            left, top, right, bottom
    );
    AndroidBitmap_unlockPixels(env, dst);
    AndroidBitmap_unlockPixels(env, src);
    return JNI_TRUE;
}
