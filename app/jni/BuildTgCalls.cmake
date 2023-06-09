# tgcalls

set(TGCALLS_DIR "${THIRDPARTY_DIR}/tgcalls")

# Commit Telegram X currently relies on:
# https://github.com/TelegramMessenger/tgcalls/tree/012f7a75ba7e20e1790203d02aedf573e3551d2f

# Source files list:
# https://github.com/TelegramMessenger/Telegram-iOS/blob/cb79afd6e87deb03582a0af7bc6a5b3e1479cf51/submodules/TgVoipWebrtc/BUILD

add_library(tgcalls STATIC
  "${TGCALLS_DIR}/tgcalls/utils/gzip.cpp"

  "${TGCALLS_DIR}/tgcalls/FieldTrialsConfig.cpp"

  "${TGCALLS_DIR}/tgcalls/ChannelManager.cpp"
  "${TGCALLS_DIR}/tgcalls/CodecSelectHelper.cpp"
  "${TGCALLS_DIR}/tgcalls/CryptoHelper.cpp"
  "${TGCALLS_DIR}/tgcalls/EncryptedConnection.cpp"
  "${TGCALLS_DIR}/tgcalls/Instance.cpp"
  "${TGCALLS_DIR}/tgcalls/InstanceImpl.cpp"
  "${TGCALLS_DIR}/tgcalls/LogSinkImpl.cpp"
  "${TGCALLS_DIR}/tgcalls/Manager.cpp"
  "${TGCALLS_DIR}/tgcalls/MediaManager.cpp"
  "${TGCALLS_DIR}/tgcalls/Message.cpp"
  "${TGCALLS_DIR}/tgcalls/NetworkManager.cpp"
  "${TGCALLS_DIR}/tgcalls/StaticThreads.cpp"
  "${TGCALLS_DIR}/tgcalls/ThreadLocalObject.cpp"
  "${TGCALLS_DIR}/tgcalls/VideoCaptureInterface.cpp"
  "${TGCALLS_DIR}/tgcalls/VideoCaptureInterfaceImpl.cpp"
  "${TGCALLS_DIR}/tgcalls/AudioDeviceHelper.cpp"
  "${TGCALLS_DIR}/tgcalls/SctpDataChannelProviderInterfaceImpl.cpp"
  "${TGCALLS_DIR}/tgcalls/TurnCustomizerImpl.cpp"

  "${TGCALLS_DIR}/tgcalls/legacy/InstanceImplLegacy.cpp"

  "${TGCALLS_DIR}/tgcalls/FakeVideoTrackSource.cpp"
  "${TGCALLS_DIR}/tgcalls/FakeAudioDeviceModule.cpp"

  "${TGCALLS_DIR}/tgcalls/group/VideoStreamingPart.cpp"
  "${TGCALLS_DIR}/tgcalls/group/StreamingMediaContext.cpp"
  "${TGCALLS_DIR}/tgcalls/group/GroupNetworkManager.cpp"
  "${TGCALLS_DIR}/tgcalls/group/GroupJoinPayloadInternal.cpp"
  "${TGCALLS_DIR}/tgcalls/group/GroupInstanceCustomImpl.cpp"
  "${TGCALLS_DIR}/tgcalls/group/AVIOContextImpl.cpp"
  "${TGCALLS_DIR}/tgcalls/group/AudioStreamingPartPersistentDecoder.cpp"
  "${TGCALLS_DIR}/tgcalls/group/AudioStreamingPartInternal.cpp"
  "${TGCALLS_DIR}/tgcalls/group/AudioStreamingPart.cpp"

  "${TGCALLS_DIR}/tgcalls/v2/InstanceV2Impl.cpp"
  "${TGCALLS_DIR}/tgcalls/v2/NativeNetworkingImpl.cpp"
  "${TGCALLS_DIR}/tgcalls/v2/Signaling.cpp"
  "${TGCALLS_DIR}/tgcalls/v2/SignalingEncryption.cpp"
  "${TGCALLS_DIR}/tgcalls/v2/ContentNegotiation.cpp"
  "${TGCALLS_DIR}/tgcalls/v2/InstanceV2ReferenceImpl.cpp"
  "${TGCALLS_DIR}/tgcalls/v2/ExternalSignalingConnection.cpp"
  "${TGCALLS_DIR}/tgcalls/v2/ReflectorPort.cpp"
  "${TGCALLS_DIR}/tgcalls/v2/ReflectorRelayPortFactory.cpp"
  "${TGCALLS_DIR}/tgcalls/v2/SignalingConnection.cpp"
  "${TGCALLS_DIR}/tgcalls/v2/SignalingSctpConnection.cpp"

  "${TGCALLS_DIR}/tgcalls/v2_4_0_0/InstanceV2_4_0_0Impl.cpp"
  "${TGCALLS_DIR}/tgcalls/v2_4_0_0/Signaling_4_0_0.cpp"

  "${TGCALLS_DIR}/tgcalls/desktop_capturer/DesktopCaptureSourceManager.cpp"
  "${TGCALLS_DIR}/tgcalls/desktop_capturer/DesktopCaptureSourceHelper.cpp"
  "${TGCALLS_DIR}/tgcalls/desktop_capturer/DesktopCaptureSource.cpp"

  "${TGCALLS_DIR}/tgcalls/platform/android/AndroidContext.cpp"
  "${TGCALLS_DIR}/tgcalls/platform/android/AndroidInterface.cpp"
  "${TGCALLS_DIR}/tgcalls/platform/android/VideoCameraCapturer.cpp"
  "${TGCALLS_DIR}/tgcalls/platform/android/VideoCapturerInterfaceImpl.cpp"
)

