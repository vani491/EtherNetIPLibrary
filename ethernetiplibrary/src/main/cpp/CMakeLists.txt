cmake_minimum_required(VERSION 3.18.1)
project(opener_android)



# Platform-specific flags that were originally in OpENer_PLATFORM_INCLUDES.cmake
# These replicate the logic of that macro directly

# Set required C flags
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=c99 -Werror=implicit-function-declaration -fcommon")

# Define macros used in POSIX port
add_definitions(-D_POSIX_C_SOURCE=200112L -D_GNU_SOURCE -DOPENER_POSIX)

# Define RESTRICT macro based on C or C++
get_property(languages GLOBAL PROPERTY ENABLED_LANGUAGES)
if ("CXX" IN_LIST languages)
    add_definitions(-DRESTRICT=)
else()
    add_definitions(-DRESTRICT=restrict)
endif()




file(GLOB CIP_SRC        "${CMAKE_CURRENT_SOURCE_DIR}/opener/cip/*.c")
file(GLOB ENET_ENCAP_SRC "${CMAKE_CURRENT_SOURCE_DIR}/opener/enet_encap/*.c")
file(GLOB UTILS_SRC      "${CMAKE_CURRENT_SOURCE_DIR}/opener/utils/*.c")
file(GLOB PORTS_SRC      "${CMAKE_CURRENT_SOURCE_DIR}/opener/ports/*.c")
file(GLOB POSIX_APP_SRC  "${CMAKE_CURRENT_SOURCE_DIR}/opener/ports/POSIX/sample_application/*.c")
file(GLOB POSIX_SRC      "${CMAKE_CURRENT_SOURCE_DIR}/opener/ports/POSIX/*.c")
file(GLOB NVDATA_SRC      "${CMAKE_CURRENT_SOURCE_DIR}/opener/ports/nvdata/*.c")
set(OPENER_SRC
        ${CIP_SRC}
        ${ENET_ENCAP_SRC}
        ${UTILS_SRC}
        ${PORTS_SRC}
        ${POSIX_APP_SRC}
        ${POSIX_SRC}
        ${NVDATA_SRC}
)

add_library(opener-lib SHARED
        ${OPENER_SRC}
        native-lib.cpp
)

find_library(log-lib log)

target_link_libraries(opener-lib
        ${log-lib}
        # other libraries...
)

target_include_directories(opener-lib PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/opener
        ${CMAKE_CURRENT_SOURCE_DIR}/opener/cip
        ${CMAKE_CURRENT_SOURCE_DIR}/opener/enet_encap
        ${CMAKE_CURRENT_SOURCE_DIR}/opener/utils
        ${CMAKE_CURRENT_SOURCE_DIR}/opener/ports
        ${CMAKE_CURRENT_SOURCE_DIR}/opener/ports/POSIX
        ${CMAKE_CURRENT_SOURCE_DIR}/opener/ports/POSIX/sample_application
        ${CMAKE_CURRENT_SOURCE_DIR}/opener/ports/nvdata
)

target_compile_definitions(opener-lib PRIVATE
        PC_OPENER_ETHERNET_BUFFER_SIZE=512
        OPENER_IS_DLR_DEVICE=0
        CIP_FILE_OBJECT=0
        CIP_SECURITY_OBJECTS=0
)
