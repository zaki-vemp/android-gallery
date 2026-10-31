#include "pixel_ops.h"

#include <algorithm>
#include <cmath>
#include <cstdint>

void apply_color_matrix_rgba(
        uint8_t* pixels,
        int width,
        int height,
        int stride,
        const float matrix[20]
) {
    for (int y = 0; y < height; ++y) {
        uint8_t* row = pixels + (size_t) y * (size_t) stride;
        for (int x = 0; x < width; ++x) {
            uint8_t* p = row + x * 4;
            const float r = p[0];
            const float g = p[1];
            const float b = p[2];
            const float a = p[3];
            float nr = matrix[0] * r + matrix[1] * g + matrix[2] * b + matrix[3] * a + matrix[4];
            float ng = matrix[5] * r + matrix[6] * g + matrix[7] * b + matrix[8] * a + matrix[9];
            float nb = matrix[10] * r + matrix[11] * g + matrix[12] * b + matrix[13] * a + matrix[14];
            float na = matrix[15] * r + matrix[16] * g + matrix[17] * b + matrix[18] * a + matrix[19];
            p[0] = (uint8_t) std::clamp((int) std::lround(nr), 0, 255);
            p[1] = (uint8_t) std::clamp((int) std::lround(ng), 0, 255);
            p[2] = (uint8_t) std::clamp((int) std::lround(nb), 0, 255);
            p[3] = (uint8_t) std::clamp((int) std::lround(na), 0, 255);
        }
    }
}

void blit_cover_rgba(
        const uint8_t* src,
        int src_w,
        int src_h,
        int src_stride,
        uint8_t* dst,
        int dst_stride,
        int left,
        int top,
        int right,
        int bottom
) {
    if (src_w <= 0 || src_h <= 0) return;
    const int dw = right - left;
    const int dh = bottom - top;
    if (dw <= 0 || dh <= 0) return;

    const float scale = std::max((float) dw / (float) src_w, (float) dh / (float) src_h);
    const float scaled_w = src_w * scale;
    const float scaled_h = src_h * scale;
    const float origin_x = (scaled_w - dw) * 0.5f;
    const float origin_y = (scaled_h - dh) * 0.5f;

    for (int y = 0; y < dh; ++y) {
        uint8_t* out = dst + (size_t) (top + y) * (size_t) dst_stride + left * 4;
        const float sy = (origin_y + y) / scale;
        int y0 = (int) sy;
        int y1 = std::min(y0 + 1, src_h - 1);
        y0 = std::clamp(y0, 0, src_h - 1);
        const float fy = sy - y0;
        const uint8_t* row0 = src + (size_t) y0 * (size_t) src_stride;
        const uint8_t* row1 = src + (size_t) y1 * (size_t) src_stride;
        for (int x = 0; x < dw; ++x) {
            const float sx = (origin_x + x) / scale;
            int x0 = (int) sx;
            int x1 = std::min(x0 + 1, src_w - 1);
            x0 = std::clamp(x0, 0, src_w - 1);
            const float fx = sx - x0;
            const uint8_t* p00 = row0 + x0 * 4;
            const uint8_t* p10 = row0 + x1 * 4;
            const uint8_t* p01 = row1 + x0 * 4;
            const uint8_t* p11 = row1 + x1 * 4;
            for (int c = 0; c < 4; ++c) {
                const float v0 = p00[c] + (p10[c] - p00[c]) * fx;
                const float v1 = p01[c] + (p11[c] - p01[c]) * fx;
                out[x * 4 + c] = (uint8_t) (v0 + (v1 - v0) * fy);
            }
        }
    }
}

std::vector<int> build_timeline_boundaries(
        const int64_t* dates,
        int count,
        int zone_offset_seconds
) {
    std::vector<int> packed;
    if (count <= 0) return packed;
    packed.reserve((size_t) count);
    int64_t last_day = INT64_MIN;
    int grid_index = 0;
    for (int i = 0; i < count; ++i) {
        int64_t local = dates[i] + zone_offset_seconds;
        int64_t day = local >= 0 ? local / 86400 : (local - 86399) / 86400;
        if (day != last_day) {
            packed.push_back(grid_index);
            packed.push_back(i);
            last_day = day;
            grid_index += 1;
        }
        grid_index += 1;
    }
    return packed;
}
