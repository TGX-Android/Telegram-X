package org.thunderdog.challegram.stage8;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.navigation.ForumRailTransition;
import org.thunderdog.challegram.navigation.ForumRailTransition.Avatar;
import org.thunderdog.challegram.stage8.Stage8SyntheticInstrumentation.Case;
import org.thunderdog.challegram.theme.ThemeId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.thunderdog.challegram.stage8.SyntheticEnvironment.equal;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.measure;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.require;

/** Production avatar projection/capture and source-layout hold; no accounts or navigation animator. */
public final class ForumRailTransitionChecks {
  private static final int WIDTH = 320, HEIGHT = 288;
  private static final int SOURCE_COLOR = 0xffcc4422, TARGET_COLOR = 0xff2266cc;
  private static final int BADGE_COLOR = 0xffeebb33;

  private ForumRailTransitionChecks () { }

  public static void register (List<Case> cases) {
    for (boolean rtl : new boolean[] {false, true}) {
      cases.add(new Case("rail_transition_matched_geometry_swapped_ids_rtl" + rtl,
        env -> matchedGeometry(env, rtl)));
    }
    cases.add(new Case("rail_transition_unmatched_avatar_fades", ForumRailTransitionChecks::unmatchedFades));
    cases.add(new Case("rail_transition_endpoint_raster_and_clamping", ForumRailTransitionChecks::endpoints));
    cases.add(new Case("rail_transition_close_restores_source_visibility_once", ForumRailTransitionChecks::closeRestoresVisibility));
    cases.add(new Case("rail_transition_reverse_and_cancel_retrace_frames", ForumRailTransitionChecks::reverseAndCancel));
    cases.add(new Case("rail_transition_capture_nested_view_coordinates", ForumRailTransitionChecks::capture));
    cases.add(new Case("rail_transition_source_layout_holds_pending_updates", ForumRailTransitionChecks::sourceLayoutHoldsUpdates));
    cases.add(new Case("rail_transition_source_layout_preserves_existing_suppression", ForumRailTransitionChecks::sourceLayoutPreservesSuppression));
    cases.add(new Case("rail_transition_empty_projection", ForumRailTransitionChecks::empty));
  }

  /** Avatar drawings are unit circles; marks observe the production canvas transform. */
  private static final class Probe {
    final String name;
    final float x, y, radius;
    final int color;
    final Avatar avatar;
    final List<Boolean> visibility = new ArrayList<>();
    float decorations = Float.NaN;

    Probe (String side, long id, float x, float y, float radius, int color) {
      this.name = side + ":" + id;
      this.x = x; this.y = y; this.radius = radius; this.color = color;
      avatar = new Avatar(id, x, y, radius, this::draw, visibility::add);
    }

    void draw (Canvas canvas, float decorationAlpha) {
      decorations = decorationAlpha;
      ((RecordingCanvas) canvas).mark(name, new RectF(-1f, -1f, 1f, 1f), color);
      Paint paint = new Paint(); // Static endpoints use this same paint and shape.
      paint.setColor(color);
      canvas.drawCircle(0f, 0f, 1f, paint);
      if (decorationAlpha > 0f) {
        paint.setColor(BADGE_COLOR);
        paint.setAlpha(Math.round(255f * decorationAlpha));
        canvas.drawCircle(.75f, -1f, .25f, paint);
      }
    }

    void drawEndpoint (Canvas canvas, float decorationAlpha) {
      int save = canvas.save();
      canvas.translate(x, y);
      canvas.scale(radius, radius);
      draw(canvas, decorationAlpha);
      canvas.restoreToCount(save);
    }
  }

  private static float mirrored (float x, boolean rtl) { return rtl ? WIDTH - x : x; }

  private static RecordingCanvas render (ForumRailTransition transition, float progress) {
    RecordingCanvas canvas = new RecordingCanvas(WIDTH, HEIGHT);
    try {
      int saveCount = canvas.getSaveCount();
      transition.draw(canvas, progress);
      equal(saveCount, canvas.getSaveCount(), "Projection must restore canvas layers and saves");
      return canvas;
    } catch (RuntimeException | Error failure) {
      canvas.close();
      throw failure;
    }
  }

