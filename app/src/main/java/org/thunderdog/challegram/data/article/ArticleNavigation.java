/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import org.drinkless.tdlib.TdApi;
import java.util.ArrayList;

/** Opens only the enclosing details on a display copy, keeping the saved document unchanged. */
public final class ArticleNavigation {
  private ArticleNavigation () { }

  public static boolean revealAnchor (TdApi.RichMessage display, String name) {
    if (name == null) return false;
    ArrayList<TdApi.PageBlockDetails> parents = new ArrayList<>();
    ArrayList<Integer> depths = new ArrayList<>();
    boolean[] found = {false};
    ArticleCodec.visit(display, (value, depth) -> {
      if (found[0]) return;
      while (!depths.isEmpty() && depths.get(depths.size() - 1) >= depth) {
        depths.remove(depths.size() - 1); parents.remove(parents.size() - 1);
      }
      if (value instanceof TdApi.PageBlockDetails) {
        parents.add((TdApi.PageBlockDetails) value); depths.add(depth);
      }
      String anchor = value instanceof TdApi.PageBlockAnchor ? ((TdApi.PageBlockAnchor) value).name : value instanceof TdApi.RichTextAnchor ? ((TdApi.RichTextAnchor) value).name : null;
      if (name.equals(anchor)) {
        found[0] = true;
        for (TdApi.PageBlockDetails parent : parents) parent.isOpen = true;
      }
    });
    return found[0];
  }
}
