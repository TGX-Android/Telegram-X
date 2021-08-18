package me.vkryl.core.unit;

/*
 * Copyright 2011 Fabian Barney
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 *
 * @author Fabian Barney
 *
 */
public enum ByteUnit {

  /** <pre>
   * Byte (B)
   * 1 Byte
   */
  BYTE {
    @Override
    public long toBytes(double size) { return (long) size; }

    @Override
    public double convert(double size, ByteUnit unit) { return unit.toBytes(size); }
  },

  /** <pre>
   * Kibibyte (KiB)
   * 2^10 Byte = 1.024 Byte
   */
  KIB {
    @Override
    public long toBytes(double size) { return (long) safeMulti(size, C_KIB); }

    @Override
    public double convert(double size, ByteUnit unit) { return unit.toKiB(size); }
  },

  /** <pre>
   * Mebibyte (MiB)
   * 2^20 Byte = 1.024 * 1.024 Byte = 1.048.576 Byte
   */
  MIB {
    @Override
    public long toBytes(double size) { return (long) safeMulti(size, C_MIB); }

    @Override
    public double convert(double size, ByteUnit unit) { return unit.toMiB(size); }
  },

  /** <pre>
   * Gibibyte (GiB)
   * 2^30 Byte = 1.024 * 1.024 * 1.024 Byte = 1.073.741.824 Byte
   */
  GIB {
    @Override
    public long toBytes(double size) { return (long) safeMulti(size, C_GIB); }

    @Override
    public double convert(double size, ByteUnit unit) { return unit.toGiB(size); }
  },

  /** <pre>
   * Tebibyte (TiB)
   * 2^40 Byte = 1.024 * 1.024 * 1.024 * 1.024 Byte = 1.099.511.627.776 Byte
   */
  TIB {
    @Override
    public long toBytes(double size) { return (long) safeMulti(size, C_TIB); }

    @Override
    public double convert(double size, ByteUnit unit) { return unit.toTiB(size); }
  },

  /** <pre>
   * Pebibyte (PiB)
   * 2^50 Byte = 1.024 * 1.024 * 1.024 * 1.024 * 1.024 Byte = 1.125.899.906.842.624 Byte
   */
  PIB {
    @Override
    public long toBytes(double size) { return (long) safeMulti(size, C_PIB); }

    @Override
    public double convert(double size, ByteUnit unit) { return unit.toPiB(size); }
  },

  /** <pre>
   * Kilobyte (kB)
   * 10^3 Byte = 1.000 Byte
   */
  KB {
    @Override
    public long toBytes(double size) { return (long) safeMulti(size, C_KB); }

    @Override
    public double convert(double size, ByteUnit u) { return u.toKB(size); }
  },

  /** <pre>
   * Megabyte (MB)
   * 10^6 Byte = 1.000.000 Byte
   */
  MB {
    @Override
    public long toBytes(double size) { return (long) safeMulti(size, C_MB); }

    @Override
    public double convert(double size, ByteUnit u) { return u.toMB(size); }
  },

  /** <pre>
   * Gigabyte (GB)
   * 10^9 Byte = 1.000.000.000 Byte
   */
  GB {
    @Override
    public long toBytes(double size) { return (long) safeMulti(size, C_GB); }

    @Override
    public double convert(double size, ByteUnit u) { return u.toGB(size); }
  },

  /** <pre>
   * Terabyte (TB)
   * 10^12 Byte = 1.000.000.000.000 Byte
   */
  TB {
    @Override
    public long toBytes(double size) { return (long) safeMulti(size, C_TB); }

    @Override
    public double convert(double size, ByteUnit u) { return u.toTB(size); }
  },

  /** <pre>
   * Petabyte (PB)
   * 10^15 Byte = 1.000.000.000.000.000 Byte
   */
  PB {
    @Override
    public long toBytes(double size) { return (long) safeMulti(size, C_PB); }

    @Override
    public double convert(double size, ByteUnit u) { return u.toPB(size); }
  };



  static final double C_KIB = Math.pow(2d, 10d);
  static final double C_MIB = Math.pow(2d, 20d);
  static final double C_GIB = Math.pow(2d, 30d);
  static final double C_TIB = Math.pow(2d, 40d);
  static final double C_PIB = Math.pow(2d, 50d);

