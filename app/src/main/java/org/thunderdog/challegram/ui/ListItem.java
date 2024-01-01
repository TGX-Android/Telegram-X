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
 * File created on 16/11/2016
 */
package org.thunderdog.challegram.ui;

import android.text.InputFilter;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.PorterDuffColorId;
import org.thunderdog.challegram.util.DrawModifier;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.StringUtils;

public class ListItem {
  public static final int TYPE_CUSTOM = -1;
  public static final int TYPE_EMPTY_OFFSET = 0;
  public static final int TYPE_SEPARATOR = 1;
  public static final int TYPE_SHADOW_TOP = 2;
  public static final int TYPE_SHADOW_BOTTOM = 3;
  public static final int TYPE_SETTING = 4;
  public static final int TYPE_VALUED_SETTING = 5;
  public static final int TYPE_INFO_SETTING = 6;
  public static final int TYPE_RADIO_SETTING = 7;
  public static final int TYPE_HEADER = 8;
  public static final int TYPE_DESCRIPTION = 9;
  public static final int TYPE_BUILD_NO = 10;
  public static final int TYPE_SEPARATOR_FULL = 11;
  public static final int TYPE_CHECKBOX_OPTION = 12;
  public static final int TYPE_RADIO_OPTION = 13;
  public static final int TYPE_EMPTY_OFFSET_SMALL = 14;
  public static final int TYPE_PROGRESS = 15;
  public static final int TYPE_SESSION = 16;
  // public static final int TYPE_VALUED_SETTING_RED = 17;
  public static final int TYPE_SESSIONS_EMPTY = 18;
  public static final int TYPE_ICONIZED_EMPTY = 19;
  public static final int TYPE_BUTTON = 20;
  public static final int TYPE_2FA_EMAIL = 21;
  public static final int TYPE_VALUED_SETTING_RED_STUPID = 22;
  public static final int TYPE_STICKER_SET = 23;
  public static final int TYPE_EMPTY = 24;
  public static final int TYPE_DRAWER_OFFSET = 25;
  public static final int TYPE_ARCHIVED_STICKER_SET = 26;
  public static final int TYPE_USER = 27;
  public static final int TYPE_INFO = 28;
  public static final int TYPE_SHADOWED_OFFSET = 29;
  public static final int TYPE_SLIDER = 30;
  public static final int TYPE_EDITTEXT = 31;
  public static final int TYPE_CUSTOM_SINGLE = 32;
  public static final int TYPE_COUNTRY = 33;
  public static final int TYPE_EDITTEXT_NO_PADDING = 34;
  public static final int TYPE_PADDING = 35;
  public static final int TYPE_EMPTY_OFFSET_NO_HEAD = 36;
  public static final int TYPE_INFO_MULTILINE = 37;
  public static final int TYPE_MEMBERS_LIST = 38;
  public static final int TYPE_FAKE_PAGER_TOPVIEW = 39;
  public static final int TYPE_SMALL_MEDIA = 40;
  public static final int TYPE_CUSTOM_INLINE = 41;
  public static final int TYPE_LIST_INFO_VIEW = 42;
  public static final int TYPE_SMART_PROGRESS = 43;
  public static final int TYPE_SMART_EMPTY = 44;
  public static final int TYPE_DOUBLE_TEXTVIEW = 45;
  public static final int TYPE_DOUBLE_TEXTVIEW_ROUNDED = 46;
  public static final int TYPE_CHECKBOX_OPTION_DOUBLE_LINE = 47;
  public static final int TYPE_EDITTEXT_NO_PADDING_REUSABLE = 56;
  public static final int TYPE_CHAT_BETTER = 57;
  public static final int TYPE_RECYCLER_HORIZONTAL = 58;
  public static final int TYPE_CHAT_VERTICAL = 59;
  public static final int TYPE_CHAT_VERTICAL_FULLWIDTH = 60;
  public static final int TYPE_HEADER_WITH_ACTION = 61;
  public static final int TYPE_EDITTEXT_COUNTERED = 62;
  public static final int TYPE_CHAT_SMALL = 63;
  public static final int TYPE_CHAT_SMALL_SELECTABLE = 64;
  public static final int TYPE_EDITTEXT_WITH_PHOTO = 65;
  public static final int TYPE_EDITTEXT_WITH_PHOTO_SMALLER = 66;
  public static final int TYPE_RADIO_SETTING_WITH_NEGATIVE_STATE = 67;
  public static final int TYPE_EDITTEXT_CHANNEL_DESCRIPTION = 68;
  public static final int TYPE_CHECKBOX_OPTION_WITH_AVATAR = 69;
  public static final int TYPE_HEADER_PADDED = 70;
  public static final int TYPE_DESCRIPTION_CENTERED = 71;
  public static final int TYPE_CHATS_PLACEHOLDER = 72;
  public static final int TYPE_ZERO_VIEW = 73;
  public static final int TYPE_SLIDER_BRIGHTNESS = 74;
  public static final int TYPE_WEBSITES_EMPTY = 75;
  public static final int TYPE_SESSION_WITH_AVATAR = 76;
  public static final int TYPE_CHECKBOX_OPTION_REVERSE = 77;
  public static final int TYPE_DRAWER_EMPTY = 78;
  public static final int TYPE_DRAWER_ITEM = 79;
  public static final int TYPE_DRAWER_ITEM_WITH_RADIO = 80;
  public static final int TYPE_DRAWER_ITEM_WITH_AVATAR = 81;
  public static final int TYPE_ATTACH_LOCATION = 82;
  public static final int TYPE_ATTACH_LOCATION_BIG = 83;
  public static final int TYPE_LIVE_LOCATION_PROMO = 84;
  public static final int TYPE_RADIO_OPTION_WITH_AVATAR = 85;
  public static final int TYPE_LIVE_LOCATION_TARGET = 86;
  public static final int TYPE_VALUED_SETTING_WITH_RADIO = 87;
  public static final int TYPE_DRAWER_ITEM_WITH_RADIO_SEPARATED = 88;
  public static final int TYPE_VALUED_SETTING_COMPACT = 89;
  public static final int TYPE_VALUED_SETTING_COMPACT_WITH_RADIO = 90;
  public static final int TYPE_VALUED_SETTING_COMPACT_WITH_RADIO_2 = 390;
  public static final int TYPE_VALUED_SETTING_COMPACT_WITH_COLOR = 91;
  public static final int TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER = 92;
  public static final int TYPE_VALUED_SETTING_COMPACT_WITH_CHECKBOX = 393;
  public static final int TYPE_DESCRIPTION_SMALL = 93;
  public static final int TYPE_COLOR_PICKER = 94;
  public static final int TYPE_EDITTEXT_REUSABLE = 95;
  public static final int TYPE_EDITTEXT_POLL_OPTION = 96;
  public static final int TYPE_EDITTEXT_POLL_OPTION_ADD = 97;
  public static final int TYPE_RADIO_OPTION_LEFT = 98;
  public static final int TYPE_CHECKBOX_OPTION_MULTILINE = 99;
  public static final int TYPE_TEXT_VIEW = 100;

