/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.annotation.SuppressLint;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.GlobalAccountListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.PopupLayout;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import tgx.td.Td;

import static org.thunderdog.challegram.miniapps.MiniAppTheme.object;

/** API 21+ native sheet owning one WebView; the TDLib session also survives minimization. */
public final class MiniAppController extends ViewController<Void> implements MiniAppBridgeTransport, GlobalAccountListener {
  private static final long PAGE_READY_FALLBACK_MS = 5000;
  private final MiniAppLaunchRequest request;
  private final MiniAppSession session;
  private final MiniAppPlatform platform = new MiniAppPlatform();
  private Runnable onOpened;
  private MiniAppDocument document;
  private WebView webView;
  private FrameLayout root, browserArea;
  private LinearLayout column, header;
  private MiniAppFullscreenControls fullscreenControls;
  private MiniAppButtons buttons;
  private TextView title, errorView;
  private Bitmap preview;
  private long pageLoadGeneration;
  private boolean pageReady, pageRevealRequested, pageLoadFailed;
  private ValueAnimator sizeAnimator;
  private MiniAppLoadingView progress;
  private Runnable pageReadyFallback;
  private ImageView back, collapse, more;
  private PopupLayout popup;
  private AlertDialog activeDialog;
  private MiniAppMessageSender phoneSender;
  private MiniAppDocument.Request phoneRequest, writeRequest;
  private MiniAppSensors sensors;
  private MiniAppBiometry biometry;
  private MiniAppLocation location;
  private MiniAppMedia media;
  private MiniAppQrScanner qrScanner;
  private MiniAppActions actions;
  private MiniAppRequestedChat requestedChat;
  private MiniAppPreparedMessage preparedMessage;
  private MiniAppOptions options;
  private MiniAppDownloadBulletin downloadBulletin;
  private final List<Runnable> deferredReplies = new ArrayList<>();
  private boolean minimized, expanded, fullscreen, backVisible, settingsVisible, needsCloseConfirmation;
  private boolean restoreWhenHidden;
  private boolean bridgeInstalled;
  private boolean swipeEnabled = true, pageAllowsVerticalSwipe = true, dataSending;
  private int previousOrientation;
  private boolean orientationLocked;
  private boolean orientationRequested;
  private Integer headerColor, backgroundColor, bottomColor;
  private String headerColorKey;
  private long lastInteraction, lastPopupClosed, popupCooldownUntil;
  private int sequentialPopups;
  private float dragStartY;
  private int dragStartHeight;
  private boolean dragging;
  private float webTouchX, webTouchY;
  private int legacyKeyboardInset;
  private android.view.ViewTreeObserver legacyKeyboardObserver;
  private final android.view.ViewTreeObserver.OnGlobalLayoutListener legacyKeyboardListener = () -> {
    if (popup == null || minimized || isDestroyed()) return;
    Rect visible = new Rect();
    popup.getWindowVisibleDisplayFrame(visible);
    int[] location = new int[2];
    popup.getLocationOnScreen(location);
    int covered = Math.max(0, location[1] + popup.getHeight() - visible.bottom);
    int inset = covered > Screen.dp(100) ? covered : 0;
    if (legacyKeyboardInset != inset) { legacyKeyboardInset = inset; resize(); }
  };
  private final org.thunderdog.challegram.widget.RootFrameLayout.InsetsChangeListener sheetInsetsListener =
    (view, effective, effectiveWithoutIme, system, systemWithoutIme, update) -> resize();

  private static final class SheetLayout extends FrameLayout implements org.thunderdog.challegram.widget.RootFrameLayout.MarginModifier {
    SheetLayout (Context context) { super(context); }
    @Override public void onApplyMarginInsets (View child, FrameLayout.LayoutParams params, Rect legacy, Rect effective, Rect withoutIme) {
      // resize() already accounts for raw IME/cutout insets. Do not replace its margins with ignored fullscreen insets.
    }
  }

  public MiniAppController (Context context, Tdlib tdlib, MiniAppLaunchRequest request, Runnable onOpened) {
    super(context, tdlib);
    this.request = request;
    this.onOpened = onOpened;
    session = new MiniAppSession(id -> tdlib.send(new TdApi.CloseWebApp(id), tdlib.typedOkHandler()));
    expanded = !(request.mode instanceof TdApi.WebAppOpenModeCompact);
    fullscreen = request.mode instanceof TdApi.WebAppOpenModeFullScreen;
    context().addActivityListener(this);
    tdlib.context().global().addAccountListener(this);
  }

  @Override
  public int getId () { return R.id.controller_miniApp; }

  public long launchId () { return session.launchId(); }
  public long guardQueryId () { return request.guardQueryId; }
  public boolean isMinimized () { return minimized && !isDestroyed(); }
  public boolean isShowing () { return popup != null && !minimized && !isDestroyed(); }
  public boolean isShowingFullscreen () { return isShowing() && fullscreen; }
  public void onTabsChanged () {
    if (isShowing()) resize();
  }
  public String botName () { return tdlib.cache().userName(request.botUserId); }
  public Bitmap preview () { return preview; }
  public int previewColor () { return header.getBackground() instanceof ColorDrawable ? ((ColorDrawable) header.getBackground()).getColor() : Theme.fillingColor(); }
  public boolean needsCloseConfirmation () { return needsCloseConfirmation; }
  public boolean canRestore (MiniAppLaunchRequest request) { return isMinimized() && this.request.sameLaunch(request); }
  public void restore () {
    if (!isMinimized()) return;
    if (popup == null) showSheet(); else restoreWhenHidden = true;
  }

  public void open (boolean allowWriteAccess) {
    getValue();
    if (!bridgeInstalled) { close(); UI.showToast(R.string.MiniAppWebViewUnavailable, Toast.LENGTH_LONG); return; }
    tdlib.client().send(request.function(MiniAppTheme.parameters(), allowWriteAccess), result -> tdlib.ui().post(() -> {
      long launchId = result instanceof TdApi.WebAppInfo ? ((TdApi.WebAppInfo) result).launchId : 0;
      if (!session.accept(launchId) || isDestroyed()) return;
      TdApi.WebAppUrl url;
      if (result instanceof TdApi.WebAppInfo) url = ((TdApi.WebAppInfo) result).url;
      else if (result instanceof TdApi.WebAppUrl) url = (TdApi.WebAppUrl) result;
      else if (result instanceof TdApi.MainWebApp) {
        TdApi.MainWebApp main = (TdApi.MainWebApp) result;
        url = main.url;
        fullscreen = main.mode instanceof TdApi.WebAppOpenModeFullScreen;
        expanded = !(main.mode instanceof TdApi.WebAppOpenModeCompact);
      } else {
        if (result instanceof TdApi.Error) UI.showError(result);
        showSheet();
        showLoadError();
        return;
      }
      if (MiniAppOrigin.of(url.url) == null) { showSheet(); showLoadError(); return; }
      document = new MiniAppDocument(url.url, url.requireSameOrigin);
      // Resolve the server's launch mode before the sheet's first visible frame.
      showSheet();
      webView.loadUrl(url.url);
      if (onOpened != null) { onOpened.run(); onOpened = null; }
    }));
  }

