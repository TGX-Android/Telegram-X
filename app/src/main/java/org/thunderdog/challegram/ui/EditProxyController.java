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
 * File created on 07/02/2017
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Rect;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibContext;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.widget.FillingDecoration;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.text.AcceptFilter;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;

public class EditProxyController extends EditBaseController<EditProxyController.Args> implements SettingsAdapter.TextChangeListener, Menu, View.OnClickListener {
  public static final int MODE_SOCKS5 = 1;
  public static final int MODE_MTPROTO = 2;
  public static final int MODE_HTTP = 3;

  public static class Args {
    public int mode;
    public Settings.Proxy existingProxy;

    public Args (int mode) {
      this.mode = mode;
    }

    public Args (Settings.Proxy proxy) {
      switch (proxy.type.getConstructor()) {
        case TdApi.ProxyTypeSocks5.CONSTRUCTOR:
          this.mode = MODE_SOCKS5;
          break;
        case TdApi.ProxyTypeMtproto.CONSTRUCTOR:
          this.mode = MODE_MTPROTO;
          break;
        case TdApi.ProxyTypeHttp.CONSTRUCTOR:
          this.mode = MODE_HTTP;
          break;
        default:
          throw new IllegalArgumentException("proxy.type == " + proxy.type);
      }
      this.existingProxy = proxy;
    }
  }

  public EditProxyController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public CharSequence getName () {
    switch (getArgumentsStrict().mode) {
      case MODE_SOCKS5:
        return Lang.getString(R.string.Socks5Proxy);
      case MODE_MTPROTO:
        return Lang.getString(R.string.MtprotoProxy);
      case MODE_HTTP:
        return Lang.getString(R.string.HttpProxy);
    }
    return "";
  }

  @Override
  public int getId () {
    return R.id.controller_proxy;
  }

  private SettingsAdapter adapter;
  private ListItem server, port;

  private ListItem username, password;
  private ListItem secret;
  private ListItem tcpOnly;

