/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 11/04/2017
 */
package org.thunderdog.challegram.mediaview.paint;

import android.graphics.Canvas;
import android.util.Base64;

import androidx.collection.SparseArrayCompat;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.io.File;
import java.io.RandomAccessFile;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;
import me.vkryl.core.reference.ReferenceUtils;
import me.vkryl.core.util.Blob;

public class PaintState {
  public interface SimpleDrawingChangeListener {
    void onSimpleDrawingsChanged (PaintState state);
  }

  public interface UndoStateListener {
    void onUndoAvailableStateChanged (PaintState state, boolean isAvailable, int totalActionsCount);
  }

  private List<SimpleDrawing> drawingsList;
  private List<PaintAction> paintActions;

  private List<Reference<SimpleDrawingChangeListener>> simpleDrawingListeners;
  private List<Reference<UndoStateListener>> undoStateListeners;

  public PaintState () { }

  public PaintState (PaintState copy) {
    if (copy.drawingsList != null && !copy.drawingsList.isEmpty()) {
      this.drawingsList = new ArrayList<>(copy.drawingsList.size());
      this.drawingsList.addAll(copy.drawingsList);
    }
    if (copy.paintActions != null && !copy.paintActions.isEmpty()) {
      this.paintActions = new ArrayList<>(copy.paintActions.size());
      this.paintActions.addAll(copy.paintActions);
    }
  }

  // TODO save & restore paint

  private static SparseArrayCompat<PaintState> pendingPaints;

  public static void putPaintState (int paintId, PaintState paintState) {
    synchronized (PaintState.class) {
      if (pendingPaints == null) {
        pendingPaints = new SparseArrayCompat<>();
      }
      pendingPaints.put(paintId, paintState);
    }
  }

  public static PaintState obtainPaintState (int paintId) {
    synchronized (PaintState.class) {
      if (pendingPaints == null) {
        return null;
      }
      int i = pendingPaints.indexOfKey(paintId);
      if (i >= 0) {
        PaintState painting = pendingPaints.valueAt(i);
        pendingPaints.removeAt(i);
        return painting;
      }
      return null;
    }
  }

  public static File getPaintsDir () {
    return new File(UI.getAppContext().getFilesDir(), "paints");
  }

