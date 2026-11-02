# Times of frames, one in ms on each line, as "fling <i>: <frames> frames, mean, 90th percentile
# and longest in ms, and frames over budget ms, the time of one refresh, e.g. 16.7 at 60 Hz"
{
  t[++n] = $1
  sum += $1
  if ($1 > budget) {
    slow++
  }
}
END {
  for (a = 2; a <= n; a++) {
    v = t[a]
    for (b = a - 1; b >= 1 && t[b] + 0 > v + 0; b--) {
      t[b + 1] = t[b]
    }
    t[b + 1] = v
  }
  p90 = n ? t[int((n - 1) * 0.9) + 1] : 0
  printf "fling %s\t%d frames\tmean %.1f\tp90 %.1f\tmax %.1f\tover %s ms %d\n", \
    i, n, n ? sum / n : 0, p90, n ? t[n] : 0, budget, slow
}
