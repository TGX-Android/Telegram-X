# Durations of frames in ms from "Profile data in ms" of dumpsys gfxinfo of Android 4.1-4.3 with
# debug.hwui.profile, the sum of the times of Draw, Process and Execute of each frame
/^Profile data in ms:/ {
  p = 1
  next
}
p && /^\t[0-9.]+\t[0-9.]+\t[0-9.]+$/ {
  print $1 + $2 + $3
}
/^View hierarchy:/ {
  p = 0
}
