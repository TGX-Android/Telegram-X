# tgcalls

set(TGCALLS_DIR "${THIRDPARTY_DIR}/tgcalls")

# Commit Telegram X currently relies on:
# https://github.com/TelegramMessenger/tgcalls/tree/012f7a75ba7e20e1790203d02aedf573e3551d2f

# Source files list:
# https://github.com/TelegramMessenger/Telegram-iOS/blob/cb79afd6e87deb03582a0af7bc6a5b3e1479cf51/submodules/TgVoipWebrtc/BUILD

add_library(tgcalls STATIC
  "${TGCALLS_DIR}/tgcalls/utils/gzip.cpp"
  "${TGCALLS_DIR}/tgcalls/third-party/json11.cpp"

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
  -Wall -finline-functions -ffast-math -fno-strict-aliasing -O3 -frtti -Wno-unknown-pragmas -funroll-loops -fexceptions -fno-math-errno
)

target_link_libraries(tgcalls PRIVATE
  rnnoise
  z
)

target_link_libraries(tgcalls PUBLIC
  webrtc
  tgvoip
)