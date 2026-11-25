/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 26/10/2026
 */

package org.thunderdog.challegram.util.text.quotes;

import android.graphics.Canvas;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.graphics.RectF;
import android.os.Build;

import androidx.annotation.RequiresApi;

@RequiresApi(Build.VERSION_CODES.Q)
class CodeBlockRenderNode {
  private final RenderNode node = new RenderNode("codeBlock");

  Canvas beginRecording (RectF bounds) {
    node.setPosition((int) bounds.left, (int) bounds.top, (int) bounds.right, (int) bounds.bottom);
    RecordingCanvas canvas = node.beginRecording();
    canvas.translate(-(int) bounds.left, -(int) bounds.top);
    return canvas;
  }

  void endRecording (Canvas c) {
    node.endRecording();
    c.drawRenderNode(node);
  }
}
