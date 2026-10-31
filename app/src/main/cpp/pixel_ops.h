#pragma once

#include <cstdint>
#include <cstddef>
#include <vector>

void apply_color_matrix_rgba(
        uint8_t* pixels,
        int width,
        int height,
        int stride,
        const float matrix[20]
);

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
);

// packed [gridIndex, dateIndex] pairs for each date header
std::vector<int> build_timeline_boundaries(
        const int64_t* dates,
        int count,
        int zone_offset_seconds
);
