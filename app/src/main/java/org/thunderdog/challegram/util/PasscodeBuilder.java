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
 * File created on 08/08/2015 at 13:44
 */
package org.thunderdog.challegram.util;

public class PasscodeBuilder {
  public static final int PATTERN_MAX_SIZE = 9;

  private int[] passcode;
  private int size;

  public PasscodeBuilder () {
    this.passcode = new int[PATTERN_MAX_SIZE];
  }

  public PasscodeBuilder (PasscodeBuilder copy) {
    this.passcode = new int[PATTERN_MAX_SIZE];
    this.size = copy.size;
    System.arraycopy(copy.passcode, 0, passcode, 0, size);
  }

  public void append (int digit) {
    this.passcode[size] = digit;
    size++;
  }

  public boolean compare (PasscodeBuilder pattern) {
    if (pattern == null || pattern.size != size) {
      return false;
    }
    for (int i = 0; i < size; i++) {
      if (pattern.passcode[i] != passcode[i]) {
        return false;
      }
    }
    return true;
  }

  public int getSize () {
    return size;
  }

  public int getLastDigit () {
    return passcode[size - 1];
  }

  public void clear () {
    size = 0;
  }

  public void removeLast () {
    if (size > 0) {
      size--;
    }
  }

  @Override
  public String toString () {
    StringBuilder builder = new StringBuilder(size);
    for (int i = 0; i < size; i++) {
      builder.append(passcode[i]);
    }
    return builder.toString();
  }
}
