# yuv

set(YUV_DIR "${THIRDPARTY_DIR}/libyuv")

add_subdirectory("${YUV_DIR}"
  EXCLUDE_FROM_ALL
)

if(TARGET yuv_shared)
  set_target_properties(yuv_shared PROPERTIES
    EXCLUDE_FROM_ALL TRUE
    EXCLUDE_FROM_DEFAULT_BUILD TRUE
  )
endif()

target_include_directories(yuv PUBLIC
  "${YUV_DIR}/include"
)
