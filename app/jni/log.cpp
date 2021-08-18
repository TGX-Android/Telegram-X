//
// Created by default on 7/9/17.
//

#include "log.h"

#include <iostream>
#include <unistd.h>
#include <sys/stat.h>
#include <math.h>
#include <jni.h>
#include <cstdlib>
#include <sstream>
#include <string.h>
#include <dirent.h>
#include <errno.h>
#include <jni_utils.h>

#ifdef __ANDROID__
#include <android/log.h>
#endif

#include "bridge.h"

// Log level & tags

enum log_level {
  LogLevelAssert = LOG_LEVEL_ASSERT,
  LogLevelError = LOG_LEVEL_ERROR,
  LogLevelWarning = LOG_LEVEL_WARNING,
  LogLevelInfo = LOG_LEVEL_INFO,
  LogLevelDebug = LOG_LEVEL_DEBUG,
  LogLevelVerbose = LOG_LEVEL_VERBOSE
};

inline const char *get_log_tag(int flag, const char *defValue = "APP") {
  switch (flag) {
    case TAG_ACCOUNTS:
      return "ACCOUNT";
    case TAG_VOIP:
      return "VOIP";
    case TAG_CONTACT:
      return "CONTACT";
    case TAG_ROUND:
      return "ROUND";
    case TAG_MESSAGES_LOADER:
      return "CHAT";
    case TAG_NETWORK_STATE:
      return "NET";
    case TAG_FCM:
      return "FCM";
    case TAG_INTRO:
      return "INTRO";
    case TAG_IMAGE_LOADER:
      return "IMAGE";
    case TAG_TEXT_SPEED:
      return "SPEED/TEXT";
    case TAG_YOUTUBE:
      return "YOUTUBE";
    case TAG_CRASH:
      return "CRASH";
    case TAG_GIF_LOADER:
      return "GIF";
    case TAG_PAINT:
      return "PAINT";
    case TAG_CAMERA:
      return "CAMERA";
    case TAG_VOICE:
      return "VOICE";
    case TAG_EMOJI:
      return "EMOJI";
    case TAG_LUX:
      return "LUX";
    case TAG_VIDEO:
      return "VIDEO";
    case TAG_COMPRESS:
      return "COMPRESS";
    case TAG_PLAYER:
      return "PLAYER";
    case TAG_NDK:
      return "NDK";

    case TAG_TDLIB_FILES:
      return "TDLIB/FILES";
    case TAG_TDLIB_OPTIONS:
      return "TDLIB/OPTIONS";
    default:
      return defValue;
  }
}

inline const char *get_log_tag_desc(int flag, const char *defValue = "") {
  switch (flag) {
    case TAG_VOIP:
      return "Calls";
    case TAG_ACCOUNTS:
      return "Accounts management (battery optimizations)";
    case TAG_MESSAGES_LOADER:
      return "Messages Loader";
    case TAG_CONTACT:
      return "Contacts synchronization";
    case TAG_NETWORK_STATE:
      return "Network State";
    case TAG_PAINT:
      return "In-app paint tool";
    case TAG_FCM:
      return "Message Notifications";
    case TAG_INTRO:
      return "Intro Screen";
    case TAG_IMAGE_LOADER:
      return "Image Loader";
    case TAG_TEXT_SPEED:
      return "Performance measure: text";
    case TAG_YOUTUBE:
      return "YouTube player";
    case TAG_CRASH:
      return "Crash logs";
    case TAG_GIF_LOADER:
      return "GIF Loader";
    case TAG_CAMERA:
      return "In-app camera";
    case TAG_ROUND:
      return "Video message record";
    case TAG_VOICE:
      return "Voice messages play/record";
    case TAG_EMOJI:
      return "Emoji suggestions";
    case TAG_LUX:
      return "Trace current lux value";
    case TAG_VIDEO:
      return "Circular video messages";
    case TAG_COMPRESS:
      return "Video compression";
    case TAG_PLAYER:
      return "Music & voice player";
    case TAG_NDK:
      return "Native Code";

    case TAG_TDLIB_FILES:
      return "TDLib: updateFile";
    case TAG_TDLIB_OPTIONS:
      return "TDLib: updateOption";
    default:
      return defValue;
  }
}

