cmake_minimum_required(VERSION 3.10.2 FATAL_ERROR)

# == Include ==

include("${CMAKE_HOME_DIRECTORY}/cmake/ReadVariables.cmake")
include("${CMAKE_HOME_DIRECTORY}/cmake/Prefix.cmake")
include("${CMAKE_HOME_DIRECTORY}/cmake/Join.cmake")

# == Dirs ==

set(THIRDPARTY_DIR "${CMAKE_HOME_DIRECTORY}/third_party")
set(STUB_DIR "${CMAKE_HOME_DIRECTORY}/stub")
set(TDLIB_DIR "${CMAKE_HOME_DIRECTORY}/../../tdlib")
set(EXO_FLAC_DIR "${CMAKE_HOME_DIRECTORY}/../../thirdparty/ExoPlayer/extensions/flac/src/main/jni")
set(UTILS_DIR "${THIRDPARTY_DIR}/jni-utils")

set(SSL_DIR "${THIRDPARTY_DIR}/boringssl")
set(SSL_LIB_PATH "${SSL_DIR}/build/${ANDROID_ABI}/ssl/libssl.a")
set(CRYPTO_LIB_PATH "${SSL_DIR}/build/${ANDROID_ABI}/crypto/libcrypto.a")

set(YUV_DIR "${THIRDPARTY_DIR}/libyuv")
set(LZ4_DIR "${THIRDPARTY_DIR}/lz4/lib")
set(RLOTTIE_DIR "${THIRDPARTY_DIR}/rlottie")
set(FLAC_DIR "${THIRDPARTY_DIR}/flac")
set(OPUS_DIR "${THIRDPARTY_DIR}/opus")
set(OPUSFILE_DIR "${THIRDPARTY_DIR}/opusfile")
set(OGG_DIR "${THIRDPARTY_DIR}/ogg")
set(WEBP_DIR "${THIRDPARTY_DIR}/webp")

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
  error("Unknown abi: ${ANDROID_ABI}")
endif()
set(FFMPEG_DIR "${THIRDPARTY_DIR}/ffmpeg/build/${FFMPEG_ABI}")
set(LIBVPX_DIR "${THIRDPARTY_DIR}/libvpx/build/${FFMPEG_ABI}")
set(FFMPEG_LIBS
  swresample
  avformat
  swscale
  avcodec
  avfilter
  avutil
)

# == Setup ==

set(CMAKE_C_VISIBILITY_PRESET hidden)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)

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
  )
endif()
set(ADD_C_FLAGS ${ADD_COMMON_FLAGS}
  -std=c11
  -D_LARGEFILE_SOURCE=1
)
set(ADD_CXX_FLAGS ${ADD_COMMON_FLAGS})

foreach(lang C CXX)
  Join(ADD_${lang}_FLAGS "${ADD_${lang}_FLAGS}" " ")
  set(CMAKE_${lang}_FLAGS "${CMAKE_${lang}_FLAGS} ${ADD_${lang}_FLAGS}")
endforeach()

set(ADD_LINKER_FLAGS
  -Wl,--gc-sections,--icf=safe
  -Wl,--build-id=none
)
set(EXCLUDE_LIBS
  libjni-utils.a
  libflac.a
  libtgvoip.a
  libyuv.a
  libopus.a
  libogg.a
  libopusfile.a
  librlottie.a
  liblz4.a
  "${SSL_LIB_PATH}"
  "${CRYPTO_LIB_PATH}"
  libusrsctp.a
  libsrtp.a
)
if (${USE_WEBP})
  list(APPEND EXCLUDE_LIBS
    libwebpdecoder_static.a
  )
endif()
list(APPEND EXCLUDE_LIBS "${LIBVPX_DIR}/lib/libvpx.a")
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

add_library(tdjni SHARED IMPORTED)
set_target_properties(tdjni PROPERTIES IMPORTED_LOCATION
  "${TDLIB_DIR}/src/main/libs/${ANDROID_ABI}/libtdjni.so"
)
if(${CMAKE_VERSION} VERSION_GREATER_EQUAL "3.11.0")
  target_include_directories(tdjni INTERFACE
    "${TDLIB_DIR}/include"
  )
