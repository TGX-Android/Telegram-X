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
 * File created on 18/11/2016
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.FillingDrawable;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.charts.data.ChartDataUtil;
import org.thunderdog.challegram.charts.view_data.ChartHeaderView;
import org.thunderdog.challegram.component.RelativeSessionLayout;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.attach.MediaLocationPlaceView;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.base.TogglerView;
import org.thunderdog.challegram.component.chat.DetachedChatHeaderView;
import org.thunderdog.challegram.component.chat.MessagePreviewView;
import org.thunderdog.challegram.component.inline.CustomResultView;
import org.thunderdog.challegram.component.sharedmedia.MediaSmallView;
import org.thunderdog.challegram.component.user.UserView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.mediaview.paint.ColorPaletteView;
import org.thunderdog.challegram.mediaview.paint.widget.ColorPreviewView;
import org.thunderdog.challegram.mediaview.paint.widget.ColorToneView;
import org.thunderdog.challegram.navigation.DrawerItemView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.AttachDelegate;
import org.thunderdog.challegram.widget.AvatarView;
import org.thunderdog.challegram.widget.BetterChatView;
import org.thunderdog.challegram.widget.ChartLayout;
import org.thunderdog.challegram.widget.CheckBoxView;
import org.thunderdog.challegram.widget.CustomTextView;
import org.thunderdog.challegram.widget.DoubleTextView;
import org.thunderdog.challegram.widget.DoubleTextViewWithIcon;
import org.thunderdog.challegram.widget.EmbeddableStickerView;
import org.thunderdog.challegram.widget.EmptySmartView;
import org.thunderdog.challegram.widget.JoinedUsersView;
import org.thunderdog.challegram.widget.ListInfoView;
import org.thunderdog.challegram.widget.LiveLocationView;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.NonMaterialButton;
import org.thunderdog.challegram.widget.PageBlockView;
import org.thunderdog.challegram.widget.PageBlockWrapView;
import org.thunderdog.challegram.widget.ProgressComponentView;
import org.thunderdog.challegram.widget.RadioView;
import org.thunderdog.challegram.widget.ReactionCheckboxSettingsView;
import org.thunderdog.challegram.widget.ScalableTextView;
import org.thunderdog.challegram.widget.ScoutFrameLayout;
import org.thunderdog.challegram.widget.SeparatorView;
import org.thunderdog.challegram.widget.SettingStupidView;
import org.thunderdog.challegram.widget.ShadowView;
import org.thunderdog.challegram.widget.SliderWrapView;
import org.thunderdog.challegram.widget.SmallChatView;
import org.thunderdog.challegram.widget.TimerView;
import org.thunderdog.challegram.widget.VerticalChatView;
import org.thunderdog.challegram.widget.WebsiteEmptyImageView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.text.AcceptFilter;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.Destroyable;

public class SettingHolder extends RecyclerView.ViewHolder {

  public SettingHolder (View itemView) {
    super(itemView);
  }

  public static int measureHeightForType (ListItem item) {
    final int viewType = item.getViewType();
    switch (viewType) {
      case ListItem.TYPE_CUSTOM_INLINE: {
        Object data = item.getData();
        return data != null && data instanceof InlineResult ? ((InlineResult<?>) data).getHeight() : 0;
      }
      case ListItem.TYPE_PADDING: {
        return item.getHeight();
      }
    }
    return measureHeightForType(viewType);
  }

  public static int measureHeightForType (int viewType) {
    switch (viewType) {
      case ListItem.TYPE_EMPTY_OFFSET: { // 0 OK
        return Screen.dp(12f) + Size.getMaximumHeaderSizeDifference();
      }
      case ListItem.TYPE_INFO_SETTING:
      case ListItem.TYPE_VALUED_SETTING:
      case ListItem.TYPE_VALUED_SETTING_WITH_RADIO: { // 6, 5 OK
        return Screen.dp(76f);
      }
      case ListItem.TYPE_EDITTEXT_POLL_OPTION_ADD: {
        return Screen.dp(57f);
      }
      case ListItem.TYPE_VALUED_SETTING_COMPACT:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_COLOR:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_RADIO:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_RADIO_2:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_CHECKBOX: {
        return Screen.dp(64f);
      }
      case ListItem.TYPE_SHADOW_TOP: { // 2 OK
        return Screen.dp(6f);
      }
      case ListItem.TYPE_SHADOW_BOTTOM: { // 3 OK
        return Screen.dp(6f);
      }
      case ListItem.TYPE_MEMBERS_LIST: {
        return Screen.dp(95f);
      }
      case ListItem.TYPE_FAKE_PAGER_TOPVIEW: {
        return Screen.dp(48f);
      }
      case ListItem.TYPE_LIST_INFO_VIEW: {
        return Screen.dp(42f);
      }

      // unchecked
      case ListItem.TYPE_SEPARATOR_FULL:
      case ListItem.TYPE_SEPARATOR: {
        return Screen.dp(1f);
      }
      case ListItem.TYPE_BUTTON: {
        return Screen.dp(57f);
      }
      case ListItem.TYPE_DRAWER_ITEM:
      case ListItem.TYPE_DRAWER_ITEM_WITH_RADIO:
      case ListItem.TYPE_DRAWER_ITEM_WITH_RADIO_SEPARATED:
      case ListItem.TYPE_DRAWER_ITEM_WITH_AVATAR:
      case ListItem.TYPE_LIVE_LOCATION_TARGET: {
        return Screen.dp(52f);
      }
      case ListItem.TYPE_SETTING:
      case ListItem.TYPE_RADIO_SETTING:
      case ListItem.TYPE_RADIO_SETTING_WITH_NEGATIVE_STATE:
      case ListItem.TYPE_CHECKBOX_OPTION:
      case ListItem.TYPE_CHECKBOX_OPTION_MULTILINE:
      case ListItem.TYPE_CHECKBOX_OPTION_REVERSE:
      case ListItem.TYPE_CHECKBOX_OPTION_WITH_AVATAR:
      case ListItem.TYPE_RADIO_OPTION:
      case ListItem.TYPE_RADIO_OPTION_LEFT:
      case ListItem.TYPE_RADIO_OPTION_WITH_AVATAR:
      case ListItem.TYPE_SHADOWED_OFFSET: {
        return Screen.dp(55f);
      }
      case ListItem.TYPE_INFO_MULTILINE:
      case ListItem.TYPE_CHECKBOX_OPTION_DOUBLE_LINE: {
        return Screen.dp(76f);
      }
      case ListItem.TYPE_HEADER_PADDED: {
        return Screen.dp(32f) + Screen.dp(4f);
      }
      case ListItem.TYPE_HEADER:
      case ListItem.TYPE_HEADER_WITH_ACTION: {
        return Screen.dp(32f);
      }
      case ListItem.TYPE_SESSION:
      case ListItem.TYPE_SESSION_WITH_AVATAR: {
        return Screen.dp(112f);
      }
      case ListItem.TYPE_MESSAGE_PREVIEW: {
        return Screen.dp(51f); // FIXME?
      }
      case ListItem.TYPE_SLIDER: {
        return Screen.dp(56f);
      }
      case ListItem.TYPE_ATTACH_LOCATION: {
        return Screen.dp(56f);
      }
      case ListItem.TYPE_ATTACH_LOCATION_BIG: {
        return Screen.dp(64f);
      }
      case ListItem.TYPE_INFO: {
        return Screen.dp(64f);
      }
      case ListItem.TYPE_USER_SMALL: {
        return Screen.dp(63);
      }
      case ListItem.TYPE_CHAT_BETTER:
      case ListItem.TYPE_USER: {
        return Screen.dp(72f);
      }
      case ListItem.TYPE_RECYCLER_HORIZONTAL: {
        return Screen.dp(95f);
      }
      case ListItem.TYPE_EMPTY_OFFSET_SMALL: {
        return Screen.dp(4f);
      }
      case ListItem.TYPE_CHAT_SMALL:
      case ListItem.TYPE_CHAT_SMALL_SELECTABLE: {
        return Screen.dp(62f);
      }
      case ListItem.TYPE_EDITTEXT_WITH_PHOTO: {
        return Screen.dp(86f);
      }
      case ListItem.TYPE_REACTION_CHECKBOX: {
        return Screen.dp(60f);
      }
      case ListItem.TYPE_EMBED_STICKER: {
        return Screen.dp(96f);
      }
      case ListItem.TYPE_JOIN_REQUEST: {
        return Screen.dp(72f);
      }
      case ListItem.TYPE_CHAT_HEADER_LARGE: {
        return DetachedChatHeaderView.getViewHeight();
      }
      case ListItem.TYPE_EDITTEXT_WITH_PHOTO_SMALLER:
        return Screen.dp(82f);
      case ListItem.TYPE_LIVE_LOCATION_PROMO:
        return Screen.dp(132f);
      case ListItem.TYPE_COLOR_PICKER: {
        int toneHeight = Screen.dp(192f);
        int paletteHeight = Screen.dp(18f + 8f + 4f);
        int inputHeight = Screen.dp(60f + 2f);
        int barHeight = Screen.dp(42f);
        int marginBottom = Screen.dp(12f);
        return toneHeight + paletteHeight * 2 + inputHeight * 2 + barHeight + marginBottom;
      }
      default: {
        // FIXME: This can be used only by ThemeController
        if (viewType <= ListItem.TYPE_CUSTOM)
          return measureHeightForType(ListItem.TYPE_VALUED_SETTING_COMPACT);
      }
    }
    throw new AssertionError(viewType);
  }

  public void attach () {
    final int viewType = getItemViewType();
    switch (viewType) {
      case ListItem.TYPE_SESSION:
      case ListItem.TYPE_SESSION_WITH_AVATAR: {
        ((AttachDelegate) ((RelativeLayout) itemView).getChildAt(4)).attach();
        if (viewType == ListItem.TYPE_SESSION_WITH_AVATAR) {
          ((AttachDelegate) ((RelativeLayout) itemView).getChildAt(5)).attach();
        }
        break;
      }
      case ListItem.TYPE_JOIN_REQUEST: {
        ((DoubleTextViewWithIcon) itemView).attach();
        break;
      }
      case ListItem.TYPE_CHAT_HEADER_LARGE: {
        ((DetachedChatHeaderView) itemView).attach();
        break;
      }
      case ListItem.TYPE_STICKER_SET:
      case ListItem.TYPE_ARCHIVED_STICKER_SET:
      case ListItem.TYPE_DOUBLE_TEXTVIEW:
      case ListItem.TYPE_DOUBLE_TEXTVIEW_ROUNDED: {
        ((DoubleTextView) itemView).attach();
        break;
      }
      case ListItem.TYPE_REACTION_CHECKBOX: {
        ((ReactionCheckboxSettingsView) itemView).attach();
        break;
      }
      case ListItem.TYPE_EMBED_STICKER: {
        ((EmbeddableStickerView) itemView).attach();
        break;
      }
      case ListItem.TYPE_MEMBERS_LIST: {
        // TODO maybe aka ((RecyclerView) itemView).attach()
        break;
      }
      case ListItem.TYPE_SMALL_MEDIA: {
        ((MediaSmallView) itemView).attach();
        break;
      }
      case ListItem.TYPE_CUSTOM_INLINE: {
        ((CustomResultView) itemView).attach();
        break;
      }
      case ListItem.TYPE_SMART_PROGRESS: {
        ((ProgressComponentView) itemView).attach();
        break;
      }
      case ListItem.TYPE_PAGE_BLOCK_MEDIA:
      case ListItem.TYPE_PAGE_BLOCK_GIF:
      case ListItem.TYPE_PAGE_BLOCK_COLLAGE:
      case ListItem.TYPE_PAGE_BLOCK_AVATAR: {
        ((PageBlockView) itemView).attach();
        break;
      }
      case ListItem.TYPE_CHATS_PLACEHOLDER: {
        ((JoinedUsersView) ((ViewGroup) ((ViewGroup) itemView).getChildAt(0)).getChildAt(0)).attach();
        break;
      }
      case ListItem.TYPE_PAGE_BLOCK_SLIDESHOW:
      case ListItem.TYPE_PAGE_BLOCK_EMBEDDED:
      case ListItem.TYPE_PAGE_BLOCK_VIDEO: {
        ((PageBlockWrapView) itemView).attach();
        break;
      }
      case ListItem.TYPE_CHAT_BETTER: {
        ((BetterChatView) itemView).attach();
        break;
      }
      case ListItem.TYPE_LIVE_LOCATION_TARGET: {
        ((DrawerItemView) ((FrameLayoutFix) itemView).getChildAt(0)).attach();
        break;
      }
      default: {
        if (itemView instanceof AttachDelegate) {
          ((AttachDelegate) itemView).attach();
        }
        break;
      }
    }
  }