  private static void near (float expected, float actual, String message) {
    require(Math.abs(expected - actual) <= .001f, message + ": expected " + expected + ", was " + actual);
  }

  private static void geometry (RecordingCanvas canvas, String name, float x, float y, float radius) {
    RecordingCanvas.Mark mark = canvas.one(name);
    near(x - radius, mark.bounds.left, name + " left");
    near(y - radius, mark.bounds.top, name + " top");
    near(x + radius, mark.bounds.right, name + " right");
    near(y + radius, mark.bounds.bottom, name + " bottom");
    RecordingCanvas.inside(mark, WIDTH, HEIGHT);
  }

  private static void absent (RecordingCanvas canvas, String name) {
    for (RecordingCanvas.Mark mark : canvas.marks) require(!mark.name.equals(name), "Unexpected draw for " + name);
  }

  private static void alpha (int expected, int pixel, String message) {
    require(Math.abs(expected - Color.alpha(pixel)) <= 1,
      message + ": expected alpha " + expected + ", was " + Color.alpha(pixel));
  }

  private static String bitmapDescription (Bitmap bitmap) {
    return bitmap.getWidth() + "x" + bitmap.getHeight() + " " + bitmap.getConfig() +
      " hasAlpha=" + bitmap.hasAlpha() + " premultiplied=" + bitmap.isPremultiplied() +
      " rowBytes=" + bitmap.getRowBytes() + " density=" + bitmap.getDensity();
  }

  private static void sameRaster (RecordingCanvas expected, RecordingCanvas actual, String message) {
    if (expected.bitmap.sameAs(actual.bitmap)) return;
    String metadata = "; expectedBitmap={" + bitmapDescription(expected.bitmap) +
      "}; actualBitmap={" + bitmapDescription(actual.bitmap) + "}";
    int width = expected.bitmap.getWidth(), height = expected.bitmap.getHeight();
    require(width == actual.bitmap.getWidth() && height == actual.bitmap.getHeight(),
      message + ": sameAs=false; dimensions differ" + metadata);
    int[] expectedPixels = new int[width * height], actualPixels = new int[width * height];
    expected.bitmap.getPixels(expectedPixels, 0, width, 0, 0, width, height);
    actual.bitmap.getPixels(actualPixels, 0, width, 0, 0, width, height);
    int different = 0, first = -1;
    int left = width, top = height, right = -1, bottom = -1;
    for (int i = 0; i < expectedPixels.length; i++) {
      if (expectedPixels[i] == actualPixels[i]) continue;
      different++;
      if (first < 0) first = i;
      int x = i % width, y = i / width;
      left = Math.min(left, x); top = Math.min(top, y);
      right = Math.max(right, x); bottom = Math.max(bottom, y);
    }
    String firstDifference = first < 0 ? "none (all getPixels ARGB values equal; inspect metadata/raw storage)" :
      "(" + first % width + "," + first / width + ") expected=" +
        String.format(java.util.Locale.ROOT, "0x%08x", expectedPixels[first]) + " actual=" +
        String.format(java.util.Locale.ROOT, "0x%08x", actualPixels[first]);
    throw new AssertionError(message + ": sameAs=false; differentPixels=" + different + "/" + expectedPixels.length +
      "; firstDifference=" + firstDifference + (first < 0 ? "" :
        "; differenceBoundsInclusive=[" + left + "," + top + ".." + right + "," + bottom + "]") +
      metadata + "; defaultPaintAntiAlias=" + new Paint().isAntiAlias());
  }

