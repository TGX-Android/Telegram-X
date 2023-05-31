cmake_minimum_required(VERSION 3.10.2 FATAL_ERROR)

# == Include ==

include("${CMAKE_HOME_DIRECTORY}/cmake/ReadVariables.cmake")
include("${CMAKE_HOME_DIRECTORY}/cmake/Prefix.cmake")
include("${CMAKE_HOME_DIRECTORY}/cmake/Join.cmake")

# == Dirs ==

set(THIRDPARTY_DIR "${CMAKE_HOME_DIRECTORY}/thirdparty")
set(TDLIB_DIR "${CMAKE_HOME_DIRECTORY}/../../tdlib")
set(EXO_FLAC_DIR "${CMAKE_HOME_DIRECTORY}/../../thirdparty/ExoPlayer/extensions/flac/src/main/jni")
set(UTILS_DIR "${THIRDPARTY_DIR}/jni-utils")

set(YUV_DIR "${THIRDPARTY_DIR}/libyuv")
set(LZ4_DIR "${THIRDPARTY_DIR}/lz4/lib")
set(RLOTTIE_DIR "${THIRDPARTY_DIR}/rlottie")
set(FLAC_DIR "${THIRDPARTY_DIR}/flac")
set(OPUS_DIR "${THIRDPARTY_DIR}/opus")
set(OPUSFILE_DIR "${THIRDPARTY_DIR}/opusfile")
set(OGG_DIR "${THIRDPARTY_DIR}/ogg")
set(TGVOIP_DIR "${THIRDPARTY_DIR}/libtgvoip")
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
)
if (${USE_WEBP})
  list(APPEND EXCLUDE_LIBS
    libwebpdecoder_static.a
  )