// Utils

off_t file_size(const char *filename) {
  struct stat st;

  if (stat(filename, &st) == 0)
    return st.st_size;

  return -1;
}

bool file_exists(const char *filename) {
  struct stat st;
  return stat(filename, &st) == 0;
}

bool file_isdir(const char *filename) {
  struct stat st;
  if (stat(filename, &st) == 0) {
    return (st.st_mode & S_IFDIR) != 0;
  }
  return 0;
}

// Inline utils

inline const char *level_tag (log_level level) {
  switch (level) {
    case LogLevelDebug:
      return "DEBUG";
    case LogLevelVerbose:
      return "VERBOSE";
    case LogLevelInfo:
      return "INFO";
    case LogLevelWarning:
      return "WARN";
    case LogLevelError:
      return "ERROR";
    case LogLevelAssert:
      return "ASSERT";
  }
  return "UNKNOWN";
}
#ifdef __ANDROID__
inline int get_android_priority (log_level level) {
  switch (level) {
    case LogLevelAssert:
      return ANDROID_LOG_FATAL;
    case LogLevelError:
      return ANDROID_LOG_ERROR;
    case LogLevelWarning:
      return ANDROID_LOG_WARN;
    case LogLevelInfo:
      return ANDROID_LOG_INFO;
    case LogLevelDebug:
      return ANDROID_LOG_DEBUG;
    case LogLevelVerbose:
    default:
      return ANDROID_LOG_VERBOSE;
  }
}
#endif

// Implementation

#define LOG_SIZE_THRESHOLD (1024 * 512 * 3 /* 1.5MB */)
#define LOG_CHECKPOINT_SIZE 32

static pthread_mutex_t file_mutex = PTHREAD_MUTEX_INITIALIZER;

static std::string log_dir;

static std::string os_arch;
static std::string app_version_signature;
static int app_version_code = 0, sdk_version = 0;
static std::string sdk_version_name;
static std::string device_model, device_brand, device_display, device_product, device_manufacturer;
static std::string device_fingerprint;
static int screen_width = 0, screen_height = 0;
static float screen_density = 0.0f;

static FILE *file = NULL;
static std::string current_log_path;
static uint64_t capture_timestamp = 0;
static bool log_empty = true;
static log_level current_log_level = LogLevelAssert;
static int64_t current_log_tags = 0;

void log_reset () {
  file = NULL;
  log_empty = true;
  capture_timestamp = 0;
  current_log_path.clear();
}

void fatal(const char *fmt, ...) {
#ifdef __ANDROID__
  va_list args;
  va_start(args, fmt);
  __android_log_vprint(ANDROID_LOG_FATAL, LOG_TAG, fmt, args);
  va_end(args);
#else
  va_list args;
  va_start(args, fmt);
  vfprintf(stderr, fmt, args);
  va_end(args);
  exit(1);
#endif
}

inline void log_checkpoint (char *buffer) {
  time_t checkpoint = time(NULL);
  struct tm tm = *localtime(&checkpoint);
  strftime(buffer, LOG_CHECKPOINT_SIZE, "%m/%d/%Y %T", &tm);
}

void log_close() {
  pthread_mutex_lock(&file_mutex);
  if (file != NULL) {
    char deathpoint[LOG_CHECKPOINT_SIZE];
    log_checkpoint(deathpoint);
    if (!log_empty) {
      fprintf(file, "====== DEATHPOINT %s ======\n\n", deathpoint);
    }

    fflush(file);
    fclose(file);

    if (log_empty) {
      unlink(current_log_path.c_str());
    }

    log_reset();
  }
  pthread_mutex_unlock(&file_mutex);
}

