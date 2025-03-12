/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 16/06/2017
 */

#include <jni_utils.h>
#include "bridge.h"

#include <VoIPController.h>
#include <client/android/tg_voip_jni.h>

#ifndef DISABLE_TGCALLS
#include <modules/utility/include/jvm_android.h>
#include <sdk/android/native_api/video/wrapper.h>
#include <sdk/android/native_api/base/init.h>
#include <rtc_base/ssl_adapter.h>
#include <webrtc/media/base/media_constants.h>

#include <os/android/JNIUtilities.h>

#include <tgcalls/legacy/InstanceImplLegacy.h>
#include <tgcalls/InstanceImpl.h>
#include <tgcalls/v2/InstanceV2Impl.h>
#include <tgcalls/v2_4_0_0/InstanceV2_4_0_0Impl.h>
#include <tgcalls/v2/InstanceV2ReferenceImpl.h>

#include <tgcalls/VideoCaptureInterface.h>
#include <platform/android/AndroidInterface.h>
#include <platform/android/AndroidContext.h>

#include <utility>

#else

#ifdef HAVE_TDLIB_CRYPTO
#include <tdjni/telegram_crypto.h>
#else

extern "C" {
#include <openssl/sha.h>
#include <openssl/aes.h>
#ifndef OPENSSL_IS_BORINGSSL
#include <openssl/modes.h>
#endif
#include <openssl/rand.h>
}

void telegram_aes_ige_encrypt(uint8_t* in, uint8_t* out, size_t length, uint8_t* key, uint8_t* iv){
  AES_KEY akey;
  AES_set_encrypt_key(key, 32*8, &akey);
  AES_ige_encrypt(in, out, length, &akey, iv, AES_ENCRYPT);
}

void telegram_aes_ige_decrypt(uint8_t* in, uint8_t* out, size_t length, uint8_t* key, uint8_t* iv){
  AES_KEY akey;
  AES_set_decrypt_key(key, 32*8, &akey);
  AES_ige_encrypt(in, out, length, &akey, iv, AES_DECRYPT);
}

void telegram_rand_bytes(uint8_t* buffer, size_t len){
  RAND_bytes(buffer, len);
}

void telegram_sha1(uint8_t* msg, size_t len, uint8_t* output){
  SHA1(msg, len, output);
}

void telegram_sha256(uint8_t* msg, size_t len, uint8_t* output){
  SHA256(msg, len, output);
}

void telegram_aes_ctr_encrypt(uint8_t* inout, size_t length, uint8_t* key, uint8_t* iv, uint8_t* ecount, uint32_t* num){
  AES_KEY akey;
  AES_set_encrypt_key(key, 32*8, &akey);
#ifdef OPENSSL_IS_BORINGSSL
  AES_ctr128_encrypt(inout, inout, length, &akey, iv, ecount, num);
#else
  CRYPTO_ctr128_encrypt(inout, inout, length, &akey, iv, ecount, num, (block128_f) AES_encrypt);
#endif
}

void telegram_aes_cbc_encrypt(uint8_t* in, uint8_t* out, size_t length, uint8_t* key, uint8_t* iv){
  AES_KEY akey;
  AES_set_encrypt_key(key, 256, &akey);
  AES_cbc_encrypt(in, out, length, &akey, iv, AES_ENCRYPT);
}

void telegram_aes_cbc_decrypt(uint8_t* in, uint8_t* out, size_t length, uint8_t* key, uint8_t* iv){
  AES_KEY akey;
  AES_set_decrypt_key(key, 256, &akey);
  AES_cbc_encrypt(in, out, length, &akey, iv, AES_DECRYPT);
}
#endif
#endif

namespace tgcalls {
  bool isInitialized = false;

  jclass javaTgCallsController = nullptr;
  jclass javaNetworkStats = nullptr;
  jclass javaCallConfiguration = nullptr;
  jclass javaCallOptions = nullptr;
  jclass javaSocks5Proxy = nullptr;

