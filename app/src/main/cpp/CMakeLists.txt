cmake_minimum_required(VERSION 3.22.1)
project(cubik555 LANGUAGES CXX)

add_library(
    cubik555
    SHARED
    five_by_five_core.cpp
    five_by_five_jni.cpp
)

target_compile_features(cubik555 PRIVATE cxx_std_20)
target_compile_options(
    cubik555
    PRIVATE
    -O3
    -DNDEBUG
    -fvisibility=hidden
    -fvisibility-inlines-hidden
)

set_target_properties(
    cubik555
    PROPERTIES
    INTERPROCEDURAL_OPTIMIZATION TRUE
)
