cmake_minimum_required(VERSION 3.10.2 FATAL_ERROR)

# == Include ==

include("${CMAKE_HOME_DIRECTORY}/cmake/ReadVariables.cmake")
include("${CMAKE_HOME_DIRECTORY}/cmake/Prefix.cmake")
include("${CMAKE_HOME_DIRECTORY}/cmake/Join.cmake")

# == Dirs ==

set(THIRDPARTY_DIR "${CMAKE_HOME_DIRECTORY}/third_party")
set(STUB_DIR "${CMAKE_HOME_DIRECTORY}/stub")
set(TGX_ROOT_DIR "${CMAKE_HOME_DIRECTORY}/../..")
set(TDLIB_DIR "${TGX_ROOT_DIR}/tdlib")
set(UTILS_DIR "${THIRDPARTY_DIR}/jni-utils")

set(ENABLE_TG_CALLS yes)

# Using webp only if building for 32-bit platform
if (${ANDROID_ABI} STREQUAL "armeabi-v7a" OR ${ANDROID_ABI} STREQUAL "x86")
  set(USE_WEBP yes)
else()
  set(USE_WEBP no)
endif()

if (${ANDROID_ABI} STREQUAL "x86_64" OR ${ANDROID_ABI} STREQUAL "arm64-v8a")
  set(FFMPEG_ABI ${ANDROID_ABI})
elseif(${ANDROID_ABI} STREQUAL "x86")
  set(FFMPEG_ABI "i686")
elseif(${ANDROID_ABI} STREQUAL "armeabi-v7a")
  set(FFMPEG_ABI "armv7-a")
else()
  message(FATAL_ERROR "Unknown abi: ${ANDROID_ABI}")
endif()
set(FFMPEG_DIR "${THIRDPARTY_DIR}/ffmpeg/build/${FFMPEG_ABI}")
set(FFMPEG_LIBS
  swresample
  avformat
  swscale
  avcodec
  avfilter
  avutil
)

set(VPX_DIR "${THIRDPARTY_DIR}/libvpx/build/${FFMPEG_ABI}")
set(VPX_LIB_PATH "${VPX_DIR}/lib/libvpx.a")

# == Constants ==
# $TGX_PUBLIC — optimize native bundle size / enable optimizations for end users
# $IS_ARM_FAMILY – armeabi-v7a or arm64-v8a

if ("${CMAKE_BUILD_TYPE}" STREQUAL "Debug")
  set(TGX_PUBLIC no)
else()
  set(TGX_PUBLIC yes)
endif()

if (${ANDROID_ABI} STREQUAL "armeabi-v7a" OR ${ANDROID_ABI} STREQUAL "arm64-v8a")
  set(IS_ARM_FAMILY yes)
else()
  set(IS_ARM_FAMILY no)
endif()

message(STATUS "Building native library... \
        CMAKE_BUILD_TYPE: ${CMAKE_BUILD_TYPE} \
        IS_ARM_FAMILY: ${IS_ARM_FAMILY} \
        TGX_PUBLIC: ${TGX_PUBLIC}")

# == CMake global settings ==

set(CMAKE_SKIP_RPATH ON)
set(CMAKE_BUILD_WITH_INSTALL_RPATH ON)

if (${IS_ARM_FAMILY})
  enable_language(ASM)
else()
  enable_language(ASM_NASM)
endif()
set(ORIGINAL_CMAKE_ASM_FLAGS "${CMAKE_ASM_FLAGS}")

if ($TGX_PUBLIC)
  set(CMAKE_C_VISIBILITY_PRESET hidden)
  set(CMAKE_CXX_VISIBILITY_PRESET hidden)
else()
  set(CMAKE_C_VISIBILITY_PRESET default)
  set(CMAKE_CXX_VISIBILITY_PRESET default)
endif()

set(ADD_COMMON_FLAG
  -w -Werror=return-type
  -ferror-limit=0
)
if ($TGX_PUBLIC)
  list(APPEND ADD_COMMON_FLAG
    -O3
    -finline-functions -ffast-math
    -fno-rtti
  )
else()
  list(APPEND ADD_COMMON_FLAG
    -O2
    -fno-omit-frame-pointer
  )
endif()

Join(ADD_COMMON_FLAGS "${ADD_COMMON_FLAGS}" " ")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${COMMON_FLAGS} -D_LARGEFILE_SOURCE=1")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${COMMON_FLAGS} -std=c++17")

