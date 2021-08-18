package me.vkryl.core.unit;

public final class BitwiseUtils {
  private BitwiseUtils () { }

  public static boolean getFlag (int flags, int flag) {
    return (flags & flag) != 0;
  }

  public static boolean getFlag (long flags, long flag) {
    return (flags & flag) != 0;
  }

  public static int setFlag (int flags, int flag, boolean enabled) {
    if (enabled) {
      flags |= flag;
    } else {
      flags &= ~flag;
    }
    return flags;
  }

  public static long setFlag (long flags, long flag, boolean enabled) {
    if (enabled) {
      flags |= flag;
    } else {
      flags &= ~flag;
    }
    return flags;
  }

  public static int hashCode (long x) {
    return (int) (x ^ (x >>> 32));
  }

  public static int splitLongToFirstInt (long x) {
    return (int) (x >> 32);
  }

  public static int splitLongToSecondInt (long x) {
    return (int) x;
  }

  public static long mergeLong (int a, int b) {
    return ((long) a << 32) | (b & 0xFFFFFFFFL);
  }

  public static int mergeTimeToInt (int hour, int minute, int second) {
    return ((0xff & hour) << 16) | ((0xff & minute) << 8) | (0xff & second);
  }

  public static int splitIntToSecond (int time) {
    return (time & 0xff);
  }

  public static int splitIntToMinute (int time) {
    return ((time >> 8) & 0xff);
  }

  public static int splitIntToHour (int time) {
    return ((time >> 16) & 0xff);
  }

  private static boolean isAfter (int time, int afterTime) {
    return time > afterTime;
  }

  public static boolean belongsToSchedule (int time, int startTime, int endTime) {
    if (startTime == endTime) {
      return false;
    }

    int startHour = splitIntToHour(startTime);
    int startMinute = splitIntToMinute(startTime);
    int startSecond = splitIntToSecond(startTime);

    int endHour = splitIntToHour(endTime);
    int endMinute = splitIntToMinute(endTime);
    int endSecond = splitIntToSecond(endTime);

    int hour = splitIntToHour(time);
    int minute = splitIntToMinute(time);
    int second = splitIntToSecond(time);

    if (hour == startHour && minute == startMinute && second == startSecond) {
      return true;
    }
    if (hour == endHour && minute == endMinute && second == endSecond) {
      return false;
    }

    //  isAfter(startHour, startMinute, startSecond, endHour, endMinute, endSecond)
    if (isAfter(startHour, endHour)) {
      // 22:00-end || start-7:00
      // return isAfter(hour, minute, second, startHour, startMinute, startSecond) || isAfter(endHour, endMinute, endSecond, hour, minute, second);
      return isAfter(time, startTime) || isAfter(endTime, time);
    } else {
      // 7:00-22:00
      // return isAfter(hour, minute, second, startHour, startMinute, startSecond) && isAfter(endHour, endMinute, endSecond, hour, minute, second);
      return isAfter(time, startTime) && isAfter(endTime, time);
    }
  }
}