  public void detach () {
    final int viewType = getItemViewType();
    switch (viewType) {
      case ListItem.TYPE_SESSION:
      case ListItem.TYPE_SESSION_WITH_AVATAR: {
        ((AttachDelegate) ((RelativeLayout) itemView).getChildAt(4)).detach();
        if (viewType == ListItem.TYPE_SESSION_WITH_AVATAR) {
          ((AttachDelegate) ((RelativeLayout) itemView).getChildAt(5)).detach();
        }
        break;
      }
      case ListItem.TYPE_JOIN_REQUEST: {
        ((DoubleTextViewWithIcon) itemView).detach();
        break;
      }
      case ListItem.TYPE_CHAT_HEADER_LARGE: {
        ((DetachedChatHeaderView) itemView).detach();
        break;
      }
      case ListItem.TYPE_STICKER_SET:
      case ListItem.TYPE_ARCHIVED_STICKER_SET:
      case ListItem.TYPE_DOUBLE_TEXTVIEW:
      case ListItem.TYPE_DOUBLE_TEXTVIEW_ROUNDED: {
        ((DoubleTextView) itemView).detach();
        break;
      }
      case ListItem.TYPE_LIVE_LOCATION_TARGET: {
        ((DrawerItemView) ((FrameLayoutFix) itemView).getChildAt(0)).detach();
        break;
      }
      case ListItem.TYPE_REACTION_CHECKBOX: {
        ((ReactionCheckboxSettingsView) itemView).detach();
        break;
      }
      case ListItem.TYPE_EMBED_STICKER: {
        ((EmbeddableStickerView) itemView).detach();
        break;
      }
      case ListItem.TYPE_MEMBERS_LIST: {
        // TODO maybe aka ((RecyclerView) itemView).detach()
        break;
      }
      case ListItem.TYPE_SMART_PROGRESS: {
        ((ProgressComponentView) itemView).detach();
        break;
      }
      case ListItem.TYPE_SMALL_MEDIA: {
        ((MediaSmallView) itemView).detach();
        break;
      }
      case ListItem.TYPE_CUSTOM_INLINE: {
        ((CustomResultView) itemView).detach();
        break;
      }
      case ListItem.TYPE_PAGE_BLOCK_MEDIA:
      case ListItem.TYPE_PAGE_BLOCK_GIF:
      case ListItem.TYPE_PAGE_BLOCK_COLLAGE:
      case ListItem.TYPE_PAGE_BLOCK_AVATAR: {
        ((PageBlockView) itemView).detach();
        break;
      }
      case ListItem.TYPE_CHATS_PLACEHOLDER: {
        ((JoinedUsersView) ((ViewGroup) ((ViewGroup) itemView).getChildAt(0)).getChildAt(0)).detach();
        break;
      }
      case ListItem.TYPE_PAGE_BLOCK_SLIDESHOW:
      case ListItem.TYPE_PAGE_BLOCK_EMBEDDED:
      case ListItem.TYPE_PAGE_BLOCK_VIDEO: {
        ((PageBlockWrapView) itemView).detach();
        break;
      }
      case ListItem.TYPE_CHAT_BETTER: {
        ((BetterChatView) itemView).detach();
        break;
      }
      default: {
        if (itemView instanceof AttachDelegate) {
          ((AttachDelegate) itemView).detach();
        }
        break;
      }
    }
  }

  public void destroy () {
    final int viewType = getItemViewType();
    switch (viewType) {
      case ListItem.TYPE_SESSION:
      case ListItem.TYPE_SESSION_WITH_AVATAR: {
        ((Destroyable) ((RelativeLayout) itemView).getChildAt(4)).performDestroy();
        if (viewType == ListItem.TYPE_SESSION_WITH_AVATAR) {
          ((Destroyable) ((RelativeLayout) itemView).getChildAt(5)).performDestroy();
        }
        break;
      }
      default: {
        if (itemView instanceof Destroyable) {
          ((Destroyable) itemView).performDestroy();
        }
        break;
      }
    }
  }

  public static boolean isValuedType (int viewType) {
    switch (viewType) {
      case ListItem.TYPE_DRAWER_ITEM:
      case ListItem.TYPE_DRAWER_ITEM_WITH_RADIO:
      case ListItem.TYPE_DRAWER_ITEM_WITH_RADIO_SEPARATED:
      case ListItem.TYPE_SETTING:
      case ListItem.TYPE_CHECKBOX_OPTION:
      case ListItem.TYPE_CHECKBOX_OPTION_REVERSE:
      case ListItem.TYPE_CHECKBOX_OPTION_WITH_AVATAR:
      case ListItem.TYPE_CHECKBOX_OPTION_DOUBLE_LINE:
      case ListItem.TYPE_CHECKBOX_OPTION_MULTILINE:
      case ListItem.TYPE_REACTION_CHECKBOX:
      case ListItem.TYPE_RADIO_OPTION:
      case ListItem.TYPE_RADIO_OPTION_LEFT:
      case ListItem.TYPE_RADIO_OPTION_WITH_AVATAR:
      case ListItem.TYPE_VALUED_SETTING:
      case ListItem.TYPE_VALUED_SETTING_COMPACT:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_COLOR:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_RADIO:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_RADIO_2:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_CHECKBOX:
      case ListItem.TYPE_VALUED_SETTING_WITH_RADIO:
      case ListItem.TYPE_INFO_MULTILINE:
      case ListItem.TYPE_INFO_SETTING:
      case ListItem.TYPE_VALUED_SETTING_RED_STUPID:
      case ListItem.TYPE_RADIO_SETTING:
      case ListItem.TYPE_RADIO_SETTING_WITH_NEGATIVE_STATE: {
        return true;
      }
    }
    return false;
  }

  public static class PaddingView extends View {
    public PaddingView (Context context) {
      super(context);
    }

    private ListItem item;

    private int getDesiredHeight () {
      return item != null ? item.getHeight() : 0;
    }

