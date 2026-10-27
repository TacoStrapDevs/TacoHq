cmake_minimum_required(VERSION 3.22.1)
project("tacostrap_native")

add_library(
    tacostrap_native
    SHARED
    tacostrap_native.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    tacostrap_native
    ${log-lib}
)
