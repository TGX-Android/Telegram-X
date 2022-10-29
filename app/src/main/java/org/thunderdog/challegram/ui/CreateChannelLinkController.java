/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 05/02/2016 at 18:17
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.StringRes;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.ComplexHeaderView;
import org.thunderdog.challegram.navigation.ComplexScrollView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.util.Unlockable;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.PrefixEditText;
import org.thunderdog.challegram.widget.RadioView;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import me.vkryl.td.ChatId;
import me.vkryl.td.TdConstants;

public class CreateChannelLinkController extends ViewController<CreateChannelLinkController.Args> implements View.OnClickListener, Client.ResultHandler, Unlockable {
  public static class Args {
    private TdApi.Chat chat;
    private ImageFile photo;

    public Args (TdApi.Chat chat, ImageFile photo) {
      this.chat = chat;
      this.photo = photo;
    }
  }

  public CreateChannelLinkController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private TdApi.Chat chat;
  private ImageFile photo;

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.chat = args.chat;
    this.photo = args.photo;
  }

  private ComplexScrollView scrollView;
  private LinearLayout contentView;
  private PrefixEditText linkView;

  private RadioView publicRadio;
  private RadioView privateRadio;
  private TextView hintView;

  private ComplexHeaderView headerView;

  @Override
  public int getId () {
    return R.id.controller_newChannel_link;
  }

  @Override
  protected View onCreateView (Context context) {
    contentView = new LinearLayout(context);
    contentView.setOrientation(LinearLayout.VERTICAL);
    contentView.setPadding(0, Size.getHeaderSizeDifference(false), 0, 0);

    publicRadio = addOption(context, R.id.btn_publicChannel, true, R.string.ChannelPublic, R.string.ChannelPublicInfo, Screen.dp(33f));
    privateRadio = addOption(context, R.id.btn_privateChannel, false, R.string.ChannelPrivate, R.string.ChannelPrivateInfo, Screen.dp(2f));

    LinearLayout ll = new LinearLayout(context);
    ll.setOrientation(LinearLayout.HORIZONTAL);
    ll.setPadding(Screen.dp(16f), Screen.dp(32f), Screen.dp(16f), 0);

    ImageView iconView = new ImageView(context);
    iconView.setScaleType(ImageView.ScaleType.CENTER);
    iconView.setImageResource(R.drawable.baseline_link_24);
    iconView.setColorFilter(Theme.iconColor());
    addThemeFilterListener(iconView, R.id.theme_color_icon);
    iconView.setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(24f), Screen.dp(46f)));

    LinearLayout.LayoutParams params;

    params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.leftMargin = Screen.dp(32f);

    int padding = Screen.dp(9f);

    linkView = new PrefixEditText(context);
    linkView.setFocusable(false);
    linkView.setFocusableInTouchMode(false);
    linkView.setId(R.id.edit_link);
    linkView.setTextColor(Theme.textAccentColor());
    addThemeTextAccentColorListener(linkView);
    addThemeInvalidateListener(linkView);
    if (Lang.rtl()) {
      linkView.setPadding(padding, padding, 0, padding);
    } else {
      linkView.setPadding(0, padding, padding, padding);
    }
    linkView.setSingleLine(true);
    linkView.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    linkView.setInputType(linkView.getInputType() | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    linkView.setLayoutParams(params);
    linkView.setPrefix("t.me/");

    ll.addView(iconView);
    ll.addView(linkView);

    contentView.addView(ll);

    hintView = new NoScrollTextView(context);
    hintView.setTextColor(Theme.textDecentColor());
    addThemeTextDecentColorListener(hintView);
    hintView.setTypeface(Fonts.getRobotoRegular());
    hintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
    hintView.setGravity(Lang.gravity());
    hintView.setPadding(Screen.dp(72f), Screen.dp(5f), Screen.dp(16f), Screen.dp(16f));
    hintView.setText(Lang.getString(R.string.ChannelUsernameHelp));
    contentView.addView(hintView);

    // HeaderView

    headerView = new ComplexHeaderView(context, tdlib, this);
    headerView.setNoExpand(true);
    headerView.initWithController(this, true);
    headerView.setInnerMargins(Screen.dp(56f), 0);
    headerView.setText(chat.title, Lang.plural(R.string.xMembers, 1));
    if (photo == null) {
      headerView.setAvatarPlaceholder(tdlib.chatPlaceholder(chat, true, ComplexHeaderView.getBaseAvatarRadiusDp(), null));
    } else {
      headerView.setAvatar(photo);
    }

    final Runnable scroller = () -> scrollView.fullScroll(View.FOCUS_DOWN);

    scrollView = new ComplexScrollView(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        post(scroller);
      }
    };
    scrollView.setHeaderView(headerView);
    ViewSupport.setThemedBackground(scrollView, R.id.theme_color_filling, this);
    scrollView.addView(contentView);
    scrollView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    return scrollView;
  }

  private long getSupergroupId () {
    return ChatId.toSupergroupId(chat.id);
  }

  private String username;

  private void updateLink () {
    if (privateRadio.isChecked()) {
      linkView.setEditable(false);
      if (inviteLink == null) {
        linkView.setSuffix("...");
        loadInviteLink();
      } else {
        linkView.setSuffix(inviteLink);
      }
    } else {
      linkView.setEditable(true);
      linkView.setSuffix(username == null ? "" : username);
    }
  }

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.btn_publicChannel: {
        if (privateRadio.isChecked()) {
          privateRadio.toggleChecked();
          publicRadio.toggleChecked();
          updateLink();
          hintView.setText(Lang.getString(R.string.ChannelUsernameHelp));
        }
        break;
      }
      case R.id.btn_privateChannel: {
        if (publicRadio.isChecked()) {
          username = linkView.getSuffix();
          publicRadio.toggleChecked();
          privateRadio.toggleChecked();
          updateLink();
          hintView.setText(Lang.getString(R.string.ChannelPrivateLinkHelp));
        }
        break;
      }
    }
  }

  private RadioView addOption (Context context, @IdRes int id, boolean isChecked, @StringRes int titleResId, @StringRes int hintResId, int offset) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.topMargin = offset;

    FrameLayoutFix content = new FrameLayoutFix(context);
    content.setId(id);
    content.setPadding(0, Screen.dp(8f), 0, Screen.dp(10f));
    content.setOnClickListener(this);
    content.setLayoutParams(lp);
    Views.setClickable(content);
    RippleSupport.setTransparentSelector(content);

    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(Screen.dp(20f), Screen.dp(20f));
    params.topMargin = Screen.dp(2f);
    if (Lang.rtl()) {
      params.gravity = Gravity.RIGHT;
      params.rightMargin = Screen.dp(18f);
    } else {
      params.gravity = Gravity.LEFT;
      params.leftMargin = Screen.dp(18f);
    }
    RadioView radio = new RadioView(context);
    radio.setChecked(isChecked, false);
    radio.setLayoutParams(params);
    content.addView(radio);
    addThemeInvalidateListener(radio);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    if (Lang.rtl()) {
      params.gravity = Gravity.RIGHT;
      params.rightMargin = Screen.dp(73f);
      params.leftMargin = Screen.dp(12f);
    } else {
      params.gravity = Gravity.LEFT;
      params.leftMargin = Screen.dp(73f);
      params.rightMargin = Screen.dp(12f);
    }

    TextView text = new NoScrollTextView(context);
    text.setGravity(Lang.gravity());
    text.setText(Lang.getString(titleResId));
    text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    text.setTextColor(Theme.textAccentColor());
    addThemeTextAccentColorListener(text);
    text.setTypeface(Fonts.getRobotoRegular());
    text.setSingleLine();
    text.setEllipsize(TextUtils.TruncateAt.END);
    text.setLayoutParams(params);
    content.addView(text);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = Screen.dp(24f);
    if (Lang.rtl()) {
      params.gravity = Gravity.RIGHT;
      params.rightMargin = Screen.dp(73f);
      params.leftMargin = Screen.dp(12f);
    } else {
      params.gravity = Gravity.LEFT;
      params.leftMargin = Screen.dp(73f);
      params.rightMargin = Screen.dp(12f);
    }

    text = new NoScrollTextView(context);
    text.setGravity(Lang.gravity());
    text.setText(Lang.getString(hintResId));
    text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
    text.setTextColor(Theme.textDecentColor());
    text.setTypeface(Fonts.getRobotoRegular());
    addThemeTextDecentColorListener(text);
    text.setLayoutParams(params);
    content.addView(text);

    contentView.addView(content);

    return radio;
  }

  private boolean linkRequested;

  private void loadInviteLink () {
    if (!linkRequested) {
      linkRequested = true;
      tdlib.getPrimaryChatInviteLink(chat.id, this);
    }
  }

  private String inviteLink;

  @Override
  public void onResult (TdApi.Object object) {
    switch (object.getConstructor()) {
      case TdApi.ChatInviteLink.CONSTRUCTOR: {
        inviteLink = StringUtils.urlWithoutProtocol(((TdApi.ChatInviteLink) object).inviteLink);
        for (String host : TdConstants.TME_HOSTS) {
          if (inviteLink.startsWith(host)) {
            inviteLink = inviteLink.substring(host.length() + 1);
            break;
          }
        }

        UI.post(() -> updateLink());
        break;
      }
      case TdApi.Error.CONSTRUCTOR: {
        UI.showError(object);
        break;
      }
    }
  }

  @Override
  public View getCustomHeaderCell () {
    return headerView;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  protected int getHeaderHeight () {
    return Size.getHeaderBigPortraitSize(false);
  }

  @Override
  protected int getFloatingButtonId () {
    return R.drawable.baseline_arrow_forward_24;
  }

  @Override
  protected void onFloatingButtonPressed () {
    if (publicRadio.isChecked()) {
      String username = linkView.getSuffix().trim();
      if (username.length() < 5) {
        UI.showToast(R.string.PublicLinkTooShort, Toast.LENGTH_SHORT);
        return;
      }
      if (username.length() != 0 && !TD.matchUsername(username)) {
        UI.showToast(R.string.PublicLinkIsInvalid, Toast.LENGTH_SHORT);
        return;
      }
      setUsername(username);
    } else {
      nextStep();
    }
  }

  private void setEnabled (boolean enabled) {
    privateRadio.setEnabled(enabled);
    publicRadio.setEnabled(enabled);
    linkView.setEnabled(enabled);
  }

  private boolean usernameRequested;

  @Override
  public void unlock () {
    usernameRequested = false;
    setEnabled(true);
  }

  private void setUsername (String username) {
    if (usernameRequested) {
      return;
    }
    usernameRequested = true;
    setEnabled(false);
    tdlib.client().send(new TdApi.SetSupergroupUsername(getSupergroupId(), username), object -> {
      switch (object.getConstructor()) {
        case TdApi.Ok.CONSTRUCTOR: {
          UI.post(this::nextStep);
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          UI.showError(object);
          UI.unlock(CreateChannelLinkController.this);
          break;
        }
        default: {
          Log.unexpectedTdlibResponse(object, TdApi.SetSupergroupUsername.class, TdApi.Ok.class);
          UI.unlock(CreateChannelLinkController.this);
          break;
        }
      }
    });
  }

  private void nextStep () {
    Keyboard.hide(linkView);
    ContactsController c = new ContactsController(context, tdlib);
    c.initWithMode(ContactsController.MODE_CHANNEL_MEMBERS);
    c.setChat(chat);
    navigateTo(c);
  }

  @Override
  public void onFocus () {
    super.onFocus();
    if (publicRadio.isChecked()) {
      linkView.setFocusable(true);
      linkView.setFocusableInTouchMode(true);
    }
    if (stackSize() == 3 && stackItemAt(1) instanceof CreateChannelController) {
      destroyStackItemAt(1);
    }
  }

  // Updates

  /*@Override
  public void updateChatTitle (long chatId, String title) {
    if (chat != null && chat.id == chatId) {
      chat.title = title;
    }
  }

  @Override
  public void updateChatPhoto (long chatId, TdApi.ChatPhoto photo) {
    if (chat != null && chat.id == chatId) {
      chat.photo = photo;
    }
  }

  @Override
  public void updateChannel (TdApi.Channel channel) {
    if (chat != null && chat.type.getConstructor() == TdApi.ChannelChatInfo.CONSTRUCTOR && ((TdApi.ChannelChatInfo) chat.type).channel.id == channel.id) {
      ((TdApi.ChannelChatInfo) chat.type).channel = channel;
    }
  }*/
}