#define APP_INFO_FORMAT "App: %s\nSDK: %d (%s), NDK: %d (%s)\nManufacturer: %s\nModel: %s\nBrand: %s\nDisplay: %s\nProduct: %s\nFingerprint: %s\nScreen: %dx%d (%f)\n", app_version_signature.c_str(), sdk_version, sdk_version_name.c_str(), __ANDROID_API__, os_arch.c_str(), device_manufacturer.c_str(), device_model.c_str(), device_brand.c_str(), device_display.c_str(), device_product.c_str(), device_fingerprint.c_str(), screen_width, screen_height, screen_density

inline void fprint_device_info (FILE *file, bool need_short) {
  if (need_short) {
    fprintf(file, "App: %s\nSDK: %d (%s), NDK: %d (%s)\nDevice: %s %s\nFingerprint: %s\nScreen: %dx%d (%f)\n", app_version_signature.c_str(), sdk_version, sdk_version_name.c_str(), __ANDROID_API__, os_arch.c_str(), device_manufacturer.c_str(), device_model.c_str(), device_fingerprint.c_str(), screen_width, screen_height, screen_density);
  } else {
    fprintf(file, APP_INFO_FORMAT);
  }
}

inline std::string sprintf_device_info () {
  int count = snprintf(NULL, 0, APP_INFO_FORMAT);
  char buffer[count + 1];
  buffer[count + 1] = '\0';
  snprintf(buffer, count, APP_INFO_FORMAT);
  return std::string(buffer);
}

inline std::string get_log_path (int index, int app_version = 0) {
  if (app_version == 0) {
    app_version = app_version_code;
  }
  std::string name(log_dir);
  name.push_back('/');
  name.append(std::to_string(app_version));
  name.push_back('.');
  name.append(std::to_string(index));
  name.append(".log");
  return name;
}

inline bool starts_with(const char *str, const char *prefix) {
  size_t lenpre = strlen(prefix);
  size_t lenstr = strlen(str);
  return lenstr >= lenpre && strncmp(prefix, str, lenpre) == 0;
}

int parse_version (const char *str) {
  std::string version;
  if (starts_with(str, "call")) {
    return 0;
  }
  if (starts_with(str, "crash.")) {
    str += 6;
  }
  if (starts_with(str, "beta.")) {
    str += 5;
  }
  char c;
  while ((c = *str++) && isdigit(c)) {
    version.push_back(c);
  }
  return version.empty() ? 0 : atoi(version.c_str());
}

void log_cleanup () {
  if (log_dir.empty()) {
    return;
  }
  size_t freed = 0;
  DIR *dir = opendir(log_dir.c_str());
  if (dir != NULL) {
    struct dirent *ent;
    while ((ent = readdir(dir)) != NULL) {
      if (!strcmp(ent->d_name, ".") || !strcmp(ent->d_name, "..")) {
        continue;
      }

      int file_app_version = parse_version(ent->d_name);
      if (file_app_version != 0 && file_app_version < app_version_code) {
        std::string file_path(log_dir);
        file_path.push_back('/');
        file_path.append(ent->d_name);
        struct stat st;
        if (!stat(file_path.c_str(), &st) && !S_ISDIR(st.st_mode)) {
          unlink(file_path.c_str());
          freed += st.st_size;
        }
      }
    }
    closedir(dir);
  }

  if (freed > 0) {
    // TODO log message?
  }
}

