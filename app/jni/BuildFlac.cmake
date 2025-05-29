# flac

set(FLAC_DIR "${THIRDPARTY_DIR}/flac")
set(INSTALL_MANPAGES OFF)
add_subdirectory("${FLAC_DIR}"
  EXCLUDE_FROM_ALL
)