endif()
list(APPEND EXCLUDE_LIBS ${LIBVPX_DIR}/lib/libvpx.a)
foreach(ffmpeg_lib ${FFMPEG_LIBS})
  list(APPEND EXCLUDE_LIBS
    ${FFMPEG_DIR}/lib/lib${ffmpeg_lib}.a
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

ReadVariables(${EXO_FLAC_DIR}/flac_sources.mk)
list(FILTER FLAC_SOURCES INCLUDE REGEX "^flac/.+$")
Transform(FLAC_SOURCES "^flac/" "${FLAC_DIR}/")

add_library(flac STATIC
  ${FLAC_SOURCES}
)
target_include_directories(flac PRIVATE
  ${FLAC_DIR}/src/libFLAC/include
)
target_include_directories(flac PUBLIC
  ${FLAC_DIR}/include
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
  ${UTILS_DIR}
)

# tgvoip

# TODO move to thirdparty/libtgvoip/CMakeLists.txt
add_library(tgvoip STATIC
  ${TGVOIP_DIR}/logging.cpp
  ${TGVOIP_DIR}/VoIPController.cpp
  ${TGVOIP_DIR}/VoIPGroupController.cpp
  ${TGVOIP_DIR}/Buffers.cpp
  ${TGVOIP_DIR}/BlockingQueue.cpp
  ${TGVOIP_DIR}/audio/AudioInput.cpp
  ${TGVOIP_DIR}/os/android/AudioInputOpenSLES.cpp
  ${TGVOIP_DIR}/MediaStreamItf.cpp
  ${TGVOIP_DIR}/audio/AudioOutput.cpp
  ${TGVOIP_DIR}/OpusEncoder.cpp
  ${TGVOIP_DIR}/os/android/AudioOutputOpenSLES.cpp
  ${TGVOIP_DIR}/JitterBuffer.cpp
  ${TGVOIP_DIR}/OpusDecoder.cpp
  ${TGVOIP_DIR}/os/android/OpenSLEngineWrapper.cpp
  ${TGVOIP_DIR}/os/android/AudioInputAndroid.cpp
  ${TGVOIP_DIR}/os/android/AudioOutputAndroid.cpp
  ${TGVOIP_DIR}/EchoCanceller.cpp
  ${TGVOIP_DIR}/CongestionControl.cpp
  ${TGVOIP_DIR}/VoIPServerConfig.cpp
  ${TGVOIP_DIR}/audio/Resampler.cpp
  ${TGVOIP_DIR}/NetworkSocket.cpp
  ${TGVOIP_DIR}/os/posix/NetworkSocketPosix.cpp
  ${TGVOIP_DIR}/PacketReassembler.cpp
  ${TGVOIP_DIR}/MessageThread.cpp
  ${TGVOIP_DIR}/json11.cpp
  ${TGVOIP_DIR}/audio/AudioIO.cpp
  ${TGVOIP_DIR}/video/VideoRenderer.cpp
  ${TGVOIP_DIR}/video/VideoSource.cpp
  ${TGVOIP_DIR}/video/ScreamCongestionController.cpp
  ${TGVOIP_DIR}/os/android/VideoSourceAndroid.cpp
  ${TGVOIP_DIR}/os/android/VideoRendererAndroid.cpp
  ${TGVOIP_DIR}/client/android/tg_voip_jni.cpp
)
if (${ANDROID_ABI} STREQUAL "armeabi-v7a")
  target_compile_definitions(tgvoip PRIVATE
    WEBRTC_HAS_NEON
  )
elseif(${ANDROID_ABI} STREQUAL "arm64-v8a")
  target_compile_definitions(tgvoip PRIVATE
    WEBRTC_HAS_NEON
    __ARM64_NEON__
  )
endif()
set(CC_NEON "cc")
target_sources(tgvoip PRIVATE
  ${TGVOIP_DIR}/webrtc_dsp/system_wrappers/source/field_trial.cc
  ${TGVOIP_DIR}/webrtc_dsp/system_wrappers/source/metrics.cc
  ${TGVOIP_DIR}/webrtc_dsp/system_wrappers/source/cpu_features.cc
  ${TGVOIP_DIR}/webrtc_dsp/absl/strings/internal/memutil.cc
  ${TGVOIP_DIR}/webrtc_dsp/absl/strings/string_view.cc
  ${TGVOIP_DIR}/webrtc_dsp/absl/strings/ascii.cc
  ${TGVOIP_DIR}/webrtc_dsp/absl/types/bad_optional_access.cc
  ${TGVOIP_DIR}/webrtc_dsp/absl/types/optional.cc
  ${TGVOIP_DIR}/webrtc_dsp/absl/base/internal/raw_logging.cc
  ${TGVOIP_DIR}/webrtc_dsp/absl/base/internal/throw_delegate.cc
  ${TGVOIP_DIR}/webrtc_dsp/rtc_base/race_checker.cc
  ${TGVOIP_DIR}/webrtc_dsp/rtc_base/strings/string_builder.cc
  ${TGVOIP_DIR}/webrtc_dsp/rtc_base/memory/aligned_malloc.cc
  ${TGVOIP_DIR}/webrtc_dsp/rtc_base/timeutils.cc
  ${TGVOIP_DIR}/webrtc_dsp/rtc_base/platform_file.cc
  ${TGVOIP_DIR}/webrtc_dsp/rtc_base/string_to_number.cc
  ${TGVOIP_DIR}/webrtc_dsp/rtc_base/thread_checker_impl.cc
  ${TGVOIP_DIR}/webrtc_dsp/rtc_base/stringencode.cc
  ${TGVOIP_DIR}/webrtc_dsp/rtc_base/stringutils.cc
  ${TGVOIP_DIR}/webrtc_dsp/rtc_base/checks.cc
  ${TGVOIP_DIR}/webrtc_dsp/rtc_base/platform_thread.cc
  ${TGVOIP_DIR}/webrtc_dsp/rtc_base/criticalsection.cc
  ${TGVOIP_DIR}/webrtc_dsp/rtc_base/platform_thread_types.cc
  ${TGVOIP_DIR}/webrtc_dsp/rtc_base/event.cc
  ${TGVOIP_DIR}/webrtc_dsp/rtc_base/event_tracer.cc
  ${TGVOIP_DIR}/webrtc_dsp/rtc_base/logging_webrtc.cc
  ${TGVOIP_DIR}/webrtc_dsp/third_party/rnnoise/src/rnn_vad_weights.cc
  ${TGVOIP_DIR}/webrtc_dsp/third_party/rnnoise/src/kiss_fft.cc
  ${TGVOIP_DIR}/webrtc_dsp/api/audio/audio_frame.cc
  ${TGVOIP_DIR}/webrtc_dsp/api/audio/echo_canceller3_config.cc
  ${TGVOIP_DIR}/webrtc_dsp/api/audio/echo_canceller3_factory.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/third_party/fft/fft.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/pitch_estimator.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/lpc_shape_swb16_tables.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/pitch_gain_tables.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/arith_routines_logist.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/filterbanks.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/transform.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/pitch_filter.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/encode_lpc_swb.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/filter_functions.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/decode.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/lattice.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/intialize.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/lpc_tables.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/lpc_gain_swb_tables.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/bandwidth_estimator.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/encode.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/lpc_analysis.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/arith_routines_hist.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/entropy_coding.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/isac_vad.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/arith_routines.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/crc.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/lpc_shape_swb12_tables.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/decode_bwe.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/spectrum_ar_model_tables.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/pitch_lag_tables.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/isac.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/rms_level.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/echo_detector/normalized_covariance_estimator.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/echo_detector/moving_max.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/echo_detector/circular_buffer.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/echo_detector/mean_variance_estimator.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/splitting_filter.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/gain_control_impl.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/ns/nsx_core.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/ns/noise_suppression_x.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/ns/nsx_core_c.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/ns/ns_core.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/ns/noise_suppression.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/audio_buffer.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/typing_detection.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/include/audio_processing_statistics.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/include/audio_generator_factory.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/include/aec_dump.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/include/audio_processing.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/include/config.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc2/interpolated_gain_curve.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc2/agc2_common.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc2/gain_applier.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc2/adaptive_agc.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc2/adaptive_digital_gain_applier.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc2/limiter.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc2/saturation_protector.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc2/rnn_vad/spectral_features_internal.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc2/rnn_vad/rnn.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc2/rnn_vad/pitch_search_internal.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc2/rnn_vad/spectral_features.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc2/rnn_vad/pitch_search.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc2/rnn_vad/features_extraction.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc2/rnn_vad/fft_util.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc2/rnn_vad/lp_residual.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc2/adaptive_mode_level_estimator_agc.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc2/vector_float_frame.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc2/noise_level_estimator.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc2/agc2_testing_common.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc2/fixed_digital_level_estimator.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc2/fixed_gain_controller.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc2/vad_with_level.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc2/limiter_db_gain_curve.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc2/down_sampler.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc2/signal_classifier.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc2/noise_spectrum_estimator.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc2/compute_interpolated_gain_curve.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc2/biquad_filter.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc2/adaptive_mode_level_estimator.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/transient/moving_moments.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/transient/wpd_tree.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/transient/wpd_node.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/transient/transient_suppressor.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/transient/transient_detector.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/low_cut_filter.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/level_estimator_impl.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/three_band_filter_bank.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec/echo_cancellation.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec/aec_resampler.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec/aec_core.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/voice_detection_impl.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/echo_cancellation_impl.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/gain_control_for_experimental_agc.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc/agc.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc/loudness_histogram.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc/agc_manager_direct.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc/legacy/analog_agc.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc/legacy/digital_agc.c
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/agc/utility.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/audio_processing_impl.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/audio_generator/file_audio_generator.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/gain_controller2.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/residual_echo_detector.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/noise_suppression_impl.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aecm/aecm_core.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aecm/aecm_core_c.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aecm/echo_control_mobile.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/render_reverb_model.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/reverb_model_fallback.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/echo_remover_metrics.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/matched_filter_lag_aggregator.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/render_delay_buffer2.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/echo_path_variability.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/frame_blocker.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/subtractor.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/aec3_fft.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/fullband_erle_estimator.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/suppression_filter.${CC_NEON}
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/block_processor.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/subband_erle_estimator.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/render_delay_controller_metrics.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/render_delay_buffer.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/vector_buffer.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/erl_estimator.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/aec_state.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/adaptive_fir_filter.${CC_NEON}
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/render_delay_controller.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/skew_estimator.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/echo_path_delay_estimator.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/block_framer.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/erle_estimator.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/reverb_model.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/cascaded_biquad_filter.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/render_buffer.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/subtractor_output.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/stationarity_estimator.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/render_signal_analyzer.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/subtractor_output_analyzer.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/suppression_gain.${CC_NEON}
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/echo_audibility.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/block_processor_metrics.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/moving_average.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/reverb_model_estimator.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/aec3_common.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/residual_echo_estimator.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/matched_filter.${CC_NEON}
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/reverb_decay_estimator.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/render_delay_controller2.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/suppression_gain_limiter.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/main_filter_update_gain.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/echo_remover.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/downsampled_render_buffer.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/matrix_buffer.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/block_processor2.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/echo_canceller3.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/block_delay_buffer.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/fft_buffer.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/comfort_noise_generator.${CC_NEON}
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/shadow_filter_update_gain.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/filter_analyzer.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/reverb_frequency_response.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec3/decimator.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/echo_control_mobile_impl.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/logging/apm_data_dumper.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/vad/voice_activity_detector.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/vad/standalone_vad.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/vad/pitch_internal.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/vad/vad_circular_buffer.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/vad/vad_audio_proc.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/vad/pole_zero_filter.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/vad/pitch_based_vad.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/vad/gmm.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/utility/ooura_fft.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/utility/delay_estimator_wrapper.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/utility/delay_estimator.cc
  ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/utility/block_mean_calculator.cc
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/window_generator.cc
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/channel_buffer.cc
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/fir_filter_factory.cc
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/wav_header.cc
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/real_fourier_ooura.cc
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/audio_util.cc
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/resampler/push_sinc_resampler.cc
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/resampler/resampler.cc
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/resampler/push_resampler.cc
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/resampler/sinc_resampler.cc
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/resampler/sinusoidal_linear_chirp_source.cc
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/wav_file.cc
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/third_party/spl_sqrt_floor/spl_sqrt_floor.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/third_party/fft4g/fft4g.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/audio_converter.cc
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/real_fourier.cc
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/sparse_fir_filter.cc
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/smoothing_filter.cc
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/fir_filter_c.cc
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/ring_buffer.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/complex_fft.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/filter_ma_fast_q12.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/levinson_durbin.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/dot_product_with_scale.cc
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/auto_corr_to_refl_coef.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/resample_by_2_internal.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/energy.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/sqrt_of_one_minus_x_squared.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/downsample_fast.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/splitting_filter1.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/filter_ar_fast_q12.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/spl_init.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/lpc_to_refl_coef.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/cross_correlation.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/division_operations.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/auto_correlation.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/get_scaling_square.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/resample.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/min_max_operations.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/refl_coef_to_lpc.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/filter_ar.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/vector_scaling_operations.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/resample_fractional.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/real_fft.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/ilbc_specific_functions.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/complex_bit_reverse.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/randomization_functions.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/copy_set_operations.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/resample_by_2.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/get_hanning_window.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/resample_48khz.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/spl_inl.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/spl_sqrt.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/vad/vad_sp.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/vad/vad.cc
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/vad/webrtc_vad.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/vad/vad_filterbank.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/vad/vad_core.c
  ${TGVOIP_DIR}/webrtc_dsp/common_audio/vad/vad_gmm.c
)
if (${ANDROID_ABI} STREQUAL "armeabi-v7a" OR ${ANDROID_ABI} STREQUAL "arm64-v8a")
  target_sources(tgvoip PRIVATE
    ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/ns/nsx_core_neon.c
    ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec/aec_core_neon.cc
    ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aecm/aecm_core_neon.cc
    ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/utility/ooura_fft_neon.cc
    ${TGVOIP_DIR}/webrtc_dsp/common_audio/fir_filter_neon.cc
    ${TGVOIP_DIR}/webrtc_dsp/common_audio/resampler/sinc_resampler_neon.cc
    ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/downsample_fast_neon.c
    ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/min_max_operations_neon.c
    ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/cross_correlation_neon.c
  )
endif()
if (${ANDROID_ABI} STREQUAL "armeabi-v7a")
  target_sources(tgvoip PRIVATE
    ${TGVOIP_DIR}/webrtc_dsp/common_audio/third_party/spl_sqrt_floor/spl_sqrt_floor_arm.S
    ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/complex_bit_reverse_arm.S
    ${TGVOIP_DIR}/webrtc_dsp/common_audio/signal_processing/filter_ar_fast_q12_armv7.S
  )
endif()
if (${ANDROID_ABI} STREQUAL "x86" OR ${ANDROID_ABI} STREQUAL "x86_64")
  target_sources(tgvoip PRIVATE
    ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/aec/aec_core_sse2.cc
    ${TGVOIP_DIR}/webrtc_dsp/modules/audio_processing/utility/ooura_fft_sse2.cc
    ${TGVOIP_DIR}/webrtc_dsp/common_audio/fir_filter_sse.cc
    ${TGVOIP_DIR}/webrtc_dsp/common_audio/resampler/sinc_resampler_sse.cc
  )
endif()
target_compile_definitions(tgvoip PRIVATE
  TGVOIP_USE_CUSTOM_CRYPTO
  TGVOIP_HAS_CONFIG
  TGVOIP_NO_VIDEO
  TGVOIP_NO_GROUP_CALLS
  TGVOIP_PACKAGE_PATH="org/thunderdog/challegram/voip"
  TGVOIP_PEER_TAG_VARIABLE_NAME="peerTag"
  TGVOIP_ENDPOINT_CLASS="org/drinkless/td/libcore/telegram/TdApi$CallServer"

  USE_KISS_FFT
  WEBRTC_APM_DEBUG_DUMP=0
  WEBRTC_POSIX
  WEBRTC_ANDROID
  FIXED_POINT
  WEBRTC_NS_FLOAT
  __STDC_LIMIT_MACROS
)
target_include_directories(tgvoip PRIVATE
  ${OPUS_DIR}/include
  ${TGVOIP_DIR}/webrtc_dsp
  .
)
target_compile_options(tgvoip PRIVATE
  -frtti -fexceptions -finline-functions -ffast-math -fno-strict-aliasing -Wno-unknown-pragmas
)
target_link_libraries(tgvoip PUBLIC log OpenSLES)

# yuv

add_library(yuv STATIC
  ${YUV_DIR}/source/compare.cc
  ${YUV_DIR}/source/compare_common.cc
  ${YUV_DIR}/source/compare_gcc.cc
  ${YUV_DIR}/source/compare_mmi.cc
  ${YUV_DIR}/source/compare_msa.cc
  ${YUV_DIR}/source/compare_neon.cc
  ${YUV_DIR}/source/compare_neon64.cc
  ${YUV_DIR}/source/compare_win.cc
  ${YUV_DIR}/source/convert.cc
  ${YUV_DIR}/source/convert_argb.cc
  ${YUV_DIR}/source/convert_from.cc
  ${YUV_DIR}/source/convert_from_argb.cc
  ${YUV_DIR}/source/convert_to_argb.cc
  ${YUV_DIR}/source/convert_to_i420.cc
  ${YUV_DIR}/source/cpu_id.cc
  ${YUV_DIR}/source/planar_functions.cc
  ${YUV_DIR}/source/rotate.cc
  ${YUV_DIR}/source/rotate_any.cc
  ${YUV_DIR}/source/rotate_argb.cc
  ${YUV_DIR}/source/rotate_common.cc
  ${YUV_DIR}/source/rotate_gcc.cc
  ${YUV_DIR}/source/rotate_mmi.cc
  ${YUV_DIR}/source/rotate_msa.cc
  ${YUV_DIR}/source/rotate_neon.cc
  ${YUV_DIR}/source/rotate_neon64.cc
  ${YUV_DIR}/source/rotate_win.cc
  ${YUV_DIR}/source/row_any.cc
  ${YUV_DIR}/source/row_common.cc
  ${YUV_DIR}/source/row_gcc.cc
  ${YUV_DIR}/source/row_mmi.cc
  ${YUV_DIR}/source/row_msa.cc
  ${YUV_DIR}/source/row_neon.cc
  ${YUV_DIR}/source/row_neon64.cc
  ${YUV_DIR}/source/row_win.cc
  ${YUV_DIR}/source/scale.cc
  ${YUV_DIR}/source/scale_any.cc
  ${YUV_DIR}/source/scale_argb.cc
  ${YUV_DIR}/source/scale_common.cc
  ${YUV_DIR}/source/scale_gcc.cc
  ${YUV_DIR}/source/scale_mmi.cc
  ${YUV_DIR}/source/scale_msa.cc
  ${YUV_DIR}/source/scale_neon.cc
  ${YUV_DIR}/source/scale_neon64.cc
  ${YUV_DIR}/source/scale_uv.cc
  ${YUV_DIR}/source/scale_win.cc
  ${YUV_DIR}/source/video_common.cc
)
if(${ANDROID_ABI} STREQUAL "armeabi-v7a")
  target_sources(yuv PRIVATE
    ${YUV_DIR}/source/compare_neon.cc
    ${YUV_DIR}/source/rotate_neon.cc
    ${YUV_DIR}/source/row_neon.cc
    ${YUV_DIR}/source/scale_neon.cc
  )
  target_compile_definitions(yuv PRIVATE
    LIBYUV_NEON
  )
endif()
target_include_directories(yuv PUBLIC
  ${YUV_DIR}/include
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
set_target_properties(vpx PROPERTIES IMPORTED_LOCATION ${LIBVPX_DIR}/lib/libvpx.a)
target_include_directories(vpx INTERFACE ${LIBVPX_DIR}/include)

# opus

ReadVariables(${OPUS_DIR}/celt_sources.mk)
ReadVariables(${OPUS_DIR}/opus_sources.mk)
ReadVariables(${OPUS_DIR}/silk_sources.mk)

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
    ${OPUS_DIR}/celt/arm/armopts_gnu.s
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
  ${OPUS_DIR}/include
)
target_include_directories(opus PRIVATE
  ${OPUS_DIR}/src
  ${OPUS_DIR}/silk
  ${OPUS_DIR}/celt
  ${OPUS_DIR}/silk/fixed
  ${OPUS_DIR}
)


# ogg

add_library(ogg STATIC
  ${OGG_DIR}/src/bitwise.c
  ${OGG_DIR}/src/framing.c
)
target_include_directories(ogg PUBLIC
  ${OGG_DIR}/include
)
target_compile_definitions(ogg PUBLIC
  __EMX__
)

# opusfile

add_library(opusfile STATIC
  ${OPUSFILE_DIR}/src/info.c
  ${OPUSFILE_DIR}/src/internal.c
  ${OPUSFILE_DIR}/src/opusfile.c
  ${OPUSFILE_DIR}/src/stream.c
)
target_include_directories(opusfile PUBLIC
  ${OPUSFILE_DIR}/include
)
target_link_libraries(opusfile PUBLIC
  ogg opus
)

# rlottie
# TODO: move to thirdparty/rlottie
add_library(rlottie STATIC
  ${RLOTTIE_DIR}/src/lottie/lottieanimation.cpp
  ${RLOTTIE_DIR}/src/lottie/lottieitem.cpp
  ${RLOTTIE_DIR}/src/lottie/lottiekeypath.cpp
  ${RLOTTIE_DIR}/src/lottie/lottieloader.cpp
  ${RLOTTIE_DIR}/src/lottie/lottiemodel.cpp
  ${RLOTTIE_DIR}/src/lottie/lottieparser.cpp
  ${RLOTTIE_DIR}/src/lottie/lottieproxymodel.cpp
  ${RLOTTIE_DIR}/src/vector/freetype/v_ft_math.cpp
  ${RLOTTIE_DIR}/src/vector/freetype/v_ft_raster.cpp
  ${RLOTTIE_DIR}/src/vector/freetype/v_ft_stroker.cpp
  ${RLOTTIE_DIR}/src/vector/pixman/vregion.cpp
  ${RLOTTIE_DIR}/src/vector/stb/stb_image.cpp
  ${RLOTTIE_DIR}/src/vector/vbezier.cpp
  ${RLOTTIE_DIR}/src/vector/vbitmap.cpp
  ${RLOTTIE_DIR}/src/vector/vbrush.cpp
  ${RLOTTIE_DIR}/src/vector/vcompositionfunctions.cpp
  ${RLOTTIE_DIR}/src/vector/vdasher.cpp
  ${RLOTTIE_DIR}/src/vector/vdebug.cpp
  ${RLOTTIE_DIR}/src/vector/vdrawable.cpp
  ${RLOTTIE_DIR}/src/vector/vdrawhelper.cpp
  ${RLOTTIE_DIR}/src/vector/vdrawhelper_neon.cpp
  ${RLOTTIE_DIR}/src/vector/velapsedtimer.cpp
  ${RLOTTIE_DIR}/src/vector/vimageloader.cpp
  ${RLOTTIE_DIR}/src/vector/vinterpolator.cpp
  ${RLOTTIE_DIR}/src/vector/vmatrix.cpp
  ${RLOTTIE_DIR}/src/vector/vpainter.cpp
  ${RLOTTIE_DIR}/src/vector/vpath.cpp
  ${RLOTTIE_DIR}/src/vector/vpathmesure.cpp
  ${RLOTTIE_DIR}/src/vector/vraster.cpp
  ${RLOTTIE_DIR}/src/vector/vrect.cpp
  ${RLOTTIE_DIR}/src/vector/vrle.cpp)
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
  ${RLOTTIE_DIR}/inc
  ${RLOTTIE_DIR}/src/vector/
  ${RLOTTIE_DIR}/src/vector/pixman
  ${RLOTTIE_DIR}/src/vector/freetype
  ${RLOTTIE_DIR}/src/vector/stb)

