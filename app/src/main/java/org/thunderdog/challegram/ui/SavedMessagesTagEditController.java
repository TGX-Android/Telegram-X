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
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGSavedMessagesTag;
import org.thunderdog.challegram.emoji.EmojiFilter;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.CharacterStyleFilter;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.text.CodePointCountFilter;
import me.vkryl.android.text.RestrictFilter;
import me.vkryl.android.widget.FrameLayoutFix;

/**
 * Controller for editing Saved Messages tag labels.
 * Tag labels are a Premium feature and are limited to 12 characters.
 */
public class SavedMessagesTagEditController extends EditBaseController<SavedMessagesTagEditController.Arguments> implements SettingsAdapter.TextChangeListener {

  public static final int MAX_TAG_LABEL_LENGTH = 12;

  public static class Arguments {
    public final TGSavedMessagesTag tag;
    public final @Nullable TagLabelChangeListener listener;

    public Arguments (TGSavedMessagesTag tag, @Nullable TagLabelChangeListener listener) {
      this.tag = tag;
      this.listener = listener;
    }
  }

  public interface TagLabelChangeListener {
    void onTagLabelChanged (TdApi.ReactionType reactionType, String newLabel);
  }

  public SavedMessagesTagEditController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_editSavedMessagesTagLabel;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.SavedMessagesTagLabel);
  }

  private String currentLabel;
  private TGSavedMessagesTag tag;

  @Override
  public void setArguments (Arguments args) {
    super.setArguments(args);
    this.tag = args.tag;
    this.currentLabel = args.tag.getLabel() != null ? args.tag.getLabel() : "";
  }

  private SettingsAdapter adapter;

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    // Check for Premium
    if (!tdlib.hasPremium()) {
      UI.showToast(Lang.getMarkdownString(this, R.string.SavedMessagesTagLabelPremiumRequired), Toast.LENGTH_LONG);
      navigateBack();
      return;
    }

    adapter = new SettingsAdapter(this) {
      @Override
      protected void modifyEditText (ListItem item, ViewGroup parent, MaterialEditTextGroup editText) {
        editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        Views.setSingleLine(editText.getEditText(), true);
        editText.setMaxLength(MAX_TAG_LABEL_LENGTH);
      }
    };

    ListItem item = new ListItem(ListItem.TYPE_EDITTEXT_COUNTERED, R.id.input, 0, R.string.SavedMessagesTagLabel)
      .setStringValue(currentLabel)
      .setInputFilters(new InputFilter[] {
        new CodePointCountFilter(MAX_TAG_LABEL_LENGTH),
        new EmojiFilter(),
        new CharacterStyleFilter(),
        new RestrictFilter(new char[] {'\n'})
          .setListener((filter, source, start, end, index, c) -> {
            if (end - start == 1) {
              onDoneClick(null);
            }
          })
      })
      .setOnEditorActionListener(new SimpleEditorActionListener(EditorInfo.IME_ACTION_DONE, this));

    List<ListItem> items = new ArrayList<>();
    items.add(item);
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, R.id.description, 0, R.string.SavedMessagesTagLabelHint)
      .setTextColorId(ColorId.textLight));

    adapter.setTextChangeListener(this);
    adapter.setLockFocusOn(this, true);
    adapter.setItems(items, false);

    recyclerView.setAdapter(adapter);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);

    setDoneVisible(true);
  }

  @Override
  public void onTextChanged (int id, ListItem item, MaterialEditTextGroup v) {
    currentLabel = v.getText().toString();
  }

  @Override
  protected void onProgressStateChanged (boolean inProgress) {
    adapter.updateLockEditTextById(R.id.input, inProgress ? currentLabel : null);
  }

  @Override
  protected final boolean onDoneClick () {
    if (!isInProgress()) {
      // Re-check Premium (in case subscription expired during editing)
      if (!tdlib.hasPremium()) {
        UI.showToast(Lang.getMarkdownString(this, R.string.SavedMessagesTagLabelPremiumRequired), Toast.LENGTH_LONG);
        return true;
      }

      setInProgress(true);
      String label = this.currentLabel.trim();
      TdApi.ReactionType reactionType = tag.getReactionType();

      tdlib.setSavedMessagesTagLabel(reactionType, label, result -> tdlib.ui().post(() -> {
        if (!isDestroyed()) {
          setInProgress(false);
          switch (result.getConstructor()) {
            case TdApi.Ok.CONSTRUCTOR: {
              Arguments args = getArgumentsStrict();
              if (args.listener != null) {
                args.listener.onTagLabelChanged(reactionType, label);
              }
              onSaveCompleted();
              break;
            }
            case TdApi.Error.CONSTRUCTOR: {
              UI.showError(result);
              break;
            }
          }
        }
      }));
    }
    return true;
  }
}
