package org.thunderdog.challegram.texture;

import android.content.Context;
import android.opengl.GLES20;
import android.view.TextureView;

/**
 * Date: 21/12/2016
 * Author: default
 */

@SuppressWarnings("NewApi")
public class CustomTextureView extends TextureView {
  public interface Listener {
    void onTextureCreated (int width, int height);
    void onTextureSizeChanged (int width, int height);
    void onDrawFrame ();
  }

  private final TextureViewQueue queue;
  private Listener listener;

  public CustomTextureView (Context context) {
    super(context);
    this.queue = new TextureViewQueue(new TextureViewQueue.Listener() {
      @Override
      public void onSurfaceCreated (int width, int height) {
        if (listener != null) {
          listener.onTextureCreated(width, height);
        }
      }

      @Override
      public void onSurfaceSizeChanged (int width, int height) {
        if (listener != null) {
          listener.onTextureSizeChanged(width, height);
        }
      }

      @Override
      public void onSurfaceDraw () {
        if (listener != null) {
          listener.onDrawFrame();
        } else {
          GLES20.glClearColor(1f, 0f, 0f, 1f);
          GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        }
      }
    });
  }

  // UI thread

  public void setListener (Listener listener) {
    // this.listener = listener;
  }

  public void onPause () {
    queue.onPause();
  }

  public void onResume () {
    queue.onResume();
  }

  public void onDestroy () {
    queue.onDestroy();
  }

  public void requestRender () {
    queue.requestRender();
  }
}
