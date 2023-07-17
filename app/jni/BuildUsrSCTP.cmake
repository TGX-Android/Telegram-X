# usrsctp

set(USRSCTP_DIR "${THIRDPARTY_DIR}/usrsctp/usrsctplib")

add_library(usrsctp STATIC
  "${USRSCTP_DIR}/netinet/sctp_asconf.c"
  "${USRSCTP_DIR}/netinet/sctp_auth.c"
  "${USRSCTP_DIR}/netinet/sctp_bsd_addr.c"
  "${USRSCTP_DIR}/netinet/sctp_callout.c"
  "${USRSCTP_DIR}/netinet/sctp_cc_functions.c"
  "${USRSCTP_DIR}/netinet/sctp_crc32.c"
  "${USRSCTP_DIR}/netinet/sctp_indata.c"
  "${USRSCTP_DIR}/netinet/sctp_input.c"
  "${USRSCTP_DIR}/netinet/sctp_output.c"
  "${USRSCTP_DIR}/netinet/sctp_pcb.c"
  "${USRSCTP_DIR}/netinet/sctp_peeloff.c"
  "${USRSCTP_DIR}/netinet/sctp_sha1.c"
  "${USRSCTP_DIR}/netinet/sctp_ss_functions.c"
  "${USRSCTP_DIR}/netinet/sctp_sysctl.c"
  "${USRSCTP_DIR}/netinet/sctp_timer.c"
  "${USRSCTP_DIR}/netinet/sctp_userspace.c"
  "${USRSCTP_DIR}/netinet/sctp_usrreq.c"
  "${USRSCTP_DIR}/netinet/sctputil.c"
  "${USRSCTP_DIR}/netinet6/sctp6_usrreq.c"
  "${USRSCTP_DIR}/user_environment.c"
  "${USRSCTP_DIR}/user_mbuf.c"
  "${USRSCTP_DIR}/user_recv_thread.c"
  "${USRSCTP_DIR}/user_socket.c"
)
target_compile_definitions(usrsctp PRIVATE
  __Userspace__
  SCTP_SIMPLE_ALLOCATOR
  SCTP_PROCESS_LEVEL_LOCKS
)
target_include_directories(usrsctp PUBLIC
  "${USRSCTP_DIR}"
)