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
 * File created on 25/07/2017
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.emoji.EmojiFilter;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.CharacterStyleFilter;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;

import me.vkryl.android.text.CodePointCountFilter;
import me.vkryl.android.text.RestrictFilter;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.td.TdConstants;

public class EditBioController extends EditBaseController<EditBioController.Arguments> implements SettingsAdapter.TextChangeListener {
  public static class Arguments {
    public final String currentBio;
    public final long chatId;
    public @Nullable ProfileController bioChangeListener;

    public Arguments (String currentBio, long chatId) {
      this.currentBio = currentBio;
      this.chatId = chatId;
    }

    public Arguments setBioChangeListener (ProfileController c) {
      this.bioChangeListener = c;
      return this;
    }
  }

  public EditBioController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private boolean isDescription () {
    long chatId = getArguments() != null ? getArguments().chatId : 0;
    return chatId != 0 && !tdlib.isSelfChat(chatId);
  }

  @Override
  public int getId () {
    return isDescription() ? R.id.controller_editDescription : R.id.controller_editBio;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(isDescription() ? R.string.Description : R.string.UserBio);
  }

  private String currentBio;

  @Override
  public void setArguments (Arguments args) {
    super.setArguments(args);
    currentBio = args.currentBio;
  }

  private SettingsAdapter adapter;

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void modifyEditText (ListItem item, ViewGroup parent, MaterialEditTextGroup editText) {
        editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        Views.setSingleLine(editText.getEditText(), false);
        editText.setMaxLength(isDescription() ? TdConstants.MAX_CHANNEL_DESCRIPTION_LENGTH : tdlib.maxBioLength());
      }
    };

    ListItem item = new ListItem(ListItem.TYPE_EDITTEXT_COUNTERED, R.id.input, 0, isDescription() ? R.string.Description : R.string.UserBio).setStringValue(currentBio);
    ListItem[] items;
    if (isDescription()) {
      item.setInputFilters(new InputFilter[]{
        new CodePointCountFilter(TdConstants.MAX_CHANNEL_DESCRIPTION_LENGTH),
        new EmojiFilter(),
        new CharacterStyleFilter()
      });
      items = new ListItem[] {item};
    } else {
      item.setInputFilters(new InputFilter[]{
        new CodePointCountFilter(tdlib.maxBioLength()),
        new EmojiFilter(),
        new CharacterStyleFilter(),
        new RestrictFilter(new char[] {'\n'})
          .setListener((filter, source, start, end, index, c) -> {
          if (end - start == 1) {
            onDoneClick(null);
          }
        })
      }).setOnEditorActionListener(new SimpleEditorActionListener(EditorInfo.IME_ACTION_DONE, this));
      items = new ListItem[] {
        item,
        (new ListItem(ListItem.TYPE_DESCRIPTION, R.id.description, 0, R.string.BioDescription)
        .setTextColorId(ColorId.textLight))
      };
    }
    adapter.setTextChangeListener(this);
    adapter.setLockFocusOn(this, true);
    adapter.setItems(items, false);

    recyclerView.setAdapter(adapter);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);

    setDoneVisible(true);
  }

  @Override
  public void onTextChanged (int id, ListItem item, MaterialEditTextGroup v, String text) {
    currentBio = text;
  }

  @Override
  protected void onProgressStateChanged (boolean inProgress) {
    adapter.updateLockEditTextById(R.id.input, inProgress ? currentBio : null);
  }

  @Override
  protected final boolean onDoneClick () {
    if (!isInProgress()) {
      setInProgress(true);
      String currentBio = this.currentBio;
      if (isDescription()) {
        tdlib.client().send(new TdApi.SetChatDescription(getArgumentsStrict().chatId, currentBio), object -> tdlib.ui().post(() -> {
          if (!isDestroyed()) {
            setInProgress(false);
            switch (object.getConstructor()) {
              case TdApi.Ok.CONSTRUCTOR: {
                if (getArgumentsStrict().bioChangeListener != null) {
                  getArgumentsStrict().bioChangeListener.updateDescription(getArgumentsStrict().chatId, currentBio);
                }
                onSaveCompleted();
                break;
              }
              case TdApi.Error.CONSTRUCTOR: {
                UI.showError(object);
                break;
              }
            }
          }
        }));
      } else {
        tdlib.client().send(new TdApi.SetBio(currentBio), object -> tdlib.ui().post(() -> {
          switch (object.getConstructor()) {
            case TdApi.Ok.CONSTRUCTOR: {
              // Do nothing, as bio should be already updated via updateUserFull
              break;
            }
            case TdApi.Error.CONSTRUCTOR: {
              UI.showError(object);
              break;
            }
          }
          if (!isDestroyed()) {
            setInProgress(false);
            if (object.getConstructor() == TdApi.Ok.CONSTRUCTOR) {
              onSaveCompleted();
            }
          }
        }));
      }
    }
    return true;
  }
}
