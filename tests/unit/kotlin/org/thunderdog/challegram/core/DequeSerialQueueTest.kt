package org.thunderdog.challegram.core

class DequeSerialQueueTest : SerialQueueGuarantees() {
  override fun create(name: String, callback: SerialQueue.Callback?): SerialQueue =
    DequeSerialQueue(name, Thread.NORM_PRIORITY, callback)
}
