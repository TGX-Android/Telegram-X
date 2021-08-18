/**
 * File created on 13/05/15 at 11:57
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.tool;

import android.os.Parcel;

public class Parcels {
  public static void writeString (String s, Parcel p) {
    if (s == null) {
      p.writeByte((byte) 0);
    } else {
      p.writeByte((byte) 1);
      p.writeString(s);
    }
  }

  public static String readString (Parcel p) {
    if (p.readByte() == (byte) 1) {
      return p.readString();
    } else {
      return null;
    }
  }

  //returns true, if object is not null
  public static boolean writeNull (Object obj, Parcel p) {
    if (obj == null) {
      p.writeByte((byte) 0);
      return false;
    } else {
      p.writeByte((byte) 1);
      return true;
    }
  }

  //Returns true if object is not null
  public static boolean readBool (Parcel p) {
    return p.readByte() == (byte) 1;
  }

  public static void writeBool (boolean v, Parcel p) {
    if (v) {
      p.writeByte((byte) 1);
    } else {
      p.writeByte((byte) 0);
    }
  }
}
