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
 * @author Fabian Barney
 *
 */
public enum BitUnit {

  BIT {
    @Override
    public double toBits(double d) { return d; }

    @Override
    public double convert(double d, BitUnit u) { return u.toBits(d); }
  },

  KIBIT {
    @Override
    public double toBits(double d) { return safeMulti(d, C_KIBIT); }

    @Override
    public double convert(double d, BitUnit u) { return u.toKibit(d); }
  },

  MIBIT {
    @Override
    public double toBits(double d) { return safeMulti(d, C_MIBIT); }

    @Override
    public double convert(double d, BitUnit u) { return u.toMibit(d); }
  },

  GIBIT {
    @Override
    public double toBits(double d) { return safeMulti(d, C_GIBIT); }

    @Override
    public double convert(double d, BitUnit u) { return u.toGibit(d); }
  },

  TIBIT {
    @Override
    public double toBits(double d) { return safeMulti(d, C_TIBIT); }

    @Override
    public double convert(double d, BitUnit u) { return u.toTibit(d); }
  },

  PIBIT {
    @Override
    public double toBits(double d) { return safeMulti(d, C_PIBIT); }

    @Override
    public double convert(double d, BitUnit u) { return u.toPibit(d); }
  },

  KBIT {
    @Override
    public double toBits(double d) { return safeMulti(d, C_KBIT); }

    @Override
    public double convert(double d, BitUnit u) { return u.toKbit(d); }
  },

  MBIT {
    @Override
    public double toBits(double d) { return safeMulti(d, C_MBIT); }

    @Override
    public double convert(double d, BitUnit u) { return u.toMbit(d); }
  },

  GBIT {
    @Override
    public double toBits(double d) { return safeMulti(d, C_GBIT); }

    @Override
    public double convert(double d, BitUnit u) { return u.toGbit(d); }
  },

  TBIT {
    @Override
    public double toBits(double d) { return safeMulti(d, C_TBIT); }

    @Override
    public double convert(double d, BitUnit u) { return u.toTbit(d); }
  },

  PBIT {
    @Override
    public double toBits(double d) { return safeMulti(d, C_PBIT); }

    @Override
    public double convert(double d, BitUnit u) { return u.toPbit(d); }
  };


  static final double C_KIBIT = Math.pow(2d, 10d);
  static final double C_MIBIT = Math.pow(2d, 20d);
  static final double C_GIBIT = Math.pow(2d, 30d);
  static final double C_TIBIT = Math.pow(2d, 40d);
  static final double C_PIBIT = Math.pow(2d, 50d);

  static final double C_KBIT = Math.pow(10d, 3d);
  static final double C_MBIT = Math.pow(10d, 6d);
  static final double C_GBIT = Math.pow(10d, 9d);
  static final double C_TBIT = Math.pow(10d, 12d);
  static final double C_PBIT = Math.pow(10d, 15d);


  private static final double MAX = Double.MAX_VALUE;

  static final double safeMulti(double d, double multi) {
    double limit = MAX / multi;

    if (d >  limit) {
      return Double.MAX_VALUE;
    }

    if (d < -limit) {
      return Double.MIN_VALUE;
    }

    return d * multi;
  }


  public abstract double toBits(double d);


  public final double toKibit(double d){
    return toBits(d) / C_KIBIT;
  }

  public final double toMibit(double d) {
    return toBits(d) / C_MIBIT;
  }

  public final double toGibit(double d) {
    return toBits(d) / C_GIBIT;
  }

  public final double toTibit(double d) {
    return toBits(d) / C_TIBIT;
  }

  public final double toPibit(double d) {
    return toBits(d) / C_PIBIT;
  }


  public final double toKbit(double d) {
    return toBits(d) / C_KBIT;
  }

  public final double toMbit(double d) {
    return toBits(d) / C_MBIT;
  }

  public final double toGbit(double d) {
    return toBits(d) / C_GBIT;
  }

  public final double toTbit(double d) {
    return toBits(d) / C_TBIT;
  }

  public final double toPbit(double d) {
    return toBits(d) / C_PBIT;
  }


  public abstract double convert(double d, BitUnit u);

  public final double convert(double d, ByteUnit u){
    return convert(d, u, Byte.SIZE);
  }

  public final double convert(double d, ByteUnit u, int wordSize){
    double bits = safeMulti(u.toBytes(d), wordSize);
    return convert(bits, BIT);
  }



  /*
   * Komfort-Methoden für Cross-Konvertierung
   */

  public final double toBytes(double d){
    return ByteUnit.BYTE.convert(d, this);
  }

  public final double toBytes(double d, int wordSize){
    return ByteUnit.BYTE.convert(d, this, wordSize);
  }


  public final double toKiB(double d){
    return ByteUnit.KIB.convert(d, this);
  }

  public final double toMiB(double d) {
    return ByteUnit.MIB.convert(d, this);
  }

  public final double toGiB(double d) {
    return ByteUnit.GIB.convert(d, this);
  }

  public final double toTiB(double d) {
    return ByteUnit.TIB.convert(d, this);
  }

  public final double toPiB(double d) {
    return ByteUnit.PIB.convert(d, this);
  }



  public final double toKiB(double d, int wordSize){
    return ByteUnit.KIB.convert(d, this, wordSize);
  }

  public final double toMiB(double d, int wordSize) {
    return ByteUnit.MIB.convert(d, this, wordSize);
  }

  public final double toGiB(double d, int wordSize) {
    return ByteUnit.GIB.convert(d, this, wordSize);
  }

  public final double toTiB(double d, int wordSize) {
    return ByteUnit.TIB.convert(d, this, wordSize);
  }

  public final double toPiB(double d, int wordSize) {
    return ByteUnit.PIB.convert(d, this, wordSize);
  }




  public final double toKB(double d) {
    return ByteUnit.KB.convert(d, this);
  }

  public final double toMB(double d) {
    return ByteUnit.MB.convert(d, this);
  }

  public final double toGB(double d) {
    return ByteUnit.GB.convert(d, this);
  }

  public final double toTB(double d) {
    return ByteUnit.TB.convert(d, this);
  }

  public final double toPB(double d) {
    return ByteUnit.PB.convert(d, this);
  }


  public final double toKB(double d, int wordSize) {
    return ByteUnit.KB.convert(d, this, wordSize);
  }

  public final double toMB(double d, int wordSize) {
    return ByteUnit.MB.convert(d, this, wordSize);
  }

  public final double toGB(double d, int wordSize) {
    return ByteUnit.GB.convert(d, this, wordSize);
  }

  public final double toTB(double d, int wordSize) {
    return ByteUnit.TB.convert(d, this, wordSize);
  }

  public final double toPB(double d, int wordSize) {
    return ByteUnit.PB.convert(d, this, wordSize);
  }


}
