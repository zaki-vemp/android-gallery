cmake_minimum_required(VERSION 3.22.1)
project(gallery_native)

add_library(gallery_native SHARED
        jni_bridge.cpp
        image_decode.cpp
        exif_parser.cpp
        pixel_ops.cpp
)

target_compile_features(gallery_native PRIVATE cxx_std_17)
target_compile_options(gallery_native PRIVATE
        -O3
        -ffast-math
        -fvisibility=hidden
        -fno-exceptions
        -fno-rtti
)

find_library(log-lib log)
find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(gallery_native
        ${log-lib}
        ${android-lib}
        ${jnigraphics-lib}
)
