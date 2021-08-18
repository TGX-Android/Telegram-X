package org.thunderdog.challegram.widget;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ScrollView;

import androidx.annotation.Nullable;
import androidx.collection.SparseArrayCompat;
import androidx.viewpager.widget.PagerAdapter;

import org.json.JSONObject;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.data.PageBlock;
import org.thunderdog.challegram.data.PageBlockMedia;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.WebViewHolder;

import java.util.ArrayList;

import me.vkryl.android.ViewUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;

/**
 * Date: 27/02/2017
 * Author: default
 */

public class PageBlockWrapView extends FrameLayoutFix implements ViewPager.OnPageChangeListener, Runnable, Destroyable, DrawableProvider, WebViewHolder {
  public static final int MODE_EMBEDDED = 1; // WebView
  // public static final int MODE_VIDEO = 2; // FrameLayout + simpleView
  public static final int MODE_SLIDESHOW = 3; // Pager
  public static final int MODE_TABLE = 4;

  private int mode;
  private PageBlockWrapAdapter adapter;
  private @Nullable WebView webView;
  private @Nullable PageBlockView tableView;

  private ImageReceiver receiver;
  private DoubleImageReceiver preview;
  private final ComplexReceiver iconReceiver;

  public PageBlockWrapView (Context context) {
    super(context);
    this.iconReceiver = new ComplexReceiver(this);
  }

  private int webViewHeight;

  public int getExactWebViewHeight () {
    return webViewHeight != 0 ? Screen.dp(webViewHeight) : 0;
  }

  @SuppressWarnings("unused")
  private static class WebViewProxy {
    private final PageBlockWrapView context;

    public WebViewProxy (PageBlockWrapView context) {
      this.context = context;
    }

    @JavascriptInterface
    public final void postEvent (final String eventName, final String eventData) {
      UI.post(() -> {
        if ("resize_frame".equals(eventName)) {
          try {
            JSONObject object = new JSONObject(eventData);
            int exactWebViewHeight = StringUtils.parseInt(object.getString("height"));
            if (context.webViewHeight != exactWebViewHeight) {
              context.webViewHeight = exactWebViewHeight;
              if (context.block != null) {
                context.block.invalidateHeight(context);
              }
            }
            // requestLayout();
          } catch (Throwable ignore) { }
        }
      });
    }
  }

  private boolean wasUserInteraction;

