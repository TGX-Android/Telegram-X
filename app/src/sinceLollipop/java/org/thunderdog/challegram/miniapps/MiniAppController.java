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
import android.widget.ProgressBar;
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
  private final MiniAppLaunchRequest request;
  private final MiniAppSession session;
  private final MiniAppPlatform platform = new MiniAppPlatform();
  private Runnable onOpened;
  private MiniAppDocument document;
  private WebView webView;
  private FrameLayout root, browserArea;
  private LinearLayout column, header;
  private MiniAppButtons buttons;
  private TextView title, errorView;
  private Bitmap preview;
  private ValueAnimator sizeAnimator;
  private ProgressBar progress;
  private ImageView back, collapse, more;
  private PopupLayout popup;
  private AlertDialog activeDialog;
  private ValueCallback<Uri[]> fileCallback;
  private MiniAppDocument.Request fileRequest;
  private int fileRequestCode;
  private static int nextFileRequestCode = 0x5a00;
  private MiniAppMessageSender phoneSender;
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
  private long lastInteraction, lastPopupClosed;
  private int sequentialPopups;
  private float dragStartY;
  private float webTouchX, webTouchY;

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
    root = new FrameLayout(context);
    column = new LinearLayout(context);
    column.setOrientation(LinearLayout.VERTICAL);
    root.addView(column, new FrameLayout.LayoutParams(-1, -1));
    browserArea = new FrameLayout(context);
    column.addView(browserArea, new LinearLayout.LayoutParams(-1, 0, 1));
    progress = new ProgressBar(context);
    browserArea.addView(progress, new FrameLayout.LayoutParams(Screen.dp(40), Screen.dp(40), Gravity.CENTER));
    errorView = new TextView(context);
    errorView.setText(Lang.getString(R.string.MiniAppLoadError));
    errorView.setTextColor(Theme.textAccentColor());
    errorView.setGravity(Gravity.CENTER);
    errorView.setPadding(Screen.dp(32), Screen.dp(24), Screen.dp(32), Screen.dp(24));
    errorView.setVisibility(View.GONE);
    browserArea.addView(errorView, new FrameLayout.LayoutParams(-1, -1));
    buttons = new MiniAppButtons(context, () -> pressed("main_button_pressed"), () -> pressed("secondary_button_pressed"));
    column.addView(buttons, new LinearLayout.LayoutParams(-1, -2));
    header = new LinearLayout(context);
    header.setGravity(Gravity.CENTER_VERTICAL);
    back = icon(R.drawable.baseline_close_24, R.string.MiniAppClose, v -> headerBack());
    header.addView(back);
    title = new TextView(context);
    title.setText(tdlib.cache().userName(request.botUserId));
    title.setTextSize(20);
    title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    title.setSingleLine();
    title.setEllipsize(TextUtils.TruncateAt.END);
    title.setPadding(Screen.dp(16), 0, Screen.dp(8), 0);
    header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    collapse = icon(R.drawable.baseline_direction_arrow_down_24, R.string.MiniAppMinimize, v -> minimize());
    header.addView(collapse);
    more = icon(R.drawable.baseline_more_vert_24, R.string.MiniAppMore, v -> showMenu());
    header.addView(more);
    root.addView(header, new FrameLayout.LayoutParams(-1, Screen.dp(56), Gravity.TOP));
    header.setOnTouchListener((v, event) -> {
      if (event.getAction() == MotionEvent.ACTION_DOWN) { dragStartY = event.getRawY(); return true; }
      if (event.getAction() == MotionEvent.ACTION_UP) {
        float distance = event.getRawY() - dragStartY;
        if (distance < -Screen.dp(48)) { expanded = true; resize(true); }
        else if (swipeEnabled && distance > Screen.dp(80)) minimize();
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
          if (document != null) document.navigate();
          cancelDocumentRequests();
          deferredReplies.clear();
          if (phoneSender != null) { phoneSender.cancel(); phoneSender = null; }
          dataSending = false;
          backVisible = settingsVisible = needsCloseConfirmation = false;
          headerColor = backgroundColor = bottomColor = null;
          headerColorKey = null;
          swipeEnabled = pageAllowsVerticalSwipe = true;
          applyTheme();
          back.setImageResource(R.drawable.baseline_close_24);
          back.setContentDescription(Lang.getString(R.string.MiniAppClose));
          buttons.reset();
          progress.setVisibility(View.VISIBLE);
          errorView.setVisibility(View.GONE);
          platform.onPageStarted(view);
        }

        @Override
        public void onPageFinished (WebView view, String url) {
          if (isDestroyed()) return;
          platform.onPageStarted(view);
          sendTheme();
          sendViewport();
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
        @Override
        public boolean onShowFileChooser (WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
          cancelFileChooser();
          if (document == null || !document.canSend(view.getUrl()) || minimized) { callback.onReceiveValue(null); return true; }
          fileRequest = document.receive(view.getUrl(), view.getUrl(), 0);
          fileCallback = callback;
          fileRequestCode = ++nextFileRequestCode;
          try {
            Intent intent = params.createIntent();
            context().putActivityResultHandler(fileRequestCode, (code, result, data) -> {
              ValueCallback<Uri[]> target = fileCallback;
              MiniAppDocument.Request origin = fileRequest;
              fileCallback = null;
              fileRequest = null;
              if (target != null) target.onReceiveValue(current(origin) ? FileChooserParams.parseResult(result, data) : null);
            });
            context().startActivityForResult(intent, fileRequestCode);
          } catch (RuntimeException e) { cancelFileChooser(); }
          return true;
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
      }
    };
    popup.init(false);
    popup.setNeedRootInsets();
    popup.setOverlayStatusBar(true);
    popup.setPadding(0, 0, 0, fullscreen ? 0 : tdlib.miniApps().tabsHeight(context()));
    popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    popup.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
      if (b - t != ob - ot || r - l != or - ol) resize();
    });
    popup.setBackListener((fromTop, commit) -> {
      if (commit) { if (backVisible) pressed("back_button_pressed"); else minimize(); }
      return true;
    });
    popup.setTouchDownInterceptor((window, event) -> { minimize(); return true; });
    popup.setDismissListener(window -> {
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
    if (orientationRequested) setOrientationLocked(true);
    sendTheme();
    emit("activated", object());
    for (Runnable reply : new ArrayList<>(deferredReplies)) reply.run();
    deferredReplies.clear();
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
    Rect insets = popup != null && popup.getHeight() > 0 ? popup.getSystemInsets() : context().getRootView().getSystemInsets();
    int top = Math.max(insets.top, Screen.getStatusBarHeight());
    int bottom = fullscreen ? Math.max(0, insets.bottom - context().getRootView().getSystemInsetsWithoutIme().bottom) : insets.bottom;
    int height = MiniAppViewport.sheetHeight(available, top, bottom, expanded, fullscreen);
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, height, Gravity.BOTTOM);
    params.bottomMargin = bottom;
    if (!fullscreen) { params.leftMargin = insets.left; params.rightMargin = insets.right; }
    FrameLayout.LayoutParams headerParams = (FrameLayout.LayoutParams) header.getLayoutParams();
    headerParams.topMargin = fullscreen ? top : 0;
    header.setLayoutParams(headerParams);
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
    if (popup != null) {
      popup.setNeedFullScreen(fullscreen);
      popup.setIgnoreAllInsets(fullscreen);
    }
    root.post(this::sendViewport);
  }

  private void showLoadError () {
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
    if (browserArea.getWidth() > 0 && browserArea.getHeight() > 0) {
      if (preview != null) preview.recycle();
      float scale = Math.min(1f, 360f / browserArea.getWidth());
      preview = Bitmap.createBitmap(Math.max(1, Math.round(browserArea.getWidth() * scale)), Math.max(1, Math.round(browserArea.getHeight() * scale)), Bitmap.Config.RGB_565);
      Canvas canvas = new Canvas(preview);
      canvas.scale(scale, scale);
      browserArea.draw(canvas);
    }
    minimized = true;
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
    session.close();
    context().removeFullScreenView(this, true);
    if (document != null) document.close();
    cancelDocumentRequests();
    deferredReplies.clear();
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
    if (webView != null) webView.onPause();
  }

  @Override
  public void onActivityResume () {
    super.onActivityResume();
    if (webView != null && !minimized) webView.onResume();
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

  private void cancelFileChooser () {
    if (fileCallback != null) {
      context().putActivityResultHandler(fileRequestCode, null);
      fileCallback.onReceiveValue(null);
      fileCallback = null;
      fileRequest = null;
    }
  }

  private void cancelDocumentRequests () {
    cancelFileChooser();
    if (activeDialog != null) { activeDialog.dismiss(); activeDialog = null; }
  }

  private boolean current (MiniAppDocument.Request origin) {
    return !isDestroyed() && tdlib.isCurrent() && webView != null && document != null && document.isCurrent(origin, webView.getUrl());
  }

  @Override
  public void onMessage (String message, String sourceOrigin) {
    if (isDestroyed() || minimized || !tdlib.isCurrent() || document == null || webView == null) return;
    MiniAppDocument.Request origin = document.receive(sourceOrigin, webView.getUrl(), message.length());
    if (origin == null) return;
    try {
      JSONObject envelope = new JSONObject(message);
      String event = envelope.getString("eventType");
      if (event.isEmpty() || event.length() > 128) return;
      Object data = envelope.opt("eventData");
      if (data instanceof String && !((String) data).isEmpty()) data = new JSONTokener((String) data).nextValue();
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
      case "web_app_ready": progress.setVisibility(View.GONE); break;
      case "web_app_request_theme": sendTheme(); break;
      case "web_app_request_viewport": sendViewport(); break;
      case "web_app_expand": if (!expanded) { expanded = true; resize(true); } break;
      case "web_app_close": close(); break;
      case "web_app_setup_closing_behavior": needsCloseConfirmation = data.optBoolean("need_confirmation"); break;
      case "web_app_setup_swipe_behavior": swipeEnabled = data.optBoolean("allow_vertical_swipe", true); break;
      case "web_app_hide_keyboard": Keyboard.hide(webView); break;
      case "web_app_setup_back_button":
        backVisible = data.optBoolean("is_visible");
        back.setImageResource(backVisible ? R.drawable.baseline_arrow_back_24 : R.drawable.baseline_close_24);
        back.setContentDescription(Lang.getString(backVisible ? R.string.MiniAppBack : R.string.MiniAppClose));
        break;
      case "web_app_setup_settings_button": settingsVisible = data.optBoolean("is_visible"); break;
      case "web_app_setup_main_button": buttons.configure(false, data); break;
      case "web_app_setup_secondary_button": buttons.configure(true, data); break;
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
        webView.performHapticFeedback("selection_change".equals(data.optString("type")) ? HapticFeedbackConstants.CLOCK_TICK : HapticFeedbackConstants.LONG_PRESS);
        break;
      case "web_app_request_fullscreen": fullscreen = expanded = true; resize(); emit("fullscreen_changed", object("is_fullscreen", true)); sendSafeAreas(); break;
      case "web_app_exit_fullscreen": fullscreen = false; resize(); emit("fullscreen_changed", object("is_fullscreen", false)); sendSafeAreas(); break;
      case "web_app_request_safe_area":
      case "web_app_request_content_safe_area": sendSafeAreas(); break;
      case "web_app_toggle_orientation_lock": setOrientationLocked(data.optBoolean("locked")); break;
      case "web_app_open_link":
        if (recentGesture() && MiniAppOrigin.of(data.optString("url")) != null) {
          tdlib.ui().openUrl(this, data.getString("url"), new TdlibUi.UrlOpenParameters().disableInstantView());
          lastInteraction = 0;
        }
        break;
      case "web_app_open_tg_link":
        String path = data.optString("path_full");
        if (path.startsWith("/") && !path.startsWith("//")) { minimize(); tdlib.ui().openUrl(this, "https://t.me" + path, null); }
        break;
      case "web_app_data_send": sendData(origin, data.getString("data")); break;
      case "web_app_invoke_custom_method": customMethod(origin, data); break;
      case "web_app_request_write_access": requestWriteAccess(origin); break;
      case "web_app_request_phone": requestPhone(origin); break;
      default:
        // Further native capabilities are added as separate services; never invent successful replies.
        break;
    }
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
    tdlib.send(new TdApi.SendWebAppCustomRequest(request.botUserId, method, parameters.toString()), (result, error) -> tdlib.ui().post(() -> {
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
    tdlib.send(new TdApi.CanBotSendMessages(request.botUserId), (ok, error) -> tdlib.ui().post(() -> {
      if (!current(origin)) return;
      if (error == null) { reply(origin, "write_access_requested", object("status", "allowed")); return; }
      if (error.code != 404) { UI.showError(error); return; }
      confirmAction(origin, Lang.getString(R.string.MiniAppWriteAccess, tdlib.cache().userName(request.botUserId)), "write_access_requested", () ->
        tdlib.send(new TdApi.AllowBotToSendMessages(request.botUserId), (result, allowError) -> tdlib.ui().post(() -> {
          if (!current(origin)) return;
          if (allowError != null) UI.showError(allowError);
          reply(origin, "write_access_requested", object("status", allowError == null ? "allowed" : "cancelled"));
        })));
    }));
  }

  private void requestPhone (MiniAppDocument.Request origin) {
    if (phoneSender != null) return;
    confirmAction(origin, Lang.getString(R.string.MiniAppSharePhone, tdlib.cache().userName(request.botUserId)), "phone_requested", () -> {
      TdApi.User me = tdlib.myUser();
      if (me == null || me.phoneNumber.isEmpty()) { reply(origin, "phone_requested", object("status", "cancelled")); return; }
      tdlib.send(new TdApi.CreatePrivateChat(request.botUserId, false), (chat, error) -> tdlib.ui().post(() -> {
        if (!current(origin)) return;
        if (error != null) { UI.showError(error); reply(origin, "phone_requested", object("status", "cancelled")); return; }
        TdApi.Contact contact = new TdApi.Contact(me.phoneNumber, me.firstName, me.lastName, "", me.id);
        phoneSender = new MiniAppMessageSender(tdlib, new TdApi.SendMessage(chat.id, null, null, Td.newSendOptions(), null, new TdApi.InputMessageContact(contact)), sendError -> {
          phoneSender = null;
          if (!current(origin)) return;
          if (sendError != null) UI.showError(sendError);
          reply(origin, "phone_requested", object("status", sendError == null ? "sent" : "cancelled"));
        });
        phoneSender.send();
      }));
    });
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
    if (activeDialog != null) return;
    long elapsed = SystemClock.uptimeMillis() - lastPopupClosed;
    if (elapsed < 150) sequentialPopups++;
    else if (elapsed > 3000) sequentialPopups = 0;
    if (sequentialPopups >= 3 && elapsed < 3000) return;
    String heading = data.optString("title", ""), message = data.getString("message");
    JSONArray options = data.getJSONArray("buttons");
    if (heading.length() > 64 || message.length() > 256 || options.length() < 1 || options.length() > 3) return;
    AlertDialog.Builder builder = new AlertDialog.Builder(context()).setTitle(heading).setMessage(message);
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

  private boolean recentGesture () { return lastInteraction != 0 && SystemClock.uptimeMillis() - lastInteraction < 10000; }

  private void pressed (String event) {
    lastInteraction = SystemClock.uptimeMillis();
    emit(event, new JSONObject());
  }

  private void showMenu () {
    List<String> labels = new ArrayList<>();
    List<Runnable> actions = new ArrayList<>();
    labels.add(Lang.getString(R.string.OpenBot));
    actions.add(() -> { minimize(); tdlib.ui().openPrivateProfile(this, request.botUserId, null); });
    if (settingsVisible) { labels.add(Lang.getString(R.string.Settings)); actions.add(() -> pressed("settings_button_pressed")); }
    labels.add(Lang.getString(R.string.MiniAppReload));
    actions.add(() -> { if (webView != null && document != null) { document.navigate(); webView.reload(); } });
    labels.add(Lang.getString(R.string.MiniAppTerms));
    actions.add(() -> tdlib.ui().openUrl(this, "https://telegram.org/tos/mini-apps", null));
    labels.add(Lang.getString(R.string.MiniAppClose));
    actions.add(this::requestClose);
    showAlert(new AlertDialog.Builder(context()).setItems(labels.toArray(new String[0]), (dialog, index) -> actions.get(index).run()));
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
    if (webView != null) webView.setBackgroundColor(background);
    buttons.setBackgroundColor(bottomColor != null ? bottomColor : theme.bottomBarBackgroundColor);
    buttons.refreshTheme();
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
      "is_expanded", expanded, "is_state_stable", sizeAnimator == null));
    sendSafeAreas();
  }

  private void sendSafeAreas () {
    Rect insets = context().getRootView().getSystemInsetsWithoutIme();
    float density = context().getResources().getDisplayMetrics().density;
    // Normal sheets consume system insets. Fullscreen web content receives the real safe area.
    emit("safe_area_changed", object("top", fullscreen ? insets.top / density : 0,
      "bottom", fullscreen && buttons.getHeight() == 0 ? insets.bottom / density : 0,
      "left", fullscreen ? insets.left / density : 0, "right", fullscreen ? insets.right / density : 0));
    emit("content_safe_area_changed", object("top", fullscreen ? 56 : 0, "bottom", 0, "left", 0, "right", 0));
  }

  private void reply (MiniAppDocument.Request origin, String event, JSONObject data) {
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
