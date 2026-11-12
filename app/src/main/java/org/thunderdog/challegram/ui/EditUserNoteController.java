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

public class EditUserNoteController extends EditTextController<String> {
  private final long userId;

  public EditUserNoteController (Context context, Tdlib tdlib, long userId) {
    super(context, tdlib);
    this.userId = userId;
    setDelegate(new Delegate() {
      @Override
      public int getId () {
        return R.id.controller_userNote;
      }

      @Override
      public CharSequence getName () {
        return Lang.getString(R.string.UserNote);
      }

      @Override
      public CharSequence getHint () {
        return Lang.getString(R.string.UserNoteHint);
      }

      @Override
      public CharSequence getDescription () {
        return Lang.getString(R.string.UserNoteDescription);
      }

      @Override
      public String getCurrentValue () {
        return getArguments();
      }

      @Override
      public boolean onDonePressed (EditTextController<?> controller, DoneButton button, CharSequence value) {
        if (isInProgress()) {
          return false;
        }
        TdApi.FormattedText note = new TdApi.FormattedText(value != null ? value.toString() : "", null);
        setDoneInProgress(true);
        setStackLocked(true);
        tdlib.send(new TdApi.SetUserNote(userId, note), (ok, error) -> runOnUiThreadOptional(() -> {
          setStackLocked(false);
          setDoneInProgress(false);
          if (error != null) {
            context().tooltipManager().builder(button).icon(R.drawable.baseline_warning_24).show(tdlib, TD.toErrorString(error));
          } else {
            navigateBack();
          }
        }));
        return true;
      }
    });
  }
}
