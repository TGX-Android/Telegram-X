# rnnoise

set(RNNOISE_DIR "${TGCALLS_DEPS_DIR}/rnnoise")

add_library(rnnoise STATIC
  "${RNNOISE_DIR}/src/celt_lpc.c"
  "${RNNOISE_DIR}/src/denoise.c"
  "${RNNOISE_DIR}/src/kiss_fft.c"
  "${RNNOISE_DIR}/src/pitch.c"
  "${RNNOISE_DIR}/src/rnn_data.c"
  "${RNNOISE_DIR}/src/rnn_reader.c"
  "${RNNOISE_DIR}/src/rnn_reader.c"
  "${RNNOISE_DIR}/src/rnn.c"
)
target_include_directories(rnnoise PUBLIC
  "${RNNOISE_DIR}/include"
)

# Making sure symbols do not conflict with opus/celt
target_compile_definitions(rnnoise PRIVATE
  _celt_autocorr=_rnn_celt_autocorr
  _celt_lpc=_rnn_celt_lpc
  _celt_fir=_rnn_celt_fir
  _celt_iir=_rnn_celt_iir
  celt_iir=rnn_celt_iir
  pitch_downsample=rnn_pitch_downsample
  pitch_search=rnn_pitch_search
  remove_doubling=rnn_remove_doubling
)