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
 * File created on 25/11/2018
 */
package org.thunderdog.challegram.theme;

import android.graphics.Color;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ColorState {
  @ColorId
  private final int colorId;

  private int savedColor;

  private int stackIndex;
  private final List<StackColor> stack;

  private int[] savedStack;

  public ColorState (int themeId, int colorId, int savedColor, int defaultColor) {
    this.colorId = colorId;
    this.savedColor = savedColor;
    this.savedStack = Settings.instance().getColorHistory(ThemeManager.resolveCustomThemeId(themeId), colorId);
    int addLength = savedStack != null ? savedStack.length : 0;
    stack = new ArrayList<>(1 + addLength + 1);
    stack.add(new StackColor(defaultColor));
    if (addLength > 0) {
      for (int color : savedStack) {
        stack.add(new StackColor(color));
      }
    }
    int foundIndex = -1;
    for (int i = stack.size() - 1; i >= 0; i--) {
      if (stack.get(i).value == savedColor) {
        // stack.get(i).canModify = i < stack.size() - 1;
        foundIndex = i;
        break;
      }
    }
    if (foundIndex == -1) {
      foundIndex = stack.size();
      stack.add(new StackColor(savedColor));
    }
    this.stackIndex = foundIndex;
  }

  public int getVersionCount (boolean removeSaved) {
    int size = stack.size();
    if (removeSaved) {
      /*while (size > 0 && stackIndex < size - 1 && stack.get(size - 1).canModify)
        size--; // Remove last color in the stack, if it's temporary color
        // upd: I didn't like this behavior
       */
      if (savedColor == stack.get(size - 1).value)
        size--; // Remove last color in the stack, if it equals to the current one, since it can be restored
    }
    size--; // Ignore default color
    return Math.max(0, size);
  }

  public boolean saveStack (int[] stack) {
    if (!Arrays.equals(this.savedStack, stack)) {
      this.savedStack = stack;
      return true;
    }
    return false;
  }

  public boolean hasHistory () {
    return canUndo() || canRedo();
  }

  public @Nullable
  int[] getNewStack () {
    int size = getVersionCount(true);
    if (size <= 0)
      return null;
    Set<Integer> set = new LinkedHashSet<>(size); // Clean duplicated colors, since they have no use
    for (int i = 0; i < size; i++) {
      set.add(stack.get(i + 1).value);
    }
    int[] result = new int[set.size()];
    int i = 0;
    for (Integer color : set) {
      result[i] = color;
      i++;
    }
    return result;
  }

  public boolean isDefault () {
    return stackIndex == 0;
  }

  public int getColorId () {
    return colorId;
  }

  public int getColor () {
    return stack.get(stackIndex).value;
  }

  public float getHsv (int prop) {
    return stack.get(stackIndex).hsv[prop];
  }

  public float[] getHsv () {
    return stack.get(stackIndex).hsv;
  }

  public boolean setHsv (int prop, float val, boolean save) {
    float[] hsv = stack.get(stackIndex).hsv;
    float tempVal = hsv[prop];
    if (tempVal != val) {
      hsv[prop] = val;
      int hsvColor = Color.HSVToColor(Color.alpha(getColor()), hsv);
      hsv[prop] = tempVal;
      if (stack.get(stackIndex).canModify) {
        stack.get(stackIndex).setHsv(prop, val);
        if (stackIndex > 0 && stack.get(stackIndex - 1).equals(stack.get(stackIndex)) && !stack.get(stackIndex - 1).canModify) {
          stack.remove(stackIndex);
          stackIndex--;
        }
      } else {
        int foundIndex = -1;
        for (int i = stack.size() - 1; i >= 0; i--) {
          if (stack.get(i).canModify && stack.get(i).value == hsvColor) {
            foundIndex = i;
            break;
          }
        }
        if (foundIndex == -1 && stack.get(stack.size() - 1).canModify) {
          foundIndex = stack.size() - 1;
        }
        if (foundIndex != -1) {
          this.stackIndex = foundIndex;
          stack.get(foundIndex).setHsv(prop, val);
        } else {
          StackColor current;
          /*
          if (stackIndex > 0 && stackIndex < stack.size() - 1)
            stack.add(current = stack.remove(stackIndex));
          else
            current = stack.get(stackIndex);*/
          current = stack.get(stackIndex);
          stackIndex = stack.size();
          StackColor newColor = new StackColor(hsvColor, current);
          stack.add(newColor);
          newColor.setHsv(prop, val);
        }
      }
      if (save)
        this.savedColor = hsvColor;
      return true;
    }
    int color = getColor();
    if (save && this.savedColor != color) {
      this.savedColor = color;
      return true;
    }
    return false;
  }

  public boolean setCurrentColor (int color, boolean save) {
    if (getColor() != color) {
      if (stack.get(stackIndex).canModify) {
        stack.get(stackIndex).setValue(color);
        if (stackIndex > 0 && stack.get(stackIndex - 1).equals(stack.get(stackIndex)) && !stack.get(stackIndex - 1).canModify) {
          stack.remove(stackIndex);
          stackIndex--;
        }
      } else {
        int foundIndex = -1;
        for (int i = stack.size() - 1; i >= 0; i--) {
          if (stack.get(i).canModify && stack.get(i).value == color) {
            foundIndex = i;
            break;
          }
        }
        if (foundIndex == -1 && stack.get(stack.size() - 1).canModify) {
          foundIndex = stack.size() - 1;
        }
        if (foundIndex != -1) {
          this.stackIndex = foundIndex;
          stack.get(foundIndex).setValue(color);
        } else {
          StackColor current;
          /*if (stackIndex > 0 && stackIndex < stack.size() - 1)
            stack.add(current = stack.remove(stackIndex));
          else
            current = stack.get(stackIndex);*/
          current = stack.get(stackIndex);
          stackIndex = stack.size();
          stack.add(new StackColor(color, current));
        }
      }
      if (save)
        this.savedColor = color;
      return true;
    } else if (save && this.savedColor != color) {
      this.savedColor = color;
      return true;
    } else {
      return false;
    }
  }

  public boolean canUndo () {
    return stackIndex > 0;
  }

  public boolean canClear () {
    return getVersionCount(false) > 0;
  }

  public boolean canRedo () {
    return stackIndex < stack.size() - 1;
  }

  public boolean canSaveStack () {
    return stackIndex == stack.size() - 1 && stack.get(stackIndex).canModify;
  }

  public boolean saveLastColor () {
    if (canSaveStack()) {
      stack.get(stackIndex).canModify = false;
      return true;
    }
    return false;
  }

  public boolean hasTransparency () {
    return Color.alpha(getColor()) < 255;
  }

  public boolean redo () {
    if (stackIndex < stack.size() - 1) {
      stackIndex++;
      return true;
    }
    return false;
  }

  public boolean undo () {
    if (stackIndex > 0) {
      stackIndex--;
      return true;
    }
    return false;
  }

  public boolean removeCurrent () {
    if (stackIndex > 0) {
      stack.remove(stackIndex);
      stackIndex--;
      return true;
    }
    return false;
  }

  public boolean clear () {
    if (stack.size() > 1) {
      StackColor color = stack.get(0);
      stack.clear();
      stack.add(color);
      stackIndex = 0;
      return true;
    }
    return false;
  }
}
