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

set(SSL_DIR "${THIRDPARTY_DIR}/openssl/android_static/${ANDROID_ABI}")
set(SSL_LIB_PATH "${SSL_DIR}/lib/libssl.a")
set(CRYPTO_LIB_PATH "${SSL_DIR}/lib/libcrypto.a")

set(LINK_TDLIB yes)
set(USE_TDLIB_CRYPTO no)

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

# == Setup ==

if (${ANDROID_ABI} STREQUAL "armeabi-v7a" OR ${ANDROID_ABI} STREQUAL "arm64-v8a")
  enable_language(ASM)
else()
  enable_language(ASM_NASM)
endif()
set(ORIGINAL_CMAKE_ASM_FLAGS "${CMAKE_ASM_FLAGS}")

set(ADD_COMMON_FLAGS
  -w -Werror=return-type
)

if ("${CMAKE_BUILD_TYPE}" STREQUAL "Release")
  set(CMAKE_C_VISIBILITY_PRESET hidden)
  set(CMAKE_CXX_VISIBILITY_PRESET hidden)

  list(APPEND ADD_COMMON_FLAGS
    -O3
    -fvisibility=hidden
    -ffunction-sections -fdata-sections
    -finline-functions -ffast-math
    -fno-rtti
  )
else()
  list(APPEND ADD_COMMON_FLAGS
    -O2
    -fno-omit-frame-pointer
    -g
  )
  set(CMAKE_C_VISIBILITY_PRESET default)
  set(CMAKE_CXX_VISIBILITY_PRESET default)
endif()
set(ADD_C_FLAGS ${ADD_COMMON_FLAGS}
  -D_LARGEFILE_SOURCE=1
)
set(ADD_CXX_FLAGS ${ADD_COMMON_FLAGS} -std=c++17)

foreach(lang C CXX)
  Join(ADD_${lang}_FLAGS "${ADD_${lang}_FLAGS}" " ")
  set(CMAKE_${lang}_FLAGS "${CMAKE_${lang}_FLAGS} ${ADD_${lang}_FLAGS}")
endforeach()

set(CMAKE_SKIP_RPATH ON)
set(CMAKE_BUILD_WITH_INSTALL_RPATH ON)

file(READ "${TGX_ROOT_DIR}/build-id.txt" TGX_BUILD_ID)
string(LENGTH "${TGX_BUILD_ID}" TGX_BUILD_ID_LENGTH)

if(${TGX_BUILD_ID_LENGTH} EQUAL 0)
  message(FATAL_ERROR "Build-Id is empty or doesn't exist, check: ${TGX_ROOT_DIR}/build-id.txt")
endif()


set(ADD_LINKER_FLAGS
  -Wl,--gc-sections,--icf=safe
  -Wl,--build-id=none
)
set(EXCLUDE_LIBS
  libjni-utils.a
  libflac.a
  libtgvoip.a
  libyuv.a
  "${VPX_LIB_PATH}"
  libopus.a
  libogg.a
  libopusfile.a
  librlottie.a
  liblz4.a
  "${SSL_LIB_PATH}"
  "${CRYPTO_LIB_PATH}"
  libusrsctp.a
  libsrtp.a
  libopenh264.a
  libabsl.a
  libwebrtc.a
  libtgcalls.a
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
if (${LINK_TDLIB})
  add_library(tdjni SHARED IMPORTED)
  set_target_properties(tdjni PROPERTIES IMPORTED_LOCATION
    "${TDLIB_DIR}/src/main/libs/${ANDROID_ABI}/libtdjni.so"
  )
  if (${USE_TDLIB_CRYPTO})
    if(${CMAKE_VERSION} VERSION_GREATER_EQUAL "3.11.0")
      target_include_directories(tdjni INTERFACE
        "${TDLIB_DIR}/include"
      )
    endif()
  endif()
endif()

# crypto
add_library(crypto STATIC IMPORTED)
set_target_properties(crypto PROPERTIES IMPORTED_LOCATION "${CRYPTO_LIB_PATH}")

# ssl
add_library(ssl STATIC IMPORTED)
set_target_properties(ssl PROPERTIES IMPORTED_LOCATION "${SSL_LIB_PATH}")
target_include_directories(ssl INTERFACE "${SSL_DIR}/include")

# flac
include("${CMAKE_HOME_DIRECTORY}/BuildFlac.cmake")

# jni-utils
add_subdirectory(
  "${UTILS_DIR}"
)

# yuv
include("${CMAKE_HOME_DIRECTORY}/BuildYuv.cmake")

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

# webrtc
include("${CMAKE_HOME_DIRECTORY}/BuildWebRTC.cmake")

# libtgvoip
include("${CMAKE_HOME_DIRECTORY}/BuildTelegramVoIP.cmake")

# tgcalls
include("${CMAKE_HOME_DIRECTORY}/BuildTgCalls.cmake")

# rnnoise
include("${CMAKE_HOME_DIRECTORY}/BuildRNNoise.cmake")

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

set(NATIVE_LIB "challegram.23")
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
  tgvoip.cpp
  views.c

  "${THIRDPARTY_DIR}/exoplayer/opus_jni.cc"
  "${THIRDPARTY_DIR}/exoplayer/flac_jni.cc"
  "${THIRDPARTY_DIR}/exoplayer/flac_parser.cc"
  "${THIRDPARTY_DIR}/exoplayer/ffmpeg_jni.cc"
  "${THIRDPARTY_DIR}/exoplayer/vpx_jni.cc"

  bridge.cpp
)
target_include_directories(${NATIVE_LIB} PRIVATE
  "${THIRDPARTY_DIR}/telegram_intro"
  "${THIRDPARTY_DIR}/exoplayer"

  "${THIRDPARTY_DIR}"
  .
)
if (${LINK_TDLIB} AND ${USE_TDLIB_CRYPTO})
  if(${CMAKE_VERSION} VERSION_LESS "3.11.0")
    target_include_directories(${NATIVE_LIB} PRIVATE
      "${TDLIB_DIR}/include"
    )
  endif()
endif()

target_compile_definitions(${NATIVE_LIB} PUBLIC
  SOCKLEN_T=socklen_t
  LOCALE_NOT_USED

  DISABLE_IMPORTGL
  BSD=1
  AVOID_TABLES
  ANDROID_TILE_BASED_DECODE
  ANDROID_ARMV6_IDCT
  __STDC_CONSTANT_MACROS
)
target_compile_options(${NATIVE_LIB} PUBLIC
  -Wall -Werror -fno-math-errno -fno-strict-aliasing -ffast-math -funroll-loops
  -error-limit=0
)

# == Linking dependencies ==

if(${LINK_TDLIB})
  target_link_libraries(${NATIVE_LIB} tdjni)
  if (${USE_TDLIB_CRYPTO})
    target_compile_definitions(${NATIVE_LIB} PRIVATE HAVE_TDLIB_CRYPTO)
  endif()
endif()

target_link_libraries(${NATIVE_LIB}
  jni-utils
  flac
  tgvoip
  yuv
  vpx
  ogg
  opusfile
  opus
  rlottie
  lz4
  tgcalls
  -Wl,--whole-archive webrtc_android -Wl,--no-whole-archive
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

include(AndroidNdkModules)
android_ndk_import_module_cpufeatures()