void log_file(log_level level, const char *msg, const char *tag, bool stub) {
  char checkpoint[LOG_CHECKPOINT_SIZE];
  log_checkpoint(checkpoint);

  if (file == NULL) {
    if (log_dir.empty()) {
      // fatal("log_file: log directory is not specified. Aborting");
      return;
    }
    if (!file_isdir(log_dir.c_str()) && mkdir(log_dir.c_str(), S_IRWXU) != 0) {
      fatal("log_file: cannot create output directory (%d): %s\n", errno, log_dir.c_str());
      return;
    }
    log_cleanup();
    bool reused = false;
    int num = -1;
    if (capture_timestamp == 0) {
      while (true) {
        std::string name = get_log_path(num + 1);
        if (access(name.c_str(), F_OK) != -1) {
          if (num == -1 && file_size(name.c_str()) <= LOG_SIZE_THRESHOLD) {
            reused = true;
            break;
          }
          num++;
        } else {
          break;
        }
      }
      if (num != -1) {
        for (int i = num; i >= 0; i--) {
          std::string from_name = get_log_path(i);
          std::string to_name = get_log_path(i + 1);
          if (rename(from_name.c_str(), to_name.c_str()) != 0) {
            fatal("log_file: cannot move %s -> %s (%d)\n", from_name.c_str(), to_name.c_str(),
                  errno);
            return;
          }
        }
      }
    }

    std::string log_path;
    if (capture_timestamp != 0) {
      log_path.append(log_dir);
      log_path.append("/capture.");
      log_path.append(std::to_string(app_version_code));
      log_path.push_back('.');
      log_path.append(std::to_string(capture_timestamp));
      log_path.append(".log");
    } else {
      log_path = get_log_path(0);
    }
    file = fopen(log_path.c_str(), "a");
    if (file == NULL) {
      fatal("log_file: cannot open %s (%d)\n", log_path.c_str(), errno);
      return;
    }
    current_log_path.clear();
    current_log_path.append(log_path);
    std::atexit(log_close);
    if (capture_timestamp != 0) {
      fprintf(file, "====== CAPTURE %s ======\n", checkpoint);
    } else {
      fprintf(file, "====== CHECKPOINT %s ======\n", checkpoint);
    }
    log_empty = !reused;
    fprint_device_info(file, reused && capture_timestamp == 0);
    fflush(file);
  }

  if (!stub) {
    log_empty = false;
    if (tag != NULL) {
      fprintf(file, "[%s][%s][%s]: %s\n", tag, level_tag(level), checkpoint, msg);
    } else {
      fprintf(file, "[%s][%s]: %s\n", level_tag(level), checkpoint, msg);
    }
    fflush(file);
  }
}

void log(int tag, log_level level, const char *msg, bool mute) {
  pthread_mutex_lock(&file_mutex);
  if (check_log_permission(tag, (int) level)) {
#ifdef __ANDROID__
    if (!mute) {
      __android_log_print(get_android_priority(level), LOG_TAG, "[%s]: %s", get_log_tag(tag, "JNI"), msg);
    }
#endif
    log_file(level, msg, get_log_tag(tag), false);
  }
  pthread_mutex_unlock(&file_mutex);
}

extern "C" {

int check_log_permission (int tag, int level) {
  return ((int) current_log_level >= level || capture_timestamp != 0) && (tag == 0 || (current_log_tags & tag) == tag || level <= LOG_LEVEL_WARNING);
}

void _vlogf (int level, int tag, const char *fmt, va_list args) {
  if (check_log_permission(tag, level)) {
    char *buffer = NULL;
    vasprintf(&buffer, fmt, args);
    log(tag, (log_level) level, buffer, false);
    free(buffer);
  }
}

void _logf (int level, int tag, const char *fmt, ...) {
  if (check_log_permission(tag, level)) {
    va_list args;
    va_start(args, fmt);
    _vlogf(level, tag, fmt, args);
    va_end(args);
  }
}
}