  @Override
  protected int getRecyclerBackgroundColorId () {
    return R.id.theme_color_background;
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void modifyEditText (ListItem item, ViewGroup parent, MaterialEditTextGroup editText) {
        switch (item.getId()) {
          case R.id.edit_proxy_server: {
            editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            editText.getEditText().setIsPassword(false);
            break;
          }
          case R.id.edit_proxy_port: {
            editText.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
            editText.getEditText().setIsPassword(false);
            break;
          }
          case R.id.edit_proxy_username: {
            editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            editText.getEditText().setIsPassword(false);
            break;
          }
          case R.id.edit_proxy_password: {
            editText.getEditText().setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
            editText.getEditText().setIsPassword(true);
            break;
          }
        }
      }

      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        switch (item.getId()) {
          case R.id.edit_proxy_tcpOnly: {
            view.getToggler().setRadioEnabled(tcpOnly.getBoolValue(), isUpdate);
            break;
          }
        }
      }
    };
    adapter.setLockFocusOn(this, getArgumentsStrict().existingProxy == null);
    adapter.setTextChangeListener(this);

    final Settings.Proxy proxy = getArgumentsStrict().existingProxy;
    final int mode = getArgumentsStrict().mode;

    int baseFillCount = 2;

    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.Connection));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(server = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.edit_proxy_server, 0, R.string.UseProxyServer)
      .setStringValue(proxy != null ? proxy.server : "")
      .setInputFilters(new InputFilter[]{ new InputFilter.LengthFilter(255) }));
    items.add((port = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.edit_proxy_port, 0, R.string.UseProxyPort)
      .setStringValue(proxy != null ? Integer.toString(proxy.port) : "")
      .setInputFilters(new InputFilter[] {new InputFilter.LengthFilter(255)})));
    if (mode == MODE_HTTP) {
      items.add((tcpOnly = new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.edit_proxy_tcpOnly, 0, R.string.HttpProxyTransparent, R.id.edit_proxy_tcpOnly, false).setBoolValue(proxy != null && !((TdApi.ProxyTypeHttp) proxy.type).httpOnly)));
      baseFillCount++;
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    if (mode == MODE_HTTP) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.HttpProxyTransparentHint));
    }

    final int fillIndex, fillCount;

    switch (mode) {
      case MODE_SOCKS5:
      case MODE_HTTP: {
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.ProxyCredentialsOptional));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        fillIndex = items.size();
        items.add(username = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.edit_proxy_username, 0, R.string.ProxyUsernameHint)
          .setStringValue(proxy != null ? Settings.getProxyUsername(proxy.type) : null));
        items.add(password = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.edit_proxy_password, 0, R.string.ProxyPasswordHint)
          .setOnEditorActionListener(new SimpleEditorActionListener(EditorInfo.IME_ACTION_DONE, this))
          .setStringValue(proxy != null ? Settings.getProxyPassword(proxy.type) : null));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        fillCount = 2;
        break;
      }
      case MODE_MTPROTO: {
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.ProxyCredentials));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        fillIndex = items.size();
        items.add(secret = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.edit_proxy_secret, 0, R.string.ProxySecretHint)
          .setInputFilters(new InputFilter[] {new AcceptFilter() {
            @Override
            protected boolean accept (char c) {
              return Strings.isHex(c);
            }
          }})
          .setStringValue(proxy != null ? ((TdApi.ProxyTypeMtproto) proxy.type).secret : null));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        fillCount = 1;
        break;
      }
      default:
        throw new IllegalStateException();
    }

    adapter.setItems(items, false);

    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
      @Override
      public void getItemOffsets (Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        ListItem item = (ListItem) view.getTag();
        int itemId = item != null ? item.getId() : 0;
        switch (itemId) {
          case R.id.edit_proxy_port:
          case R.id.edit_proxy_password:
          case R.id.edit_proxy_secret:
            outRect.bottom = Screen.dp(12f);
            break;
          default:
            outRect.bottom = 0;
            break;
        }
      }
    });
    recyclerView.addItemDecoration(new FillingDecoration(recyclerView, this).addRange(2, 2 + baseFillCount).addRange(fillIndex, fillIndex + fillCount));
    recyclerView.setAdapter(adapter);

    setDoneVisible(false);
    setDoneIcon(R.drawable.baseline_check_24);
  }

  @Override
  public void onClick (View view) {
    ListItem item = (ListItem) view.getTag();
    switch (item.getId()) {
      case R.id.edit_proxy_tcpOnly: {
        item.setBoolValue(adapter.toggleView(view));
        checkDoneVisibility(0);
        break;
      }
    }
  }

  @Override
  public void onTextChanged (int id, ListItem item, MaterialEditTextGroup v, String text) {
    switch (id) {
      case R.id.edit_proxy_server:
      case R.id.edit_proxy_port:
      case R.id.edit_proxy_username:
      case R.id.edit_proxy_password:
      case R.id.edit_proxy_secret: {
        checkDoneVisibility(id);
        break;
      }
    }
  }

  private void checkDoneVisibility (int id) {
    if (id != 0) {
      adapter.updateEditTextById(id, false, false);
    }
    final String server = this.server.getStringValue().trim();
    final String port = this.port.getStringValue().trim();
    setDoneVisible(!server.isEmpty() && !port.isEmpty());
  }

  @Override
  protected int getMenuId () {
    return 0; // return getArgumentsStrict().existingProxy != null ? R.id.menu_proxy : 0;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    switch (id) {
      case R.id.menu_proxy: {
        if (tdlib.myUserId() != 0) {
          header.addForwardButton(menu, this, getHeaderIconColorId());
        }
        header.addDeleteButton(menu, this, getHeaderIconColorId());
        break;
      }
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    switch (id) {
      case R.id.menu_btn_forward: {
        Keyboard.hide(getLockFocusView());
        tdlib.getProxyLink(getArgumentsStrict().existingProxy, url -> tdlib.ui().shareProxyUrl(new TdlibContext(context, context.currentTdlib()), url));
        break;
      }
      case R.id.menu_btn_delete: {
        if (Settings.instance().removeProxy(getArgumentsStrict().existingProxy.id)) {
          onSaveCompleted();
        }
        break;
      }
    }
  }

  private void addProxy (String server, int port, final TdApi.ProxyType type) {
    setInProgress(true);
    // Calling TDLib method just to validate input
    tdlib.client().send(new TdApi.AddProxy(server, port, false, type), result -> {
      final boolean ok;
      switch (result.getConstructor()) {
        case TdApi.Error.CONSTRUCTOR:
          UI.showError(result);
          ok = false;
          break;
        case TdApi.Proxy.CONSTRUCTOR:
          ok = true;
          break;
        default:
          ok = false;
          break;
      }
      tdlib.ui().post(() -> {
        setInProgress(false);
        if (!isDestroyed() && ok) {
          int proxyId = getArgumentsStrict().existingProxy != null ? getArgumentsStrict().existingProxy.id : Settings.PROXY_ID_NONE;
          Settings.instance().addOrUpdateProxy(server, port, type, null, true, proxyId);
          if (navigationController != null) {
            ViewController<?> c = navigationController.getPreviousStackItem();
            if (c != null && c.getId() != R.id.controller_proxyList) {
              navigationController.getStack().insertBack(new SettingsProxyController(context, tdlib));
            }
          }
          navigateBack();
        }
      });
    });
  }

  @Override
  protected void onProgressStateChanged (boolean inProgress) {
    setStackLocked(inProgress);
  }

  @Override
  protected boolean onDoneClick () {
    final String server = this.server.getStringValue().trim();
    final String portRaw = this.port.getStringValue().trim();
    final String port = StringUtils.isNumeric(portRaw) ? portRaw : "";

    final boolean invalidServer = server.isEmpty();
    final boolean invalidPort = port.isEmpty();

    if (invalidServer)
      adapter.updateEditTextById(R.id.edit_proxy_server, false, true);
    if (invalidPort)
      adapter.updateEditTextById(R.id.edit_proxy_port, false, true);
    if (invalidServer || invalidPort)
      return false;

    TdApi.ProxyType type;
    switch (getArgumentsStrict().mode) {
      case MODE_SOCKS5:
        type = new TdApi.ProxyTypeSocks5(this.username.getStringValue(), this.password.getStringValue());
        break;
      case MODE_MTPROTO:
        type = new TdApi.ProxyTypeMtproto(this.secret.getStringValue());
        break;
      case MODE_HTTP:
        type = new TdApi.ProxyTypeHttp(this.username.getStringValue(), this.password.getStringValue(), !this.tcpOnly.getBoolValue());
        break;
      default:
        throw new IllegalStateException();
    }
    addProxy(server, StringUtils.parseInt(port), type);
    return true;
  }
}
