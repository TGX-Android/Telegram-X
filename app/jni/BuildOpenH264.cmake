# OpenH264

set(OPENH264_DIR "${THIRDPARTY_DIR}/openh264")

add_library(openh264 STATIC
  "${OPENH264_DIR}/codec/encoder/core/src/au_set.cpp"
  "${OPENH264_DIR}/codec/encoder/core/src/deblocking.cpp"
  "${OPENH264_DIR}/codec/encoder/core/src/decode_mb_aux.cpp"
  "${OPENH264_DIR}/codec/encoder/core/src/encode_mb_aux.cpp"
  "${OPENH264_DIR}/codec/encoder/core/src/encoder_data_tables.cpp"
  "${OPENH264_DIR}/codec/encoder/core/src/encoder_ext.cpp"
  "${OPENH264_DIR}/codec/encoder/core/src/encoder.cpp"
  "${OPENH264_DIR}/codec/encoder/core/src/get_intra_predictor.cpp"
  "${OPENH264_DIR}/codec/encoder/core/src/md.cpp"
  "${OPENH264_DIR}/codec/encoder/core/src/mv_pred.cpp"
  "${OPENH264_DIR}/codec/encoder/core/src/nal_encap.cpp"
  "${OPENH264_DIR}/codec/encoder/core/src/paraset_strategy.cpp"
  "${OPENH264_DIR}/codec/encoder/core/src/picture_handle.cpp"
  "${OPENH264_DIR}/codec/encoder/core/src/ratectl.cpp"
  "${OPENH264_DIR}/codec/encoder/core/src/ref_list_mgr_svc.cpp"
  "${OPENH264_DIR}/codec/encoder/core/src/sample.cpp"
  "${OPENH264_DIR}/codec/encoder/core/src/set_mb_syn_cabac.cpp"
  "${OPENH264_DIR}/codec/encoder/core/src/set_mb_syn_cavlc.cpp"
  "${OPENH264_DIR}/codec/encoder/core/src/slice_multi_threading.cpp"
  "${OPENH264_DIR}/codec/encoder/core/src/svc_base_layer_md.cpp"
  "${OPENH264_DIR}/codec/encoder/core/src/svc_enc_slice_segment.cpp"
  "${OPENH264_DIR}/codec/encoder/core/src/svc_encode_mb.cpp"
  "${OPENH264_DIR}/codec/encoder/core/src/svc_encode_slice.cpp"
  "${OPENH264_DIR}/codec/encoder/core/src/svc_mode_decision.cpp"
  "${OPENH264_DIR}/codec/encoder/core/src/svc_motion_estimate.cpp"
  "${OPENH264_DIR}/codec/encoder/core/src/svc_set_mb_syn_cabac.cpp"
  "${OPENH264_DIR}/codec/encoder/core/src/svc_set_mb_syn_cavlc.cpp"
  "${OPENH264_DIR}/codec/encoder/core/src/wels_preprocess.cpp"
  "${OPENH264_DIR}/codec/encoder/core/src/wels_task_base.cpp"
  "${OPENH264_DIR}/codec/encoder/core/src/wels_task_encoder.cpp"
  "${OPENH264_DIR}/codec/encoder/core/src/wels_task_management.cpp"
  "${OPENH264_DIR}/codec/encoder/plus/src/welsEncoderExt.cpp"
  "${OPENH264_DIR}/codec/common/src/welsCodecTrace.cpp"
  "${OPENH264_DIR}/codec/common/src/common_tables.cpp"
  "${OPENH264_DIR}/codec/common/src/copy_mb.cpp"
  "${OPENH264_DIR}/codec/common/src/cpu.cpp"
  "${OPENH264_DIR}/codec/common/src/crt_util_safe_x.cpp"
  "${OPENH264_DIR}/codec/common/src/deblocking_common.cpp"
  "${OPENH264_DIR}/codec/common/src/expand_pic.cpp"
  "${OPENH264_DIR}/codec/common/src/intra_pred_common.cpp"
  "${OPENH264_DIR}/codec/common/src/mc.cpp"
  "${OPENH264_DIR}/codec/common/src/memory_align.cpp"
  "${OPENH264_DIR}/codec/common/src/sad_common.cpp"
  "${OPENH264_DIR}/codec/common/src/WelsTaskThread.cpp"
  "${OPENH264_DIR}/codec/common/src/WelsThread.cpp"
  "${OPENH264_DIR}/codec/common/src/WelsThreadLib.cpp"
  "${OPENH264_DIR}/codec/common/src/WelsThreadPool.cpp"
  "${OPENH264_DIR}/codec/common/src/utils.cpp"
  "${OPENH264_DIR}/codec/processing/src/adaptivequantization/AdaptiveQuantization.cpp"
  "${OPENH264_DIR}/codec/processing/src/backgrounddetection/BackgroundDetection.cpp"
  "${OPENH264_DIR}/codec/processing/src/common/memory.cpp"
  "${OPENH264_DIR}/codec/processing/src/common/WelsFrameWork.cpp"
  "${OPENH264_DIR}/codec/processing/src/common/WelsFrameWorkEx.cpp"
  "${OPENH264_DIR}/codec/processing/src/complexityanalysis/ComplexityAnalysis.cpp"
  "${OPENH264_DIR}/codec/processing/src/denoise/denoise.cpp"
  "${OPENH264_DIR}/codec/processing/src/denoise/denoise_filter.cpp"
  "${OPENH264_DIR}/codec/processing/src/downsample/downsample.cpp"
  "${OPENH264_DIR}/codec/processing/src/downsample/downsamplefuncs.cpp"
  "${OPENH264_DIR}/codec/processing/src/imagerotate/imagerotate.cpp"
  "${OPENH264_DIR}/codec/processing/src/imagerotate/imagerotatefuncs.cpp"
  "${OPENH264_DIR}/codec/processing/src/scenechangedetection/SceneChangeDetection.cpp"
  "${OPENH264_DIR}/codec/processing/src/scrolldetection/ScrollDetection.cpp"
  "${OPENH264_DIR}/codec/processing/src/scrolldetection/ScrollDetectionFuncs.cpp"
  "${OPENH264_DIR}/codec/processing/src/vaacalc/vaacalcfuncs.cpp"
  "${OPENH264_DIR}/codec/processing/src/vaacalc/vaacalculation.cpp"
)
target_compile_options(openh264 PUBLIC
  -Wall -finline-functions -fno-strict-aliasing -O3 -frtti -Wno-unknown-pragmas -funroll-loops -fexceptions -fno-math-errno
)
set_target_properties(openh264 PROPERTIES
  ANDROID_ARM_MODE arm
)
target_compile_definitions(openh264 PRIVATE
)
target_compile_definitions(openh264 PUBLIC
)
target_include_directories(openh264 PUBLIC
  "${OPENH264_DIR}/codec/encoder/core/inc"
  "${OPENH264_DIR}/codec/encoder/plus/inc"
  "${OPENH264_DIR}/codec/decoder/plus/inc"
  "${OPENH264_DIR}/codec/common/inc"
  "${OPENH264_DIR}/codec/api/svc"
  "${OPENH264_DIR}/codec/processing/interface"
  "${OPENH264_DIR}/codec/processing/src/common"
)