  private static void matchedGeometry (SyntheticEnvironment env, boolean rtl) throws Exception {
    env.configure(1f, rtl, ThemeId.BLUE);
    Probe fromA = new Probe("source", 101L, mirrored(96, rtl), 72, 24, SOURCE_COLOR);
    Probe fromB = new Probe("source", 202L, mirrored(224, rtl), 208, 32, 0xff448833);
    Probe toA = new Probe("target", 101L, mirrored(32, rtl), 120, 16, TARGET_COLOR);
    Probe toB = new Probe("target", 202L, mirrored(32, rtl), 56, 20, 0xff884499);
    ForumRailTransition transition = new ForumRailTransition(Arrays.asList(fromA.avatar, fromB.avatar),
      Arrays.asList(toB.avatar, toA.avatar)); // Deliberately not source order.
    try {
      for (float p : new float[] {0f, .25f, .5f, .75f, 1f}) {
        try (RecordingCanvas canvas = render(transition, p)) {
          float ax = mirrored(96f - 64f * p, rtl), ay = 72f + 48f * p, ar = 24f - 8f * p;
          float bx = mirrored(224f - 192f * p, rtl), by = 208f - 152f * p, br = 32f - 12f * p;
          if (p > 0f) {
            geometry(canvas, toA.name, ax, ay, ar);
            geometry(canvas, toB.name, bx, by, br);
            near(p, toA.decorations, "Matched target decorations follow shared progress");
            near(p, toB.decorations, "Matching must be by chat ID, not position");
          } else {
            absent(canvas, toA.name);
            absent(canvas, toB.name);
          }
          if (p < 1f) {
            geometry(canvas, fromA.name, ax, ay, ar);
            geometry(canvas, fromB.name, bx, by, br);
            near(0f, fromA.decorations, "Source decorations are suppressed");
            near(0f, fromB.decorations, "Second source decorations are suppressed");
          } else {
            absent(canvas, fromA.name);
            absent(canvas, fromB.name);
          }
          int pixel = canvas.bitmap.getPixel(Math.round(ax), Math.round(ay));
          equal(255, Color.alpha(pixel), "Matched image remains opaque while receivers blend");
          int sourceAlpha = Math.round(255f * (1f - p));
          int red = Math.round((Color.red(SOURCE_COLOR) * sourceAlpha + Color.red(TARGET_COLOR) * (255 - sourceAlpha)) / 255f);
          require(Math.abs(red - Color.red(pixel)) <= 1, "Matched receiver raster blend follows progress");
          canvas.mark("after_projection", new RectF(3, 5, 7, 9), 0);
          require(new RectF(3, 5, 7, 9).equals(canvas.one("after_projection").bounds), "Projection must not leak its transform");
        }
      }
    } finally { transition.close(); }
  }

  private static void unmatchedFades (SyntheticEnvironment env) throws Exception {
    env.configure(1f, false, ThemeId.BLUE);
    Probe source = new Probe("source", 303L, 80, 80, 20, SOURCE_COLOR);
    Probe target = new Probe("target", 404L, 208, 176, 24, TARGET_COLOR);
    ForumRailTransition transition = new ForumRailTransition(Collections.singletonList(source.avatar), Collections.singletonList(target.avatar));
    try {
      for (float p : new float[] {0f, .125f, .25f, .5f, .75f, 1f}) {
        try (RecordingCanvas canvas = render(transition, p)) {
          alpha(Math.round(255f * (1f - p)), canvas.bitmap.getPixel(80, 80), "Unmatched source fades in place");
          alpha(Math.round(255f * p), canvas.bitmap.getPixel(208, 176), "Unmatched target fades in place");
          if (p < 1f) {
            geometry(canvas, source.name, 80, 80, 20);
            near(0f, source.decorations, "Unmatched source has no rail decorations");
          } else absent(canvas, source.name);
          if (p > 0f) {
            geometry(canvas, target.name, 208, 176, 24);
            near(1f, target.decorations, "Unmatched target fades its decorations with the whole layer");
          } else absent(canvas, target.name);
        }
      }
    } finally { transition.close(); }
  }

  private static void endpoints (SyntheticEnvironment env) throws Exception {
    env.configure(1f, false, ThemeId.BLUE);
    Probe source = new Probe("source", 101L, 96, 72, 24, SOURCE_COLOR);
    Probe sourceOnly = new Probe("source", 202L, 224, 208, 32, 0xff448833);
    Probe target = new Probe("target", 101L, 32, 120, 16, TARGET_COLOR);
    Probe targetOnly = new Probe("target", 303L, 272, 64, 20, 0xff884499);
    ForumRailTransition transition = new ForumRailTransition(Arrays.asList(source.avatar, sourceOnly.avatar),
      Arrays.asList(targetOnly.avatar, target.avatar));
    try (RecordingCanvas sourceEndpoint = new RecordingCanvas(WIDTH, HEIGHT);
         RecordingCanvas targetEndpoint = new RecordingCanvas(WIDTH, HEIGHT)) {
      source.drawEndpoint(sourceEndpoint, 0f);
      sourceOnly.drawEndpoint(sourceEndpoint, 0f);
      target.drawEndpoint(targetEndpoint, 1f);
      targetOnly.drawEndpoint(targetEndpoint, 1f);
      for (float p : new float[] {Float.NEGATIVE_INFINITY, -2f, 0f, 1f, 2f, Float.POSITIVE_INFINITY}) {
        try (RecordingCanvas canvas = render(transition, p)) {
          RecordingCanvas expected = p <= 0f ? sourceEndpoint : targetEndpoint;
          sameRaster(expected, canvas, "Endpoint handoff must be pixel-identical at progress " + p);
        }
      }
    } finally { transition.close(); }
  }

