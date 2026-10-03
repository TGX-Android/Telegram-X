package org.thunderdog.challegram.stage8;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

/** Records real Android text advances/transforms while also rasterizing to an ARGB bitmap. */
public final class RecordingCanvas extends Canvas implements AutoCloseable {
  public static final class Mark {
    public final String name;
    public final RectF bounds;
    public final int color;
    public final float textSize;
    Mark (String name, RectF bounds, int color, float textSize) {
      this.name = name; this.bounds = bounds; this.color = color; this.textSize = textSize;
    }
  }

  public final Bitmap bitmap;
  public final List<Mark> marks = new ArrayList<>();
  private boolean drawingRoundRect;

  public RecordingCanvas (int width, int height) {
    this(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888));
  }

  private RecordingCanvas (Bitmap bitmap) { super(bitmap); this.bitmap = bitmap; }

  public void mark (String name, RectF local, int color) {
    mark(name, local, color, 0);
  }

  @SuppressWarnings("deprecation")
  private void mark (String name, RectF local, int color, float textSize) {
    RectF transformed = new RectF(local);
    getMatrix().mapRect(transformed);
    marks.add(new Mark(name, transformed, color, textSize));
  }

  @Override public void drawText (String text, float x, float y, Paint paint) {
    float advance = paint.measureText(text);
    float left = x - (paint.getTextAlign() == Paint.Align.RIGHT ? advance :
      paint.getTextAlign() == Paint.Align.CENTER ? advance / 2 : 0);
    if (!text.isEmpty()) mark("text:" + text, new RectF(left, y + paint.ascent(), left + advance, y + paint.descent()), paint.getColor(), paint.getTextSize());
    super.drawText(text, x, y, paint);
  }

  @Override public void drawRoundRect (RectF rect, float rx, float ry, Paint paint) {
    boolean nested = drawingRoundRect;
    if (!nested) mark("roundRect", rect, paint.getColor());
    drawingRoundRect = true;
    try {
      // API 21+ may dispatch to the float overload; record this primitive only once.
      super.drawRoundRect(rect, rx, ry, paint);
    } finally { drawingRoundRect = nested; }
  }

  @TargetApi(21)
  @Override public void drawRoundRect (float left, float top, float right, float bottom, float rx, float ry, Paint paint) {
    boolean nested = drawingRoundRect;
    if (!nested) mark("roundRect", new RectF(left, top, right, bottom), paint.getColor());
    drawingRoundRect = true;
    try {
      super.drawRoundRect(left, top, right, bottom, rx, ry, paint);
    } finally { drawingRoundRect = nested; }
  }

  @Override public void drawCircle (float cx, float cy, float radius, Paint paint) {
    mark("circle", new RectF(cx - radius, cy - radius, cx + radius, cy + radius), paint.getColor());
    super.drawCircle(cx, cy, radius, paint);
  }

  public Mark one (String name) {
    Mark result = null;
    for (Mark mark : marks) if (mark.name.equals(name)) {
      SyntheticEnvironment.require(result == null, "Duplicate draw for " + name);
      result = mark;
    }
    SyntheticEnvironment.require(result != null, "Missing draw for " + name);
    return result;
  }

  public static void inside (Mark mark, int width, int height) {
    RectF r = mark.bounds;
    SyntheticEnvironment.require(!r.isEmpty() && r.left >= -.75f && r.top >= -.75f && r.right <= width + .75f && r.bottom <= height + .75f,
      mark.name + " outside " + width + "x" + height + ": " + r);
  }

  public static void separate (Mark a, Mark b) {
    RectF overlap = new RectF(a.bounds);
    SyntheticEnvironment.require(!overlap.intersect(b.bounds) || overlap.width() <= .75f || overlap.height() <= .75f,
      "Collision: " + a.name + " " + a.bounds + " / " + b.name + " " + b.bounds);
  }

  @Override public void close () { setBitmap(null); bitmap.recycle(); }
}