endif()

# crypto

add_library(crypto STATIC IMPORTED)
set_target_properties(crypto PROPERTIES IMPORTED_LOCATION "${CRYPTO_LIB_PATH}")

# ssl

add_library(ssl STATIC IMPORTED)
set_target_properties(ssl PROPERTIES IMPORTED_LOCATION "${SSL_LIB_PATH}")
target_include_directories(ssl INTERFACE "${SSL_DIR}/include")

# flac

ReadVariables("${EXO_FLAC_DIR}/flac_sources.mk")
list(FILTER FLAC_SOURCES INCLUDE REGEX "^flac/.+$")
Transform(FLAC_SOURCES "^flac/" "${FLAC_DIR}/")

add_library(flac STATIC
  ${FLAC_SOURCES}
)
target_include_directories(flac PRIVATE
  "${FLAC_DIR}/src/libFLAC/include"
)
target_include_directories(flac PUBLIC
  "${FLAC_DIR}/include"
)
set_target_properties(flac PROPERTIES
  ANDROID_ARM_MODE arm
)
target_compile_definitions(flac PRIVATE
  PACKAGE_VERSION="1.3.3"
  _REENTRANT
  PIC
  U_COMMON_IMPLEMENTATION
  HAVE_SYS_PARAM_H
  FLAC__NO_MD5
  FLAC__INTEGER_ONLY_LIBRARY
  FLAC__NO_ASM
  FLAC__HAS_OGG=0
)
target_compile_options(flac PRIVATE
  -funroll-loops -finline-functions -fPIC
)

# jni-utils

add_subdirectory(
  "${UTILS_DIR}"
)

# tgvoip

include("${CMAKE_HOME_DIRECTORY}/BuildVoIP.cmake")

# yuv

add_library(yuv STATIC
  "${YUV_DIR}/source/compare.cc"
  "${YUV_DIR}/source/compare_common.cc"
  "${YUV_DIR}/source/compare_gcc.cc"
  "${YUV_DIR}/source/compare_mmi.cc"
  "${YUV_DIR}/source/compare_msa.cc"
  "${YUV_DIR}/source/compare_neon.cc"
  "${YUV_DIR}/source/compare_neon64.cc"
  "${YUV_DIR}/source/compare_win.cc"
  "${YUV_DIR}/source/convert.cc"
  "${YUV_DIR}/source/convert_argb.cc"
  "${YUV_DIR}/source/convert_from.cc"
  "${YUV_DIR}/source/convert_from_argb.cc"
  "${YUV_DIR}/source/convert_to_argb.cc"
  "${YUV_DIR}/source/convert_to_i420.cc"
  "${YUV_DIR}/source/cpu_id.cc"
  "${YUV_DIR}/source/planar_functions.cc"
  "${YUV_DIR}/source/rotate.cc"
  "${YUV_DIR}/source/rotate_any.cc"
  "${YUV_DIR}/source/rotate_argb.cc"
  "${YUV_DIR}/source/rotate_common.cc"
  "${YUV_DIR}/source/rotate_gcc.cc"
  "${YUV_DIR}/source/rotate_mmi.cc"
  "${YUV_DIR}/source/rotate_msa.cc"
  "${YUV_DIR}/source/rotate_neon.cc"
  "${YUV_DIR}/source/rotate_neon64.cc"
  "${YUV_DIR}/source/rotate_win.cc"
  "${YUV_DIR}/source/row_any.cc"
  "${YUV_DIR}/source/row_common.cc"
  "${YUV_DIR}/source/row_gcc.cc"
  "${YUV_DIR}/source/row_mmi.cc"
  "${YUV_DIR}/source/row_msa.cc"
  "${YUV_DIR}/source/row_neon.cc"
  "${YUV_DIR}/source/row_neon64.cc"
  "${YUV_DIR}/source/row_win.cc"
  "${YUV_DIR}/source/scale.cc"
  "${YUV_DIR}/source/scale_any.cc"
  "${YUV_DIR}/source/scale_argb.cc"
  "${YUV_DIR}/source/scale_common.cc"
  "${YUV_DIR}/source/scale_gcc.cc"
  "${YUV_DIR}/source/scale_mmi.cc"
  "${YUV_DIR}/source/scale_msa.cc"
  "${YUV_DIR}/source/scale_neon.cc"
  "${YUV_DIR}/source/scale_neon64.cc"
  "${YUV_DIR}/source/scale_uv.cc"
  "${YUV_DIR}/source/scale_win.cc"
  "${YUV_DIR}/source/video_common.cc"
)
if(${ANDROID_ABI} STREQUAL "armeabi-v7a")
  target_sources(yuv PRIVATE
    "${YUV_DIR}/source/compare_neon.cc"
    "${YUV_DIR}/source/rotate_neon.cc"
    "${YUV_DIR}/source/row_neon.cc"
    "${YUV_DIR}/source/scale_neon.cc"
  )
  target_compile_definitions(yuv PRIVATE
    LIBYUV_NEON
  )