if (${ANDROID_ABI} STREQUAL "armeabi-v7a")
  target_sources(openh264 PRIVATE
    "${OPENH264_DIR}/codec/encoder/core/arm/intra_pred_neon.S"
    "${OPENH264_DIR}/codec/encoder/core/arm/intra_pred_sad_3_opt_neon.S"
    "${OPENH264_DIR}/codec/encoder/core/arm/memory_neon.S"
    "${OPENH264_DIR}/codec/encoder/core/arm/pixel_neon.S"
    "${OPENH264_DIR}/codec/encoder/core/arm/reconstruct_neon.S"
    "${OPENH264_DIR}/codec/encoder/core/arm/svc_motion_estimation.S"
    "${OPENH264_DIR}/codec/common/arm/copy_mb_neon.S"
    "${OPENH264_DIR}/codec/common/arm/deblocking_neon.S"
    "${OPENH264_DIR}/codec/common/arm/expand_picture_neon.S"
    "${OPENH264_DIR}/codec/common/arm/intra_pred_common_neon.S"
    "${OPENH264_DIR}/codec/common/arm/mc_neon.S"
    "${OPENH264_DIR}/codec/processing/src/arm/adaptive_quantization.S"
    "${OPENH264_DIR}/codec/processing/src/arm/down_sample_neon.S"
    "${OPENH264_DIR}/codec/processing/src/arm/pixel_sad_neon.S"
    "${OPENH264_DIR}/codec/processing/src/arm/vaa_calc_neon.S"
  )
  target_include_directories(openh264 PUBLIC
    "${OPENH264_DIR}/codec/common/arm"
  )
  target_compile_definitions(openh264 PUBLIC
    HAVE_NEON=1
  )
elseif(${ANDROID_ABI} STREQUAL "arm64-v8a")
  target_sources(openh264 PRIVATE
    "${OPENH264_DIR}/codec/encoder/core/arm64/intra_pred_aarch64_neon.S"
    "${OPENH264_DIR}/codec/encoder/core/arm64/intra_pred_sad_3_opt_aarch64_neon.S"
    "${OPENH264_DIR}/codec/encoder/core/arm64/memory_aarch64_neon.S"
    "${OPENH264_DIR}/codec/encoder/core/arm64/pixel_aarch64_neon.S"
    "${OPENH264_DIR}/codec/encoder/core/arm64/reconstruct_aarch64_neon.S"
    "${OPENH264_DIR}/codec/encoder/core/arm64/svc_motion_estimation_aarch64_neon.S"
    "${OPENH264_DIR}/codec/common/arm64/copy_mb_aarch64_neon.S"
    "${OPENH264_DIR}/codec/common/arm64/deblocking_aarch64_neon.S"
    "${OPENH264_DIR}/codec/common/arm64/expand_picture_aarch64_neon.S"
    "${OPENH264_DIR}/codec/common/arm64/intra_pred_common_aarch64_neon.S"
    "${OPENH264_DIR}/codec/common/arm64/mc_aarch64_neon.S"
    "${OPENH264_DIR}/codec/processing/src/arm64/adaptive_quantization_aarch64_neon.S"
    "${OPENH264_DIR}/codec/processing/src/arm64/down_sample_aarch64_neon.S"
    "${OPENH264_DIR}/codec/processing/src/arm64/pixel_sad_aarch64_neon.S"
    "${OPENH264_DIR}/codec/processing/src/arm64/vaa_calc_aarch64_neon.S"
  )
  target_include_directories(openh264 PUBLIC
    "${OPENH264_DIR}/codec/common/arm64"
  )
  target_compile_definitions(openh264 PUBLIC
    HAVE_NEON_AARCH64=1
  )
endif()