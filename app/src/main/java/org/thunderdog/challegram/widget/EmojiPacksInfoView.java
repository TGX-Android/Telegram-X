/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 18/08/2023
 */
package org.thunderdog.challegram.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.style.ClickableSpan;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.text.FormattedText;
import org.thunderdog.challegram.util.text.TextEntity;

import me.vkryl.td.Td;

@SuppressLint("ViewConstructor")
public class EmojiPacksInfoView extends CustomTextView {
  private final ViewController<?> parent;
  private @Nullable TdApi.StickerSetInfo lastInfo;
  private int key = 0;
  private long[] emojiPacksIds;

  public EmojiPacksInfoView (Context context, ViewController<?> parent, Tdlib tdlib) {
    super(context, tdlib);
    this.parent = parent;

    setTextColorId(ColorId.textLight);
    setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(40f)));
    setPadding(Screen.dp(16f), Screen.dp(14f), Screen.dp(16f), Screen.dp(6f));
  }

  public void update (long firstEmojiId, long[] emojiPacksIds, ClickableSpan onClickListener, boolean animated) {
    this.emojiPacksIds = emojiPacksIds;
    boolean isSingle = emojiPacksIds.length == 1;

    if (isSingle && (lastInfo == null || lastInfo.id != emojiPacksIds[0])) {
      lastInfo = null;
      this.key += 1;

      final int key = this.key;
      parent.tdlib().client().send(new TdApi.GetStickerSet(emojiPacksIds[0]), (obj) -> {
        if (obj.getConstructor() != TdApi.StickerSet.CONSTRUCTOR) return;
        UI.post(() -> {
          if (this.key != key) return;
          this.lastInfo = Td.toStickerSetInfo((TdApi.StickerSet) obj);
          updateImpl(firstEmojiId, emojiPacksIds.length, onClickListener, lastInfo, false);
        });
      });
    }

    updateImpl(firstEmojiId, emojiPacksIds.length, onClickListener, lastInfo, animated);
  }

  public long[] getEmojiPacksIds () {
    return emojiPacksIds;
  }

  private void updateImpl (long firstEmojiId, int emojiPacksCount, ClickableSpan onClickListener, @Nullable TdApi.StickerSetInfo info, boolean animated) {
    boolean isSingle = emojiPacksCount == 1;

    String link;
    if (isSingle) {
      link = Lang.getString(R.string.xEmojiPacksEmojiSingle,
        info != null ? info.title : Lang.getString(R.string.LoadingMessageEmojiPack)
      );
    } else {
      link = Lang.plural(R.string.xEmojiPacks, emojiPacksCount);;
    }
    String text = Lang.getString(isSingle ? R.string.EmojiUsedFromSingle : R.string.EmojiUsedFromX, link);

    // FIXME: do not rely on cloud strings here
    final int linkStart = text.indexOf(link);
    final int emojiStart = text.indexOf("*");

    try {
      final TdApi.FormattedText formattedTextRaw;
      if (!isSingle || emojiStart == -1) {
        formattedTextRaw = new TdApi.FormattedText(text, new TdApi.TextEntity[]{
          new TdApi.TextEntity(linkStart, link.length(), new TdApi.TextEntityTypeUrl())
        });
      } else {
        formattedTextRaw = new TdApi.FormattedText(text, emojiStart >= linkStart ? new TdApi.TextEntity[]{
          new TdApi.TextEntity(linkStart, link.length(), new TdApi.TextEntityTypeUrl()),
          new TdApi.TextEntity(emojiStart, 1, new TdApi.TextEntityTypeCustomEmoji(firstEmojiId))
        }: new TdApi.TextEntity[]{
          new TdApi.TextEntity(emojiStart, 1, new TdApi.TextEntityTypeCustomEmoji(firstEmojiId)),
          new TdApi.TextEntity(linkStart, link.length(), new TdApi.TextEntityTypeUrl())
        });
      }

      FormattedText formattedText = FormattedText.valueOf(parent, formattedTextRaw, null);
      if (formattedText.entities != null) {
        for (TextEntity entity : formattedText.entities) {
          entity.setOnClickListener(onClickListener);
          if (!entity.isCustomEmoji()) {
            entity.makeBold(true);
          }
        }
      }

      setText(text, formattedText.entities, animated);
    } catch (Throwable t) {
      Log.e("Cannot get string", t);
    }
  }
}