endif()
target_include_directories(yuv PUBLIC
  "${YUV_DIR}/include"
)

target_compile_options(yuv PRIVATE
  -fexceptions
  -finline-functions -ffast-math
  -Os
  -fno-unwind-tables -fno-asynchronous-unwind-tables
  -Wnon-virtual-dtor -Woverloaded-virtual
  -Wno-unused-parameter
)

# vpx

add_library(vpx STATIC IMPORTED)
set_target_properties(vpx PROPERTIES IMPORTED_LOCATION "${LIBVPX_DIR}/lib/libvpx.a")
target_include_directories(vpx INTERFACE "${LIBVPX_DIR}/include")

# opus

ReadVariables("${OPUS_DIR}/celt_sources.mk")
ReadVariables("${OPUS_DIR}/opus_sources.mk")
ReadVariables("${OPUS_DIR}/silk_sources.mk")

Prefix(CELT_SOURCES "${OPUS_DIR}/")
Prefix(OPUS_SOURCES "${OPUS_DIR}/")
Prefix(OPUS_SOURCES_FLOAT "${OPUS_DIR}/")
Prefix(SILK_SOURCES "${OPUS_DIR}/")
Prefix(SILK_SOURCES_FIXED "${OPUS_DIR}/")
Prefix(CELT_SOURCES_ARM "${OPUS_DIR}/")
Prefix(CELT_SOURCES_ARM_ASM "${OPUS_DIR}/")

add_library(opus STATIC
  ${CELT_SOURCES}
  ${OPUS_SOURCES}
  ${OPUS_SOURCES_FLOAT}
  ${SILK_SOURCES}
  ${SILK_SOURCES_FIXED}
)
target_compile_definitions(opus PRIVATE
  OPUS_BUILD
  FIXED_POINT
  USE_ALLOCA
  HAVE_LRINT
  HAVE_LRINTF
)
set_target_properties(opus PROPERTIES
  ANDROID_ARM_MODE arm
)
if (${ANDROID_ABI} STREQUAL "armeabi-v7a")
  Transform(CELT_SOURCES_ARM_ASM "\\.s$" "_gnu.s")
  target_sources(opus PRIVATE
    ${CELT_SOURCES_ARM}
    "${OPUS_DIR}/celt/arm/armopts_gnu.s"
    ${CELT_SOURCES_ARM_ASM}
  )
  target_compile_definitions(opus PRIVATE
    OPUS_ARM_ASM
    OPUS_ARM_INLINE_ASM
    OPUS_ARM_INLINE_EDSP
    OPUS_ARM_INLINE_MEDIA
    OPUS_ARM_INLINE_NEON
    OPUS_ARM_MAY_HAVE_NEON
    OPUS_ARM_MAY_HAVE_MEDIA
    OPUS_ARM_MAY_HAVE_EDSP
  )
endif()
target_include_directories(opus PUBLIC
  "${OPUS_DIR}/include"
)
target_include_directories(opus PRIVATE
  "${OPUS_DIR}/src"
  "${OPUS_DIR}/silk"
  "${OPUS_DIR}/celt"
  "${OPUS_DIR}/silk/fixed"
  "${OPUS_DIR}"
)


