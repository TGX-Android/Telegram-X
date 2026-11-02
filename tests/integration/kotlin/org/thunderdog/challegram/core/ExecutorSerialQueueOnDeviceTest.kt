package org.thunderdog.challegram.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExecutorSerialQueueOnDeviceTest : SerialQueueGuarantees() {
  override fun create(name: String, callback: SerialQueue.Callback?): SerialQueue =
    ExecutorSerialQueue(name, Thread.NORM_PRIORITY, callback)
}
