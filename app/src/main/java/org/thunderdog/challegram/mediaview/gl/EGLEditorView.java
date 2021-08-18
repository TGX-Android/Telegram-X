package org.thunderdog.challegram.mediaview.gl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.mediaview.MediaCellView;
import org.thunderdog.challegram.mediaview.crop.CropState;
import org.thunderdog.challegram.mediaview.data.FiltersState;
import org.thunderdog.challegram.mediaview.paint.PaintMode;
import org.thunderdog.challegram.mediaview.paint.PaintState;
import org.thunderdog.challegram.mediaview.paint.SimpleDrawing;
import org.thunderdog.challegram.mediaview.paint.widget.SimpleDrawingView;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.android.widget.FrameLayoutFix;

/**
 * Date: 11/12/2016
 * Author: default
 */

public class EGLEditorView extends ViewGroup {
  private SimpleDrawingView paintView;
  private @Nullable View textureView;
  private @Nullable EGLEditorContext editorContext;
  private @Nullable Bitmap sourceBitmap;
  private FiltersState currentFiltersState;
  private PaintState currentPaintState;
  private ImageGalleryFile currentFile;

  private int croppedWidth, croppedHeight;
  private int centerX, centerY;
  private int normalWidth, normalHeight;

  private class OverlayView extends View {
    public OverlayView (Context context) {
      super(context);
    }

    @Override
    protected void onDraw (Canvas c) {
      int width = croppedWidth;
      int height = croppedHeight;
      int left = centerX - width / 2;
      int right = left + width;
      int top = centerY -  height / 2;
      int bottom = top + height;

      int viewWidth = getMeasuredWidth();
      int viewHeight = getMeasuredHeight();

      Paint paint = Paints.fillingPaint(0xff000000);

      if (left > 0) {
        c.drawRect(0, top, left, bottom, paint);
      }
      if (right < viewWidth) {
        c.drawRect(right, top, viewWidth, bottom, paint);
      }
      if (top > 0) {
        c.drawRect(0, 0, viewWidth, top, paint);
      }
      if (bottom < viewHeight) {
        c.drawRect(0, bottom, viewWidth, viewHeight, paint);
      }
    }
  }

  private FrameLayoutFix textureWrap;
  private OverlayView overlayView;

  private ContentLayout contentWrap;

  public EGLEditorView (Context context) {
    super(context);
    textureWrap = new FrameLayoutFix(context);
    textureWrap.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    addView(textureWrap);

    contentWrap = new ContentLayout(context);
    contentWrap.setParent(this);
    contentWrap.setLayoutParams(FrameLayoutFix.newParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    contentWrap.setVisibility(View.INVISIBLE);
    textureWrap.addView(contentWrap);

    overlayView = new OverlayView(context);
    overlayView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    addView(overlayView);
  }

  private static final int TYPE_NONE = 0;
  private static final int TYPE_FILTERS = 1;
  private static final int TYPE_PAINT = 2;

  private int initedType;

  public void init (ImageGalleryFile imageFile, Bitmap bitmap, @Nullable FiltersState filtersState, @Nullable PaintState paintState) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      this.currentFile = imageFile;
      this.sourceBitmap = bitmap;

      final int initType;

      if (filtersState != null) {
        this.currentFiltersState = filtersState;
        initType = TYPE_FILTERS;
      } else if (paintState != null) {
        this.currentPaintState = paintState;
        initType = TYPE_PAINT;
      } else {
        throw new IllegalArgumentException("filtersState == null && paintState == null");
      }


      android.view.TextureView textureView = null;

      switch (initType) {
        case TYPE_FILTERS: {
          textureView = new android.view.TextureView(getContext());
          textureView.setLayoutParams(FrameLayoutFix.newParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
          textureView.setVisibility(textureVisible ? View.VISIBLE : View.INVISIBLE);
          textureView.setSurfaceTextureListener(new android.view.TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable (SurfaceTexture surface, int width, int height) {
              if (surface != null && editorContext == null && sourceBitmap != null && !sourceBitmap.isRecycled() && currentFiltersState != null) {
                editorContext = new EGLEditorContext(surface, sourceBitmap, currentFiltersState, width, height);
                editorContext.requestRender(true, true);
              }
            }

            @Override
            public void onSurfaceTextureSizeChanged (SurfaceTexture surface, int width, int height) {
              if (editorContext != null) {
                editorContext.setSurfaceTextureSize(width, height);
                editorContext.requestRender(false, true);
                UI.post(() -> editorContext.requestRender(false, true));
              }
            }

            @Override
            public boolean onSurfaceTextureDestroyed (SurfaceTexture surface) {
              destroy();
              return true;
            }

            @Override
            public void onSurfaceTextureUpdated (SurfaceTexture surface) { }
          });
          contentWrap.addView(textureView);
          break;
        }
        case TYPE_PAINT: {
          contentWrap.setPaintingGesturesEnabled(true);
          break;
        }
      }
      paintView = new SimpleDrawingView(getContext());
      paintView.setLayoutParams(FrameLayoutFix.newParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
      contentWrap.addView(paintView);
      contentWrap.setPaintingState(paintState);
      this.textureView = textureView;
      applyCurrentStyles();
      initedType = initType;
    }
  }