  private static void closeRestoresVisibility (SyntheticEnvironment env) throws Exception {
    env.configure(1f, false, ThemeId.BLUE);
    Probe source = new Probe("source", 101L, 96, 72, 24, SOURCE_COLOR);
    Probe sourceOnly = new Probe("source", 202L, 224, 208, 32, SOURCE_COLOR);
    Probe target = new Probe("target", 101L, 32, 120, 16, TARGET_COLOR);
    Probe targetOnly = new Probe("target", 303L, 272, 64, 20, TARGET_COLOR);
    ForumRailTransition transition = new ForumRailTransition(Arrays.asList(source.avatar, sourceOnly.avatar),
      Arrays.asList(target.avatar, targetOnly.avatar));
    try {
      require(source.visibility.equals(Collections.singletonList(true)), "Matched source is hidden on capture");
      require(sourceOnly.visibility.equals(Collections.singletonList(true)), "Unmatched source is also hidden");
      for (float p : new float[] {.25f, .75f, .25f}) {
        try (RecordingCanvas canvas = render(transition, p)) {
          require(!canvas.marks.isEmpty(), "Open projection draws avatars");
        }
      }
      equal(1, source.visibility.size(), "Drawing never toggles source visibility");
      transition.close();
      transition.close();
      require(source.visibility.equals(Arrays.asList(true, false)), "Matched source restored exactly once");
      require(sourceOnly.visibility.equals(Arrays.asList(true, false)), "Unmatched source restored exactly once");
      require(target.visibility.isEmpty() && targetOnly.visibility.isEmpty(), "Target visibility remains container-owned");
      try (RecordingCanvas canvas = render(transition, .5f); RecordingCanvas blank = new RecordingCanvas(WIDTH, HEIGHT)) {
        require(canvas.marks.isEmpty(), "Closed projection must not issue draws");
        sameRaster(blank, canvas, "Closed projection is inert");
      }
      equal(2, source.visibility.size(), "Drawing after close cannot re-hide a source");
    } finally { transition.close(); }
  }

  private static void reverseAndCancel (SyntheticEnvironment env) throws Exception {
    env.configure(1f, false, ThemeId.BLUE);
    Probe source = new Probe("source", 101L, 96, 72, 24, SOURCE_COLOR);
    Probe target = new Probe("target", 101L, 32, 120, 16, TARGET_COLOR);
    ForumRailTransition transition = new ForumRailTransition(Collections.singletonList(source.avatar), Collections.singletonList(target.avatar));
    RecordingCanvas[] reference = new RecordingCanvas[9];
    try {
      for (int step = 0; step <= 8; step++) reference[step] = render(transition, step / 8f);
      // Open, partially close with Back, cancel to Topics, then close fully to Chats.
      int[] trajectory = {0, 1, 2, 3, 4, 5, 6, 7, 8, 7, 6, 5, 4, 4, 5, 6, 7, 8, 7, 6, 5, 4, 3, 2, 1, 0};
      float previousX = source.x, previousY = source.y, previousRadius = source.radius;
      int previousStep = 0;
      for (int step : trajectory) {
        try (RecordingCanvas canvas = render(transition, step / 8f)) {
          sameRaster(reference[step], canvas, "Reversed/cancelled trajectory must retrace raster frame " + step);
          String geometryName = step == 0 ? source.name : target.name;
          RectF bounds = canvas.one(geometryName).bounds;
          geometry(canvas, geometryName, 96f - 8f * step, 72f + 6f * step, 24f - step);
          if (step == 0) absent(canvas, target.name);
          if (step == 8) absent(canvas, source.name);
          int delta = step - previousStep;
          near(-8f * delta, bounds.centerX() - previousX, "Continuous X across direction changes");
          near(6f * delta, bounds.centerY() - previousY, "Continuous Y across direction changes");
          near(-delta, bounds.width() / 2f - previousRadius, "Continuous radius across direction changes");
          previousX = bounds.centerX(); previousY = bounds.centerY(); previousRadius = bounds.width() / 2f;
          previousStep = step;
        }
      }
      require(source.visibility.equals(Collections.singletonList(true)), "Reversal must not prematurely restore the source");
    } finally {
      transition.close();
      for (RecordingCanvas canvas : reference) if (canvas != null) canvas.close();
    }
  }

