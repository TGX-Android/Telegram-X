/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import org.drinkless.tdlib.TdApi;
import java.util.ArrayList;
import java.util.List;

/** Reassembles editor spans without splitting one button or link into multiple semantic nodes. */
public final class ArticleTextBuilder {
  private static final class Frame {
    final TdApi.RichText wrapper;
    final ArrayList<TdApi.RichText> children = new ArrayList<>();
    Frame (TdApi.RichText wrapper) { this.wrapper = wrapper; }
  }
  private final ArrayList<Frame> stack = new ArrayList<>();
  public ArticleTextBuilder () { stack.add(new Frame(null)); }

  public void append (List<TdApi.RichText> wrappers, TdApi.RichText leaf) {
    int common = 0;
    while (common < wrappers.size() && common + 1 < stack.size() && wrappers.get(common) == stack.get(common + 1).wrapper) common++;
    while (stack.size() > common + 1) close();
    for (int i = common; i < wrappers.size(); i++) stack.add(new Frame(wrappers.get(i)));
    stack.get(stack.size() - 1).children.add(leaf);
  }
  private void close () {
    Frame frame = stack.remove(stack.size() - 1);
    stack.get(stack.size() - 1).children.add(ArticleRichText.withChild(frame.wrapper, concatenate(frame.children)));
  }
  private static TdApi.RichText concatenate (List<TdApi.RichText> children) {
    return children.isEmpty() ? new TdApi.RichTextPlain("") : children.size() == 1 ? children.get(0) : new TdApi.RichTexts(children.toArray(new TdApi.RichText[0]));
  }
  public TdApi.RichText build () {
    while (stack.size() > 1) close();
    return concatenate(stack.get(0).children);
  }
}