  bool initialize (JNIEnv *env) {
    if (isInitialized) {
      return true;
    }

    // find Java classes
#define INIT(var_name, path) var_name = jni_class::get(env, path);\
    if ((var_name) == nullptr) {\
      jni::throw_new(env, #var_name" not found by path: " path, jni_class::IllegalStateException(env)); \
      return false;\
    }
    INIT(javaTgCallsController, "org/thunderdog/challegram/voip/TgCallsController")
    INIT(javaNetworkStats, "org/thunderdog/challegram/voip/NetworkStats")
    INIT(javaCallConfiguration, "org/thunderdog/challegram/voip/CallConfiguration")
    INIT(javaCallOptions, "org/thunderdog/challegram/voip/CallOptions")
    INIT(javaSocks5Proxy, "org/thunderdog/challegram/voip/Socks5Proxy")
#undef INIT

#ifndef DISABLE_TGCALLS
    // register tgcalls implementations
#define REGISTER(impl) if (!Register<impl>()) { \
      jni::throw_new(env, #impl" could not be registered", jni_class::IllegalStateException(env)); \
    }
    // "2.4.4"
    REGISTER(InstanceImplLegacy)
    // "2.7.7", "5.0.0"
    REGISTER(InstanceImpl)
    // "7.0.0", "8.0.0", "9.0.0"
    REGISTER(InstanceV2Impl)
    // "10.0.0", "11.0.0"
    REGISTER(InstanceV2ReferenceImpl)
#undef REGISTER
#endif

    if (env->ExceptionCheck()) {
      return false;
    }

    JavaVM* vm;
    env->GetJavaVM(&vm);

    webrtc::InitAndroid(vm);
    if (env->ExceptionCheck()) {
      return false;
    }

    webrtc::JVM::Initialize(vm);
    if (env->ExceptionCheck()) {
      return false;
    }

    rtc::InitializeSSL();
    if (env->ExceptionCheck()) {
      return false;
    }

    isInitialized = true;
    return true;
  }
}

tgcalls::DataSaving toDataSaving (JNIEnv *env, jint jDataSavingOption) {
  // Match with voip/annotation/DataSavingOption.java
  switch (jDataSavingOption) {
    case /*DataSavingOption.NEVER*/ 0:
      return tgcalls::DataSaving::Never;
    case /*DataSavingOption.MOBILE*/ 1:
      return tgcalls::DataSaving::Mobile;
    case /*DataSavingOption.ALWAYS*/ 2:
      return tgcalls::DataSaving::Always;
    default:
      jni::throw_new(env,"Invalid dataSavingOption: " + std::to_string(jDataSavingOption),jni_class::IllegalArgumentException(env));
      break;
  }
  return tgcalls::DataSaving::Never;
}

tgcalls::NetworkType toNetworkType (JNIEnv *env, jint jNetworkType) {
  // Match with voip/annotation/CallNetworkType.java
  switch (jNetworkType) {
    case /*CallNetworkType.UNKNOWN*/ 0:
      return tgcalls::NetworkType::Unknown;
    case /*CallNetworkType.GPRS*/ 1:
      return tgcalls::NetworkType::Gprs;
    case /*CallNetworkType.MOBILE_EDGE*/ 2:
      return tgcalls::NetworkType::Edge;
    case /*CallNetworkType.MOBILE_3G*/ 3:
      return tgcalls::NetworkType::ThirdGeneration;
    case /*CallNetworkType.MOBILE_HSPA*/ 4:
      return tgcalls::NetworkType::Hspa;
    case /*CallNetworkType.MOBILE_LTE*/ 5:
      return tgcalls::NetworkType::Lte;
    case /*CallNetworkType.WIFI*/ 6:
      return tgcalls::NetworkType::WiFi;
    case /*CallNetworkType.ETHERNET*/ 7:
      return tgcalls::NetworkType::Ethernet;
    case /*CallNetworkType.OTHER_HIGH_SPEED*/ 8:
      return tgcalls::NetworkType::OtherHighSpeed;
    case /*CallNetworkType.OTHER_LOW_SPEED*/ 9:
      return tgcalls::NetworkType::OtherLowSpeed;
    case /*CallNetworkType.DIALUP*/ 10:
      return tgcalls::NetworkType::Dialup;
    case /*CallNetworkType.OTHER_MOBILE*/ 11:
      return tgcalls::NetworkType::OtherMobile;
    default:
      jni::throw_new(env, "Unknown type: " + std::to_string(jNetworkType), jni_class::IllegalArgumentException(env));
      break;
  }
  return tgcalls::NetworkType::Unknown;
}

jint toJavaCallState (JNIEnv *env, tgcalls::State state) {
  // Match with voip/annotation/CallState.java
  switch (state) {
    case tgcalls::State::WaitInit:
      return /*CallState.WAIT_INIT*/ 1;
    case tgcalls::State::WaitInitAck:
      return /*CallState.WAIT_INIT_ACK*/ 2;
    case tgcalls::State::Established:
      return /*CallState.ESTABLISHED*/ 3;
    case tgcalls::State::Failed:
      return /*CallState.FAILED*/ 4;
    case tgcalls::State::Reconnecting:
      return /*CallState.RECONNECTING*/ 5;
  }
  jni::throw_new(env, "Unknown state: " + std::to_string((int) state), jni_class::IllegalArgumentException(env));
  return -1;
}

jint toJavaAudioState (JNIEnv *env, tgcalls::AudioState state) {
  // Match with voip/annotation/AudioState.java
  switch (state) {
    case tgcalls::AudioState::Muted:
      return /*AudioState.MUTED*/ 0;
    case tgcalls::AudioState::Active:
      return /*AudioState.ACTIVE*/ 1;
  }
  jni::throw_new(env, "Unknown state: " + std::to_string((int) state), jni_class::IllegalArgumentException(env));
  return -1;
}

jint toJavaVideoState (JNIEnv *env, tgcalls::VideoState state) {
  // Match with voip/annotation/VideoState.java
  switch (state) {
    case tgcalls::VideoState::Inactive:
      return /*VideoState.INACTIVE*/ 0;
    case tgcalls::VideoState::Paused:
      return /*VideoState.PAUSED*/ 1;
    case tgcalls::VideoState::Active:
      return /*VideoState.ACTIVE*/ 2;
  }
  jni::throw_new(env, "Unknown state: " + std::to_string((int) state), jni_class::IllegalArgumentException(env));
  return -1;
}

jint findTdApiConstructor (JNIEnv *env, const std::string& name) {
  jclass clazz = env->FindClass((TDLIB_TDAPI_CLASS_PATH "$" + name).c_str());
  jfieldID constructor = env->GetStaticFieldID(clazz, "CONSTRUCTOR", "I");
  return (int32_t) env->GetStaticIntField(clazz, constructor);
}

jint findTdApiConstructor (JNIEnv *env, jobject obj) {
  jclass clazz = env->GetObjectClass(obj);
  jfieldID constructor = env->GetStaticFieldID(clazz, "CONSTRUCTOR", "I");
  return (int32_t) env->GetStaticIntField(clazz, constructor);
}

std::string hexString (JNIEnv *env, jbyteArray array) {
  if (array == nullptr) {
    return "";
  }
  static const char *hexDigits = "0123456789abcdef";

  jsize size = env->GetArrayLength(array);

  jbyte *arrayBytes = env->GetByteArrayElements(array, nullptr);

  std::string hex;
  hex.reserve(size * 2);
  for (jsize i = 0; i < size; i++) {
    jbyte b = arrayBytes[i];

    uint8_t p1 = (b >> 4) & 0xF;
    uint8_t p2 = b & 0xF;
    hex.push_back(hexDigits[p1]);
    hex.push_back(hexDigits[p2]);
  }

  env->ReleaseByteArrayElements(array, arrayBytes, JNI_ABORT);

  return hex;
}

void readPersistentState (const char *filePath, tgcalls::PersistentState &persistentState) {
  FILE *persistentStateFile = fopen(filePath, "r");
  if (persistentStateFile) {
    fseek(persistentStateFile, 0, SEEK_END);
    auto len = static_cast<size_t>(ftell(persistentStateFile));
    fseek(persistentStateFile, 0, SEEK_SET);
    if (len < 1024 * 512 && len > 0) {
      auto *buffer = static_cast<uint8_t *>(malloc(len));
      fread(buffer, 1, len, persistentStateFile);
      persistentState.value = std::vector<uint8_t>(buffer, buffer + len);
      free(buffer);
    }
    fclose(persistentStateFile);
  }
}

void savePersistentState (const char *filePath, const tgcalls::PersistentState &persistentState) {
  FILE *persistentStateFile = fopen(filePath, "w");
  if (persistentStateFile) {
    fwrite(persistentState.value.data(), 1, persistentState.value.size(), persistentStateFile);
    fclose(persistentStateFile);
  }
}

class JniWrapper {
public:
  jobject thiz;
  jclass clazz;
  bool releaseClazz;
  pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;

