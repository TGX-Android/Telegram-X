package tgx.test

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Benchmarks of [org.thunderdog.challegram.util.text.Text] and its drawings in more cases,
 * which run together by one class name
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(TextBenchmarks::class, TextOutputs::class)
class TextSuite
