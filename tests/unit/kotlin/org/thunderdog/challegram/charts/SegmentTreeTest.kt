package org.thunderdog.challegram.charts

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

class SegmentTreeTest {
  @Test
  fun rangeQueriesMatchLinearSearch() {
    val random = Random(42)
    // Arrays shorter than 30 values are searched without building tree
    for (size in listOf(1, 29, 30, 31, 100, 257)) {
      val values = IntArray(size) {
        random.nextInt(0, 1000)
      }
      val tree = SegmentTree(values.copyOf())
      for (from in 0 until size) {
        for (to in from until size) {
          val range = values.copyOfRange(from, to + 1)
          assertThat(tree.rMaxQ(from, to)).isEqualTo(range.max())
          assertThat(tree.rMinQ(from, to)).isEqualTo(range.min())
        }
      }
    }
  }
}