  JniWrapper(JNIEnv *env, jobject thiz, jclass clazz) : thiz(env->NewGlobalRef(thiz)), clazz(clazz), releaseClazz(false) { }
  JniWrapper(JNIEnv *env, jobject thiz) : thiz(env->NewGlobalRef(thiz)), clazz((jclass) env->NewGlobalRef(env->GetObjectClass(thiz))), releaseClazz(true) { }

  ~JniWrapper() {
    pthread_mutex_destroy(&mutex);
  }

  void runSafely (std::function<void(JNIEnv *env)> act) {
    // tgcalls 9.0.0 & 11.0.0 invokes signalingDataEmitted after callback from tgcalls->stop(callback).
    // Once it'll be guaranteed by tgcalls that none of the callbacks are invoked after stop(...),
    // there won't be need in mutex here.
    pthread_mutex_lock(&mutex);
    if (thiz != nullptr) {
      tgvoip::jni::DoWithJNI(std::move(act));
    }
    pthread_mutex_unlock(&mutex);
  }

  void releaseReference (JNIEnv *env) {
    pthread_mutex_lock(&mutex);
    if (releaseClazz && clazz != nullptr) {
      env->DeleteGlobalRef(clazz);
      clazz = nullptr;
    }
    if (thiz != nullptr) {
      env->DeleteGlobalRef(thiz);
      thiz = nullptr;
    }
    pthread_mutex_unlock(&mutex);
  }

