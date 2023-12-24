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
 * File created on 24/12/2023 at 22:32
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.emoji.EmojiFilter;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.CharacterStyleFilter;
import org.thunderdog.challegram.widget.DoneButton;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.vkryl.android.text.CodePointCountFilter;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;

public class EditTextController<T> extends EditBaseController<T> {
  public interface Delegate {
    @IdRes int getId ();
    default @DrawableRes int getDoneIcon () {
      return 0;
    }
    CharSequence getName ();
    CharSequence getHint ();
    CharSequence getDescription ();
    default String getCurrentValue () {
      return null;
    }
    default void onValueChanged (EditTextController<?> controller, String value) {
      controller.setDoneVisible(allowEmptyValue() || !StringUtils.isEmptyOrBlank(value));
    }
    boolean onDonePressed (EditTextController<?> controller, DoneButton button, String value);
    default int getMaxLength () {
      return 0;
    }
    default boolean allowEmptyValue () {
      return true;
    }
    default boolean needFocusInput () {
      return true;
    }
  }

  private Delegate delegate;

  public final void setDelegate (Delegate delegate) {
    this.delegate = delegate;
  }

  public EditTextController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return delegate.getId();
  }

  @Override
  public CharSequence getName () {
    return delegate.getName();
  }

  private SettingsAdapter adapter;
  private String currentValue;

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    final int maxLength = delegate.getMaxLength();
    adapter = new SettingsAdapter(this) {
      @Override
      protected void modifyEditText (ListItem item, ViewGroup parent, MaterialEditTextGroup editText) {
        editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        Views.setSingleLine(editText.getEditText(), false);
        if (maxLength > 0) {
          editText.setMaxLength(maxLength);
        }
      }
    };

    currentValue = delegate.getCurrentValue();
    CharSequence hint = delegate.getHint();
    ListItem item = new ListItem(maxLength > 0 ? ListItem.TYPE_EDITTEXT_COUNTERED : ListItem.TYPE_EDITTEXT, R.id.input, 0, hint, false);
    if (!StringUtils.isEmpty(currentValue)) {
      item.setStringValue(currentValue);
    }

    List<InputFilter> inputFilters = new ArrayList<>();
    if (maxLength > 0) {
      inputFilters.add(new CodePointCountFilter(maxLength));
    }
    Collections.addAll(inputFilters,
      new EmojiFilter(),
      new CharacterStyleFilter()
    );
    item.setInputFilters(inputFilters.toArray(new InputFilter[0]));

    List<ListItem> items = new ArrayList<>();
    items.add(item);

    CharSequence description = delegate.getDescription();
    if (!StringUtils.isEmpty(description)) {
      ListItem descriptionItem = new ListItem(ListItem.TYPE_DESCRIPTION, R.id.description, 0, description, false)
        .setTextColorId(ColorId.textLight);
      items.add(descriptionItem);
    }

    adapter.setTextChangeListener((id, item1, v, text) -> {
      currentValue = text;
      delegate.onValueChanged(this, text);
    });
    adapter.setLockFocusOn(this, delegate.needFocusInput());
    adapter.setItems(items, false);

    recyclerView.setAdapter(adapter);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);

    setDoneVisible(delegate.allowEmptyValue());
    @DrawableRes int iconRes = delegate.getDoneIcon();
    if (iconRes != 0) {
      setDoneIcon(iconRes);
    }
  }

  @Override
  protected void onProgressStateChanged (boolean inProgress) {
    adapter.updateLockEditTextById(R.id.input, inProgress ? currentValue : null);
  }

  @Override
  protected boolean onDoneClick () {
    return delegate.onDonePressed(this, getDoneButton(), currentValue);
  }
}
