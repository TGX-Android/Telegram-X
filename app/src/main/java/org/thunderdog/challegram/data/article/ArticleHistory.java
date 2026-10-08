/* This file is a part of Telegram X. SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import java.util.ArrayList;

/** Bounded immutable history; a failed or duplicate edit never consumes an undo step. */
public final class ArticleHistory {
  private static final int MAX_STEPS = 64;
  private static final int MAX_BYTES = 8 * 1024 * 1024;
  private final ArrayList<ArticleDocument> states = new ArrayList<>();
  private final ArrayList<int[]> selections = new ArrayList<>();
  private int position;
  private int bytes;

  public ArticleHistory (ArticleDocument initial) {
    states.add(initial);
    selections.add(new int[] {-1, 0, 0, 0});
    bytes = initial.save().length;
  }

  public ArticleDocument current () { return states.get(position); }
  public void rememberSelection (int field, int start, int end, int scroll) { selections.set(position, new int[] {field, start, end, scroll}); }
  public int[] selection () { return selections.get(position).clone(); }
  public boolean canUndo () { return position > 0; }
  public boolean canRedo () { return position + 1 < states.size(); }
  public ArticleDocument undo () { if (canUndo()) position--; return current(); }
  public ArticleDocument redo () { if (canRedo()) position++; return current(); }

  public boolean push (ArticleDocument value) {
    if (value.equals(current())) return false;
    while (states.size() > position + 1) { bytes -= states.remove(states.size() - 1).save().length; selections.remove(selections.size() - 1); }
    states.add(value);
    selections.add(selection());
    bytes += value.save().length;
    position++;
    while (states.size() > 1 && (states.size() > MAX_STEPS || bytes > MAX_BYTES)) {
      bytes -= states.remove(0).save().length;
      selections.remove(0);
      position--;
    }
    return true;
  }
}