  private static void capture (SyntheticEnvironment env) throws Exception {
    Context context = env.configure(1f, false, ThemeId.BLUE);
    FrameLayout host = new FrameLayout(context), parent = new FrameLayout(context);
    View sourceView = new View(context);
    FrameLayout.LayoutParams parentParams = new FrameLayout.LayoutParams(220, 200, Gravity.TOP | Gravity.LEFT);
    parentParams.leftMargin = 40; parentParams.topMargin = 30;
    host.addView(parent, parentParams);
    FrameLayout.LayoutParams viewParams = new FrameLayout.LayoutParams(80, 80, Gravity.TOP | Gravity.LEFT);
    viewParams.leftMargin = 12; viewParams.topMargin = 18;
    parent.addView(sourceView, viewParams);
    measure(host, WIDTH, HEIGHT);
    parent.scrollTo(5, 7);
    sourceView.setAlpha(.65f);
    RectF originalBounds = new RectF(sourceView.getLeft(), sourceView.getTop(), sourceView.getRight(), sourceView.getBottom());
    List<Boolean> visibility = new ArrayList<>();
    Paint paint = new Paint();
    paint.setColor(SOURCE_COLOR);
    Avatar captured = Avatar.capture(101L, host, sourceView, 20f, 24f, 10f, (canvas, decorations) -> {
      near(0f, decorations, "Captured source decorations stay suppressed");
      ((RecordingCanvas) canvas).mark("captured_source", new RectF(10, 14, 30, 34), SOURCE_COLOR);
      canvas.drawCircle(20f, 24f, 10f, paint);
    }, hidden -> {
      visibility.add(hidden);
      sourceView.setVisibility(hidden ? View.INVISIBLE : View.VISIBLE);
    });
    equal(101L, captured.chatId, "Capture retains the chat ID");
    near(67f, captured.x, "Capture includes nested left positions and scroll offset");
    near(65f, captured.y, "Capture includes nested top positions and scroll offset");
    near(10f, captured.radius, "Capture retains the receiver radius");
    equal(View.VISIBLE, sourceView.getVisibility(), "Capture alone does not hide a view");
    Probe target = new Probe("target", 101L, 208, 168, 20, TARGET_COLOR);
    ForumRailTransition transition = new ForumRailTransition(Collections.singletonList(captured), Collections.singletonList(target.avatar));
    try {
      equal(View.INVISIBLE, sourceView.getVisibility(), "Projection hides its captured source");
      for (float p : new float[] {0f, .5f, 1f}) {
        try (RecordingCanvas canvas = render(transition, p)) {
          float x = 67f + 141f * p, y = 65f + 103f * p, radius = 10f + 10f * p;
          if (p > 0f) geometry(canvas, target.name, x, y, radius);
          else absent(canvas, target.name);
          if (p < 1f) geometry(canvas, "captured_source", x, y, radius);
          else absent(canvas, "captured_source");
          equal(255, Color.alpha(canvas.bitmap.getPixel(Math.round(x), Math.round(y))), "Captured circle reaches the raster at its projected center");
        }
      }
      require(originalBounds.equals(new RectF(sourceView.getLeft(), sourceView.getTop(), sourceView.getRight(), sourceView.getBottom())),
        "Capture/draw must not mutate source view layout");
      near(.65f, sourceView.getAlpha(), "Capture/draw must not mutate source alpha");
    } finally { transition.close(); }
    transition.close();
    equal(View.VISIBLE, sourceView.getVisibility(), "Close restores the captured view");
    require(visibility.equals(Arrays.asList(true, false)), "Captured visibility restoration is idempotent");
  }

