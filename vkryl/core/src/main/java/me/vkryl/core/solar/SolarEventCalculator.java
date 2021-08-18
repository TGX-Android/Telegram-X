package me.vkryl.core.solar;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Calendar;
import java.util.TimeZone;


/**
 * Parent class of the Sunrise and Sunset calculator classes.
 */
public class SolarEventCalculator {
  final private Location location;
  final private TimeZone timeZone;

  /**
   * Constructs a new <code>SolarEventCalculator</code> using the given parameters.
   *
   * @param location
   *            <code>Location</code> of the place where the solar event should be calculated from.
   * @param timeZoneIdentifier
   *            time zone identifier of the timezone of the location parameter. For example,
   *            "America/New_York".
   */
  public SolarEventCalculator(Location location, String timeZoneIdentifier) {
    this.location = location;
    this.timeZone = TimeZone.getTimeZone(timeZoneIdentifier);
  }

  /**
   * Constructs a new <code>SolarEventCalculator</code> using the given parameters.
   *
   * @param location
   *            <code>Location</code> of the place where the solar event should be calculated from.
   * @param timeZone
   *            timezone of the location parameter.
   */
  public SolarEventCalculator(Location location, TimeZone timeZone) {
    this.location = location;
    this.timeZone = timeZone;
  }

  /**
   * Computes the sunrise time for the given zenith at the given date.
   *
   * @param solarZenith
   *            <code>Zenith</code> enum corresponding to the type of sunrise to compute.
   * @param date
   *            <code>Calendar</code> object representing the date to compute the sunrise for.
   * @return the sunrise time, in HH:MM format (24-hour clock), 00:00 if the sun does not rise on the given
   *         date.
   */
  public String computeSunriseTime(Zenith solarZenith, Calendar date) {
    return getLocalTimeAsString(computeSolarEventTime(solarZenith, date, true));
  }

  /**
   * Computes the sunrise time for the given zenith at the given date.
   *
   * @param solarZenith
   *            <code>Zenith</code> enum corresponding to the type of sunrise to compute.
   * @param date
   *            <code>Calendar</code> object representing the date to compute the sunrise for.
   * @return the sunrise time as a calendar or null for no sunrise
   */
  public Calendar computeSunriseCalendar(Zenith solarZenith, Calendar date) {
    return getLocalTimeAsCalendar(computeSolarEventTime(solarZenith, date, true), date);
  }

  /**
   * Computes the sunset time for the given zenith at the given date.
   *
   * @param solarZenith
   *            <code>Zenith</code> enum corresponding to the type of sunset to compute.
   * @param date
   *            <code>Calendar</code> object representing the date to compute the sunset for.
   * @return the sunset time, in HH:MM format (24-hour clock), 00:00 if the sun does not set on the given
   *         date.
   */
  public String computeSunsetTime(Zenith solarZenith, Calendar date) {
    return getLocalTimeAsString(computeSolarEventTime(solarZenith, date, false));
  }

  /**
   * Computes the sunset time for the given zenith at the given date.
   *
   * @param solarZenith
   *            <code>Zenith</code> enum corresponding to the type of sunset to compute.
   * @param date
   *            <code>Calendar</code> object representing the date to compute the sunset for.
   * @return the sunset time as a Calendar or null for no sunset.
   */
  public Calendar computeSunsetCalendar(Zenith solarZenith, Calendar date) {
    return getLocalTimeAsCalendar(computeSolarEventTime(solarZenith, date, false), date);
  }

  private BigDecimal computeSolarEventTime(Zenith solarZenith, Calendar date, boolean isSunrise) {
    date.setTimeZone(this.timeZone);
    BigDecimal longitudeHour = getLongitudeHour(date, isSunrise);

    BigDecimal meanAnomaly = getMeanAnomaly(longitudeHour);
    BigDecimal sunTrueLong = getSunTrueLongitude(meanAnomaly);
    BigDecimal cosineSunLocalHour = getCosineSunLocalHour(sunTrueLong, solarZenith);
    if ((cosineSunLocalHour.doubleValue() < -1.0) || (cosineSunLocalHour.doubleValue() > 1.0)) {
      return null;
    }

    BigDecimal sunLocalHour = getSunLocalHour(cosineSunLocalHour, isSunrise);
    BigDecimal localMeanTime = getLocalMeanTime(sunTrueLong, longitudeHour, sunLocalHour);
    BigDecimal localTime = getLocalTime(localMeanTime, date);
    return localTime;
  }

