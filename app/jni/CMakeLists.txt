cmake_minimum_required(VERSION 3.22.1 FATAL_ERROR)
project(tgx VERSION 1.0.0 LANGUAGES C CXX)
message(STATUS "Building ${PROJECT_NAME} platform: ${ANDROID_PLATFORM} abi: ${ANDROID_ABI} type: ${CMAKE_BUILD_TYPE} flavor: ${TGX_FLAVOR} (${PROJECT_SOURCE_DIR})")

if (NOT TGX_FLAVOR MATCHES "^(legacy|lollipop|marshmallow|latest)$")
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

set(FFMPEG_SRC_DIR "${THIRDPARTY_DIR}/ffmpeg")
set(FFMPEG_LIBS
  swresample
  avformat
  swscale
  avcodec
  avfilter
  avutil
)

set(VPX_DIR "${LIBVPX_DIR}/${ANDROID_ABI}")
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

add_compile_options(
  "$<$<CONFIG:Release,RelWithDebInfo,MinSizeRel>:-ffunction-sections>"
  "$<$<CONFIG:Release,RelWithDebInfo,MinSizeRel>:-fdata-sections>"
  "$<$<CONFIG:Release,RelWithDebInfo,MinSizeRel>:-fvisibility=hidden>"
  "$<$<CONFIG:Release,RelWithDebInfo,MinSizeRel>:-fvisibility-inlines-hidden>"
  "$<$<CONFIG:Release,RelWithDebInfo,MinSizeRel>:-flto=full>"
)

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
target_link_libraries(tdjni INTERFACE crypto ssl)

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
if(EXISTS "${VPX_LIB_PATH}" AND EXISTS "${VPX_DIR}/include")
  add_library(vpx STATIC IMPORTED)
  set_target_properties(vpx PROPERTIES IMPORTED_LOCATION "${VPX_LIB_PATH}")
  target_include_directories(vpx INTERFACE "${VPX_DIR}/include")
else()
  message(WARNING "./gradlew buildLibvpx was not run yet")
  add_library(vpx INTERFACE)
endif()

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
  set(FFMPEG_LIB_PATH "${FFMPEG_DIR}/${ANDROID_ABI}/lib/lib${FFMPEG_LIB}.a")
  set(FFMPEG_INCLUDE_DIR "${FFMPEG_DIR}/${ANDROID_ABI}/include")
  if(EXISTS "${FFMPEG_LIB_PATH}" AND EXISTS "${FFMPEG_INCLUDE_DIR}")
    add_library(${FFMPEG_LIB} STATIC IMPORTED)
    set_target_properties(${FFMPEG_LIB} PROPERTIES IMPORTED_LOCATION "${FFMPEG_LIB_PATH}")
    target_include_directories(${FFMPEG_LIB} INTERFACE
      "${FFMPEG_INCLUDE_DIR}"
      "${FFMPEG_SRC_DIR}"
    )
  else()
    message(WARNING "./gradlew buildFFmpeg was not run yet, missing ${FFMPEG_LIB}")
    add_library(${FFMPEG_LIB} INTERFACE)
  endif()
endforeach()

# tgcalls
if (${ENABLE_TGVOIP})
  add_subdirectory(
    "${PROJECT_SOURCE_DIR}/tgvoip"
  )
endif()

# == Target ==

set(NATIVE_LIB "tgxjni")
set(NATIVE_SOURCES
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
  bridge.cpp
)
if(EXISTS "${ANDROIDX_MEDIA_DIR}")
  list(APPEND NATIVE_SOURCES
    "${ANDROIDX_MEDIA_DIR}/opus_jni.cc"
    "${ANDROIDX_MEDIA_DIR}/flac_jni.cc"
    "${ANDROIDX_MEDIA_DIR}/flac_parser.cc"
    "${ANDROIDX_MEDIA_DIR}/ffmpeg_jni.cc"
    "${ANDROIDX_MEDIA_DIR}/vpx_jni.cc"
  )
else()
  message(WARNING "./gradlew patchJetpackMedia was not run yet")
endif()

add_library(${NATIVE_LIB} SHARED ${NATIVE_SOURCES})
target_include_directories(${NATIVE_LIB} PRIVATE
  "${THIRDPARTY_DIR}/telegram_intro"
  "${ANDROIDX_MEDIA_DIR}"

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
target_compile_options(${NATIVE_LIB} PRIVATE
  -Wall -Werror -Wno-deprecated-declarations
  -Wno-macro-redefined -Wno-unused-variable
  -fno-math-errno -fno-strict-aliasing -funroll-loops
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

target_link_options(${NATIVE_LIB} PRIVATE
  "$<$<CONFIG:Release,RelWithDebInfo,MinSizeRel>:-Wl,--gc-sections,--icf=safe>"
  "$<$<CONFIG:Release,RelWithDebInfo,MinSizeRel>:-Wl,--exclude-libs,ALL>"
  "$<$<CONFIG:Release,RelWithDebInfo,MinSizeRel>:-flto=full>"
)

if (${ANDROID_ABI} STREQUAL "arm64-v8a" OR ${ANDROID_ABI} STREQUAL "x86_64")
  # Enable 16 KB ELF alignment.
  target_link_options(${NATIVE_LIB} PRIVATE
    "-Wl,-z,max-page-size=16384"
  )
endif()

include(AndroidNdkModules)
android_ndk_import_module_cpufeatures()