  public static final int TYPE_CHART_HEADER = 101;
  public static final int TYPE_CHART_LINEAR = 102;
  public static final int TYPE_CHART_DOUBLE_LINEAR = 103;
  public static final int TYPE_CHART_STACK_BAR = 104;
  public static final int TYPE_CHART_STACK_PIE = 105;
  public static final int TYPE_CHART_HEADER_DETACHED = 106;

  public static final int TYPE_HEADER_MULTILINE = 110;

  public static final int TYPE_PAGE_BLOCK = 48;
  public static final int TYPE_PAGE_BLOCK_MEDIA = 49;
  public static final int TYPE_PAGE_BLOCK_GIF = 50;
  public static final int TYPE_PAGE_BLOCK_COLLAGE = 51;
  public static final int TYPE_PAGE_BLOCK_AVATAR = 52;
  public static final int TYPE_PAGE_BLOCK_SLIDESHOW = 53;
  public static final int TYPE_PAGE_BLOCK_EMBEDDED = 54;
  public static final int TYPE_PAGE_BLOCK_VIDEO = 55;
  public static final int TYPE_PAGE_BLOCK_TABLE = 111;

  public static final int TYPE_MESSAGE_PREVIEW = 120;
  public static final int TYPE_STATS_MESSAGE_PREVIEW = 121;

  public static final int TYPE_EMBED_STICKER = 130;
  public static final int TYPE_JOIN_REQUEST = 131;
  public static final int TYPE_CHAT_HEADER_LARGE = 132;

  public static final int TYPE_REACTION_CHECKBOX = 140;

  public static final int TYPE_USER_SMALL = 141;

  public static final int TYPE_GIFT_HEADER = 142;

  private static final int FLAG_SELECTED = 1;
  private static final int FLAG_BOOL_VALUE = 1 << 1;
  private static final int FLAG_USE_SELECTION_INDEX = 1 << 2;

  private int viewType;
  private final int id;
  private int iconResource;
  private int stringResource;
  private @Nullable CharSequence string;
  private final int checkId;
  private int flags;
  private long longId;
  private String highlight;

  private @Nullable String[] sliderValues;
  private int sliderValue;

  private @Nullable DrawModifier drawModifier;

