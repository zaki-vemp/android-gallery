#include "exif_parser.h"

#include <cstdio>
#include <cstring>
#include <cmath>

namespace {

uint16_t ru16(const uint8_t* p, bool le) {
    return le ? (uint16_t) (p[0] | (p[1] << 8)) : (uint16_t) (p[1] | (p[0] << 8));
}

uint32_t ru32(const uint8_t* p, bool le) {
    return le
           ? (uint32_t) (p[0] | (p[1] << 8) | (p[2] << 16) | (p[3] << 24))
           : (uint32_t) (p[3] | (p[2] << 8) | (p[1] << 16) | (p[0] << 24));
}

int32_t ri32(const uint8_t* p, bool le) {
    return (int32_t) ru32(p, le);
}

bool in_range(const uint8_t* base, size_t length, const uint8_t* p, size_t n) {
    return p >= base && p + n <= base + length;
}

void copy_ascii(char* dst, size_t dst_len, const uint8_t* src, size_t src_len) {
    if (dst_len == 0) return;
    size_t n = src_len < dst_len - 1 ? src_len : dst_len - 1;
    memcpy(dst, src, n);
    dst[n] = 0;
    for (size_t i = 0; i < n; ++i) {
        if (dst[i] == 0) break;
        if (dst[i] < 32) dst[i] = ' ';
    }
}

void format_rational(char* dst, size_t dst_len, int32_t num, int32_t den, bool as_exposure) {
    if (den == 0) {
        dst[0] = 0;
        return;
    }
    const double value = (double) num / (double) den;
    if (as_exposure && value > 0 && value < 1.0) {
        snprintf(dst, dst_len, "1/%ds", (int) std::lround(1.0 / value));
        return;
    }
    if (std::fabs(value - std::lround(value)) < 0.05) {
        snprintf(dst, dst_len, "%d", (int) std::lround(value));
    } else {
        snprintf(dst, dst_len, "%.1f", value);
    }
}

double rational(const uint8_t* p, bool le, bool signed_value) {
    if (signed_value) {
        int32_t num = ri32(p, le);
        int32_t den = ri32(p + 4, le);
        return den == 0 ? 0 : (double) num / (double) den;
    }
    uint32_t num = ru32(p, le);
    uint32_t den = ru32(p + 4, le);
    return den == 0 ? 0 : (double) num / (double) den;
}

double gps_coord(const uint8_t* tiff, size_t tiff_len, bool le, uint32_t offset, const char* ref) {
    if (!in_range(tiff, tiff_len, tiff + offset, 24)) return 0;
    const double d = rational(tiff + offset, le, false);
    const double m = rational(tiff + offset + 8, le, false);
    const double s = rational(tiff + offset + 16, le, false);
    double value = d + m / 60.0 + s / 3600.0;
    if (ref && (ref[0] == 'S' || ref[0] == 'W')) value = -value;
    return value;
}

void read_ifd(
        const uint8_t* file,
        size_t length,
        const uint8_t* tiff,
        size_t tiff_len,
        uint32_t ifd_offset,
        bool le,
        NativeExif* out,
        bool gps_ifd
) {
    if (!in_range(tiff, tiff_len, tiff + ifd_offset, 2)) return;
    const uint16_t count = ru16(tiff + ifd_offset, le);
    const uint8_t* entries = tiff + ifd_offset + 2;
    uint32_t exif_ifd = 0;
    uint32_t gps_off = 0;
    char lat_ref[4] = {0};
    char lon_ref[4] = {0};
    uint32_t lat_off = 0;
    uint32_t lon_off = 0;

    for (uint16_t i = 0; i < count; ++i) {
        const uint8_t* e = entries + (size_t) i * 12;
        if (!in_range(tiff, tiff_len, e, 12)) break;
        const uint16_t tag = ru16(e, le);
        const uint16_t type = ru16(e + 2, le);
        const uint32_t components = ru32(e + 4, le);
        const uint8_t* value = e + 8;
        const uint32_t unit = (type == 3) ? 2u : (type == 4 || type == 9) ? 4u : (type == 5 || type == 10) ? 8u : 1u;
        const size_t bytes = (size_t) components * unit;
        const uint8_t* data = value;
        if (bytes > 4) {
            const uint32_t off = ru32(value, le);
            if (!in_range(tiff, tiff_len, tiff + off, bytes)) continue;
            data = tiff + off;
        }

        if (gps_ifd) {
            if (tag == 1 && bytes > 0) copy_ascii(lat_ref, sizeof(lat_ref), data, bytes);
            if (tag == 2) lat_off = ru32(value, le);
            if (tag == 3 && bytes > 0) copy_ascii(lon_ref, sizeof(lon_ref), data, bytes);
            if (tag == 4) lon_off = ru32(value, le);
            continue;
        }

        switch (tag) {
            case 0x010F:
                copy_ascii(out->make, sizeof(out->make), data, bytes);
                break;
            case 0x0110:
                copy_ascii(out->model, sizeof(out->model), data, bytes);
                break;
            case 0x0100:
                out->width = (int32_t) (type == 3 ? ru16(data, le) : ru32(data, le));
                break;
            case 0x0101:
                out->height = (int32_t) (type == 3 ? ru16(data, le) : ru32(data, le));
                break;
            case 0x0132:
                copy_ascii(out->datetime, sizeof(out->datetime), data, bytes);
                break;
            case 0x8769:
                exif_ifd = ru32(value, le);
                break;
            case 0x8825:
                gps_off = ru32(value, le);
                break;
            case 0x829A: {
                format_rational(out->exposure, sizeof(out->exposure), ri32(data, le), ri32(data + 4, le), true);
                break;
            }
            case 0x829D: {
                char tmp[24];
                format_rational(tmp, sizeof(tmp), ri32(data, le), ri32(data + 4, le), false);
                snprintf(out->aperture, sizeof(out->aperture), "f/%s", tmp);
                break;
            }
            case 0x8827:
                snprintf(out->iso, sizeof(out->iso), "%u", type == 3 ? ru16(data, le) : ru32(data, le));
                break;
            case 0x920A: {
                char tmp[24];
                format_rational(tmp, sizeof(tmp), ri32(data, le), ri32(data + 4, le), false);
                snprintf(out->focal, sizeof(out->focal), "%s mm", tmp);
                break;
            }
            case 0xA20E: { // FocalLengthIn35mmFilm
                snprintf(out->aperture, sizeof(out->aperture), "%u", type == 3 ? ru16(data, le) : ru32(data, le));
                break;
            }
            case 0x9209: {
                const uint16_t flash = type == 3 ? ru16(data, le) : (uint16_t) ru32(data, le);
                snprintf(out->flash, sizeof(out->flash), "%s", (flash & 1) ? "On" : "Off");
                break;
            }
            case 0xA002:
                out->width = (int32_t) (type == 3 ? ru16(data, le) : ru32(data, le));
                break;
            case 0xA003:
                out->height = (int32_t) (type == 3 ? ru16(data, le) : ru32(data, le));
                break;
            default:
                break;
        }
        (void) file;
        (void) length;
    }

    if (!gps_ifd && exif_ifd) {
        read_ifd(file, length, tiff, tiff_len, exif_ifd, le, out, false);
    }
    if (!gps_ifd && gps_off) {
        read_ifd(file, length, tiff, tiff_len, gps_off, le, out, true);
    }
    if (gps_ifd && lat_off && lon_off) {
        out->latitude = gps_coord(tiff, tiff_len, le, lat_off, lat_ref);
        out->longitude = gps_coord(tiff, tiff_len, le, lon_off, lon_ref);
        out->has_gps = 1;
    }
}

const uint8_t* find_jpeg_exif(const uint8_t* data, size_t length, size_t* out_len) {
    if (length < 4 || data[0] != 0xFF || data[1] != 0xD8) return nullptr;
    size_t i = 2;
    while (i + 4 < length) {
        if (data[i] != 0xFF) return nullptr;
        const uint8_t marker = data[i + 1];
        if (marker == 0xDA || marker == 0xD9) return nullptr;
        const uint16_t seg_len = (uint16_t) ((data[i + 2] << 8) | data[i + 3]);
        if (seg_len < 2 || i + 2 + seg_len > length) return nullptr;
        if (marker == 0xE1 && seg_len > 8 && memcmp(data + i + 4, "Exif\0\0", 6) == 0) {
            *out_len = (size_t) seg_len - 8;
            return data + i + 10;
        }
        i += 2u + seg_len;
    }
    return nullptr;
}

} // namespace

void parse_exif(const uint8_t* data, size_t length, NativeExif* out) {
    memset(out, 0, sizeof(*out));
    size_t tiff_len = 0;
    const uint8_t* tiff = find_jpeg_exif(data, length, &tiff_len);
    if (!tiff) {
        // Some HEIF/JPEG buffers start at TIFF
        if (length > 8 && ((data[0] == 'I' && data[1] == 'I') || (data[0] == 'M' && data[1] == 'M'))) {
            tiff = data;
            tiff_len = length;
        } else {
            return;
        }
    }
    if (tiff_len < 8) return;
    const bool le = tiff[0] == 'I';
    if (!(le && tiff[1] == 'I') && !(tiff[0] == 'M' && tiff[1] == 'M')) return;
    const uint32_t ifd0 = ru32(tiff + 4, le);
    read_ifd(data, length, tiff, tiff_len, ifd0, le, out, false);
}