  /**
   * Computes the base longitude hour, lngHour in the algorithm.
   *
   * @return the longitude of the location of the solar event divided by 15 (deg/hour), in
   *         <code>BigDecimal</code> form.
   */
  private BigDecimal getBaseLongitudeHour() {
    return divideBy(location.getLongitude(), BigDecimal.valueOf(15));
  }

  /**
   * Computes the longitude time, t in the algorithm.
   *
   * @return longitudinal time in <code>BigDecimal</code> form.
   */
  private BigDecimal getLongitudeHour(Calendar date, Boolean isSunrise) {
    int offset = 18;
    if (isSunrise) {
      offset = 6;
    }
    BigDecimal dividend = BigDecimal.valueOf(offset).subtract(getBaseLongitudeHour());
    BigDecimal addend = divideBy(dividend, BigDecimal.valueOf(24));
    BigDecimal longHour = getDayOfYear(date).add(addend);
    return setScale(longHour);
  }

  /**
   * Computes the mean anomaly of the Sun, M in the algorithm.
   *
   * @return the suns mean anomaly, M, in <code>BigDecimal</code> form.
   */
  private BigDecimal getMeanAnomaly(BigDecimal longitudeHour) {
    BigDecimal meanAnomaly = multiplyBy(new BigDecimal("0.9856"), longitudeHour).subtract(new BigDecimal("3.289"));
    return setScale(meanAnomaly);
  }

  /**
   * Computes the true longitude of the sun, L in the algorithm, at the given location, adjusted to fit in
   * the range [0-360].
   *
   * @param meanAnomaly
   *            the suns mean anomaly.
   * @return the suns true longitude, in <code>BigDecimal</code> form.
   */
  private BigDecimal getSunTrueLongitude(BigDecimal meanAnomaly) {
    BigDecimal sinMeanAnomaly = new BigDecimal(Math.sin(convertDegreesToRadians(meanAnomaly).doubleValue()));
    BigDecimal sinDoubleMeanAnomaly = new BigDecimal(Math.sin(multiplyBy(convertDegreesToRadians(meanAnomaly), BigDecimal.valueOf(2))
      .doubleValue()));

    BigDecimal firstPart = meanAnomaly.add(multiplyBy(sinMeanAnomaly, new BigDecimal("1.916")));
    BigDecimal secondPart = multiplyBy(sinDoubleMeanAnomaly, new BigDecimal("0.020")).add(new BigDecimal("282.634"));
    BigDecimal trueLongitude = firstPart.add(secondPart);

    if (trueLongitude.doubleValue() > 360) {
      trueLongitude = trueLongitude.subtract(BigDecimal.valueOf(360));
    }
    return setScale(trueLongitude);
  }

  /**
   * Computes the suns right ascension, RA in the algorithm, adjusting for the quadrant of L and turning it
   * into degree-hours. Will be in the range [0,360].
   *
   * @param sunTrueLong
   *            Suns true longitude, in <code>BigDecimal</code>
   * @return suns right ascension in degree-hours, in <code>BigDecimal</code> form.
   */
  private BigDecimal getRightAscension(BigDecimal sunTrueLong) {
    BigDecimal tanL = new BigDecimal(Math.tan(convertDegreesToRadians(sunTrueLong).doubleValue()));

    BigDecimal innerParens = multiplyBy(convertRadiansToDegrees(tanL), new BigDecimal("0.91764"));
    BigDecimal rightAscension = new BigDecimal(Math.atan(convertDegreesToRadians(innerParens).doubleValue()));
    rightAscension = setScale(convertRadiansToDegrees(rightAscension));

    if (rightAscension.doubleValue() < 0) {
      rightAscension = rightAscension.add(BigDecimal.valueOf(360));
    } else if (rightAscension.doubleValue() > 360) {
      rightAscension = rightAscension.subtract(BigDecimal.valueOf(360));
    }

    BigDecimal ninety = BigDecimal.valueOf(90);
    BigDecimal longitudeQuadrant = sunTrueLong.divide(ninety, 0, RoundingMode.FLOOR);
    longitudeQuadrant = longitudeQuadrant.multiply(ninety);

    BigDecimal rightAscensionQuadrant = rightAscension.divide(ninety, 0, RoundingMode.FLOOR);
    rightAscensionQuadrant = rightAscensionQuadrant.multiply(ninety);

    BigDecimal augend = longitudeQuadrant.subtract(rightAscensionQuadrant);
    return divideBy(rightAscension.add(augend), BigDecimal.valueOf(15));
  }

