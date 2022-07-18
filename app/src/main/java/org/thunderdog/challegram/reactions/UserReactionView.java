package org.thunderdog.challegram.reactions;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.SmallChatView;

import me.vkryl.td.Td;

public class UserReactionView extends SmallChatView {
  private ImageReceiver reactionReceiver;
  private Path outline = new Path();
  private boolean needSeparator;

  public UserReactionView (Context context, Tdlib tdlib) {
    super(context, tdlib);
    reactionReceiver = new ImageReceiver(this, 0);
    setPadding(0, 0, Screen.dp(54), 0);
  }

  @Override
  protected void onDraw (Canvas c) {
    super.onDraw(c);
    int size = Screen.dp(20);
    reactionReceiver.setBounds(0, 0, size, size);
    c.save();
    c.translate(getWidth() - getHeight() / 2 - size / 2, getHeight() / 2 - size / 2);
    if (reactionReceiver.needPlaceholder()) {
      c.drawPath(outline, Paints.getPlaceholderPaint());
    }
    reactionReceiver.draw(c);
    c.restore();

    if (needSeparator) {
      int sHeight = Math.max(1, Screen.dp(.5f));
      c.drawRect(Screen.dp(72), getHeight() - sHeight, getWidth(), getHeight(), Paints.fillingPaint(Theme.separatorColor()));
    }
  }

  public void setReaction (String reaction) {
    TdApi.Reaction r = tdlib.getReaction(reaction);
    reactionReceiver.requestFile(TD.toImageFile(tdlib, r.staticIcon.thumbnail));
    outline.rewind();
    int targetSize = Screen.dp(30);
    TdApi.Sticker sticker = r.staticIcon;
    Td.buildOutline(sticker.outline, Math.min((float) targetSize / (float) sticker.width, (float) targetSize / (float) sticker.height), outline);
  }

  public void setUser (long id) {
    setChat(new DoubleTextWrapper(tdlib, id, true));
  }

  public void setNeedSeparator (boolean needSeparator) {
    this.needSeparator = needSeparator;
  }
}
