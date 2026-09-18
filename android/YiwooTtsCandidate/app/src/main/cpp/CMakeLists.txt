cmake_minimum_required(VERSION 3.22.1)
project(yiwoo_espeak LANGUAGES C CXX)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(COMPILE_INTONATIONS OFF CACHE BOOL "" FORCE)
set(ENABLE_TESTS OFF CACHE BOOL "" FORCE)
set(USE_ASYNC OFF CACHE BOOL "" FORCE)
set(USE_MBROLA OFF CACHE BOOL "" FORCE)
set(USE_LIBSONIC OFF CACHE BOOL "" FORCE)
set(USE_LIBPCAUDIO OFF CACHE BOOL "" FORCE)
set(USE_KLATT OFF CACHE BOOL "" FORCE)
set(USE_SPEECHPLAYER OFF CACHE BOOL "" FORCE)
set(EXTRA_cmn ON CACHE BOOL "" FORCE)
set(EXTRA_ru ON CACHE BOOL "" FORCE)

add_subdirectory(espeak-ng)
add_library(yiwoo_espeak SHARED native_espeak.c)
target_include_directories(yiwoo_espeak PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/espeak-ng/src/include
)
find_library(log-lib log)
target_link_libraries(yiwoo_espeak PRIVATE espeak-ng ${log-lib})