  private static final class SourceRow {
    final long chatId;
    int revision;

    SourceRow (long chatId) { this.chatId = chatId; }
  }

  private static final class SourceHolder extends RecyclerView.ViewHolder {
    long chatId = RecyclerView.NO_ID;
    int revision = -1;

    SourceHolder (View view) { super(view); }
  }

  private static final class SourceAdapter extends RecyclerView.Adapter<SourceHolder> {
    static final int ROW_HEIGHT = 48;
    final List<SourceRow> rows = new ArrayList<>(Arrays.asList(new SourceRow(101L), new SourceRow(202L), new SourceRow(303L)));
    int binds, recycles;

    SourceAdapter () { setHasStableIds(true); }

    @Override public long getItemId (int position) { return rows.get(position).chatId; }
    @Override public int getItemCount () { return rows.size(); }

    @Override public SourceHolder onCreateViewHolder (ViewGroup parent, int viewType) {
      View row = new View(parent.getContext());
      row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ROW_HEIGHT));
      return new SourceHolder(row);
    }

    @Override public void onBindViewHolder (SourceHolder holder, int position) {
      SourceRow row = rows.get(position);
      holder.chatId = row.chatId;
      holder.revision = row.revision;
      binds++;
    }

    @Override public void onViewRecycled (SourceHolder holder) {
      holder.chatId = RecyclerView.NO_ID;
      holder.revision = -1;
      recycles++;
    }
  }

  /** Detached, fixed-size list: all traversals are synchronous on the synthetic runner's main thread. */
  private static final class SourceList implements AutoCloseable {
    final RecyclerView view;
    final SourceAdapter adapter = new SourceAdapter();
    final SourceHolder[] captured = new SourceHolder[3];
    final long[] capturedIds = {101L, 202L, 303L};
    final int initialBinds, initialRecycles;

    SourceList (Context context) {
      view = new RecyclerView(context);
      view.setLayoutManager(new LinearLayoutManager(context));
      view.setItemAnimator(null);
      view.setAdapter(adapter);
      measure(view, WIDTH, HEIGHT);
      equal(captured.length, view.getChildCount(), "Initial source rows are visible");
      for (int i = 0; i < captured.length; i++) {
        captured[i] = (SourceHolder) view.findViewHolderForItemId(capturedIds[i]);
        require(captured[i] != null, "Initial holder exists for stable ID " + capturedIds[i]);
        equal(capturedIds[i], captured[i].chatId, "Initial holder is bound to its stable ID");
      }
      require(!view.hasPendingAdapterUpdates(), "Initial layout consumes adapter updates");
      require(!view.isLayoutRequested(), "Source layout is settled before capture");
      initialBinds = adapter.binds;
      initialRecycles = adapter.recycles;
    }

    void queueUpdates () {
      adapter.rows.add(0, new SourceRow(404L));
      adapter.notifyItemInserted(0);
      heldTraversal("insert");

      adapter.rows.add(0, adapter.rows.remove(3));
      adapter.notifyItemMoved(3, 0);
      heldTraversal("move");

      equal(101L, adapter.rows.get(2).chatId, "Rebind targets the captured chat after insert/move");
      adapter.rows.get(2).revision++;
      adapter.notifyItemChanged(2);
      heldTraversal("rebind");
    }

    void heldTraversal (String operation) {
      // Use unchanged bounds; forceLayout/changed bounds would bypass normal requestLayout deferral.
      view.requestLayout();
      require(!view.isLayoutRequested(), operation + ": held layout request is deferred");
      measure(view, WIDTH, HEIGHT);
      require(view.isLayoutSuppressed(), operation + ": source remains suppressed");
      require(view.hasPendingAdapterUpdates(), operation + ": adapter updates remain queued");
      equal(initialBinds, adapter.binds, operation + ": no source rebind during morph");
      equal(initialRecycles, adapter.recycles, operation + ": no source recycle during morph");
      equal(captured.length, view.getChildCount(), operation + ": visible row count is unchanged");
      for (int i = 0; i < captured.length; i++) {
        SourceHolder holder = captured[i];
        equal(capturedIds[i], holder.chatId, operation + ": captured holder retains chatId");
        equal(0, holder.revision, operation + ": captured drawing data is not rebound");
        require(view.getChildAt(i) == holder.itemView, operation + ": captured row order is unchanged");
        equal(i * SourceAdapter.ROW_HEIGHT, holder.itemView.getTop(), operation + ": captured row geometry is unchanged");
      }
    }

    void assertReleasedUpdates () {
      require(!view.isLayoutSuppressed(), "Release restores an unsuppressed source");
      require(view.isLayoutRequested(), "Release requests the deferred layout without a manual request");
      measure(view, WIDTH, HEIGHT);
      require(!view.hasPendingAdapterUpdates(), "Release traversal consumes pending updates");
      long[] expectedIds = {303L, 404L, 101L, 202L};
      equal(expectedIds.length, view.getChildCount(), "Inserted row becomes visible after release");
      for (int i = 0; i < expectedIds.length; i++) {
        SourceHolder holder = (SourceHolder) view.getChildViewHolder(view.getChildAt(i));
        equal(expectedIds[i], holder.chatId, "Pending insert/move order is applied at row " + i);
        equal(expectedIds[i], holder.getItemId(), "Holder stable ID agrees with its chat binding");
        equal(i, holder.getBindingAdapterPosition(), "Holder position agrees with the updated adapter");
        equal(expectedIds[i] == 101L ? 1 : 0, holder.revision, "Pending rebind data is applied after release");
        equal(i * SourceAdapter.ROW_HEIGHT, holder.itemView.getTop(), "Released rows use the updated geometry");
      }
      require(adapter.binds > initialBinds, "Release performs real adapter binds");
    }

    @Override public void close () {
      view.suppressLayout(false);
      view.setAdapter(null);
      view.setLayoutManager(null);
    }
  }

  private static void sourceLayoutHoldsUpdates (SyntheticEnvironment env) throws Exception {
    try (SourceList source = new SourceList(env.configure(1f, false, ThemeId.BLUE))) {
      require(!source.view.isLayoutSuppressed(), "Source starts unsuppressed");
      Runnable release = ForumRailTransition.holdSourceLayout(source.view);
      try {
        require(source.view.isLayoutSuppressed(), "Production helper suppresses the source immediately");
        source.queueUpdates();
        release.run();
        source.assertReleasedUpdates();
        release.run();
        require(!source.view.isLayoutSuppressed(), "Repeated release stays unsuppressed");

        source.view.suppressLayout(true);
        release.run();
        require(source.view.isLayoutSuppressed(), "Stale release must not clear a later owner's suppression");
      } finally { release.run(); }
    }
  }

  private static void sourceLayoutPreservesSuppression (SyntheticEnvironment env) throws Exception {
    try (SourceList source = new SourceList(env.configure(1f, false, ThemeId.BLUE))) {
      source.view.suppressLayout(true);
      Runnable release = ForumRailTransition.holdSourceLayout(source.view);
      try {
        source.queueUpdates();
        release.run();
        source.heldTraversal("release preserves prior suppression");
        release.run();
        source.heldTraversal("repeated release preserves prior suppression");

        source.view.suppressLayout(false); // The original owner, not the morph, releases this hold.
        release.run();
        source.assertReleasedUpdates();
      } finally { release.run(); }
    }
  }

  private static void empty (SyntheticEnvironment env) throws Exception {
    env.configure(1f, false, ThemeId.BLUE);
    ForumRailTransition transition = new ForumRailTransition(Collections.emptyList(), Collections.emptyList());
    try {
      for (float p : new float[] {0f, .5f, 1f}) {
        try (RecordingCanvas canvas = render(transition, p); RecordingCanvas blank = new RecordingCanvas(WIDTH, HEIGHT)) {
          require(canvas.marks.isEmpty(), "Empty projections must not issue draws");
          sameRaster(blank, canvas, "Empty projections leave the canvas untouched");
        }
      }
    } finally { transition.close(); }
  }
}
