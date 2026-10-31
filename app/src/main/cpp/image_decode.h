#pragma once

#include <jni.h>
#include <android/bitmap.h>

jobject decode_thumbnail_from_fd(JNIEnv* env, int fd, int max_size);
