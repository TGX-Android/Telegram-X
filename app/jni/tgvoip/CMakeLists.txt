cmake_minimum_required(VERSION 3.10.2 FATAL_ERROR)
project(tgcalls VERSION 1.0.0 LANGUAGES C CXX)

message(STATUS "Building ${PROJECT_NAME} platform: ${ANDROID_PLATFORM} abi: ${ANDROID_ABI} type: ${CMAKE_BUILD_TYPE} flavor: ${TGX_FLAVOR} (${PROJECT_SOURCE_DIR})")

set(TGCALLS_DEPS_DIR "${PROJECT_SOURCE_DIR}/third_party")
set(STUB_DIR "${PROJECT_SOURCE_DIR}/stub")

# usrsctp
include("${PROJECT_SOURCE_DIR}/BuildUsrSCTP.cmake")
# srtp
include("${PROJECT_SOURCE_DIR}/BuildLibSRTP.cmake")
# openh264
include("${PROJECT_SOURCE_DIR}/BuildOpenH264.cmake")
# absl
include("${PROJECT_SOURCE_DIR}/BuildAbsl.cmake")
# json11
include("${PROJECT_SOURCE_DIR}/BuildJson11.cmake")
# rnnoise
include("${PROJECT_SOURCE_DIR}/BuildRNNoise.cmake")
# webrtc
include("${PROJECT_SOURCE_DIR}/BuildWebRTC.cmake")
# libtgvoip
include("${PROJECT_SOURCE_DIR}/BuildTelegramVoIP.cmake")
# tgcalls
include("${PROJECT_SOURCE_DIR}/BuildTgCalls.cmake")

# webrtc sdk + tgvoip.cpp layer

set(TGCALLS_LIB "tgcallsjni")

# SDK 26+
# "${WEBRTC_DIR}/sdk/android/src/jni/audio_device/aaudio_player.cc"
# "${WEBRTC_DIR}/sdk/android/src/jni/audio_device/aaudio_recorder.cc"
# "${WEBRTC_DIR}/sdk/android/src/jni/audio_device/aaudio_wrapper.cc"

# libaom, dav1d:
# "${WEBRTC_DIR}/sdk/android/src/jni/libaom_av1_encoder.cc"
# "${WEBRTC_DIR}/sdk/android/src/jni/dav1d_codec.cc"

add_library(${TGCALLS_LIB} SHARED
  tgvoip.cpp

  "${WEBRTC_DIR}/sdk/android/native_api/audio_device_module/audio_device_android.cc"
  "${WEBRTC_DIR}/sdk/android/native_api/base/init.cc"
  "${WEBRTC_DIR}/sdk/android/native_api/codecs/wrapper.cc"

  "${WEBRTC_DIR}/sdk/android/native_api/jni/application_context_provider.cc"
  "${WEBRTC_DIR}/sdk/android/native_api/jni/class_loader.cc"
  "${WEBRTC_DIR}/sdk/android/native_api/jni/java_types.cc"
  "${WEBRTC_DIR}/sdk/android/native_api/jni/jvm.cc"

  "${WEBRTC_DIR}/sdk/android/native_api/network_monitor/network_monitor.cc"

  "${WEBRTC_DIR}/sdk/android/native_api/peerconnection/peer_connection_factory.cc"

  "${WEBRTC_DIR}/sdk/android/native_api/stacktrace/stacktrace.cc"

  "${WEBRTC_DIR}/sdk/android/native_api/video/video_source.cc"
  "${WEBRTC_DIR}/sdk/android/native_api/video/wrapper.cc"

  "${WEBRTC_DIR}/sdk/android/src/jni/audio_device/audio_device_module.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/audio_device/audio_record_jni.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/audio_device/audio_track_jni.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/audio_device/java_audio_device_module.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/audio_device/opensles_common.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/audio_device/opensles_player.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/audio_device/opensles_recorder.cc"

  "${WEBRTC_DIR}/sdk/android/src/jni/logging/log_sink.cc"

  "${WEBRTC_DIR}/sdk/android/src/jni/pc/add_ice_candidate_observer.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/audio.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/audio_track.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/call_session_file_rotating_log_sink.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/crypto_options.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/data_channel.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/dtmf_sender.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/ice_candidate.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/logging.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/media_constraints.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/media_source.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/media_stream.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/media_stream_track.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/owned_factory_and_threads.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/peer_connection.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/peer_connection_factory.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/rtc_certificate.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/rtc_stats_collector_callback_wrapper.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/rtp_capabilities.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/rtp_parameters.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/rtp_receiver.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/rtp_sender.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/rtp_transceiver.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/sdp_observer.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/session_description.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/ssl_certificate_verifier_wrapper.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/stats_observer.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/turn_customizer.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/video.cc"

  "${WEBRTC_DIR}/sdk/android/src/jni/android_histogram.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/android_metrics.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/android_network_monitor.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/android_video_track_source.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/builtin_audio_decoder_factory_factory.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/builtin_audio_encoder_factory_factory.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/egl_base_10_impl.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/encoded_image.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/h264_utils.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/java_i420_buffer.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/jni_common.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/jni_generator_helper.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/jni_helpers.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/jvm.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/native_capturer_observer.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/nv12_buffer.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/nv21_buffer.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/scoped_java_ref_counted.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/software_video_decoder_factory.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/software_video_encoder_factory.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/timestamp_aligner.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/video_codec_info.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/video_codec_status.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/video_decoder_factory_wrapper.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/video_decoder_fallback.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/video_decoder_wrapper.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/video_encoder_factory_wrapper.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/video_encoder_fallback.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/video_encoder_wrapper.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/video_frame.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/video_sink.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/video_track.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/vp8_codec.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/vp9_codec.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/wrapped_native_i420_buffer.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/yuv_helper.cc"
)
target_include_directories(${TGCALLS_LIB} PRIVATE
  "${WEBRTC_DIR}/generated"
  "${TGCALLS_DEPS_DIR}"
  "${CMAKE_HOME_DIRECTORY}"
  .
  "${TGCALLS_DEPS_DIR}/abseil-cpp"
  "${YUV_DIR}/include"
  "${TGCALLS_DEPS_DIR}/webrtc_deps"
  "${STUB_DIR}"
)
if (${ANDROID_ABI} STREQUAL "armeabi-v7a" OR ${ANDROID_ABI} STREQUAL "arm64-v8a")
  set_target_properties(${TGCALLS_LIB} PROPERTIES
    ANDROID_ARM_MODE arm
  )