  @Override
  protected View onCreateView (Context context) {
    root = new SheetLayout(context);
    column = new LinearLayout(context);
    column.setOrientation(LinearLayout.VERTICAL);
    root.addView(column, new FrameLayout.LayoutParams(-1, -1));
    browserArea = new FrameLayout(context);
    column.addView(browserArea, new LinearLayout.LayoutParams(-1, 0, 1));
    progress = new MiniAppLoadingView(context);
    browserArea.addView(progress, new FrameLayout.LayoutParams(-1, -1));
    errorView = new TextView(context);
    errorView.setText(Lang.getString(R.string.MiniAppLoadError));
    errorView.setTextColor(Theme.textAccentColor());
    errorView.setGravity(Gravity.CENTER);
    errorView.setPadding(Screen.dp(32), Screen.dp(24), Screen.dp(32), Screen.dp(24));
    errorView.setVisibility(View.GONE);
    browserArea.addView(errorView, new FrameLayout.LayoutParams(-1, -1));
    buttons = new MiniAppButtons(context, tdlib, () -> pressed("main_button_pressed"), () -> pressed("secondary_button_pressed"));
    column.addView(buttons, new LinearLayout.LayoutParams(-1, -2));
    header = new LinearLayout(context);
    header.setGravity(Gravity.CENTER_VERTICAL);
    back = icon(R.drawable.baseline_close_24, R.string.MiniAppClose, v -> headerBack());
    back.getLayoutParams().width = Screen.dp(56);
    header.addView(back);
    title = new TextView(context);
    title.setText(tdlib.cache().userName(request.botUserId));
    title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 20);
    title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    title.setSingleLine();
    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    title.setPadding(Screen.dp(16), 0, Screen.dp(8), 0);
    header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    collapse = icon(R.drawable.baseline_direction_arrow_down_24, R.string.MiniAppMinimize, v -> minimize());
    header.addView(collapse);
    more = icon(R.drawable.baseline_more_vert_24, R.string.MiniAppMore, v -> showMenu());
    header.addView(more);
    root.addView(header, new FrameLayout.LayoutParams(-1, Screen.dp(56), Gravity.TOP));
    fullscreenControls = new MiniAppFullscreenControls(context, this::headerBack, this::minimize, this::showMenu);
    root.addView(fullscreenControls, new FrameLayout.LayoutParams(-1, Screen.dp(MiniAppFullscreenControls.CONTENT_INSET_DP), Gravity.TOP));
    fullscreenControls.setVisibility(View.GONE);
    header.setOnTouchListener((v, event) -> {
      float distance = event.getRawY() - dragStartY;
      if (event.getAction() == MotionEvent.ACTION_DOWN) {
        if (sizeAnimator != null) { sizeAnimator.cancel(); sizeAnimator = null; }
        dragStartY = event.getRawY(); dragStartHeight = root.getHeight(); dragging = false;
        return true;
      }
      if (event.getAction() == MotionEvent.ACTION_MOVE && !fullscreen) {
        if (!dragging && Math.abs(distance) < Screen.dp(8)) return true;
        dragging = true;
        if (distance < 0 && !expanded) {
          FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) root.getLayoutParams();
          int maximum = popup.getHeight() - Math.max(popup.getSystemInsets().top, Screen.getStatusBarHeight()) - p.bottomMargin - tdlib.miniApps().tabsHeight(context());
          p.height = Math.min(maximum, dragStartHeight - Math.round(distance));
          root.setLayoutParams(p);
        } else if (distance > 0 && swipeEnabled) root.setTranslationY(distance);
        sendViewport();
        return true;
      }
      if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
        boolean committed = event.getAction() == MotionEvent.ACTION_UP;
        dragging = false;
        if (committed && distance < -Screen.dp(48)) expanded = true;
        if (committed && swipeEnabled && distance > Screen.dp(80)) {
          root.setTranslationY(0); minimize();
        } else {
          root.animate().translationY(0).setDuration(200).start(); resize(true);
        }
        v.performClick();
        return true;
      }
      return true;
    });
    root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> sendViewport());
    browserArea.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> sendViewport());
    createWebView();
    applyTheme();
    return root;
  }

  private ImageView icon (int drawable, int description, View.OnClickListener listener) {
    ImageView view = new ImageView(context());
    view.setImageResource(drawable);
    view.setScaleType(ImageView.ScaleType.CENTER);
    view.setContentDescription(Lang.getString(description));
    view.setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(48), Screen.dp(56)));
    view.setOnClickListener(listener);
    return view;
  }

  @SuppressLint("SetJavaScriptEnabled")
  private void createWebView () {
    try {
      webView = new WebView(context());
      if (!MiniAppPlatform.isolate(webView, tdlib.myUserId(), request.botUserId, tdlib.account().isDebug())) {
        showLoadError();
        return;
      }
      // Like BotWebViewContainer, keep initial HTML hidden until ready() or page completion.
      webView.setAlpha(0f);
      WebSettings settings = webView.getSettings();
      settings.setJavaScriptEnabled(true);
      settings.setDomStorageEnabled(true);
      settings.setTextZoom(100);
      settings.setUseWideViewPort(true);
      settings.setLoadWithOverviewMode(true);
      settings.setAllowFileAccess(false);
      settings.setAllowContentAccess(false);
      settings.setAllowFileAccessFromFileURLs(false);
      settings.setAllowUniversalAccessFromFileURLs(false);
      settings.setMediaPlaybackRequiresUserGesture(true);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
      settings.setSupportMultipleWindows(false);
      bridgeInstalled = platform.install(webView, this);
      if (!bridgeInstalled) { showLoadError(); return; }
      webView.setWebViewClient(new WebViewClient() {
        @Override
        public void onPageStarted (WebView view, String url, Bitmap favicon) {
          beginPageLoad();
          if (document != null) document.navigate();
          cancelDocumentRequests();
          if (sensors != null) sensors.stopAll();
          deferredReplies.clear();
          if (phoneSender != null) { phoneSender.cancel(); phoneSender = null; }
          phoneRequest = writeRequest = null;
          dataSending = false;
          backVisible = settingsVisible = needsCloseConfirmation = false;
          fullscreenControls.setBack(false);
          headerColor = backgroundColor = bottomColor = null;
          headerColorKey = null;
          swipeEnabled = pageAllowsVerticalSwipe = true;
          applyTheme();
          back.setImageResource(R.drawable.baseline_close_24);
          back.setContentDescription(Lang.getString(R.string.MiniAppClose));
          buttons.reset();
          platform.onPageStarted(view);
        }

        @Override
        public void onPageFinished (WebView view, String url) {
          if (isDestroyed()) return;
          platform.onPageStarted(view);
          sendTheme();
          sendViewport();
          // Loading the document can finish before deferred scripts apply the Telegram theme.
          // Give ready() priority; apps which never call it still have a bounded fallback.
          schedulePageReadyFallback();
        }

        @Override
        public boolean shouldOverrideUrlLoading (WebView view, String url) { return routeNavigation(url, true); }

        @Override
        public boolean shouldOverrideUrlLoading (WebView view, WebResourceRequest resource) {
          return routeNavigation(resource.getUrl().toString(), resource.isForMainFrame());
        }

        @Override
        public void onReceivedError (WebView view, WebResourceRequest resource, WebResourceError error) {
          if (resource.isForMainFrame()) showLoadError();
        }

        @Override
        public void onReceivedError (WebView view, int code, String description, String failingUrl) {
          if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) showLoadError();
        }

        @Override
        public boolean onRenderProcessGone (WebView view, RenderProcessGoneDetail detail) {
          close();
          UI.showToast(R.string.MiniAppLoadError, Toast.LENGTH_LONG);
          return true;
        }
      });
      webView.setWebChromeClient(new WebChromeClient() {
        @Override public void onProgressChanged (WebView view, int value) {
          if (!isDestroyed() && view == webView && !pageLoadFailed) progress.setProgress(value);
        }
        @Override public void onPermissionRequest (android.webkit.PermissionRequest request) { media().permission(request); }
        @Override public void onPermissionRequestCanceled (android.webkit.PermissionRequest request) { media().cancelled(request); }
        @Override public void onGeolocationPermissionsShowPrompt (String origin, android.webkit.GeolocationPermissions.Callback callback) { media().geolocation(origin, callback); }
        @Override public void onGeolocationPermissionsHidePrompt () { media().hideGeolocation(); }
        @Override
        public boolean onShowFileChooser (WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
          return media().choose(view.getUrl(), callback, params);
        }
      });
      webView.setOnTouchListener((v, event) -> {
        if (event.getAction() == MotionEvent.ACTION_DOWN) { webTouchX = event.getX(); webTouchY = event.getY(); }
        if (event.getAction() == MotionEvent.ACTION_UP) {
          lastInteraction = SystemClock.uptimeMillis();
          float dy = event.getY() - webTouchY;
          if (!fullscreen && swipeEnabled && pageAllowsVerticalSwipe && webView.getScrollY() == 0 && dy > Screen.dp(80) && Math.abs(event.getX() - webTouchX) < dy / 2f) {
            minimize();
            return true;
          }
        }
        return false;
      });
      browserArea.addView(webView, 0, new FrameLayout.LayoutParams(-1, -1));
    } catch (RuntimeException e) {
      // WebView provider may be disabled or missing. Do not log the authorized URL.
      showLoadError();
    }
  }

  private void showSheet () {
    if (isDestroyed()) return;
    getValue();
    tdlib.miniApps().activate(this);
    minimized = false;
    tdlib.miniApps().updateTabs(context());
    popup = new PopupLayout(context()) {
      @Override
      protected void dispatchDraw (Canvas canvas) {
        super.dispatchDraw(canvas);
        // The expanded sheet colors the status area, but its controls stay below the cutout.
        int top = Math.max(getTopInset(), Screen.getStatusBarHeight());
        if (expanded && !fullscreen && root.getY() <= top + 1 && header.getBackground() instanceof ColorDrawable) {
          canvas.drawRect(0, 0, getWidth(), top, Paints.fillingPaint(((ColorDrawable) header.getBackground()).getColor()));
        }
        // The WebView consumes navigation insets; cover them so the underlying chat/profile cannot show through.
        if ((!fullscreen || buttons.getVisibility() == View.VISIBLE) && buttons.getBackground() instanceof ColorDrawable) {
          Rect systemInsets = context().getRootView().getSystemInsetsWithoutIme();
          int color = ((ColorDrawable) buttons.getBackground()).getColor();
          if (systemInsets.bottom > 0 && getSystemInsets().bottom <= systemInsets.bottom) {
            canvas.drawRect(0, getHeight() - systemInsets.bottom, getWidth(), getHeight(), Paints.fillingPaint(color));
          }
          if (!fullscreen && systemInsets.left > 0) canvas.drawRect(0, 0, systemInsets.left, getHeight(), Paints.fillingPaint(color));
          if (!fullscreen && systemInsets.right > 0) canvas.drawRect(getWidth() - systemInsets.right, 0, getWidth(), getHeight(), Paints.fillingPaint(color));
        }
      }
    };
    popup.init(false);
    popup.addInsetsChangeListener(sheetInsetsListener);
    popup.setNeedRootInsets();
    popup.setOverlayStatusBar(true);
    popup.setPadding(0, 0, 0, fullscreen ? 0 : tdlib.miniApps().tabsHeight(context()));
    popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    popup.setShowListener(window -> {
      // Before API30, FLAG_FULLSCREEN suppresses IME window insets/adjustResize.
      // Measure only the part covered by the IME, so resized windows are not charged twice.
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        legacyKeyboardObserver = window.getViewTreeObserver();
        legacyKeyboardObserver.addOnGlobalLayoutListener(legacyKeyboardListener);
        legacyKeyboardListener.onGlobalLayout();
      }
      // A restored page may not emit a new header color; repaint after the slide animation.
      window.invalidate();
      applyTheme();
      sendViewport();
    });
    popup.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
      if (b - t != ob - ot || r - l != or - ol) resize();
    });
    popup.setBackListener((fromTop, commit) -> {
      if (commit) { if (backVisible) pressed("back_button_pressed"); else minimize(); }
      return true;
    });
    popup.setTouchDownInterceptor((window, event) -> { minimize(); return true; });
    popup.setDismissListener(window -> {
      if (legacyKeyboardObserver != null) {
        if (legacyKeyboardObserver.isAlive()) legacyKeyboardObserver.removeOnGlobalLayoutListener(legacyKeyboardListener);
        legacyKeyboardObserver = null;
      }
      legacyKeyboardInset = 0;
      // PopupLayout destroys its remaining children. A minimized session retains this entire tree.
      if (root.getParent() == window) window.removeView(root);
      if (popup == window) popup = null;
      if (!minimized && !isDestroyed()) destroy();
      else if (restoreWhenHidden && !isDestroyed()) { restoreWhenHidden = false; root.post(this::restore); }
    });
    resize();
    popup.showSimplePopupView(root, root.getLayoutParams().height);
    if (!fullscreen) tdlib.miniApps().raiseTabs(context());
    if (webView != null) webView.onResume();
    platform.setMediaPaused(false);
    if (sensors != null && context().getActivityState() == UI.State.RESUMED) sensors.resume();
    if (orientationRequested) setOrientationLocked(true);
    sendTheme();
    emit("activated", object());
    for (Runnable reply : new ArrayList<>(deferredReplies)) reply.run();
    deferredReplies.clear();
    if (pageReady) revealPageWhenReady();
  }

  private void resize () {
    resize(false);
  }

  private void resize (boolean animate) {
    if (root == null) return;
    if (popup != null) popup.setPadding(0, 0, 0, fullscreen ? 0 : tdlib.miniApps().tabsHeight(context()));
    int available = popup != null && popup.getHeight() > 0 ? popup.getHeight() : context().getRootView().getHeight();
    if (available <= 0) available = context().getResources().getDisplayMetrics().heightPixels;
    if (!fullscreen) available -= tdlib.miniApps().tabsHeight(context());
    Rect insets = sheetSystemInsets();
    Rect stableInsets = stableSystemInsets();
    int top = fullscreen ? stableInsets.top : Math.max(insets.top, Screen.getStatusBarHeight());
    boolean keyboard = insets.bottom > stableInsets.bottom;
    int bottom = !fullscreen || keyboard ? insets.bottom : buttons.getVisibility() == View.VISIBLE ? stableInsets.bottom : 0;
    int height = MiniAppViewport.sheetHeight(available, top, bottom, expanded, fullscreen);
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, height, Gravity.BOTTOM);
    params.bottomMargin = bottom;
    if (!fullscreen) { params.leftMargin = insets.left; params.rightMargin = insets.right; }
    FrameLayout.LayoutParams headerParams = (FrameLayout.LayoutParams) header.getLayoutParams();
    headerParams.topMargin = fullscreen ? top : 0;
    header.setLayoutParams(headerParams);
    header.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
    FrameLayout.LayoutParams controls = (FrameLayout.LayoutParams) fullscreenControls.getLayoutParams();
    controls.topMargin = top;
    controls.leftMargin = stableInsets.left; controls.rightMargin = stableInsets.right;
    fullscreenControls.setLayoutParams(controls);
    fullscreenControls.setVisibility(fullscreen ? View.VISIBLE : View.GONE);
    LinearLayout.LayoutParams buttonParams = (LinearLayout.LayoutParams) buttons.getLayoutParams();
    buttonParams.leftMargin = fullscreen ? stableInsets.left : 0;
    buttonParams.rightMargin = fullscreen ? stableInsets.right : 0;
    buttons.setLayoutParams(buttonParams);
    column.setPadding(0, fullscreen ? 0 : Screen.dp(56), 0, 0);
    if (sizeAnimator != null) { sizeAnimator.cancel(); sizeAnimator = null; }
    if (animate && root.getHeight() > 0 && root.getHeight() != height && !minimized) {
      sizeAnimator = ValueAnimator.ofInt(root.getHeight(), height);
      sizeAnimator.setDuration(250);
      sizeAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
      sizeAnimator.addUpdateListener(a -> { params.height = (int) a.getAnimatedValue(); root.setLayoutParams(params); });
      sizeAnimator.addListener(new AnimatorListenerAdapter() {
        @Override public void onAnimationEnd (Animator animation) { sizeAnimator = null; sendViewport(); }
      });
      sizeAnimator.start();
    } else root.setLayoutParams(params);
    updateFullscreen();
    applyTheme();
    if (popup != null) {
      popup.setNeedFullScreen(fullscreen);
      popup.setIgnoreAllInsets(fullscreen);
    }
    root.post(this::sendViewport);
  }

  private void beginPageLoad () {
    cancelPageReadyFallback();
    if (sensors != null) sensors.stopAll();
    pageLoadGeneration++;
    pageReady = pageRevealRequested = pageLoadFailed = false;
    webView.animate().cancel();
    webView.setAlpha(0f);
    progress.animate().cancel();
    progress.setAlpha(1f);
    progress.setVisibility(View.VISIBLE);
    progress.reset();
    errorView.setVisibility(View.GONE);
  }

  private void cancelPageReadyFallback () {
    if (pageReadyFallback != null && webView != null) webView.removeCallbacks(pageReadyFallback);
    pageReadyFallback = null;
  }

  private void schedulePageReadyFallback () {
    if (pageReady || pageLoadFailed || pageReadyFallback != null || webView == null) return;
    final long generation = pageLoadGeneration;
    final WebView page = webView;
    pageReadyFallback = () -> {
      if (!isDestroyed() && webView == page && pageLoadGeneration == generation) {
        pageReadyFallback = null;
        revealPageWhenReady();
      }
    };
    page.postDelayed(pageReadyFallback, PAGE_READY_FALLBACK_MS);
  }

  private void revealPageWhenReady () {
    if (isDestroyed() || pageLoadFailed || webView == null) return;
    cancelPageReadyFallback();
    pageReady = true;
    if (minimized || popup == null || pageRevealRequested) return;
    pageRevealRequested = true;
    final long generation = pageLoadGeneration;
    final WebView page = webView;
    // DOM theme updates and the renderer's next frame are asynchronous to the JS ready event.
    platform.whenVisualStateReady(page, generation, () -> {
      if (isDestroyed() || page != webView || generation != pageLoadGeneration || pageLoadFailed) return;
      if (minimized) { pageRevealRequested = false; return; }
      page.animate().alpha(1f).setDuration(300).start();
      progress.animate().alpha(0f).setDuration(300).withEndAction(() -> {
        if (!isDestroyed() && generation == pageLoadGeneration) progress.setVisibility(View.GONE);
      }).start();
    });
  }

  private void showLoadError () {
    cancelPageReadyFallback();
    pageLoadGeneration++;
    pageLoadFailed = true;
    pageReady = pageRevealRequested = false;
    if (webView != null) { webView.animate().cancel(); webView.setAlpha(0f); }
    if (progress != null) progress.animate().cancel();
    if (progress != null) progress.setVisibility(View.GONE);
    if (errorView != null) errorView.setVisibility(View.VISIBLE);
  }

  private void headerBack () {
    if (backVisible) pressed("back_button_pressed"); else requestClose();
  }

  public void requestClose () {
    if (isDestroyed()) return;
    if (!needsCloseConfirmation) { close(); return; }
    showAlert(new AlertDialog.Builder(context()).setTitle(Lang.getString(R.string.MiniAppClose))
      .setMessage(Lang.getString(R.string.MiniAppCloseConfirm))
      .setNegativeButton(Lang.getString(R.string.Cancel), null)
      .setPositiveButton(Lang.getString(R.string.MiniAppClose), (d, w) -> close()));
  }

  public void minimize () {
    if (isDestroyed() || minimized) return;
    emit("deactivated", object());
    platform.setMediaPaused(true);
    if (browserArea.getWidth() > 0 && browserArea.getHeight() > 0) {
      if (preview != null) preview.recycle();
      float scale = Math.min(1f, 1080f / browserArea.getWidth());
      preview = Bitmap.createBitmap(Math.max(1, Math.round(browserArea.getWidth() * scale)), Math.max(1, Math.round(browserArea.getHeight() * scale)), Bitmap.Config.RGB_565);
      Canvas canvas = new Canvas(preview);
      canvas.scale(scale, scale);
      browserArea.draw(canvas);
    }
    minimized = true;
    context().clearSystemBarAppearance(this);
    if (sensors != null) sensors.pause();
    updateFullscreen();
    if (webView != null) Keyboard.hide(webView);
    if (webView != null) webView.onPause();
    unlockOrientation();
    cancelDocumentRequests();
    if (popup != null) popup.hideWindow(true);
    tdlib.miniApps().onMinimized(this);
  }

  public void close () { destroy(); }

  @Override
  public void destroy () {
    if (isDestroyed()) return;
    cancelPageReadyFallback();
    pageLoadGeneration++;
    if (webView != null) webView.animate().cancel();
    if (progress != null) progress.animate().cancel();
    session.close();
    if (sensors != null) { sensors.stopAll(); sensors = null; }
    if (buttons != null) buttons.performDestroy();
    context().removeFullScreenView(this, true);
    context().clearSystemBarAppearance(this);
    if (document != null) document.close();
    cancelDocumentRequests();
    deferredReplies.clear();
    if (media != null) media.destroy();
    if (qrScanner != null) qrScanner.destroy();
    if (phoneSender != null) { phoneSender.cancel(); phoneSender = null; }
    unlockOrientation();
    if (sizeAnimator != null) { sizeAnimator.cancel(); sizeAnimator = null; }
    context().removeActivityListener(this);
    tdlib.context().global().removeAccountListener(this);
    tdlib.miniApps().onClosed(this);
    if (preview != null) { preview.recycle(); preview = null; }
    super.destroy();
    if (popup != null) { popup.hideWindow(false); popup = null; }
    if (webView != null) {
      platform.destroy(webView);
      webView.stopLoading();
      webView.setWebChromeClient(null);
      webView.setWebViewClient(null);
      if (webView.getParent() instanceof ViewGroup) ((ViewGroup) webView.getParent()).removeView(webView);
      webView.destroy();
      webView = null;
    }
  }

  @Override
  public void onActivityPause () {
    super.onActivityPause();
    dismissOptions();
    if (sensors != null) sensors.pause();
    if (webView != null) webView.onPause();
    platform.setMediaPaused(true);
    if (location != null) location.pause();
    if (biometry != null) biometry.cancel();
    if (qrScanner != null) qrScanner.pause();
  }

  @Override
  public void onActivityResume () {
    super.onActivityResume();
    if (sensors != null && !minimized) sensors.resume();
    if (webView != null && !minimized) webView.onResume();
    if (!minimized) platform.setMediaPaused(false);
    if (location != null) emit("location_checked", location.info());
    if (biometry != null) emit("biometry_info_received", biometry.info());
  }

  @Override
  public void onAccountSwitched (TdlibAccount account, TdApi.User profile, int reason, TdlibAccount oldAccount) {
    // Never leave a renderer from another account interactive in the current activity.
    if (!tdlib.isCurrent()) close();
  }

  @Override
  public void onConfigurationChanged (Configuration config) {
    super.onConfigurationChanged(config);
    resize();
  }

  private void cancelDocumentRequests () {
    if (requestedChat != null) requestedChat.cancel();
    if (preparedMessage != null) preparedMessage.cancel();
    dismissOptions();
    hideDownloadBulletin();
    if (media != null) media.cancel();
    if (biometry != null) biometry.cancel();
    if (location != null) location.cancel();
    if (qrScanner != null) qrScanner.close();
    if (actions != null) actions.cancel();
    if (activeDialog != null) { activeDialog.dismiss(); activeDialog = null; }
  }

  boolean current (MiniAppDocument.Request origin) {
    return !isDestroyed() && tdlib.isCurrent() && webView != null && document != null && document.isCurrent(origin, webView.getUrl());
  }

  long botId () { return request.botUserId; }
  TdApi.InputMessageReplyTo preparedReplyTo (long chatId, TdApi.MessageTopic topic) {
    return MiniAppPreparedMessagePolicy.replyTo(request.chatId, request.topicId, request.replyTo, chatId, topic);
  }
  boolean canShowNativeUi () { return isShowing() && activeDialog == null && context().getActivityState() == UI.State.RESUMED; }
  String storageScope () { return MiniAppBotPermissions.scope(tdlib, request.botUserId); }
  MiniAppDocument.Request webRequest (String source) {
    return document != null && webView != null && !minimized && tdlib.isCurrent() ? document.receive(source, webView.getUrl(), 0) : null;
  }
  private MiniAppMedia media () { if (media == null) media = new MiniAppMedia(this); return media; }
  private MiniAppBiometry biometry () { if (biometry == null) biometry = new MiniAppBiometry(this); return biometry; }
  MiniAppLocation location () { if (location == null) location = new MiniAppLocation(this); return location; }
  private MiniAppActions actions () { if (actions == null) actions = new MiniAppActions(this); return actions; }

  void nativePrompt (MiniAppDocument.Request origin, String title, String message, int positive, @Nullable View illustration, Runnable accepted, Runnable cancelled) {
    if (!current(origin) || !canShowNativeUi() || requestedChat != null && requestedChat.active() || preparedMessage != null && preparedMessage.active()) { cancelled.run(); return; }
    boolean[] answered = {false};
    AlertDialog.Builder builder = new AlertDialog.Builder(context(), Theme.dialogTheme());
    if (!title.isEmpty()) builder.setTitle(title);
    if (illustration instanceof MiniAppPermissionView || illustration instanceof MiniAppEmojiPreview) {
      LinearLayout content = new LinearLayout(context()); content.setOrientation(LinearLayout.VERTICAL);
      content.addView(illustration, new LinearLayout.LayoutParams(-1, -2));
      TextView text = new TextView(context()); text.setText(message); text.setTextSize(16); text.setTextColor(Theme.textAccentColor());
      text.setPadding(Screen.dp(24), Screen.dp(16), Screen.dp(24), Screen.dp(8)); content.addView(text, new LinearLayout.LayoutParams(-1, -2));
      builder.setView(content);
    } else { builder.setMessage(message); if (illustration != null) builder.setView(illustration); }
    builder.setNegativeButton(Lang.getString(R.string.Cancel), null).setPositiveButton(Lang.getString(positive), (dialog, which) -> {
      answered[0] = true; dialog.dismiss(); recordGesture();
      if (current(origin) && !minimized) accepted.run(); else cancelled.run();
    });
    AlertDialog dialog = showAlert(builder); activeDialog = dialog;
    if (dialog == null) { cancelled.run(); return; }
    if (dialog.getWindow() != null) {
      android.graphics.drawable.Drawable previous = dialog.getWindow().getDecorView().getBackground();
      Rect padding = new Rect(); if (previous != null) previous.getPadding(padding);
      GradientDrawable shape = new GradientDrawable(); shape.setColor(Theme.fillingColor()); shape.setCornerRadius(Screen.dp(10));
      dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.InsetDrawable(shape, padding.left, padding.top, padding.right, padding.bottom));
      dialog.getWindow().getDecorView().setClipToOutline(true);
    }
    dialog.setOnDismissListener(ignored -> {
      if (activeDialog == dialog) activeDialog = null;
      if (illustration instanceof org.thunderdog.challegram.widget.CustomEmojiTextView) ((org.thunderdog.challegram.widget.CustomEmojiTextView) illustration).performDestroy();
      if (!answered[0]) { answered[0] = true; cancelled.run(); }
    });
  }

  private void showPermissions (MiniAppDocument.Request origin, boolean biometric) {
    minimize();
    tdlib.ui().openPrivateProfile(this, request.botUserId, null);
  }

  @Override
  public void onMessage (String message, String sourceOrigin) {
    if (isDestroyed() || !tdlib.isCurrent() || document == null || webView == null) return;
    MiniAppDocument.Request origin = document.receive(sourceOrigin, webView.getUrl(), message.length());
    if (origin == null) return;
    try {
      JSONObject envelope = new JSONObject(message);
      String event = envelope.getString("eventType");
      if (event.isEmpty() || event.length() > 128) return;
      Object data = envelope.opt("eventData");
      if (message.length() > MiniAppDocument.MAX_ACTION_LENGTH && !"web_app_device_storage_save_key".equals(event) && !"web_app_secure_storage_save_key".equals(event)) return;
      if (data instanceof String && !((String) data).isEmpty()) data = new JSONTokener((String) data).nextValue();
      // A page may open a Telegram link and then close itself. The link minimizes
      // its host first; still accept the authenticated close, without enabling
      // other actions or return-to-caller navigation from a background app.
      if (minimized) {
        if ("web_app_close".equals(event)) close();
        // Remember readiness without showing or activating a background app.
        else if ("web_app_ready".equals(event)) revealPageWhenReady();
        return;
      }
      if ("web_app_allow_scroll".equals(event)) {
        pageAllowsVerticalSwipe = !(data instanceof JSONArray) || ((JSONArray) data).optBoolean(1, true);
        return;
      }
      JSONObject args = data instanceof JSONObject ? (JSONObject) data : new JSONObject();
      dispatch(origin, event, args);
    } catch (JSONException | IllegalArgumentException ignored) {
      // Malformed web input is untrusted. It must not crash the host or leak initData to logs.
    }
  }

  private void dispatch (MiniAppDocument.Request origin, String event, JSONObject data) throws JSONException {
    switch (event) {
      case "web_app_ready": revealPageWhenReady(); break;
      case "web_app_request_theme": sendTheme(); break;
      case "web_app_request_viewport": sendViewport(); break;
      case "web_app_expand": if (!expanded) { expanded = true; resize(true); } break;
      case "web_app_close":
        close();
        if (data.optBoolean("return_back") && request.returnToCaller != null) request.returnToCaller.run();
        break;
      case "web_app_setup_closing_behavior": needsCloseConfirmation = data.optBoolean("need_confirmation"); break;
      case "web_app_setup_swipe_behavior": swipeEnabled = data.optBoolean("allow_vertical_swipe", true); break;
      case "web_app_hide_keyboard": Keyboard.hide(webView); break;
      case "web_app_setup_back_button":
        backVisible = data.optBoolean("is_visible");
        back.setImageResource(backVisible ? R.drawable.baseline_arrow_back_24 : R.drawable.baseline_close_24);
        back.setContentDescription(Lang.getString(backVisible ? R.string.MiniAppBack : R.string.MiniAppClose));
        fullscreenControls.setBack(backVisible);
        break;
      case "web_app_setup_settings_button": settingsVisible = data.optBoolean("is_visible"); break;
      case "web_app_setup_main_button": buttons.configure(false, data); resize(); break;
      case "web_app_setup_secondary_button": buttons.configure(true, data); resize(); break;
      case "web_app_set_header_color":
        if (data.has("color")) { headerColor = MiniAppButtons.parseColor(data.optString("color")); headerColorKey = null; }
        else if ("bg_color".equals(data.optString("color_key")) || "secondary_bg_color".equals(data.optString("color_key"))) {
          headerColorKey = data.optString("color_key");
          headerColor = null;
        }
        applyTheme();
        break;
      case "web_app_set_background_color": backgroundColor = MiniAppButtons.parseColor(data.optString("color")); applyTheme(); break;
      case "web_app_set_bottom_bar_color": bottomColor = MiniAppButtons.parseColor(data.optString("color")); applyTheme(); break;
      case "web_app_open_popup": openPopup(origin, data); break;
      case "web_app_trigger_haptic_feedback":
        MiniAppHaptics.perform(context(), data);
        break;
      case "web_app_request_fullscreen": fullscreen = expanded = true; resize(); emit("fullscreen_changed", object("is_fullscreen", true)); sendSafeAreas(); break;
      case "web_app_exit_fullscreen": fullscreen = false; resize(); emit("fullscreen_changed", object("is_fullscreen", false)); sendSafeAreas(); break;
      case "web_app_request_safe_area":
      case "web_app_request_content_safe_area": sendSafeAreas(); break;
      case "web_app_toggle_orientation_lock": setOrientationLocked(data.optBoolean("locked")); break;
      case "web_app_open_link":
        openLink(origin, data);
        break;
      case "web_app_open_tg_link":
        String path = data.optString("path_full");
        if (path.startsWith("/") && !path.startsWith("//")) { minimize(); tdlib.ui().openUrl(this, "https://t.me" + path, null); }
        break;
      case "web_app_data_send": sendData(origin, data.getString("data")); break;
      case "web_app_invoke_custom_method": customMethod(origin, data); break;
      case "web_app_request_write_access": requestWriteAccess(origin); break;
      case "web_app_request_phone": requestPhone(origin); break;
      case "web_app_biometry_get_info": biometry().notifyInfo(origin); break;
      case "web_app_biometry_request_access": biometry().requestAccess(origin, data.optString("reason", "")); break;
      case "web_app_biometry_request_auth": biometry().requestAuth(origin, data.optString("reason", "")); break;
      case "web_app_biometry_update_token": biometry().updateToken(origin, data.getString("token"), data.optString("reason", "")); break;
      case "web_app_biometry_open_settings": if (recentGesture()) { lastInteraction = 0; showPermissions(origin, true); } break;
      case "web_app_check_location": reply(origin, "location_checked", location().info()); break;
      case "web_app_request_location": location().request(origin); break;
      case "web_app_open_location_settings": if (recentGesture()) { lastInteraction = 0; showPermissions(origin, false); } break;
      case "web_app_open_scan_qr_popup":
        if (qrScanner == null) qrScanner = new MiniAppQrScanner(this);
        qrScanner.open(origin, data.optString("text", "")); break;
      case "web_app_close_scan_qr_popup": if (qrScanner != null) qrScanner.close(); break;
      case "web_app_request_file_download": actions().download(origin, data); break;
      case "web_app_check_home_screen": reply(origin, "home_screen_checked", object("status", actions().shortcutStatus())); break;
      case "web_app_add_to_home_screen": actions().addShortcut(origin); break;
      case "web_app_set_emoji_status": actions().emojiStatus(origin, data); break;
      case "web_app_request_emoji_status_access": actions().emojiAccess(origin); break;
      case "web_app_start_accelerometer":
      case "web_app_stop_accelerometer":
      case "web_app_start_gyroscope":
      case "web_app_stop_gyroscope":
      case "web_app_start_device_orientation":
      case "web_app_stop_device_orientation": {
        if (sensors == null) sensors = new MiniAppSensors(context());
        if (context().getActivityState() != UI.State.RESUMED) sensors.pause();
        boolean start = event.startsWith("web_app_start_");
        String type = event.substring(start ? 14 : 13);
        int kind = "accelerometer".equals(type) ? 0 : "gyroscope".equals(type) ? 1 : 2;
        boolean ok = start ? sensors.start(kind, data.optLong("refresh_rate", 1000), data.optBoolean("need_absolute"),
          (name, payload) -> reply(origin, name, payload)) : sensors.stop(kind);
        reply(origin, type + (ok ? start ? "_started" : "_stopped" : "_failed"), ok ? object() : object("error", "UNSUPPORTED"));
        break;
      }
      case "web_app_switch_inline_query": switchInline(data); break;
      case "web_app_send_prepared_message":
        if (!recentGesture() || requestedChat != null && requestedChat.active()) break;
        if (preparedMessage == null) preparedMessage = new MiniAppPreparedMessage(this);
        preparedMessage.request(origin, data); break;
      case "web_app_request_chat":
        if (preparedMessage != null && preparedMessage.active()) break;
        if (requestedChat == null) requestedChat = new MiniAppRequestedChat(this);
        requestedChat.request(origin, data); break;
      case "web_app_read_text_from_clipboard": {
        JSONObject response = object("req_id", data.getString("req_id"));
        if (recentGesture() && (tdlib.isTrustedMiniAppBot(request.botUserId) || tdlib.miniApps().isAddedToMenu(request.botUserId))) {
          android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context().getSystemService(Context.CLIPBOARD_SERVICE);
          android.content.ClipData clip = clipboard != null ? clipboard.getPrimaryClip() : null;
          CharSequence text = clip != null && clip.getItemCount() > 0 ? clip.getItemAt(0).getText() : null;
          response.put("data", text != null ? text.toString() : "");
        }
        reply(origin, "clipboard_text_received", response);
        break;
      }
      case "web_app_device_storage_save_key":
      case "web_app_device_storage_get_key":
      case "web_app_device_storage_clear":
      case "web_app_secure_storage_save_key":
      case "web_app_secure_storage_get_key":
      case "web_app_secure_storage_clear":
      case "web_app_secure_storage_restore_key": storage(origin, event, data); break;
      default:
        // Further native capabilities are added as separate services; never invent successful replies.
        break;
    }
  }

  private void switchInline (JSONObject data) {
    String username = tdlib.cache().userUsername(request.botUserId);
    TdApi.User bot = tdlib.cache().user(request.botUserId);
    if (username == null || username.isEmpty() || bot == null || !(bot.type instanceof TdApi.UserTypeBot) || !((TdApi.UserTypeBot) bot.type).isInline) return;
    String query = data.optString("query");
    if (query.length() > 256) return;
    JSONArray chatTypes = data.optJSONArray("chat_types");
    ViewController<?> parent = context().navigation().getCurrentStackItem();
    if (chatTypes == null || chatTypes.length() == 0) {
      if (parent instanceof org.thunderdog.challegram.ui.MessagesController) {
        ((org.thunderdog.challegram.ui.MessagesController) parent).switchInline(request.botUserId,
          new TdApi.InlineKeyboardButtonTypeSwitchInline(query, new TdApi.TargetChatCurrent()));
        close();
      }
      return;
    }
    TdApi.TargetChatTypes types = new TdApi.TargetChatTypes();
    for (int i = 0; i < chatTypes.length(); i++) {
      switch (chatTypes.optString(i)) {
        case "users": types.allowUserChats = true; break;
        case "bots": types.allowBotChats = true; break;
        case "groups": types.allowGroupChats = true; break;
        case "channels": types.allowChannelChats = true; break;
      }
    }
    org.thunderdog.challegram.telegram.ChatFilter filter = chat -> MiniAppChatPolicy.allows(types, chat, tdlib.isBotChat(chat)) && tdlib.canSendBasicMessage(chat);
    org.thunderdog.challegram.ui.ChatsController picker = new org.thunderdog.challegram.ui.ChatsController(context(), tdlib);
    picker.setArguments(new org.thunderdog.challegram.ui.ChatsController.Arguments(filter, new org.thunderdog.challegram.ui.ChatsController.PickerDelegate() {
      @Override public boolean onChatPicked (TdApi.Chat chat, Runnable done) { return filter.accept(chat); }
      @Override public Object getShareItem () { return new org.thunderdog.challegram.data.TGSwitchInline(username, query); }
    }));
    minimize(); context().navigation().navigateTo(picker);
  }

  private java.util.Set<Long> activeStorageAccounts () {
    java.util.Set<Long> active = new java.util.HashSet<>();
    for (TdlibAccount account : tdlib.context()) {
      if (!account.isUnauthorized() && account.isDebug() == tdlib.account().isDebug()) active.add(account.getKnownUserId());
    }
    return active;
  }

  private void storage (MiniAppDocument.Request origin, String event, JSONObject data) throws JSONException {
    final String id = data.getString("req_id");
    if (id.length() > 128) return;
    boolean secure = event.startsWith("web_app_secure_");
    String prefix = secure ? "secure_storage_" : "device_storage_";
    String operation = event.substring(("web_app_" + prefix).length());
    if (!operation.equals("clear") && !(data.opt("key") instanceof String)) {
      reply(origin, prefix + "failed", object("req_id", id, "error", "KEY_INVALID")); return;
    }
    if (operation.equals("save_key") && !data.isNull("value") && !(data.opt("value") instanceof String)) {
      reply(origin, prefix + "failed", object("req_id", id, "error", "VALUE_INVALID")); return;
    }
    String key = data.optString("key", "");
    String value = data.isNull("value") ? null : data.optString("value", null);
    MiniAppStorage store = new MiniAppStorage(context(), tdlib.myUserId(), request.botUserId, secure, tdlib.account().isDebug());
    String userName = tdlib.cache().userName(tdlib.myUserId());
    java.util.Set<Long> active = activeStorageAccounts();
    MiniAppStorage.IO.execute(() -> {
      try {
        store.setOwnerName(userName);
        if (operation.equals("restore_key")) {
          List<Long> candidates = store.restorable(key, active);
          String[] labels = new String[candidates.size()];
          for (int i = 0; i < labels.length; i++) labels[i] = store.ownerName(candidates.get(i));
          tdlib.ui().post(() -> restoreStorage(origin, id, key, store, candidates, labels));
          return;
        }
        JSONObject response = object("req_id", id);
        String result;
        if (operation.equals("save_key")) { store.set(key, value); result = "key_saved"; }
        else if (operation.equals("clear")) { store.clear(); result = "cleared"; }
        else {
          String stored = store.get(key);
          response.put("value", stored != null ? stored : JSONObject.NULL);
          if (secure && stored == null) response.put("can_restore", !store.restorable(key, active).isEmpty());
          result = "key_received";
        }
        tdlib.ui().post(() -> reply(origin, prefix + result, response));
      } catch (Exception error) {
        String reason = storageError(error);
        tdlib.ui().post(() -> reply(origin, prefix + "failed", object("req_id", id, "error", reason)));
      }
    });
  }

  private static String storageError (Exception error) {
    String message = error.getMessage();
    return "QUOTA_EXCEEDED".equals(message) || "UNSUPPORTED".equals(message) || "STORAGE_NOT_EMPTY".equals(message) ||
      "RESTORE_UNAVAILABLE".equals(message) ? message : "UNKNOWN_ERROR";
  }

  private void restoreStorage (MiniAppDocument.Request origin, String id, String key, MiniAppStorage store, List<Long> candidates, String[] labels) {
    if (!current(origin)) return;
    if (candidates.isEmpty()) { reply(origin, "secure_storage_failed", object("req_id", id, "error", "RESTORE_UNAVAILABLE")); return; }
    if (minimized || activeDialog != null) { reply(origin, "secure_storage_failed", object("req_id", id, "error", "RESTORE_CANCELLED")); return; }
    for (int i = 0; i < labels.length; i++) if (labels[i].isEmpty()) labels[i] = Lang.getString(R.string.MiniAppStorageAccount, Integer.toString(i + 1));
    activeDialog = showAlert(new AlertDialog.Builder(context()).setTitle(Lang.getString(R.string.MiniAppRestoreStorage))
      .setItems(labels, (dialog, which) -> {
        if (!current(origin)) return;
        java.util.Set<Long> active = activeStorageAccounts();
        MiniAppStorage.IO.execute(() -> {
          try {
            String value = store.restore(candidates.get(which), key, active);
            tdlib.ui().post(() -> reply(origin, "secure_storage_key_restored", object("req_id", id, "value", value != null ? value : JSONObject.NULL)));
          } catch (Exception error) {
            tdlib.ui().post(() -> reply(origin, "secure_storage_failed", object("req_id", id, "error", storageError(error))));
          }
        });
      }).setNegativeButton(Lang.getString(R.string.Cancel), (dialog, which) -> reply(origin, "secure_storage_failed", object("req_id", id, "error", "RESTORE_CANCELLED")))
      .setOnCancelListener(dialog -> reply(origin, "secure_storage_failed", object("req_id", id, "error", "RESTORE_CANCELLED"))));
    if (activeDialog != null) activeDialog.setOnDismissListener(dialog -> activeDialog = null);
  }

  private void sendData (MiniAppDocument.Request origin, String data) {
    if (request.source != MiniAppLaunchRequest.Source.REPLY_KEYBOARD || dataSending || data.isEmpty() || data.getBytes(Charset.forName("UTF-8")).length > 4096) return;
    dataSending = true;
    tdlib.send(new TdApi.SendWebAppData(request.botUserId, request.buttonText, data), (ok, error) -> tdlib.ui().post(() -> {
      if (!current(origin)) return;
      dataSending = false;
      if (error == null) close(); else UI.showError(error);
    }));
  }

  private void customMethod (MiniAppDocument.Request origin, JSONObject data) throws JSONException {
    String id = data.getString("req_id");
    String method = data.getString("method");
    Object parameters = data.get("params");
    if (id.length() > 128 || method.isEmpty() || method.length() > 256) return;
    String json = parameters instanceof String ? JSONObject.quote((String) parameters) : parameters.toString();
    tdlib.send(new TdApi.SendWebAppCustomRequest(request.botUserId, method, json), (result, error) -> tdlib.ui().post(() -> {
      if (!current(origin)) return;
      JSONObject response = object("req_id", id);
      try {
        if (error != null) response.put("error", error.message);
        else response.put("result", new JSONTokener(result.result).nextValue());
      } catch (JSONException e) { response = object("req_id", id, "error", "INVALID_RESPONSE"); }
      reply(origin, "custom_method_invoked", response);
    }));
  }

  private void requestWriteAccess (MiniAppDocument.Request origin) {
    if (writeRequest != null) return;
    if (ignoreBotDialog(3)) { reply(origin, "write_access_requested", object("status", "cancelled")); return; }
    writeRequest = origin;
    tdlib.send(new TdApi.CanBotSendMessages(request.botUserId), (ok, error) -> tdlib.ui().post(() -> {
      if (!current(origin) || writeRequest != origin) return;
      if (error == null) { finishWrite(origin, true); return; }
      if (error.code != 404) { UI.showError(error); finishWrite(origin, false); return; }
      recordBotDialog(3);
      nativePrompt(origin, Lang.getString(R.string.MiniAppWriteAccessTitle), Lang.getString(R.string.MiniAppWriteAccess, botName()), R.string.Allow, null, () ->
        tdlib.send(new TdApi.AllowBotToSendMessages(request.botUserId), (result, allowError) -> tdlib.ui().post(() -> {
          if (!current(origin) || writeRequest != origin) return;
          if (allowError != null) UI.showError(allowError);
          finishWrite(origin, allowError == null);
        })), () -> finishWrite(origin, false));
    }));
  }
  private void finishWrite (MiniAppDocument.Request origin, boolean allowed) {
    if (writeRequest != origin) return;
    writeRequest = null; reply(origin, "write_access_requested", object("status", allowed ? "allowed" : "cancelled"));
  }

  private void requestPhone (MiniAppDocument.Request origin) {
    if (phoneSender != null || phoneRequest != null) return;
    if (ignoreBotDialog(4)) { reply(origin, "phone_requested", object("status", "cancelled")); return; }
    phoneRequest = origin;
    TdApi.UserFullInfo bot = tdlib.cache().userFull(request.botUserId);
    boolean blocked = tdlib.chatFullyBlocked(tgx.td.ChatId.fromUserId(request.botUserId)) || bot != null && bot.blockList instanceof TdApi.BlockListMain;
    String message = Lang.getString(R.string.MiniAppSharePhone, botName());
    if (blocked) message += "\n\n" + Lang.getString(R.string.MiniAppSharePhoneUnblock);
    recordBotDialog(4);
    nativePrompt(origin, Lang.getString(R.string.MiniAppSharePhoneTitle), message, R.string.ShareContact, null, () -> {
      if (blocked) {
        tdlib.send(new TdApi.SetMessageSenderBlockList(new TdApi.MessageSenderUser(request.botUserId), null), (ok, error) -> tdlib.ui().post(() -> {
          if (!current(origin) || phoneRequest != origin) return;
          if (error != null) { UI.showError(error); finishPhone(origin, false); } else sendContact(origin);
        }));
      } else sendContact(origin);
    }, () -> finishPhone(origin, false));
  }
  private void sendContact (MiniAppDocument.Request origin) {
      TdApi.User me = tdlib.myUser();
      if (me == null || me.phoneNumber.isEmpty()) { finishPhone(origin, false); return; }
      tdlib.send(new TdApi.CreatePrivateChat(request.botUserId, false), (chat, error) -> tdlib.ui().post(() -> {
        if (!current(origin) || phoneRequest != origin) return;
        if (error != null) { UI.showError(error); finishPhone(origin, false); return; }
        TdApi.Contact contact = new TdApi.Contact(me.phoneNumber, me.firstName, me.lastName, "", me.id);
        phoneSender = new MiniAppMessageSender(tdlib, new TdApi.SendMessage(chat.id, null, null, Td.newSendOptions(), null, new TdApi.InputMessageContact(contact)), sendError -> {
          phoneSender = null;
          if (!current(origin)) return;
          if (sendError != null) UI.showError(sendError);
          finishPhone(origin, sendError == null);
        });
        phoneSender.send();
      }));
  }
  private int lastBotDialog = -1, shownBotDialogs;
  private long blockedBotDialogsUntil;
  private boolean ignoreBotDialog (int type) {
    if (activeDialog != null || SystemClock.uptimeMillis() < blockedBotDialogsUntil) return true;
    if (lastBotDialog == type && shownBotDialogs > 3) {
      blockedBotDialogsUntil = SystemClock.uptimeMillis() + 3000; shownBotDialogs = 0; return true;
    }
    return false;
  }
  private void recordBotDialog (int type) { if (lastBotDialog != type) { lastBotDialog = type; shownBotDialogs = 0; blockedBotDialogsUntil = 0; } shownBotDialogs++; }
  private void finishPhone (MiniAppDocument.Request origin, boolean sent) {
    if (phoneRequest != origin) return;
    phoneRequest = null; reply(origin, "phone_requested", object("status", sent ? "sent" : "cancelled"));
  }

  private void confirmAction (MiniAppDocument.Request origin, String message, String response, Runnable accepted) {
    if (activeDialog != null || minimized || isPaused()) { reply(origin, response, object("status", "cancelled")); return; }
    boolean[] answered = { false };
    activeDialog = showAlert(new AlertDialog.Builder(context()).setTitle(tdlib.cache().userName(request.botUserId)).setMessage(message)
      .setNegativeButton(Lang.getString(R.string.Cancel), null)
      .setPositiveButton(Lang.getString(R.string.OK), (dialog, which) -> {
        answered[0] = true;
        if (current(origin)) accepted.run();
      }));
    if (activeDialog != null) activeDialog.setOnDismissListener(dialog -> {
      activeDialog = null;
      if (!answered[0]) reply(origin, response, object("status", "cancelled"));
    });
  }

  private void openPopup (MiniAppDocument.Request origin, JSONObject data) throws JSONException {
    if (activeDialog != null || requestedChat != null && requestedChat.active() || preparedMessage != null && preparedMessage.active()) return;
    long now = SystemClock.uptimeMillis();
    if (now < popupCooldownUntil) return;
    if (now - lastPopupClosed <= 150 && ++sequentialPopups >= 3) {
      sequentialPopups = 0; popupCooldownUntil = now + 3000; return;
    }
    String heading = data.optString("title", ""), message = data.getString("message");
    JSONArray options = data.getJSONArray("buttons");
    if (heading.length() > 64 || message.length() > 256 || options.length() < 1 || options.length() > 3) return;
    AlertDialog.Builder builder = new AlertDialog.Builder(context(), Theme.dialogTheme()).setTitle(heading.isEmpty() ? null : heading).setMessage(message);
    boolean[] answered = { false };
    int[] whichButtons = { AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL };
    boolean[] destructive = new boolean[options.length()];
    for (int i = 0; i < options.length(); i++) {
      JSONObject button = options.getJSONObject(i);
      String id = button.optString("id", ""), type = button.optString("type", "default");
      String label = button.optString("text", "");
      if ("ok".equals(type)) label = Lang.getString(R.string.OK);
      else if ("cancel".equals(type)) label = Lang.getString(R.string.Cancel);
      else if ("close".equals(type)) label = Lang.getString(R.string.MiniAppPopupClose);
      if (label.isEmpty() || label.length() > 64 || id.length() > 64) return;
      destructive[i] = "destructive".equals(type);
      android.content.DialogInterface.OnClickListener listener = (dialog, which) -> {
        answered[0] = true;
        lastInteraction = SystemClock.uptimeMillis();
        reply(origin, "popup_closed", object("button_id", id));
      };
      if (i == 0) builder.setPositiveButton(label, listener);
      else if (i == 1) builder.setNegativeButton(label, listener);
      else builder.setNeutralButton(label, listener);
    }
    activeDialog = showAlert(builder);
    if (activeDialog == null) return;
    for (int i = 0; i < destructive.length; i++) if (destructive[i]) activeDialog.getButton(whichButtons[i]).setTextColor(Theme.getColor(ColorId.textNegative));
    activeDialog.setOnDismissListener(dialog -> {
      if (!answered[0]) reply(origin, "popup_closed", new JSONObject());
      activeDialog = null;
      lastPopupClosed = SystemClock.uptimeMillis();
    });
  }

  private void openLink (MiniAppDocument.Request origin, JSONObject data) {
    if (!recentGesture()) return;
    lastInteraction = 0;
    String url = data.optString("url"), browser = MiniAppActionPolicy.browserPackage(data.optString("try_browser"));
    boolean instantView = data.optBoolean("try_instant_view");
    tdlib.send(new TdApi.GetOption("web_app_allowed_protocols"), (value, error) -> tdlib.ui().post(() -> {
      if (!current(origin) || minimized) return;
      String protocols = value instanceof TdApi.OptionValueString ? ((TdApi.OptionValueString) value).value : "http https";
      if (!MiniAppActionPolicy.externalUrl(url, protocols)) return;
      if (browser != null) {
        try { context().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(browser)); return; }
        catch (android.content.ActivityNotFoundException ignored) { }
      }
      TdlibUi.UrlOpenParameters options = new TdlibUi.UrlOpenParameters();
      if (instantView) options.forceInstantView(); else options.disableInstantView();
      tdlib.ui().openUrl(this, url, options);
    }));
  }

  private boolean routeNavigation (String url, boolean mainFrame) {
    Uri uri = Uri.parse(url);
    String scheme = uri.getScheme(), host = uri.getHost();
    boolean telegram = "tg".equalsIgnoreCase(scheme) || "t.me".equalsIgnoreCase(host) || "telegram.me".equalsIgnoreCase(host);
    if (telegram) {
      if (mainFrame && recentGesture()) { minimize(); tdlib.ui().openUrl(this, url, null); }
      return true;
    }
    return MiniAppOrigin.of(url) == null;
  }

  boolean recentGesture () { return lastInteraction != 0 && SystemClock.uptimeMillis() - lastInteraction < 10000; }
  void recordGesture () { lastInteraction = SystemClock.uptimeMillis(); }

  private void pressed (String event) {
    lastInteraction = SystemClock.uptimeMillis();
    emit(event, new JSONObject());
  }

  private void showMenu () {
    dismissOptions(); options = new MiniAppOptions(this);
    if (!actions().downloads().isEmpty()) {
      options.add(R.drawable.baseline_file_download_24, Lang.getString(R.string.Downloads), () -> actions().showDownloads()); options.gap();
    }
    options.add(R.drawable.miniapp_msg_bot, Lang.getString(R.string.MiniAppOpenBot), () -> { minimize(); tdlib.ui().openPrivateChat(this, request.botUserId, null); });
    if (settingsVisible) options.add(R.drawable.baseline_settings_24, Lang.getString(R.string.Settings), () -> pressed("settings_button_pressed"));
    options.add(R.drawable.miniapp_msg_retry, Lang.getString(R.string.MiniAppReload), () -> { if (webView != null && document != null) { document.navigate(); webView.reload(); } });
    TdApi.User bot = tdlib.cache().user(request.botUserId);
    if (bot != null && bot.type instanceof TdApi.UserTypeBot && ((TdApi.UserTypeBot) bot.type).hasMainWebApp) {
      options.add(R.drawable.miniapp_msg_home, Lang.getString(R.string.MiniAppAddShortcut), () -> {
        recordGesture(); if (webView != null) { MiniAppDocument.Request origin = webRequest(webView.getUrl()); if (origin != null) actions().addShortcut(origin); }
      });
    }
    options.add(R.drawable.miniapp_menu_intro, Lang.getString(R.string.MiniAppTerms), () -> tdlib.ui().openUrl(this, "https://telegram.org/tos/mini-apps", null));
    options.add(R.drawable.miniapp_msg_report, Lang.getString(R.string.Report), () ->
      tdlib.send(new TdApi.CreatePrivateChat(request.botUserId, false), (chat, error) -> tdlib.ui().post(() -> {
        if (!isShowing()) return;
        if (error != null) UI.showError(error);
        else org.thunderdog.challegram.telegram.TdlibUi.reportChat(this, chat.id, null, null, null, false);
      })));
    TdApi.AttachmentMenuBot menuBot = tdlib.miniApps().addedMenuBot(request.botUserId);
    if (menuBot != null) options.add(R.drawable.baseline_delete_24, Lang.getString(R.string.MiniAppRemoveBot), () -> tdlib.miniApps().removeMenuBot(this, menuBot));
    options.show(fullscreen ? fullscreenControls : more);
  }

  private void dismissOptions () { if (options != null) { options.dismiss(); options = null; } }
  void showDownloadsMenu (MiniAppActions actions) {
    dismissOptions(); options = new MiniAppOptions(this); options.downloads(actions, this::showMenu);
    options.show(fullscreen ? fullscreenControls : more);
  }
  void showDownloadBulletin (MiniAppActions actions, long id, String name) {
    hideDownloadBulletin();
    downloadBulletin = new MiniAppDownloadBulletin(this, actions, id, name, this::hideDownloadBulletin);
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, Screen.dp(64), Gravity.TOP);
    params.leftMargin = params.rightMargin = Screen.dp(8);
    params.topMargin = (fullscreen ? fullscreenControls.getBottom() : column.getTop() + header.getBottom()) + Screen.dp(8);
    root.addView(downloadBulletin, params);
  }
  private void hideDownloadBulletin () {
    if (downloadBulletin != null) { downloadBulletin.stop(); root.removeView(downloadBulletin); downloadBulletin = null; }
  }

  private void setOrientationLocked (boolean locked) {
    orientationRequested = locked;
    if (locked == orientationLocked) return;
    if (locked) {
      previousOrientation = context().getRequestedOrientation();
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) context().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
      else context().setRequestedOrientation(context().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
      orientationLocked = true;
    } else unlockOrientation();
  }

  private void unlockOrientation () {
    if (orientationLocked) { orientationLocked = false; context().setRequestedOrientation(previousOrientation); }
  }

  private void updateFullscreen () {
    if (fullscreen && !minimized && !isDestroyed()) context().addFullScreenView(this, true);
    else context().removeFullScreenView(this, true);
    tdlib.miniApps().updateTabsVisibility(context());
  }

  private void applyTheme () {
    if (root == null) return;
    TdApi.ThemeParameters theme = MiniAppTheme.parameters();
    int background = backgroundColor != null ? backgroundColor : theme.backgroundColor;
    int bar = headerColor != null ? headerColor : "secondary_bg_color".equals(headerColorKey) ? theme.secondaryBackgroundColor : theme.backgroundColor;
    GradientDrawable shape = new GradientDrawable();
    shape.setColor(background);
    float radius = expanded || fullscreen ? 0 : Screen.dp(16);
    shape.setCornerRadii(new float[] { radius, radius, radius, radius, 0, 0, 0, 0 });
    root.setBackground(shape);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) root.setClipToOutline(true);
    header.setBackgroundColor(bar);
    int foreground = (.299 * Color.red(bar) + .587 * Color.green(bar) + .114 * Color.blue(bar)) > 150 ? Color.BLACK : Color.WHITE;
    title.setTextColor(foreground);
    back.setColorFilter(foreground);
    collapse.setColorFilter(foreground);
    more.setColorFilter(foreground);
    browserArea.setBackgroundColor(background);
    progress.refreshColors(background);
    if (webView != null) webView.setBackgroundColor(background);
    buttons.setBackgroundColor(bottomColor != null ? bottomColor : theme.bottomBarBackgroundColor);
    buttons.refreshTheme();
    if (popup != null && !minimized && !isDestroyed()) {
      // Match the official sheet's luminance threshold, independent of the chat theme.
      Boolean lightStatus = expanded && !fullscreen ? androidx.core.graphics.ColorUtils.calculateLuminance(bar) >= .721 : null;
      Boolean lightNavigation = !fullscreen || buttons.getVisibility() == View.VISIBLE
        ? androidx.core.graphics.ColorUtils.calculateLuminance(bottomColor != null ? bottomColor : theme.bottomBarBackgroundColor) >= .721 : null;
      context().setSystemBarAppearance(this, lightStatus, lightNavigation);
    }
    if (popup != null) popup.invalidate();
  }

  @Override
  public void onThemeColorsChanged (boolean temporary, @Nullable ColorState state) {
    super.onThemeColorsChanged(temporary, state);
    applyTheme();
    sendTheme();
    tdlib.miniApps().updateTabs(context());
  }

  private void sendTheme () { emit("theme_changed", object("theme_params", MiniAppTheme.json())); }

  private void sendViewport () {
    if (webView == null || root == null) return;
    emit("viewport_changed", object("height", webView.getHeight() / context().getResources().getDisplayMetrics().density,
      "is_expanded", expanded, "is_state_stable", sizeAnimator == null && !dragging));
    sendSafeAreas();
  }

  private void sendSafeAreas () {
    Rect insets = stableSystemInsets();
    float density = context().getResources().getDisplayMetrics().density;
    // Normal sheets consume system insets. Fullscreen web content receives the real safe area.
    emit("safe_area_changed", object("top", fullscreen ? insets.top / density : 0,
      "bottom", fullscreen && buttons.getVisibility() != View.VISIBLE && sheetSystemInsets().bottom <= insets.bottom ? insets.bottom / density : 0,
      "left", fullscreen ? insets.left / density : 0, "right", fullscreen ? insets.right / density : 0));
    emit("content_safe_area_changed", object("top", fullscreen ? MiniAppFullscreenControls.CONTENT_INSET_DP : 0, "bottom", 0, "left", 0, "right", 0));
  }

  private Rect stableSystemInsets () {
    View view = popup != null ? popup : context().getRootView();
    androidx.core.view.WindowInsetsCompat window = androidx.core.view.ViewCompat.getRootWindowInsets(view);
    if (window != null) {
      androidx.core.graphics.Insets insets = window.getInsetsIgnoringVisibility(androidx.core.view.WindowInsetsCompat.Type.systemBars() | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
      return new Rect(insets.left, insets.top, insets.right, insets.bottom);
    }
    return new Rect(context().getRootView().getSystemInsetsWithoutIme());
  }

  private Rect sheetSystemInsets () {
    Rect insets = new Rect(popup != null && popup.getHeight() > 0 ? popup.getSystemInsets() : context().getRootView().getSystemInsets());
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) insets.bottom = Math.max(insets.bottom, legacyKeyboardInset);
    return insets;
  }

  void reply (MiniAppDocument.Request origin, String event, JSONObject data) {
    if (!current(origin)) return;
    if (minimized) deferredReplies.add(() -> { if (current(origin)) emit(event, data); });
    else emit(event, data);
  }

  private void emit (String event, JSONObject data) {
    if (isDestroyed() || minimized || webView == null || document == null || !tdlib.isCurrent() || !document.canSend(webView.getUrl())) return;
    String script = "window.Telegram&&Telegram.WebView&&Telegram.WebView.receiveEvent(" + JSONObject.quote(event) + "," + data + ");";
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) webView.evaluateJavascript(script, null);
  }
}