if (${ANDROID_ABI} STREQUAL "armeabi-v7a")
  target_compile_options(rlottie PUBLIC
    -fno-integrated-as)
  target_compile_definitions(rlottie PRIVATE
    USE_ARM_NEON
  )
  target_sources(rlottie PRIVATE
    ${RLOTTIE_DIR}/src/vector/pixman/pixman-arm-neon-asm.S)
elseif(${ANDROID_ABI} STREQUAL "arm64-v8a")
  target_compile_options(rlottie PUBLIC
    -fno-integrated-as)
  target_compile_definitions(rlottie PRIVATE
    USE_ARM_NEON
    __ARM64_NEON__
  )
  target_sources(rlottie PRIVATE
    ${RLOTTIE_DIR}/src/vector/pixman/pixman-arma64-neon-asm.S
  )
endif()

# lz4

add_library(lz4 STATIC
  ${LZ4_DIR}/lz4.c
  ${LZ4_DIR}/lz4frame.c
  ${LZ4_DIR}/xxhash.c
)
set_target_properties(lz4 PROPERTIES
  ANDROID_ARM_MODE arm
)
target_include_directories(lz4 PUBLIC
  ${LZ4_DIR}
)

# webp

if (${USE_WEBP})
  ReadVariables(${WEBP_DIR}/Android.mk)

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
    ${WEBP_DIR}
  )
  target_include_directories(webpdecoder_static PUBLIC
    ${WEBP_DIR}/src
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
  set_target_properties(${FFMPEG_LIB} PROPERTIES IMPORTED_LOCATION ${FFMPEG_DIR}/lib/lib${FFMPEG_LIB}.a)
endforeach()

# == Target ==

set(NATIVE_LIB "challegram.23")
add_library(${NATIVE_LIB} SHARED
  log.cpp

  thirdparty/telegram_intro/IntroRenderer.c

  thirdparty/emoji_suggestions/emoji_suggestions_data.cpp
  thirdparty/emoji_suggestions/emoji_suggestions.cpp

  jni.c
  voice.c
  emoji.cpp
  utils.cpp
  image.c
  gif.cpp
  tgvoip.cpp
  views.c

  thirdparty/exoplayer/opus_jni.cc
  thirdparty/exoplayer/flac_jni.cc
  thirdparty/exoplayer/flac_parser.cc
  thirdparty/exoplayer/ffmpeg_jni.cc
  thirdparty/exoplayer/vpx_jni.cc

  bridge.cpp
)
target_include_directories(${NATIVE_LIB} PRIVATE
  thirdparty/telegram_intro
  thirdparty/exoplayer

  ${FFMPEG_DIR}/include

  thirdparty
  .
)
if(${CMAKE_VERSION} VERSION_LESS "3.11.0")
  target_include_directories(${NATIVE_LIB} PRIVATE
    ${TDLIB_DIR}/include
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
  crypto
  ssl
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
)
if (${USE_WEBP})
  target_link_libraries(${NATIVE_LIB} webpdecoder_static)
else()
  target_compile_definitions(${NATIVE_LIB} PRIVATE NO_WEBP)
endif()
foreach(ffmpeg_lib ${FFMPEG_LIBS})
  target_link_libraries(${NATIVE_LIB} ${ffmpeg_lib})
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