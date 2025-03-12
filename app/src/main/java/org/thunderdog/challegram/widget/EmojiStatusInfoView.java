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
 * File created on 08/06/2024
 */
package org.thunderdog.challegram.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.style.ClickableSpan;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.emoji.EmojiCodes;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.text.FormattedText;
import org.thunderdog.challegram.util.text.TextEntity;

import tgx.td.Td;

@SuppressLint("ViewConstructor")
public class EmojiStatusInfoView extends CustomTextView {
  private final ViewController<?> parent;
  private @Nullable TdApi.StickerSetInfo lastInfo;
  private int key = 0;
  private String displayName;

  public EmojiStatusInfoView(Context context, ViewController<?> parent, Tdlib tdlib) {
    super(context, tdlib);
    this.parent = parent;

    setTextColorId(ColorId.textLight);
    setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(40f)));
    setPadding(Screen.dp(16f), Screen.dp(14f), Screen.dp(16f), Screen.dp(6f));
  }

  public void update (long firstEmojiId, long emojiPacksIds, String displayName, ClickableSpan onClickListener, boolean animated) {
    this.displayName = displayName;

    if (lastInfo == null || lastInfo.id != emojiPacksIds) {
      lastInfo = null;
      this.key += 1;

      final int key = this.key;
      parent.tdlib().client().send(new TdApi.GetStickerSet(emojiPacksIds), (obj) -> {
        if (obj.getConstructor() != TdApi.StickerSet.CONSTRUCTOR) return;
        UI.post(() -> {
          if (this.key != key) return;
          this.lastInfo = Td.toStickerSetInfo((TdApi.StickerSet) obj);
          updateImpl(firstEmojiId, onClickListener, lastInfo, false);
        });
      });
    }

    updateImpl(firstEmojiId, onClickListener, lastInfo, animated);
  }

  private void updateImpl (long firstEmojiId, ClickableSpan onClickListener, @Nullable TdApi.StickerSetInfo info, boolean animated) {
    final Tdlib tdlib = parent.tdlib();

    final FormattedText formattedName; {
      final String text = displayName;
      final TextEntity[] entities = TextEntity.valueOf(tdlib, text, new TdApi.TextEntity[]{
        new TdApi.TextEntity(0, text.length(), new TdApi.TextEntityTypeBold())
      }, null);
      formattedName = new FormattedText(text, entities);
    }

    final FormattedText formattedPackName; {
      final String text = info == null ? Lang.getString(R.string.LoadingMessageEmojiPack) : info.title;
      final TextEntity[] entities = TextEntity.valueOf(tdlib, text, new TdApi.TextEntity[]{
        new TdApi.TextEntity(0, text.length(), new TdApi.TextEntityTypeUrl()),
        new TdApi.TextEntity(0, text.length(), new TdApi.TextEntityTypeBold())
      }, null);

      if (entities != null && entities.length > 0) {
        entities[0].setOnClickListener(onClickListener);
      }

      formattedPackName = new FormattedText(text, entities);
    }

    final TdApi.Sticker cover = info != null && info.covers != null && info.covers.length > 0 && info.covers[0].fullType.getConstructor() == TdApi.StickerFullTypeCustomEmoji.CONSTRUCTOR ?
      info.covers[0] : null;
    final FormattedText formattedEmoji = info != null ? FormattedText.customEmoji(tdlib,
      cover != null ? cover.emoji : EmojiCodes.THUMBS_UP,
      cover != null ? cover.id : firstEmojiId
    ) : null;

    final FormattedText formattedPackText = formattedEmoji != null ?
      FormattedText.concat(" ", formattedEmoji, formattedPackName) :
      formattedPackName;

    FormattedText formattedText = FormattedText.valueOf(tdlib, null, R.string.EmojiStatusUsed,
      formattedName,
      formattedPackText
    );

    setText(formattedText.text, formattedText.entities, animated);
  }
}
