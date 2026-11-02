package org.thunderdog.challegram.core

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Guarantees of SerialQueue, the same as those of a Looper, run against each implementation: the
 * Looper of LooperSerialQueue is the reference. Times are of System.nanoTime(), which counts as
 * uptimeMillis() of a Looper does
 */
abstract class SerialQueueGuarantees {
  abstract fun create(name: String, callback: SerialQueue.Callback? = null): SerialQueue

  private val queues = mutableListOf<SerialQueue>()

  private fun queue(callback: SerialQueue.Callback? = null): SerialQueue =
    create("SerialQueueGuarantees", callback).also { queues += it }

  @After
  fun quitQueues() {
    queues.forEach { it.quit(false) }
  }

  private fun now() = System.nanoTime()

  private fun block(queue: SerialQueue): CountDownLatch {
    // Blocks the queue until the latch opens, so that work sent meanwhile waits
    val blocked = CountDownLatch(1)
    val started = CountDownLatch(1)
    queue.post {
      started.countDown()
      blocked.await(10, TimeUnit.SECONDS)
    }
    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()
    return blocked
  }

  private fun drain(queue: SerialQueue) {
    val done = CountDownLatch(1)
    queue.post { done.countDown() }
    assertWithMessage("the queue ran all its work").that(done.await(10, TimeUnit.SECONDS)).isTrue()
  }

  @Test
  fun workWithoutDelayRunsInOrderOfSendingFromSeveralThreads() {
    val queue = queue()
    val ran = Collections.synchronizedList(mutableListOf<Int>())
    var sent = 0
    val lock = Any()
    val threads = (0 until 4).map {
      Thread {
        repeat(2_000) {
          // The order of sending is the order of this lock
          synchronized(lock) {
            val number = sent++
            queue.post { ran += number }
          }
        }
      }
    }
    threads.forEach { it.start() }
    threads.forEach { it.join() }
    drain(queue)
    assertThat(ran).isEqualTo((0 until 8_000).toList())
  }

  @Test
  fun delayedWorkRunsByTimeThenByOrderOfSending() {
    val queue = queue()
    val ran = Collections.synchronizedList(mutableListOf<String>())
    val blocked = block(queue)
    queue.post({ ran += "30" }, 30)
    queue.post({ ran += "10 first" }, 10)
    queue.post({ ran += "20" }, 20)
    queue.post({ ran += "10 second" }, 10)
    Thread.sleep(60)
    blocked.countDown()
    drain(queue)
    assertThat(ran).containsExactly("10 first", "10 second", "20", "30").inOrder()
  }

  @Test
  fun dueDelayedWorkRunsBeforeWorkSentAfterItsTime() {
    val queue = queue()
    val ran = Collections.synchronizedList(mutableListOf<String>())
    val blocked = block(queue)
    queue.post({ ran += "delayed" }, 10)
    queue.post { ran += "sent before its time" }
    Thread.sleep(30)
    queue.post { ran += "sent after its time" }
    queue.post({ ran += "not due" }, 1_000)
    blocked.countDown()
    drain(queue)
    assertThat(ran).containsExactly("sent before its time", "delayed", "sent after its time")
      .inOrder()
  }

  @Test
  fun delayedWorkRunsNotEarlierThanItsTime() {
    val queue = queue()
    val delays = listOf(1L, 5L, 20L, 50L)
    val early = Collections.synchronizedList(mutableListOf<String>())
    val done = CountDownLatch(delays.size)
    for (delay in delays) {
      val sentAt = now()
      queue.post({
        val waited = (now() - sentAt) / 1_000_000.0
        // A Looper counts delays in whole milliseconds of uptime
        if (waited < delay - 1) {
          early += "$delay ms after $waited ms"
        }
        done.countDown()
      }, delay)
    }
    assertThat(done.await(5, TimeUnit.SECONDS)).isTrue()
    assertThat(early).isEmpty()
  }

  @Test
  fun workInOrderOfTimeAndSendingAgainstModel() {
    // Several threads send work with random delays: work without a delay and delayed work that
    // is due run in the order of their times, by the order of sending for the same time. The
    // queue takes the time of a work between the moments before and after its send, which the
    // thread can be preempted between, and to the millisecond at least, so only pairs whose
    // times differ by more than that are compared
    val queue = queue()
    class Sent(val number: Int, val sentBefore: Long, val delay: Long) {
      var sentAfter = 0L
      var ranAt = 0L
      val earliest get() = sentBefore + delay * 1_000_000
      val latest get() = sentAfter + delay * 1_000_000
    }
    val all = Collections.synchronizedList(mutableListOf<Sent>())
    val ran = Collections.synchronizedList(mutableListOf<Sent>())
    val lock = Any()
    var number = 0
    val threads = (0 until 4).map { index ->
      Thread {
        val random = Random(index)
        repeat(500) {
          val delay = if (random.nextInt(3) == 0) random.nextLong(1, 20) else 0L
          synchronized(lock) {
            val sent = Sent(number++, now(), delay)
            all += sent
            queue.post({
              sent.ranAt = now()
              ran += sent
            }, delay)
            sent.sentAfter = now()
          }
          if (random.nextInt(50) == 0) {
            Thread.sleep(random.nextLong(1, 5))
          }
        }
      }
    }
    threads.forEach { it.start() }
    threads.forEach { it.join() }
    Thread.sleep(40)
    drain(queue)
    assertThat(ran.size).isEqualTo(all.size)
    val problems = mutableListOf<String>()
    val margin = 1_000_000L
    for (i in ran.indices) {
      val sent = ran[i]
      if (sent.ranAt < sent.earliest - 1_000_000) {
        problems += "${sent.number} ran ${(sent.earliest - sent.ranAt) / 1_000} µs early"
      }
      for (j in i + 1 until ran.size) {
        val later = ran[j]
        val inOrder = if (sent.delay == 0L && later.delay == 0L) {
          sent.number < later.number
        } else {
          // The later one was due clearly before and waited when this one ran
          !(later.latest + margin < sent.earliest && later.sentAfter < sent.ranAt)
        }
        if (!inOrder) {
          problems += "${later.number} (due ${later.latest}) after ${sent.number} " +
            "(due ${sent.earliest})"
        }
      }
      if (problems.size > 10) {
        break
      }
    }
    assertThat(problems).isEmpty()
  }

