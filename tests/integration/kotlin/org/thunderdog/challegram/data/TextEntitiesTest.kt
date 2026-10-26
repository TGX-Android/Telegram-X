package org.thunderdog.challegram.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.drinkless.tdlib.TdApi
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextEntitiesTest {
  @Test
  fun findsMentionAndUrl() {
    val text = TdApi.FormattedText("Follow @tgx_android at https://t.me/tgx_android", arrayOf())
    assertThat(TD.parseEntities(text)).isTrue()
    assertThat(text.entities.map { it.type.constructor }).containsExactly(
      TdApi.TextEntityTypeMention.CONSTRUCTOR,
      TdApi.TextEntityTypeUrl.CONSTRUCTOR
    ).inOrder()
  }
}
