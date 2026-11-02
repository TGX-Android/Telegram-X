# Durations of frames in ms from framestats of dumpsys gfxinfo of Android 6.0 and newer, from the
# vsync that a frame was meant for to its completion; frames with flags, e.g. of the first draw of a
# window, and frames that never completed are left out. Columns are found by their names in the
# header
/^Flags,/ {
  for (c = 1; c <= NF; c++) {
    col[$c] = c
  }
  next
}
col["Flags"] && $1 == "0" && NF > 5 && $col["FrameCompleted"] > $col["IntendedVsync"] {
  print ($col["FrameCompleted"] - $col["IntendedVsync"]) / 1e6
}
