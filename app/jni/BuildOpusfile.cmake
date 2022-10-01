# opusfile

set(OPUSFILE_DIR "${THIRDPARTY_DIR}/opusfile")

add_library(opusfile STATIC
  "${OPUSFILE_DIR}/src/info.c"
  "${OPUSFILE_DIR}/src/internal.c"
  "${OPUSFILE_DIR}/src/opusfile.c"
  "${OPUSFILE_DIR}/src/stream.c"
)
target_include_directories(opusfile PUBLIC
  "${OPUSFILE_DIR}/include"
)
target_link_libraries(opusfile PUBLIC
  ogg opus
)