target_include_directories(tgcalls PRIVATE
  "${CMAKE_HOME_DIRECTORY}"
)
target_include_directories(tgcalls PUBLIC
  "${TGCALLS_DIR}"
  "${TGCALLS_DIR}/tgcalls"
)

target_compile_options(tgcalls PUBLIC
  -Wall -Werror -Wno-deprecated-declarations
  -fno-strict-aliasing
  -frtti
  -funroll-loops
  -fexceptions
  -fno-math-errno
)

target_link_libraries(tgcalls PRIVATE
  rnnoise
  json11
  z
)

target_link_libraries(tgcalls PUBLIC
  tgvoip
)

set(TGCALLS_LIB "tgcallsjni")

add_library(${TGCALLS_LIB} SHARED
  tgvoip.cpp

  "${WEBRTC_DIR}/modules/audio_device/android/audio_manager.cc"

  "${WEBRTC_DIR}/modules/audio_device/android/build_info.cc"

  "${WEBRTC_DIR}/modules/audio_device/android/opensles_common.cc"
  "${WEBRTC_DIR}/modules/audio_device/android/opensles_player.cc"
  "${WEBRTC_DIR}/modules/audio_device/android/opensles_recorder.cc"

  "${WEBRTC_DIR}/sdk/android/native_api/audio_device_module/audio_device_android.cc"
  "${WEBRTC_DIR}/sdk/android/native_api/base/init.cc"
  "${WEBRTC_DIR}/sdk/android/native_api/codecs/wrapper.cc"

  "${WEBRTC_DIR}/sdk/android/native_api/jni/jvm.cc"
  "${WEBRTC_DIR}/sdk/android/native_api/peerconnection/peer_connection_factory.cc"
  "${WEBRTC_DIR}/sdk/android/native_api/stacktrace/stacktrace.cc"
  "${WEBRTC_DIR}/sdk/android/native_api/video/video_source.cc"
  "${WEBRTC_DIR}/sdk/android/native_api/video/wrapper.cc"
  "${WEBRTC_DIR}/sdk/android/native_api/network_monitor/network_monitor.cc"

  "${WEBRTC_DIR}/sdk/android/src/jni/audio_device/opensles_common.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/audio_device/opensles_player.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/audio_device/opensles_recorder.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/jni_generator_helper.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/jni_helpers.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/jvm.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/audio.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/logging.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/owned_factory_and_threads.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/video.cc"

  "${WEBRTC_DIR}/sdk/android/src/jni/pc/add_ice_candidate_observer.cc"
  "${WEBRTC_DIR}/sdk/android/native_api/jni/class_loader.cc"
  "${WEBRTC_DIR}/sdk/android/native_api/jni/java_types.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/egl_base_10_impl.cc"
  "${WEBRTC_DIR}/modules/audio_device/android/audio_record_jni.cc"
  "${WEBRTC_DIR}/modules/audio_device/android/audio_track_jni.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/android_video_track_source.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/android_network_monitor.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/java_i420_buffer.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/jni_common.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/builtin_audio_decoder_factory_factory.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/builtin_audio_encoder_factory_factory.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/android_histogram.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/android_metrics.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/h264_utils.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/encoded_image.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/audio_device/java_audio_device_module.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/crypto_options.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/nv12_buffer.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/nv21_buffer.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/video_codec_status.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/scoped_java_ref_counted.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/timestamp_aligner.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/audio_track.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/dtmf_sender.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/media_source.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/media_stream_track.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/audio_device/audio_device_module.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/audio_device/audio_record_jni.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/audio_device/audio_track_jni.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/rtp_parameters.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/call_session_file_rotating_log_sink.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/session_description.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/logging/log_sink.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/data_channel.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/media_stream.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/native_capturer_observer.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/rtp_receiver.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/rtp_sender.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/rtp_transceiver.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/ssl_certificate_verifier_wrapper.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/turn_customizer.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/ice_candidate.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/video_codec_info.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/video_decoder_factory_wrapper.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/video_decoder_fallback.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/video_frame.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/video_sink.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/sdp_observer.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/stats_observer.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/rtc_stats_collector_callback_wrapper.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/media_constraints.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/vp8_codec.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/vp9_codec.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/wrapped_native_i420_buffer.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/yuv_helper.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/video_track.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/video_encoder_fallback.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/peer_connection.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/peer_connection_factory.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/pc/rtc_certificate.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/video_decoder_wrapper.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/video_encoder_factory_wrapper.cc"
  "${WEBRTC_DIR}/sdk/android/src/jni/video_encoder_wrapper.cc"
)
target_include_directories(${TGCALLS_LIB} PRIVATE
  "${WEBRTC_DIR}/generated"
  "${THIRDPARTY_DIR}"
  .
  "${THIRDPARTY_DIR}/abseil-cpp"
  "${YUV_DIR}/include"
  "${STUB_DIR}"
)
set_target_properties(${TGCALLS_LIB} PROPERTIES
  ANDROID_ARM_MODE arm
)
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
  libtgvoip.a
  libusrsctp.a
  libsrtp.a
  libopenh264.a
  libabsl.a
  libjson11.a
  librnnoise.a
  libwebrtc.a
  libtgcalls.a
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