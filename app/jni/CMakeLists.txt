cmake_minimum_required(VERSION 3.10.2 FATAL_ERROR)
project(tgx VERSION 1.0.0 LANGUAGES C CXX)
message(STATUS "Building ${PROJECT_NAME} platform: ${ANDROID_PLATFORM} abi: ${ANDROID_ABI} type: ${CMAKE_BUILD_TYPE} flavor: ${TGX_FLAVOR} (${PROJECT_SOURCE_DIR})")

if (NOT ${TGX_FLAVOR} MATCHES "legacy|lollipop|latest")
  message(FATAL_ERROR "Unknown flavor: ${TGX_FLAVOR}")
endif()

# == Include ==

include("${CMAKE_HOME_DIRECTORY}/cmake/ReadVariables.cmake")
include("${CMAKE_HOME_DIRECTORY}/cmake/Prefix.cmake")
include("${CMAKE_HOME_DIRECTORY}/cmake/Join.cmake")

# == Dirs ==

set(THIRDPARTY_DIR "${PROJECT_SOURCE_DIR}/third_party")
set(TGX_ROOT_DIR "${PROJECT_SOURCE_DIR}/../..")
set(TDLIB_DIR "${TGX_ROOT_DIR}/tdlib")
set(UTILS_DIR "${THIRDPARTY_DIR}/jni-utils")

set(ENABLE_TGVOIP yes)

# Using webp only if building for 32-bit platform
#if (${ANDROID_ABI} STREQUAL "armeabi-v7a" OR ${ANDROID_ABI} STREQUAL "x86")
#  set(USE_WEBP yes)
#else()
#  set(USE_WEBP no)
#endif()
set(USE_WEBP yes)

if (${ANDROID_ABI} STREQUAL "x86_64" OR ${ANDROID_ABI} STREQUAL "arm64-v8a")
  set(FFMPEG_ABI ${ANDROID_ABI})
  if (${TGX_FLAVOR} MATCHES "legacy")
    message(FATAL_ERROR "Invalid combination: ${TGX_FLAVOR} ${ANDROID_ABI}")
  endif()
elseif(${ANDROID_ABI} STREQUAL "x86")
  set(FFMPEG_ABI "i686")
elseif(${ANDROID_ABI} STREQUAL "armeabi-v7a")
  set(FFMPEG_ABI "armv7-a")
else()
  message(FATAL_ERROR "Unknown abi: ${ANDROID_ABI}")
endif()
set(FFMPEG_SRC_DIR "${THIRDPARTY_DIR}/ffmpeg")
set(FFMPEG_DIR "${FFMPEG_SRC_DIR}/build/${TGX_FLAVOR}/${FFMPEG_ABI}")
set(FFMPEG_LIBS
  swresample
  avformat
  swscale
  avcodec
  avfilter
  avutil
)

set(VPX_DIR "${THIRDPARTY_DIR}/libvpx/build/${TGX_FLAVOR}/${FFMPEG_ABI}")
set(VPX_LIB_PATH "${VPX_DIR}/lib/libvpx.a")

# == Constants ==
# $IS_ARM_FAMILY – armeabi-v7a or arm64-v8a

if (${ANDROID_ABI} STREQUAL "armeabi-v7a" OR ${ANDROID_ABI} STREQUAL "arm64-v8a")
  set(IS_ARM_FAMILY yes)
else()
  set(IS_ARM_FAMILY no)
endif()

# == CMake global settings ==

set(CMAKE_SKIP_RPATH ON)
set(CMAKE_BUILD_WITH_INSTALL_RPATH ON)

if (${IS_ARM_FAMILY})
  enable_language(ASM)
else()
  enable_language(ASM_NASM)
endif()
set(ORIGINAL_CMAKE_ASM_FLAGS "${CMAKE_ASM_FLAGS}")

set(EXCLUDE_LIBS
  libjni-utils.a
  libyuv.a
  "${VPX_LIB_PATH}"
  libopus.a
  libopusfile.a
  librlottie.a
  liblz4.a
)
if (${USE_WEBP})
  list(APPEND EXCLUDE_LIBS
    libwebpdecoder_static.a
  )
endif()
foreach(ffmpeg_lib ${FFMPEG_LIBS})
  list(APPEND EXCLUDE_LIBS
    "${FFMPEG_DIR}/lib/lib${ffmpeg_lib}.a"
  )
endforeach()
Join(EXCLUDE_LIBS "${EXCLUDE_LIBS}" ",")
list(APPEND ADD_LINKER_FLAGS
  -Wl,--exclude-libs,${EXCLUDE_LIBS}
)
Join(ADD_LINKER_FLAGS "${ADD_LINKER_FLAGS}" " ")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} ${ADD_LINKER_FLAGS}")

# == Libraries ==

# tdjni

add_library(crypto SHARED IMPORTED)
set_target_properties(crypto PROPERTIES IMPORTED_LOCATION
  ${TDLIB_DIR}/openssl/${ANDROID_ABI}/lib/libcryptox.so
)
add_library(ssl SHARED IMPORTED)
set_target_properties(ssl PROPERTIES IMPORTED_LOCATION
  ${TDLIB_DIR}/openssl/${ANDROID_ABI}/lib/libsslx.so
)
if(${CMAKE_VERSION} VERSION_GREATER_EQUAL "3.11.0")
  target_include_directories(crypto INTERFACE
    ${TDLIB_DIR}/openssl/${ANDROID_ABI}/include
  )
  target_include_directories(ssl INTERFACE
    ${TDLIB_DIR}/openssl/${ANDROID_ABI}/include
  )
endif()