set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} \
  -Wl,--gc-sections,--icf=safe \
  -Wl,--build-id=sha1"
)
set(EXCLUDE_LIBS
  libjni-utils.a
  libflac.a
  libyuv.a
  "${VPX_LIB_PATH}"
  libopus.a
  libogg.a
  libopusfile.a
  librlottie.a
  liblz4.a
)
#if (${ENABLE_TG_CALLS})
#  list(APPEND EXCLUDE_LIBS
#    libtgvoip.a
#    libusrsctp.a
#    libsrtp.a
#    libopenh264.a
#    libabsl.a
#    libjson11.a
#    librnnoise.a
#    libwebrtc.a
#    libtgcalls.a
#  )
#endif()
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
  ${TDLIB_DIR}/openssl/${ANDROID_ABI}/lib/libcrypto.so
)
add_library(ssl SHARED IMPORTED)
set_target_properties(ssl PROPERTIES IMPORTED_LOCATION
  ${TDLIB_DIR}/openssl/${ANDROID_ABI}/lib/libssl.so
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

# flac
include("${CMAKE_HOME_DIRECTORY}/BuildFlac.cmake")

# jni-utils
add_subdirectory(
  "${UTILS_DIR}"
)

# yuv
include("${CMAKE_HOME_DIRECTORY}/BuildYuv.cmake")

# vpx
add_library(vpx STATIC IMPORTED)
set_target_properties(vpx PROPERTIES IMPORTED_LOCATION "${VPX_LIB_PATH}")
target_include_directories(vpx INTERFACE "${VPX_DIR}/include")

# opus
include("${CMAKE_HOME_DIRECTORY}/BuildOpus.cmake")

# ogg
include("${CMAKE_HOME_DIRECTORY}/BuildOgg.cmake")

# opusfile
include("${CMAKE_HOME_DIRECTORY}/BuildOpusfile.cmake")

# rlottie
include("${CMAKE_HOME_DIRECTORY}/BuildRlottie.cmake")

# lz4
include("${CMAKE_HOME_DIRECTORY}/BuildLz4.cmake")

if (${ENABLE_TG_CALLS})
  # usrsctp
  include("${CMAKE_HOME_DIRECTORY}/BuildUsrSCTP.cmake")
  # srtp
  include("${CMAKE_HOME_DIRECTORY}/BuildLibSRTP.cmake")
  # openh264
  include("${CMAKE_HOME_DIRECTORY}/BuildOpenH264.cmake")
  # absl
  include("${CMAKE_HOME_DIRECTORY}/BuildAbsl.cmake")
  # json11
  include("${CMAKE_HOME_DIRECTORY}/BuildJson11.cmake")
  # rnnoise
  include("${CMAKE_HOME_DIRECTORY}/BuildRNNoise.cmake")
  # webrtc
  include("${CMAKE_HOME_DIRECTORY}/BuildWebRTC.cmake")
  # libtgvoip
  include("${CMAKE_HOME_DIRECTORY}/BuildTelegramVoIP.cmake")
  # tgcalls
  include("${CMAKE_HOME_DIRECTORY}/BuildTgCalls.cmake")
endif()

# webp
if (${USE_WEBP})
  include("${CMAKE_HOME_DIRECTORY}/BuildWebp.cmake")
endif()

# ffmpeg
foreach(FFMPEG_LIB IN LISTS FFMPEG_LIBS)
  add_library(${FFMPEG_LIB} STATIC IMPORTED)
  set_target_properties(${FFMPEG_LIB} PROPERTIES IMPORTED_LOCATION "${FFMPEG_DIR}/lib/lib${FFMPEG_LIB}.a")
  target_include_directories(${FFMPEG_LIB} INTERFACE "${FFMPEG_DIR}/include")
endforeach()

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

  "${THIRDPARTY_DIR}/exoplayer/opus_jni.cc"
  "${THIRDPARTY_DIR}/exoplayer/flac_jni.cc"
  "${THIRDPARTY_DIR}/exoplayer/flac_parser.cc"
  "${THIRDPARTY_DIR}/exoplayer/ffmpeg_jni.cc"
  "${THIRDPARTY_DIR}/exoplayer/vpx_jni.cc"

  bridge.cpp
)
#if (${ENABLE_TG_CALLS})
#  target_sources(${NATIVE_LIB} PRIVATE
#    tgvoip.cpp
#  )
#else()
#  target_compile_definitions(${NATIVE_LIB} PRIVATE
#    NO_TGCALLS
#  )
#endif()
target_include_directories(${NATIVE_LIB} PRIVATE
  "${THIRDPARTY_DIR}/telegram_intro"
  "${THIRDPARTY_DIR}/exoplayer"

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
)
# TODO: remove -Wno-macro-redefined -Wno-unused-variable
# only "${THIRDPARTY_DIR}/exoplayer/ffmpeg_jni.cc" needs them.

# == Linking dependencies ==

target_link_libraries(${NATIVE_LIB}
  crypto
  ssl
  tdjni
  jni-utils
  flac
  yuv
  vpx
  ogg
  opusfile
  opus
  rlottie
  lz4
)
#if (${ENABLE_TG_CALLS})
#  # tgcalls
#  target_link_libraries(${NATIVE_LIB}
#    tgcalls
#    -Wl,--whole-archive webrtc_android -Wl,--no-whole-archive
#  )
#endif()
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

include(AndroidNdkModules)
android_ndk_import_module_cpufeatures()