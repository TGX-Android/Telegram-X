# flac

set(FLAC_DIR "${THIRDPARTY_DIR}/flac")
set(EXO_FLAC_DIR "${CMAKE_HOME_DIRECTORY}/../../thirdparty/ExoPlayer/extensions/flac/src/main/jni")

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
  -funroll-loops -fPIC
)