add_library(tdjni SHARED IMPORTED)
set_target_properties(tdjni PROPERTIES IMPORTED_LOCATION
  ${TDLIB_DIR}/src/main/libs/${ANDROID_ABI}/libtdjni.so
)
target_link_libraries(tdjni INTERFACE ssl crypto)

# opus
include("${PROJECT_SOURCE_DIR}/BuildOpus.cmake")

# ogg
include("${PROJECT_SOURCE_DIR}/BuildOgg.cmake")

# flac
include("${PROJECT_SOURCE_DIR}/BuildFlac.cmake")

# jni-utils
add_subdirectory(
  "${UTILS_DIR}"
)

# yuv
include("${PROJECT_SOURCE_DIR}/BuildYuv.cmake")

# vpx
add_library(vpx STATIC IMPORTED)
set_target_properties(vpx PROPERTIES IMPORTED_LOCATION "${VPX_LIB_PATH}")
target_include_directories(vpx INTERFACE "${VPX_DIR}/include")

# opusfile
include("${PROJECT_SOURCE_DIR}/BuildOpusfile.cmake")

# rlottie
include("${PROJECT_SOURCE_DIR}/BuildRlottie.cmake")

# lz4
include("${PROJECT_SOURCE_DIR}/BuildLz4.cmake")

# webp
if (${USE_WEBP})
  include("${PROJECT_SOURCE_DIR}/BuildWebp.cmake")
endif()

# ffmpeg
foreach(FFMPEG_LIB IN LISTS FFMPEG_LIBS)
  add_library(${FFMPEG_LIB} STATIC IMPORTED)
  set_target_properties(${FFMPEG_LIB} PROPERTIES IMPORTED_LOCATION "${FFMPEG_DIR}/lib/lib${FFMPEG_LIB}.a")
  target_include_directories(${FFMPEG_LIB} INTERFACE
    "${FFMPEG_DIR}/include"
    "${FFMPEG_SRC_DIR}"
  )
endforeach()

# tgcalls
if (${ENABLE_TGVOIP})
  add_subdirectory(
    "${PROJECT_SOURCE_DIR}/tgvoip"
  )
endif()

# == Target ==

set(NATIVE_LIB "tgxjni")
add_library(${NATIVE_LIB} SHARED
  log.cpp

  "${THIRDPARTY_DIR}/telegram_intro/IntroRenderer.c"

  "${THIRDPARTY_DIR}/emoji_suggestions/emoji_suggestions_data.cpp"
  "${THIRDPARTY_DIR}/emoji_suggestions/emoji_suggestions.cpp"

  jni.c
  voice.c
  emoji.cpp
  utils.cpp
  image.c
  gif.cpp
  views.c

  "${THIRDPARTY_DIR}/androidx-media/${TGX_FLAVOR}/opus_jni.cc"
  "${THIRDPARTY_DIR}/androidx-media/${TGX_FLAVOR}/flac_jni.cc"
  "${THIRDPARTY_DIR}/androidx-media/${TGX_FLAVOR}/flac_parser.cc"
  "${THIRDPARTY_DIR}/androidx-media/${TGX_FLAVOR}/ffmpeg_jni.cc"
  "${THIRDPARTY_DIR}/androidx-media/${TGX_FLAVOR}/vpx_jni.cc"

  bridge.cpp
)
target_include_directories(${NATIVE_LIB} PRIVATE
  "${THIRDPARTY_DIR}/telegram_intro"
  "${THIRDPARTY_DIR}/androidx-media/${TGX_FLAVOR}"

  "${THIRDPARTY_DIR}"
  .
)

target_compile_definitions(${NATIVE_LIB} PUBLIC
  SOCKLEN_T=socklen_t
  LOCALE_NOT_USED

  DISABLE_IMPORTGL
  BSD=1
  AVOID_TABLES
  ANDROID_TILE_BASED_DECODE
  ANDROID_ARMV6_IDCT
  __STDC_CONSTANT_MACROS

  TDLIB_TDAPI_CLASS_PATH="org/drinkless/tdlib/TdApi"
)
target_compile_options(${NATIVE_LIB} PUBLIC
  -Wall -Werror -Wno-deprecated-declarations
  -Wno-macro-redefined -Wno-unused-variable
  -fno-math-errno -fno-strict-aliasing -funroll-loops
  -ffast-math
  -fno-rtti
)
# TODO: remove -Wno-macro-redefined -Wno-unused-variable
# only "${THIRDPARTY_DIR}/androidx-media/ffmpeg_jni.cc" needs them.

# == Linking dependencies ==

target_link_libraries(${NATIVE_LIB}
  crypto
  ssl
  tdjni
  jni-utils
  FLAC
  yuv
  vpx
  ogg
  opusfile
  opus
  rlottie
  lz4
)
if (${USE_WEBP})
  target_link_libraries(${NATIVE_LIB} webpdecoder_static)
else()
  target_compile_definitions(${NATIVE_LIB} PRIVATE NO_WEBP)
endif()
foreach(ffmpeg_lib ${FFMPEG_LIBS})
  target_link_libraries(${NATIVE_LIB} "${ffmpeg_lib}")
endforeach()

target_link_libraries(${NATIVE_LIB}
  jnigraphics
  log
  GLESv2
  EGL
  android
  cpufeatures
)

if (${ANDROID_ABI} STREQUAL "arm64-v8a" OR ${ANDROID_ABI} STREQUAL "x86_64")
  # Enable 16 KB ELF alignment.
  target_link_options(${NATIVE_LIB} PRIVATE
    "-Wl,-z,max-page-size=16384"
  )
endif()

include(AndroidNdkModules)
android_ndk_import_module_cpufeatures()