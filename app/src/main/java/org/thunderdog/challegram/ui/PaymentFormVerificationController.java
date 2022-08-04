package org.thunderdog.challegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Size;

public class PaymentFormVerificationController extends WebkitController<PaymentFormVerificationController.Args> {
  public static class Args {
    public final String url;
    public final String paymentProcessor;
    public final Runnable onFinish;

    public Args (String url, String paymentProcessor, Runnable onFinish) {
      this.url = url;
      this.paymentProcessor = paymentProcessor;
      this.onFinish = onFinish;
    }
  }

  public PaymentFormVerificationController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @SuppressLint("AddJavascriptInterface")
  @Override
  protected void onCreateWebView (DoubleHeaderView headerCell, WebView webView) {
    if (getArguments() != null) {
      headerCell.setTitle(R.string.PaymentFormMethodVerification);
      headerCell.setSubtitle(getArguments().paymentProcessor);
    }

    webView.setWebChromeClient(new WebChromeClient() {
      @Override
      public void onCloseWindow (WebView window) {
        super.onCloseWindow(window);
        navigateBack();
        getArgumentsStrict().onFinish.run();
      }
    });

    if (getArguments() != null) {
      webView.loadUrl(getArguments().url);
    }
  }

  @Override
  public boolean canSlideBackFrom (NavigationController navigationController, float x, float y) {
    return y < Size.getHeaderPortraitSize() || x <= Screen.dp(15f);
  }
}