  private String stringKey, stringValue;
  private @PorterDuffColorId int textColorId;
  private TdlibAccentColor accentColor;
  private int textPaddingLeft;
  private int intValue;
  private long longValue;

  private int firstVisiblePosition = -1, offsetInPixels;

  private @ColorId int radioColorId;

  private int height;

  private Object data;

  public ListItem (int viewType) {
    this(viewType, 0, 0, 0, null, 0, false);
  }

  public ListItem (int viewType, int id) {
    this(viewType, id, 0, 0, null, 0, false);
  }

  public ListItem (int viewType, int id, int iconResource, int stringResource) {
    this(viewType, id, iconResource, stringResource, null, 0, false);
  }

  public ListItem (int viewType, int id, int iconResource, int stringResource, boolean isSelected) {
    this(viewType, id, iconResource, stringResource, null, id, isSelected);
  }

  public ListItem (int viewType, int id, int iconResource, CharSequence string) {
    this(viewType, id, iconResource, 0, string, id, false);
  }

  public ListItem (int viewType, int id, int iconResource, CharSequence string, boolean isSelected) {
    this(viewType, id, iconResource, 0, string, id, isSelected);
  }

  public ListItem (int viewType, int id, int iconResource, CharSequence string, int checkId, boolean isSelected) {
    this(viewType, id, iconResource, 0, string, checkId, isSelected);
  }

  public ListItem (int viewType, int id, int iconResource, int stringResource, int checkId, boolean isSelected) {
    this(viewType, id, iconResource, stringResource, null, checkId, isSelected);
  }

  public ListItem (int viewType, int id, int iconResource, int stringResource, @Nullable CharSequence string, int checkId, boolean isSelected) {
    this.viewType = viewType;
    this.id = id;
    this.iconResource = iconResource;
    this.stringResource = stringResource;
    this.string = string;
    this.checkId = checkId;
    if (isSelected) {
      this.flags = FLAG_SELECTED;
    }
  }

  public boolean setViewType (int viewType) {
    if (this.viewType != viewType) {
      this.viewType = viewType;
      return true;
    }
    return false;
  }

  private EditBaseController.SimpleEditorActionListener onEditorActionListener;

  private InputFilter[] inputFilter;

  public int getTextColorId (@ColorId int defColorId) {
    return textColorId != 0 ? textColorId : defColorId;
  }

  /*public int getTextColor (@ThemedColorIdRes int defColorId) {
    return TGTheme.getColor(getTextColorId(defColorId));
  }*/

  public ListItem setTextColorId (@PorterDuffColorId int colorId) {
    this.textColorId = colorId;
    return this;
  }

  public TdlibAccentColor getAccentColor () {
    return accentColor;
  }

  public ListItem setAccentColor (TdlibAccentColor accentColor) {
    this.accentColor = accentColor;
    return this;
  }

  public ListItem setIntValue (int value) {
    this.intValue = value;
    return this;
  }

  public ListItem setDoubleValue (double value) {
    return setLongValue(Double.doubleToLongBits(value));
  }

  public double getDoubleValue () {
    return Double.longBitsToDouble(getLongValue());
  }

  public ListItem setLongValue (long value) {
    this.longValue = value;
    return this;
  }

  public long getLongValue () {
    return longValue;
  }

  public ListItem setRadioColorId (@ColorId int colorId) {
    this.radioColorId = colorId;
    return this;
  }

  @ColorId
  public int getRadioColorId () {
    return radioColorId;
  }

  public int getIntValue () {
    return intValue;
  }

  public ListItem setBoolValue (boolean value) {
    this.flags = BitwiseUtils.setFlag(this.flags, FLAG_BOOL_VALUE, value);
    return this;
  }

  public boolean getBoolValue () {
    return (flags & FLAG_BOOL_VALUE) != 0;
  }

  public ListItem setOnEditorActionListener (EditBaseController.SimpleEditorActionListener listener) {
    this.onEditorActionListener = listener;
    return this;
  }

  public EditBaseController.SimpleEditorActionListener getOnEditorActionListener () {
    return onEditorActionListener;
  }

  public ListItem setTextPaddingLeft (int paddingLeft) {
    this.textPaddingLeft = paddingLeft;
    return this;
  }

  public ListItem setData (Object data) {
    this.data = data;
    return this;
  }

  public ListItem setHeight (int height) {
    this.height = height;
    return this;
  }

  public int getHeight () {
    return height;
  }

  public Object getData () {
    return data;
  }

  public ListItem setInputFilters (InputFilter[] inputFilter) {
    this.inputFilter = inputFilter;
    return this;
  }

  public ListItem setStringKey (String key) {
    this.stringKey = key;
    return this;
  }

