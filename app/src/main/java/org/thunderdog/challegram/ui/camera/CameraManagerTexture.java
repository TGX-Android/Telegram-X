package org.thunderdog.challegram.ui.camera;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.Gravity;
import android.view.TextureView;
import android.view.ViewGroup;

import me.vkryl.android.widget.FrameLayoutFix;

public abstract class CameraManagerTexture extends CameraManager<CameraTextureView> implements TextureView.SurfaceTextureListener {
  private SurfaceTexture surfaceTexture;
  private int textureWidth, textureHeight;

  public CameraManagerTexture (Context context, CameraDelegate delegate) {
    super(context, delegate);
  }

  public abstract void setPreviewSize (int viewWidth, int viewHeight);

  protected abstract void onTextureAvailable (SurfaceTexture surfaceTexture, int width, int height);
  protected abstract void onTextureSizeChanged (SurfaceTexture surfaceTexture, int width, int height);
  protected abstract void onTextureDestroyed (SurfaceTexture surfaceTexture);

  @Override
  protected final CameraTextureView onCreateView () {
    CameraTextureView cameraView = new CameraTextureView(context);
    cameraView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    cameraView.setManager(this);
    cameraView.setSurfaceTextureListener(this);
    return cameraView;
  }

  @Override
  public final void onSurfaceTextureAvailable (SurfaceTexture surface, int width, int height) {
    this.surfaceTexture = surface;
    this.textureWidth = width;
    this.textureHeight = height;
    onTextureAvailable(surface, width, height);
  }

  @Override
  public final void onSurfaceTextureSizeChanged (SurfaceTexture surface, int width, int height) {
    this.textureWidth = width;
    this.textureHeight = height;
    onTextureSizeChanged(surface, width, height);
  }

  @Override
  public final boolean onSurfaceTextureDestroyed (SurfaceTexture surface) {
    onTextureDestroyed(surface);
    this.surfaceTexture = null;
    return true;
  }

  @Override
  public final void onSurfaceTextureUpdated (SurfaceTexture surface) { }

  protected final SurfaceTexture getSurfaceTexture () {
    return surfaceTexture;
  }

  protected final int getTextureWidth () {
    return textureWidth;
  }

  protected final int getTextureHeight () {
    return textureHeight;
  }
}
