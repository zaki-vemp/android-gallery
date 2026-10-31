#include "image_decode.h"

#include <algorithm>
#include <cstdint>
#include <dlfcn.h>
#include <android/api-level.h>
#include <android/bitmap.h>

namespace {

constexpr int kDecoderSuccess = 0;
constexpr int kBitmapFormatRgba8888 = 1;

struct AImageDecoder;
struct AImageDecoderHeaderInfo;

struct ImageDecoderApi {
    int (*createFromFd)(int fd, AImageDecoder** outDecoder);
    void (*destroy)(AImageDecoder* decoder);
    const AImageDecoderHeaderInfo* (*header)(AImageDecoder* decoder);
    int32_t (*width)(const AImageDecoderHeaderInfo* info);
    int32_t (*height)(const AImageDecoderHeaderInfo* info);
    int (*setTargetSize)(AImageDecoder* decoder, int32_t width, int32_t height);
    int (*setFormat)(AImageDecoder* decoder, int32_t format);
    int (*decode)(AImageDecoder* decoder, void* pixels, size_t stride, size_t size);
    bool ready = false;
};

ImageDecoderApi load_api() {
    ImageDecoderApi api{};
    void* lib = dlopen("libjnigraphics.so", RTLD_NOW);
    if (!lib) return api;
    api.createFromFd = reinterpret_cast<int (*)(int, AImageDecoder**)>(
            dlsym(lib, "AImageDecoder_createFromFd"));
    api.destroy = reinterpret_cast<void (*)(AImageDecoder*)>(
            dlsym(lib, "AImageDecoder_delete"));
    api.header = reinterpret_cast<const AImageDecoderHeaderInfo* (*)(AImageDecoder*)>(
            dlsym(lib, "AImageDecoder_getHeaderInfo"));
    api.width = reinterpret_cast<int32_t (*)(const AImageDecoderHeaderInfo*)>(
            dlsym(lib, "AImageDecoderHeaderInfo_getWidth"));
    api.height = reinterpret_cast<int32_t (*)(const AImageDecoderHeaderInfo*)>(
            dlsym(lib, "AImageDecoderHeaderInfo_getHeight"));
    api.setTargetSize = reinterpret_cast<int (*)(AImageDecoder*, int32_t, int32_t)>(
            dlsym(lib, "AImageDecoder_setTargetSize"));
    api.setFormat = reinterpret_cast<int (*)(AImageDecoder*, int32_t)>(
            dlsym(lib, "AImageDecoder_setAndroidBitmapFormat"));
    api.decode = reinterpret_cast<int (*)(AImageDecoder*, void*, size_t, size_t)>(
            dlsym(lib, "AImageDecoder_decodeImage"));
    api.ready = api.createFromFd && api.destroy && api.header && api.width &&
                api.height && api.setTargetSize && api.setFormat && api.decode;
    return api;
}

const ImageDecoderApi& decoder_api() {
    static const ImageDecoderApi api = load_api();
    return api;
}

jobject create_bitmap(JNIEnv* env, int width, int height) {
    jclass bitmap_class = env->FindClass("android/graphics/Bitmap");
    if (!bitmap_class) return nullptr;
    jmethodID create = env->GetStaticMethodID(
            bitmap_class,
            "createBitmap",
            "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;"
    );
    jclass config_class = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID argb = env->GetStaticFieldID(
            config_class, "ARGB_8888", "Landroid/graphics/Bitmap$Config;"
    );
    jobject config = env->GetStaticObjectField(config_class, argb);
    return env->CallStaticObjectMethod(bitmap_class, create, width, height, config);
}

void target_size(int src_w, int src_h, int max_size, int* out_w, int* out_h) {
    if (src_w <= max_size && src_h <= max_size) {
        *out_w = src_w;
        *out_h = src_h;
        return;
    }
    if (src_w >= src_h) {
        *out_w = max_size;
        *out_h = std::max(1, (int) ((int64_t) src_h * max_size / src_w));
    } else {
        *out_h = max_size;
        *out_w = std::max(1, (int) ((int64_t) src_w * max_size / src_h));
    }
}

} // namespace

jobject decode_thumbnail_from_fd(JNIEnv* env, int fd, int max_size) {
    const ImageDecoderApi& api = decoder_api();
    if (!api.ready || android_get_device_api_level() < 30 || fd < 0 || max_size <= 0) {
        return nullptr;
    }

    AImageDecoder* decoder = nullptr;
    if (api.createFromFd(fd, &decoder) != kDecoderSuccess || !decoder) {
        return nullptr;
    }

    const AImageDecoderHeaderInfo* header = api.header(decoder);
    const int src_w = api.width(header);
    const int src_h = api.height(header);
    int dst_w = 0;
    int dst_h = 0;
    target_size(src_w, src_h, max_size, &dst_w, &dst_h);

    api.setTargetSize(decoder, dst_w, dst_h);
    api.setFormat(decoder, kBitmapFormatRgba8888);

    jobject bitmap = create_bitmap(env, dst_w, dst_h);
    if (!bitmap) {
        api.destroy(decoder);
        return nullptr;
    }

    AndroidBitmapInfo info{};
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        api.destroy(decoder);
        return nullptr;
    }

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        api.destroy(decoder);
        return nullptr;
    }

    const int result = api.decode(
            decoder,
            pixels,
            info.stride,
            (size_t) info.stride * info.height
    );
    AndroidBitmap_unlockPixels(env, bitmap);
    api.destroy(decoder);

    if (result != kDecoderSuccess) {
        return nullptr;
    }
    return bitmap;
}
