# rnnoise

set(RNNOISE_DIR "${THIRDPARTY_DIR}/rnnoise")

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