extern "C" {

/*(String logDir,
                                                String appVersionSignature,
                                                int sdkVersion, String sdkVersionName, String systemFingerprint,
                                                String deviceModel, String deviceBrand, String deviceDisplay, String deviceProduct, String deviceManufacturer);*/
JNIEXPORT void Java_org_thunderdog_challegram_Log_setInternalValues (JNIEnv *env, jclass clazz,
                                                                     jstring logDir,
                                                                     jstring osArch,
                                                                     jstring appVersionSignature, jint appVersionCode,
                                                                     jint sdkVersion, jstring sdkVersionName,
                                                                     jstring deviceModel, jstring deviceBrand, jstring deviceDisplay, jstring deviceProduct, jstring deviceManufacturer,
                                                                     jstring deviceFingerprint,
                                                                     jint screenWidth, jint screenHeight, jfloat screenDensity) {
  pthread_mutex_lock(&file_mutex);

  log_dir = jni::from_jstring(env, logDir);
  os_arch = jni::from_jstring(env, osArch);
  app_version_signature = jni::from_jstring(env, appVersionSignature);
  app_version_code = appVersionCode;
  sdk_version = (int) sdkVersion;
  sdk_version_name = jni::from_jstring(env, sdkVersionName);
  device_model = jni::from_jstring(env, deviceModel);
  device_brand = jni::from_jstring(env, deviceBrand);
  device_display = jni::from_jstring(env, deviceDisplay);
  device_product = jni::from_jstring(env, deviceProduct);
  device_manufacturer = jni::from_jstring(env, deviceManufacturer);
  device_fingerprint = jni::from_jstring(env, deviceFingerprint);
  screen_width = screenWidth;
  screen_height = screenHeight;
  screen_density = screenDensity;

  pthread_mutex_unlock(&file_mutex);
}

JNIEXPORT jboolean Java_org_thunderdog_challegram_Log_startCaptureImpl (JNIEnv *env, jclass clazz) {
  jboolean res;
  pthread_mutex_lock(&file_mutex);
  if (capture_timestamp == 0) {
    capture_timestamp = (uint64_t) time(NULL);
    log_file(LogLevelInfo, NULL, NULL, true);
    if (file != NULL) {
      res = JNI_TRUE;
    } else {
      res = JNI_FALSE;
      capture_timestamp = 0;
      log_close();
    }
  } else {
    res = JNI_TRUE;
  }
  pthread_mutex_unlock(&file_mutex);
  return res;
}

JNIEXPORT jstring Java_org_thunderdog_challegram_Log_endCaptureImpl (JNIEnv *env, jclass clazz) {
  std::string path;
  pthread_mutex_lock(&file_mutex);
  if (capture_timestamp != 0) {
    if (!log_empty) {
      path.append(current_log_path);
    }
    log_close();
  }
  pthread_mutex_unlock(&file_mutex);
  return (*env).NewStringUTF(path.c_str());
}

JNIEXPORT void Java_org_thunderdog_challegram_Log_logToFileImpl (JNIEnv *env, jclass clazz, jint tag, jint level, jstring msg) {
  const char *msg_str = (*env).GetStringUTFChars(msg, JNI_FALSE);
  log(tag, (log_level) level, msg_str, true);
  if (msg_str != NULL) {
    (*env).ReleaseStringUTFChars(msg, msg_str);
  }
}

JNIEXPORT void Java_org_thunderdog_challegram_Log_setLogLevelImpl (JNIEnv *env, jclass clazz, jint level) {
  pthread_mutex_lock(&file_mutex);
  current_log_level = (log_level) level;
  pthread_mutex_unlock(&file_mutex);
}

JNIEXPORT void Java_org_thunderdog_challegram_Log_setLogTagsImpl (JNIEnv *env, jclass clazz, jlong tags) {
  pthread_mutex_lock(&file_mutex);
  current_log_tags = (int64_t) tags;
  pthread_mutex_unlock(&file_mutex);
}

JNIEXPORT jstring Java_org_thunderdog_challegram_Log_getLogTag (JNIEnv *env, jclass clazz, jint tag) {
  const char *str = get_log_tag(tag, "");
  return (*env).NewStringUTF(str);
}

JNIEXPORT jstring Java_org_thunderdog_challegram_Log_getLogTagDescription (JNIEnv *env, jclass clazz, jint tag) {
  const char *str = get_log_tag_desc(tag, "");
  return (*env).NewStringUTF(str);
}

JNIEXPORT jstring Java_org_thunderdog_challegram_Log_getDeviceInformation (JNIEnv *env, jclass clazz) {
  std::string str = sprintf_device_info();
  return (*env).NewStringUTF(str.c_str());
}

JNIEXPORT void Java_org_thunderdog_challegram_Log_closeLogImpl (JNIEnv *env, jclass clazz) {
  log_close();
}
}

JNI_FUNC(void, onFatalError, jstring jmessage, jint cause) {
  std::string message = jni::from_jstring(env, jmessage);
  onFatalError(env, message, cause);
}