  private int appliedRotation;
  private CropState sourceCropState;

  private void applyCurrentStyles () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      if (appliedRotation != sourceRotation) {
        boolean wasRotated = U.isRotated(appliedRotation);
        appliedRotation = sourceRotation;
        boolean nowRotated = U.isRotated(sourceRotation);
        if (wasRotated != nowRotated) {
          contentWrap.requestLayout();
        }
      }
      if (sourceCropState != null && !sourceCropState.isEmpty()) {
        float degrees = sourceCropState.getDegreesAroundCenter();
        contentWrap.setRotation(degrees);

        double rad = Math.toRadians(degrees);
        float sin = (float) Math.abs(Math.sin(rad));
        float cos = (float) Math.abs(Math.cos(rad));

        float w = sourceWidth;
        float h = sourceHeight;

        // W = w·|cos φ| + h·|sin φ|
        // H = w·|sin φ| + h·|cos φ|

        float W = w * cos + h * sin;
        float H = w * sin + h * cos;

        float scale = Math.max(W / w, H / h);
        contentWrap.setScaleX(scale);
        contentWrap.setScaleY(scale);
      } else {
        contentWrap.setRotation(0);
        contentWrap.setScaleX(1f);
        contentWrap.setScaleY(1f);
        contentWrap.setTranslationX(0f);
        contentWrap.setTranslationY(0f);
      }
      textureWrap.setRotation(sourceRotation);
    }
  }

  public void setViewSizes (int width, int height, int croppedWidth, int croppedHeight) {
    this.normalWidth = width;
    this.normalHeight = height;
    this.croppedWidth = croppedWidth;
    this.croppedHeight = croppedHeight;
    overlayView.invalidate();
  }

  public void setCenter (int centerX, int centerY) {
    this.centerX = centerX;
    this.centerY = centerY;
    overlayView.invalidate();
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(widthMeasureSpec, heightMeasureSpec);

    if (getChildCount() == 0) {
      return;
    }

    int textureWidth, textureHeight;
    textureWidth = normalWidth;
    textureHeight = normalHeight;

    if (U.isRotated(sourceRotation)) {
      textureWrap.measure(MeasureSpec.makeMeasureSpec(textureHeight, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(textureWidth, MeasureSpec.EXACTLY));
    } else {
      textureWrap.measure(MeasureSpec.makeMeasureSpec(textureWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(textureHeight, MeasureSpec.EXACTLY));
    }

    float scale = 1f;
    float translateX = 0;
    float translateY = 0;
    if (sourceCropState != null) {
      double left = sourceCropState.getLeft();
      double top = sourceCropState.getTop();
      double right = sourceCropState.getRight();
      double bottom = sourceCropState.getBottom();

      double width = (right - left);
      double height = (bottom - top);

      scale = Math.max((float) croppedWidth / (float) (textureWidth * width), (float) croppedHeight / (float) (textureHeight * height));

      double cx = ((left + right) / 2 - 0.5);
      double cy = ((top + bottom) / 2 - 0.5);

      textureWidth *= scale;
      textureHeight *= scale;

      float textureCx = (float) (cx * (float) textureWidth);
      float textureCy = (float) (cy * (float) textureHeight);

      translateX = -textureCx;
      translateY = -textureCy;
    }
    textureWrap.setScaleX(scale);
    textureWrap.setScaleY(scale);
    textureWrap.setTranslationX(translateX);
    textureWrap.setTranslationY(translateY);

    overlayView.measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
  }

  @Override
  protected void onLayout (boolean changed, int l, int t, int r, int b) {
    final int childCount = getChildCount();

    /*final int parentWidth = getMeasuredWidth();
    final int parentHeight = getMeasuredHeight();*/

    for (int i = 0; i < childCount; i++) {
      View view = getChildAt(i);
      if (view == null) {
        continue;
      }

      if (view == overlayView) {
        view.layout(l, t, r, b);
      } else {
        int viewWidth = view.getMeasuredWidth();
        int viewHeight = view.getMeasuredHeight();

        /*if (Utils.isRotated(sourceRotation)) {
          int temp = viewWidth;
          viewWidth = viewHeight;
          viewHeight = temp;
        }*/

        int left, top, right, bottom;

        left = centerX - viewWidth / 2;
        top = centerY - viewHeight / 2;

        right = left + viewWidth;
        bottom = top + viewHeight;

        view.layout(left, top, right, bottom);
      }
    }
  }

  public void requestRenderByChange (boolean isBlur) {
    if (editorContext != null) {
      editorContext.requestRender(isBlur);
    }
  }

  public void destroy () {
    if (editorContext != null) {
      editorContext.destroy();
      editorContext = null;
    }
  }

  public void pause () {
    if (editorContext != null) {
      editorContext.pause();
    }
  }

  private boolean textureVisible;

  public void setEditorVisible (boolean isVisible) {
    this.textureVisible = isVisible;
    if (textureView != null) {
      textureView.setVisibility(isVisible ? View.VISIBLE : View.INVISIBLE);
    }
    contentWrap.setVisibility(isVisible ? View.VISIBLE : View.INVISIBLE);
    overlayView.setVisibility(isVisible ? View.VISIBLE : View.INVISIBLE);
  }

  public boolean isEditorVisible () {
    return textureVisible;
  }

  private int sourceWidth, sourceHeight, sourceRotation;

  public void reset (ImageGalleryFile imageFile, int sourceWidth, int sourceHeight, @Nullable Bitmap bitmap, @Nullable FiltersState filtersState, @Nullable PaintState paintState) {
    this.sourceWidth = sourceWidth;
    this.sourceHeight = sourceHeight;
    this.sourceRotation = imageFile.getVisualRotationWithCropRotation();
    this.sourceCropState = imageFile.getCropState();
    if (initedType == TYPE_NONE) {
      init(imageFile, bitmap, filtersState, paintState);
      return;
    }
    this.sourceBitmap = bitmap;
    this.currentFiltersState = filtersState;
    this.currentPaintState = paintState;
    this.currentFile = imageFile;
    applyCurrentStyles();
    switch (initedType) {
      case TYPE_FILTERS:
        if (editorContext != null) {
          editorContext.resumeWithData(bitmap, filtersState);
        }
        break;
    }
    contentWrap.setPaintingState(paintState);
  }

  public void setSizes (int width, int height, int rotation, CropState cropState) {
    this.sourceWidth = width;
    this.sourceCropState = cropState;
    this.sourceHeight = height;
    this.sourceRotation = rotation;
  }

  private boolean isReady () {
    switch (initedType) {
      case TYPE_FILTERS:
        return editorContext != null;
      case TYPE_PAINT:
        return false; // TODO
    }
    return false;
  }

  public void getBitmapAsync (BitmapCallback callback) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH && isReady()) {
      switch (initedType) {
        case TYPE_FILTERS:
          if (editorContext != null) {
            editorContext.getBitmap(callback);
          } else {
            callback.onBitmapObtained(null);
          }
          break;
      }
    } else {
      callback.onBitmapObtained(null);
    }
  }

  private int paintingMode;

  public void setPaintingMode (int mode) {
    if (this.paintingMode != mode) {
      this.paintingMode = mode;
      contentWrap.setPaintingGesturesEnabled(mode != PaintMode.FREE_MOVEMENT);
      contentWrap.setPaintingMode(mode);
    }
  }

  // ContentLayout

  public ContentLayout getContentWrap () {
    return contentWrap;
  }

  public static class ContentLayout extends FrameLayoutFix implements SimpleDrawing.ChangeListener, Runnable {
    private EGLEditorView parent;

    public ContentLayout (@NonNull Context context) {
      super(context);
    }

    public void setParent (EGLEditorView parent) {
      this.parent = parent;
    }

    private boolean paintingEnabled;

    public void setPaintingGesturesEnabled (boolean isEnabled) {
      this.paintingEnabled = isEnabled;
    }

    private PaintState state;
    private int paintingMode;

    public void setPaintingMode (int mode) {
      this.paintingMode = mode;
    }

    public void setPaintingState (PaintState state) {
      this.state = state;
      parent.paintView.setPaintState(state);
    }

    private int effectiveMode = PaintMode.NONE;
    private boolean needZoomCancel;

    public void cancelDrawingByZoom () {
      needZoomCancel = true;
    }

    public interface DrawingListener {
      void onDrawingStateChanged (View editorView, int paintMode);
    }

    private DrawingListener listener;

    public void setDrawingListener (DrawingListener listener) {
      this.listener = listener;
    }

    public boolean hasEffectiveDrawing () {
      return effectiveMode != PaintMode.NONE;
    }

    private void setEffectiveMode (int mode) {
      if (this.effectiveMode != mode) {
        this.effectiveMode = mode;
        if (this.listener != null) {
          listener.onDrawingStateChanged(this, mode);
        }
      }
    }

    @SuppressWarnings("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent (MotionEvent e) {
      int action = e.getAction();

      if (state == null) {
        return false;
      }

      if (action == MotionEvent.ACTION_DOWN) {
        needZoomCancel = false;
      }

      if (action != MotionEvent.ACTION_DOWN && effectiveMode == PaintMode.NONE) {
        return false;
      }

      switch (action) {
        case MotionEvent.ACTION_DOWN: {
          if (!paintingEnabled || paintingMode == PaintMode.FREE_MOVEMENT || e.getPointerCount() > 1 || !beginDrawing(paintingMode, e, getMeasuredWidth(), getMeasuredHeight())) {
            setEffectiveMode(PaintMode.NONE);
            return false;
          }
          setEffectiveMode(paintingMode);
          break;
        }
        case MotionEvent.ACTION_MOVE: {
          if (!moveDrawing(effectiveMode, e, ((MediaCellView) parent.getParent()).getZoomFactor() == 1f)) {
            setEffectiveMode(PaintMode.NONE);
          }
          break;
        }
        case MotionEvent.ACTION_UP: {
          completeDrawing(effectiveMode, e, false);
          setEffectiveMode(PaintMode.NONE);
          break;
        }
        case MotionEvent.ACTION_CANCEL: {
          completeDrawing(effectiveMode, e, needZoomCancel);
          setEffectiveMode(PaintMode.NONE);
          needZoomCancel = false;
          break;
        }
      }

      return true;
    }

    private SimpleDrawing currentSimpleDrawing;

    private int brushColor;
    private float brushRadius;

    public void setBrushParameters (int brushColor, float brushRadius) {
      if (this.brushColor != brushColor || this.brushRadius != brushRadius) {
        this.brushColor = brushColor;
        this.brushRadius = brushRadius;
        if (currentSimpleDrawing != null) {
          currentSimpleDrawing.setBrushParameters(brushColor, brushRadius);
        }
      }
    }

    private boolean beginDrawing (int mode, MotionEvent e, int canvasWidth, int canvasHeight) {
      int type;
      switch (mode) {
        case PaintMode.ARROW:
          type = SimpleDrawing.TYPE_ARROW;
          break;
        case PaintMode.RECTANGLE:
          type = SimpleDrawing.TYPE_RECTANGLE;
          break;
        case PaintMode.PATH:
          type = SimpleDrawing.TYPE_PATH;
          break;
        default:
          return false;
      }
      SimpleDrawing drawing = new SimpleDrawing(type, canvasWidth, canvasHeight, 1f, parent.sourceCropState != null ? parent.sourceCropState.getDegreesAroundCenter() : 0f);
      drawing.startDrawing(e);
      drawing.setBrushParameters(brushColor, brushRadius);
      state.addSimpleDrawing(drawing);
      drawing.addChangeListener(this);
      currentSimpleDrawing = drawing;
      return true;
    }

    private boolean moveDrawing (int mode, MotionEvent e, boolean allowHistory) {
      switch (mode) {
        case PaintMode.ARROW:
        case PaintMode.RECTANGLE:
        case PaintMode.PATH: {
          currentSimpleDrawing.moveDrawing(e, allowHistory);
          return true;
        }
      }
      throw new UnsupportedOperationException("mode == " + mode);
    }

    private void completeDrawing (int mode, MotionEvent e, boolean byPinch) {
      switch (mode) {
        case PaintMode.ARROW:
        case PaintMode.RECTANGLE:
        case PaintMode.PATH: {
          if (currentSimpleDrawing.completeDrawing(e, byPinch)) {
            state.trackSimpleDrawingAction(currentSimpleDrawing);
          } else {
            state.removeSimpleDrawing(currentSimpleDrawing);
          }
          currentSimpleDrawing.removeChangeListener(this);
          currentSimpleDrawing = null;
          break;
        }
      }
    }

    public boolean isBusy () {
      return effectiveMode != PaintMode.NONE;
    }

    private boolean scheduled;

    @Override
    public void run () {
      if (scheduled) {
        scheduled = false;
        parent.paintView.invalidate();
      }
    }

    @Override
    public void onDrawingHasChanged (SimpleDrawing drawing, boolean delayed) {
      if (delayed) {
        if (!scheduled) {
          scheduled = true;
          postDelayed(this, 6);
          return;
        }
      } else if (scheduled) {
        removeCallbacks(this);
      }
      parent.paintView.invalidate();
    }
  }
}