  public static PaintState parse (String in) {
    if (StringUtils.isEmpty(in)) {
      return null;
    }
    byte[] data;
    try {
      data = Base64.decode(in, Base64.NO_PADDING);
    } catch (Throwable t) {
      Log.e("Unable to decode painting", t);
      return null;
    }
    if (data == null || data.length == 0) {
      Log.w("Decoded painting is null");
      return null;
    }
    try {
      Blob blob = new Blob(data);
      int paintId = blob.readVarint();
      if (paintId != 0) {
        PaintState cachedState = obtainPaintState(paintId);
        if (cachedState == null) {
          File dir = getPaintsDir();
          if (dir.exists()) {
            File file = new File(dir, paintId + ".bin");
            if (file.exists()) {
              PaintState state;
              RandomAccessFile f = new RandomAccessFile(file, "r");
              try {
                int count = Blob.readVarint(f);
                final ArrayList<SimpleDrawing> drawings = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                  SimpleDrawing drawing = SimpleDrawing.restore(f);
                  drawings.add(drawing);
                }
                state = new PaintState();
                state.drawingsList = drawings;
              } catch (Throwable t) {
                try { f.close(); } catch (Throwable ignored) { }
                Log.w("Unable to read paint file", t);
                throw t;
              }
              try { f.close(); } catch (Throwable ignored) { }
              return state;
            }
          }
        }
        if (cachedState == null) {
          throw new IllegalStateException("Unable to find paints/" + paintId + ".bin");
        }
        return cachedState;
      }

      int count = blob.readVarint();
      if (count <= 0) {
        Log.w("Decoded painting count is empty: %d", count);
        return null;
      }
      final ArrayList<SimpleDrawing> drawings = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        SimpleDrawing drawing = SimpleDrawing.restore(blob);
        drawings.add(drawing);
      }
      PaintState state = new PaintState();
      state.drawingsList = drawings;
      return state;
    } catch (Throwable t) {
      Log.e("Unable to decode painting: %s", t, in);
    }
    return null;
  }

  public byte[] save () {
    int size = Blob.sizeOf(drawingsList.size());
    for (SimpleDrawing drawing : drawingsList) {
      size += drawing.getEstimatedOutputSize();
    }

    Blob b = null;

    if (size >= 256) {
      int paintId = Settings.instance().getPaintId();
      File dir = new File(UI.getAppContext().getFilesDir(), "paints");
      if (dir.exists() || dir.mkdir()) {
        File file;
        do {
          paintId++;
          Settings.instance().setPaintId(paintId);
          file = new File(dir, paintId + ".bin");
        } while (file.exists());

        b = new Blob(Blob.sizeOf(paintId));
        b.writeVarint(paintId);

        putPaintState(paintId, this);
        final File fileFinal = file;
        final int sizeFinal = size;

        boolean ok = false;
        try {
          ok = file.createNewFile();
        } catch (Throwable t) {
          Log.w("Cannot create %d.bin paint file", t, paintId);
        }
        if (ok) {
          Background.instance().post(() -> {
            RandomAccessFile f = null;
            try {
              f = new RandomAccessFile(fileFinal, "rw");
              f.setLength(sizeFinal);
              Blob.writeVarint(f, drawingsList.size());
              for (SimpleDrawing drawing : drawingsList) {
                drawing.save(f);
              }
            } catch (Throwable t) {
              Log.w("Cannot save paint file: %s", t, fileFinal.getName());
            }
            if (f != null) { try { f.close(); } catch (Throwable t) { Log.w(t); } }
          });
        }
      }
    }

    if (b != null) {
      return b.toByteArray();
    }

    b = new Blob(size);
    b.writeVarint(0);
    b.writeVarint(drawingsList.size());
    for (SimpleDrawing drawing : drawingsList) {
      drawing.save(b);
    }
    return b.toByteArray();
  }

  @Override
  public String toString () {
    if (isEmpty()) {
      return "";
    }
    byte[] data = save();
    return Base64.encodeToString(data, Base64.NO_PADDING);
  }

  // Listenrs

  public void addUndoStateListener (UndoStateListener listener) {
    if (this.undoStateListeners == null) {
      this.undoStateListeners = new ArrayList<>();
    }
    ReferenceUtils.addReference(undoStateListeners, listener);
  }

  public void removeUndoStateListener (UndoStateListener listener) {
    if (undoStateListeners != null) {
      ReferenceUtils.removeReference(undoStateListeners, listener);
    }
  }

  public void notifyUndoStateChanged () {
    if (undoStateListeners != null) {
      final int count = paintActions != null ? paintActions.size() : 0;
      final int size = undoStateListeners.size();
      for (int i = size - 1; i >= 0; i--) {
        UndoStateListener listener = undoStateListeners.get(i).get();
        if (listener != null) {
          listener.onUndoAvailableStateChanged(this, count > 0, count);
        } else {
          undoStateListeners.remove(i);
        }
      }
    }
  }

  public void addSimpleDrawingChangeListener (SimpleDrawingChangeListener listener) {
    if (this.simpleDrawingListeners == null) {
      this.simpleDrawingListeners = new ArrayList<>();
    }
    ReferenceUtils.addReference(simpleDrawingListeners, listener);
  }

  public void removeSimpleDrawingChangeListener (SimpleDrawingChangeListener listener) {
    if (simpleDrawingListeners != null) {
      ReferenceUtils.removeReference(simpleDrawingListeners, listener);
    }
  }

  public void notifySimpleDrawingChanged () {
    notifySimpleDrawingChanged(-1, -1, -1, -1);
  }

  public void notifySimpleDrawingChanged (int left, int top, int right, int bottom) {
    if (simpleDrawingListeners != null) {
      final int size = simpleDrawingListeners.size();
      for (int i = size - 1; i >= 0; i--) {
        SimpleDrawingChangeListener listener = simpleDrawingListeners.get(i).get();
        if (listener != null) {
          listener.onSimpleDrawingsChanged(this);
        } else {
          simpleDrawingListeners.remove(i);
        }
      }
    }
  }

  private void trackAction (PaintAction action) {
    if (paintActions == null) {
      paintActions = new ArrayList<>();
    }
    paintActions.add(action);
    notifyUndoStateChanged();
  }

  private PaintAction removeLastAction () {
    if (paintActions != null && !paintActions.isEmpty()) {
      return paintActions.remove(paintActions.size() - 1);
    }
    return null;
  }

  public void undoAllActions () {
    int drawingsBelowCount = drawingsList != null ? drawingsList.size() : 0;

    boolean hadDrawingsBelow = drawingsBelowCount > 0;

    final int size = paintActions.size();
    for (int i = size - 1; i >= 0; i--) {
      PaintAction action = paintActions.get(i);
      switch (action.getType()) {
        case PaintAction.SIMPLE_DRAWING:
          if (drawingsBelowCount > 0) {
            drawingsList.remove(--drawingsBelowCount);
          }
          break;
      }
      paintActions.remove(i);
    }

    if (hadDrawingsBelow) {
      notifySimpleDrawingChanged();
    }

    notifyUndoStateChanged();
  }

  public void removeSimpleDrawing (SimpleDrawing drawing) {
    if (!isEmpty()) {
      if (drawingsList.remove(drawing)) {
        notifySimpleDrawingChanged();
      }
    }
  }

  private void removeLastSimpleDrawing () {
    if (!isEmpty()) {
      drawingsList.remove(drawingsList.size() - 1);
      notifySimpleDrawingChanged();
    }
  }

  public void undoLastAction () {
    PaintAction action = removeLastAction();
    if (action != null) {
      switch (action.getType()) {
        case PaintAction.SIMPLE_DRAWING:
          removeLastSimpleDrawing();
          break;
      }
      notifyUndoStateChanged();
    }
  }

  // Simple drawings

  public void addSimpleDrawing (SimpleDrawing drawing) {
    if (drawingsList == null) {
      drawingsList = new ArrayList<>();
    }
    drawingsList.add(drawing);
    notifySimpleDrawingChanged();
  }

  public void trackSimpleDrawingAction (SimpleDrawing drawing) {
    trackAction(new PaintAction(PaintAction.SIMPLE_DRAWING, drawing));
  }

  // Impl

  private static boolean compareDrawings (List<SimpleDrawing> a, List<SimpleDrawing> b) {
    boolean aEmpty = a == null || a.isEmpty();
    boolean bEmpty = b == null || b.isEmpty();

    if (aEmpty != bEmpty) {
      return false;
    }

    if (aEmpty) {
      return true;
    }

    final int size = a.size();
    if (size != b.size()) {
      return false;
    }

    int i = 0;
    for (SimpleDrawing aDrawing : a) {
      SimpleDrawing bDrawing = b.get(i);
      if (!aDrawing.compare(bDrawing)) {
        return false;
      }
      i++;
    }

    return true;
  }

  public boolean compare (PaintState b) {
    if (b == null) {
      return false;
    }

    if (b == this) {
      return true;
    }

    if (isEmpty() != b.isEmpty()) {
      return false;
    }

    if (isEmpty()) {
      return true;
    }

    return compareDrawings(b.drawingsList, drawingsList);
  }

  // Getters

  public boolean isEmpty () {
    return drawingsList == null || drawingsList.isEmpty();
  }

  // Drawing

  public void draw (Canvas c, final int x, final int y, final int viewWidth, final int viewHeight) {
    if (drawingsList != null && !drawingsList.isEmpty()) {
      for (SimpleDrawing drawing : drawingsList) {
        drawing.draw(c, x, y, x + viewWidth, y + viewHeight);
      }
    }
  }
}
