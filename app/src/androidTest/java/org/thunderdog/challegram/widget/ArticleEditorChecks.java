/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.article.ArticleEditorTree;
import org.thunderdog.challegram.data.article.ArticleRichText;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;

/** IME, structural editing and Canvas checks against the production inline editor. */
public final class ArticleEditorChecks {
  public static final class PresentationActivity extends org.thunderdog.challegram.MainActivity {
    private Context source;
    void attachPresentation (Context context) { source = context; attachBaseContext(context); }
    @Override public android.content.res.Resources.Theme getTheme () { return source.getTheme(); }
    @Override public android.content.res.Resources getResources () { return source.getResources(); }
    @Override public Object getSystemService (String name) { return source.getSystemService(name); }
  }
  private static TdApi.RichText text (String value) { return new TdApi.RichTextPlain(value); }
  private static TdApi.InputPageBlockParagraph paragraph (String value) { return new TdApi.InputPageBlockParagraph(text(value)); }
  private static void require (boolean condition, String message) { if (!condition) throw new AssertionError(message); }
  private static final class Fixture implements ArticleDocumentView.Delegate {
    final TdApi.InputRichMessage document;
    final ArticleDocumentView view;
    int changes;
    Fixture (Context context, TdApi.InputPageBlock... blocks) {
      document = new TdApi.InputRichMessage(new TdApi.RichMessageSourceBlocks(blocks), false, false);
      view = new ArticleDocumentView(context, this); view.bind(document);
    }
    TdApi.InputPageBlock[] blocks () { return ((TdApi.RichMessageSourceBlocks) document.source).blocks; }
    ArticleTextInput focus (int index, int at) { ArticleTextInput input = view.inputs().get(index); input.requestFocus(); input.setSelection(at); return input; }
    @Override public void changed (boolean structural) { changes++; if (structural) view.bind(document); }
    @Override public void selection (ArticleTextInput input) { }
    @Override public void options (ArticleEditorTree.Entry entry) { }
    @Override public void media (ArticleEditorTree.Entry entry, LinearLayout parent) { throw new AssertionError("Unexpected media in fixture"); }
    @Override public void formula (ArticleEditorTree.Entry entry, TextView view) { }
  }
  public static void splitAndJoin (Context context) {
    TdApi.RichText link = new TdApi.RichTextUrl(new TdApi.RichTextBold(text("link")), "https://example.org/value", false);
    Fixture fixture = new Fixture(context, new TdApi.InputPageBlockParagraph(new TdApi.RichTexts(new TdApi.RichText[] {link, text(" after")})));
    ArticleTextInput input = fixture.focus(0, 4);
    require(input.onCreateInputConnection(new EditorInfo()).commitText("\n", 1), "IME Enter was not handled");
    require(fixture.blocks().length == 2, "Enter did not split the paragraph");
    require(((TdApi.InputPageBlockParagraph) fixture.blocks()[0]).text instanceof TdApi.RichTextUrl, "Split lost link metadata");
    input = fixture.focus(1, 0); input.onKeyDown(KeyEvent.KEYCODE_DEL, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
    require(fixture.blocks().length == 1, "Backspace did not join paragraphs");
    require(ArticleRichText.plain(((TdApi.InputPageBlockParagraph) fixture.blocks()[0]).text).equals("link after"), "Join lost text");
  }
  public static void namedParagraphEditing (Context context) {
    TdApi.RichText original = new TdApi.RichTextReference("paragraph", new TdApi.RichTexts(new TdApi.RichText[] {new TdApi.RichTextBold(text("Visible")), text(" paragraph"), new TdApi.RichTextUrl(text(" link"), "https://example.org", false)}));
    ArticleTextInput input = new ArticleTextInput(context, original, value -> { });
    require(input.getText().toString().equals("Visible paragraph link"), "Named paragraph is an opaque editor token");
    require(ArticleRichText.plain(input.richText()).equals("Visible paragraph link"), "Editor lost named paragraph text");
    final boolean[] reference = {false}, link = {false};
    org.thunderdog.challegram.data.article.ArticleCodec.visit(input.richText(), (node, depth) -> { if (node instanceof TdApi.RichTextReference) reference[0] = true; if (node instanceof TdApi.RichTextUrl) link[0] = true; });
    require(reference[0] && link[0], "Editing stripped reference or link metadata");
    input.setRichText(new TdApi.RichTextCustomEmoji(42, "😀"));
    require(input.getText().toString().equals("😀"), "Custom emoji is still an opaque placeholder");
    require(input.richText() instanceof TdApi.RichTextCustomEmoji && ((TdApi.RichTextCustomEmoji) input.richText()).customEmojiId == 42, "Emoji rendering lost its TDLib identity");
    input.performDestroy();
  }
  public static void crossBlockSelection (Context context) {
    Fixture fixture = new Fixture(context, paragraph("Alpha"), paragraph("Beta"), paragraph("Gamma"));
    ArticleSelectionView selection = new ArticleSelectionView(context, fixture.view, new android.widget.ScrollView(context)); fixture.view.setSelectionView(selection);
    selection.select(fixture.view.inputs().get(0), 2, fixture.view.inputs().get(2), 2);
    fixture.view.formatSelection(new TdApi.RichTextBold(text("")));
    require(fixture.changes == 1, "Cross-block formatting created multiple history entries");
    require(fixture.view.isFormatApplied(TdApi.RichTextBold.CONSTRUCTOR), "Formatting missed selected blocks");
    ArticleTextInput first = fixture.view.inputs().get(0); first.setSelection(0, 2);
    require(!first.isFormatApplied(TdApi.RichTextBold.CONSTRUCTOR), "Formatting leaked before the selection");
    fixture.view.formatSelection(new TdApi.RichTextBold(text("")));
    require(!fixture.view.isFormatApplied(TdApi.RichTextBold.CONSTRUCTOR), "Second formatting action failed to clear every block");
    require(fixture.view.replaceSelectedBlocks(new TdApi.InputPageBlock[] {paragraph("replacement")}), "Replacement rejected a cross-block range");
    require(fixture.blocks().length == 1 && ArticleRichText.plain(((TdApi.InputPageBlockParagraph) fixture.blocks()[0]).text).equals("Alreplacementmma"), "Replacement lost text outside the selection");
  }
  public static void blockReordering (Context context) {
    Fixture fixture = new Fixture(context, paragraph("First"), paragraph("Second"), paragraph("Third"));
    ArticleEditorTree.Group group = ArticleEditorTree.root(fixture.document);
    fixture.view.moveBlock(new ArticleEditorTree.Entry(group, 0, 0), new ArticleEditorTree.Entry(group, 2, 0), true);
    require(ArticleRichText.plain(((TdApi.InputPageBlockParagraph) fixture.blocks()[0]).text).equals("Second"), "Move left an incorrect first block");
    require(ArticleRichText.plain(((TdApi.InputPageBlockParagraph) fixture.blocks()[2]).text).equals("First"), "Move did not retain all intervening blocks");
    require(fixture.changes == 1, "Reorder created more than one history entry");
    fixture.view.deleteDragged(new ArticleEditorTree.Entry(group, 1, 0));
    require(fixture.blocks().length == 2, "Dropping a block on Delete did not remove it");
  }
  public static void listEditing (Context context) {
    Fixture fixture = new Fixture(context, paragraph("First")); fixture.focus(0, 5); fixture.view.listStyle(2);
    ArticleTextInput input = fixture.focus(0, 5); input.onKeyDown(KeyEvent.KEYCODE_ENTER, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
    TdApi.InputPageBlockList list = (TdApi.InputPageBlockList) fixture.blocks()[0];
    require(list.items.length == 2 && list.items[1].value == 2, "Enter did not continue numbering");
    fixture.focus(1, 0).onKeyDown(KeyEvent.KEYCODE_ENTER, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
    require(fixture.blocks().length == 2 && fixture.blocks()[1] instanceof TdApi.InputPageBlockParagraph, "Enter on an empty list item did not exit the list");
    require(((TdApi.InputPageBlockList) fixture.blocks()[0]).items.length == 1, "Exiting a list changed previous items");
  }
  public static void nestedListEditing (Context context) {
    TdApi.InputPageBlockList list = new TdApi.InputPageBlockList(new TdApi.InputPageBlockListItem[] {
      new TdApi.InputPageBlockListItem(new TdApi.InputPageBlock[] {paragraph("One")}, false, false, 1, "1"),
      new TdApi.InputPageBlockListItem(new TdApi.InputPageBlock[] {paragraph("Two")}, false, false, 2, "1")});
    Fixture fixture = new Fixture(context, list); fixture.focus(1, 2); fixture.view.indent(false);
    require(list.items.length == 1 && list.items[0].blocks[1] instanceof TdApi.InputPageBlockList, "Indent did not create a nested item");
    fixture.focus(1, 2); fixture.view.indent(true); require(list.items.length == 2, "Outdent did not restore the outer item");
    require(ArticleRichText.plain(((TdApi.InputPageBlockParagraph) list.items[1].blocks[0]).text).equals("Two"), "Indent/outdent lost text");
  }
  public static void partialFormatting (Context context) {
    ArticleTextInput input = new ArticleTextInput(context, new TdApi.RichTextBold(text("abcdef")), ignored -> { });
    input.setSelection(2, 4); input.format(new TdApi.RichTextBold(text("")));
    require(input.richText(0, 2) instanceof TdApi.RichTextBold && input.richText(4, 6) instanceof TdApi.RichTextBold, "Unformatting removed styles outside the selection");
    require(input.richText(2, 4) instanceof TdApi.RichTextPlain, "Unformatting did not clear the selected range");
    input.setSelection(6); input.format(new TdApi.RichTextItalic(text(""))); input.getText().append("z");
    require(input.richText(6, 7) instanceof TdApi.RichTextItalic, "A caret format was not applied to newly typed text");
  }
  public static void selectionFormattingState (Context context) {
    ArticleTextInput input = new ArticleTextInput(context, new TdApi.RichTexts(new TdApi.RichText[] {new TdApi.RichTextBold(text("ab")), text("cd")}), ignored -> { });
    input.setSelection(0, 4);
    require(!input.isFormatApplied(TdApi.RichTextBold.CONSTRUCTOR), "Mixed selection reported as fully bold");
    input.format(new TdApi.RichTextBold(text("")));
    require(input.isFormatApplied(TdApi.RichTextBold.CONSTRUCTOR), "Applying bold did not cover the selection");
    input.setFormat(new TdApi.RichTextUrl(text(""), "https://example.org/one", false));
    input.setFormat(new TdApi.RichTextUrl(text(""), "https://example.org/two", false));
    require(input.isFormatApplied(TdApi.RichTextBold.CONSTRUCTOR), "Changing link metadata lost bold");
    require(((TdApi.RichTextUrl) input.selectedElement()).url.equals("https://example.org/two"), "Link update toggled the link off");
    input.format(new TdApi.RichTextSubscript(text("")));
    input.format(new TdApi.RichTextSuperscript(text("")));
    require(input.isFormatApplied(TdApi.RichTextSuperscript.CONSTRUCTOR) && !input.isFormatApplied(TdApi.RichTextSubscript.CONSTRUCTOR), "Conflicting index styles remained active");
    input.format(new TdApi.RichTextBold(text("")));
    require(!input.isFormatApplied(TdApi.RichTextBold.CONSTRUCTOR) && input.isFormatApplied(TdApi.RichTextUrl.CONSTRUCTOR), "Removing bold damaged the link");
  }
  public static void selectedQuoteAndListMenuState (Context context) {
    Fixture fixture = new Fixture(context, new TdApi.InputPageBlockParagraph(new TdApi.RichTextUrl(text("before middle after"), "https://example.org", false)));
    ArticleTextInput input = fixture.focus(0, 7); input.setSelection(7, 13);
    require(fixture.view.canIndent() && !fixture.view.canOutdent(), "Plain text has incorrect indentation actions");
    fixture.view.toggleQuoteSelection();
    require(fixture.blocks().length == 3 && fixture.blocks()[1] instanceof TdApi.InputPageBlockBlockQuote, "Quote did not split around the selection");
    require(fixture.view.selectedBlock() instanceof TdApi.InputPageBlockBlockQuote, "Quote selection state was lost");
    require(fixture.view.inputs().get(1).getSelectionEnd() == 6, "Quote did not preserve selection");
    require(((TdApi.InputPageBlockParagraph) ((TdApi.InputPageBlockBlockQuote) fixture.blocks()[1]).blocks[0]).text instanceof TdApi.RichTextUrl, "Quote lost its link");
    fixture.view.toggleQuoteSelection();
    require(fixture.blocks()[1] instanceof TdApi.InputPageBlockParagraph, "Quote did not toggle off");
    fixture.focus(1, 0); fixture.view.listStyle(3);
    require(fixture.view.selectedListStyle() == 3 && fixture.view.canOutdent(), "Checklist menu state is incorrect");
    fixture.view.listStyle(0); require(fixture.view.selectedListStyle() == 0, "None did not exit the list");
  }
  public static void selectionToolbar (Context context) throws Exception {
    PresentationActivity activity = allocate(PresentationActivity.class); activity.attachPresentation(context);
    org.thunderdog.challegram.ui.ArticleEditorController controller = new org.thunderdog.challegram.ui.ArticleEditorController(activity, null);
    TdApi.InputRichMessage input = new TdApi.InputRichMessage(new TdApi.RichMessageSourceBlocks(new TdApi.InputPageBlock[] {paragraph("Select these words")}), false, false);
    set(controller, "working", input); set(controller, "history", new org.thunderdog.challegram.data.article.ArticleHistory(new org.thunderdog.challegram.data.article.ArticleDocument(input)));
    java.lang.reflect.Method build = controller.getClass().getDeclaredMethod("createEditorLayout", Context.class, boolean.class); build.setAccessible(true);
    android.os.Handler handler = (android.os.Handler) get(controller, "handler");
    try {
      build.invoke(controller, activity, false);
      require(get(controller, "sendButton") != null, "Normal toolbar lacks send");
      ArticleTextInput selected = ((ArticleDocumentView) get(controller, "fields")).inputs().get(0); selected.requestFocus(); selected.setSelection(0, 6);
      require(get(controller, "sendButton") == null, "Send remains in selection toolbar");
      LinearLayout styles = (LinearLayout) get(controller, "tools");
      require(styles.getChildCount() == 11, "Selection style group differs from Telegram");
      require(get(controller, "linkTool") != null && get(controller, "dateTool") != null && get(controller, "inlineButtonTool") != null, "Selection actions missing");
      styles.getChildAt(0).performClick();
      require(styles.getChildAt(0).isSelected() && selected.isFormatApplied(TdApi.RichTextBold.CONSTRUCTOR), "Applied bold is not marked active");
      styles.getChildAt(0).performClick(); require(!styles.getChildAt(0).isSelected(), "Bold toggle state is stale");
      selected.setSelection(6);
      require(get(controller, "sendButton") != null && ((LinearLayout) get(controller, "tools")).getChildCount() == 6, "Normal toolbar was not restored");
    } finally { handler.removeCallbacksAndMessages(null); }
  }
  public static void aiSelectionPreservesSurrounding (Context context) {
    Fixture fixture = new Fixture(context, new TdApi.InputPageBlockParagraph(new TdApi.RichTextUrl(text("before typo after"), "https://example.org/keep", false)), paragraph("Other paragraph"));
    ArticleTextInput input = fixture.focus(0, 7); input.setSelection(7, 11);
    require(fixture.view.replaceSelection(input, 7, 11, new TdApi.InputPageBlock[] {paragraph("fixed")}), "AI selection was not applied");
    require(ArticleRichText.plain(((TdApi.InputPageBlockParagraph) fixture.blocks()[0]).text).equals("before fixed after"), "AI replaced surrounding text");
    require(input.richText(0, 6) instanceof TdApi.RichTextUrl && input.richText(13, 18) instanceof TdApi.RichTextUrl, "AI damaged surrounding link metadata");
    require(fixture.blocks().length == 2 && ArticleRichText.plain(((TdApi.InputPageBlockParagraph) fixture.blocks()[1]).text).equals("Other paragraph"), "AI changed another block");
    require(fixture.view.replaceSelection(input, 7, 12, new TdApi.InputPageBlock[] {new TdApi.InputPageBlockSectionHeading(text("Heading"), 2), paragraph("Result")}), "Structured AI selection was not applied");
    require(fixture.blocks().length == 5 && fixture.blocks()[1] instanceof TdApi.InputPageBlockSectionHeading, "Structured replacement lost its block type");
  }
  public static void inlineTableCanvas (Context context) throws Exception {
    TdApi.PageBlockTableCell[][] cells = new TdApi.PageBlockTableCell[3][3];
    for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) cells[r][c] = new TdApi.PageBlockTableCell(text(r == 0 ? "Column " + (c + 1) : "Cell " + r + "," + c), r == 0, 1, 1, new TdApi.PageBlockHorizontalAlignmentLeft(), new TdApi.PageBlockVerticalAlignmentTop());
    Fixture fixture = new Fixture(context, new TdApi.InputPageBlockSectionHeading(text("An editable article"), 2), paragraph("Write directly in the document. Select words to format them."),
      new TdApi.InputPageBlockList(new TdApi.InputPageBlockListItem[] {new TdApi.InputPageBlockListItem(new TdApi.InputPageBlock[] {paragraph("A checklist item")}, true, true, 0, "")}),
      new TdApi.InputPageBlockTable(text("Table title"), cells, true, false, false), paragraph("Text continues below the table."));
    ArticleDocumentView view = fixture.view; view.setBackgroundColor(Theme.fillingColor());
    int width = Screen.dp(392); view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)); view.layout(0, 0, width, view.getMeasuredHeight());
    require(view.inputs().size() == 14, "Table was not rendered as editable cells");
    for (ArticleTextInput input : view.inputs()) require(input.getMeasuredHeight() > 0, "Editor input is not laid out");
    Bitmap bitmap = Bitmap.createBitmap(width, view.getHeight(), Bitmap.Config.ARGB_8888);
    try { view.draw(new Canvas(bitmap)); try (java.io.FileOutputStream stream = new java.io.FileOutputStream(new java.io.File(context.getFilesDir(), "article-inline-editor.png"))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); } }
    finally { bitmap.recycle(); }
  }

  public static void fullEditorCanvas (Context context) throws Exception {
    // Construct only the presentation shell. No Activity.onCreate, TDLib, draft store or navigation.
    PresentationActivity activity = allocate(PresentationActivity.class); activity.attachPresentation(context);
    org.thunderdog.challegram.ui.ArticleEditorController controller = new org.thunderdog.challegram.ui.ArticleEditorController(activity, null);
    TdApi.InputRichMessage input = new TdApi.InputRichMessage(new TdApi.RichMessageSourceBlocks(new TdApi.InputPageBlock[] {
      new TdApi.InputPageBlockSectionHeading(text("A rich article"), 2),
      paragraph("Select text to format it. Lists and tables are editable directly in the document."),
      new TdApi.InputPageBlockList(new TdApi.InputPageBlockListItem[] {
        new TdApi.InputPageBlockListItem(new TdApi.InputPageBlock[] {paragraph("First item")}, false, false, 1, "1"),
        new TdApi.InputPageBlockListItem(new TdApi.InputPageBlock[] {paragraph("Second item")}, false, false, 2, "1")}),
      new TdApi.InputPageBlockTable(text("Table title"), new TdApi.PageBlockTableCell[][] {
        {tableCell("Heading A", true), tableCell("Heading B", true)}, {tableCell("Cell one", false), tableCell("Cell two", false)}}, true, false, false),
      paragraph("Continue writing here.")}), false, false);
    set(controller, "working", input); set(controller, "history", new org.thunderdog.challegram.data.article.ArticleHistory(new org.thunderdog.challegram.data.article.ArticleDocument(input)));
    java.lang.reflect.Method build = controller.getClass().getDeclaredMethod("createEditorLayout", Context.class, boolean.class); build.setAccessible(true);
    android.os.Handler handler = (android.os.Handler) get(controller, "handler");
    try {
      build.invoke(controller, activity, false);
      ArticleDocumentView document = (ArticleDocumentView) get(controller, "fields");
      ArticleTextInput textInput = document.inputs().get(document.inputs().size() - 1); textInput.requestFocus(); textInput.setSelection(textInput.length()); textInput.insert(text(" Added."));
      ((View) get(controller, "undoButton")).performClick();
      require(!ArticleRichText.plain(((TdApi.InputPageBlockParagraph) ArticleEditorTree.root((TdApi.InputRichMessage) get(controller, "working")).blocks()[4]).text).endsWith(" Added."), "Undo failed in the controller");
      ((View) get(controller, "redoButton")).performClick();
      require(ArticleRichText.plain(((TdApi.InputPageBlockParagraph) ArticleEditorTree.root((TdApi.InputRichMessage) get(controller, "working")).blocks()[4]).text).endsWith(" Added."), "Redo failed in the controller");
      View root = (View) get(controller, "root"); int width = Screen.dp(392), height = Screen.dp(720);
      root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)); root.layout(0, 0, width, height);
      Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
      try { root.draw(new Canvas(bitmap)); try (java.io.FileOutputStream stream = new java.io.FileOutputStream(new java.io.File(context.getFilesDir(), "article-editor-full.png"))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); } }
      finally { bitmap.recycle(); }
    } finally { handler.removeCallbacksAndMessages(null); }
  }
  public static void aiResultDoesNotCrash (Context context) throws Exception {
    PresentationActivity activity = allocate(PresentationActivity.class); activity.attachPresentation(context);
    org.thunderdog.challegram.ui.ArticleEditorController controller = new org.thunderdog.challegram.ui.ArticleEditorController(activity, null);
    TdApi.InputRichMessage input = new TdApi.InputRichMessage(new TdApi.RichMessageSourceBlocks(new TdApi.InputPageBlock[] {paragraph("Thsi is a test.")}), false, false);
    org.thunderdog.challegram.data.article.ArticleDocument original = new org.thunderdog.challegram.data.article.ArticleDocument(input);
    set(controller, "working", input); set(controller, "history", new org.thunderdog.challegram.data.article.ArticleHistory(original));
    java.lang.reflect.Method build = controller.getClass().getDeclaredMethod("createEditorLayout", Context.class, boolean.class); build.setAccessible(true);
    java.lang.reflect.Method apply = controller.getClass().getDeclaredMethod("applyAiResult", TdApi.RichMessage.class); apply.setAccessible(true);
    android.os.Handler handler = (android.os.Handler) get(controller, "handler");
    try {
      build.invoke(controller, activity, false);
      TdApi.RichMessage corrected = new TdApi.RichMessage(new TdApi.PageBlock[] {new TdApi.PageBlockParagraph(new TdApi.RichTextDiff(text("This is a test."), text("Thsi is a test.")))}, false, true);
      require((Boolean) apply.invoke(controller, corrected), "AI correction was not accepted");
      ArticleDocumentView document = (ArticleDocumentView) get(controller, "fields");
      require(document.inputs().get(0).getText().toString().equals("This is a test."), "Corrected text is not visible in the editor");
      ((View) get(controller, "undoButton")).performClick();
      require(original.equals(new org.thunderdog.challegram.data.article.ArticleDocument((TdApi.InputRichMessage) get(controller, "working"))), "Undo did not restore the pre-AI draft");
      TdApi.RichMessage unsupported = new TdApi.RichMessage(new TdApi.PageBlock[] {new TdApi.PageBlockUnsupported()}, false, true);
      require(!(Boolean) apply.invoke(controller, unsupported), "Unsupported AI output was accepted");
      require(original.equals(new org.thunderdog.challegram.data.article.ArticleDocument((TdApi.InputRichMessage) get(controller, "working"))), "Rejected AI output modified the draft");
      ((View) get(controller, "redoButton")).performClick();
      require(((ArticleDocumentView) get(controller, "fields")).inputs().get(0).getText().toString().equals("This is a test."), "Rejected output damaged the redo history");
    } finally { handler.removeCallbacksAndMessages(null); }
  }

  public static void aiIconCentered (Context context) {
    android.graphics.drawable.Drawable icon = androidx.core.content.ContextCompat.getDrawable(context, org.thunderdog.challegram.R.drawable.article_input_ai_24);
    require(icon != null, "Missing AI icon");
    Bitmap image = Bitmap.createBitmap(144, 144, Bitmap.Config.ARGB_8888);
    try {
      icon.setBounds(0, 0, 144, 144); icon.draw(new Canvas(image));
      int left = 144, top = 144, right = -1, bottom = -1;
      for (int y = 0; y < 144; y++) for (int x = 0; x < 144; x++) if (android.graphics.Color.alpha(image.getPixel(x, y)) > 128) {
        left = Math.min(left, x); top = Math.min(top, y); right = Math.max(right, x); bottom = Math.max(bottom, y);
      }
      require(right > left && bottom > top, "AI glyph is empty");
      require(Math.abs(left + right - 143) <= 2 && Math.abs(top + bottom - 143) <= 2, "AI letters are not centred in the button drawable");
    } finally { image.recycle(); }
  }

  private static TdApi.PageBlockTableCell tableCell (String value, boolean header) { TdApi.PageBlockTableCell cell = org.thunderdog.challegram.data.article.ArticleTableGrid.empty(); cell.text = text(value); cell.isHeader = header; return cell; }
  private static java.lang.reflect.Field field (Class<?> type, String name) throws Exception {
    for (Class<?> cls = type; cls != null; cls = cls.getSuperclass()) try { java.lang.reflect.Field field = cls.getDeclaredField(name); field.setAccessible(true); return field; } catch (NoSuchFieldException ignored) { }
    throw new NoSuchFieldException(name);
  }
  private static void set (Object object, String name, Object value) throws Exception { field(object.getClass(), name).set(object, value); }
  private static Object get (Object object, String name) throws Exception { return field(object.getClass(), name).get(object); }
  private static <T> T allocate (Class<T> type) throws Exception {
    Class<?> cls = Class.forName("sun.misc.Unsafe"); Object unsafe;
    try { unsafe = field(cls, "theUnsafe").get(null); } catch (NoSuchFieldException ignored) { unsafe = field(cls, "THE_ONE").get(null); }
    return type.cast(cls.getMethod("allocateInstance", Class.class).invoke(unsafe, type));
  }
}