  jobject getObject (JNIEnv *env, const char *fieldName, const char *sig) {
    jfieldID fieldId = env->GetFieldID(clazz, fieldName, sig);
    return env->GetObjectField(thiz, fieldId);
  }

  void callVoid (JNIEnv *env, const char *methodName) {
    jmethodID methodId = env->GetMethodID(clazz, methodName, "()V");
    env->CallVoidMethod(thiz, methodId);
  }

  void callVoid (JNIEnv *env, const char *methodName, jint arg1) {
    jmethodID methodId = env->GetMethodID(clazz, methodName, "(I)V");
    env->CallVoidMethod(thiz, methodId, arg1);
  }

  void callVoid (JNIEnv *env, const char *methodName, jint arg1, jint arg2) {
    jmethodID methodId = env->GetMethodID(clazz, methodName, "(II)V");
    env->CallVoidMethod(thiz, methodId, arg1, arg2);
  }

  void callVoid (JNIEnv *env, const char *methodName, jfloat arg1) {
    jmethodID methodId = env->GetMethodID(clazz, methodName, "(F)V");
    env->CallVoidMethod(thiz, methodId, arg1);
  }

  void callVoid (JNIEnv *env, const char *methodName, jboolean arg1) {
    jmethodID methodId = env->GetMethodID(clazz, methodName, "(Z)V");
    env->CallVoidMethod(thiz, methodId, arg1);
  }

