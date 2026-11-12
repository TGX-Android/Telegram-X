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

import me.vkryl.core.StringUtils;

public class EditStickerEmojisController extends EditTextController<String> {
  private final int stickerId;
  private final Runnable onEmojisChanged;

  public EditStickerEmojisController (Context context, Tdlib tdlib, int stickerId, Runnable onEmojisChanged) {
    super(context, tdlib);
    this.stickerId = stickerId;
    this.onEmojisChanged = onEmojisChanged;
    setDelegate(new Delegate() {
      @Override
      public int getId () {
        return R.id.controller_stickerEmojis;
      }

      @Override
      public CharSequence getName () {
        return Lang.getString(R.string.StickerEmojis);
      }

      @Override
      public CharSequence getHint () {
        return Lang.getString(R.string.StickerEmojisHint);
      }

      @Override
      public CharSequence getDescription () {
        return Lang.getString(R.string.StickerEmojisDescription);
      }

      @Override
      public boolean allowEmptyValue () {
        return false;
      }

      @Override
      public String getCurrentValue () {
        return getArguments();
      }

      @Override
      public boolean onDonePressed (EditTextController<?> controller, DoneButton button, CharSequence value) {
        if (isInProgress() || StringUtils.isEmptyOrBlank(value)) {
          return false;
        }
        setDoneInProgress(true);
        setStackLocked(true);
        tdlib.send(new TdApi.SetStickerEmojis(new TdApi.InputFileId(stickerId), value.toString().trim()), (ok, error) -> runOnUiThreadOptional(() -> {
          setStackLocked(false);
          setDoneInProgress(false);
          if (error != null) {
            context().tooltipManager().builder(button).icon(R.drawable.baseline_warning_24).show(tdlib, TD.toErrorString(error));
          } else {
            if (onEmojisChanged != null) {
              onEmojisChanged.run();
            }
            navigateBack();
          }
        }));
        return true;
      }
    });
  }
}