  static final double C_KB = Math.pow(10d, 3d);
  static final double C_MB = Math.pow(10d, 6d);
  static final double C_GB = Math.pow(10d, 9d);
  static final double C_TB = Math.pow(10d, 12d);
  static final double C_PB = Math.pow(10d, 15d);


  private static final double MAX = Double.MAX_VALUE;



  static double safeMulti(double d, double multi) {
    double limit = MAX / multi;

    if (d >  limit) {
      return Double.MAX_VALUE;
    }
    if (d < -limit) {
      return Double.MIN_VALUE;
    }

    return d * multi;
  }


  public abstract long toBytes(double d);

  public final double toKiB(double d){
    return toBytes(d) / C_KIB;
  }

  public final double toMiB(double d) {
    return toBytes(d) / C_MIB;
  }

  public final double toGiB(double d) {
    return toBytes(d) / C_GIB;
  }

  public final double toTiB(double d) {
    return toBytes(d) / C_TIB;
  }

  public final double toPiB(double d) {
    return toBytes(d) / C_PIB;
  }


  public final double toKB(double d) {
    return toBytes(d) / C_KB;
  }

  public final double toMB(double d) {
    return toBytes(d) / C_MB;
  }

  public final double toGB(double d) {
    return toBytes(d) / C_GB;
  }

  public final double toTB(double d) {
    return toBytes(d) / C_TB;
  }

  public final double toPB(double d) {
    return toBytes(d) / C_PB;
  }



  public abstract double convert(double d, ByteUnit u);

  public final double convert(double d, BitUnit u) {
    return convert(d, u, Byte.SIZE);
  }

  public final double convert(double d, BitUnit u, int wordSize){
    double bytes = u.toBits(d) / wordSize;
    return convert(bytes, BYTE);
  }



  /*
   * Komfort-Methoden für Cross-Konvertierung
   */
  public final double toBits(double size) {
    return BitUnit.BIT.convert(size, this);
  }

  public final double toBits(double size, int wordSize) {
    return BitUnit.BIT.convert(size, this, wordSize);
  }


  public final double toKibit(double size){
    return BitUnit.KIBIT.convert(size, this);
  }

  public final double toMibit(double size) {
    return BitUnit.MIBIT.convert(size, this);
  }

  public final double toGibit(double size) {
    return BitUnit.GIBIT.convert(size, this);
  }

  public final double toTibit(double size) {
    return BitUnit.TIBIT.convert(size, this);
  }

  public final double toPibit(double size) {
    return BitUnit.PIBIT.convert(size, this);
  }

  public final double toKibit(double size, int wordSize){
    return BitUnit.KIBIT.convert(size, this, wordSize);
  }

  public final double toMibit(double size, int wordSize) {
    return BitUnit.MIBIT.convert(size, this, wordSize);
  }

  public final double toGibit(double size, int wordSize) {
    return BitUnit.GIBIT.convert(size, this, wordSize);
  }

  public final double toTibit(double size, int wordSize) {
    return BitUnit.TIBIT.convert(size, this, wordSize);
  }

  public final double toPibit(double size, int wordSize) {
    return BitUnit.PIBIT.convert(size, this, wordSize);
  }


  public final double toKbit(double size) {
    return BitUnit.KBIT.convert(size, this);
  }

  public final double toMbit(double size) {
    return BitUnit.MBIT.convert(size, this);
  }

  public final double toGbit(double size) {
    return BitUnit.GBIT.convert(size, this);
  }

  public final double toTbit(double size) {
    return BitUnit.TBIT.convert(size, this);
  }

  public final double toPbit(double size) {
    return BitUnit.PBIT.convert(size, this);
  }


  public final double toKbit(double size, int wordSize) {
    return BitUnit.KBIT.convert(size, this, wordSize);
  }

  public final double toMbit(double size, int wordSize) {
    return BitUnit.MBIT.convert(size, this, wordSize);
  }

  public final double toGbit(double size, int wordSize) {
    return BitUnit.GBIT.convert(size, this, wordSize);
  }

  public final double toTbit(double size, int wordSize) {
    return BitUnit.TBIT.convert(size, this, wordSize);
  }

  public final double toPbit(double size, int wordSize) {
    return BitUnit.PBIT.convert(size, this, wordSize);
  }

}