  void callVoid (JNIEnv *env, const char *methodName, jbyteArray arg1) {
    jmethodID methodId = env->GetMethodID(clazz, methodName, "([B)V");
    env->CallVoidMethod(thiz, methodId, arg1);
  }
};

struct TgCallsContext {
  std::unique_ptr<tgcalls::Instance> tgcalls;
  std::shared_ptr<JniWrapper> javaController;
};

jbyteArray toJavaByteArray (JNIEnv *env, const std::vector<uint8_t> &data) {
  auto size = (jsize) data.size();
  jbyteArray bytesArray = env->NewByteArray(size);
  env->SetByteArrayRegion(bytesArray, 0, size, (jbyte *) data.data());
  return bytesArray;
}

JNI_FUNC(jstring, toHexString, jbyteArray jArray) {
  std::string hex (hexString(env, jArray));
  return jni::to_jstring(env, hex);
}

JNI_OBJECT_FUNC(jlong, voip_TgCallsController, newInstance,
         jstring jVersion,
         jobject jConfiguration,
         jobject jOptions) {

  if (!tgcalls::initialize(env)) {
    return 0;
  }

  std::string version = jni::from_jstring(env, jVersion);
  jni::Object configuration (env, jConfiguration, tgcalls::javaCallConfiguration);
  jni::Object options (env, jOptions, tgcalls::javaCallOptions);

  jni::Object callStateReady = configuration.getObject("state", "L" TDLIB_TDAPI_CLASS_PATH "$CallStateReady;");
  jni::Object callProtocol = callStateReady.getObject("protocol", "L" TDLIB_TDAPI_CLASS_PATH "$CallProtocol;");

  // tgcalls::Config
  auto connectTimeoutMs = (double) configuration.getLong("connectTimeoutMs");
  auto packetTimeoutMs = (double) configuration.getLong("packetTimeoutMs");
  auto dataSaving = toDataSaving(env, configuration.getInt("dataSavingOption"));
  bool allowP2p = callStateReady.getBoolean("allowP2p") == JNI_TRUE;
  bool udpP2p = callProtocol.getBoolean("udpP2p") == JNI_TRUE;
  auto maxApiLayer = (int) callProtocol.getInt("maxLayer");
  std::string logFilePath = jni::from_jstring(env, configuration.getString("logFilePath"));
  std::string statsLogFilePath = jni::from_jstring(env, configuration.getString("statsLogFilePath"));
  std::string persistentStateFilePath = jni::from_jstring(env, configuration.getString("persistentStateFilePath"));
  bool useBuiltInAcousticEchoCancellation = configuration.getBoolean("enableAcousticEchoCanceler") == JNI_TRUE;
  bool useBuiltInNoiseSuppressor = configuration.getBoolean("enableNoiseSuppressor") == JNI_TRUE;
  bool useBuiltInAutomaticGainControl = configuration.getBoolean("enableAutomaticGainControl") == JNI_TRUE;
  bool enableStunMarking = configuration.getBoolean("enableStunMarking") == JNI_TRUE;
  std::string customParameters = jni::from_jstring(env, callStateReady.getString("customParameters"));

  // tgcalls::EncryptionKey
  jbyteArray jEncryptionKey = callStateReady.getByteArray("encryptionKey");
  jsize jEncryptionKeyLength = jEncryptionKey != nullptr ? env->GetArrayLength(jEncryptionKey) : 0;
  if (jEncryptionKeyLength != tgcalls::EncryptionKey::kSize) {
    jni::throw_new(env, "Invalid encryption key size", jni_class::IllegalArgumentException(env));
    return 0;
  }
  auto *jEncryptionKeyData = (uint8_t *) env->GetByteArrayElements(jEncryptionKey, nullptr);
  auto encryptionKey = std::make_shared<std::array<uint8_t, tgcalls::EncryptionKey::kSize>>();
  memcpy(encryptionKey->data(), jEncryptionKeyData, tgcalls::EncryptionKey::kSize);
  env->ReleaseByteArrayElements(jEncryptionKey, (jbyte *) jEncryptionKeyData, JNI_ABORT);

  bool isOutgoingCall = configuration.getBoolean("isOutgoing") == JNI_TRUE;

  // tgcalls::Endpoint

  std::vector<tgcalls::Endpoint> endpoints;
  std::vector<int64_t> phoneConnectionIds;

  bool forceTcp = configuration.getBoolean("forceTcp") == JNI_TRUE;

  jint typeTelegramReflector = findTdApiConstructor(env, "CallServerTypeTelegramReflector");
  jint typeWebrtc = findTdApiConstructor(env, "CallServerTypeWebrtc");

  jobjectArray jServers = callStateReady.getObjectArray("servers", "[L" TDLIB_TDAPI_CLASS_PATH "$CallServer;");
  jsize jServersCount = env->GetArrayLength(jServers);
  for (jsize i = 0; i < jServersCount; i++) {
    jobject jServer = env->GetObjectArrayElement(jServers, i);
    jni::Object server (env, jServer, "L" TDLIB_TDAPI_CLASS_PATH "$CallServer;");
    jni::Object serverType = server.getObject("type", "L" TDLIB_TDAPI_CLASS_PATH "$CallServerType;");
    jint serverTypeConstructor = findTdApiConstructor(env, serverType.getThis());

    auto id = (int64_t) server.getLong("id");
    tgcalls::EndpointHost host = {
      .ipv4 = jni::from_jstring(env, server.getString("ipAddress")),
      .ipv6 = jni::from_jstring(env, server.getString("ipv6Address"))
    };
    tgcalls::EndpointType endpointType;
    if (forceTcp) { // TODO check the isTcp flag?
      endpointType = tgcalls::EndpointType::TcpRelay;
    } else {
      endpointType = tgcalls::EndpointType::UdpRelay;
    }
    auto port = (uint16_t) server.getInt("port");
    tgcalls::Endpoint endpoint = {
      .endpointId = id,
      .host = host,
      .port = port,
      .type = endpointType
    };
    if (serverTypeConstructor == typeTelegramReflector) {
      phoneConnectionIds.push_back(endpoint.endpointId);
      jbyteArray peerTag = serverType.getByteArray("peerTag");
      if (peerTag != nullptr) {
        jsize peerTagLength = env->GetArrayLength(peerTag);
        if (peerTagLength <= 16) {
          jbyte *peerTagBytes = env->GetByteArrayElements(peerTag, nullptr);
          memcpy(endpoint.peerTag, peerTagBytes, peerTagLength);
          env->ReleaseByteArrayElements(peerTag, peerTagBytes, JNI_ABORT);
        }
        env->DeleteLocalRef(peerTag);
      }
    }
    env->DeleteLocalRef(jServer);

    endpoints.push_back(endpoint);
  }

  // tgcalls options

  tgcalls::NetworkType networkType = toNetworkType(env, options.getInt("networkType"));
  bool audioOutputGainControlEnabled = options.getBoolean("audioGainControlEnabled") == JNI_TRUE;
  int echoCancellationStrength = options.getInt("echoCancellationStrength");
  bool muteMicrophone = options.getBoolean("isMicDisabled") == JNI_TRUE;

  // tgcalls::RtcServer

  std::vector<tgcalls::RtcServer> rtcServers;

  if (!phoneConnectionIds.empty()) {
    std::sort(phoneConnectionIds.begin(), phoneConnectionIds.end());
  }

  for (jsize i = 0; i < jServersCount; i++) {
    jobject jServer = env->GetObjectArrayElement(jServers, i);
    jni::Object server (env, jServer, "L" TDLIB_TDAPI_CLASS_PATH "$CallServer;");
    jni::Object serverType = server.getObject("type", "L" TDLIB_TDAPI_CLASS_PATH "$CallServerType;");
    jint serverTypeConstructor = findTdApiConstructor(env, serverType.getThis());

    auto id = (int64_t) server.getLong("id");
    std::string ipAddress (jni::from_jstring(env, server.getString("ipAddress")));
    std::string ipv6Address (jni::from_jstring(env, server.getString("ipv6Address")));
    auto port = (uint16_t) server.getInt("port");

    tgcalls::RtcServer rtcServer {
      .host = ipAddress.empty() ? ipv6Address : ipAddress,
      .port = port
    };
    if (serverTypeConstructor == typeTelegramReflector) {
      jbyteArray peerTag = serverType.getByteArray("peerTag");
      std::string password (hexString(env, peerTag));
      if (peerTag != nullptr) {
        env->DeleteLocalRef(peerTag);
      }

      auto itr = std::find(phoneConnectionIds.begin(), phoneConnectionIds.end(), id);
      size_t reflectorId = itr - phoneConnectionIds.begin() + 1;
      rtcServer.id = reflectorId;
      rtcServer.isTcp = serverType.getBoolean("isTcp") == JNI_TRUE;
      rtcServer.login = "reflector";
      rtcServer.password = password;
      rtcServer.isTurn = true;
    } else if (serverTypeConstructor == typeWebrtc) {
      std::string username (jni::from_jstring(env, serverType.getString("username")));
      std::string password (jni::from_jstring(env, serverType.getString("password")));
      rtcServer.id = id;
      rtcServer.login = username;
      rtcServer.password = password;
      rtcServer.isTurn = serverType.getBoolean("supportsTurn") == JNI_TRUE;
      // TODO? bool supportsStun = serverType.getBoolean("supportsStun") == JNI_TRUE;
    }
    rtcServers.push_back(rtcServer);

    env->DeleteLocalRef(jServer);
  }

  std::shared_ptr<JniWrapper> javaController = std::make_shared<JniWrapper>(env, thiz, tgcalls::javaTgCallsController);

  tgcalls::Descriptor descriptor = {
    .version = version,
    .config = tgcalls::Config {
      .initializationTimeout = connectTimeoutMs,
      .receiveTimeout = packetTimeoutMs,

      .dataSaving = dataSaving,

      .enableP2P = allowP2p && udpP2p,
      .allowTCP = forceTcp,
      .enableStunMarking = enableStunMarking,
      .enableAEC = useBuiltInAcousticEchoCancellation,
      .enableNS = useBuiltInNoiseSuppressor,
      .enableAGC = useBuiltInAutomaticGainControl,

      .enableCallUpgrade = false,
      .enableVolumeControl = true,

      .logPath = {logFilePath},
      .statsLogPath = {statsLogFilePath},

      .maxApiLayer = maxApiLayer,
      .enableHighBitrateVideo = false,
      .preferredVideoCodecs = {/*cricket::kVp9CodecName*/},
      .customParameters = customParameters
    },
    .endpoints = endpoints,
    .rtcServers = rtcServers,
    .initialNetworkType = networkType,
    .encryptionKey = tgcalls::EncryptionKey(
      std::move(encryptionKey),
      isOutgoingCall
    ),
    .videoCapture = nullptr,
    .stateUpdated = [javaController](tgcalls::State state) {
      javaController->runSafely([javaController, state](JNIEnv *env) {
        jint javaState = toJavaCallState(env, state);
        javaController->callVoid(env, "handleStateChange", javaState);
      });
    },
    .signalBarsUpdated = [javaController](int count) {
      javaController->runSafely([javaController, count](JNIEnv *env) {
        javaController->callVoid(env, "handleSignalBarsChange", (jint) count);
      });
    },
    .audioLevelUpdated = [javaController](float audioLevel) {
      javaController->runSafely([javaController, audioLevel](JNIEnv *env) {
        javaController->callVoid(env, "handleAudioLevelChange", (jfloat) audioLevel);
      });
    },
    .remoteMediaStateUpdated = [javaController](tgcalls::AudioState audioState, tgcalls::VideoState videoState) {
      javaController->runSafely([javaController, audioState, videoState](JNIEnv *env) {
        jint jAudioState = toJavaAudioState(env, audioState);
        jint jVideoState = toJavaVideoState(env, videoState);
        javaController->callVoid(env, "handleRemoteMediaStateChange", jAudioState, jVideoState);
      });
    },
    .signalingDataEmitted = [javaController](const std::vector<uint8_t> &data) {
      javaController->runSafely([javaController, data](JNIEnv *env) {
        jbyteArray jBuffer = toJavaByteArray(env, data);
        if (jBuffer != nullptr) {
          javaController->callVoid(env, "handleEmittedSignalingData", jBuffer);
          env->DeleteLocalRef(jBuffer);
        }
      });
    }
  };

  // tgcalls::Proxy
  jobject jProxy = configuration.getRawObject("proxy", "Lorg/thunderdog/challegram/voip/Socks5Proxy;");
  if (jProxy != nullptr) {
    jni::Object proxy (env, jProxy, tgcalls::javaSocks5Proxy);
    descriptor.proxy = std::make_unique<tgcalls::Proxy>();
    descriptor.proxy->host = jni::from_jstring(env, proxy.getString("host"));
    descriptor.proxy->port = (uint16_t) proxy.getInt("port");
    descriptor.proxy->login = jni::from_jstring(env, proxy.getString("username"));
    descriptor.proxy->password = jni::from_jstring(env, proxy.getString("password"));
    env->DeleteLocalRef(jProxy);
  }

  readPersistentState(persistentStateFilePath.c_str(), descriptor.persistentState);

  if (env->ExceptionCheck()) {
    return 0;
  }

  auto *context = new TgCallsContext;
  context->javaController = javaController;
  context->tgcalls = tgcalls::Meta::Create(version, std::move(descriptor));
  context->tgcalls->setNetworkType(networkType);
  context->tgcalls->setAudioOutputGainControlEnabled(audioOutputGainControlEnabled);
  context->tgcalls->setEchoCancellationStrength(echoCancellationStrength);
  context->tgcalls->setMuteMicrophone(muteMicrophone);

  return jni::ptr_to_jlong(context);
}

JNI_OBJECT_FUNC(jlong, voip_TgCallsController, preferredConnectionId, jlong ptr) {
  auto context = jni::jlong_to_ptr<TgCallsContext *>(ptr);
  if (context != nullptr && context->tgcalls != nullptr) {
    return (jlong) context->tgcalls->getPreferredRelayId();
  }
  return 0;
}

JNI_OBJECT_FUNC(jstring, voip_TgCallsController, lastError, jlong ptr) {
  auto context = jni::jlong_to_ptr<TgCallsContext *>(ptr);
  if (context != nullptr && context->tgcalls != nullptr) {
    std::string error = context->tgcalls->getLastError();
    if (!error.empty()) {
      return jni::to_jstring(env, error);
    }
  }
  return nullptr;
}

JNI_OBJECT_FUNC(jstring, voip_TgCallsController, debugLog, jlong ptr) {
  auto context = jni::jlong_to_ptr<TgCallsContext *>(ptr);
  if (context != nullptr && context->tgcalls != nullptr) {
    std::string debugLog = context->tgcalls->getDebugInfo();
    if (!debugLog.empty()) {
      return jni::to_jstring(env, debugLog);
    }
  }
  return nullptr;
}

JNI_OBJECT_FUNC(void, voip_TgCallsController, processIncomingSignalingData, jlong ptr, jbyteArray jBuffer) {
  auto context = jni::jlong_to_ptr<TgCallsContext *>(ptr);
  if (context != nullptr && context->tgcalls != nullptr) {
    auto *buffer = (uint8_t *) env->GetByteArrayElements(jBuffer, nullptr);
    const size_t size = env->GetArrayLength(jBuffer);
    auto array = std::vector<uint8_t>(size);
    memcpy(&array[0], buffer, size);
    context->tgcalls->receiveSignalingData(array);
    env->ReleaseByteArrayElements(jBuffer, (jbyte *) buffer, JNI_ABORT);
  }
}

JNI_OBJECT_FUNC(void, voip_TgCallsController, updateNetworkType, jlong ptr, jint jNetworkType) {
  auto context = jni::jlong_to_ptr<TgCallsContext *>(ptr);
  if (context != nullptr && context->tgcalls != nullptr) {
    tgcalls::NetworkType networkType = toNetworkType(env, jNetworkType);
    context->tgcalls->setNetworkType(networkType);
  }
}

JNI_OBJECT_FUNC(void, voip_TgCallsController, updateMicrophoneDisabled, jlong ptr, jboolean jDisabled) {
  auto context = jni::jlong_to_ptr<TgCallsContext *>(ptr);
  if (context != nullptr && context->tgcalls != nullptr) {
    bool muteMicrophone = jDisabled == JNI_TRUE;
    context->tgcalls->setMuteMicrophone(muteMicrophone);
  }
}

JNI_OBJECT_FUNC(void, voip_TgCallsController, updateEchoCancellationStrength, jlong ptr, jint strength) {
  auto context = jni::jlong_to_ptr<TgCallsContext *>(ptr);
  if (context != nullptr && context->tgcalls != nullptr) {
    context->tgcalls->setEchoCancellationStrength((int) strength);
  }
}

JNI_OBJECT_FUNC(void, voip_TgCallsController, updateAudioOutputGainControlEnabled, jlong ptr, jboolean jEnabled) {
  auto context = jni::jlong_to_ptr<TgCallsContext *>(ptr);
  if (context != nullptr && context->tgcalls != nullptr) {
    bool isEnabled = jEnabled == JNI_TRUE;
    context->tgcalls->setAudioOutputGainControlEnabled(isEnabled);
  }
}

JNI_OBJECT_FUNC(void, voip_TgCallsController, fetchNetworkStats, jlong ptr, jobject jOutStats) {
  auto context = jni::jlong_to_ptr<TgCallsContext *>(ptr);
  if (context != nullptr && context->tgcalls != nullptr) {
    tgcalls::TrafficStats trafficStats = context->tgcalls->getTrafficStats();
    jni::Object outStats (env, jOutStats, tgcalls::javaNetworkStats);
    outStats.setLong("bytesSentWifi", (jlong) trafficStats.bytesSentWifi);
    outStats.setLong("bytesRecvdWifi", (jlong) trafficStats.bytesReceivedWifi);
    outStats.setLong("bytesSentMobile", (jlong) trafficStats.bytesSentMobile);
    outStats.setLong("bytesRecvdMobile", (jlong) trafficStats.bytesReceivedMobile);
  }
}

JNI_OBJECT_FUNC(void, voip_TgCallsController, destroyInstance, jlong ptr) {
  auto context = jni::jlong_to_ptr<TgCallsContext *>(ptr);
  if (context == nullptr) {
    return;
  }
  if (context->tgcalls == nullptr) {
    delete context;
    return;
  }
  context->tgcalls->stop([context](const tgcalls::FinalState& finalState) {
    tgvoip::jni::DoWithJNI([context, finalState](JNIEnv *env) {

      jobject jConfiguration = context->javaController->getObject(env, "configuration", "Lorg/thunderdog/challegram/voip/CallConfiguration;");
      jni::Object configuration (env, jConfiguration, tgcalls::javaCallConfiguration);
      std::string persistentStateFilePath = jni::from_jstring(env, configuration.getString("persistentStateFilePath"));

      savePersistentState(persistentStateFilePath.c_str(), finalState.persistentState);

      jstring jDebugLog = !finalState.debugLog.empty() ? jni::to_jstring(env, finalState.debugLog) : nullptr;

      jmethodID initMethodId = env->GetMethodID(tgcalls::javaNetworkStats, "<init>", "(JJJJ)V");
      jobject jTrafficStats = env->NewObject(tgcalls::javaNetworkStats, initMethodId,
        (jlong) finalState.trafficStats.bytesSentWifi,
        (jlong) finalState.trafficStats.bytesReceivedWifi,
        (jlong) finalState.trafficStats.bytesSentMobile,
        (jlong) finalState.trafficStats.bytesReceivedMobile
      );

      jmethodID methodId = env->GetMethodID(
        tgcalls::javaTgCallsController, "handleStop",
        "(Lorg/thunderdog/challegram/voip/NetworkStats;Ljava/lang/String;)V"
      );
      env->CallVoidMethod(context->javaController->thiz, methodId, jTrafficStats, jDebugLog);

      if (jDebugLog != nullptr) {
        env->DeleteLocalRef(jDebugLog);
      }
      if (jTrafficStats != nullptr) {
        env->DeleteLocalRef(jTrafficStats);
      }

      if (context->javaController != nullptr) {
        context->javaController->releaseReference(env);
      }

      delete context;
    });
  });
}

JNI_FUNC(jobjectArray, getTgCallsVersions) {
#ifndef DISABLE_TGCALLS
  tgcalls::initialize(env);
  std::vector<std::string> versions (tgcalls::Meta::Versions());

  jobjectArray jArray = env->NewObjectArray(
    (jsize) versions.size(),
    jni_class::String(env),
    nullptr
  );

  jsize index = 0;
  for (const auto &version : versions) {
    jstring jVersion = jni::to_jstring(env, version);
    env->SetObjectArrayElement(jArray, index, jVersion);
    env->DeleteLocalRef(jVersion);
    index++;
  }

  return jArray;
#else
  return env->NewObjectArray(0, jni_class::String(env), nullptr);
#endif
}

// JNI initialization

extern "C" {
int voipOnJNILoad(JavaVM *vm, JNIEnv *env) {
#ifdef DISABLE_TGCALLS
  tgvoip::VoIPController::crypto.sha1 = &telegram_sha1;
  tgvoip::VoIPController::crypto.sha256 = &telegram_sha256;
  tgvoip::VoIPController::crypto.rand_bytes = &telegram_rand_bytes;
  tgvoip::VoIPController::crypto.aes_ige_encrypt = &telegram_aes_ige_encrypt;
  tgvoip::VoIPController::crypto.aes_ige_decrypt = &telegram_aes_ige_decrypt;
  tgvoip::VoIPController::crypto.aes_ctr_encrypt = &telegram_aes_ctr_encrypt;
#ifndef HAVE_TDLIB_CRYPTO
  tgvoip::VoIPController::crypto.aes_cbc_decrypt = &telegram_aes_cbc_decrypt;
  tgvoip::VoIPController::crypto.aes_cbc_encrypt = &telegram_aes_cbc_encrypt;
#endif
#endif
  tgvoipRegisterNatives(env);
  return 0;
}

jint JNI_OnLoad (JavaVM *vm, void *reserved) {
  JNIEnv *env = 0;
  srand(time(NULL));

  if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
    return -1;
  }

  voipOnJNILoad(vm, env);

  return JNI_VERSION_1_6;
}
}