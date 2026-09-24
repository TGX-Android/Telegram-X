# opus

if(EXISTS "${OPUS_DIR}/CMakeLists.txt")
  add_subdirectory(
    "${OPUS_DIR}"
    "${CMAKE_BINARY_DIR}/third_party/opus"
    EXCLUDE_FROM_ALL
  )
else()
  message(WARNING "./gradlew patchOpus was not run yet")
endif()