#pragma once

#include <cstdint>
#include <cstddef>

struct NativeExif {
    char make[64];
    char model[64];
    char aperture[32];
    char focal[32];
    char iso[16];
    char exposure[32];
    char flash[16];
    char datetime[32];
    int32_t width;
    int32_t height;
    int has_gps;
    double latitude;
    double longitude;
};

void parse_exif(const uint8_t* data, size_t length, NativeExif* out);
