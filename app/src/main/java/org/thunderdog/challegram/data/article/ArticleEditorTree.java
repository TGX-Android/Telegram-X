/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import org.drinkless.tdlib.TdApi;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Mutable editing handles into one detached working document, including nested containers. */
public final class ArticleEditorTree {
  private ArticleEditorTree () { }

  public static final class Group {
    private final Supplier<TdApi.InputPageBlock[]> getter;
    private final Consumer<TdApi.InputPageBlock[]> setter;
    public Group (Supplier<TdApi.InputPageBlock[]> getter, Consumer<TdApi.InputPageBlock[]> setter) { this.getter = getter; this.setter = setter; }
    public TdApi.InputPageBlock[] blocks () { return getter.get(); }
    public void replace (int index, TdApi.InputPageBlock block) { TdApi.InputPageBlock[] result = blocks().clone(); result[index] = block; setter.accept(result); }
    public void insert (int index, TdApi.InputPageBlock block) {
      List<TdApi.InputPageBlock> result = new ArrayList<>(Arrays.asList(blocks()));
      result.add(index, block); setter.accept(result.toArray(new TdApi.InputPageBlock[0]));
    }
    public void remove (int index) {
      List<TdApi.InputPageBlock> result = new ArrayList<>(Arrays.asList(blocks()));
      result.remove(index); setter.accept(result.toArray(new TdApi.InputPageBlock[0]));
    }
    public void move (int index, int delta) {
      TdApi.InputPageBlock[] result = blocks().clone();
      int target = index + delta;
      if (target < 0 || target >= result.length) return;
      TdApi.InputPageBlock block = result[index]; result[index] = result[target]; result[target] = block;
      setter.accept(result);
    }
  }

  public static final class Entry {
    public final Group group;
    public final int index, depth;
    public final TdApi.InputPageBlock block;
    public Entry (Group group, int index, int depth) { this.group = group; this.index = index; this.depth = depth; block = group.blocks()[index]; }
  }
  public enum FieldName { TEXT, CREDIT, CAPTION, HEADER, CELL, BUTTON }
  public static final class TextField {
    public final FieldName name;
    public final TdApi.RichText value;
    public final Consumer<TdApi.RichText> set;
    TextField (FieldName name, TdApi.RichText value, Consumer<TdApi.RichText> set) { this.name = name; this.value = value; this.set = set; }
  }
  public static Group root (TdApi.InputRichMessage message) {
    TdApi.RichMessageSourceBlocks source = (TdApi.RichMessageSourceBlocks) message.source;
    return new Group(() -> source.blocks, value -> source.blocks = value);
  }
  public static List<Entry> entries (TdApi.InputRichMessage message) {
    List<Entry> result = new ArrayList<>();
    collect(root(message), 0, result);
    return result;
  }
  private static void collect (Group group, int depth, List<Entry> out) {
    if (depth > 32) throw new IllegalArgumentException("Article nesting is too deep");
    for (int i = 0; i < group.blocks().length; i++) {
      Entry entry = new Entry(group, i, depth);
      out.add(entry);
      for (Group child : children(entry.block)) collect(child, depth + 1, out);
    }
  }
  public static List<Group> children (TdApi.InputPageBlock block) {
    List<Group> out = new ArrayList<>();
    switch (block.getConstructor()) {
      case TdApi.InputPageBlockBlockQuote.CONSTRUCTOR: {
        TdApi.InputPageBlockBlockQuote b = (TdApi.InputPageBlockBlockQuote) block; out.add(new Group(() -> b.blocks, value -> b.blocks = value)); break;
      }
      case TdApi.InputPageBlockDetails.CONSTRUCTOR: {
        TdApi.InputPageBlockDetails b = (TdApi.InputPageBlockDetails) block; out.add(new Group(() -> b.blocks, value -> b.blocks = value)); break;
      }
      case TdApi.InputPageBlockCollage.CONSTRUCTOR: {
        TdApi.InputPageBlockCollage b = (TdApi.InputPageBlockCollage) block; out.add(new Group(() -> b.blocks, value -> b.blocks = value)); break;
      }
      case TdApi.InputPageBlockSlideshow.CONSTRUCTOR: {
        TdApi.InputPageBlockSlideshow b = (TdApi.InputPageBlockSlideshow) block; out.add(new Group(() -> b.blocks, value -> b.blocks = value)); break;
      }
      case TdApi.InputPageBlockList.CONSTRUCTOR: {
        for (TdApi.InputPageBlockListItem item : ((TdApi.InputPageBlockList) block).items) out.add(new Group(() -> item.blocks, value -> item.blocks = value));
        break;
      }
    }
    return out;
  }

