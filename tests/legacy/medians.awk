# Medians of each kind and value, e.g. "cold chats shown 2350", with the number of starts and the
# range; empty values, e.g. of a start that am start -W didn't time, are left out
{
  for (f = 3; f <= NF; f++) {
    split($f, kv, "=")
    if (kv[2] == "") {
      continue
    }
    key = $1 SUBSEP kv[1]
    if (!(key in values)) {
      order[++n] = key
    }
    values[key] = values[key] " " kv[2]
  }
}
END {
  for (i = 1; i <= n; i++) {
    split(order[i], k, SUBSEP)
    c = split(values[order[i]], x, " ")
    sort(x, c)
    median = c % 2 ? x[(c + 1) / 2] : (x[c / 2] + x[c / 2 + 1]) / 2
    printf "%s\t%s\t%s\t%d starts, %s–%s\n", k[1], k[2], median, c, x[1], x[c]
  }
}
function sort(a, n,   i, j, t) {
  for (i = 2; i <= n; i++) {
    t = a[i]
    for (j = i - 1; j >= 1 && a[j] + 0 > t + 0; j--) {
      a[j + 1] = a[j]
    }
    a[j + 1] = t
  }
}