# ogg

add_library(ogg STATIC
  "${OGG_DIR}/src/bitwise.c"
  "${OGG_DIR}/src/framing.c"
)
target_include_directories(ogg PUBLIC
  "${OGG_DIR}/include"
)
target_compile_definitions(ogg PUBLIC
  __EMX__
)

# opusfile

add_library(opusfile STATIC
  "${OPUSFILE_DIR}/src/info.c"
  "${OPUSFILE_DIR}/src/internal.c"
  "${OPUSFILE_DIR}/src/opusfile.c"
  "${OPUSFILE_DIR}/src/stream.c"
)
target_include_directories(opusfile PUBLIC
  "${OPUSFILE_DIR}/include"
)
target_link_libraries(opusfile PUBLIC
  ogg opus
)

# rlottie
# TODO: move to "${THIRDPARTY_DIR}/rlottie"
add_library(rlottie STATIC
  "${RLOTTIE_DIR}/src/lottie/lottieanimation.cpp"
  "${RLOTTIE_DIR}/src/lottie/lottieitem.cpp"
  "${RLOTTIE_DIR}/src/lottie/lottiekeypath.cpp"
  "${RLOTTIE_DIR}/src/lottie/lottieloader.cpp"
  "${RLOTTIE_DIR}/src/lottie/lottiemodel.cpp"
  "${RLOTTIE_DIR}/src/lottie/lottieparser.cpp"
  "${RLOTTIE_DIR}/src/lottie/lottieproxymodel.cpp"
  "${RLOTTIE_DIR}/src/vector/freetype/v_ft_math.cpp"
  "${RLOTTIE_DIR}/src/vector/freetype/v_ft_raster.cpp"
  "${RLOTTIE_DIR}/src/vector/freetype/v_ft_stroker.cpp"
  "${RLOTTIE_DIR}/src/vector/pixman/vregion.cpp"
  "${RLOTTIE_DIR}/src/vector/stb/stb_image.cpp"
  "${RLOTTIE_DIR}/src/vector/vbezier.cpp"
  "${RLOTTIE_DIR}/src/vector/vbitmap.cpp"
  "${RLOTTIE_DIR}/src/vector/vbrush.cpp"
  "${RLOTTIE_DIR}/src/vector/vcompositionfunctions.cpp"
  "${RLOTTIE_DIR}/src/vector/vdasher.cpp"
  "${RLOTTIE_DIR}/src/vector/vdebug.cpp"
  "${RLOTTIE_DIR}/src/vector/vdrawable.cpp"
  "${RLOTTIE_DIR}/src/vector/vdrawhelper.cpp"
  "${RLOTTIE_DIR}/src/vector/vdrawhelper_neon.cpp"
  "${RLOTTIE_DIR}/src/vector/velapsedtimer.cpp"
  "${RLOTTIE_DIR}/src/vector/vimageloader.cpp"
  "${RLOTTIE_DIR}/src/vector/vinterpolator.cpp"
  "${RLOTTIE_DIR}/src/vector/vmatrix.cpp"
  "${RLOTTIE_DIR}/src/vector/vpainter.cpp"
  "${RLOTTIE_DIR}/src/vector/vpath.cpp"
  "${RLOTTIE_DIR}/src/vector/vpathmesure.cpp"
  "${RLOTTIE_DIR}/src/vector/vraster.cpp"
  "${RLOTTIE_DIR}/src/vector/vrect.cpp"
  "${RLOTTIE_DIR}/src/vector/vrle.cpp")
target_compile_options(rlottie PRIVATE
  -finline-functions -ffast-math
  -Os
  -fno-unwind-tables -fno-asynchronous-unwind-tables
  -Wnon-virtual-dtor -Woverloaded-virtual
  -Wno-unused-parameter
)
set_target_properties(rlottie PROPERTIES
  ANDROID_ARM_MODE arm)
target_compile_definitions(rlottie PUBLIC
  HAVE_PTHREAD NDEBUG)