endif()
target_compile_definitions(${TGCALLS_LIB} PUBLIC ${WEBRTC_OPTIONS})
target_compile_definitions(${TGCALLS_LIB} PRIVATE
  TDLIB_TDAPI_CLASS_PATH="org/drinkless/tdlib/TdApi"
)
target_include_directories(${TGCALLS_LIB} PUBLIC
  "${WEBRTC_DIR}"
)

if (${ANDROID_ABI} STREQUAL "armeabi-v7a")
  target_compile_definitions(${TGCALLS_LIB} PUBLIC
    WEBRTC_ARCH_ARM
    WEBRTC_ARCH_ARM_V7
    WEBRTC_HAS_NEON
  )
elseif(${ANDROID_ABI} STREQUAL "arm64-v8a")
  target_compile_definitions(${TGCALLS_LIB} PUBLIC
    WEBRTC_ARCH_ARM64
    WEBRTC_HAS_NEON
  )
elseif(${ANDROID_ABI} STREQUAL "x86")
  target_compile_definitions(${TGCALLS_LIB} PUBLIC
    HAVE_SSE2
  )
elseif(${ANDROID_ABI} STREQUAL "x86_64")
  target_compile_definitions(${TGCALLS_LIB} PUBLIC
    HAVE_SSE2
  )
endif()

target_link_libraries(${TGCALLS_LIB}
  jni-utils
  tgcalls
)

target_link_libraries(${TGCALLS_LIB}
  log
  GLESv2
  EGL
  android
  cpufeatures
)

set(TGCALLS_EXCLUDE_LIBS
  tgcalls/libtgvoip.a
  tgcalls/libusrsctp.a
  tgcalls/libsrtp.a
  tgcalls/libopenh264.a
  tgcalls/libabsl.a
  tgcalls/libjson11.a
  tgcalls/librnnoise.a
  tgcalls/libwebrtc.a
  tgcalls/libtgcalls.a
)
list(APPEND TGCALLS_EXCLUDE_LIBS
  libyuv.a
  libopus.a
  "${VPX_LIB_PATH}"
  "${FFMPEG_DIR}/lib/libavcodec.a"
)
Join(TGCALLS_EXCLUDE_LIBS "${TGCALLS_EXCLUDE_LIBS}" ",")
target_link_options(${TGCALLS_LIB} PUBLIC
  -Wl,--exclude-libs,${TGCALLS_EXCLUDE_LIBS}
)

if (${ANDROID_ABI} STREQUAL "arm64-v8a" OR ${ANDROID_ABI} STREQUAL "x86_64")
  # Enable 16 KB ELF alignment.
  target_link_options(${TGCALLS_LIB} PRIVATE
    "-Wl,-z,max-page-size=16384"
  )
endif()