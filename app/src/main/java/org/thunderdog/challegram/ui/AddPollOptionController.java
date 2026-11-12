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

public class AddPollOptionController extends EditTextController<long[]> {
  public AddPollOptionController (Context context, Tdlib tdlib) {
    super(context, tdlib);
    setDelegate(new Delegate() {
      @Override
      public int getId () {
        return R.id.controller_addPollOption;
      }

      @Override
      public CharSequence getName () {
        return Lang.getString(R.string.AddPollOption);
      }

      @Override
      public CharSequence getHint () {
        return Lang.getString(R.string.AddPollOptionHint);
      }

      @Override
      public CharSequence getDescription () {
        return Lang.getString(R.string.AddPollOptionDescription);
      }

      @Override
      public int getMaxLength () {
        return 100;
      }

      @Override
      public boolean allowEmptyValue () {
        return false;
      }

      @Override
      public boolean onDonePressed (EditTextController<?> controller, DoneButton button, CharSequence value) {
        if (isInProgress() || StringUtils.isEmptyOrBlank(value)) {
          return false;
        }
        long[] args = getArguments();
        if (args == null) {
          return false;
        }
        TdApi.InputPollOption option = new TdApi.InputPollOption(new TdApi.FormattedText(value.toString(), null), null);
        setDoneInProgress(true);
        setStackLocked(true);
        tdlib.send(new TdApi.AddPollOption(args[0], args[1], option), (ok, error) -> runOnUiThreadOptional(() -> {
          setStackLocked(false);
          setDoneInProgress(false);
          if (error != null) {
            context().tooltipManager()
              .builder(button)
              .icon(R.drawable.baseline_warning_24)
              .show(tdlib, TD.toErrorString(error));
          } else {
            navigateBack();
          }
        }));
        return true;
      }
    });
  }
}
