/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 25/02/2017
 */
package org.thunderdog.challegram.util;

import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.data.MediaWrapper;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.tool.Screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.collection.FloatList;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.util.LocalVar;

public class CollageContext {
  private static final int FLAG_LAST_ROW = 0x01;
  private static final int FLAG_LAST_COLUMN = 0x02;
  private static final int FLAG_SINGLE = 0x04;

  private static class CollageItem {
    public MediaWrapper wrapper;
    public int flags;
    public int x, y;

    public CollageItem (MediaWrapper wrapper) {
      this.wrapper = wrapper;
    }
  }

  private final ArrayList<CollageItem> items;

  private int maxWidth, maxHeight;
  private final int spacing;

  private int collageWidth;
  private int collageHeight;

  public CollageContext (@NonNull List<MediaWrapper> wrappers, int spacing) {
    this.items = new ArrayList<>(wrappers.size());
    for (MediaWrapper wrapper : wrappers) {
      this.items.add(new CollageItem(wrapper));
    }
    this.spacing = spacing;
  }

  public int getWidth () {
    return collageWidth;
  }

  public final void rebuild (@NonNull ArrayList<MediaWrapper> wrappers) {
    final int size = items.size();
    final int wrappersLength = wrappers.size();
    for (int i = 0; i < size && i < wrappersLength; i++) {
      items.get(i).wrapper = wrappers.get(i);
    }
    if (size > wrappersLength) {
      for (int i = size; i >= wrappersLength; i--) {
        items.remove(i);
      }
    } else {
      for (int i = size; i< wrappersLength; i++) {
        items.add(new CollageItem(wrappers.get(i)));
      }
    }

    if (maxWidth != 0 && maxHeight != 0) {
      int maxWidth = this.maxWidth;
      int maxHeight = this.maxHeight;
      this.maxWidth = this.maxHeight = 0;
      getHeight(maxWidth, maxHeight);
    }
  }

  public final int getCachedHeight () {
    return collageHeight;
  }

  public final int getHeight (int maxWidth, int maxHeight) {
    if ((maxWidth > 0 && maxHeight > 0) && (this.maxWidth != maxWidth || this.maxHeight != maxHeight)) {
      this.maxWidth = maxWidth;
      this.maxHeight = maxHeight;
      buildCollage(maxWidth, maxHeight);
    }
    return collageHeight;
  }

  public final void requestFiles (ComplexReceiver multipleReceiver, boolean invalidate) {
    multipleReceiver.clearReceiversWithHigherKey(items.size());
    int i = 0;
    for (CollageItem item : items) {
      if (!invalidate) {
        item.wrapper.requestPreview(multipleReceiver.getPreviewReceiver(i));
      }
      if (item.wrapper.needGif()) {
        item.wrapper.requestGif(multipleReceiver.getGifReceiver(i));
      } else {
        item.wrapper.requestImage(multipleReceiver.getImageReceiver(i));
      }
      i++;
    }

  }

  public final void autoDownloadContent () {
    for (CollageItem item : items) {
      item.wrapper.getFileProgress().downloadAutomatically();
    }
  }

  public final boolean onTouchEvent (View view, MotionEvent e, int startX, int startY) {
    for (CollageItem item : items) {
      if (item.wrapper.onTouchEvent(view, e)) {
        return true;
      }
    }
    return false;
  }

  public final <T extends View & DrawableProvider> void draw (T view, Canvas c, int startX, int startY, ComplexReceiver multipleReceiver) {
    int i = 0;
    for (CollageItem item : items) {
      item.wrapper.draw(view, c, startX + item.x, startY + item.y, multipleReceiver.getPreviewReceiver(i), multipleReceiver.getReceiver(i, item.wrapper.needGif()), 1f);
      i++;
    }
  }

  // Computation

  private static @Nullable
  LocalVar<IntList> orients_local;
  private static @Nullable LocalVar<int[]> orients_count_local;
  private static @Nullable LocalVar<FloatList> ratios_local;

  private static final int ORIENT_W = 0;
  private static final int ORIENT_V = 1;
  private static final int ORIENT_Q = 2;

