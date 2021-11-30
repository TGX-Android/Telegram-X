/**
 * File created on 09/05/15 at 11:18
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.navigation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.base.TogglerView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.GlobalAccountListener;
import org.thunderdog.challegram.telegram.GlobalCountersListener;
import org.thunderdog.challegram.telegram.SessionListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibBadgeCounter;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibOptionListener;
import org.thunderdog.challegram.telegram.TdlibSettingsManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.ChatsController;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.PeopleController;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.ui.SettingsController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.unsorted.Test;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.widget.BaseView;
import org.thunderdog.challegram.widget.ForceTouchView;
import org.thunderdog.challegram.widget.ShadowView;
import org.thunderdog.challegram.widget.TimerView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.td.ChatId;

public class DrawerController extends ViewController<Void> implements View.OnClickListener, Settings.ProxyChangeListener, GlobalAccountListener, GlobalCountersListener, BaseView.CustomControllerProvider, BaseView.ActionListProvider, View.OnLongClickListener, TdlibSettingsManager.NotificationProblemListener, TdlibOptionListener, SessionListener {
  private int currentWidth, shadowWidth;

  private boolean isVisible;
  private boolean isAnimating;

  private FrameLayoutFix contentView;
  private DrawerHeaderView headerView;
  private SettingsAdapter adapter;

  public DrawerController (Context context) {
    super(context, null);
  }

  @Override
  public boolean needsTempUpdates () {
    return factor > 0f;
  }

  @Override
  public int getId () {
    return R.id.controller_drawer;
  }

  private RecyclerView recyclerView;
  private ItemTouchHelper touchHelper;

  private final ListItem proxyItem = new ListItem(ListItem.TYPE_DRAWER_ITEM_WITH_RADIO_SEPARATED, R.id.btn_proxy, R.drawable.baseline_security_24, R.string.Proxy);

  private boolean proxyAvailable;
  private int settingsErrorIcon;
  private Tdlib.SessionsInfo sessionsInfo; // TODO move to BaseActivity

  private int getSettingsErrorIcon () {
    int errorIcon = context.getSettingsErrorIcon();
    boolean haveIncompleteLoginAttempts = sessionsInfo != null && sessionsInfo.incompleteLoginAttempts.length > 0;
    if (errorIcon != 0 && haveIncompleteLoginAttempts) {
      return Tdlib.CHAT_FAILED;
    } else if (errorIcon != 0) {
      return errorIcon;
    } else if (haveIncompleteLoginAttempts) {
      return Tdlib.CHAT_FAILED; // TODO find a good matching icon
    }
    return 0;
  }

  private Tdlib lastTdlib;

  public void onCurrentTdlibChanged (Tdlib tdlib) {
    if (lastTdlib != null) {
      lastTdlib.settings().removeNotificationProblemAvailabilityChangeListener(this);
      lastTdlib.listeners().removeOptionListener(this);
      lastTdlib.listeners().unsubscribeFromSessionUpdates(this);
    }
    this.lastTdlib = tdlib;
    this.sessionsInfo = null;
    tdlib.settings().addNotificationProblemAvailabilityChangeListener(this);
    tdlib.listeners().addOptionsListener(this);
    tdlib.listeners().subscribeToSessionUpdates(this);
    checkSettingsError();
    fetchSessions();
  }

  private void fetchSessions () {
    Tdlib tdlib = lastTdlib;
    if (tdlib != null) {
      tdlib.getSessions(true, sessionsInfo -> {
        if (sessionsInfo != null && this.sessionsInfo != sessionsInfo) {
          runOnUiThreadOptional(() -> {
            if (this.lastTdlib == tdlib) {
              this.sessionsInfo = sessionsInfo;
              checkSettingsError();
            }
          });
        }
      });
    }
  }

  @Override
  public void onSessionListChanged (Tdlib tdlib, boolean isWeakGuess) {
    runOnUiThreadOptional(() -> {
      if (lastTdlib == tdlib) {
        fetchSessions();
      }
    });
  }

  @Override
  public void onNotificationProblemsAvailabilityChanged (Tdlib tdlib, boolean available) {
    tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        checkSettingsError();
      }
    });
  }

  @Override
  public void onSuggestedActionsChanged (TdApi.SuggestedAction[] addedActions, TdApi.SuggestedAction[] removedActions) {
    context.currentTdlib().ui().post(() -> {
      if (!isDestroyed()) {
        checkSettingsError();
      }
    });
  }

  public void checkSettingsError () {
    int settingsErrorIcon = getSettingsErrorIcon();
    if (this.settingsErrorIcon != settingsErrorIcon) {
      this.settingsErrorIcon = settingsErrorIcon;
      if (adapter != null) {
        int i = adapter.indexOfViewById(R.id.btn_settings);
        if (i != -1) {
          adapter.notifyItemChanged(i);
        }
      }
    }
  }

  @Override
  public void onActivityResume () {
    super.onActivityResume();
    checkSettingsError();
  }

  @Override
  protected View onCreateView (Context context) {
    shadowWidth = Screen.dp(7f);

    currentWidth = Math.min(Screen.smallestSide() - Screen.dp(56f), Screen.dp(300f)) + shadowWidth;

    contentView = new DrawerContentView(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        updateTranslation();
      }
    };
    contentView.setVisibility(View.GONE);
    contentView.setTranslationX(-currentWidth);
    contentView.setLayoutParams(FrameLayoutFix.newParams(currentWidth, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT));

    ShadowView shadowView = new ShadowView(context);
    shadowView.setSimpleRightShadow(false);
    shadowView.setLayoutParams(FrameLayoutFix.newParams(shadowWidth, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT));
    addThemeInvalidateListener(shadowView);
    contentView.addView(shadowView);

    headerView = new DrawerHeaderView(context, this);
    addThemeInvalidateListener(headerView);
    headerView.setLayoutParams(FrameLayoutFix.newParams(currentWidth - shadowWidth, Screen.dp(148f) + HeaderView.getTopOffset(), Gravity.TOP));
    contentView.addView(headerView);

    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(currentWidth - shadowWidth, ViewGroup.LayoutParams.MATCH_PARENT);
    params.setMargins(0, Screen.dp(148f) + HeaderView.getTopOffset(), 0, 0);

    ArrayList<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_DRAWER_EMPTY));

    if ((showingAccounts = Settings.instance().isAccountListOpened())) {
      fillAccountItems(items);
      headerView.getExpanderView().setExpanded(true, false);
    }

    items.add(new ListItem(ListItem.TYPE_DRAWER_ITEM, R.id.btn_contacts, R.drawable.baseline_perm_contact_calendar_24, R.string.Contacts));
    items.add(new ListItem(ListItem.TYPE_DRAWER_ITEM, R.id.btn_savedMessages, R.drawable.baseline_bookmark_24, R.string.SavedMessages));
    this.settingsErrorIcon = getSettingsErrorIcon();
    items.add(new ListItem(ListItem.TYPE_DRAWER_ITEM, R.id.btn_settings, R.drawable.baseline_settings_24, R.string.Settings));
    items.add(new ListItem(ListItem.TYPE_DRAWER_ITEM, R.id.btn_invite, R.drawable.baseline_person_add_24, R.string.InviteFriends));

    this.proxyAvailable = Settings.instance().getAvailableProxyCount() > 0;
    if (proxyAvailable) {
      proxyItem.setSelected(Settings.instance().getEffectiveProxyId() != Settings.PROXY_ID_NONE);
      items.add(proxyItem);
    }
    items.add(new ListItem(ListItem.TYPE_DRAWER_ITEM, R.id.btn_help, R.drawable.baseline_help_24, R.string.Help));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_DRAWER_ITEM_WITH_RADIO, R.id.btn_night, R.drawable.baseline_brightness_2_24, R.string.NightMode, R.id.btn_night, Theme.isDark()));
    if (Test.NEED_CLICK) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_DRAWER_ITEM, R.id.btn_reportBug, R.drawable.baseline_bug_report_24, Test.CLICK_NAME, false));
    }
    if (Settings.instance().inDeveloperMode()) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_DRAWER_ITEM, R.id.btn_tdlib_clearLogs, R.drawable.baseline_bug_report_24, "Clear TDLib logs", false));
      items.add(new ListItem(ListItem.TYPE_DRAWER_ITEM, R.id.btn_tdlib_shareLogs, R.drawable.baseline_bug_report_24, "Send TDLib log", false));
      /*if (BuildConfig.DEBUG) {
        items.add(new SettingItem(SettingItem.TYPE_DRAWER_ITEM, R.id.btn_submitCrash, R.drawable.baseline_bug_report_24, "Crash app", false));
      }*/
    }

    adapter = new SettingsAdapter(this) {
      @Override
      protected void setDrawerItem (ListItem item, DrawerItemView view, TimerView timerView, boolean isUpdate) {
        isUpdate = isUpdate && factor > 0f;
        switch (item.getId()) {
          case R.id.account: {
            TdlibAccount account = (TdlibAccount) item.getData();
            TdlibBadgeCounter badge = account.getUnreadBadge();
            view.setChecked(account.id == account.context().preferredAccountId(), isUpdate);
            view.setUnreadCount(badge.getCount(), badge.isMuted(), isUpdate);
            view.setAvatar(account.getAvatarPlaceholderMetadata(), account.getAvatarFile(false));
            view.setText(Lang.getDebugString(account.getName(), account.isDebug()));
            view.setCustomControllerProvider(DrawerController.this);
            view.setPreviewActionListProvider(DrawerController.this);
            break;
          }
          case R.id.btn_settings: {
            view.setError(settingsErrorIcon != 0, settingsErrorIcon != Tdlib.CHAT_FAILED ? settingsErrorIcon : 0, isUpdate);
            break;
          }
          default: {
            view.setError(false, 0, isUpdate);
            break;
          }
        }
      }
    };
    adapter.setOnLongClickListener(this);
    adapter.setItems(items, true);

    recyclerView = new RecyclerView(context);
    recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
      @Override
      public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        RecyclerView.ViewHolder holder = parent.getChildViewHolder(view);
        if (holder != null && holder.getItemViewType() == ListItem.TYPE_SEPARATOR_FULL) {
          outRect.top = outRect.bottom = Screen.dp(8f);
        } else {
          outRect.top = outRect.bottom = 0;
        }
      }
    });
    recyclerView.setItemAnimator(null);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
    ViewSupport.setThemedBackground(recyclerView, R.id.theme_color_filling, this);
    addThemeFillingColorListener(recyclerView);
    recyclerView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
    recyclerView.setAdapter(adapter);
    recyclerView.setLayoutParams(params);
    contentView.addView(recyclerView);

    touchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
      @Override
      public int getMovementFlags (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        if (!showingAccounts) {
          return 0;
        }
        int position = viewHolder.getAdapterPosition();
        int accountsNum = TdlibManager.instance().getActiveAccounts().size();
        if (accountsNum <= 1) {
          return 0;
        }
        if (position != -1 && position >= 1 && position < 1 + accountsNum) {
          int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
          return makeMovementFlags(dragFlags, 0);
        }
        return 0;
      }

      @Override
      public boolean isLongPressDragEnabled () {
        return false;
      }

      private int dragFrom = -1;
      private int dragTo = -1;

      @Override
      public void onMoved (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, int fromPos, RecyclerView.ViewHolder target, int toPos, int x, int y) {
        super.onMoved(recyclerView, viewHolder, fromPos, target, toPos, x, y);
        viewHolder.itemView.invalidate();
        target.itemView.invalidate();
      }

      @Override
      public boolean onMove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
        if (!showingAccounts) {
          return false;
        }

        int fromPosition = viewHolder.getAdapterPosition();
        int toPosition = target.getAdapterPosition();

        int accountsNum = TdlibManager.instance().getActiveAccounts().size();

        if (fromPosition >= 1 && fromPosition < 1 + accountsNum && toPosition >= 1 && toPosition < 1 + accountsNum) {
          TdlibManager.instance().moveAccount(fromPosition - 1, toPosition - 1);
          if (dragFrom == -1) {
            dragFrom = fromPosition;
          }
          dragTo = toPosition;
          return true;
        }

        return false;
      }

      @Override
      public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);
        if (dragFrom != -1 && dragTo != -1 && dragFrom != dragTo) {
          TdlibManager.instance().saveAccountOrders();
        }
        ((DrawerItemView) viewHolder.itemView).setIsDragging(false);
        animator.setValue(false, true);
        dragFrom = dragTo = -1;
      }

      @Override
      public void onSwiped (RecyclerView.ViewHolder viewHolder, int direction) { }

      @Override
      public void onSelectedChanged (RecyclerView.ViewHolder viewHolder, int actionState) {
        super.onSelectedChanged(viewHolder, actionState);
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
          prepareShadows();
          ((DrawerItemView) viewHolder.itemView).setIsDragging(true);
          animator.setValue(true, true);
        }
      }

      private Paint topShadowPaint, bottomShadowPaint;
      private void prepareShadows () {
        if (topShadowPaint != null) {
          return;
        }

        this.topShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        int[] topShadowColors = ShadowView.topShadowColors();
        topShadowPaint.setShader(new LinearGradient(0, 0, 0, ShadowView.simpleTopShadowHeight(), topShadowColors, null, Shader.TileMode.CLAMP));

        this.bottomShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        int[] bottomShadowColors = ShadowView.bottomShadowColors();
        bottomShadowPaint.setShader(new LinearGradient(0, 0, 0, ShadowView.simpleBottomShadowHeight(), bottomShadowColors, null, Shader.TileMode.CLAMP));
      }

      private float dragFactor;

      private final BoolAnimator animator = new BoolAnimator(0, new FactorAnimator.Target() {
        @Override
        public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
          dragFactor = factor;
          recyclerView.invalidate();
        }

        @Override
        public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {

        }
      }, AnimatorUtils.DECELERATE_INTERPOLATOR, 280l);

      @Override
      public void onChildDraw (Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);

        if (viewHolder == null || !(viewHolder.itemView instanceof DrawerItemView)) {
          return;
        }

        DrawerItemView itemView = (DrawerItemView) viewHolder.itemView;
        if (dragFactor == 0f) {
          return;
        }

        int top = itemView.getTop();
        int left = itemView.getLeft();
        int right = itemView.getRight();
        int bottom = itemView.getBottom();

        top += dY;
        bottom += dY;
        left += dX;
        right += dX;

        // c.drawRect(left, top, right, bottom, Paints.fillingPaint(U.alphaColor(dragFactor, 0xffff0000)));

        final float maxAlpha = Theme.getShadowDepth();
        float lineFactor = Theme.getSeparatorReplacement();
        float shadowFactor = 1f - lineFactor;


        if (shadowFactor != 0f) {
          final int alpha = (int) (255f * maxAlpha * shadowFactor * dragFactor);

          topShadowPaint.setAlpha(alpha);
          bottomShadowPaint.setAlpha(alpha);

          c.save();
          int y;
          c.translate(left, y = top - ShadowView.simpleTopShadowHeight());
          c.drawRect(0, 0, right - left, ShadowView.simpleTopShadowHeight(), topShadowPaint);
          c.translate(0, bottom - y);
          c.drawRect(0, 0, right - left, ShadowView.simpleBottomShadowHeight(), bottomShadowPaint);
          c.restore();
        }

        if (lineFactor != 0f) {
          final int separatorHeight = Math.max(1, Screen.dp(.5f, 3f));
          final int color = ColorUtils.alphaColor(lineFactor * dragFactor, Theme.separatorColor());
          c.drawRect(left, top, right, top + separatorHeight, Paints.fillingPaint(color));
          c.drawRect(left, bottom - separatorHeight, right, bottom, Paints.fillingPaint(color));
        }
      }
    });
    touchHelper.attachToRecyclerView(recyclerView);

    Settings.instance().addProxyListener(this);
    TdlibManager.instance().global().addAccountListener(this);
    TdlibManager.instance().global().addCountersListener(this);

    return contentView;
  }

  /*private static SettingItem newWalletItem () {
    return new SettingItem(SettingItem.TYPE_DRAWER_ITEM, R.id.btn_wallet, R.drawable.baseline_account_balance_wallet_24, R.string.Wallet);
  }*/

  @Override
  public void destroy () {
    super.destroy();
    Settings.instance().removeProxyListener(this);
    TdlibManager.instance().global().removeAccountListener(this);
    TdlibManager.instance().global().removeCountersListener(this);
  }

  @Override
  public void onUnreadCountersChanged (Tdlib tdlib, @NonNull TdApi.ChatList chatList, int count, boolean isMuted) {
    if (showingAccounts) {
      int i = adapter.indexOfViewByData(tdlib.account());
      if (i != -1) {
        adapter.updateValuedSettingByPosition(i);
      }
    }
  }

  @Override
  public void onTotalUnreadCounterChanged (@NonNull TdApi.ChatList chatList, boolean isReset) {
    if (isReset && showingAccounts) {
      updateAllAccounts();
    }
  }

  private void updateAllAccounts () {
    List<ListItem> items = adapter.getItems();
    int i = 0;
    while (++i < items.size() && items.get(i).getId() == R.id.account) {
      adapter.updateValuedSettingByPosition(i);
    }
  }

  @Override
  public void onThemeChanged (ThemeDelegate oldTheme, ThemeDelegate newTheme) {
    int newItem = adapter.indexOfViewById(R.id.btn_night);
    if (newItem != -1) {
      ListItem item = adapter.getItems().get(newItem);
      adapter.setToggled(item, newTheme.isDark());
    }
  }

  @Override
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    updateTranslation();
    if (headerView != null) {
      headerView.invalidate();
    }
  }

  private void updateTranslation () {
    float factor = this.factor;
    if (factor != 0f) {
      this.factor = 0f;
      setFactor(factor);
    }
  }

  @Override
  public void handleLanguagePackEvent (int event, int arg1) {
    switch (event) {
      case Lang.EVENT_PACK_CHANGED:
      case Lang.EVENT_DIRECTION_CHANGED:
        adapter.notifyAllStringsChanged();
        break;
      case Lang.EVENT_STRING_CHANGED:
        adapter.notifyStringChanged(arg1);
        break;
      case Lang.EVENT_DATE_FORMAT_CHANGED:
        // nothing to update
        break;
    }
  }

  @Override
  public ForceTouchView.ActionListener onCreateActions (View v, ForceTouchView.ForceTouchContext context, IntList ids, IntList icons, StringList strings, ViewController<?> target) {
    TdlibAccount account = (TdlibAccount) ((ListItem) v.getTag()).getData();

    ids.append(R.id.btn_removeAccount);
    icons.append(R.drawable.baseline_delete_forever_24);
    strings.append(R.string.LogOut);
    context.setStateListenerArgument(account);

    context.setExcludeHeader(true);

    context.setTdlib(account.tdlib());
    context.setBoundUserId(account.tdlib().myUserId());

    return new ForceTouchView.ActionListener() {
      @Override
      public void onForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) {

      }

      @Override
      public void onAfterForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) {
        switch (actionId) {
          case R.id.btn_removeAccount: {
            TdlibAccount tdlibAccount = (TdlibAccount) arg;
            TdlibUi.removeAccount(DrawerController.this, tdlibAccount);
            break;
          }
        }
      }
    };
  }

  @Override
  public boolean needsForceTouch (BaseView v, float x, float y) {
    TdlibAccount account = (TdlibAccount) ((ListItem) v.getTag()).getData();
    return account.id != account.context().preferredAccountId();
  }

  @Override
  public boolean onSlideOff (BaseView v, float x, float y, @Nullable ViewController<?> openPreview) {
    if (y < 0 && (openPreview == null || !openPreview.hasInteractedWithContent())) {
      RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(v);
      if (holder != null) {
        v.dropPress(x, y);
        touchHelper.startDrag(holder);
        return true;
      }
    }
    return false;
  }

  @Override
  public ViewController<?> createForceTouchPreview (BaseView v, float x, float y) {
    TdlibAccount account = (TdlibAccount) ((ListItem) v.getTag()).getData();
    return new ChatsController(context, account.tdlib());
  }

  private boolean creatingStorageChat;

  private void openSavedMessages () {
    final Tdlib tdlib = context.currentTdlib();
    final long userId = tdlib.myUserId();
    if (userId == 0) {
      return;
    }
    TdApi.Chat chat = tdlib.chat(ChatId.fromUserId(userId));
    if (chat != null) {
      openChat(tdlib, chat.id);
      return;
    }
    if (!creatingStorageChat) {
      creatingStorageChat = true;
      tdlib.client().send(new TdApi.CreatePrivateChat(userId, true), object -> {
        switch (object.getConstructor()) {
          case TdApi.Chat.CONSTRUCTOR: {
            final long chatId = TD.getChatId(object);
            tdlib.ui().post(() -> {
              creatingStorageChat = false;
              if (factor == 1f) {
                openChat(tdlib, chatId);
              }
            });
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            creatingStorageChat = false;
            UI.showError(object);
            break;
          }
          default: {
            creatingStorageChat = false;
            Log.unexpectedTdlibResponse(object, TdApi.CreatePrivateChat.class, TdApi.Chat.class);
            break;
          }
        }
      });
    }
  }

  // Account list

  @Override
  public void onAccountSwitched (TdlibAccount newAccount, TdApi.User profile, int reason, TdlibAccount oldAccount) {
    if (oldAccount != null) {
      adapter.updateValuedSettingByData(oldAccount);
    }
    adapter.updateValuedSettingByData(newAccount);
    checkSettingsError();
  }

  @Override
  public void onAccountProfileChanged (TdlibAccount account, TdApi.User profile, boolean isCurrent, boolean isLoaded) {
    if (showingAccounts) {
      int i = adapter.indexOfViewByData(account);
      if (i != -1)
        adapter.updateValuedSettingByPosition(i);
    }
  }

  @Override
  public void onAccountProfilePhotoChanged (TdlibAccount account, boolean big, boolean isCurrent) {
    if (!big && showingAccounts) {
      int i = adapter.indexOfViewByData(account);
      if (i != -1)
        adapter.updateValuedSettingByPosition(i);
    }
  }

  @Override
  public void onAuthorizationStateChanged (TdlibAccount account, TdApi.AuthorizationState authorizationState, int status) { }

  @Override
  public void onActiveAccountRemoved (TdlibAccount account, int position) {
    if (showingAccounts) {
      adapter.removeItem(1 + position);
    }
  }

  @Override
  public void onActiveAccountAdded (TdlibAccount account, int position) {
    if (showingAccounts) {
      adapter.addItem(1 + position, new ListItem(ListItem.TYPE_DRAWER_ITEM_WITH_AVATAR, R.id.account).setLongId(account.id).setData(account));
    }
  }

  @Override
  public void onActiveAccountMoved (TdlibAccount account, int fromPosition, int toPosition) {
    if (showingAccounts) {
      adapter.moveItem(1 + fromPosition, 1 + toPosition);
    }
  }

  private boolean showingAccounts;

  private void fillAccountItems (List<ListItem> items) {
    ArrayList<TdlibAccount> accounts = TdlibManager.instance().getActiveAccounts();
    for (TdlibAccount account : accounts) {
      items.add(new ListItem(ListItem.TYPE_DRAWER_ITEM_WITH_AVATAR, R.id.account).setLongId(account.id).setData(account));
    }
    items.add(new ListItem(ListItem.TYPE_DRAWER_ITEM, R.id.btn_addAccount, R.drawable.baseline_add_24, R.string.AddAccount));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
  }

  public void setShowAccounts (boolean showAccounts) {
    if (this.showingAccounts == showAccounts) {
      return;
    }
    this.showingAccounts = showAccounts;
    Settings.instance().setAccountListOpened(showAccounts);
    if (showAccounts) {
      List<ListItem> items = new ArrayList<>();
      fillAccountItems(items);
      adapter.getItems().addAll(1, items);
      adapter.notifyItemRangeInserted(1, items.size());
    } else {
      int count = adapter.indexOfViewById(R.id.btn_contacts) - 1;
      adapter.removeRange(1, count);
    }
  }

  // Other

  private CancellableRunnable supportOpen;

  private boolean needAnimationDelay;

  private long lastPreferTime;

  @Override
  public boolean onLongClick (View v) {
    if (!(v instanceof BaseView)) {
      return false;
    }
    RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(v);
    if (holder == null) {
      return false;
    }
    ListItem item = (ListItem) v.getTag();
    if (item == null) {
      return false;
    }
    if (item.getId() != R.id.account) {
      switch (item.getId()) {
        case R.id.btn_addAccount: {
          if (BuildConfig.DEBUG) {
            if (!UI.inTestMode()) {
              UI.TEST_MODE = UI.TEST_MODE_USER;
              context.currentTdlib().ui().addAccount(context, true, true);
            }
          } else {
            context.currentTdlib().getTesterLevel(level -> {
              if (level >= Tdlib.TESTER_LEVEL_ADMIN) {
                context.currentTdlib().ui().addAccount(context, true, true);
              }
            });
          }
          break;
        }
      }
      return false;
    }
    if (needsForceTouch((BaseView) v, 0, 0)) {
      // TODO swipe-up from gesture
      return false;
    }
    touchHelper.startDrag(holder);
    return false;
  }

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.btn_tdlib_clearLogs: {
        TdlibUi.clearLogs(true, size -> TdlibUi.clearLogs(false, size2 -> UI.showToast("Logs Cleared", Toast.LENGTH_SHORT)));
        break;
      }
      case R.id.btn_tdlib_shareLogs: {
        TdlibUi.sendLogs(context.navigation().getCurrentStackItem(), false, false);
        break;
      }
      case R.id.btn_wallet: {
        /*context.currentTdlib().withTon(ton -> {
          // ton.send(new TonApi.WalletInit(new TonApi.InputKey(, )));
        });*/
        break;
      }
      case R.id.account: {
        TdlibAccount account = (TdlibAccount) ((ListItem) v.getTag()).getData();
        long now = SystemClock.uptimeMillis();
        if (account.context().preferredAccountId() != account.id && (lastPreferTime == 0 || now - lastPreferTime >= 720)) {
          lastPreferTime = now;
          needAnimationDelay = true;
          account.context().changePreferredAccountId(account.id, TdlibManager.SWITCH_REASON_USER_CLICK);
        }
        break;
      }
      case R.id.btn_contacts: {
        openContacts();
        // openEmptyChat();
        break;
      }
      case R.id.btn_reportBug: {
        if (Test.NEED_CLICK) {
          Test.onClick(context);
        }
        break;
      }
      case R.id.btn_savedMessages: {
        openSavedMessages();
        break;
      }
      case R.id.btn_addAccount: {
        context.currentTdlib().ui().addAccount(context, true, false);
        break;
      }
      /*case R.id.btn_logout: {
        logoutOnClose = true;
        close(0f);
        break;
      }*/
      case R.id.btn_settings: {
        openSettings();
        break;
      }
      case R.id.btn_proxy: {
        if (v instanceof TogglerView) {
          boolean value = Settings.instance().toggleProxySetting(Settings.PROXY_FLAG_ENABLED);
          setProxyEnabled(value);
        } else {
          context.currentTdlib().ui().openProxySettings(context.navigation().getCurrentStackItem(), false);
        }
        break;
      }
      case R.id.btn_help: {
        cancelSupportOpen();
        supportOpen = context.currentTdlib().ui().openSupport(context.navigation().getCurrentStackItem());
        break;
      }
      case R.id.btn_invite: {
        context.currentTdlib().cache().getInviteText(text -> {
          if (isVisible() && !isDestroyed()) {
            shareText(text.text);
          }
        });
        break;
      }
      case R.id.btn_night: {
        ThemeManager.instance().toggleNightMode();
        break;
      }
      case R.id.btn_bubble: {
        context().currentTdlib().settings().toggleChatStyle();
        break;
      }
    }
  }

  private String shareTextOnClose;

  private void shareText (String text) {
    if (factor != 0f) {
      shareTextOnClose = text;
      close(0f, null);
      return;
    }

    shareTextOnClose = null;

    Intents.shareText(text);
  }

  private void cancelSupportOpen () {
    if (supportOpen != null) {
      supportOpen.cancel();
      supportOpen = null;
    }
  }

  private void openChat (final Tdlib tdlib, final long chatId) {
    if (chatId == 0)
      return;
    ignoreClose = true;
    tdlib.ui().openChat(context.navigation().getCurrentStackItem(), chatId, new TdlibUi.ChatOpenParameters().after(arg1 -> {
      ignoreClose = false;
      close(0f, null);
    }));
  }

  @Override
  public void onProxyConfigurationChanged (int proxyId, @Nullable String server, int port, @Nullable TdApi.ProxyType type, String description, boolean isCurrent, boolean isNewAdd) {
    if (!isCurrent) {
      return;
    }
    boolean newValue = proxyId != Settings.PROXY_ID_NONE;
    if (proxyItem.isSelected() != newValue) {
      setProxyEnabled(newValue);
    }
  }

  @Override
  public void onProxyAvailabilityChanged (boolean isAvailable) {
    if (this.proxyAvailable != isAvailable) {
      this.proxyAvailable = isAvailable;
      if (isAvailable) {
        int i = adapter.indexOfViewByIdReverse(R.id.btn_help);
        adapter.addItem(i, proxyItem);
      } else {
        int i = adapter.indexOfViewById(R.id.btn_proxy);
        adapter.removeItem(i);
      }
    }
  }

  @Override
  public void onProxyAdded (Settings.Proxy proxy, boolean isCurrent) { }

  private void setProxyEnabled (boolean value) {
    if (proxyItem.isSelected() == value) {
      return;
    }
    if (proxyAvailable) {
      if (factor > 0f) {
        adapter.setToggled(proxyItem, value);
      } else {
        proxyItem.setSelected(value);
        adapter.updateValuedSettingById(proxyItem.getId());
      }
    } else {
      proxyItem.setSelected(value);
    }
  }

  /*private void openProxy () {
    if (proxy != null) {
      EditProxyController c = new EditProxyController(context, context.currentTdlib());
      c.setArguments(proxy);
      UI.navigateTo(c);
      close(0f);
    }
  }*/

  private void openSettings () {
    openController(new SettingsController(context, context.currentTdlib()));
  }

  private void openContacts () {
    context.currentTdlib().contacts().startSyncIfNeeded(context, true, () -> {
      PeopleController c = new PeopleController(context, context.currentTdlib());
      c.setNeedSearch();
      openController(c);
    });
  }

  private boolean ignoreClose;

  private void openController (ViewController<?> c) {
    if (c.needAsynchronousAnimation()) {
      ignoreClose = true;
      c.postOnAnimationReady(() -> {
        ignoreClose = false;
        close(0f, null);
      });
    }
    UI.navigateTo(c);
  }

  public DrawerHeaderView getHeaderView () {
    return headerView;
  }

  public boolean isVisible () {
    return isVisible;
  }

  public float getShowFactor () {
    return factor;
  }

  public boolean isAnimating () {
    return isAnimating;
  }

  public int getWidth () {
    return currentWidth;
  }

  public int getShadowWidth () {
    return shadowWidth;
  }

  private void showView () {
    if (navigationController != null) {
      navigationController.preventLayout();
    }
    if (Views.HARDWARE_LAYER_ENABLED) {
      Views.setLayerType(contentView, View.LAYER_TYPE_HARDWARE);
    }
    contentView.setVisibility(View.VISIBLE);
    context().showOverlayView(0xff000000, OverlayView.OVERLAY_MODE_DRAWER);
    if (navigationController != null) {
      navigationController.cancelLayout();
    }
  }

  private void hideView () {
    if (Views.HARDWARE_LAYER_ENABLED) {
      Views.setLayerType(contentView, View.LAYER_TYPE_NONE);
    }
    contentView.setVisibility(View.GONE);
    context().removeOverlayView();
  }

  public void open () {
    ViewController<?> c = UI.getCurrentStackItem(context());
    if (c != null && c.useDrawer() && !c.isIntercepted()) {
      prepare();
      open(0f);
    }
  }

  public void open (float velocity) {
    if (isAnimating) return;

    isAnimating = true;

    ValueAnimator animator = AnimatorUtils.simpleValueAnimator();
    final float startFactor = getFactor();
    final float diffFactor = 1f - startFactor;
    animator.addUpdateListener(animation -> setFactor(startFactor + diffFactor * AnimatorUtils.getFraction(animation)));
    animator.setDuration(NavigationController.calculateDropDuration(lastTranslation(), velocity, 300, 180));
    animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    animator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        setFactor(1f);
        isAnimating = false;
        setIsVisible(true);
      }
    });

    ViewController<?> c = UI.getCurrentStackItem(context());
    View view = c != null ? c.get() : null;
    if (view != null && view instanceof ContentFrameLayout) {
      currentView = (ContentFrameLayout) view;
    } else {
      currentView = null;
    }

    animator.setStartDelay(10l);
    animator.start();
  }

  public void close (float velocity, Runnable after) {
    if (isAnimating || ignoreClose) return;
    isAnimating = true;

    if (factor == 0f) {
      forceClose();
    } else {
      ValueAnimator animator = AnimatorUtils.simpleValueAnimator();
      final float startFactor = getFactor();
      animator.addUpdateListener(animation -> setFactor(startFactor - startFactor * AnimatorUtils.getFraction(animation)));
      animator.setDuration(NavigationController.calculateDropDuration(currentWidth + lastTranslation(), velocity, 300, 180));
      animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
      animator.addListener(new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd (Animator animation) {
          hideView();
          setFactor(0f);
          isAnimating = false;
          if (after != null) {
            after.run();
          }
          /*if (logoutOnClose) {
            logoutOnClose = false;
            tdlib.ui().logOut();
            LogoutHelper.instance().showLogoutConfirm();
          }*/
        }
      });

      setIsVisible(false);

      /*View view = UI.getContext(getContext()).getNavigation().getCurrentStackItem().getWrap();
      if (view instanceof ContentFrameLayout) {
        currentView = (ContentFrameLayout) view;
      } else {
        currentView = null;
      }*/

      if (needAnimationDelay) {
        needAnimationDelay = false;
        animator.setStartDelay(DrawerHeaderView.SWITCH_CLOSE_DURATION);
      } else {
        animator.setStartDelay(10l);
      }
      animator.start();
    }
  }

  private void setIsVisible (boolean isVisible) {
    this.isVisible = isVisible;
  }

  private void forceOpen () {
    setIsVisible(true);
    isAnimating = false;
    setFactor(1f);
  }

  private void forceClose () {
    setIsVisible(false);
    isAnimating = false;
    setFactor(0f);
    hideView();
  }

  public void drop () {
    if (factor < .4f) {
      setIsVisible(true);
      close(0f, null);
    } else {
      setIsVisible(false);
      open(0f);
    }
  }

  public void forceDrop () {
    if (factor < .4f) {
      forceClose();
    } else {
      forceOpen();
    }
  }

  private ContentFrameLayout currentView;

  // private int currentScreenWidth;

  public boolean isNeeded () {
    ViewController<?> c = context.navigation().getCurrentStackItem();
    return c != null && c.useDrawer();
  }

  public boolean prepare () {
    if (!isNeeded() && !isVisible) {
      return false;
    }

    if (isAnimating) {
      return false;
    }

    showView();

    /*View view = UI.getCurrentStackItem().getWrap();
    if (view instanceof ContentFrameLayout) {
      currentView = (ContentFrameLayout) view;
    } else {
      currentView = null;
    }*/

    if (!isVisible) {
      context().getOverlayView().setAlpha(0f);

      if (Lang.rtl()) {
        contentView.setTranslationX(getScreenWidth());
      } else {
        contentView.setTranslationX(-currentWidth);
      }
    }

    return true;
  }

  private float getScreenWidth () {
    return context.navigation().get().getMeasuredWidth();
  }

  public void translate (int lastScrollX) {
    float translation;
    if (Lang.rtl()) {
      translation = isVisible ? currentWidth - lastScrollX : -lastScrollX;
    } else {
      translation = isVisible ? currentWidth + lastScrollX : lastScrollX;
    }
    setFactor(MathUtils.clamp(translation / (float) currentWidth));
  }

  private float factor;

  private final RecyclerView.ItemAnimator recyclerAnimator = new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 160l);

  public void setFactor (float factor) {
    if (this.factor != factor) {
      if (factor == 0f && recyclerView.getItemAnimator() != null) {
        recyclerView.setItemAnimator(null);
      } else if (factor > 0f && recyclerView.getItemAnimator() == null) {
        recyclerView.setItemAnimator(recyclerAnimator);
        checkSettingsError();
      }
      cancelSupportOpen();

      float translation, oldTranslation;

      if (Lang.rtl()) {
        float currentScreenWidth = getScreenWidth();
        translation = currentScreenWidth - (currentWidth - shadowWidth) * factor;
        oldTranslation = currentScreenWidth - (currentWidth - shadowWidth) * this.factor;
      } else {
        translation = -currentWidth * (1f - factor);
        oldTranslation = -currentWidth * (1f - this.factor);
      }

      if (factor != 0f && factor != 1f && Math.abs(oldTranslation - translation) < 1f) {
        return;
      }

      this.factor = factor;

      View overlay = context().getOverlayView();
      contentView.setTranslationX(translation);
      if (overlay != null) {
        overlay.setAlpha(.6f * factor);
      }

      /*float cx = currentWidth * factor;
      overlay.setTranslationX(cx <= 1f ? 0 : cx - 1f);*/
      if (currentView != null) {
        currentView.setClipLeft((int) (currentWidth * factor));
      }

      if (factor == 0f && !StringUtils.isEmpty(shareTextOnClose)) {
        shareText(shareTextOnClose);
      }
    }
  }

  public float getFactor () {
    return factor;
  }

  public float lastTranslation () {
    return currentWidth * (1f - factor);
  }

  /*private static class DrawerItem {
    private final int id;
    private final int icon;
    private final int string;
    private int viewType = DrawerHolder.TYPE_DRAWER_ITEM;

    public DrawerItem (int viewType) {
      this.id = 0;
      this.icon = 0;
      this.string = 0;
      this.viewType = viewType;
    }

    public DrawerItem (@IdRes int id, @DrawableRes int icon, @StringRes int string) {
      this.id = id;
      this.icon = icon;
      this.string = string;
    }

    public DrawerItem setViewType (int viewType) {
      this.viewType = viewType;
      return this;
    }

    public int getId () {
      return id;
    }

    public int getIcon () {
      return icon;
    }

    public int getString () {
      return string;
    }
  }

  private static class DrawerAdapter extends RecyclerView.Adapter<DrawerHolder> {
    private final ViewController context;
    private final ArrayList<SettingItem> settingItems;
    private final View.OnClickListener onClickListener;
    private final ViewController themeProvider;

    public DrawerAdapter (ViewController context, ArrayList<SettingItem> settingItems, View.OnClickListener onClickListener, ViewController themeProvider) {
      this.context = context;
      this.settingItems = settingItems;
      this.onClickListener = onClickListener;
      this.themeProvider = themeProvider;
    }

    public int indexOfSettingById (@IdRes int id) {
      int i = 1;
      for (SettingItem item : settingItems) {
        if (item.getId() == id) {
          return i;
        }
        i++;
      }
      return -1;
    }

    @Override
    public DrawerHolder onCreateViewHolder (ViewGroup parent, int viewType) {
      return DrawerHolder.create(context.context(), viewType, onClickListener, themeProvider);
    }

    @Override
    public void onBindViewHolder (DrawerHolder holder, int position) {
      final int viewType = holder.getItemViewType();
      switch (viewType) {
        case DrawerHolder.TYPE_DRAWER_ITEM:
        case DrawerHolder.TYPE_DRAWER_RADIO_ITEM: {
          SettingItem item = settingItems.get(position - 1);
          DrawerItemView view = viewType == DrawerHolder.TYPE_DRAWER_RADIO_ITEM ? (DrawerItemView) ((FrameLayoutFix) holder.itemView).getChildAt (0) : (DrawerItemView) holder.itemView;
          view.setId(item.getId());
          view.setIcon(Screen.dp(18f), Screen.dp(13.5f), item.getIcon());
          view.setText(item.getString());
          if (viewType == DrawerHolder.TYPE_DRAWER_RADIO_ITEM) {
            RadioButton radioButton =  (RadioButton) ((FrameLayoutFix) holder.itemView).getChildAt(1);
            switch (item.getId()) {
              case R.id.btn_night: {
                radioButton.setRadioEnabled(ThemeManager.isDarkTheme(ThemeManager.globalTheme()), false);
                break;
              }
              case R.id.btn_bubble: {
                radioButton.setRadioEnabled(context.context().currentTdlib().settings().chatStyle() == ThemeManager.CHAT_STYLE_BUBBLES, false);
                break;
              }
            }
          }
          break;
        }
      }
    }

    @Override
    public int getItemCount () {
      return settingItems.size() + 1; // TODO chats
    }

    @Override
    public int getItemViewType (int position) {
      if (position == 0) {
        return DrawerHolder.TYPE_EMPTY;
      } else if (--position < settingItems.size()) {
        return settingItems.get(position).viewType;
      }
      // TODO
      return DrawerHolder.TYPE_EMPTY;
    }
  }

  private static class DrawerHolder extends RecyclerView.ViewHolder {
    public static final int TYPE_EMPTY = 0;
    public static final int TYPE_SEPARATOR = 1;
    public static final int TYPE_CHAT = 3;
    public static final int TYPE_DRAWER_ITEM = 4;
    public static final int TYPE_DRAWER_RADIO_ITEM = 5;

    public DrawerHolder (View itemView) {
      super(itemView);
    }

    public static DrawerHolder create (Context context, int viewType, View.OnClickListener onClickListener, @Nullable ViewController themeProvider) {
      switch (viewType) {
        case TYPE_EMPTY: {
          View view = new View(context);
          view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(6f)));
          return new DrawerHolder(view);
        }
        case TYPE_SEPARATOR: {
          SeparatorView separatorView = new SeparatorView(context);
          separatorView.setSeparatorHeight(Math.max(1, Screen.dp(.5f)));
          if (themeProvider != null) {
            themeProvider.addThemeInvalidateListener(separatorView);
          }
          separatorView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(18f)));
          return new DrawerHolder(separatorView);
        }
        case TYPE_DRAWER_ITEM:
        case TYPE_DRAWER_RADIO_ITEM: {
          DrawerItemView item = new DrawerItemView(context);
          if (themeProvider != null) {
            themeProvider.addThemeInvalidateListener(item);
          }
          item.setItemHeight(Screen.dp(52f));
          item.setIcon(Screen.dp(18f), Screen.dp(13.5f), R.drawable.ic_settings_gray);
          item.setOnClickListener(onClickListener);

          if (viewType == TYPE_DRAWER_RADIO_ITEM) {
            FrameLayoutFix wrap = new FrameLayoutFix(context);
            wrap.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(52f)));
            wrap.addView(item);
            RadioButton radioButton = new RadioButton(context);
            radioButton.init(false);
            radioButton.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT));
            wrap.addView(radioButton);
            if (themeProvider != null) {
              themeProvider.addThemeInvalidateListener(radioButton);
            }

            return new DrawerHolder(wrap);
          }
          return new DrawerHolder(item);
        }
        case TYPE_CHAT: {
          // TODO
          break;
        }
      }
      throw new IllegalArgumentException("viewType == " + viewType);
    }
  }*/
}
