/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import org.drinkless.tdlib.TdApi;

/** Checkbox positions follow the document walk, including nested lists and closed details. */
public final class ArticleCheckbox {
  private ArticleCheckbox () { }
  public static ArticleDocument toggle (ArticleDocument document, int position) {
    TdApi.InputRichMessage input = document.toInput(); int[] index = {0}; boolean[] found = {false};
    ArticleCodec.visit(input, (value, depth) -> {
      if (value instanceof TdApi.InputPageBlockListItem && index[0]++ == position) {
        TdApi.InputPageBlockListItem item = (TdApi.InputPageBlockListItem) value;
        if (!item.hasCheckbox) throw new IllegalArgumentException("This list item has no checkbox");
        item.isChecked = !item.isChecked; found[0] = true;
      }
    });
    if (!found[0]) throw new IllegalArgumentException("Checkbox not found");
    return new ArticleDocument(input);
  }
}
