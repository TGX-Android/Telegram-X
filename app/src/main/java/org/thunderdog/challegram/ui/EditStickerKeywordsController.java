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
 */
package org.thunderdog.challegram.ui;

import android.content.Context;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.widget.DoneButton;

import java.util.LinkedHashSet;

public class EditStickerKeywordsController extends EditTextController<Void> {
  public EditStickerKeywordsController (
    Context context,
    Tdlib tdlib,
    int stickerId,
    Runnable onChanged
  ) {
    super(context, tdlib);
    setDelegate(new Delegate() {
      @Override
      public int getId () {
        return R.id.controller_stickerKeywords;
      }

      @Override
      public CharSequence getName () {
        return Lang.getString(R.string.StickerKeywords);
      }

      @Override
      public CharSequence getHint () {
        return Lang.getString(R.string.StickerKeywordsHint);
      }

      @Override
      public CharSequence getDescription () {
        return Lang.getString(R.string.StickerKeywordsDescription);
      }

      @Override
      public boolean onDonePressed (
        EditTextController<?> controller,
        DoneButton button,
        CharSequence value
      ) {
        if (isInProgress()) {
          return false;
        }
        final String[] keywords;
        try {
          keywords = parseKeywords(value.toString());
        } catch (IllegalArgumentException e) {
          context().tooltipManager().builder(button).show(tdlib, R.string.StickerKeywordsLimit);
          return false;
        }
        setDoneInProgress(true);
        setStackLocked(true);
        tdlib.send(new TdApi.SetStickerKeywords(new TdApi.InputFileId(stickerId), keywords),
          (ok, error) -> runOnUiThreadOptional(() -> {
            setStackLocked(false);
            setDoneInProgress(false);
            if (error != null) {
              context().tooltipManager().builder(button).show(tdlib, TD.toErrorString(error));
            } else {
              if (onChanged != null) {
                onChanged.run();
              }
              navigateBack();
            }
          })
        );
        return true;
      }
    });
  }

  public static String[] parseKeywords (String value) {
    LinkedHashSet<String> keywords = new LinkedHashSet<>();
    int length = 0;
    for (String part : value.split("[,\\n\\r]")) {
      String keyword = part.trim();
      if (!keyword.isEmpty() && keywords.add(keyword)) {
        length += keyword.codePointCount(0, keyword.length());
        if (keywords.size() > 20 || length > 64) {
          throw new IllegalArgumentException("Sticker keyword limit exceeded");
        }
      }
    }
    return keywords.toArray(new String[0]);
  }
}
