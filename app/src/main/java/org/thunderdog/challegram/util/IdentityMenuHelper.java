package org.thunderdog.challegram.util;

import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.component.chat.IdentityItemView;
import org.thunderdog.challegram.component.chat.IdentityView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.Identity;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.navigation.MenuIdentityWrap;
import org.thunderdog.challegram.navigation.MenuMoreWrap;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.widget.PopupLayout;

import java.util.List;

import me.vkryl.core.lambda.Destroyable;
import me.vkryl.td.Td;

public class IdentityMenuHelper {

  public interface Provider {
    List<MenuItem> onCreateIdentityMenu (View view);
  }

  public interface OnItemClickListener {
    void onIdentityMenuItemSelected (View view);
  }

  public interface OnShowMoreListener {
    void onShowMoreSelected ();
  }

  public static class MenuItem implements Destroyable {
    public final int id;
    private final Identity identity;

    private Tdlib tdlib;

    public MenuItem (int id, Identity identity) {
      this.id = id;
      this.identity = identity;
    }
    @Override
    public void performDestroy () {
      if (this.tdlib != null) {
        this.tdlib = null;
      }
    }
  }

  private PopupLayout identityMenu;
  private MenuIdentityWrap identityWrap;

  private final Tdlib tdlib;
  private final IdentityMenuHelper.Provider provider;
  private final IdentityMenuHelper.OnItemClickListener onItemClickListener;
  private final IdentityMenuHelper.OnShowMoreListener onShowMoreListener;
  @Nullable
  private final ThemeListenerList themeListeners;
  @Nullable
  private final ThemeDelegate forcedTheme;

  public IdentityMenuHelper (
    Tdlib tdlib,
    IdentityMenuHelper.Provider provider,
    IdentityMenuHelper.OnItemClickListener onItemClickListener,
    IdentityMenuHelper.OnShowMoreListener onShowMoreListener,
    @Nullable ThemeListenerList themeListeners,
    @Nullable ThemeDelegate forcedTheme)
  {
    this.tdlib = tdlib;
    this.provider = provider;
    this.onItemClickListener = onItemClickListener;
    this.onShowMoreListener = onShowMoreListener;
    this.themeListeners = themeListeners;
    this.forcedTheme = forcedTheme;
  }

  public boolean openMenu (View view) {
    List<IdentityMenuHelper.MenuItem> items = provider.onCreateIdentityMenu(view);
    if (items != null && !items.isEmpty()) {
      openMenu(view, items);
      return true;
    }
    return false;
  }

  private void openMenu (View view, List<IdentityMenuHelper.MenuItem> items) {
    if (identityMenu != null)
      identityMenu.hideWindow(false);

    identityWrap = new MenuIdentityWrap(view.getContext());
    identityWrap.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
      int viewHeight = v.getMeasuredHeight();
      int[] out = Views.getLocationInWindow(view);
      int targetY = out[1];

      v.setTranslationX(0);
      v.setTranslationY(targetY - viewHeight);
    });
    identityWrap.init(themeListeners, forcedTheme);
    if (items.size() > 3) {
      identityWrap.addMoreButton(tdlib, this::showMore);
    }
    identityWrap.setTarget(view);
    boolean hasLockedItem = false;
    for (IdentityMenuHelper.MenuItem item : items) {
      if (items.indexOf(item) > 3) {
        break;
      }
      identityWrap.addItem(item.tdlib, item.id, item.identity, this::onMenuItemSelected);
      if (item.identity.isLocked()) {
        hasLockedItem = true;
      }
    }
    if (!Lang.rtl()) {
      identityWrap.setAnchorMode(MenuMoreWrap.ANCHOR_MODE_RIGHT);
    }

    identityWrap.setShouldPivotBottom(true);
    identityWrap.setRightNumber(0);
    if (hasLockedItem) {
      identityWrap.setMinimumWidth(Screen.dp(245f));
    } else {
      identityWrap.setMinimumWidth(Screen.dp(211f));
    }

    identityMenu = new PopupLayout(view.getContext());
    identityMenu.init(false);
    identityMenu.setNeedRootInsets();
    identityMenu.setOverlayStatusBar(true);
    identityMenu.setDismissListener(popup -> {
      for (IdentityMenuHelper.MenuItem item : items) {
        item.performDestroy();
      }
      if (this.identityMenu == popup) {
        this.identityMenu = null;
      }
    });
    identityMenu.showIdentityMenuWrap(identityWrap);
  }

  public void processTouchEvent (MotionEvent event) {
    if (identityMenu != null) {
      identityMenu.onTouchEvent(event);
      boolean shouldClose = identityWrap.processTouchEvent(event);
      if (shouldClose) {
        hideMenu();
      }
    }
  }

  public void hideMenu () {
    if (this.identityMenu != null) {
      this.identityMenu.hideWindow(true);
      this.identityMenu = null;
    }
  }

  private void onMenuItemSelected (View view) {
    if (identityMenu != null && !identityMenu.isWindowHidden()) {
      Identity identity = ((IdentityItemView) view).getIdentity();
      if (!identity.isLocked() && onItemClickListener != null) {
        onItemClickListener.onIdentityMenuItemSelected(view);
      }
    }
  }

  private void showMore () {
    if (onShowMoreListener != null) {
      onShowMoreListener.onShowMoreSelected();
    }
  }
}
