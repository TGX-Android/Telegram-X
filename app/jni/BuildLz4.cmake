# lz4

set(LZ4_DIR "${THIRDPARTY_DIR}/lz4/lib")

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