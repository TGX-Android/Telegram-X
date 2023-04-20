# webp

set(WEBP_DIR "${THIRDPARTY_DIR}/webp")

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
    -frename-registers
    -s
  )
endif()