    public void setItem (ListItem item) {
      boolean neededFilling = this.item != null && this.item.getBoolValue();
      this.item = item;
      if (getMeasuredHeight() != getDesiredHeight()) {
        requestLayout();
      }
      boolean needFilling = item != null && item.getBoolValue();
      if (neededFilling != needFilling) {
        invalidate();
      }
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
        MeasureSpec.makeMeasureSpec(getDesiredHeight(), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw (Canvas c) {
      if (item != null && item.getBoolValue()) {
        c.drawColor(Theme.fillingColor());
      }
    }
  }

  /*public static SettingHolder create (final Context context, final int viewType, final @Nullable ViewController themeProvider, RecyclerView.OnScrollListener innerOnScrollListener) {
    return create(context, viewType, null, null, null, null, null, null, null, themeProvider, innerOnScrollListener, );
  }*/

  public static SettingHolder create (final Context context, final Tdlib tdlib, final int viewType,
                                      final SettingsAdapter adapter,
                                      final View.OnClickListener onClickListener,
                                      final View.OnLongClickListener onLongClickListener,
                                      final @Nullable ViewController<?> themeProvider,
                                      final RecyclerView.OnScrollListener innerOnScrollListener,
                                      final ClickHelper.Delegate clickDelegate) {
    switch (viewType) {
      case ListItem.TYPE_EMPTY_OFFSET:
      case ListItem.TYPE_EMPTY_OFFSET_SMALL:
      case ListItem.TYPE_DRAWER_OFFSET:
      case ListItem.TYPE_EMPTY_OFFSET_NO_HEAD:
      case ListItem.TYPE_DRAWER_EMPTY: {
        View view = new View(context);
        switch (viewType) {
          case ListItem.TYPE_EMPTY_OFFSET_SMALL: {
            ViewSupport.setThemedBackground(view, ColorId.background, themeProvider);
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(4f)));
            break;
          }
          case ListItem.TYPE_DRAWER_OFFSET: {
            ViewSupport.setThemedBackground(view, ColorId.filling, themeProvider);
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Size.getHeaderDrawerSize()));
            break;
          }
          case ListItem.TYPE_EMPTY_OFFSET_NO_HEAD: {
            ViewSupport.setThemedBackground(view, ColorId.filling, themeProvider);
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(12f)));
            break;
          }
          case ListItem.TYPE_EMPTY_OFFSET: {
            ViewSupport.setThemedBackground(view, ColorId.filling, themeProvider);
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(12f) + Size.getMaximumHeaderSizeDifference()));
            break;
          }
          case ListItem.TYPE_DRAWER_EMPTY: {
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(6f)));
            break;
          }
        }
        return new SettingHolder(view);
      }
      case ListItem.TYPE_TEXT_VIEW: {
        CustomTextView customTextView = new CustomTextView(context, tdlib);
        return new SettingHolder(customTextView);
      }
      case ListItem.TYPE_PADDING: {
        PaddingView paddingView = new PaddingView(context);
        paddingView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new SettingHolder(paddingView);
      }
      case ListItem.TYPE_SEPARATOR_FULL:
      case ListItem.TYPE_SEPARATOR: {
        int height = Math.max(1, Screen.dp(.5f));
        SeparatorView view = new SeparatorView(context);
        if (themeProvider != null) {
          themeProvider.addThemeInvalidateListener(view);
        }
        view.setNoAlign();
        view.setUseFilling();
        view.setSeparatorHeight(height);
        if (viewType == ListItem.TYPE_SEPARATOR) {
          view.setOffsets(Screen.dp(72f), 0);
        }
        view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(1f)));
        return new SettingHolder(view);
      }
      case ListItem.TYPE_SHADOW_TOP: {
        ShadowView shadowView = new ShadowView(context);
        if (themeProvider != null) {
          themeProvider.addThemeInvalidateListener(shadowView);
        }
        shadowView.setSimpleTopShadow(true);
        shadowView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(6f)));
        return new SettingHolder(shadowView);
      }
      case ListItem.TYPE_SLIDER:
      case ListItem.TYPE_SLIDER_BRIGHTNESS: {
        SliderWrapView wrapView = new SliderWrapView(context);
        switch (viewType) {
          case ListItem.TYPE_SLIDER:
            wrapView.initWithName();
            break;
          case ListItem.TYPE_SLIDER_BRIGHTNESS:
            wrapView.initWithBrightnessIcons();
            break;
        }

        wrapView.setRealTimeChangeListener(adapter.getSliderChangeListener());
        ViewSupport.setThemedBackground(wrapView, ColorId.filling, themeProvider);
        wrapView.addThemeListeners(themeProvider);
        wrapView.setCallback(adapter);
        return new SettingHolder(wrapView);
      }
      case ListItem.TYPE_MESSAGE_PREVIEW: {
        MessagePreviewView view = new MessagePreviewView(context, tdlib);
        view.setOnClickListener(onClickListener);
        if (themeProvider != null) {
          themeProvider.addThemeInvalidateListener(view);
        }
        return new SettingHolder(view);
      }
      case ListItem.TYPE_STATS_MESSAGE_PREVIEW: {
        MessagePreviewView view = new MessagePreviewView(context, tdlib);
        view.setLinePadding(0);
        view.setUseAvatarFallback(true);
        view.setOnClickListener(onClickListener);
        if (themeProvider != null) {
          themeProvider.addThemeInvalidateListener(view);
        }
        return new SettingHolder(view);
      }
      case ListItem.TYPE_SHADOWED_OFFSET: {
        FrameLayoutFix frameLayout = new FrameLayoutFix(context) {
          @Override
          protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
            int height = Screen.currentHeight() / 2 + Screen.dp(56f);
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
          }
        };
        frameLayout.setEnabled(false);

        ShadowView shadowView = new ShadowView(context);
        if (themeProvider != null) {
          themeProvider.addThemeInvalidateListener(shadowView);
        }
        shadowView.setSimpleTopShadow(true);
        shadowView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(6f), Gravity.BOTTOM));
        frameLayout.addView(shadowView);

        return new SettingHolder(frameLayout);
      }
      case ListItem.TYPE_ATTACH_LOCATION:
      case ListItem.TYPE_ATTACH_LOCATION_BIG: {
        MediaLocationPlaceView view = new MediaLocationPlaceView(context);
        view.init(themeProvider, viewType == ListItem.TYPE_ATTACH_LOCATION_BIG);
        view.setOnClickListener(onClickListener);
        return new SettingHolder(view);
      }
      case ListItem.TYPE_SHADOW_BOTTOM: {
        ShadowView shadowView = new ShadowView(context);
        if (themeProvider != null) {
          themeProvider.addThemeInvalidateListener(shadowView);
        }
        shadowView.setSimpleBottomTransparentShadow(false);
        shadowView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(6f)));
        return new SettingHolder(shadowView);
      }
      case ListItem.TYPE_DRAWER_ITEM:
      case ListItem.TYPE_DRAWER_ITEM_WITH_RADIO:
      case ListItem.TYPE_DRAWER_ITEM_WITH_RADIO_SEPARATED:
      case ListItem.TYPE_DRAWER_ITEM_WITH_AVATAR:
      case ListItem.TYPE_LIVE_LOCATION_TARGET: {
        DrawerItemView item = new DrawerItemView(context, tdlib);
        switch (viewType) {
          case ListItem.TYPE_LIVE_LOCATION_TARGET:
          case ListItem.TYPE_DRAWER_ITEM_WITH_AVATAR:
            item.addAvatar();
            break;
          default:
            item.setIcon(Screen.dp(18f), Screen.dp(13.5f), R.drawable.baseline_settings_24);
            break;
        }
        if (themeProvider != null) {
          themeProvider.addThemeInvalidateListener(item);
        }
        item.setItemHeight(Screen.dp(52f));

        switch (viewType) {
          case ListItem.TYPE_DRAWER_ITEM_WITH_RADIO:
          case ListItem.TYPE_DRAWER_ITEM_WITH_RADIO_SEPARATED:
          case ListItem.TYPE_LIVE_LOCATION_TARGET: {
            FrameLayoutFix wrap = new FrameLayoutFix(context);
            wrap.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(52f)));
            wrap.addView(item);

            switch (viewType) {
              case ListItem.TYPE_DRAWER_ITEM_WITH_RADIO:
              case ListItem.TYPE_DRAWER_ITEM_WITH_RADIO_SEPARATED: {
                Views.setClickable(wrap);
                RippleSupport.setTransparentSelector(wrap);
                item.setEnabled(false);
                wrap.setOnClickListener(onClickListener);

                TogglerView togglerView = new TogglerView(context);
                togglerView.init(false);
                if (viewType == ListItem.TYPE_DRAWER_ITEM_WITH_RADIO_SEPARATED) {
                  togglerView.setOnClickListener(onClickListener);
                }
                FrameLayout.LayoutParams params = FrameLayoutFix.newParams(Screen.dp(74f), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_VERTICAL | (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT));
                // params.rightMargin = Screen.dp(10f);
                togglerView.setLayoutParams(params);
                wrap.addView(togglerView);
                if (themeProvider != null) {
                  themeProvider.addThemeInvalidateListener(togglerView);
                }
                break;
              }
              case ListItem.TYPE_LIVE_LOCATION_TARGET: {
                RippleSupport.setSimpleWhiteBackground(item, themeProvider);
                Views.setClickable(item);
                item.setOnClickListener(onClickListener);
                item.setOnLongClickListener(onLongClickListener);

                FrameLayout.LayoutParams params = FrameLayoutFix.newParams(Screen.dp(26f), Screen.dp(26f), Gravity.CENTER_VERTICAL | Gravity.RIGHT);
                params.rightMargin = Screen.dp(10f);
                TimerView timerView = new TimerView(context);
                timerView.setTextColor(Theme.progressColor());
                timerView.setLayoutParams(params);
                wrap.addView(timerView);
                if (themeProvider != null) {
                  themeProvider.addThemeTextColorListener(timerView, ColorId.progress);
                }
                break;
              }
            }
            return new SettingHolder(wrap);
          }
        }

        Views.setClickable(item);
        RippleSupport.setTransparentSelector(item);
        item.setOnClickListener(onClickListener);
        item.setOnLongClickListener(onLongClickListener);
        return new SettingHolder(item);
      }
      case ListItem.TYPE_SETTING:
      case ListItem.TYPE_CHECKBOX_OPTION:
      case ListItem.TYPE_CHECKBOX_OPTION_REVERSE:
      case ListItem.TYPE_CHECKBOX_OPTION_MULTILINE:
      case ListItem.TYPE_RADIO_OPTION:
      case ListItem.TYPE_RADIO_OPTION_LEFT:
      case ListItem.TYPE_RADIO_OPTION_WITH_AVATAR:
      case ListItem.TYPE_CHECKBOX_OPTION_WITH_AVATAR: {
        SettingView settingView = new SettingView(context, tdlib);
        settingView.setType(SettingView.TYPE_SETTING);
        settingView.setOnClickListener(onClickListener);
        settingView.setOnLongClickListener(onLongClickListener);
        if (themeProvider != null) {
          themeProvider.addThemeInvalidateListener(settingView);
        }
        boolean needAvatar = false;
        int paddingLeft = 0, paddingRight = 0;
        switch (viewType) {
          case ListItem.TYPE_CHECKBOX_OPTION:
          case ListItem.TYPE_CHECKBOX_OPTION_REVERSE:
          case ListItem.TYPE_CHECKBOX_OPTION_WITH_AVATAR:
          case ListItem.TYPE_CHECKBOX_OPTION_MULTILINE: {
            paddingRight = Screen.dp(32f);
            CheckBoxView checkBox = CheckBoxView.simpleCheckBox(context, (viewType == ListItem.TYPE_CHECKBOX_OPTION_REVERSE) != Lang.rtl());
            settingView.addView(checkBox);
            if (themeProvider != null) {
              themeProvider.addThemeInvalidateListener(checkBox);
            }
            if (viewType == ListItem.TYPE_CHECKBOX_OPTION_WITH_AVATAR) {
              needAvatar = true;
            } else if (viewType == ListItem.TYPE_CHECKBOX_OPTION_REVERSE) {
              paddingLeft = Screen.dp(60f);
              paddingRight = 0; // Screen.dp(8f);
            }
            break;
          }
          case ListItem.TYPE_RADIO_OPTION_LEFT: {
            paddingLeft = Screen.dp(32f);
            RadioView radioView = RadioView.simpleRadioView(context, true);
            settingView.addView(radioView);
            if (themeProvider != null) {
              themeProvider.addThemeInvalidateListener(radioView);
            }
            break;
          }
          case ListItem.TYPE_RADIO_OPTION:
          case ListItem.TYPE_RADIO_OPTION_WITH_AVATAR: {
            paddingRight = Screen.dp(32f);
            RadioView radioView = RadioView.simpleRadioView(context);
            settingView.addView(radioView);
            if (themeProvider != null) {
              themeProvider.addThemeInvalidateListener(radioView);
            }
            if (viewType == ListItem.TYPE_RADIO_OPTION_WITH_AVATAR) {
              needAvatar = true;
            }
            break;
          }
        }
        if (needAvatar) {
          FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(Screen.dp(32f), Screen.dp(32f), Gravity.CENTER_VERTICAL | Gravity.LEFT);
          params.leftMargin = Screen.dp(13f);
          AvatarView avatarView = new AvatarView(context);
          avatarView.setLayoutParams(params);
          settingView.addView(avatarView);
          paddingLeft = Screen.dp(60f);
        }
        settingView.forcePadding(paddingLeft, paddingRight);
        return new SettingHolder(settingView);
      }
      case ListItem.TYPE_EMPTY: {
        TextView textView = new NoScrollTextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
        textView.setTypeface(Fonts.getRobotoRegular());
        textView.setPadding(Screen.dp(16f), Screen.dp(16f), Screen.dp(16f), Screen.dp(16f));
        textView.setGravity(Gravity.CENTER);
        textView.setTextColor(Theme.textDecent2Color());
        if (themeProvider != null) {
          themeProvider.addThemeTextColorListener(textView, ColorId.background_textLight);
        }
        textView.setOnClickListener(onClickListener);
        textView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return new SettingHolder(textView);
      }
      case ListItem.TYPE_ZERO_VIEW: {
        View view = new View(context);
        view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0));
        return new SettingHolder(view);
      }
      case ListItem.TYPE_CHAT_HEADER_LARGE: {
        DetachedChatHeaderView view = new DetachedChatHeaderView(context);
        view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, DetachedChatHeaderView.getViewHeight()));
        return new SettingHolder(view);
      }
      case ListItem.TYPE_JOIN_REQUEST: {
        DoubleTextViewWithIcon viewGroup = new DoubleTextViewWithIcon(context);
        viewGroup.setOnClickListener(onClickListener);
        viewGroup.addThemeListeners(themeProvider);
        viewGroup.text().setIsRounded(true);
        return new SettingHolder(viewGroup);
      }
      case ListItem.TYPE_STICKER_SET:
      case ListItem.TYPE_ARCHIVED_STICKER_SET:
      case ListItem.TYPE_DOUBLE_TEXTVIEW:
      case ListItem.TYPE_DOUBLE_TEXTVIEW_ROUNDED: {
        DoubleTextView viewGroup = new DoubleTextView(context);
        viewGroup.setOnClickListener(onClickListener);
        switch (viewType) {
          case ListItem.TYPE_ARCHIVED_STICKER_SET: {
            viewGroup.setButton(R.string.Add, onClickListener);
            break;
          }
          case ListItem.TYPE_DOUBLE_TEXTVIEW_ROUNDED: {
            viewGroup.setIsRounded(true);
            break;
          }
        }
        Views.setClickable(viewGroup);
        RippleSupport.setSimpleWhiteBackground(viewGroup);
        viewGroup.addThemeListeners(themeProvider);
        return new SettingHolder(viewGroup);
      }
      case ListItem.TYPE_VALUED_SETTING:
      case ListItem.TYPE_VALUED_SETTING_WITH_RADIO:
      case ListItem.TYPE_VALUED_SETTING_COMPACT:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_COLOR:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_RADIO:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_RADIO_2:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_CHECKBOX:
      case ListItem.TYPE_CHECKBOX_OPTION_DOUBLE_LINE: {
        SettingView settingView = new SettingView(context, tdlib);
        switch (viewType) {
          case ListItem.TYPE_VALUED_SETTING_COMPACT:
          case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_COLOR:
          case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_RADIO:
          case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_RADIO_2:
          case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER:
          case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_CHECKBOX:
            settingView.setType(SettingView.TYPE_INFO_COMPACT);
            break;
          default:
            settingView.setType(SettingView.TYPE_INFO);
            break;
        }
        settingView.setSwapDataAndName();
        settingView.setOnClickListener(onClickListener);
        settingView.setOnLongClickListener(onLongClickListener);
        switch (viewType) {
          case ListItem.TYPE_CHECKBOX_OPTION_DOUBLE_LINE: {
            CheckBoxView checkBox = CheckBoxView.simpleCheckBox(context);
            settingView.addView(checkBox);
            if (themeProvider != null) {
              themeProvider.addThemeInvalidateListener(checkBox);
            }
            break;
          }
          case ListItem.TYPE_VALUED_SETTING_WITH_RADIO: {
            RadioView radioView = RadioView.simpleRadioView(context, Lang.rtl());
            settingView.addView(radioView);
            if (themeProvider != null) {
              themeProvider.addThemeInvalidateListener(radioView);
            }
            break;
          }
          case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_RADIO:
          case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_RADIO_2: {
            RadioView radioView = RadioView.simpleRadioView(context,  !Lang.rtl());
            settingView.addView(radioView);
            if (themeProvider != null) {
              themeProvider.addThemeInvalidateListener(radioView);
            }
            if (viewType == ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_RADIO) {
              settingView.forcePadding(Screen.dp(58f), 0);
            }
            break;
          }
          case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_COLOR: {
            FrameLayout.LayoutParams params = FrameLayoutFix.newParams(Screen.dp(48f), Screen.dp(48f), Gravity.CENTER_VERTICAL | (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT));
            params.leftMargin = params.rightMargin = Screen.dp(6f);

            ColorPreviewView view = new ColorPreviewView(context);
            view.reset(false);
            view.setLayoutParams(params);
            settingView.addView(view);
            settingView.forcePadding(0, Screen.dp(58f));
            break;
          }
          case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER: {
            settingView.addToggler();
            break;
          }
          case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_CHECKBOX: {
            CheckBoxView checkBox = CheckBoxView.simpleCheckBox(context, Lang.rtl());
            settingView.addView(checkBox);
            if (themeProvider != null) {
              themeProvider.addThemeInvalidateListener(checkBox);
            }
            break;
          }
        }
        if (themeProvider != null) {
          settingView.addThemeListeners(themeProvider);
        }
        adapter.modifySettingView(viewType, settingView);
        return new SettingHolder(settingView);
      }
      case ListItem.TYPE_EDITTEXT_NO_PADDING_REUSABLE:
      case ListItem.TYPE_EDITTEXT_NO_PADDING:
      case ListItem.TYPE_EDITTEXT:
      case ListItem.TYPE_EDITTEXT_REUSABLE:
      case ListItem.TYPE_EDITTEXT_COUNTERED:
      case ListItem.TYPE_EDITTEXT_CHANNEL_DESCRIPTION:
      case ListItem.TYPE_EDITTEXT_WITH_PHOTO:
      case ListItem.TYPE_EDITTEXT_WITH_PHOTO_SMALLER:
      case ListItem.TYPE_EDITTEXT_POLL_OPTION:
      case ListItem.TYPE_EDITTEXT_POLL_OPTION_ADD: {
        FrameLayoutFix frameLayout;
        switch (viewType) {
          case ListItem.TYPE_EDITTEXT_WITH_PHOTO:
          case ListItem.TYPE_EDITTEXT_WITH_PHOTO_SMALLER: {
            frameLayout = new ScoutFrameLayout(context);
            frameLayout.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, measureHeightForType(viewType)));
            ViewSupport.setThemedBackground(frameLayout, ColorId.filling, themeProvider);
            break;
          }
          default: {
            frameLayout = new FrameLayoutFix(context) {
              private int lastHeight;

              @Override
              protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                final int viewHeight = getMeasuredHeight();

                if (lastHeight != viewHeight) {
                  lastHeight = viewHeight;
                  if (adapter.getHeightChangeListener() != null) {
                    adapter.getHeightChangeListener().onHeightChanged(this, viewHeight);
                  }
                }
              }
            };
            if (adapter.getHeightChangeListener() != null) {
              adapter.getHeightChangeListener().onHeightChanged(frameLayout, 0);
            }
            int height = ViewGroup.LayoutParams.WRAP_CONTENT;
            switch (viewType) {
              case ListItem.TYPE_EDITTEXT_POLL_OPTION_ADD: {
                height = measureHeightForType(viewType);
                break;
              }
            }
            frameLayout.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
            if (viewType == ListItem.TYPE_EDITTEXT_CHANNEL_DESCRIPTION) {
              ViewSupport.setThemedBackground(frameLayout, ColorId.filling, themeProvider);
            }
            break;
          }
        }
        int paddingTop;
        boolean needHint;
        switch (viewType) {
          case ListItem.TYPE_EDITTEXT:
          case ListItem.TYPE_EDITTEXT_REUSABLE:
          case ListItem.TYPE_EDITTEXT_COUNTERED:
            paddingTop = Screen.dp(16f);
            needHint = true;
            break;
          case ListItem.TYPE_EDITTEXT_POLL_OPTION:
          case ListItem.TYPE_EDITTEXT_POLL_OPTION_ADD:
            paddingTop = 0;
            needHint = false;
            break;
          default:
            paddingTop = 0;
            needHint = true;
            break;
        }
        if (viewType != ListItem.TYPE_EDITTEXT_POLL_OPTION_ADD && viewType != ListItem.TYPE_EDITTEXT_POLL_OPTION)
          frameLayout.setPadding(Screen.dp(16f), paddingTop, Screen.dp(16f), Screen.dp(8f));

        MaterialEditTextGroup editText = new MaterialEditTextGroup(context, needHint);
        editText.applyRtl(Lang.rtl());
        editText.addThemeListeners(themeProvider);
        editText.setTextListener(adapter);
        editText.setFocusListener(adapter);
        switch (viewType) {
          case ListItem.TYPE_EDITTEXT_COUNTERED: {
            editText.addLengthCounter(false);
            break;
          }
          case ListItem.TYPE_EDITTEXT_POLL_OPTION: {
            editText.addLengthCounter(true);
            editText.getEditText().setLineDisabled(true);
            editText.setEmptyHint(R.string.PollOption);
            break;
          }
          case ListItem.TYPE_EDITTEXT_POLL_OPTION_ADD: {
            editText.getEditText().setLineDisabled(true);
            editText.setEmptyHint(R.string.PollOptionsAdd);
            break;
          }
          case ListItem.TYPE_EDITTEXT_CHANNEL_DESCRIPTION: {
            editText.addLengthCounter(false);
            editText.getEditText().setLineDisabled(true);
            editText.setEmptyHint(R.string.DescriptionEmptyHint);
            break;
          }
        }
        adapter.setLockFocusView(editText.getEditText());
        if (viewType == ListItem.TYPE_EDITTEXT_POLL_OPTION_ADD || viewType == ListItem.TYPE_EDITTEXT_POLL_OPTION) {
          FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
          params.rightMargin = params.leftMargin = Screen.dp(16f);
          params.bottomMargin = Screen.dp(8f);
          if (viewType == ListItem.TYPE_EDITTEXT_POLL_OPTION_ADD)
            editText.getEditText().setCursorVisible(false);
          editText.setLayoutParams(params);
          editText.setOnRadioClickListener((v, radioView) -> {
            adapter.onEditTextRadioClick((ListItem) frameLayout.getTag(), frameLayout, v, radioView);
          });
        }
        frameLayout.addView(editText);

        switch (viewType) {
          case ListItem.TYPE_EDITTEXT_WITH_PHOTO:
          case ListItem.TYPE_EDITTEXT_WITH_PHOTO_SMALLER: {
            FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (Lang.rtl()) {
              params.rightMargin = Screen.dp(82f);
            } else {
              params.leftMargin = Screen.dp(82f);
            }
            params.topMargin = Screen.dp(8f);
            editText.setLayoutParams(params);

            params = FrameLayoutFix.newParams(Screen.dp(61f), Screen.dp(61f));
            params.topMargin = Screen.dp(12f);
            params.gravity = Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT;

            AvatarView avatarView = new AvatarView(context);
            avatarView.setId(R.id.avatar);
            avatarView.setOnClickListener(onClickListener);
            avatarView.setNeedOverlay();
            avatarView.setUseCustomWindowManagement();
            avatarView.setLayoutParams(params);
            frameLayout.addView(avatarView);
            break;
          }
          case ListItem.TYPE_EDITTEXT_POLL_OPTION_ADD: {
            View view = new View(context);
            view.setId(R.id.optionAdd);
            Views.setClickable(view);
            RippleSupport.setTransparentSelector(view);
            view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            view.setOnClickListener(onClickListener);
            frameLayout.addView(view);
            break;
          }
        }

        SettingHolder holder = new SettingHolder(frameLayout);
        switch (viewType) {
          case ListItem.TYPE_EDITTEXT_NO_PADDING_REUSABLE:
          case ListItem.TYPE_EDITTEXT_REUSABLE:
          case ListItem.TYPE_EDITTEXT_POLL_OPTION:
          case ListItem.TYPE_EDITTEXT_POLL_OPTION_ADD:
            break;
          default:
            holder.setIsRecyclable(false);
            break;
        }
        return holder;
      }
      case ListItem.TYPE_USER_SMALL:
      case ListItem.TYPE_USER: {
        UserView userView = new UserView(context, tdlib); // FIXME theme
        userView.setOffsetLeft(Screen.dp(11f));
        userView.setOnClickListener(onClickListener);
        userView.setHeight(measureHeightForType(viewType));
        Views.setClickable(userView);
        // RippleSupport.setTransparentSelector(userView);
        RippleSupport.setSimpleWhiteBackground(userView, themeProvider);
        return new SettingHolder(userView);
      }
      case ListItem.TYPE_INFO: {
        CustomTextView textView = new CustomTextView(context, tdlib);
        textView.setPadding(Screen.dp(16f), Screen.dp(12f), Screen.dp(16f), Screen.dp(12f));
        textView.setTextColorId(ColorId.textLight);
        ViewSupport.setThemedBackground(textView, ColorId.filling, themeProvider);
        textView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new SettingHolder(textView);
      }
      case ListItem.TYPE_VALUED_SETTING_RED_STUPID: {
        SettingStupidView stupidView = new SettingStupidView(context);
        stupidView.setOnClickListener(onClickListener);
        stupidView.setIsRed();
        stupidView.checkRtl();
        if (themeProvider != null) {
          stupidView.addThemeListeners(themeProvider);
        }
        return new SettingHolder(stupidView);
      }
      case ListItem.TYPE_REACTION_CHECKBOX: {
        ReactionCheckboxSettingsView view = new ReactionCheckboxSettingsView(context);
        view.init(tdlib);
        view.setOnClickListener(onClickListener);
        view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(104)));
        if (themeProvider != null) {
          themeProvider.addThemeInvalidateListener(view);
        }
        return new SettingHolder(view);
      }
      case ListItem.TYPE_EMBED_STICKER: {
        EmbeddableStickerView view = new EmbeddableStickerView(context);
        view.init(tdlib);
        view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (themeProvider != null) {
          themeProvider.addThemeInvalidateListener(view);
        }
        return new SettingHolder(view);
      }
      case ListItem.TYPE_MEMBERS_LIST: {
        RecyclerView recyclerView = new RecyclerView(context) {
          private int oldWidth;
          @Override
          protected void onMeasure (int widthSpec, int heightSpec) {
            super.onMeasure(widthSpec, heightSpec);
            int measuredWidth = getMeasuredWidth();
            if (oldWidth != 0 && oldWidth != measuredWidth) {
              invalidateItemDecorations();
            }
            oldWidth = measuredWidth;
          }
        };
        if (innerOnScrollListener != null) {
          recyclerView.addOnScrollListener(innerOnScrollListener);
        }
        recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
          @Override
          public void getItemOffsets (Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            RecyclerView.ViewHolder holder = parent.getChildViewHolder(view);
            if (holder == null || holder.getAdapterPosition() != 0) {
              outRect.left = 0;
              return;
            }
            int itemCount = parent.getAdapter().getItemCount();
            int padding = Screen.dp(17f);
            int imageSize = Screen.dp(50f);
            int minWidth = padding + padding + imageSize;
            if (itemCount != 0) {
              int parentWidth = parent.getMeasuredWidth();
              int itemWidth = Math.max(minWidth, parentWidth / itemCount);
              if (itemWidth > minWidth) {
                int diff = itemWidth - minWidth;
                itemWidth = Math.max(minWidth, (parentWidth - diff) / itemCount);
              }
              outRect.left = Math.max(0, (parentWidth - itemWidth * itemCount) / 2);
            } else {
              outRect.left = 0;
            }
          }
        });
        ViewSupport.setThemedBackground(recyclerView, ColorId.filling, themeProvider);
        recyclerView.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? View.OVER_SCROLL_IF_CONTENT_SCROLLS : View.OVER_SCROLL_NEVER);
        recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180l));
        recyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
        recyclerView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(95f)));
        return new SettingHolder(recyclerView);
      }
      case ListItem.TYPE_INFO_SETTING:
      case ListItem.TYPE_INFO_MULTILINE: {
        SettingView settingView = new SettingView(context, tdlib);
        settingView.setType(viewType == ListItem.TYPE_INFO_MULTILINE ? SettingView.TYPE_INFO_MULTILINE : SettingView.TYPE_INFO);
        settingView.setOnClickListener(onClickListener);
        if (themeProvider != null) {
          themeProvider.addThemeInvalidateListener(settingView);
        }
        return new SettingHolder(settingView);
      }
      case ListItem.TYPE_RADIO_SETTING:
      case ListItem.TYPE_RADIO_SETTING_WITH_NEGATIVE_STATE: {
        SettingView settingView = new SettingView(context, tdlib);
        settingView.setType(SettingView.TYPE_RADIO);
        settingView.checkRtl(false);
        if (themeProvider != null) {
          settingView.addThemeListeners(themeProvider);
        }
        settingView.setOnClickListener(onClickListener);
        if (viewType == ListItem.TYPE_RADIO_SETTING_WITH_NEGATIVE_STATE) {
          settingView.getToggler().setUseNegativeState(true);
          adapter.modifySettingView(viewType, settingView);
        }
        return new SettingHolder(settingView);
      }
      case ListItem.TYPE_HEADER:
      case ListItem.TYPE_HEADER_MULTILINE:
      case ListItem.TYPE_HEADER_WITH_ACTION:
      case ListItem.TYPE_HEADER_PADDED: {
        final boolean isRtl = Lang.rtl();

        TextView textView = new NoScrollTextView(context);
        textView.setGravity(Lang.gravity(Gravity.CENTER_VERTICAL));
        int paddingTop = viewType == ListItem.TYPE_HEADER_MULTILINE ? Screen.dp(6f) : viewType == ListItem.TYPE_HEADER_PADDED ? Screen.dp(4f) : 0;
        textView.setPadding(Screen.dp(16f), paddingTop, Screen.dp(16f), viewType == ListItem.TYPE_HEADER_MULTILINE ? Screen.dp(6f) : 0);
        textView.setSingleLine(viewType != ListItem.TYPE_HEADER_MULTILINE);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setTextColor(Theme.textAccent2Color());
        if (themeProvider != null) {
          themeProvider.addThemeTextColorListener(textView, ColorId.background_text);
        }
        textView.setTypeface(Fonts.getRobotoMedium());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
        if (viewType != ListItem.TYPE_HEADER_WITH_ACTION) {
          textView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, viewType == ListItem.TYPE_HEADER_MULTILINE ? ViewGroup.LayoutParams.WRAP_CONTENT : Screen.dp(32f) + paddingTop));
          adapter.modifyHeaderTextView(textView, Screen.dp(32f), paddingTop);
          return new SettingHolder(textView);
        }

        FrameLayoutFix wrapView = new FrameLayoutFix(context);
        textView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        wrapView.addView(textView);

        ImageView imageView = new ImageView(context);
        imageView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(52f), ViewGroup.LayoutParams.MATCH_PARENT, isRtl ? Gravity.LEFT : Gravity.RIGHT));
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setColorFilter(Theme.getColor(ColorId.background_textLight));
        if (themeProvider != null) {
          themeProvider.addThemeFilterListener(imageView, ColorId.background_textLight);
        }
        imageView.setOnClickListener(onClickListener);
        wrapView.addView(imageView);

        wrapView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(32f)));

        return new SettingHolder(wrapView);
      }
      case ListItem.TYPE_DESCRIPTION:
      case ListItem.TYPE_DESCRIPTION_CENTERED:
      case ListItem.TYPE_DESCRIPTION_SMALL: {
        return new SettingHolder(createDescription(context, viewType, ColorId.background_textLight, onClickListener, themeProvider));
      }
      case ListItem.TYPE_BUILD_NO: {
        TextView textView = new NoScrollTextView(context);
        textView.setGravity(Gravity.CENTER);
        textView.setPadding(0, 0, 0, Screen.dp(4f));
        textView.setTypeface(Fonts.getRobotoRegular());
        textView.setTextColor(Theme.textDecent2Color());
        if (themeProvider != null) {
          themeProvider.addThemeTextColorListener(textView, ColorId.background_textLight);
        }
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
        textView.setOnClickListener(onClickListener);
        textView.setOnLongClickListener(onLongClickListener);
        textView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(42f)));
        return new SettingHolder(textView);
      }
      case ListItem.TYPE_PROGRESS: {
        ProgressComponentView componentView = new ProgressComponentView(context);
        componentView.initLarge(1f);
        componentView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return new SettingHolder(componentView);
      }
      case ListItem.TYPE_SESSION:
      case ListItem.TYPE_SESSION_WITH_AVATAR: {
        boolean isAvatar = viewType == ListItem.TYPE_SESSION_WITH_AVATAR;

        RelativeLayout layout = new RelativeSessionLayout(context);
        layout.setOnClickListener(onClickListener);
        layout.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        layout.setPadding(Screen.dp(16f), Screen.dp(18f), Screen.dp(16f), Screen.dp(18f));
        Views.setClickable(layout);
        RippleSupport.setSimpleWhiteBackground(layout, themeProvider);

        RelativeLayout.LayoutParams params;

        // Time

        params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(Lang.rtl() ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_RIGHT);
        params.topMargin = Screen.dp(4f);

        TextView timeView = new NoScrollTextView(context);
        timeView.setId(R.id.session_time);
        timeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f);
        timeView.setTextColor(Theme.textDecentColor());
        if (themeProvider != null) {
          themeProvider.addThemeTextDecentColorListener(timeView);
        }
        timeView.setPadding(Lang.rtl() ? 0 : Screen.dp(12f), 0, Lang.rtl() ? Screen.dp(12f) : 0, 0);
        timeView.setLayoutParams(params);
        layout.addView(timeView);

        // Title

        params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (Lang.rtl()) {
          params.addRule(RelativeLayout.RIGHT_OF, R.id.session_time);
          params.addRule(RelativeLayout.LEFT_OF, R.id.session_icon);
        } else {
          params.addRule(RelativeLayout.LEFT_OF, R.id.session_time);
          params.addRule(RelativeLayout.RIGHT_OF, R.id.session_icon);
        }

        if (!isAvatar) {
          params.leftMargin = Screen.dp(24f);
        }

        TextView titleView = new NoScrollTextView(context);
        titleView.setId(R.id.session_title);
        titleView.setTypeface(Fonts.getRobotoMedium());
        titleView.setGravity(Lang.gravity());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
        titleView.setTextColor(Theme.textAccentColor());
        if (themeProvider != null) {
          themeProvider.addThemeTextAccentColorListener(titleView);
        }
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setSingleLine(true);
        titleView.setLayoutParams(params);
        layout.addView(titleView);


        params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.BELOW, R.id.session_title);
        if (!isAvatar) {
          if (Lang.rtl()) {
            params.addRule(RelativeLayout.ALIGN_RIGHT, R.id.session_title);
          } else {
            params.addRule(RelativeLayout.ALIGN_LEFT, R.id.session_title);
          }
        }

        TextView subtextView = new NoScrollTextView(context);
        subtextView.setId(R.id.session_device);
        subtextView.setTypeface(Fonts.getRobotoRegular());
        subtextView.setGravity(Lang.gravity());
        subtextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
        subtextView.setPadding(0, Screen.dp(3f), 0, Screen.dp(4f));
        subtextView.setTextColor(Theme.textAccentColor());
        if (themeProvider != null) {
          themeProvider.addThemeTextAccentColorListener(subtextView);
        }
        if (!isAvatar) {
          subtextView.setMaxLines(1);
          subtextView.setEllipsize(TextUtils.TruncateAt.END);
        }
        subtextView.setLayoutParams(params);
        layout.addView(subtextView);

        params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.BELOW, R.id.session_device);
        if (!isAvatar) {
          if (Lang.rtl()) {
            params.addRule(RelativeLayout.ALIGN_RIGHT, R.id.session_title);
          } else {
            params.addRule(RelativeLayout.ALIGN_LEFT, R.id.session_title);
          }
        }

        TextView locationView = new NoScrollTextView(context);
        locationView.setId(R.id.session_location);
        locationView.setTextColor(Theme.textDecentColor());
        locationView.setGravity(Lang.gravity());
        if (themeProvider != null) {
          themeProvider.addThemeTextDecentColorListener(locationView);
        }
        locationView.setTypeface(Fonts.getRobotoRegular());
        locationView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
        locationView.setLayoutParams(params);
        layout.addView(locationView);

        params = new RelativeLayout.LayoutParams(Screen.dp(14f), Screen.dp(14f));
        params.addRule(Lang.rtl() ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_RIGHT);
        params.topMargin = Screen.dp(5f);

        ProgressComponentView progressView = new ProgressComponentView(context);
        progressView.initMedium(0f);
        progressView.setInverseView(timeView);
        progressView.setLayoutParams(params);
        layout.addView(progressView);

        if (viewType == ListItem.TYPE_SESSION_WITH_AVATAR) {
          params = new RelativeLayout.LayoutParams(Screen.dp(20f) + Screen.dp(6f), Screen.dp(20f) + Screen.dp(2f) * 2);
          params.addRule(Lang.rtl() ? RelativeLayout.ALIGN_PARENT_RIGHT : RelativeLayout.ALIGN_PARENT_LEFT);

          AvatarView avatarView = new AvatarView(context);
          avatarView.setId(R.id.session_icon);
          avatarView.setPadding(Lang.rtl() ? Screen.dp(6f) : 0, Screen.dp(2f), Lang.rtl() ? 0 : Screen.dp(6f), Screen.dp(4f));
          avatarView.setUseCustomWindowManagement();
          avatarView.setLettersSizeDp(12f);
          avatarView.setLayoutParams(params);
          layout.addView(avatarView);
        } else {
          // Icon
          params = new RelativeLayout.LayoutParams(Screen.dp(24f), Screen.dp(24f));
          params.addRule(Lang.rtl() ? RelativeLayout.ALIGN_PARENT_RIGHT : RelativeLayout.ALIGN_PARENT_LEFT);
          ImageView iconView = new ImageView(context);
          iconView.setId(R.id.session_icon);
          iconView.setLayoutParams(params);
          iconView.setColorFilter(Theme.getColor(ColorId.icon));
          iconView.setScaleType(ImageView.ScaleType.CENTER);
          layout.addView(iconView);

          // State Icons
          Drawable callIcon = Drawables.get(context.getResources(), R.drawable.baseline_call_16);
          callIcon.setColorFilter(Paints.getColorFilter(Theme.getColor(ColorId.textNeutral)));
          Drawable secretIcon = Drawables.get(context.getResources(), R.drawable.baseline_lock_16);
          secretIcon.setColorFilter(Paints.getColorFilter(Theme.getColor(ColorId.textSecure)));

          params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
          params.addRule(RelativeLayout.BELOW, R.id.session_location);
          params.addRule(Lang.rtl() ? RelativeLayout.ALIGN_RIGHT : RelativeLayout.ALIGN_LEFT, R.id.session_title);
          params.topMargin = Screen.dp(6);
          params.rightMargin = Screen.dp(12);

          TextView secretState = new TextView(context);
          secretState.setId(R.id.session_secret);
          secretState.setLayoutParams(params);
          secretState.setCompoundDrawablesWithIntrinsicBounds(secretIcon, null, null, null);
          secretState.setCompoundDrawablePadding(Screen.dp(8));
          secretState.setText(Lang.getString(R.string.SessionSecretChats));
          secretState.setTextColor(Theme.getColor(ColorId.textSecure));
          secretState.setAllCaps(true);
          secretState.setGravity(Gravity.CENTER_VERTICAL);
          secretState.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f);
          layout.addView(secretState);

          params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
          params.addRule(RelativeLayout.BELOW, R.id.session_location);
          params.addRule(Lang.rtl() ? RelativeLayout.LEFT_OF : RelativeLayout.RIGHT_OF, R.id.session_secret);
          params.topMargin = Screen.dp(6);

          TextView callsState = new TextView(context);
          callsState.setId(R.id.session_calls);
          callsState.setLayoutParams(params);
          callsState.setCompoundDrawablesWithIntrinsicBounds(callIcon, null, null, null);
          callsState.setCompoundDrawablePadding(Screen.dp(8));
          callsState.setText("Calls");
          callsState.setTextColor(Theme.getColor(ColorId.textNeutral));
          callsState.setAllCaps(true);
          callsState.setGravity(Gravity.CENTER_VERTICAL);
          callsState.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f);
          layout.addView(callsState);

          if (themeProvider != null) {
            themeProvider.addThemeFilterListener(callIcon, ColorId.textNeutral);
            themeProvider.addThemeFilterListener(secretIcon, ColorId.textSecure);
            themeProvider.addThemeTextColorListener(callsState, ColorId.textNeutral);
            themeProvider.addThemeTextColorListener(secretState, ColorId.textSecure);
          }
        }

        return new SettingHolder(layout);
      }
      case ListItem.TYPE_SESSIONS_EMPTY: {
        FrameLayoutFix frameLayout = new FrameLayoutFix(context) {
          @Override
          protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
            ViewParent parent = this;
            do {
              parent = parent.getParent();
            } while (!(parent instanceof RecyclerView) && parent != null);

            int position = adapter.indexOfViewByType(viewType);
            int paddingTop = 0;

            for (int i = 0; i < position; i++) {
              int viewType = adapter.getItems().get(i).getViewType();
              if (viewType == ListItem.TYPE_SESSION && parent != null) {
                View view = ((RecyclerView) parent).getLayoutManager().findViewByPosition(i);
                if (view != null) {
                  paddingTop += view.getMeasuredHeight();
                  continue;
                }
              }
              paddingTop += measureHeightForType(viewType);
            }

            int totalHeight = parent != null ? ((RecyclerView) parent).getMeasuredHeight() : 0;
            int availHeight = totalHeight - paddingTop;

            if (availHeight > Screen.dp(240f)) {
              super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(availHeight, MeasureSpec.EXACTLY));
            } else {
              super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
          }
        };
        frameLayout.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        RelativeLayout relativeLayout = new RelativeLayout(context);
        relativeLayout.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.CENTER_HORIZONTAL);

        ImageView imageView = new ImageView(context);
        imageView.setId(R.id.btn_sessionsEmpty_image);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setImageResource(R.drawable.baseline_devices_other_96);
        imageView.setColorFilter(Theme.backgroundIconColor());
        if (themeProvider != null) {
          themeProvider.addThemeFilterListener(imageView, ColorId.background_icon);
        }
        imageView.setPadding(0, Screen.dp(12f), 0, Screen.dp(16f));
        imageView.setLayoutParams(params);
        relativeLayout.addView(imageView);

        params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.CENTER_HORIZONTAL);
        params.addRule(RelativeLayout.BELOW, R.id.btn_sessionsEmpty_image);

        TextView titleView = new NoScrollTextView(context);
        titleView.setId(R.id.btn_sessionsEmpty_title);
        titleView.setText(Lang.getString(R.string.NoOtherSessions));
        titleView.setTypeface(Fonts.getRobotoMedium());
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
        titleView.setTextColor(Theme.textDecent2Color());
        if (themeProvider != null) {
          themeProvider.addThemeTextColorListener(titleView, ColorId.background_textLight);
        }
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setLayoutParams(params);
        relativeLayout.addView(titleView);

        params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.CENTER_HORIZONTAL);
        params.addRule(RelativeLayout.BELOW, R.id.btn_sessionsEmpty_title);
        TextView descView = new NoScrollTextView(context);
        descView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
        descView.setGravity(Gravity.CENTER_HORIZONTAL);
        descView.setText(Lang.getString(R.string.NoOtherSessionsInfo));
        descView.setPadding(Screen.dp(26f), Screen.dp(16f), Screen.dp(26f), Screen.dp(12f));
        descView.setLineSpacing(Screen.dp(2f), 1);
        descView.setTextColor(Theme.textDecent2Color());
        if (themeProvider != null) {
          themeProvider.addThemeTextColorListener(descView, ColorId.background_textLight);
        }
        descView.setTypeface(Fonts.getRobotoRegular());
        descView.setGravity(Gravity.CENTER_HORIZONTAL);
        descView.setLayoutParams(params);
        relativeLayout.addView(descView);

        frameLayout.addView(relativeLayout);

        return new SettingHolder(frameLayout);
      }
      case ListItem.TYPE_ICONIZED_EMPTY:
      case ListItem.TYPE_WEBSITES_EMPTY:
      case ListItem.TYPE_CHATS_PLACEHOLDER: {
        final boolean needRegisteredUsers = viewType == ListItem.TYPE_CHATS_PLACEHOLDER;

        RelativeLayout contentView = new RelativeLayout(context);
        contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        RelativeLayout.LayoutParams params;
        params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Screen.dp(12f);
        params.bottomMargin = Screen.dp(needRegisteredUsers ? 6f : 12f);

        if (needRegisteredUsers) {
          params.height = Screen.dp(92f) + Screen.dp(84f);
          JoinedUsersView usersView = new JoinedUsersView(context, tdlib);
          if (themeProvider != null) {
            themeProvider.addThemeInvalidateListener(usersView);
          }
          usersView.setLayoutParams(params);
          contentView.addView(usersView);
          if (themeProvider instanceof ChatsController) {
            usersView.setParent(((ChatsController) themeProvider).getParentController());
          } else if (themeProvider instanceof MainController) {
            usersView.setParent((MainController) themeProvider);
          }
        } else {
          if (viewType == ListItem.TYPE_WEBSITES_EMPTY) {
            View imageView = new WebsiteEmptyImageView(context);
            imageView.setId(R.id.icon_additionalPassword);
            if (themeProvider != null) {
              themeProvider.addThemeInvalidateListener(imageView);
            }
            params.height = Screen.dp(82f);
            imageView.setLayoutParams(params);
            contentView.addView(imageView);
          } else {
            ImageView imageView = new ImageView(context);
            imageView.setId(R.id.icon_additionalPassword);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setColorFilter(Theme.backgroundIconColor());
            if (themeProvider != null) {
              themeProvider.addThemeFilterListener(imageView, ColorId.background_icon);
            }
            imageView.setLayoutParams(params);
            contentView.addView(imageView);
          }

          params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
          params.addRule(RelativeLayout.BELOW, R.id.icon_additionalPassword);

          TextView textView = new NoScrollTextView(context);
          textView.setGravity(Gravity.CENTER);
          textView.setTypeface(Fonts.getRobotoRegular());
          textView.setTextColor(Theme.textDecent2Color());
          if (themeProvider != null) {
            themeProvider.addThemeTextColorListener(textView, ColorId.background_textLight);
          }
          textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
          textView.setPadding(Screen.dp(22f), Screen.dp(12f), Screen.dp(22f), Screen.dp(24f));
          textView.setLayoutParams(params);
          contentView.addView(textView);
        }

        FrameLayoutFix wrapView = new FrameLayoutFix(context) {
          @Override
          protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
            final int paddingBottom =
              measureHeightForType(ListItem.TYPE_SHADOW_BOTTOM) +
                measureHeightForType(ListItem.TYPE_SHADOW_TOP) +
                measureHeightForType(ListItem.TYPE_BUTTON);
            ViewParent parent = this;
            do {
              parent = parent.getParent();
            } while (!(parent instanceof RecyclerView) && parent != null);
            int totalHeight = parent != null ? ((RecyclerView) parent).getMeasuredHeight() : 0;
            int availHeight = totalHeight - paddingBottom;
            int headerHeight;
            int myViewId = getId();
            if (myViewId == R.id.changePhoneText) {
              headerHeight = Screen.dp(310f);
            } else if (myViewId == R.id.inviteFriendsText) {
              headerHeight = Screen.dp(120f);
            } else {
              headerHeight = Screen.dp(240f);
            }

            if (availHeight <= headerHeight) {
              super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            } else {
              int contentHeight = headerHeight + paddingBottom + Screen.dp(12f);
              int centerY = totalHeight / 2 - contentHeight / 2 + headerHeight;
              super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(centerY, MeasureSpec.EXACTLY));
            }
          }
        };
        wrapView.addView(contentView);
        wrapView.setMinimumHeight(Screen.dp(240f));
        return new SettingHolder(wrapView);
      }
      case ListItem.TYPE_BUTTON: {
        TextView textView = new ScalableTextView(context);

        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setTypeface(Fonts.getRobotoMedium());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
        textView.setGravity(Gravity.CENTER);
        textView.setTextColor(Theme.getColor(ColorId.textNeutral));
        textView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (themeProvider != null) {
          themeProvider.addThemeTextColorListener(textView, ColorId.textNeutral);
        }

        FrameLayoutFix frameLayout = new FrameLayoutFix(context);
        Views.setClickable(frameLayout);
        RippleSupport.setSimpleWhiteBackground(frameLayout, themeProvider);
        frameLayout.setOnClickListener(onClickListener);
        frameLayout.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(57f)));
        frameLayout.addView(textView);

        return new SettingHolder(frameLayout);
      }
      case ListItem.TYPE_COUNTRY: {
        FrameLayoutFix frameLayout = new FrameLayoutFix(context);
        frameLayout.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56f) + Screen.dp(1f)));
        frameLayout.setPadding(Screen.dp(16f), 0, Screen.dp(16f), 0);
        frameLayout.setOnClickListener(onClickListener);
        Views.setClickable(frameLayout);
        RippleSupport.setSimpleWhiteBackground(frameLayout, themeProvider);

        FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56f), Gravity.LEFT);
        params.topMargin = params.leftMargin = Screen.dp(1f);
        params.rightMargin = Screen.dp(60f);

        TextView nameView = new NoScrollTextView(context);
        nameView.setTypeface(Fonts.getRobotoRegular());
        nameView.setTextColor(Theme.textAccentColor());
        if (themeProvider != null) {
          themeProvider.addThemeTextAccentColorListener(nameView);
        }
        nameView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        nameView.setLayoutParams(params);
        nameView.setEllipsize(TextUtils.TruncateAt.END);
        nameView.setSingleLine(true);
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f);
        frameLayout.addView(nameView);

        params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(56f), Gravity.RIGHT);
        params.topMargin = params.rightMargin = Screen.dp(1f);

        TextView codeView = new NoScrollTextView(context);
        codeView.setTypeface(Fonts.getRobotoRegular());
        codeView.setTextColor(Theme.textDecentColor());
        if (themeProvider != null) {
          themeProvider.addThemeTextDecentColorListener(codeView);
        }
        codeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f);
        codeView.setSingleLine(true);
        codeView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        frameLayout.addView(codeView);

        SeparatorView separatorView = new SeparatorView(context);
        if (themeProvider != null) {
          themeProvider.addThemeInvalidateListener(separatorView);
        }
        separatorView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(1f), Gravity.BOTTOM));
        frameLayout.addView(separatorView);

        return new SettingHolder(frameLayout);
      }
      case ListItem.TYPE_2FA_EMAIL: {
        FrameLayoutFix frameLayout = new FrameLayoutFix(context);
        frameLayout.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        RelativeLayout contentView = new RelativeLayout(context);
        contentView.setPadding(Screen.dp(18f), 0, Screen.dp(18f), 0);
        contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.CENTER_HORIZONTAL);

        ImageView imageView = new ImageView(context);
        imageView.setId(R.id.icon_email);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setImageResource(R.drawable.baseline_mail_96);
        imageView.setColorFilter(Theme.backgroundIconColor());
        if (themeProvider != null) {
          themeProvider.addThemeFilterListener(imageView, ColorId.background_icon);
        }
        imageView.setPadding(0, 0, 0, Screen.dp(36f));
        imageView.setLayoutParams(params);
        contentView.addView(imageView);

        params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.BELOW, R.id.icon_email);

        TextView textView = new NoScrollTextView(context);
        textView.setId(R.id.text_email);
        textView.setText(Lang.getString(R.string.CheckYourVerificationEmail));
        textView.setTextColor(Theme.textDecent2Color());
        if (themeProvider != null) {
          themeProvider.addThemeTextColorListener(textView, ColorId.background_textLight);
        }
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
        textView.setPadding(0, 0, 0, 0);
        textView.setTypeface(Fonts.getRobotoRegular());
        textView.setGravity(Gravity.CENTER_HORIZONTAL);
        textView.setLayoutParams(params);
        contentView.addView(textView);

        params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.CENTER_HORIZONTAL);
        params.addRule(RelativeLayout.BELOW, R.id.text_email);

        TextView emailView = new NoScrollTextView(context);
        emailView.setId(R.id.text_email_email);
        emailView.setTextColor(Theme.textDecent2Color());
        if (themeProvider != null) {
          themeProvider.addThemeTextColorListener(emailView, ColorId.background_textLight);
        }
        emailView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
        emailView.setTypeface(Fonts.getRobotoRegular());
        emailView.setGravity(Gravity.CENTER_HORIZONTAL);
        emailView.setPadding(0, Screen.dp(16f), 0, Screen.dp(16f));
        emailView.setLayoutParams(params);
        contentView.addView(emailView);

        params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.CENTER_HORIZONTAL);
        params.addRule(RelativeLayout.BELOW, R.id.text_email_email);

        TextView abortView = new NoScrollTextView(context);
        abortView.setId(R.id.btn_abort2FA);

        abortView.setText(Lang.getString(R.string.AbortPassword));
        abortView.setPadding(0, 0, 0, Screen.dp(42f));
        abortView.setTextColor(Theme.getColor(ColorId.textNeutral));
        if (themeProvider != null) {
          themeProvider.addThemeTextColorListener(abortView, ColorId.textNeutral);
        }
        abortView.setTypeface(Fonts.getRobotoRegular());
        abortView.setGravity(Gravity.CENTER_HORIZONTAL);
        Views.setClickable(abortView);
        abortView.setOnClickListener(onClickListener);
        abortView.setLayoutParams(params);
        contentView.addView(abortView);

        frameLayout.addView(contentView);
        return new SettingHolder(frameLayout);
      }
      case ListItem.TYPE_FAKE_PAGER_TOPVIEW: {
        View view = new View(context);
        view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, measureHeightForType(ListItem.TYPE_FAKE_PAGER_TOPVIEW)));
        return new SettingHolder(view);
      }
      case ListItem.TYPE_SMALL_MEDIA: {
        MediaSmallView smallView = new MediaSmallView(context);
        if (themeProvider != null) {
          themeProvider.addThemeInvalidateListener(smallView);
        }
        smallView.initWithClickDelegate(clickDelegate);
        smallView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // smallView.setOnClickListener(onClickListener);
        // smallView.setOnLongClickListener(onLongClickListener);
        Views.setClickable(smallView);
        return new SettingHolder(smallView);
      }
      case ListItem.TYPE_CUSTOM_INLINE: {
        CustomResultView resultView = new CustomResultView(context);
        RippleSupport.setSimpleWhiteBackground(resultView, themeProvider);
        resultView.setOnClickListener(onClickListener);
        resultView.setOnLongClickListener(onLongClickListener);
        Views.setClickable(resultView);
        resultView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new SettingHolder(resultView);
      }
      case ListItem.TYPE_LIST_INFO_VIEW: {
        ListInfoView bottomProgressView = new ListInfoView(context);
        bottomProgressView.setPadding(0, 0, 0, Screen.dp(4f));
        bottomProgressView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(42f)));
        if (themeProvider != null) {
          bottomProgressView.addThemeListeners(themeProvider);
        }
        return new SettingHolder(bottomProgressView);
      }
      case ListItem.TYPE_PAGE_BLOCK_MEDIA:
      case ListItem.TYPE_PAGE_BLOCK:
      case ListItem.TYPE_PAGE_BLOCK_GIF:
      case ListItem.TYPE_PAGE_BLOCK_COLLAGE:
      case ListItem.TYPE_PAGE_BLOCK_AVATAR: {
        PageBlockView view = new PageBlockView(context, tdlib);
        view.setClickListener(onClickListener, onLongClickListener);
        switch (viewType) {
          case ListItem.TYPE_PAGE_BLOCK_MEDIA: {
            view.initWithMode(PageBlockView.MODE_IMAGE);
            break;
          }
          case ListItem.TYPE_PAGE_BLOCK_GIF: {
            view.initWithMode(PageBlockView.MODE_GIF);
            break;
          }
          case ListItem.TYPE_PAGE_BLOCK_COLLAGE: {
            view.initWithMode(PageBlockView.MODE_COLLAGE);
            break;
          }
          case ListItem.TYPE_PAGE_BLOCK_AVATAR: {
            view.initWithMode(PageBlockView.MODE_AVATAR);
            break;
          }
        }
        view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (themeProvider != null) {
          themeProvider.addThemeInvalidateListener(view);
        }
        return new SettingHolder(view);
      }

      case ListItem.TYPE_PAGE_BLOCK_TABLE:
      case ListItem.TYPE_PAGE_BLOCK_SLIDESHOW:
      case ListItem.TYPE_PAGE_BLOCK_EMBEDDED:
      case ListItem.TYPE_PAGE_BLOCK_VIDEO: {
        PageBlockWrapView view = new PageBlockWrapView(context);
        view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        SettingHolder holder = new SettingHolder(view);
        switch (viewType) {
          case ListItem.TYPE_PAGE_BLOCK_SLIDESHOW: {
            view.initWithMode(PageBlockWrapView.MODE_SLIDESHOW, themeProvider);
            break;
          }
          case ListItem.TYPE_PAGE_BLOCK_EMBEDDED: {
            view.initWithMode(PageBlockWrapView.MODE_EMBEDDED, themeProvider);
            // holder.setIsRecyclable(false);
            break;
          }
          case ListItem.TYPE_PAGE_BLOCK_TABLE: {
            view.initWithMode(PageBlockWrapView.MODE_TABLE, themeProvider);
            break;
          }
        }
        return holder;
      }
      case ListItem.TYPE_SMART_PROGRESS: {
        ProgressComponentView componentView = new ProgressComponentView(context) { // FIXME theme
          @Override
          protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, getParent() != null ? MeasureSpec.makeMeasureSpec(Math.max(0, ((View) getParent()).getMeasuredHeight() - measureHeightForType(ListItem.TYPE_FAKE_PAGER_TOPVIEW) - measureHeightForType(ListItem.TYPE_SHADOW_BOTTOM) - measureHeightForType(ListItem.TYPE_SHADOW_BOTTOM)), MeasureSpec.EXACTLY) : 0);
          }

          @Override
          protected void onDraw (Canvas c) {
            int height = getMeasuredHeight();
            int centerY = getPaddingTop() + (height - getPaddingTop() - getPaddingBottom()) / 2;

            int top = getTop();
            int availHeight = (height - getPaddingTop() - getPaddingBottom()) - Math.abs(top);
            if (availHeight <= 0) {
              return;
            }

            if (top != 0) {
              c.save();
              float offsetRatio = 1f - ((float) availHeight / (float) height);
              int desiredCenterY = getPaddingTop() + (int) ((float) (measureHeightForType(ListItem.TYPE_FAKE_PAGER_TOPVIEW) / 2 + measureHeightForType(ListItem.TYPE_SHADOW_BOTTOM)) * offsetRatio) + availHeight / 2 * (int) Math.signum(top);
              c.translate(0, desiredCenterY - centerY);
            }

            super.onDraw(c);

            if (top != 0) {
              c.restore();
            }
          }
        };
        componentView.setPadding(0, measureHeightForType(ListItem.TYPE_FAKE_PAGER_TOPVIEW), 0, 0);
        componentView.initBig(1f);

        componentView.setUseStupidInvalidate();
        componentView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new SettingHolder(componentView);
      }
      case ListItem.TYPE_SMART_EMPTY: {
        EmptySmartView smartView = new EmptySmartView(context) {
          @Override
          protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, getParent() != null ? MeasureSpec.makeMeasureSpec(Math.max(0, ((View) getParent()).getMeasuredHeight() - measureHeightForType(ListItem.TYPE_FAKE_PAGER_TOPVIEW) - measureHeightForType(ListItem.TYPE_SHADOW_BOTTOM) - measureHeightForType(ListItem.TYPE_SHADOW_BOTTOM)), MeasureSpec.EXACTLY) : 0);
          }
        };
        smartView.setPadding(0, measureHeightForType(ListItem.TYPE_FAKE_PAGER_TOPVIEW), 0, 0);
        if (themeProvider != null) {
          themeProvider.addThemeInvalidateListener(smartView);
        }
        return new SettingHolder(smartView);
      }
      case ListItem.TYPE_CHAT_BETTER: {
        BetterChatView view = new BetterChatView(context, tdlib);
        RippleSupport.setSimpleWhiteBackground(view);
        view.setOnClickListener(onClickListener);
        view.setOnLongClickListener(onLongClickListener);
        view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(72f)));
        if (themeProvider != null) {
          themeProvider.addThemeInvalidateListener(view);
        }
        return new SettingHolder(view);
      }
      case ListItem.TYPE_RECYCLER_HORIZONTAL: {
        CustomRecyclerView recyclerView = new CustomRecyclerView(context);
        recyclerView.setBackgroundColor(Theme.fillingColor());
        if (themeProvider != null) {
          themeProvider.addThemeBackgroundColorListener(recyclerView, ColorId.filling);
        }
        recyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, Lang.rtl()));
        recyclerView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(95f)));
        return new SettingHolder(recyclerView);
      }
      case ListItem.TYPE_CHAT_VERTICAL:
      case ListItem.TYPE_CHAT_VERTICAL_FULLWIDTH: {
        VerticalChatView chatView = new VerticalChatView(context, tdlib);
        if (viewType == ListItem.TYPE_CHAT_VERTICAL_FULLWIDTH) {
          chatView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(86f)));
        } else {
          chatView.setLayoutParams(new RecyclerView.LayoutParams(Screen.dp(72f), Screen.dp(95f)));
        }
        chatView.setOnClickListener(onClickListener);
        chatView.setOnLongClickListener(onLongClickListener);
        if (themeProvider != null) {
          chatView.setThemeProvider(themeProvider);
        }
        return new SettingHolder(chatView);
      }
      case ListItem.TYPE_CHAT_SMALL:
      case ListItem.TYPE_CHAT_SMALL_SELECTABLE: {
        SmallChatView chatView = new SmallChatView(context, tdlib);
        if (viewType == ListItem.TYPE_CHAT_SMALL_SELECTABLE) {
          chatView.setPadding(0, 0, Screen.dp(18f) + Screen.dp(19f), 0);
          chatView.setEnabled(false);
          FrameLayoutFix wrapView = new FrameLayoutFix(context);
          wrapView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(62f)));
          wrapView.addView(chatView);
          wrapView.addView(CheckBoxView.simpleCheckBox(context, Lang.rtl()));
          wrapView.setOnClickListener(onClickListener);
          wrapView.setOnLongClickListener(onLongClickListener);
          Views.setClickable(wrapView);
          RippleSupport.setSimpleWhiteBackground(wrapView, themeProvider);
          return new SettingHolder(wrapView);
        } else {
          chatView.setOnClickListener(onClickListener);
          chatView.setOnLongClickListener(onLongClickListener);
          RippleSupport.setSimpleWhiteBackground(chatView, themeProvider);
          return new SettingHolder(chatView);
        }
      }
      case ListItem.TYPE_LIVE_LOCATION_PROMO: {
        LiveLocationView v = new LiveLocationView(context);
        ViewSupport.setThemedBackground(v, ColorId.file, themeProvider);
        return new SettingHolder(v);
      }
      case ListItem.TYPE_COLOR_PICKER: {
        int toneHeight = Screen.dp(192f);
        int paletteHeight = Screen.dp(18f + 8f + 4f);
        int inputHeight = Screen.dp(60f + 2f);
        int barHeight = Screen.dp(42f);
        int marginBottom = Screen.dp(12f);
        int viewHeight = toneHeight + paletteHeight * 2 + inputHeight * 2 + barHeight + marginBottom;

        FrameLayout.LayoutParams params;

        FrameLayoutFix contentView = new FrameLayoutFix(context);
        contentView.setFocusable(true);
        contentView.setFocusableInTouchMode(true);
        contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, viewHeight));
        // ViewSupport.setThemedBackground(contentView, ColorId.filling, themeProvider);
        ColorToneView pickerView = new ColorToneView(context);
        pickerView.setListener(adapter);
        pickerView.setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
        params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, toneHeight);
        if (COLOR_PICKER_PALETTE_BOTTOM)
          params.topMargin = inputHeight * 2;
        pickerView.setLayoutParams(params);
        contentView.addView(pickerView);

        // Palette

        ColorPaletteView paletteView;

        params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, paletteHeight);
        params.topMargin = toneHeight;
        if (COLOR_PICKER_PALETTE_BOTTOM)
          params.topMargin += inputHeight * 2;

        paletteView = new ColorPaletteView(context, false);
        paletteView.setId(R.id.color_huePalette);
        paletteView.setValueListener(adapter);
        paletteView.setPadding(Screen.dp(12f), Screen.dp(8f), Screen.dp(12f), 0);
        paletteView.setLayoutParams(params);
        contentView.addView(paletteView);

        // Transparent palette

        params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, paletteHeight);
        params.topMargin = toneHeight + paletteHeight;
        if (COLOR_PICKER_PALETTE_BOTTOM)
          params.topMargin += inputHeight * 2;

        paletteView = new ColorPaletteView(context, true);
        paletteView.setId(R.id.color_alphaPalette);
        paletteView.setValueListener(adapter);
        paletteView.setPadding(Screen.dp(12f), Screen.dp(8f), Screen.dp(12f), 0);
        paletteView.setLayoutParams(params);
        contentView.addView(paletteView);

        // Text

        LinearLayout.LayoutParams lParams;
        MaterialEditTextGroup editText;

        int[][][] data = new int[][][] {
          {
            {
              R.id.color_hex,
              R.id.color_red,
              R.id.color_green,
              R.id.color_blue,
              R.id.color_alpha,
            },
            {
              R.string.ColorHex,
              R.string.ColorRed,
              R.string.ColorGreen,
              R.string.ColorBlue,
              R.string.ColorAlpha
            }
          },
          {
            {
              R.id.color_default,
              R.id.color_hue,
              R.id.color_saturation,
              R.id.color_lightness,
              R.id.color_alphaPercentage,
            },
            {
              R.string.ColorDefault,
              R.string.ColorHue,
              R.string.ColorSaturation,
              R.string.ColorLightness,
              R.string.ColorAlphaPercentage
            }
          },
        };

        int dataIndex = 0;
        for (int[][] info : data) {
          int[] ids = info[0];
          int[] placeholderRes = info[1];

          int firstInputWidth = Screen.dp(84f);
          int inputSpacing = Screen.dp(14f);

          LinearLayout ll = new LinearLayout(context);
          ll.setOrientation(LinearLayout.HORIZONTAL);
          params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, inputHeight);
          params.topMargin = inputHeight * dataIndex;
          if (COLOR_PICKER_PALETTE_BOTTOM)
            params.topMargin -= Screen.dp(8f);
          else
            params.topMargin += toneHeight + paletteHeight * 2;
          params.leftMargin = inputSpacing + firstInputWidth;
          params.rightMargin = inputSpacing;
          ll.setLayoutParams(params);

          int inputIndex = 0;
          for (int id : ids) {
            editText = new MaterialEditTextGroup(context);
            if (id != R.id.color_default) {
              editText.getEditText().setSelectAllOnFocus(true);
            }
            editText.getEditText().getLayoutParams().height = Screen.dp(40f);
            editText.setId(id);
            int imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI;
            if (id == R.id.color_default) {
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                editText.getEditText().setShowSoftInputOnFocus(false);
              }
              editText.setTextColorId(ColorId.textPlaceholder);
            } else if (id == R.id.color_hex) {
              editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
              editText.getEditText().setFilters(new InputFilter[] {
                new InputFilter.AllCaps(),
                new InputFilter.LengthFilter(8),
                new AcceptFilter() {
                  @Override
                  protected boolean accept (char c) {
                    return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
                  }
                }
              });
            } else if (id == R.id.color_hue || id == R.id.color_lightness || id == R.id.color_saturation || id == R.id.color_alphaPercentage) {
              editText.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
              editText.getEditText().setFilters(new InputFilter[] {
                new AcceptFilter() {
                  @Override
                  protected boolean accept (char c) {
                    return (c >= '0' && c <= '9') || c == '.';
                  }
                }
              });
            } else {
              editText.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
              editText.getEditText().setFilters(new InputFilter[] {
                new AcceptFilter() {
                  @Override
                  protected boolean accept (char c) {
                    return (c >= '0' && c <= '9');
                  }
                },
                new InputFilter.LengthFilter(3)
              });
            }
            if (inputIndex == ids.length - 1 || id == R.id.color_hex || id == R.id.color_default) {
              imeOptions |= EditorInfo.IME_ACTION_DONE;
            } else {
              imeOptions |= EditorInfo.IME_ACTION_NEXT;
            }
            if (id != R.id.color_default)
              editText.setTextListener(adapter);
            editText.setFocusListener(adapter);
            editText.setAlwaysActive(true);
            editText.setHint(placeholderRes[inputIndex]);
            editText.getEditText().setImeOptions(imeOptions);
            editText.applyRtl(false);
            editText.addThemeListeners(themeProvider);
            if (inputIndex == 0) {
              int marginTop = params.topMargin;
              params = new FrameLayout.LayoutParams(firstInputWidth, Screen.dp(60f));
              params.topMargin = marginTop;
              params.leftMargin = inputSpacing;
              editText.setLayoutParams(params);
              contentView.addView(editText);
            } else {
              lParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(60f), 1f);
              lParams.leftMargin = inputSpacing;
              editText.setLayoutParams(lParams);
              ll.addView(editText);
            }
            inputIndex++;
            if (inputIndex != ids.length) {
              editText.getEditText().setNextFocusDownId(ids[inputIndex]);
            }
          }
          contentView.addView(ll);
          dataIndex++;
        }

        // Buttons

        int[][] buttonIds = new int[][] {
          {
            R.id.btn_colorUndo,
            R.id.btn_colorRedo,
          },
          {
            R.id.btn_colorCopy,
            R.id.btn_colorPaste,
            R.id.btn_colorCalculate,
            R.id.btn_colorClear,
            R.id.btn_colorSave,
          }
        };

        boolean first = true;

        for (int[] ids : buttonIds) {
          params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, barHeight);
          params.gravity = first ? Gravity.RIGHT : Gravity.LEFT;
          params.topMargin = toneHeight + paletteHeight * 2 + inputHeight * 2;
          params.leftMargin = params.rightMargin = Screen.dp(12f);

          LinearLayout ll = new LinearLayout(context);
          ll.setOrientation(LinearLayout.HORIZONTAL);
          ll.setGravity(Gravity.BOTTOM);
          ll.setLayoutParams(params);

          for (int id : ids) {
            NonMaterialButton btn = new NonMaterialButton(context);
            if (first) {
              btn.setBackgroundColorId(ColorId.filling);
            }
            btn.setPressureListener(adapter);
            btn.setId(id);
            btn.setOnClickListener(onClickListener);
            btn.setOnLongClickListener(onLongClickListener);
            lParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(32f));

            if (first) {
              lParams.leftMargin = Screen.dp(6f);
            } else {
              lParams.rightMargin = Screen.dp(6f);
            }
            if (id == R.id.btn_colorUndo) {
              btn.setIcon(R.drawable.baseline_undo_18);
              btn.setPadding(Screen.dp(6f), 0, Screen.dp(6f), 0);
            } else if (id == R.id.btn_colorRedo) {
              btn.setIcon(R.drawable.baseline_redo_18);
              btn.setPadding(Screen.dp(6f), 0, Screen.dp(6f), 0);
            } else if (id == R.id.btn_colorClear) {
              btn.setIcon(R.drawable.baseline_delete_18);
            } else if (id == R.id.btn_colorSave) {
              btn.setIcon(R.drawable.baseline_playlist_add_18);
            } else if (id == R.id.btn_colorCalculate) {
              btn.setIcon(R.drawable.baseline_opacity_18);
            } else if (id == R.id.btn_colorCopy) {
              btn.setIcon(R.drawable.baseline_content_copy_18);
            } else if (id == R.id.btn_colorPaste) {
              btn.setIcon(R.drawable.baseline_content_paste_18);
            }

            btn.setLayoutParams(lParams);
            ll.addView(btn);
          }

          contentView.addView(ll);
          first = false;
        }

        return new SettingHolder(contentView);
      }
      case ListItem.TYPE_CHART_HEADER_DETACHED:
      case ListItem.TYPE_CHART_HEADER: {
        ChartHeaderView headerView = new ChartHeaderView(context);
        headerView.setPadding(0, Screen.dp(8f), 0, Screen.dp(4f));
        headerView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        //ViewSupport.setThemedBackground(headerView, ColorId.filling, themeProvider);
        if (themeProvider != null) {
          themeProvider.addThemeInvalidateListener(headerView);
        }
        return new SettingHolder(headerView);
      }
      case ListItem.TYPE_CHART_LINEAR:
      case ListItem.TYPE_CHART_DOUBLE_LINEAR:
      case ListItem.TYPE_CHART_STACK_BAR:
      case ListItem.TYPE_CHART_STACK_PIE: {
        int type;
        switch (viewType) {
          case ListItem.TYPE_CHART_LINEAR:
            type = ChartDataUtil.TYPE_LINEAR;
            break;
          case ListItem.TYPE_CHART_DOUBLE_LINEAR:
            type = ChartDataUtil.TYPE_DOUBLE_LINEAR;
            break;
          case ListItem.TYPE_CHART_STACK_BAR:
            type = ChartDataUtil.TYPE_STACK_BAR;
            break;
          case ListItem.TYPE_CHART_STACK_PIE:
            type = ChartDataUtil.TYPE_STACK_PIE;
            break;
          default:
            throw new AssertionError();
        }
        ChartLayout chartLayout = new ChartLayout(context);
        chartLayout.setPadding(chartLayout.getPaddingLeft(), Screen.dp(16f), chartLayout.getPaddingRight(), chartLayout.getPaddingBottom());
        chartLayout.initWithType(tdlib, type, adapter, themeProvider);
        return new SettingHolder(chartLayout);
      }
    }
    throw new AssertionError(viewType);
  }

  public static final boolean COLOR_PICKER_PALETTE_BOTTOM = true;

  public static TextView createDescription (Context context, int viewType, @ColorId int textColorId, View.OnClickListener onClickListener, @Nullable ViewController<?> themeProvider) {
    TextView textView = new NoScrollTextView(context);
    if (viewType == ListItem.TYPE_DESCRIPTION_CENTERED) {
      textView.setGravity(Gravity.CENTER);
    } else {
      textView.setGravity((Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
    }
    textView.setMovementMethod(LinkMovementMethod.getInstance());
    textView.setTypeface(Fonts.getRobotoRegular());
    if (viewType == ListItem.TYPE_DESCRIPTION_SMALL) {
      textView.setTextColor(Theme.textDecentColor());
      textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f);
      textView.setPadding(Screen.dp(16f), 0, Screen.dp(16f), Screen.dp(12f));
      ViewUtils.setBackground(textView, new FillingDrawable(ColorId.filling));
      if (themeProvider != null) {
        themeProvider.addThemeTextColorListener(textView, ColorId.textLight);
        themeProvider.addThemeInvalidateListener(textView);
      }
    } else {
      textView.setTextColor(Theme.getColor(textColorId));
      if (themeProvider != null) {
        themeProvider.addThemeTextColorListener(textView, textColorId);
      }
      textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
      textView.setPadding(Screen.dp(16f), Screen.dp(6f), Screen.dp(16f), Screen.dp(12f));
    }
    textView.setLinkTextColor(Theme.textLinkColor());
    textView.setHighlightColor(Theme.textLinkHighlightColor());
    if (themeProvider != null) {
      themeProvider.addThemeLinkTextColorListener(textView, ColorId.textLink);
      themeProvider.addThemeHighlightColorListener(textView, ColorId.textLinkPressHighlight);
    }
    textView.setOnClickListener(onClickListener);
    textView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    return textView;
  }
}
