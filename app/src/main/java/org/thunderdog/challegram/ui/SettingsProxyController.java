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
 * File created on 06/05/2018
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.user.RemoveHelper;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.MoreDelegate;
import org.thunderdog.challegram.telegram.ConnectionListener;
import org.thunderdog.challegram.telegram.ConnectionState;
import org.thunderdog.challegram.telegram.GlobalProxyPingListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibContext;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.RadioView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.lambda.CancellableRunnable;

public class SettingsProxyController extends RecyclerViewController<Void> implements View.OnLongClickListener, View.OnClickListener, Settings.ProxyChangeListener, ConnectionListener, MoreDelegate, GlobalProxyPingListener {
  public SettingsProxyController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_proxyList;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Proxy);
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_more;
  }

  @Override
  protected void openMoreMenu () {
    IntList ids = new IntList(2);
    StringList strings = new StringList(2);
    ids.append(R.id.btn_toggleErrors);
    strings.append(Settings.instance().checkProxySetting(Settings.PROXY_FLAG_SHOW_ERRORS) ? R.string.ProxyHideErrors : R.string.ProxyShowErrors);

    if (proxies.size() > 1 && calculatePongs() == proxies.size()) {
      List<Settings.Proxy> sorted = new ArrayList<>(proxies);
      sortProxies(sorted);
      if (!sorted.equals(proxies)) {
        ids.append(R.id.btn_sortByPing);
        strings.append(R.string.ProxyReorderByPing);
      }
    }

    if (BuildConfig.DEBUG) {
      ids.append(R.id.btn_test);
      strings.append("Auto-select proxy");
    }

    showMore(ids.get(), strings.get(), 0);
  }

  private static void sortProxies (List<Settings.Proxy> proxies) {
    Collections.sort(proxies, (a, b) -> {
      long p1 = a.pingMs >= 0 ? a.pingMs : Long.MAX_VALUE;
      long p2 = b.pingMs >= 0 ? b.pingMs : Long.MAX_VALUE;
      if (p1 != p2) {
        return Long.compare(p1, p2);
      }
      boolean h1 = a.proxy != null;
      boolean h2 = b.proxy != null;
      if (h1 != h2) {
        return h1 ? -1 : 1;
      }
      if (h1) {
        int t1 = Settings.getProxyDefaultOrder(a.proxy.type);
        int t2 = Settings.getProxyDefaultOrder(b.proxy.type);
        if (t1 != t2) {
          return Integer.compare(t1, t2);
        }
      }
      return a.compareTo(b);
    });
  }

  @Override
  public void onMoreItemPressed (int id) {
    switch (id) {
      case R.id.btn_test: {
        tdlib.resolveConnectionIssues();
        break;
      }
      case R.id.btn_toggleErrors: {
        Settings.instance().toggleProxySetting(Settings.PROXY_FLAG_SHOW_ERRORS);
        if (noProxy.pingError != null) {
          adapter.updateValuedSettingByPosition(indexOfProxy(noProxy.id));
        }
        int proxyIndex = 0;
        for (Settings.Proxy proxy : proxies) {
          if (proxy.pingError != null) {
            adapter.updateValuedSettingByPosition(indexOfProxyCellByProxyIndex(proxyIndex, proxy.id));
          }
          proxyIndex++;
        }
        break;
      }
      case R.id.btn_sortByPing: {
        if (proxies.size() <= 1)
          return;
        List<Settings.Proxy> sorted = new ArrayList<>(proxies);
        sortProxies(sorted);
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
          @Override
          public int getOldListSize () {
            return proxies.size();
          }

          @Override
          public int getNewListSize () {
            return sorted.size();
          }

          @Override
          public boolean areItemsTheSame (int oldItemPosition, int newItemPosition) {
            return proxies.get(oldItemPosition) == sorted.get(newItemPosition);
          }

          @Override
          public boolean areContentsTheSame (int oldItemPosition, int newItemPosition) {
            return false;
          }
        });
        result.dispatchUpdatesTo(new ListUpdateCallback() {
          @Override
          public void onInserted (int position, int count) { }

          @Override
          public void onRemoved (int position, int count) { }

          @Override
          public void onChanged (int position, int count, Object payload) { }

          @Override
          public void onMoved (int fromPosition, int toPosition) {
            moveProxy(fromPosition, toPosition);
          }
        });
        saveProxiesOrder();
        break;
      }
    }
  }

  private void saveProxiesOrder () {
    int[] order;
    if (!proxies.isEmpty()) {
      order = new int[proxies.size()];
      int i = 0;
      for (Settings.Proxy proxy : proxies) {
        proxy.order = i;
        order[i++] = proxy.id;
      }
    } else {
      order = null;
    }
    Settings.instance().setProxyOrder(order);
  }

  private void moveProxy (int fromPosition, int toPosition) {
    if (fromPosition == toPosition)
      return;
    ArrayUtils.move(proxies, fromPosition, toPosition);
    int fromIndex = indexOfProxyCellByProxyIndex(fromPosition, -1);
    int toIndex = indexOfProxyCellByProxyIndex(toPosition, -1);
    if (toIndex > fromIndex) {
      adapter.moveItem(fromIndex, toIndex);
      adapter.moveItem(fromIndex - 1, toIndex - 1);
    } else {
      adapter.moveItem(fromIndex, toIndex - 1);
      adapter.moveItem(fromIndex, toIndex - 1);
    }
  }

  private SettingsAdapter adapter;
  private List<Settings.Proxy> proxies;
  private final Settings.Proxy noProxy = Settings.Proxy.noProxy(false);
  private int effectiveProxyId;

  private int calculatePongs () {
    int pongCount = 0;
    for (Settings.Proxy proxy : proxies) {
      if (proxy.pingMs >= 0 || proxy.pingMs == Settings.PROXY_TIME_EMPTY) {
        pongCount++;
      }
    }
    return pongCount;
  }

  private static final int WIN_STATE_NONE = 0;
  private static final int WIN_STATE_WINNER = 1;
  private static final int WIN_STATE_SECOND = 2;

  private void checkWinners () {
    int pongCount = 0;
    int completedCount = 0;
    long minimumMs = -1;
    for (Settings.Proxy proxy : proxies) {
      if (proxy.pingMs >= 0) {
        minimumMs = minimumMs == -1 ? proxy.pingMs : Math.min(minimumMs, proxy.pingMs);
        completedCount++;
        pongCount++;
      } else if (proxy.pingMs == Settings.PROXY_TIME_EMPTY) {
        completedCount++;
      }
    }
    boolean canDetermineWinner = pongCount > 1 && completedCount >= proxies.size();
    int localWinState = WIN_STATE_NONE;
    int winnerState = WIN_STATE_WINNER;
    if (canDetermineWinner && noProxy.pingMs >= 0 && noProxy.pingMs <= minimumMs) {
      localWinState = WIN_STATE_WINNER;
      winnerState = WIN_STATE_SECOND;
    }
    if (noProxy.winState != localWinState) {
      noProxy.winState = localWinState;
      adapter.updateValuedSettingByPosition(indexOfProxy(noProxy.id));
    }
    int proxyIndex = 0;
    for (Settings.Proxy proxy : proxies) {
      boolean isWinner = canDetermineWinner && proxy.pingMs >= 0 && proxy.pingMs == minimumMs;
      int winState = isWinner ? winnerState : WIN_STATE_NONE;
      if (proxy.winState != winState) {
        proxy.winState = winState;
        adapter.updateValuedSettingByPosition(indexOfProxyCellByProxyIndex(proxyIndex, proxy.id));
      }
      proxyIndex++;
    }
  }

  private void getState (@NonNull Settings.Proxy info, DisplayInfo out) {
    out.reset();
    int proxyId = info.id;
    if (info.pingMs == Settings.PROXY_TIME_UNSET) {
      pingProxy(info, false);
    }
    if (proxyId == effectiveProxyId) {
      switch (tdlib.connectionState()) {
        case ConnectionState.CONNECTED: {
          if (proxyLocked) {
            out.value = Lang.getString(R.string.network_Connecting);
          } else if (info.pingMs == Settings.PROXY_TIME_UNSET || info.pingMs == Settings.PROXY_TIME_LOADING) {
            out.value = Lang.getString(R.string.ProxyChecking);
            out.colorId = R.id.theme_color_textLink;
            out.connected = true;
          } else {
            out.colorId = R.id.theme_color_textLink;
            out.connected = true;
            if (info.pingMs >= 0) {
              if (info.winState != WIN_STATE_NONE) {
                out.value = Lang.getStringBoldLowercase(R.string.ProxyConnected, Lang.getString(info.winState == WIN_STATE_WINNER ? R.string.format_pingBest : R.string.format_ping, Strings.buildCounter(info.pingMs)));
              } else {
                out.value = Lang.getString(R.string.ProxyConnected, Lang.getString(R.string.format_ping, Strings.buildCounter(info.pingMs)));
              }
            } else {
              out.value = Lang.getString(R.string.Connected);
            }
          }
          break;
        }
        case ConnectionState.UPDATING:
          out.value = Lang.getString(R.string.network_Updating);
          break;
        case ConnectionState.WAITING_FOR_NETWORK:
          out.value = Lang.getString(R.string.network_WaitingForNetwork);
          break;
        case ConnectionState.CONNECTING:
        case ConnectionState.CONNECTING_TO_PROXY:
        case ConnectionState.UNKNOWN:
        default:
          out.value = Lang.getString(R.string.network_Connecting);
          break;
      }
    } else if (tdlib.connectionState() == ConnectionState.WAITING_FOR_NETWORK) {
      out.value = Lang.getString(R.string.ProxyChecking);
    } else if (info.pingMs >= 0) {
      out.colorId = R.id.theme_color_textSecure;
      if (info.winState != 0) {
        out.value = Lang.getStringBoldLowercase(R.string.ProxyAvailable, Lang.getString(info.winState == WIN_STATE_WINNER ? R.string.format_pingBest : R.string.format_ping, Strings.buildCounter(info.pingMs)));
      } else {
        out.value = Lang.getString(R.string.ProxyAvailable, Lang.getString(R.string.format_ping, Strings.buildCounter(info.pingMs)));
      }
    } else if (info.pingMs == Settings.PROXY_TIME_EMPTY) {
      out.colorId = R.id.theme_color_textNegative;
      if (Settings.instance().checkProxySetting(Settings.PROXY_FLAG_SHOW_ERRORS))
        out.value = Lang.getString(R.string.ProxyErrorDetailed, info.pingError == null ? "unknown" : info.pingError.code + ": " + info.pingError.message);
      else
        out.value = Lang.getString(info.id == Settings.PROXY_ID_NONE ? R.string.ProxyErrorDirect : R.string.ProxyError);
    } else {
      out.value = Lang.getString(R.string.ProxyChecking);
    }
    if (out.value instanceof String) {
      out.value = Lang.lowercase((String) out.value);
    }
  }

  private void pingProxy (@NonNull Settings.Proxy info, boolean allowNotify) {
    int nextPingId = info.pingCount + 1;
    tdlib.pingProxy(info, pingMs -> runOnUiThreadOptional(() -> {
      if (info.pingCount == nextPingId) {
        checkWinners();
        adapter.updateValuedSettingByPosition(indexOfProxy(info.id));
      }
    }));
  }

  private static class DisplayInfo {
    public CharSequence value;
    public @ThemeColorId int colorId;
    public boolean connected;

    void reset () {
      value = null;
      connected = false;
      colorId = ThemeColorId.NONE;
    }
  }

  private final DisplayInfo displayInfo = new DisplayInfo();
  private boolean canUseForCalls;

  private void setCanUseForCalls (boolean canUseForCalls) {
    if (this.canUseForCalls != canUseForCalls) {
      this.canUseForCalls = canUseForCalls;
      if (canUseForCalls) {
        int size = adapter.getItemCount();
        addProxyCallItems(adapter.getItems());
        adapter.notifyItemRangeInserted(size, adapter.getItemCount() - size);
      } else {
        int count = 5;
        adapter.removeRange(adapter.getItemCount() - count, count);
      }
    }
  }

  private final ListItem proxyCallsItem = new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_useProxyForCalls, 0, R.string.UseProxyForCalls);

  private static ListItem newProxyItem (@NonNull Settings.Proxy proxy) {
    return new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_RADIO, R.id.btn_proxy).setLongId(proxy.id).setData(proxy);
  }

  private void addProxyCallItems (List<ListItem> items) {
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.ProxyOther));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(proxyCallsItem);
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.UseProxyForCallsInfo));
  }

  private static ListItem[] newAutoSwitchItems () {
    return new ListItem[] {
      new ListItem(ListItem.TYPE_SHADOW_TOP),
      new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_proxyAutoSwitch, 0, R.string.ProxyAutoSwitch),
      new ListItem(ListItem.TYPE_SHADOW_BOTTOM),
      new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.ProxyAutoSwitchHint)
    };
  }

  private void checkAutoSwitchItems () {
    boolean canSwitchAutomatically = !proxies.isEmpty();
    if (this.hasProxyAutoSwitchSettings != canSwitchAutomatically) {
      this.hasProxyAutoSwitchSettings = canSwitchAutomatically;
      if (canSwitchAutomatically) {
        int i = adapter.indexOfViewById(R.id.btn_addProxy);
        if (i != -1) {
          adapter.addItems(i + 2, newAutoSwitchItems());
        }
      } else {
        int i = adapter.indexOfViewById(R.id.btn_proxyAutoSwitch);
        if (i != -1) {
          adapter.removeRange(i - 1, 4);
        }
      }
    }
  }

  private ItemTouchHelper touchHelper;

  @Override
  protected boolean needPersistentScrollPosition () {
    return true;
  }

  private boolean hasProxyAutoSwitchSettings;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    this.proxies = Settings.instance().getAvailableProxies();
    this.effectiveProxyId = Settings.instance().getEffectiveProxyId();

    if (effectiveProxyId != Settings.PROXY_ID_NONE) {
      for (Settings.Proxy proxy : proxies) {
        if (proxy.id == effectiveProxyId) {
          canUseForCalls = proxy.canUseForCalls();
          break;
        }
      }
    }

    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_addProxy, 0, R.string.ProxyAdd)); // TODO design: icon
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.ProxyInfo));

    hasProxyAutoSwitchSettings = !proxies.isEmpty();
    if (hasProxyAutoSwitchSettings) {
      Collections.addAll(items, newAutoSwitchItems());
    }

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.ProxyConnections));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_RADIO, R.id.btn_noProxy, 0, R.string.ProxyNone));
    pingProxy(noProxy, true);
    for (Settings.Proxy proxy : proxies) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(newProxyItem(proxy));
      pingProxy(proxy, true);
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    if (canUseForCalls) {
      addProxyCallItems(items);
    }

    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        switch (item.getId()) {
          case R.id.btn_proxyAutoSwitch: {
            view.getToggler().setRadioEnabled(Settings.instance().checkProxySetting(Settings.PROXY_FLAG_SWITCH_AUTOMATICALLY), isUpdate);
            break;
          }
          case R.id.btn_noProxy:
          case R.id.btn_proxy: {
            Settings.Proxy proxy = (Settings.Proxy) item.getData();
            if (proxy != null) {
              view.setName(proxy.getName());
            } else {
              view.setName(R.string.ProxyNone);
              proxy = noProxy;
            }
            getState(proxy, displayInfo);
            view.setDataColorId(displayInfo.colorId);
            view.setData(displayInfo.value);
            RadioView radioView = view.findRadioView();
            if (!isUpdate || proxy.id == effectiveProxyId) {
              radioView.setActive(proxy.id == effectiveProxyId && displayInfo.connected, isUpdate);
            }
            radioView.setChecked(proxy.id == effectiveProxyId, isUpdate);
            break;
          }
          case R.id.btn_useProxyForCalls: {
            view.getToggler().setRadioEnabled(Settings.instance().checkProxySetting(Settings.PROXY_FLAG_USE_FOR_CALLS), isUpdate);
            break;
          }
        }
      }
    };
    adapter.setOnLongClickListener(this);
    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);

    touchHelper = RemoveHelper.attach(recyclerView, new RemoveHelper.ExtendedCallback() {
      @Override
      public boolean isLongPressDragEnabled () {
        return false;
      }

      @Override
      public int makeDragFlags (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        ListItem item = (ListItem) viewHolder.itemView.getTag();
        if (item != null && item.getId() == R.id.btn_proxy && proxies.size() > 1) {
          return ItemTouchHelper.UP | ItemTouchHelper.DOWN;
        } else {
          return 0;
        }
      }

      @Override
      public boolean onMove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
        int fromPosition = viewHolder.getAdapterPosition();
        int toPosition = target.getAdapterPosition();

        int fromIndex = cellIndexToProxyIndex(fromPosition);
        int toIndex = cellIndexToProxyIndex(toPosition);

        if (fromIndex >= 0 && fromIndex < proxies.size() && toIndex >= 0 && toIndex < proxies.size() && fromIndex != toIndex) {
          moveProxy(fromIndex, toIndex);
          return true;
        }

        return false;
      }

      @Override
      public void onCompleteMovement (int fromPosition, int toPosition) {
        saveProxiesOrder();
      }

      @Override
      public boolean canRemove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, int position) {
        ListItem item = (ListItem) viewHolder.itemView.getTag();
        return item != null && item.getId() == R.id.btn_proxy;
      }

      @Override
      public void onRemove (RecyclerView.ViewHolder viewHolder) {
        ListItem item = (ListItem) viewHolder.itemView.getTag();
        if (item != null && item.getId() == R.id.btn_proxy) {
          removeProxy((Settings.Proxy) item.getData());
        }
      }
    });

    Settings.instance().addProxyListener(this);
    tdlib.listeners().subscribeToConnectivityUpdates(this);
    tdlib.context().global().addProxyListener(this);
  }

  private void removeProxy (Settings.Proxy proxy) {
    int proxyId = proxy.id;
    if (proxyId == Settings.PROXY_ID_NONE) {
      return;
    }
    showOptions(Lang.getString(R.string.ProxyRemoveInfo), new int[] {R.id.btn_removeProxy, R.id.btn_cancel}, new String[] {Lang.getString(R.string.ProxyRemove), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
      if (id == R.id.btn_removeProxy) {
        removeProxyImpl(proxyId);
      }
      return true;
    });
  }

  private void removeProxyImpl (int proxyId) {
    if (proxyId == Settings.PROXY_ID_NONE) {
      return;
    }
    if (proxyId == effectiveProxyId) {
      Settings.instance().disableProxy();
    }
    if (Settings.instance().removeProxy(proxyId)) {
      int i = indexOfProxy(proxyId);
      if (i != -1) {
        adapter.removeRange(i - 1, 2);
        i = cellIndexToProxyIndex(i);
        if (i != -1) {
          proxies.remove(i);
        }
        checkAutoSwitchItems();
      }
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    Settings.instance().removeProxyListener(this);
    tdlib.listeners().unsubscribeFromConnectivityUpdates(this);
    tdlib.context().global().removeProxyListener(this);
  }

  @Override
  public void onClick (View v) {
    ListItem item = (ListItem) v.getTag();
    switch (v.getId()) {
      case R.id.btn_noProxy: {
        Settings.instance().disableProxy();
        break;
      }
      case R.id.btn_proxyAutoSwitch: {
        boolean res = adapter.toggleView(v);
        if (res) {
          Settings.instance().setProxySetting(
            Settings.PROXY_FLAG_SWITCH_AUTOMATICALLY |
            Settings.PROXY_FLAG_SWITCH_ALLOW_DIRECT,
            true
          );
          if (tdlib.isConnectingOrUpdating()) {
            tdlib.resolveConnectionIssues();
          }
        } else {
          Settings.instance().setProxySetting(Settings.PROXY_FLAG_SWITCH_AUTOMATICALLY, false);
        }
        break;
      }
      case R.id.btn_addProxy: {
        tdlib.ui().addNewProxy(this, false);
        break;
      }
      case R.id.btn_proxy: {
        Settings.Proxy proxy = (Settings.Proxy) item.getData();
        if (proxy.id == effectiveProxyId) {
          showProxyOptions(proxy);
        } else if (proxy.proxy != null) {
          Settings.instance().addOrUpdateProxy(proxy.proxy, null, true);
        }
        break;
      }
      case R.id.btn_useProxyForCalls: {
        Settings.instance().setProxySetting(Settings.PROXY_FLAG_USE_FOR_CALLS, adapter.toggleView(v));
        break;
      }
    }
  }

  @Override
  public boolean onLongClick (View v) {
    switch (v.getId()) {
      case R.id.btn_proxy: {
        ListItem item = (ListItem) v.getTag();
        showProxyOptions((Settings.Proxy) item.getData());
        getRecyclerView().requestDisallowInterceptTouchEvent(true);
        v.setOnTouchListener(new View.OnTouchListener() {
          @Override
          public boolean onTouch (View v, MotionEvent event) {
            switch (event.getAction()) {
              case MotionEvent.ACTION_MOVE:
                if (event.getY() <= 0 || event.getY() >= v.getMeasuredHeight()) {
                  v.setOnTouchListener(null);
                  v.dispatchTouchEvent(MotionEvent.obtain(event.getDownTime(), event.getEventTime(), MotionEvent.ACTION_CANCEL, event.getX(), event.getY(), event.getMetaState()));
                  getRecyclerView().requestDisallowInterceptTouchEvent(false);
                  context().hideContextualPopups(true);
                  touchHelper.startDrag(getRecyclerView().getChildViewHolder(v));
                }
                break;
              case MotionEvent.ACTION_CANCEL:
              case MotionEvent.ACTION_UP:
                v.setOnTouchListener(null);
                break;
            }
            return false;
          }
        });
        return true;
      }
    }
    return false;
  }

  private void showProxyOptions (Settings.Proxy proxy) {
    IntList ids = new IntList(3);
    StringList strings = new StringList(3);
    IntList colors = new IntList(3);
    IntList icons = new IntList(3);

    ids.append(R.id.btn_editProxy);
    strings.append(R.string.ProxyEdit);
    icons.append(R.drawable.baseline_edit_24);
    colors.append(OPTION_COLOR_NORMAL);

    if (proxy.proxy.type.getConstructor() != TdApi.ProxyTypeHttp.CONSTRUCTOR) {
      ids.append(R.id.btn_share);
      strings.append(R.string.Share);
      icons.append(R.drawable.baseline_forward_24);
      colors.append(OPTION_COLOR_NORMAL);

      ids.append(R.id.btn_copyLink);
      strings.append(R.string.CopyLink);
      icons.append(R.drawable.baseline_link_24);
      colors.append(OPTION_COLOR_NORMAL);
    }

    ids.append(R.id.btn_removeProxy);
    strings.append(R.string.ProxyRemove);
    icons.append(R.drawable.baseline_delete_24);
    colors.append(OPTION_COLOR_RED);

    showOptions(proxy.getName().toString(), ids.get(), strings.get(), colors.get(), icons.get(), (itemView, id) -> {
      switch (id) {
        case R.id.btn_share: {
          tdlib.getProxyLink(proxy, url -> {
            if (!StringUtils.isEmpty(url)) {
              tdlib.ui().shareProxyUrl(new TdlibContext(context, context.currentTdlib()), url);
            }
          });
          break;
        }
        case R.id.btn_copyLink: {
          tdlib.getProxyLink(proxy, url -> {
            if (!StringUtils.isEmpty(url)) {
              UI.copyText(url, R.string.CopiedLink);
            }
          });
          break;
        }
        case R.id.btn_editProxy: {
          EditProxyController c = new EditProxyController(context, tdlib);
          c.setArguments(new EditProxyController.Args(proxy));
          navigateTo(c);
          break;
        }
        case R.id.btn_removeProxy: {
          removeProxy(proxy);
          break;
        }
      }
      return true;
    });
  }

  private void pingProxies () {
    if (isDestroyed())
      return;
    pingProxy(noProxy, true);
    for (Settings.Proxy proxy : proxies) {
      pingProxy(proxy, true);
    }
  }

  private boolean proxyLocked;
  private CancellableRunnable unlocker;

  private void setProxyLocked (boolean isLocked) {
    if (this.proxyLocked != isLocked) {
      this.proxyLocked = isLocked;
      if (isLocked) {
        tdlib.ui().postDelayed(unlocker = new CancellableRunnable() {
          @Override
          public void act () {
            setProxyLocked(false);
          }
        }, 800);
      } else {
        unlocker.cancel();
      }
      adapter.updateValuedSettingByPosition(indexOfProxy(effectiveProxyId));
    }
  }

  @Override
  public void onConnectionStateChanged (int newState, int oldState) {
    tdlib.ui().post(() -> {
      if (newState == ConnectionState.CONNECTED && proxyLocked) {
        setProxyLocked(false);
      } else if (oldState == ConnectionState.WAITING_FOR_NETWORK || newState == ConnectionState.WAITING_FOR_NETWORK) {
        pingProxies();
      } else {
        adapter.updateValuedSettingByPosition(indexOfProxy(effectiveProxyId));
      }
    });
  }

  @Override
  public void onConnectionTypeChanged (TdApi.NetworkType type) {
    tdlib.ui().post(this::pingProxies);
  }

  @Override
  public void onProxyPingChanged (@NonNull Settings.Proxy proxy, long pingMs) {
    if (adapter != null) {
      int index = indexOfProxy(proxy.id);
      if (index != -1) {
        adapter.updateValuedSettingByPosition(index);
      }
    }
  }

  private int cellIndexToProxyIndex (int cellIndex) {
    int headerItemCount = 7;
    if (hasProxyAutoSwitchSettings) {
      headerItemCount += 4;
    }
    if (cellIndex < headerItemCount)
      return -1;
    cellIndex -= headerItemCount;
    if (cellIndex > 0)
      cellIndex /= 2;
    if (cellIndex >= proxies.size() || cellIndex < 0)
      return -1;
    return cellIndex;
  }

  private int indexOfProxyCellByProxyIndex (int proxyIndex, int proxyId) {
    int headerItemCount = 7;
    if (hasProxyAutoSwitchSettings) {
      headerItemCount += 4;
    }
    int index = headerItemCount + proxyIndex * 2;
    if (proxyId != -1 && indexOfProxy(proxyId) != index)
      throw new IllegalStateException("index: " + index + ", proxyIndex: " + indexOfProxy(proxyId));
    return index;
  }

  private int indexOfProxy (int proxyId) {
    if (proxyId == Settings.PROXY_ID_NONE) {
      return 5 + (hasProxyAutoSwitchSettings ? 4 : 0); // adapter.indexOfViewById(R.id.btn_noProxy);
    } else {
      return adapter.indexOfViewByLongId(proxyId);
    }
  }

  @Override
  public void onProxyAvailabilityChanged (boolean isAvailable) { }

  @Override
  public void onProxyConfigurationChanged (int proxyId, @Nullable TdApi.InternalLinkTypeProxy proxy, String description, boolean isCurrent, boolean isNewAdd) {
    int oldIndex = indexOfProxy(effectiveProxyId);
    // resetProxyConnection(oldIndex);
    if (!isCurrent) {
      adapter.updateValuedSettingByPosition(oldIndex);
      return;
    }
    if (isNewAdd && this.effectiveProxyId == proxyId) {
      // Nothing to update
      return;
    }
    int newIndex = proxyId != effectiveProxyId ? indexOfProxy(proxyId) : oldIndex;
    this.effectiveProxyId = proxyId;

    Settings.Proxy localProxy = findProxyById(proxyId);
    if (localProxy != null) {
      localProxy.proxy = proxy;
      localProxy.description = description;
    }

    adapter.updateValuedSettingByPosition(oldIndex);
    if (oldIndex != newIndex) {
      resetProxyPing(newIndex);
      setProxyLocked(true);
      adapter.updateValuedSettingByPosition(newIndex);
    }
    setCanUseForCalls(proxy != null && Settings.Proxy.canUseForCalls(proxy.type));
  }

  private Settings.Proxy findProxyById (int proxyId) {
    for (Settings.Proxy proxy : proxies) {
      if (proxy.id == proxyId) {
        return proxy;
      }
    }
    return null;
  }

  private void resetProxyPing (int cellIndex) {
    Settings.Proxy info = (Settings.Proxy) adapter.getItems().get(cellIndex).getData();
    if (info == null) {
      info = noProxy;
    }
    if (info.pingMs < 0 && info.pingMs != Settings.PROXY_TIME_LOADING) {
      pingProxy(info, true);
    }
  }

  @Override
  public void onProxyAdded (Settings.Proxy proxy, boolean isCurrent) {
    int bestIndex = Collections.binarySearch(proxies, proxy);
    if (bestIndex < 0) {
      bestIndex = -bestIndex - 1;

      if (isCurrent && this.effectiveProxyId != proxy.id) {
        int oldIndex = indexOfProxy(this.effectiveProxyId);
        this.effectiveProxyId = proxy.id;
        adapter.notifyItemChanged(oldIndex);
      }

      proxies.add(bestIndex, proxy);
      int i = adapter.indexOfViewById(R.id.btn_noProxy);
      if (i != -1) {
        i += 1 + bestIndex * 2;
        adapter.getItems().add(i, newProxyItem(proxy));
        adapter.getItems().add(i, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        adapter.notifyItemRangeInserted(i, 2);
      }

      checkAutoSwitchItems();
    }
  }
}