target_include_directories(rlottie PUBLIC
  "${RLOTTIE_DIR}/inc"
  "${RLOTTIE_DIR}/src/vector/"
  "${RLOTTIE_DIR}/src/vector/pixman"
  "${RLOTTIE_DIR}/src/vector/freetype"
  "${RLOTTIE_DIR}/src/vector/stb")

if (${ANDROID_ABI} STREQUAL "armeabi-v7a")
  target_compile_options(rlottie PUBLIC
    -fno-integrated-as)
  target_compile_definitions(rlottie PRIVATE
    USE_ARM_NEON
  )
  target_sources(rlottie PRIVATE
    "${RLOTTIE_DIR}/src/vector/pixman/pixman-arm-neon-asm.S")
elseif(${ANDROID_ABI} STREQUAL "arm64-v8a")
  target_compile_options(rlottie PUBLIC
    -fno-integrated-as)
  target_compile_definitions(rlottie PRIVATE
    USE_ARM_NEON
    __ARM64_NEON__
  )
  target_sources(rlottie PRIVATE
    "${RLOTTIE_DIR}/src/vector/pixman/pixman-arma64-neon-asm.S"
  )
endif()

# lz4

add_library(lz4 STATIC
  "${LZ4_DIR}/lz4.c"
  "${LZ4_DIR}/lz4frame.c"
  "${LZ4_DIR}/xxhash.c"
)
set_target_properties(lz4 PROPERTIES
  ANDROID_ARM_MODE arm
)
target_include_directories(lz4 PUBLIC
  "${LZ4_DIR}"
)

# webp

if (${USE_WEBP})
  ReadVariables("${WEBP_DIR}/Android.mk")

  Transform(dec_srcs "\\$\\(NEON\\)$" "c")
  Transform(dsp_dec_srcs "\\$\\(NEON\\)$" "c")
  Transform(utils_dec_srcs "\\$\\(NEON\\)$" "c")

  Prefix(dec_srcs "${WEBP_DIR}/")
  Prefix(dsp_dec_srcs "${WEBP_DIR}/")
  Prefix(utils_dec_srcs "${WEBP_DIR}/")

  add_library(webpdecoder_static STATIC
    ${dec_srcs}
    ${dsp_dec_srcs}
    ${utils_dec_srcs}
  )
  target_include_directories(webpdecoder_static PRIVATE
    "${WEBP_DIR}"
  )
  target_include_directories(webpdecoder_static PUBLIC
    "${WEBP_DIR}/src"
  )
  set_target_properties(webpdecoder_static PROPERTIES
    ANDROID_ARM_MODE arm
  )
  target_compile_definitions(webpdecoder_static PRIVATE
    HAVE_MALLOC_H
    HAVE_PTHREAD
    WEBP_USE_THREAD
  )
  target_link_libraries(webpdecoder_static
    cpufeatures
  )
  if (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    target_compile_definitions(webpdecoder_static PRIVATE
      HAVE_CPU_FEATURES_H
    )
  endif()
  if ("${CMAKE_BUILD_TYPE}" STREQUAL "Release")
    target_compile_options(webpdecoder_static PRIVATE
      -frename-registers -s
    )
  endif()
endif()

# ffmpeg

foreach(FFMPEG_LIB IN LISTS FFMPEG_LIBS)
  add_library(${FFMPEG_LIB} STATIC IMPORTED)
  set_target_properties(${FFMPEG_LIB} PROPERTIES IMPORTED_LOCATION "${FFMPEG_DIR}/lib/lib${FFMPEG_LIB}.a")
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

  "${FFMPEG_DIR}/include"

  "${THIRDPARTY_DIR}"
  .
)
if(${CMAKE_VERSION} VERSION_LESS "3.11.0")
  target_include_directories(${NATIVE_LIB} PRIVATE
    "${TDLIB_DIR}/include"
  )
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
)

# == Linking dependencies ==

target_link_libraries(${NATIVE_LIB}
  tdjni
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
  ssl
  crypto
  usrsctp
  srtp
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
  android
  cpufeatures
)

include(AndroidNdkModules)
android_ndk_import_module_cpufeatures()