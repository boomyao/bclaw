cmake_minimum_required(VERSION 3.22.1)

project(bclaw_remote)

set(ENET_NO_INSTALL ON CACHE BOOL "Disable ENet installation" FORCE)
add_subdirectory(third_party/enet)

add_library(
    bclaw_remote
    SHARED
    native_enet.c
    third_party/nanors/rs.c
)

target_include_directories(
    bclaw_remote
    PRIVATE
    third_party/enet/include
    third_party/nanors
    third_party/nanors/deps/obl
)
target_compile_definitions(bclaw_remote PRIVATE OBLAS_TINY)
target_link_libraries(bclaw_remote PRIVATE enet log)
