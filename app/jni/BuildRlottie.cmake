# rlottie

set(RLOTTIE_DIR "${THIRDPARTY_DIR}/rlottie")

# TODO: move to "${THIRDPARTY_DIR}/rlottie"
add_library(rlottie STATIC
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
  "${RLOTTIE_DIR}/src/vector/vrle.cpp")
target_compile_options(rlottie PRIVATE
  -finline-functions -ffast-math
  -Os
  -fno-unwind-tables -fno-asynchronous-unwind-tables
  -Wnon-virtual-dtor -Woverloaded-virtual
  -Wno-unused-parameter
)
set_target_properties(rlottie PROPERTIES
  ANDROID_ARM_MODE arm)
target_compile_definitions(rlottie PUBLIC
  HAVE_PTHREAD NDEBUG)
target_include_directories(rlottie PUBLIC
  "${RLOTTIE_DIR}/inc"
  "${RLOTTIE_DIR}/src/vector/"
  "${RLOTTIE_DIR}/src/vector/pixman"
  "${RLOTTIE_DIR}/src/vector/freetype"
  "${RLOTTIE_DIR}/src/vector/stb")

if (${ANDROID_ABI} STREQUAL "armeabi-v7a")
  target_compile_options(rlottie PUBLIC
    -fno-integrated-as)
  target_compile_definitions(rlottie PRIVATE
    USE_ARM_NEON
  )
  target_sources(rlottie PRIVATE
    "${RLOTTIE_DIR}/src/vector/pixman/pixman-arm-neon-asm.S")
elseif(${ANDROID_ABI} STREQUAL "arm64-v8a")
  target_compile_options(rlottie PUBLIC
    -fno-integrated-as)
  target_compile_definitions(rlottie PRIVATE
    USE_ARM_NEON
    __ARM64_NEON__
  )
  target_sources(rlottie PRIVATE
    "${RLOTTIE_DIR}/src/vector/pixman/pixman-arma64-neon-asm.S"
  )
endif()