  public static List<TextField> fields (TdApi.InputPageBlock block) {
    List<TextField> out = new ArrayList<>();
    switch (block.getConstructor()) {
      case TdApi.InputPageBlockParagraph.CONSTRUCTOR: { TdApi.InputPageBlockParagraph b = (TdApi.InputPageBlockParagraph) block; out.add(new TextField(FieldName.TEXT, b.text, value -> b.text = value)); break; }
      case TdApi.InputPageBlockSectionHeading.CONSTRUCTOR: { TdApi.InputPageBlockSectionHeading b = (TdApi.InputPageBlockSectionHeading) block; out.add(new TextField(FieldName.TEXT, b.text, value -> b.text = value)); break; }
      case TdApi.InputPageBlockPreformatted.CONSTRUCTOR: { TdApi.InputPageBlockPreformatted b = (TdApi.InputPageBlockPreformatted) block; out.add(new TextField(FieldName.TEXT, b.text, value -> b.text = value)); break; }
      case TdApi.InputPageBlockFooter.CONSTRUCTOR: { TdApi.InputPageBlockFooter b = (TdApi.InputPageBlockFooter) block; out.add(new TextField(FieldName.TEXT, b.footer, value -> b.footer = value)); break; }
      case TdApi.InputPageBlockBlockQuote.CONSTRUCTOR: { TdApi.InputPageBlockBlockQuote b = (TdApi.InputPageBlockBlockQuote) block; out.add(new TextField(FieldName.CREDIT, b.credit, value -> b.credit = value)); break; }
      case TdApi.InputPageBlockExpandableBlockQuote.CONSTRUCTOR: { TdApi.InputPageBlockExpandableBlockQuote b = (TdApi.InputPageBlockExpandableBlockQuote) block; out.add(new TextField(FieldName.TEXT, b.text, value -> b.text = value)); out.add(new TextField(FieldName.CREDIT, b.credit, value -> b.credit = value)); break; }
      case TdApi.InputPageBlockPullQuote.CONSTRUCTOR: { TdApi.InputPageBlockPullQuote b = (TdApi.InputPageBlockPullQuote) block; out.add(new TextField(FieldName.TEXT, b.text, value -> b.text = value)); out.add(new TextField(FieldName.CREDIT, b.credit, value -> b.credit = value)); break; }
      case TdApi.InputPageBlockDetails.CONSTRUCTOR: { TdApi.InputPageBlockDetails b = (TdApi.InputPageBlockDetails) block; out.add(new TextField(FieldName.HEADER, b.header, value -> b.header = value)); break; }
      case TdApi.InputPageBlockTable.CONSTRUCTOR: {
        TdApi.InputPageBlockTable b = (TdApi.InputPageBlockTable) block;
        out.add(new TextField(FieldName.CAPTION, b.caption, value -> b.caption = value));
        for (TdApi.PageBlockTableCell[] row : b.cells) for (TdApi.PageBlockTableCell cell : row) out.add(new TextField(FieldName.CELL, cell.text, value -> cell.text = value));
        break;
      }
      case TdApi.InputPageBlockButtonRow.CONSTRUCTOR: {
        for (TdApi.InlineButton button : ((TdApi.InputPageBlockButtonRow) block).buttons) out.add(new TextField(FieldName.BUTTON, button.text, value -> button.text = value));
        break;
      }
    }
    TdApi.PageBlockCaption caption = caption(block);
    if (caption != null) {
      out.add(new TextField(FieldName.CAPTION, caption.text, value -> caption.text = value));
      out.add(new TextField(FieldName.CREDIT, caption.credit, value -> caption.credit = value));
    }
    return out;
  }

  public static TdApi.PageBlockCaption caption (TdApi.InputPageBlock block) {
    switch (block.getConstructor()) {
      case TdApi.InputPageBlockPhoto.CONSTRUCTOR: return ((TdApi.InputPageBlockPhoto) block).caption;
      case TdApi.InputPageBlockVideo.CONSTRUCTOR: return ((TdApi.InputPageBlockVideo) block).caption;
      case TdApi.InputPageBlockAnimation.CONSTRUCTOR: return ((TdApi.InputPageBlockAnimation) block).caption;
      case TdApi.InputPageBlockAudio.CONSTRUCTOR: return ((TdApi.InputPageBlockAudio) block).caption;
      case TdApi.InputPageBlockDocument.CONSTRUCTOR: return ((TdApi.InputPageBlockDocument) block).caption;
      case TdApi.InputPageBlockVoiceNote.CONSTRUCTOR: return ((TdApi.InputPageBlockVoiceNote) block).caption;
      case TdApi.InputPageBlockMap.CONSTRUCTOR: return ((TdApi.InputPageBlockMap) block).caption;
      case TdApi.InputPageBlockCollage.CONSTRUCTOR: return ((TdApi.InputPageBlockCollage) block).caption;
      case TdApi.InputPageBlockSlideshow.CONSTRUCTOR: return ((TdApi.InputPageBlockSlideshow) block).caption;
      default: return null;
    }
  }
}
