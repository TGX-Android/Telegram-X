# json11

set(JSON11_DIR "${THIRDPARTY_DIR}/tgcalls/tgcalls/third-party")

add_library(json11 STATIC
  "${JSON11_DIR}/json11.cpp"
)
set_target_properties(json11 PROPERTIES
  ANDROID_ARM_MODE arm
)
target_include_directories(json11 PUBLIC
  "${JSON11_DIR}"
)