  public int getTextPaddingLeft () {
    return textPaddingLeft;
  }

  public ListItem setStringValue (String value) {
    this.stringValue = value;
    return this;
  }

  public boolean setStringValueIfChanged (String value) {
    if (!StringUtils.equalsOrBothEmpty(this.stringValue, value)) {
      this.stringValue = value;
      return true;
    }
    return false;
  }

  public String getStringValue () {
    return stringValue;
  }

  public @Nullable String getStringCheckResult () {
    return stringKey;
  }

  public @Nullable InputFilter[] getInputFilters () {
    return inputFilter;
  }

  public ListItem setLongId (long longId) {
    this.longId = longId;
    return this;
  }

  public ListItem setSliderInfo (String[] sliderValues, int currentValue) {
    this.sliderValues = sliderValues;
    this.sliderValue = currentValue;
    return this;
  }

  public ListItem setDrawModifier (DrawModifier modifier) {
    this.drawModifier = modifier;
    return this;
  }

  @Nullable
  public DrawModifier getDrawModifier () {
    return drawModifier;
  }

  @Nullable
  public String[] getSliderValues () {
    return sliderValues;
  }

  public int getSliderValue () {
    return sliderValue;
  }

  public void setSliderValueIndex (int index) {
    this.sliderValue = index;
  }

  public long getLongId () {
    return longId;
  }

  public ListItem setSelected (boolean isSelected) {
    this.flags = BitwiseUtils.setFlag(this.flags, FLAG_SELECTED, isSelected);
    return this;
  }

  public ListItem setHighlightValue (String highlight) {
    this.highlight = highlight;
    return this;
  }

  public int decrementSelectionIndex () {
    if ((flags & FLAG_USE_SELECTION_INDEX) != 0) {
      intValue--;
    }
    return intValue;
  }

  public void setSelected (boolean isSelected, int selectionIndex) {
    int flags = this.flags;
    flags |= FLAG_USE_SELECTION_INDEX;
    flags = BitwiseUtils.setFlag(flags, FLAG_SELECTED, isSelected);
    this.flags = flags;
    this.intValue = selectionIndex;
  }

  public int getSelectionIndex () {
    return (flags & FLAG_USE_SELECTION_INDEX) != 0 ? intValue : -1;
  }

  public boolean isSelected () {
    return (flags & FLAG_SELECTED) != 0;
  }

  public @IdRes int getCheckId () {
    return checkId;
  }

  public @IdRes int getId () {
    return id;
  }

  public int getViewType () {
    return viewType;
  }

  public int getIconResource () {
    return iconResource;
  }

  public boolean setStringIfChanged (@NonNull CharSequence string) {
    if (!StringUtils.equalsOrBothEmpty(this.string, string)) {
      boolean changed = stringResource == 0 || !StringUtils.equalsOrBothEmpty(getString(), string);
      this.string = string;
      this.stringResource = 0;
      return changed;
    }
    return false;
  }

  public boolean setIconRes (@DrawableRes int iconRes) {
    if (this.iconResource != iconRes) {
      this.iconResource = iconRes;
      return true;
    }
    return false;
  }

  public boolean setStringIfChanged (int res) {
    if (this.stringResource != res) {
      boolean changed = !StringUtils.equalsOrBothEmpty(getString(), res != 0 ? Lang.getString(res) : this.string);
      this.stringResource = res;
      this.string = null;
      return changed;
    }
    return false;
  }

  public void setString (@NonNull CharSequence string) {
    this.stringResource = 0;
    this.string = string;
  }

  public void setString (int resource) {
    this.stringResource = resource;
    this.string = null;
  }

  public void setRecyclerPosition (int firstVisiblePosition, int offsetInPixels) {
    this.firstVisiblePosition = firstVisiblePosition;
    this.offsetInPixels = offsetInPixels;
  }

  public int getFirstVisiblePosition () {
    return firstVisiblePosition;
  }

  public int getOffsetInPixels () {
    return offsetInPixels;
  }

  public CharSequence getString () {
    return stringResource != 0 ? Lang.getString(stringResource) : string;
  }

  public int getStringResource () {
    return stringResource;
  }

  public String getHighlightValue () {
    return highlight;
  }

  private int[] stringResources;

  public boolean hasStringResources () {
    return stringResource != 0 || stringResources != null;
  }

  public boolean hasStringResource (@StringRes int resId) {
    if (stringResource == resId)
      return true;
    if (stringResources != null)
      return ArrayUtils.indexOf(stringResources, resId) >= 0;
    return false;
  }

  public ListItem setContentStrings (@StringRes int... resIds) {
    this.stringResources = resIds;
    return this;
  }
}