  private BigDecimal getCosineSunLocalHour(BigDecimal sunTrueLong, Zenith zenith) {
    BigDecimal sinSunDeclination = getSinOfSunDeclination(sunTrueLong);
    BigDecimal cosineSunDeclination = getCosineOfSunDeclination(sinSunDeclination);

    BigDecimal zenithInRads = convertDegreesToRadians(zenith.degrees());
    BigDecimal cosineZenith = BigDecimal.valueOf(Math.cos(zenithInRads.doubleValue()));
    BigDecimal sinLatitude = BigDecimal.valueOf(Math.sin(convertDegreesToRadians(location.getLatitude()).doubleValue()));
    BigDecimal cosLatitude = BigDecimal.valueOf(Math.cos(convertDegreesToRadians(location.getLatitude()).doubleValue()));

    BigDecimal sinDeclinationTimesSinLat = sinSunDeclination.multiply(sinLatitude);
    BigDecimal dividend = cosineZenith.subtract(sinDeclinationTimesSinLat);
    BigDecimal divisor = cosineSunDeclination.multiply(cosLatitude);

    return setScale(divideBy(dividend, divisor));
  }

  private BigDecimal getSinOfSunDeclination(BigDecimal sunTrueLong) {
    BigDecimal sinTrueLongitude = BigDecimal.valueOf(Math.sin(convertDegreesToRadians(sunTrueLong).doubleValue()));
    BigDecimal sinOfDeclination = sinTrueLongitude.multiply(new BigDecimal("0.39782"));
    return setScale(sinOfDeclination);
  }

  private BigDecimal getCosineOfSunDeclination(BigDecimal sinSunDeclination) {
    BigDecimal arcSinOfSinDeclination = BigDecimal.valueOf(Math.asin(sinSunDeclination.doubleValue()));
    BigDecimal cosDeclination = BigDecimal.valueOf(Math.cos(arcSinOfSinDeclination.doubleValue()));
    return setScale(cosDeclination);
  }

  private BigDecimal getSunLocalHour(BigDecimal cosineSunLocalHour, Boolean isSunrise) {
    BigDecimal arcCosineOfCosineHourAngle = getArcCosineFor(cosineSunLocalHour);
    BigDecimal localHour = convertRadiansToDegrees(arcCosineOfCosineHourAngle);
    if (isSunrise) {
      localHour = BigDecimal.valueOf(360).subtract(localHour);
    }
    return divideBy(localHour, BigDecimal.valueOf(15));
  }

  private BigDecimal getLocalMeanTime(BigDecimal sunTrueLong, BigDecimal longitudeHour, BigDecimal sunLocalHour) {
    BigDecimal rightAscension = this.getRightAscension(sunTrueLong);
    BigDecimal innerParens = longitudeHour.multiply(new BigDecimal("0.06571"));
    BigDecimal localMeanTime = sunLocalHour.add(rightAscension).subtract(innerParens);
    localMeanTime = localMeanTime.subtract(new BigDecimal("6.622"));

    if (localMeanTime.doubleValue() < 0) {
      localMeanTime = localMeanTime.add(BigDecimal.valueOf(24));
    } else if (localMeanTime.doubleValue() > 24) {
      localMeanTime = localMeanTime.subtract(BigDecimal.valueOf(24));
    }
    return setScale(localMeanTime);
  }

  private BigDecimal getLocalTime(BigDecimal localMeanTime, Calendar date) {
    BigDecimal utcTime = localMeanTime.subtract(getBaseLongitudeHour());
    BigDecimal utcOffSet = getUTCOffSet(date);
    BigDecimal utcOffSetTime = utcTime.add(utcOffSet);
    return adjustForDST(utcOffSetTime, date);
  }

  private BigDecimal adjustForDST(BigDecimal localMeanTime, Calendar date) {
    BigDecimal localTime = localMeanTime;
    if (timeZone.inDaylightTime(date.getTime())) {
      localTime = localTime.add(BigDecimal.ONE);
    }
    if (localTime.doubleValue() > 24.0) {
      localTime = localTime.subtract(BigDecimal.valueOf(24));
    }
    return localTime;
  }

