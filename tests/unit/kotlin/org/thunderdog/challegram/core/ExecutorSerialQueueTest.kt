package org.thunderdog.challegram.core

class ExecutorSerialQueueTest : SerialQueueGuarantees() {
  override fun create(name: String, callback: SerialQueue.Callback?): SerialQueue =
    ExecutorSerialQueue(name, Thread.NORM_PRIORITY, callback)
}
