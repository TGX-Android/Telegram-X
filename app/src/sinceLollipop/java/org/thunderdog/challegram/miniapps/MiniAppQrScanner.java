/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.app.Dialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.common.HybridBinarizer;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Screen;
import static org.thunderdog.challegram.miniapps.MiniAppTheme.object;

/** QR-only camera sheet. Decoded text remains data; it is never opened as a URL by the host. */
@SuppressWarnings("deprecation")
final class MiniAppQrScanner implements TextureView.SurfaceTextureListener {
  private final MiniAppController host;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final java.util.concurrent.ExecutorService decoder = java.util.concurrent.Executors.newSingleThreadExecutor();
  private MiniAppDocument.Request pending;
  private Dialog dialog;
  private Camera camera;
  private TextureView preview;
  private String lastText;
  private long lastScan;

  MiniAppQrScanner (MiniAppController host) { this.host = host; }
  void open (MiniAppDocument.Request origin, String text) {
    if (pending != null) return;
    pending = origin;
    if (!host.context().permissions().requestAccessCameraPermission(allowed -> {
      if (pending != origin) return;
      if (allowed && host.current(origin) && host.isShowing()) show(text); else close();
    })) show(text);
  }
  private void show (String text) {
    if (pending == null || !host.current(pending)) { close(); return; }
    dialog = new Dialog(host.context());
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    FrameLayout root = new FrameLayout(host.context()); root.setBackgroundColor(Color.BLACK);
    preview = new TextureView(host.context()); preview.setSurfaceTextureListener(this);
    root.addView(preview, new FrameLayout.LayoutParams(-1, -1));
    FrameLayout overlay = new FrameLayout(host.context()) {
      final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
      @Override protected void dispatchDraw (Canvas canvas) {
        float size = Math.min(getWidth(), getHeight()) / 1.5f, left = (getWidth() - size) / 2, top = (getHeight() - size) / 2;
        paint.setColor(0x99000000); paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(0, 0, getWidth(), top, paint); canvas.drawRect(0, top + size, getWidth(), getHeight(), paint);
        canvas.drawRect(0, top, left, top + size, paint); canvas.drawRect(left + size, top, getWidth(), top + size, paint);
        paint.setColor(Color.WHITE); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Screen.dp(3));
        canvas.drawRoundRect(new RectF(left, top, left + size, top + size), Screen.dp(12), Screen.dp(12), paint);
        super.dispatchDraw(canvas);
      }
    };
    root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
    TextView title = new TextView(host.context()); title.setText(Lang.getString(R.string.MiniAppScanQr)); title.setTextSize(24); title.setTextColor(Color.WHITE); title.setGravity(Gravity.CENTER);
    TextView subtitle = new TextView(host.context()); subtitle.setText(text.substring(0, Math.min(256, text.length()))); subtitle.setTextSize(16); subtitle.setTextColor(0xffcccccc); subtitle.setGravity(Gravity.CENTER);
    overlay.addView(title, new FrameLayout.LayoutParams(-1, -2)); overlay.addView(subtitle, new FrameLayout.LayoutParams(-1, -2));
    ImageView back = new ImageView(host.context()); back.setImageResource(R.drawable.baseline_close_24); back.setColorFilter(Color.WHITE); back.setContentDescription(Lang.getString(R.string.MiniAppPopupClose));
    overlay.addView(back, new FrameLayout.LayoutParams(Screen.dp(56), Screen.dp(56), Gravity.TOP | Gravity.START)); back.setOnClickListener(v -> close());
    ImageView flash = new ImageView(host.context()); flash.setImageResource(R.drawable.baseline_flash_on_24); flash.setColorFilter(Color.WHITE); flash.setContentDescription(Lang.getString(R.string.MiniAppFlash));
    overlay.addView(flash, new FrameLayout.LayoutParams(Screen.dp(60), Screen.dp(60), Gravity.TOP | Gravity.CENTER_HORIZONTAL));
    flash.setOnClickListener(v -> {
      if (camera == null) return;
      try {
        Camera.Parameters params = camera.getParameters();
        if (params.getSupportedFlashModes() != null && params.getSupportedFlashModes().contains(Camera.Parameters.FLASH_MODE_TORCH)) {
          params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH.equals(params.getFlashMode()) ? Camera.Parameters.FLASH_MODE_OFF : Camera.Parameters.FLASH_MODE_TORCH); camera.setParameters(params);
        }
      } catch (RuntimeException ignored) { }
    });
    overlay.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
      float size = Math.min(v.getWidth(), v.getHeight()) / 1.5f, top = (v.getHeight() - size) / 2;
      title.setY(Math.max(Screen.dp(60), top - title.getHeight() - Screen.dp(64)));
      subtitle.setY(title.getY() + title.getHeight() + Screen.dp(8));
      title.setPadding(Screen.dp(36), 0, Screen.dp(36), 0); subtitle.setPadding(Screen.dp(36), 0, Screen.dp(36), 0);
      flash.setY(Math.min(v.getHeight() - Screen.dp(76), top + size + Screen.dp(80)));
    });
    dialog.setContentView(root);
    dialog.setOnDismissListener(d -> close());
    dialog.show();
    Window window = dialog.getWindow();
    if (window != null) { window.setLayout(-1, -1); window.setGravity(Gravity.BOTTOM); window.setBackgroundDrawableResource(android.R.color.transparent); window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); }
  }
  @Override public void onSurfaceTextureAvailable (SurfaceTexture surface, int width, int height) {
    if (pending == null) return;
    try {
      camera = Camera.open();
      Camera.Parameters params = camera.getParameters();
      Camera.Size chosen = null;
      for (Camera.Size size : params.getSupportedPreviewSizes()) if (size.width <= 1280 && (chosen == null || size.width > chosen.width)) chosen = size;
      if (chosen != null) params.setPreviewSize(chosen.width, chosen.height);
      if (params.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
      camera.setParameters(params);
      Camera.CameraInfo info = new Camera.CameraInfo(); Camera.getCameraInfo(0, info);
      int rotation = host.context().getWindowManager().getDefaultDisplay().getRotation() * 90;
      camera.setDisplayOrientation((info.orientation - rotation + 360) % 360);
      camera.setPreviewTexture(surface); camera.startPreview(); nextFrame();
    } catch (Exception error) { close(); }
  }
  private void nextFrame () {
    if (camera == null || pending == null) return;
    try { camera.setOneShotPreviewCallback((bytes, source) -> {
      if (bytes == null || source != camera || pending == null) return;
      Camera.Size size = source.getParameters().getPreviewSize();
      decoder.execute(() -> {
        String value = null;
        try { value = decode(bytes, size.width, size.height); }
        catch (Exception ignored) { }
        final String text = value;
        main.post(() -> {
          if (camera != source || pending == null) return;
          if (text != null && text.length() <= 8192 && (!text.equals(lastText) || SystemClock.uptimeMillis() - lastScan > 2000)) {
            lastText = text; lastScan = SystemClock.uptimeMillis(); host.recordGesture(); host.reply(pending, "qr_text_received", object("data", text));
          }
          main.postDelayed(this::nextFrame, 120);
        });
      });
    }); } catch (RuntimeException error) { close(); }
  }
  void close () {
    MiniAppDocument.Request origin = pending; pending = null;
    main.removeCallbacksAndMessages(null);
    if (camera != null) { try { camera.setPreviewCallback(null); camera.stopPreview(); camera.release(); } catch (RuntimeException ignored) { } camera = null; }
    if (dialog != null) { Dialog current = dialog; dialog = null; current.dismiss(); }
    if (origin != null) host.reply(origin, "scan_qr_popup_closed", object());
  }
  static String decode (byte[] bytes, int width, int height) throws Exception {
    return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new PlanarYUVLuminanceSource(bytes, width, height, 0, 0, width, height, false)))).getText();
  }
  void pause () { if (camera != null) close(); }
  void destroy () { close(); decoder.shutdownNow(); }
  @Override public void onSurfaceTextureSizeChanged (SurfaceTexture surface, int width, int height) { }
  @Override public boolean onSurfaceTextureDestroyed (SurfaceTexture surface) { close(); return true; }
  @Override public void onSurfaceTextureUpdated (SurfaceTexture surface) { }
}
