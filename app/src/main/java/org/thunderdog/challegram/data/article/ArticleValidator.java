/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import org.drinkless.tdlib.TdApi;

/** Fast local checks; the server remains authoritative about chat rights and evolving limits. */
public final class ArticleValidator {
  private ArticleValidator () { }
  public enum Problem { EMPTY, TEXT_LENGTH, BLOCK_COUNT, DEPTH, MEDIA_COUNT, TABLE_COLUMNS, READ_ONLY, STRUCTURE }
  public static final class Limits {
    public final int text, blocks, depth, media, columns;
    public Limits (int text, int blocks, int depth, int media, int columns) {
      this.text = text; this.blocks = blocks; this.depth = depth; this.media = media; this.columns = columns;
    }
  }
  public static Problem validate (TdApi.InputRichMessage input, Limits limits) {
    try { return validateTree(input, limits); }
    catch (IllegalArgumentException | NullPointerException e) { return Problem.STRUCTURE; }
  }
  private static Problem validateTree (TdApi.InputRichMessage input, Limits limits) {
    if (input == null || !(input.source instanceof TdApi.RichMessageSourceBlocks)) return Problem.READ_ONLY;
    final int[] total = new int[4]; // text, blocks, media, table columns
    final boolean[] readOnly = {false};
    final boolean[] invalid = {false};
    ArticleCodec.visit(input, (value, depth) -> {
      String text = null;
      if (value instanceof TdApi.RichTextPlain) text = ((TdApi.RichTextPlain) value).text;
      else if (value instanceof TdApi.RichTextCustomEmoji) text = ((TdApi.RichTextCustomEmoji) value).alternativeText;
      else if (value instanceof TdApi.RichTextMathematicalExpression) text = ((TdApi.RichTextMathematicalExpression) value).expression;
      else if (value instanceof TdApi.InputPageBlockMathematicalExpression) text = ((TdApi.InputPageBlockMathematicalExpression) value).expression;
      if (text != null) total[0] += text.codePointCount(0, text.length());
      if (value instanceof TdApi.InputPageBlock || value instanceof TdApi.InputPageBlockListItem) total[1]++;
      if (value instanceof TdApi.InputPageBlockPhoto || value instanceof TdApi.InputPageBlockVideo || value instanceof TdApi.InputPageBlockAnimation || value instanceof TdApi.InputPageBlockAudio || value instanceof TdApi.InputPageBlockDocument || value instanceof TdApi.InputPageBlockVoiceNote || value instanceof TdApi.InputPageBlockMap) total[2]++;
      if (value instanceof TdApi.InputPageBlockTable) {
        TdApi.PageBlockTableCell[][] rows = ((TdApi.InputPageBlockTable) value).cells;
        total[1] += rows.length;
        int columns = tableColumns(rows, limits.columns);
        if (columns < 0) invalid[0] = true;
        total[3] = Math.max(total[3], columns);
      }
      if (value instanceof TdApi.InputPageBlockSectionHeading) { int size = ((TdApi.InputPageBlockSectionHeading) value).size; invalid[0] |= size < 1 || size > 6; }
      if (value instanceof TdApi.InputPageBlockList) {
        TdApi.InputPageBlockListItem[] items = ((TdApi.InputPageBlockList) value).items;
        invalid[0] |= items.length == 0;
        for (TdApi.InputPageBlockListItem item : items) invalid[0] |= item.blocks.length == 0 || item.value < 0 || (item.value == 0) != (items[0].value == 0) || !("".equals(item.type) || "1".equals(item.type) || "a".equals(item.type) || "A".equals(item.type) || "i".equals(item.type) || "I".equals(item.type));
      }
      if (value instanceof TdApi.InputPageBlockCollage) invalid[0] |= !isGallery(((TdApi.InputPageBlockCollage) value).blocks);
      if (value instanceof TdApi.InputPageBlockSlideshow) invalid[0] |= !isGallery(((TdApi.InputPageBlockSlideshow) value).blocks);
      if (value instanceof TdApi.InputPageBlockButtonRow) invalid[0] |= ((TdApi.InputPageBlockButtonRow) value).buttons.length == 0;
      if (value instanceof TdApi.InputPageBlockMap) {
        TdApi.InputPageBlockMap map = (TdApi.InputPageBlockMap) value;
        invalid[0] |= map.location == null || Double.isNaN(map.location.latitude) || Double.isNaN(map.location.longitude) || Math.abs(map.location.latitude) > 90 || Math.abs(map.location.longitude) > 180 || map.zoom < 0 || map.zoom > 24 || map.width <= 0 || map.height <= 0 || (long) map.width + map.height > 10000 || (long) map.width > (long) map.height * 20 || (long) map.height > (long) map.width * 20;
      }
      if (value instanceof TdApi.InputPageBlockThinking || value instanceof TdApi.RichTextIcon || value instanceof TdApi.RichTextDiff) readOnly[0] = true;
      if (value instanceof TdApi.InlineButton) {
        TdApi.InlineKeyboardButtonType type = ((TdApi.InlineButton) value).type;
        if (!(type instanceof TdApi.InlineKeyboardButtonTypeUrl) && !(type instanceof TdApi.InlineKeyboardButtonTypeUser) && !(type instanceof TdApi.InlineKeyboardButtonTypeCopyText)) readOnly[0] = true;
        invalid[0] |= ArticleRichText.plain(((TdApi.InlineButton) value).text).trim().isEmpty();
        if (type instanceof TdApi.InlineKeyboardButtonTypeUrl) invalid[0] |= ((TdApi.InlineKeyboardButtonTypeUrl) type).url.trim().isEmpty();
        if (type instanceof TdApi.InlineKeyboardButtonTypeCopyText) invalid[0] |= ((TdApi.InlineKeyboardButtonTypeCopyText) type).text.isEmpty();
      }
    });
    if (readOnly[0]) return Problem.READ_ONLY;
    if (invalid[0]) return Problem.STRUCTURE;
    if (total[0] == 0 && total[2] == 0) return Problem.EMPTY;
    if (total[0] > limits.text) return Problem.TEXT_LENGTH;
    if (total[1] > limits.blocks) return Problem.BLOCK_COUNT;
    if (total[2] > limits.media) return Problem.MEDIA_COUNT;
    if (total[3] > limits.columns) return Problem.TABLE_COLUMNS;
    for (ArticleEditorTree.Entry entry : ArticleEditorTree.entries(input)) {
      if (entry.depth + 1 > limits.depth) return Problem.DEPTH;
      for (ArticleEditorTree.TextField field : ArticleEditorTree.fields(entry.block)) {
        if (richDepth(field.value, 0) > limits.depth) return Problem.DEPTH;
      }
    }
    return null;
  }
  private static boolean isGallery (TdApi.InputPageBlock[] blocks) {
    if (blocks.length == 0) return false;
    for (TdApi.InputPageBlock block : blocks) if (!(block instanceof TdApi.InputPageBlockPhoto || block instanceof TdApi.InputPageBlockVideo || block instanceof TdApi.InputPageBlockAnimation)) return false;
    return true;
  }
  /** Counts occupied columns across row spans without allocating from untrusted span sizes. */
  static int tableColumns (TdApi.PageBlockTableCell[][] rows, int maxColumns) {
    int[] occupiedUntil = new int[Math.max(1, Math.min(1000, maxColumns + 1))]; int maximum = 0;
    if (rows.length == 0) return -1;
    for (int r = 0; r < rows.length; r++) {
      int column = 0;
      for (TdApi.PageBlockTableCell cell : rows[r]) {
        if (cell.colspan < 0 || cell.rowspan < 0 || (long) r + Math.max(1, cell.rowspan) > rows.length) return -1;
        while (column < occupiedUntil.length && occupiedUntil[column] > r) column++;
        int span = Math.max(1, cell.colspan);
        if ((long) column + span > maxColumns || (long) column + span > occupiedUntil.length) return maxColumns + 1;
        for (int c = column; c < column + span; c++) {
          if (occupiedUntil[c] > r) return -1;
          occupiedUntil[c] = r + Math.max(1, cell.rowspan);
        }
        column += span; maximum = Math.max(maximum, column);
      }
    }
    return maximum == 0 ? -1 : maximum;
  }
  private static int richDepth (TdApi.RichText value, int depth) {
    if (value == null) return depth;
    if (depth > 128) return depth;
    int max = depth + 1;
    TdApi.RichText child = ArticleRichText.child(value);
    if (child != null) max = Math.max(max, richDepth(child, depth + 1));
    if (value instanceof TdApi.RichTexts) for (TdApi.RichText item : ((TdApi.RichTexts) value).texts) max = Math.max(max, richDepth(item, depth + 1));
    return max;
  }
}