  private void buildCollage (int max_w, int max_h) {
    if (orients_local == null) {
      synchronized (CollageContext.class) {
        if (orients_local == null) {
          orients_local = new LocalVar<>();
          orients_count_local = new LocalVar<>();
          ratios_local = new LocalVar<>();
        }
      }
    }
    IntList orientsData = orients_local.get();
    FloatList ratiosData = ratios_local.get();
    if (orientsData == null) {
      orientsData = new IntList(10);
      orients_local.set(orientsData);
    } else {
      orientsData.clear();
    }
    if (ratiosData == null) {
      ratiosData = new FloatList(10);
      ratios_local.set(ratiosData);
    } else {
      ratiosData.clear();
    }

    int[] orients_count = U.reuseLocalInts(orients_count_local, 4);
    final int cnt = items.size();

    for (CollageItem item : items) {
      MediaWrapper wrapper = item.wrapper;
      float ratio = (float) wrapper.getContentWidth() / (float) wrapper.getContentHeight();
      int orient = ratio > 1.2f ? ORIENT_W : ratio < 0.8f ? ORIENT_V : ORIENT_Q;
      orientsData.append(orient);
      orients_count[orient]++;
      ratiosData.append(ratio);
    }

    int thumbs_width, thumbs_height;

    final float avg_ratio = ratiosData.isEmpty() ? 1f : ratiosData.sum() / (float) ratiosData.size();
    final float max_ratio = (float) max_w / (float) max_h;
    final int margin_w = spacing, margin_h = spacing;

    final float[] ratios = ratiosData.get();
    final int[] orients = orientsData.get();

    if (cnt == 1) {
      if (ratios[0] >= max_ratio) {
        thumbs_width = max_w;
        thumbs_height = (int) Math.min((float) thumbs_width / ratios[0], max_h);
      } else {
        thumbs_height = max_h;
        thumbs_width = (int) Math.min((float) thumbs_height * ratios[0], max_w);

      }
      processMediaThumb(0, thumbs_width, thumbs_height, FLAG_LAST_COLUMN | FLAG_LAST_ROW | FLAG_SINGLE, 0, 0, true);
    } else if (cnt == 2) {
      if (orients[0] == ORIENT_W && orients[1] == ORIENT_W && avg_ratio > 1.4f * max_ratio && (ratios[1] - ratios[0]) < .2f) { // 2 wide pics are one below the other
        final int w = max_w;
        final int h = (int) Math.min(Math.min((float) w / ratios[0], (float) w / ratios[1]), (float) (max_h - margin_h) / 2f);

        processMediaThumb(0, w, h, FLAG_LAST_COLUMN, 0, 0, false);
        processMediaThumb(1, w, h, FLAG_LAST_COLUMN | FLAG_LAST_ROW, 0, h + margin_h, false);

        thumbs_width = max_w;
        thumbs_height = h + h + margin_h;
      } else if ((orients[0] == ORIENT_V || orients[0] == ORIENT_Q) && (orients[1] == ORIENT_V || orients[1] == ORIENT_Q)) { // 2 equal width pic
        int w = (max_w - margin_w) / 2;
        int h = (int) Math.min(Math.min((float) w / ratios[0], (float) w / ratios[1]), max_h);
        processMediaThumb(0, w, h, FLAG_LAST_ROW, 0, 0, false);
        processMediaThumb(1, w, h, FLAG_LAST_ROW | FLAG_LAST_COLUMN, w + margin_w, 0, false);

        thumbs_width = max_w;
        thumbs_height = h;
      } else { // so, we have one wide and one not wide (square or narrow)

        int w0 = (int) ((float) (max_w - margin_w) / ratios[1] / (1 / ratios[0] + 1 / ratios[1]));
        int w1 = max_w - w0 - margin_w;
        int h = (int) Math.min(Math.min(max_w, (float) w0 / ratios[0]), (float) w1 / ratios[1]);

        processMediaThumb(0, w0, h, FLAG_LAST_ROW, 0, 0, false);
        processMediaThumb(1, w1, h, FLAG_LAST_ROW | FLAG_LAST_COLUMN, w0 + margin_w, 0, false);

        thumbs_width = max_w;
        thumbs_height = h;
      }
    } else if (cnt == 3) {
      if ((ratios[0] > 1.2f * max_ratio || avg_ratio > 1.5f * max_ratio) && orients[0] == ORIENT_W && orients[1] == ORIENT_W && orients[2] == ORIENT_W) { // 2nd and 3rd photos are on the next line
        int w = max_w;
        int h_cover = (int) Math.min((float) w / ratios[0], (float) (max_h - margin_h) * 0.66f);

        processMediaThumb(0, w, h_cover, FLAG_LAST_COLUMN, 0, 0, false);

        int h;
        if (orients[0] == ORIENT_W && orients[1] == ORIENT_W && orients[2] == ORIENT_W) {
          w = (max_w - margin_w) / 2;
          h = (int) Math.min(Math.min(max_h - h_cover - margin_h, (float) w / ratios[1]), (float) w / ratios[2]);
          processMediaThumb(1, w, h, FLAG_LAST_ROW, 0, h_cover + margin_h, false);
          processMediaThumb(2, max_w - w - margin_w, h, FLAG_LAST_ROW | FLAG_LAST_COLUMN, w + margin_w, h_cover + margin_h, false);
        } else {
          int w0 = (int) (((float) (max_w - margin_w) / ratios[2]) / (1f / ratios[1] + 1f / ratios[2]));
          int w1 = max_w - w0 - margin_w;
          h = (int) Math.min(Math.min(max_h - h_cover - margin_h, (float) w0 / ratios[2]), (float) w1 / ratios[1]);

          processMediaThumb(1, w0, h, FLAG_LAST_ROW, 0, h_cover + margin_h, false);
          processMediaThumb(2, w1, h, FLAG_LAST_ROW | FLAG_LAST_COLUMN, w0 + margin_w, h_cover + margin_h, false);
        }

        thumbs_width = max_w;
        thumbs_height = h_cover + h + margin_h;
      } else { // 2nd and 3rd photos are on the right part
        int h = max_h;
        int w_cover = (int) (Math.min((float) h * ratios[0], (float) (max_w - margin_w) * 0.75f));
        processMediaThumb(0, w_cover, h, FLAG_LAST_ROW, 0, 0, false);

        int h1 = (int) (ratios[1] * (float) (max_h - margin_h) / (ratios[2] + ratios[1]));
        int h0 = max_h - h1 - margin_h;
        int w = (int) Math.min(Math.min(max_w - w_cover - margin_w, (float) h1 * ratios[2]), (float) h0 * ratios[1]);

        processMediaThumb(1, w, h0, FLAG_LAST_COLUMN, w_cover + margin_w, 0, false);
        processMediaThumb(2, w, h1, FLAG_LAST_COLUMN | FLAG_LAST_ROW, w_cover + margin_w, h0 + margin_h, false);

        thumbs_width = w_cover + w + margin_w;
        thumbs_height = max_h;
      }
    } else if (cnt == 4) {
      if ((ratios[0] > 1.2f * max_ratio || avg_ratio > 1.5f * max_ratio) && orients[0] == ORIENT_W && orients[1] == ORIENT_W && orients[2] == ORIENT_W && orients[3] == ORIENT_W) { // 2nd, 3rd and 4th photos are on the next line
        int w = max_w;
        int h_cover = (int) Math.min((float) w / ratios[0], (float) (max_h - margin_h) * 0.66f);
        processMediaThumb(0, w, h_cover, FLAG_LAST_COLUMN, 0, 0, false);

        int h = (int) ((float) (max_w - 2 * margin_w) / (ratios[1] + ratios[2] + ratios[3]));
        int w0 = (int) ((float) h * ratios[1]);
        int w1 = (int) (h * ratios[2]);
        int w2 = w - w0 - w1 -  (2 * margin_w);
        h = Math.min(max_h - h_cover - margin_h, h);

        processMediaThumb(1, w0, h, FLAG_LAST_ROW, 0, h_cover + margin_h, false);
        processMediaThumb(2, w1, h, FLAG_LAST_ROW, w0 + margin_w, h_cover + margin_h, false);
        processMediaThumb(3, w2, h, FLAG_LAST_ROW | FLAG_LAST_COLUMN, w0 + margin_w + w1 + margin_w, h_cover + margin_h, false);

        thumbs_width = max_w;
        thumbs_height = h_cover + h + margin_h;
      } else { // 2nd, 3rd and 4th photos are on the right part
        int h = max_h;
        int w_cover = (int) Math.min((float) h * ratios[0], (float) (max_w - margin_w) * 0.66f);
        processMediaThumb(0, w_cover, h, FLAG_LAST_ROW, 0, 0, false);

        int w = (int) ((float) (max_h - 2 * margin_h) / (1f / ratios[1] + 1f / ratios[2] + 1f / ratios[3]));
        int h0 = (int) ((float) w / ratios[1]);
        int h1 = (int) ((float) w / ratios[2]);
        int h2 = h - h0 - h1 - (2 * margin_h);
        w = Math.min(max_w - w_cover - margin_w, w);

        processMediaThumb(1, w, h0, FLAG_LAST_COLUMN, w_cover + margin_w, 0, false);
        processMediaThumb(2, w, h1, FLAG_LAST_COLUMN, w_cover + margin_w, h0 + margin_h, false);
        processMediaThumb(3, w, h2, FLAG_LAST_COLUMN | FLAG_LAST_ROW, w_cover + margin_w, h0 + margin_h + h1 + margin_h, false);

        thumbs_width = w_cover + w + margin_w;
        thumbs_height = max_h;
      }
    } else {
      // TODO better

      float[] ratios_cropped = new float[ratios.length];
      if (avg_ratio > 1.1) {
        int i = 0;
        for (float ratio : ratios) {
          ratios_cropped[i++] = Math.max(1f, ratio);
        }
      } else {
        int i = 0;
        for (float ratio : ratios) {
          ratios_cropped[i++] = Math.min(1f, ratio);
        }
      }

      HashMap<int[], int[]> tries = new HashMap<>();

      int first_line, second_line, third_line;

      tries.put(new int[] {first_line = cnt}, new int[] {calculateMultiThumbsHeight(ratios_cropped, max_w, margin_w)});

      for (first_line = 1; first_line <= cnt - 1; first_line++) {
        tries.put(new int[] {first_line, (second_line = cnt - first_line)}, new int[] {
          calculateMultiThumbsHeight(ratios_cropped, 0, first_line, max_w, margin_w),
          calculateMultiThumbsHeight(ratios_cropped, first_line, ratios_cropped.length, max_w, margin_w)
        });
      }

      for (first_line = 1; first_line <= cnt - 2; first_line++) {
        for (second_line = 1; second_line <= cnt - first_line - 1; second_line++) {
          tries.put(new int[] {first_line, second_line, (third_line = cnt - first_line - second_line)}, new int[] {
            calculateMultiThumbsHeight(ratios_cropped, 0, first_line, max_w, margin_w),
            calculateMultiThumbsHeight(ratios_cropped, first_line, first_line + second_line, max_w, margin_w),
            calculateMultiThumbsHeight(ratios_cropped, first_line + second_line, ratios_cropped.length, max_w, margin_w)
          });
        }
      }

      // Looking for minimum difference between thumbs block height and max_h (may probably be little over)
      int[] opt_conf = null;
      int opt_diff = 0;
      int opt_h = 0;
      int min_w_num = 0;
      for (Map.Entry<int[], int[]> entry : tries.entrySet()) {
        int[] conf = entry.getKey();
        int[] heights = entry.getValue();

        int conf_h = ArrayUtils.sum(heights) + margin_h * (heights.length - 1);
        int conf_diff = Math.abs(conf_h - max_h);
        if (conf.length > 1) {
          if (conf[0] > conf[1] ||
            (conf.length > 2 && conf[1] > conf[2])) {
            conf_diff += Screen.dp(50f);
            conf_diff *= 1.5f;
          }
          if (min_w_num != 0 && ArrayUtils.min(conf) < min_w_num) {
            conf_diff += Screen.dp(100f);
            conf_diff *= 3;
          }
        }
        if (opt_conf == null || conf_diff < opt_diff) {
          opt_conf = conf;
          opt_diff = conf_diff;
          opt_h = conf_h;
        }
      }

      if (opt_conf == null) {
        throw new NullPointerException("opt_conf == null");
      }

      // Generating optimal UI
      int remain = 0;
      int[] chunks = opt_conf;
      int[] opt_heights = tries.get(opt_conf);
      int last_row = chunks.length - 1;
      int cy = 0;
      int i = 0;
      for (int line_chunks_num : chunks) {
        int line_height = opt_heights[i];
        int last_column = line_chunks_num - 1;
        int flags = 0;
        if (last_row == i) {
          flags |= FLAG_LAST_ROW;
        }
        int width_remains = max_w;
        int cx = 0;
        for (int j = 0; j < line_chunks_num; j++) {
          float thumb_ratio = ratios_cropped[remain];
          int thumb_flags = flags;
          int thumb_width;
          if (last_column == j) {
            thumb_width = width_remains;
            thumb_flags |= FLAG_LAST_COLUMN;
          } else {
            thumb_width = (int) (thumb_ratio * (float) line_height);
            width_remains -= thumb_width + margin_w;
          }
          processMediaThumb(remain, thumb_width, line_height, thumb_flags, cx, cy, false);
          cx += thumb_width + margin_w;
          remain++;
        }
        cy += line_height + margin_h;
        i++;
      }
      thumbs_width = max_w;
      thumbs_height = opt_h;
    }

    this.collageWidth = thumbs_width;
    this.collageHeight = thumbs_height;
  }

  private static int calculateMultiThumbsHeight (float[] ratios, int width, int margin) {
    return calculateMultiThumbsHeight(ratios, 0, ratios.length, width, margin);
  }

  private static int calculateMultiThumbsHeight (float[] ratios, int start, int end, int width, int margin) {
    float sum = 0;
    for (int i = start; i < end; i++) {
      sum += ratios[i];
    }
    return (int) ((float) (width - (end - start - 1) * margin) / sum);
  }

  private void processMediaThumb (int index, int w, int h, int flags, int x, int y, boolean set) {
    CollageItem item = items.get(index);
    item.flags = flags;
    item.wrapper.buildContent(w, h);
    item.x = x;
    item.y = y;
  }
}
