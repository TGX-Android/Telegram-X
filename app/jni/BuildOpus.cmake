# opus

set(OPUS_DIR "${THIRDPARTY_DIR}/opus")

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