  /**
   * Returns the local rise/set time in the form HH:MM.
   *
   * @param localTime
   *            <code>BigDecimal</code> representation of the local rise/set time.
   * @return <code>String</code> representation of the local rise/set time in HH:MM format.
   */
  private String getLocalTimeAsString(BigDecimal localTimeParam) {
    if (localTimeParam == null) {
      return "99:99";
    }

    BigDecimal localTime = localTimeParam;
    if (localTime.compareTo(BigDecimal.ZERO) == -1) {
      localTime = localTime.add(BigDecimal.valueOf(24.0D));
    }
    String[] timeComponents = localTime.toPlainString().split("\\.");
    int hour = Integer.parseInt(timeComponents[0]);

    BigDecimal minutes = new BigDecimal("0." + timeComponents[1]);
    minutes = minutes.multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_EVEN);
    if (minutes.intValue() == 60) {
      minutes = BigDecimal.ZERO;
      hour += 1;
    }
    if (hour == 24) {
      hour = 0;
    }

    String minuteString = minutes.intValue() < 10 ? "0" + minutes.toPlainString() : minutes.toPlainString();
    String hourString = (hour < 10) ? "0" + hour : String.valueOf(hour);
    return hourString + ":" + minuteString;
  }

  /**
   * Returns the local rise/set time in the form HH:MM.
   *
   * @param localTimeParam
   *            <code>BigDecimal</code> representation of the local rise/set time.
   * @return <code>Calendar</code> representation of the local time as a calendar, or null for none.
   */
  protected Calendar getLocalTimeAsCalendar(BigDecimal localTimeParam, Calendar date) {
    if (localTimeParam == null) {
      return null;
    }

    // Create a clone of the input calendar so we get locale/timezone information.
    Calendar resultTime = (Calendar) date.clone();

    BigDecimal localTime = localTimeParam;
    if (localTime.compareTo(BigDecimal.ZERO) == -1) {
      localTime = localTime.add(BigDecimal.valueOf(24.0D));
      resultTime.add(Calendar.HOUR_OF_DAY, -24);
    }
    String[] timeComponents = localTime.toPlainString().split("\\.");
    int hour = Integer.parseInt(timeComponents[0]);

    BigDecimal minutes = new BigDecimal("0." + timeComponents[1]);
    minutes = minutes.multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_EVEN);
    if (minutes.intValue() == 60) {
      minutes = BigDecimal.ZERO;
      hour += 1;
    }
    if (hour == 24) {
      hour = 0;
    }

    // Set the local time
    resultTime.set(Calendar.HOUR_OF_DAY, hour);
    resultTime.set(Calendar.MINUTE, minutes.intValue());
    resultTime.set(Calendar.SECOND, 0);
    resultTime.set(Calendar.MILLISECOND, 0);
    resultTime.setTimeZone(date.getTimeZone());

    return resultTime;
  }

  /** ******* UTILITY METHODS (Should probably go somewhere else. ***************** */

  private BigDecimal getDayOfYear(Calendar date) {
    return new BigDecimal(date.get(Calendar.DAY_OF_YEAR));
  }

  private BigDecimal getUTCOffSet(Calendar date) {
    double offSetInMillis = date.get(Calendar.ZONE_OFFSET);
    BigDecimal offSet = new BigDecimal(offSetInMillis / 3600000);
    return offSet.setScale(2);
  }

  private BigDecimal getArcCosineFor(BigDecimal radians) {
    BigDecimal arcCosine = BigDecimal.valueOf(Math.acos(radians.doubleValue()));
    return setScale(arcCosine);
  }

  private BigDecimal convertRadiansToDegrees(BigDecimal radians) {
    return multiplyBy(radians, new BigDecimal(180 / Math.PI));
  }

  private BigDecimal convertDegreesToRadians(BigDecimal degrees) {
    return multiplyBy(degrees, BigDecimal.valueOf(Math.PI / 180.0));
  }

  private BigDecimal multiplyBy(BigDecimal multiplicand, BigDecimal multiplier) {
    return setScale(multiplicand.multiply(multiplier));
  }

  private BigDecimal divideBy(BigDecimal dividend, BigDecimal divisor) {
    return dividend.divide(divisor, 4, RoundingMode.HALF_EVEN);
  }

  private BigDecimal setScale(BigDecimal number) {
    return number.setScale(4, RoundingMode.HALF_EVEN);
  }
}