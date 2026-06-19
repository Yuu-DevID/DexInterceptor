cmake_minimum_required(VERSION 3.22.1)
project("interceptor_native" C)

set(CMAKE_C_STANDARD 11)

add_library(interceptor_native SHARED
    native_hook.c
)

target_link_libraries(interceptor_native
    log
    dl
)
