/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Picture;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.data.PageBlock;
import org.thunderdog.challegram.data.TGMessageArticle;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.tool.Screen;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Android rendering regression checks, run on the main thread in an isolated test app.
 * The real ArticleBodyView, PageBlockView, measurement, layout and Canvas recording run here.
 * Only account/controller construction and document loading are replaced by in-memory data.
 * Colored blocks make missing rows measurable without network, files, emoji or native decoding.
 */
public final class ArticleBodyViewChecks {
  private static final int WIDTH = 240, ROW_HEIGHT = 64, VIEWPORT = 160;

  private ArticleBodyViewChecks () { }

  public static void cachedRows (Context context) throws Exception {
    try (Fixture fixture = new Fixture(context, 12)) {
      Picture recording = fixture.record();
      Bitmap bitmap = Bitmap.createBitmap(WIDTH, 12 * ROW_HEIGHT, Bitmap.Config.ARGB_8888);
      recording.draw(new Canvas(bitmap));
      try {
        for (int i = 0; i < 12; i++) {
          require(bitmap.getPixel(8, i * ROW_HEIGHT + 8) == color(i),
            "Display list omitted bound row " + i + " outside the initial viewport");
        }
      } finally { bitmap.recycle(); }
    }
  }

  public static void cachedRowsAfterScroll (Context context) throws Exception {
    try (Fixture fixture = new Fixture(context, 12)) {
      Picture recording = fixture.record();
      // Android can replay the same item display list after scrolling the RecyclerView.
      // Do not call ArticleBodyView.draw again: that would hide the regression.
      for (int offset : new int[] {ROW_HEIGHT * 7, ROW_HEIGHT * 3, 0}) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, VIEWPORT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.translate(0, -offset);
        recording.draw(canvas);
        try {
          require(bitmap.getPixel(8, 8) == color(offset / ROW_HEIGHT),
            "Cached article became blank after scrolling to " + offset);
        } finally { bitmap.recycle(); }
      }
    }
  }

  public static void positionAfterLayout (Context context) throws Exception {
    try (Fixture fixture = new Fixture(context, 240)) {
      fixture.moveTo(120 * ROW_HEIGHT);
      fixture.beforeDraw();
      fixture.assertViewport(120);
      require(fixture.body.getChildCount() < 240, "Long articles must retain bounded child allocation");
      fixture.moveTo(0);
      fixture.beforeDraw();
      fixture.assertViewport(0);
    }
  }

  public static void recycledMessage (Context context) throws Exception {
    try (Fixture fixture = new Fixture(context, 240)) {
      fixture.moveTo(120 * ROW_HEIGHT);
      fixture.beforeDraw();
      fixture.assertViewport(120);
      fixture.body.setMessage(message(3));
      fixture.count = 3;
      fixture.measure();
      fixture.moveTo(0);
      fixture.beforeDraw();
      fixture.assertViewport(0);
      require(fixture.body.getChildCount() == 3, "Recycled article retained old rows or omitted new ones");
    }
  }

  public static void richTextRows (Context context) throws Exception {
    TGMessageArticle message = message(0);
    TdApi.RichMessage article = new TdApi.RichMessage(new TdApi.PageBlock[] {
      new TdApi.PageBlockSectionHeading(new TdApi.RichTextPlain("Article rendering"), 2),
      new TdApi.PageBlockParagraph(new TdApi.RichTexts(new TdApi.RichText[] {
        new TdApi.RichTextBold(new TdApi.RichTextPlain("Bold introduction. ")),
        new TdApi.RichTextPlain("Paragraphs remain visible while the article scrolls. "),
        new TdApi.RichTextUrl(new TdApi.RichTextPlain("Open link"), "https://example.com/article", false)
      })),
      new TdApi.PageBlockList(new TdApi.PageBlockListItem[] {
        new TdApi.PageBlockListItem("1.", new TdApi.PageBlock[] {new TdApi.PageBlockParagraph(new TdApi.RichTextPlain("First list item"))}, false, false, 1, "1"),
        new TdApi.PageBlockListItem("2.", new TdApi.PageBlock[] {new TdApi.PageBlockParagraph(new TdApi.RichTextPlain("Second list item"))}, false, false, 2, "1")
      }),
      new TdApi.PageBlockTable(new TdApi.RichTextPlain("Table caption"), new TdApi.PageBlockTableCell[][] {
        {cell("Column A", true), cell("Column B", true)}, {cell("Cell one", false), cell("Cell two", false)}
      }, true, true, false),
      new TdApi.PageBlockParagraph(new TdApi.RichTextUrl(new TdApi.RichTextPlain("Link after the table"), "https://example.com/footer", false))
    }, false, true);
    set(message, "article", article);
    java.lang.reflect.Method build = TGMessageArticle.class.getDeclaredMethod("buildContent", int.class);
    build.setAccessible(true);
    int width = Screen.dp(288);
    build.invoke(message, width);
    int height = message.getArticleBodyHeight();
    require(height > 0 && !message.hasExpandButton(), "Fixture must fit within the expanded message body");
    ArticleBodyView body = new ArticleBodyView(context);
    FrameLayout host = new FrameLayout(context);
    host.addView(body);
    host.layout(0, 0, width, Screen.dp(80));
    body.setMessage(message);
    body.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    body.layout(0, 0, width, height);
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    try {
      body.draw(new Canvas(bitmap));
      for (TGMessageArticle.Row row : message.getArticleRows()) {
        require(row.height > 0, "A text or table row measured to zero");
        int pixels = 0;
        for (int y = row.top; y < row.top + row.height; y++) {
          for (int x = row.indent; x < width; x++) if ((bitmap.getPixel(x, y) >>> 24) != 0) pixels++;
        }
        require(pixels > 20, "Native row was not painted: " + row.block.getOriginalBlock().getClass().getSimpleName());
      }
      java.io.File file = new java.io.File(context.getFilesDir(), "article-native-blocks.png");
      try (java.io.FileOutputStream output = new java.io.FileOutputStream(file)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); }
    } finally { body.performDestroy(); bitmap.recycle(); }
  }

  private static TdApi.PageBlockTableCell cell (String text, boolean header) {
    return new TdApi.PageBlockTableCell(new TdApi.RichTextPlain(text), header, 1, 1,
      new TdApi.PageBlockHorizontalAlignmentLeft(), new TdApi.PageBlockVerticalAlignmentTop());
  }

  private static final class Fixture implements AutoCloseable {
    final FrameLayout viewport;
    final ArticleBodyView body;
    int count, offset;

    Fixture (Context context, int count) throws Exception {
      this.count = count;
      viewport = new FrameLayout(context);
      body = new ArticleBodyView(context);
      viewport.addView(body);
      viewport.layout(0, 0, WIDTH, VIEWPORT);
      body.setMessage(message(count));
      measure();
    }

    void measure () {
      body.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(count * ROW_HEIGHT, View.MeasureSpec.EXACTLY));
      body.layout(0, -offset, WIDTH, count * ROW_HEIGHT - offset);
    }

    void moveTo (int offset) {
      body.offsetTopAndBottom(this.offset - offset);
      this.offset = offset;
    }

    void beforeDraw () throws Exception {
      Object listener = get(body, "visibilityListener");
      if (listener instanceof ViewTreeObserver.OnPreDrawListener) {
        ViewTreeObserver.OnPreDrawListener preDraw = (ViewTreeObserver.OnPreDrawListener) listener;
        if (!preDraw.onPreDraw()) measure();
        require(preDraw.onPreDraw(), "Article viewport did not settle after the requested layout");
      }
    }

    Picture record () {
      Picture picture = new Picture();
      body.draw(picture.beginRecording(WIDTH, count * ROW_HEIGHT));
      picture.endRecording();
      return picture;
    }

    void assertViewport (int firstRow) {
      Bitmap bitmap = Bitmap.createBitmap(WIDTH, VIEWPORT, Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(bitmap);
      canvas.translate(0, -offset);
      body.draw(canvas);
      try {
        for (int i = 0; i < 2; i++) {
          require(bitmap.getPixel(8, i * ROW_HEIGHT + 8) == color(firstRow + i),
            "Missing visible row " + (firstRow + i));
        }
      } finally { bitmap.recycle(); }
    }

    @Override public void close () { body.performDestroy(); }
  }

  private static TGMessageArticle message (int count) throws Exception {
    TGMessageArticle message = allocate(TGMessageArticle.class);
    MessagesManager manager = allocate(MessagesManager.class);
    set(manager, "controller", allocate(MessagesController.class));
    set(message, "manager", manager);
    TdApi.RichMessage article = new TdApi.RichMessage();
    article.isFull = true;
    set(message, "article", article);
    set(message, "bodyHeight", count * ROW_HEIGHT);
    set(message, "height", count * ROW_HEIGHT);
    Constructor<TGMessageArticle.Row> constructor = TGMessageArticle.Row.class.getDeclaredConstructor(
      PageBlock.class, int.class, int.class, int.class);
    constructor.setAccessible(true);
    List<TGMessageArticle.Row> rows = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      rows.add(constructor.newInstance(new ColorBlock(i), i * ROW_HEIGHT, ROW_HEIGHT, 0));
    }
    set(message, "rows", rows);
    return message;
  }

  private static int color (int index) { return 0xff000000 | ((index + 1) * 0x137f31 & 0xffffff); }

  private static final class ColorBlock extends PageBlock {
    final int index;
    ColorBlock (int index) { super(null, new TdApi.PageBlockParagraph(new TdApi.RichTextPlain("row")), 0); this.index = index; }
    @Override public int getRelatedViewType () { return ListItem.TYPE_PAGE_BLOCK; }
    @Override protected int computeHeight (View view, int width) { return ROW_HEIGHT; }
    @Override protected int getContentTop () { return 0; }
    @Override protected int getContentHeight () { return ROW_HEIGHT; }
    @Override protected boolean handleTouchEvent (View view, MotionEvent event) { return false; }
    @Override public void drawInternal (View view, Canvas canvas, Receiver preview, Receiver receiver, ComplexReceiver icons) {
      Paint paint = new Paint();
      paint.setColor(color(index));
      canvas.drawRect(0, 0, view.getWidth(), view.getHeight(), paint);
    }
  }

  private static void require (boolean condition, String message) { if (!condition) throw new AssertionError(message); }

  private static Field field (Class<?> type, String name) throws Exception {
    for (Class<?> current = type; current != null; current = current.getSuperclass()) {
      try { Field field = current.getDeclaredField(name); field.setAccessible(true); return field; }
      catch (NoSuchFieldException ignored) { }
    }
    throw new NoSuchFieldException(name);
  }
  private static Object get (Object owner, String name) throws Exception { return field(owner.getClass(), name).get(owner); }
  private static void set (Object owner, String name, Object value) throws Exception { field(owner.getClass(), name).set(owner, value); }
  private static <T> T allocate (Class<T> type) throws Exception {
    Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
    Field instance;
    try { instance = field(unsafeType, "theUnsafe"); } catch (NoSuchFieldException androidName) { instance = field(unsafeType, "THE_ONE"); }
    return type.cast(unsafeType.getMethod("allocateInstance", Class.class).invoke(instance.get(null), type));
  }
}
