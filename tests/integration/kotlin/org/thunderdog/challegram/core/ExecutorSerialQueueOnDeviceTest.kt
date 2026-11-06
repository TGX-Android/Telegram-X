package org.thunderdog.challegram.core

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.LOLLIPOP)
class ExecutorSerialQueueOnDeviceTest : SerialQueueGuarantees() {
  override fun create(name: String, callback: SerialQueue.Callback?): SerialQueue =
    ExecutorSerialQueue(name, Thread.NORM_PRIORITY, callback)
}