  private void initWebView () {
    if (webView == null) {
      // FIXME android.webkit.WebViewFactory$MissingWebViewPackageException
      webView = new WebView(getContext()) {
        @Override
        public boolean onTouchEvent(MotionEvent event) {
          wasUserInteraction = true;
          return super.onTouchEvent(event);
        }
      };
      ViewSupport.setThemedBackground(webView, R.id.theme_color_placeholder);
      webView.getSettings().setJavaScriptEnabled(true);
      webView.getSettings().setAllowContentAccess(true);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
        webView.addJavascriptInterface(new WebViewProxy(this), "TelegramWebviewProxy");
        // webView.getSettings().setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);
      }
      webView.getSettings().setDomStorageEnabled(true);
      webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
      // webView.getSettings().setUserAgentString("Mozilla/5.0 (Linux; Android 4.0.4; Galaxy Nexus Build/IMM76B) AppleWebKit/535.19 (KHTML, like Gecko) Chrome/18.0.1025.133 Mobile Safari/535.19");
      // webView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
      }
      webView.setWebViewClient(new WebViewClient() {
        @Override
        public boolean shouldOverrideUrlLoading (WebView view, String url) {
          if (wasUserInteraction) {
            UI.openUrl(url);
            return true;
          }
          return false;
        }
      });
      /*webView.setWebChromeClient(new WebChromeClient() {
        @Override
        public void onShowCustomView (View view, CustomViewCallback callback) {
          super.onShowCustomView(view, callback);
        }

        @Override
        public void onHideCustomView () {
          super.onHideCustomView();
        }
      });*/
      addView(webView);
    }
  }

  public void initWithMode (int mode, @Nullable ViewController themeProvider) {
    this.mode = mode;
    switch (mode) {
      case MODE_EMBEDDED: {
        preview = new DoubleImageReceiver(this, 0);
        receiver = new ImageReceiver(this, 0);
        initWebView();
        setWillNotDraw(false);
        break;
      }
      case MODE_SLIDESHOW: {
        adapter = new PageBlockWrapAdapter(getContext(), themeProvider);

        ViewPager pager;
        pager = new ViewPager(getContext());
        pager.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pager.setOverScrollMode(View.OVER_SCROLL_NEVER);
        pager.addOnPageChangeListener(this);
        pager.setAdapter(adapter);

        addView(pager);

        ViewPagerPositionView position;
        position = new ViewPagerPositionView(getContext());
        position.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(36f), Gravity.BOTTOM));
        position.reset(adapter.getCount(), 0f);
        addView(position);

        break;
      }
      case MODE_TABLE: {
        ScrollView scrollView = new ScrollView(getContext());
        scrollView.setHorizontalScrollBarEnabled(true);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tableView = new PageBlockView(getContext(), themeProvider.tdlib());
        tableView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scrollView.addView(tableView);

        addView(scrollView);
        break;
      }
    }
  }

  public int getMode () {
    return mode;
  }

  private PageBlock block;

  public void setBlock (PageBlock block) {
    if (this.block == block) {
      return;
    }

    if (this.block != null) {
      this.block.detachFromView(this);
      this.block = null;
    }

    this.block = block;

    final int viewWidth = getMeasuredWidth();
    final int desiredHeight;
    if (block != null) {
      block.autoDownloadContent();
      block.attachToView(this);
      initialize(block);
      desiredHeight = viewWidth != 0 ? block.getHeight(this, viewWidth) : 0;
    } else {
      desiredHeight = 0;
    }

    if (viewWidth != 0 && getMeasuredHeight() != desiredHeight) {
      requestLayout();
    }
  }

  private boolean inSlideShow;

  private void setInSlideShow (boolean inSlideShow) {
    if (this.inSlideShow != inSlideShow) {
      this.inSlideShow = inSlideShow;

      if (inSlideShow) {
        // postDelayed(this, 5000l);
      } else {
        removeCallbacks(this);
      }
    }
  }

  private void initialize (PageBlock block) {
    switch (mode) {
      case MODE_SLIDESHOW: {
        PageBlockMedia slideshowBlock = (PageBlockMedia) block;
        ViewPager pager = (ViewPager) getChildAt(0);
        pager.setAdapter(null);
        adapter.setBlock(slideshowBlock);
        pager.setAdapter(adapter);
        ViewPagerPositionView position = (ViewPagerPositionView) getChildAt(1);
        int desiredItem = slideshowBlock.getSelectedPage();
        if (pager.getCurrentItem() != desiredItem) {
          pager.setCurrentItem(desiredItem, false);
        }
        position.reset(adapter.getCount(), desiredItem);
        ViewUtils.fixViewPager(pager);
        setWillNotDraw(!block.isPost());
        break;
      }
      case MODE_EMBEDDED: {
        PageBlockMedia embeddedBlock = (PageBlockMedia) block;

        embeddedBlock.requestPreview(preview);
        embeddedBlock.requestImage(receiver);

        initWebView();
        if (block.allowScrolling()) {
          webView.setVerticalScrollBarEnabled(true);
          webView.setHorizontalScrollBarEnabled(true);
        } else {
          webView.setVerticalScrollBarEnabled(false);
          webView.setHorizontalScrollBarEnabled(false);
        }
        webViewHeight = 0;
        try {
          webView.loadUrl("about:blank");
        } catch (Throwable t) {
          Log.e(t);
        }

        embeddedBlock.processWebView(webView);
        break;
      }
      case MODE_TABLE: {
        tableView.setBlock(block);
        break;
      }
    }
  }

  @Override
  public void run () {
    ViewPager pager = (ViewPager) getChildAt(0);
    final int nowItem = pager.getCurrentItem();
    final int nextItem = nowItem + 1 < adapter.getCount() ? nowItem + 1 : 0;
    if (nowItem != nextItem) {
      // TODO fakeDrag animation
      pager.setCurrentItem(nextItem, true);
    }
  }

  private boolean isAttached;

  private void checkSlideShow () {
    if (mode == MODE_SLIDESHOW) {
      setInSlideShow(isAttached && !isScrolling);
    }
  }

  public void attach () {
    iconReceiver.attach();
    switch (mode) {
      case MODE_SLIDESHOW: {
        adapter.attach();
        isAttached = true;
        checkSlideShow();
        break;
      }
      case MODE_EMBEDDED: {
        preview.attach();
        receiver.attach();
        if (webView != null) {
          webView.onResume();
        }
        break;
      }
      case MODE_TABLE: {
        tableView.attach();
        break;
      }
    }
  }

  public void detach () {
    iconReceiver.detach();
    switch (mode) {
      case MODE_SLIDESHOW: {
        adapter.detach();
        isAttached = false;
        checkSlideShow();
        break;
      }
      case MODE_EMBEDDED: {
        preview.detach();
        receiver.detach();
        if (webView != null) {
          webView.onPause();
        }
        break;
      }
      case MODE_TABLE: {
        tableView.detach();
        break;
      }
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (block != null) {
      block.draw(this, c, preview, receiver, iconReceiver);
    }
  }

  @Override
  public void destroyWebView () {
    if (webView != null) {
      webView.destroy();
      removeView(webView);
      webView = null;
    }
  }

  @Override
  public void performDestroy () {
    setBlock(null);
    iconReceiver.performDestroy();
    switch (mode) {
      case MODE_SLIDESHOW: {
        checkSlideShow();
        break;
      }
      case MODE_EMBEDDED: {
        receiver.destroy();
        preview.destroy();
        break;
      }
      case MODE_TABLE: {
        tableView.performDestroy();
        break;
      }
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    final int viewWidth = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
    if (block == null) {
      super.onMeasure(viewWidth, heightMeasureSpec);
      return;
    }

    final int viewHeight = block.getHeight(this, viewWidth);

    View view = getChildAt(0);
    if (view != null) {
      LayoutParams params = (LayoutParams) view.getLayoutParams();
      block.applyLayoutMargins(this, params, viewWidth, viewHeight);
      switch (mode) {
        case MODE_SLIDESHOW: {
          view = getChildAt(1);
          if (view != null) {
            block.applyLayoutMargins(this, (LayoutParams) view.getLayoutParams(), viewWidth, viewHeight);
          }
          break;
        }
      }
    }

    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(viewHeight, MeasureSpec.EXACTLY));
  }

  // ViewPager only

  private static class PageBlockWrapAdapter extends PagerAdapter {
    private final Context context;
    private final ArrayList<SimpleMediaWrapperView> recycledPool;
    private final ArrayList<SimpleMediaWrapperView> usedPool;

    private final @Nullable ViewController themeProvider;

    public PageBlockWrapAdapter (Context context, @Nullable ViewController themeProvider) {
      this.context = context;
      this.recycledPool = new ArrayList<>(4);
      this.usedPool = new ArrayList<>(4);
      this.themeProvider = themeProvider;
    }

    private PageBlockMedia pageBlock;

    public void setBlock (PageBlockMedia pageBlock) {
      if (this.pageBlock != pageBlock) {
        this.pageBlock = pageBlock;
        notifyDataSetChanged();
      }
    }

    @Override
    public int getCount () {
      return pageBlock != null ? pageBlock.getCount() : 0;
    }

    @Override
    public boolean isViewFromObject (View view, Object object) {
      return object == view;
    }

    @Override
    public Object instantiateItem (ViewGroup container, int position) {
      SimpleMediaWrapperView wrapperView;
      if (recycledPool.isEmpty()) {
        wrapperView = new SimpleMediaWrapperView(context);
        wrapperView.setBackgroundColorId(R.id.theme_color_placeholder);
        wrapperView.setFitsBounds();
        wrapperView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      } else {
        wrapperView = recycledPool.remove(recycledPool.size() - 1);
      }
      wrapperView.setWrapper(pageBlock != null ? pageBlock.getWrapper(position) : null);
      usedPool.add(wrapperView);
      container.addView(wrapperView);
      return wrapperView;
    }

    @Override
    public void destroyItem (ViewGroup container, int position, Object object) {
      SimpleMediaWrapperView wrapperView = (SimpleMediaWrapperView) object;
      container.removeView(wrapperView);
      usedPool.remove(wrapperView);
      wrapperView.clear();
      recycledPool.add(wrapperView);
    }

    public void destroy () {
      recycledPool.clear();
    }

    public void attach () {
      for (SimpleMediaWrapperView view : recycledPool) {
        view.attach();
      }
      for (SimpleMediaWrapperView view : usedPool) {
        view.attach();
      }
    }

    public void detach () {
      for (SimpleMediaWrapperView view : recycledPool) {
        view.detach();
      }
      for (SimpleMediaWrapperView view : usedPool) {
        view.detach();
      }
    }
  }

  private float viewPagerPosition;

  public float getViewPagerPosition () {
    return viewPagerPosition;
  }

  @Override
  public void onPageScrolled (int position, float positionOffset, int positionOffsetPixels) {
    this.viewPagerPosition = position + positionOffset;
    ViewPagerPositionView positionView = (ViewPagerPositionView) getChildAt(1);
    if (positionView != null) {
      positionView.setPositionFactor(position + positionOffset);
    }
  }

  @Override
  public void onPageSelected (int position) {
    if (block != null) {
      ((PageBlockMedia) block).setSelectedPage(position);
    }
  }

  private boolean isScrolling;

  @Override
  public void onPageScrollStateChanged (int state) {
    this.isScrolling = state != ViewPager.SCROLL_STATE_IDLE;
    checkSlideShow();
  }

  // Sparse drawable function
  private SparseArrayCompat<Drawable> sparseDrawables;
  @Override
  public final SparseArrayCompat<Drawable> getSparseDrawableHolder () { return (sparseDrawables != null ? sparseDrawables : (sparseDrawables = new SparseArrayCompat<>())); }
  @Override
  public final Resources getSparseDrawableResources () { return getResources(); }
}
