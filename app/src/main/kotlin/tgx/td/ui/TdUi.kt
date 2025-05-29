/*
 * This file is a part of tdlib-utils
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:JvmName("TdUi")
@file:JvmMultifileClass

package tgx.td.ui

import android.os.Build
import android.text.SpannableStringBuilder
import android.view.View
import android.widget.Toast
import androidx.collection.SparseArrayCompat
import me.vkryl.core.lambda.RunnableBool
import org.drinkless.tdlib.TdApi.*
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.navigation.ViewController
import org.thunderdog.challegram.navigation.ViewController.OptionColor
import org.thunderdog.challegram.telegram.Tdlib
import org.thunderdog.challegram.theme.ThemeDelegate
import org.thunderdog.challegram.tool.UI
import tgx.td.assertReportSponsoredResult_79c7a88e
import tgx.td.unsupported

@JvmOverloads
fun <T> ViewController<T>.reportChatSponsoredMessage (
  tdlib: Tdlib,
  chatId: Long,
  sponsoredMessage: SponsoredMessage,
  optionId: ByteArray = byteArrayOf(),
  forcedTheme: ThemeDelegate? = null,
  after: RunnableBool? = null
) {
  tdlib.send(ReportChatSponsoredMessage(chatId, sponsoredMessage.messageId, optionId)) { reportResult, error ->
    runOnUiThreadOptional {
      error?.let {
        UI.showError(error)
      } ?: reportResult?.let { result ->
        when (result.constructor) {
          ReportSponsoredResultOptionRequired.CONSTRUCTOR -> {
            require(result is ReportSponsoredResultOptionRequired)
            val title = SpannableStringBuilder().apply {
              append(result.title.takeIf { it.isNotEmpty() }
                ?: Lang.getMarkdownStringSecure(this@reportChatSponsoredMessage, R.string.ReportAdSubtitle))
              append("\n")
              append(Lang.getMarkdownStringSecure(this@reportChatSponsoredMessage, R.string.ReportAdPolicy))
            }
            val idToOptionId = SparseArrayCompat<ByteArray>()
            val info = ViewController.Options.Builder().apply {
              info(title)
              result.options.forEachIndexed { index, option ->
                val id = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                  View.generateViewId()
                } else {
                  index + 1
                }
                item(ViewController.OptionItem(id, option.text, OptionColor.NORMAL, 0))
                idToOptionId.put(id, option.id)
              }
            }.build()
            val popup = showOptions(info, { _, id ->
              val chosenOptionId = idToOptionId.get(id)!!
              reportChatSponsoredMessage(tdlib, chatId, sponsoredMessage, chosenOptionId, forcedTheme, after)
              true
            }, forcedTheme)
            popup?.setDisableCancelOnTouchDown(true)
          }

          ReportSponsoredResultOk.CONSTRUCTOR -> {
            require(result is ReportSponsoredResultOk)
            UI.showToast(Lang.getMarkdownStringSecure(this, R.string.ReportAdOk), Toast.LENGTH_SHORT)
            after?.runWithBool(true)
          }

          ReportSponsoredResultFailed.CONSTRUCTOR -> {
            require(result is ReportSponsoredResultFailed)
            UI.showToast(Lang.getMarkdownStringSecure(this, R.string.ReportAdFail), Toast.LENGTH_SHORT)
            after?.runWithBool(false)
          }

          ReportSponsoredResultAdsHidden.CONSTRUCTOR -> {
            require(result is ReportSponsoredResultAdsHidden)
            UI.showToast(Lang.getMarkdownStringSecure(this, R.string.ReportAdHidden), Toast.LENGTH_SHORT)
            after?.runWithBool(false)
          }

          ReportSponsoredResultPremiumRequired.CONSTRUCTOR -> {
            require(result is ReportSponsoredResultPremiumRequired)
            UI.showToast(Lang.getMarkdownStringSecure(this, R.string.ReportAdPremiumRequired), Toast.LENGTH_SHORT)
            after?.runWithBool(false)
          }

          else -> {
            assertReportSponsoredResult_79c7a88e()
            throw unsupported(result)
          }
        }
      }
    }
  }
}