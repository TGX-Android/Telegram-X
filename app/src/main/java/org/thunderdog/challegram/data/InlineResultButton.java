package org.thunderdog.challegram.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;

/**
 * Date: 05/12/2016
 * Author: default
 */

public class InlineResultButton extends InlineResult<String> {
  private final long userId;
  private final @NonNull String text;
  private long sourceChatId;

  public InlineResultButton (BaseActivity context, Tdlib tdlib, long inlineBotId, @NonNull String text, @Nullable String parameter) {
    super(context, tdlib, TYPE_BUTTON, null, parameter);
    this.userId = inlineBotId;
    this.text = text;
  }

  public long getSourceChatId () {
    return sourceChatId;
  }

  public void setSourceChatId (long chatId) {
    this.sourceChatId = chatId;
  }

  public long getUserId () {
    return userId;
  }

  public @NonNull String getText () {
    return text;
  }

  @Override
  protected int getContentHeight () {
    return Screen.dp(36f) + Screen.dp(1f);
  }
}