  @Test
  fun cancelledWorkDoesntRun() {
    val ran = Collections.synchronizedList(mutableListOf<String>())
    val first = Any()
    val second = Any()
    val queue = queue { what, arg1, _, obj ->
      ran += "$what ${if (obj === first) "first" else if (obj === second) "second" else obj} $arg1"
    }
    val blocked = block(queue)
    val runnable = Runnable { ran += "runnable" }
    queue.post(runnable)
    queue.post(runnable, 10)
    queue.send(1, 1, 0, first)
    queue.send(1, 2, 0, second)
    queue.send(1, 3, 0, first, 10)
    queue.send(2, 4, 0, null)
    queue.send(3, 5, 0, null)
    queue.send(3, 6, 0, first, 10)
    queue.cancel(runnable)
    queue.cancel(1, first)
    queue.cancel(3, null)
    blocked.countDown()
    Thread.sleep(30)
    drain(queue)
    assertThat(ran).containsExactly("1 second 2", "2 null 4").inOrder()
  }

  @Test
  fun waitingWorkIsFound() {
    val first = Any()
    val second = Any()
    val queue = queue { _, _, _, _ -> }
    val blocked = block(queue)
    queue.send(1, 0, 0, first)
    queue.send(2, 0, 0, null, 1_000)
    assertThat(queue.has(1, first)).isTrue()
    assertThat(queue.has(1, null)).isTrue()
    assertThat(queue.has(1, second)).isFalse()
    assertThat(queue.has(2, null)).isTrue()
    assertThat(queue.has(3, null)).isFalse()
    queue.cancel(2, null)
    assertThat(queue.has(2, null)).isFalse()
    blocked.countDown()
    drain(queue)
    assertThat(queue.has(1, first)).isFalse()
  }

  @Test
  fun workSentFromItsOwnThreadRunsAfterWaitingWork() {
    val queue = queue()
    val ran = Collections.synchronizedList(mutableListOf<String>())
    val onThread = Collections.synchronizedList(mutableListOf<Boolean>())
    val blocked = block(queue)
    queue.post {
      onThread += queue.isCurrentThread()
      ran += "first"
      queue.post { ran += "sent from the queue" }
    }
    queue.post { ran += "second" }
    blocked.countDown()
    drain(queue)
    drain(queue)
    assertThat(ran).containsExactly("first", "second", "sent from the queue").inOrder()
    assertThat(onThread).containsExactly(true)
    assertThat(queue.isCurrentThread()).isFalse()
  }

  @Test
  fun exceptionOfWorkGoesToUncaughtExceptionHandler() {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    val caught = CountDownLatch(1)
    var thread: String? = null
    var error: Throwable? = null
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
      thread = t.name
      error = e
      caught.countDown()
    }
    try {
      val queue = queue()
      val thrown = IllegalStateException("thrown by work")
      queue.post { throw thrown }
      assertThat(caught.await(5, TimeUnit.SECONDS)).isTrue()
      assertThat(thread).isEqualTo("SerialQueueGuarantees")
      assertThat(error).isSameInstanceAs(thrown)
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(previous)
    }
  }

  @Test
  fun quitSafelyRunsDueWorkOnly() {
    val queue = queue()
    val ran = Collections.synchronizedList(mutableListOf<String>())
    val blocked = block(queue)
    queue.post { ran += "due" }
    queue.post({ ran += "not due" }, 1_000)
    queue.quit(true)
    queue.post { ran += "sent after quit" }
    blocked.countDown()
    Thread.sleep(100)
    assertThat(ran).containsExactly("due")
  }

  @Test
  fun quitDropsWaitingWork() {
    val queue = queue()
    val ran = Collections.synchronizedList(mutableListOf<String>())
    val blocked = block(queue)
    queue.post { ran += "waiting" }
    queue.quit(false)
    queue.post { ran += "sent after quit" }
    blocked.countDown()
    Thread.sleep(100)
    assertThat(ran).isEmpty()
  }
}
