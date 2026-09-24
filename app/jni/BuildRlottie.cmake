# rlottie

set(RLOTTIE_DIR "${THIRDPARTY_DIR}/rlottie")

if (ANDROID_NDK_MAJOR LESS_EQUAL 23)
  set(PIXMAN_DIR "${RLOTTIE_DIR}/src/vector/pixman")
else()
  set(PIXMAN_DIR "${THIRDPARTY_DIR}/pixman/pixman")
endif()

set(RLOTTIE_HEADERS
  "${RLOTTIE_DIR}/inc"
  "${RLOTTIE_DIR}/src/vector/"
  "${PIXMAN_DIR}"
  "${RLOTTIE_DIR}/src/vector/pixman"
  "${RLOTTIE_DIR}/src/vector/freetype"
  "${RLOTTIE_DIR}/src/vector/stb"
)
if (ANDROID_NDK_MAJOR GREATER 23)
  list(APPEND RLOTTIE_HEADERS
    "${RLOTTIE_DIR}/include"
  )
endif()
set(RLOTTIE_SOURCES
  "${RLOTTIE_DIR}/src/lottie/lottieanimation.cpp"
  "${RLOTTIE_DIR}/src/lottie/lottieitem.cpp"
  "${RLOTTIE_DIR}/src/lottie/lottiekeypath.cpp"
  "${RLOTTIE_DIR}/src/lottie/lottieloader.cpp"
  "${RLOTTIE_DIR}/src/lottie/lottiemodel.cpp"
  "${RLOTTIE_DIR}/src/lottie/lottieparser.cpp"
  "${RLOTTIE_DIR}/src/lottie/lottieproxymodel.cpp"
  "${RLOTTIE_DIR}/src/vector/freetype/v_ft_math.cpp"
  "${RLOTTIE_DIR}/src/vector/freetype/v_ft_raster.cpp"
  "${RLOTTIE_DIR}/src/vector/freetype/v_ft_stroker.cpp"
  "${RLOTTIE_DIR}/src/vector/pixman/vregion.cpp"
  "${RLOTTIE_DIR}/src/vector/stb/stb_image.cpp"
  "${RLOTTIE_DIR}/src/vector/vbezier.cpp"
  "${RLOTTIE_DIR}/src/vector/vbitmap.cpp"
  "${RLOTTIE_DIR}/src/vector/vbrush.cpp"
  "${RLOTTIE_DIR}/src/vector/vcompositionfunctions.cpp"
  "${RLOTTIE_DIR}/src/vector/vdasher.cpp"
  "${RLOTTIE_DIR}/src/vector/vdebug.cpp"
  "${RLOTTIE_DIR}/src/vector/vdrawable.cpp"
  "${RLOTTIE_DIR}/src/vector/vdrawhelper.cpp"
  "${RLOTTIE_DIR}/src/vector/vdrawhelper_neon.cpp"
  "${RLOTTIE_DIR}/src/vector/velapsedtimer.cpp"
  "${RLOTTIE_DIR}/src/vector/vimageloader.cpp"
  "${RLOTTIE_DIR}/src/vector/vinterpolator.cpp"
  "${RLOTTIE_DIR}/src/vector/vmatrix.cpp"
  "${RLOTTIE_DIR}/src/vector/vpainter.cpp"
  "${RLOTTIE_DIR}/src/vector/vpath.cpp"
  "${RLOTTIE_DIR}/src/vector/vpathmesure.cpp"
  "${RLOTTIE_DIR}/src/vector/vraster.cpp"
  "${RLOTTIE_DIR}/src/vector/vrect.cpp"
  "${RLOTTIE_DIR}/src/vector/vrle.cpp"
)

if (${ANDROID_ABI} STREQUAL "armeabi-v7a")
  list(APPEND RLOTTIE_SOURCES
    "${PIXMAN_DIR}/pixman-arm-neon-asm.S"
  )
elseif(${ANDROID_ABI} STREQUAL "arm64-v8a")
  list(APPEND RLOTTIE_SOURCES
    "${PIXMAN_DIR}/pixman-arma64-neon-asm.S"
  )
endif()

add_library(rlottie STATIC ${RLOTTIE_SOURCES})
target_compile_options(rlottie PRIVATE
  -Os
  -fno-unwind-tables -fno-asynchronous-unwind-tables
  -Wnon-virtual-dtor -Woverloaded-virtual
  -Wno-unused-parameter
)
set_target_properties(rlottie PROPERTIES
  ANDROID_ARM_MODE arm)
target_compile_definitions(rlottie PUBLIC
  HAVE_PTHREAD NDEBUG)
target_include_directories(rlottie PUBLIC ${RLOTTIE_HEADERS})

if (${ANDROID_ABI} STREQUAL "armeabi-v7a")
  if (ANDROID_NDK_MAJOR LESS_EQUAL 23)
    target_compile_options(rlottie PRIVATE
      -fno-integrated-as
    )
  endif()
  target_compile_definitions(rlottie PRIVATE
    USE_ARM_NEON
  )
elseif(${ANDROID_ABI} STREQUAL "arm64-v8a")
  if (ANDROID_NDK_MAJOR LESS_EQUAL 23)
    target_compile_options(rlottie PRIVATE
      -fno-integrated-as
    )
  endif()
  target_compile_definitions(rlottie PRIVATE
    USE_ARM_NEON
    __ARM64_NEON__
  )
endif()