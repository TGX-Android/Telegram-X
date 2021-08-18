package me.vkryl.core;

import java.util.HashMap;

/**
 * Date: 11/3/17
 * Author: default
 */

public final class CurrencyUtils {
  private CurrencyUtils () { }

  public static final int FLAG_SYMBOL_LEFT = 1;
  public static final int FLAG_SPACE_BETWEEN = 1 << 1;

  public static class Currency {
    public final String symbol;
    public final String shortSymbol;
    public final String iconSymbol;

    public final String thousandSeparator;
    public final String decimalSeparator;
    public final int exp;
    public final int flags;

    public Currency (String symbol, String shortSymbol, String iconSymbol, String thousandSeparator, String decimalSeparator, int exp, int flags) {
      this.symbol = symbol;
      this.shortSymbol = shortSymbol;
      this.iconSymbol = iconSymbol;
      this.thousandSeparator = thousandSeparator;
      this.decimalSeparator = decimalSeparator;
      this.exp = exp;
      this.flags = flags;
    }
  }

  public static final HashMap<String, Currency> MAP = generateMap();
  public static final Currency DEFAULT_CURRENCY = new Currency("$", null, null, ",", ".", 2, FLAG_SYMBOL_LEFT);

  private static HashMap<String, Currency> generateMap () {
    HashMap<String, Currency> map = new HashMap<>();

    map.put("AED", new Currency("AED", "د.إ.‏", null, ",", ".", 2, FLAG_SYMBOL_LEFT | FLAG_SPACE_BETWEEN));
    map.put("AFN", new Currency("AFN", "؋", null, ",", ".", 2, FLAG_SYMBOL_LEFT));
    map.put("ALL", new Currency("ALL", "Lek", null, ".", ",", 2, 0));
    map.put("AMD", new Currency("AMD", "դր.", null, ",", ".", 2, FLAG_SPACE_BETWEEN));
    map.put("ARS", new Currency("ARS", "$", null, ".", ",", 2, FLAG_SYMBOL_LEFT | FLAG_SPACE_BETWEEN));
    map.put("AUD", new Currency("AU$", "$", null, ",", ".", 2, FLAG_SYMBOL_LEFT));
    map.put("AZN", new Currency("AZN", "ман.", null, " ", ",", 2, FLAG_SPACE_BETWEEN));
    map.put("BAM", new Currency("BAM", "KM", null, ".", ",", 2, FLAG_SPACE_BETWEEN));
    map.put("BDT", new Currency("BDT", "৳", null, ",", ".", 2, FLAG_SYMBOL_LEFT | FLAG_SPACE_BETWEEN));
    map.put("BGN", new Currency("BGN", "лв.", null, " ", ",", 2, FLAG_SPACE_BETWEEN));
    map.put("BND", new Currency("BND", "$", null, ".", ",", 2, FLAG_SYMBOL_LEFT));
    map.put("BOB", new Currency("BOB", "Bs", null, ".", ",", 2, FLAG_SYMBOL_LEFT | FLAG_SPACE_BETWEEN));
    map.put("BRL", new Currency("R$", "R$", null, ".", ",", 2, FLAG_SYMBOL_LEFT | FLAG_SPACE_BETWEEN));
    map.put("CAD", new Currency("CA$", "$", null, ",", ".", 2, FLAG_SYMBOL_LEFT));
    map.put("CHF", new Currency("CHF", "CHF", null, "'", ".", 2, FLAG_SPACE_BETWEEN));
    map.put("CLP", new Currency("CLP", "$", null, ".", ",", 0, FLAG_SYMBOL_LEFT | FLAG_SPACE_BETWEEN));
    map.put("CNY", new Currency("CN¥", "CN¥", null, ",", ".", 2, FLAG_SYMBOL_LEFT));
    map.put("COP", new Currency("COP", "$", null, ".", ",", 2, FLAG_SYMBOL_LEFT | FLAG_SPACE_BETWEEN));
    map.put("CRC", new Currency("CRC", "₡", null, ".", ",", 2, FLAG_SYMBOL_LEFT));
    map.put("CZK", new Currency("CZK", "Kč", null, " ", ",", 2, FLAG_SPACE_BETWEEN));
    map.put("DKK", new Currency("DKK", "kr", null, "", ",", 2, FLAG_SPACE_BETWEEN));
    map.put("DOP", new Currency("DOP", "$", null, ",", ".", 2, FLAG_SYMBOL_LEFT));
    map.put("DZD", new Currency("DZD", "د.ج.‏", null, ",", ".", 2, FLAG_SYMBOL_LEFT | FLAG_SPACE_BETWEEN));
    map.put("EGP", new Currency("EGP", "ج.م.‏", null, ",", ".", 2, FLAG_SYMBOL_LEFT | FLAG_SPACE_BETWEEN));
    map.put("EUR", new Currency("€", "€", null, " ", ",", 2, FLAG_SPACE_BETWEEN));
    map.put("GBP", new Currency("£", "£", null, ",", ".", 2, FLAG_SYMBOL_LEFT));
    map.put("GEL", new Currency("GEL", "GEL", null, " ", ",", 2, FLAG_SPACE_BETWEEN));
    map.put("GTQ", new Currency("GTQ", "Q", null, ",", ".", 2, FLAG_SYMBOL_LEFT));
    map.put("HKD", new Currency("HK$", "$", null, ",", ".", 2, FLAG_SYMBOL_LEFT));
    map.put("HNL", new Currency("HNL", "L", null, ",", ".", 2, FLAG_SYMBOL_LEFT | FLAG_SPACE_BETWEEN));
    map.put("HRK", new Currency("HRK", "kn", null, ".", ",", 2, FLAG_SPACE_BETWEEN));
    map.put("HUF", new Currency("HUF", "Ft", null, " ", ",", 2, FLAG_SPACE_BETWEEN));
    map.put("IDR", new Currency("IDR", "Rp", null, ".", ",", 2, FLAG_SYMBOL_LEFT));
    map.put("ILS", new Currency("₪", "₪", null, ",", ".", 2, FLAG_SYMBOL_LEFT | FLAG_SPACE_BETWEEN));
    map.put("INR", new Currency("₹", "₹", null, ",", ".", 2, FLAG_SYMBOL_LEFT));
    map.put("ISK", new Currency("ISK", "kr", null, ".", ",", 0, FLAG_SPACE_BETWEEN));
    map.put("JMD", new Currency("JMD", "$", null, ",", ".", 2, FLAG_SYMBOL_LEFT));
    map.put("JPY", new Currency("¥", "￥", "¥", ",", ".", 0, FLAG_SYMBOL_LEFT));
    map.put("KES", new Currency("KES", "Ksh", null, ",", ".", 2, FLAG_SYMBOL_LEFT));
    map.put("KGS", new Currency("KGS", "KGS", null, " ", "-", 2, FLAG_SPACE_BETWEEN));
    map.put("KRW", new Currency("₩", "₩", null, ",", ".", 0, FLAG_SYMBOL_LEFT));
    map.put("KZT", new Currency("KZT", "₸", null, " ", "-", 2, FLAG_SYMBOL_LEFT));
    map.put("LBP", new Currency("LBP", "ل.ل.‏", "£", ",", ".", 2, FLAG_SYMBOL_LEFT | FLAG_SPACE_BETWEEN));
    map.put("LKR", new Currency("LKR", "රු.", "₨", ",", ".", 2, FLAG_SYMBOL_LEFT | FLAG_SPACE_BETWEEN));
    map.put("MAD", new Currency("MAD", "د.م.‏", null, ",", ".", 2, FLAG_SYMBOL_LEFT | FLAG_SPACE_BETWEEN));
    map.put("MDL", new Currency("MDL", "MDL", null, ",", ".", 2, FLAG_SPACE_BETWEEN));
    map.put("MNT", new Currency("MNT", "MNT", null, " ", ",", 2, FLAG_SYMBOL_LEFT));
    map.put("MUR", new Currency("MUR", "MUR", null, ",", ".", 2, FLAG_SYMBOL_LEFT));
    map.put("MVR", new Currency("MVR", "MVR", null, ",", ".", 2, FLAG_SPACE_BETWEEN));
    map.put("MXN", new Currency("MX$", "$", null, ",", ".", 2, FLAG_SYMBOL_LEFT));
    map.put("MYR", new Currency("MYR", "RM", null, ",", ".", 2, FLAG_SYMBOL_LEFT));
    map.put("MZN", new Currency("MZN", "MTn", null, ",", ".", 2, FLAG_SYMBOL_LEFT));
    map.put("NGN", new Currency("NGN", "₦", null, ",", ".", 2, FLAG_SYMBOL_LEFT));
    map.put("NIO", new Currency("NIO", "C$", null, ",", ".", 2, FLAG_SYMBOL_LEFT | FLAG_SPACE_BETWEEN));
    map.put("NOK", new Currency("NOK", "kr", null, " ", ",", 2, FLAG_SYMBOL_LEFT | FLAG_SPACE_BETWEEN));
    map.put("NPR", new Currency("NPR", "नेरू", null, ",", ".", 2, FLAG_SYMBOL_LEFT));
    map.put("NZD", new Currency("NZ$", "$", null, ",", ".", 2, FLAG_SYMBOL_LEFT));
    map.put("PAB", new Currency("PAB", "B/.", null, ",", ".", 2, FLAG_SYMBOL_LEFT | FLAG_SPACE_BETWEEN));
    map.put("PEN", new Currency("PEN", "S/.", null, ",", ".", 2, FLAG_SYMBOL_LEFT | FLAG_SPACE_BETWEEN));
    map.put("PHP", new Currency("PHP", "₱", null, ",", ".", 2, FLAG_SYMBOL_LEFT));
    map.put("PKR", new Currency("PKR", "₨", null, ",", ".", 2, FLAG_SYMBOL_LEFT));
    map.put("PLN", new Currency("PLN", "zł", null, " ", ",", 2, FLAG_SPACE_BETWEEN));
    map.put("PYG", new Currency("PYG", "₲", null, ".", ",", 0, FLAG_SYMBOL_LEFT | FLAG_SPACE_BETWEEN));
    map.put("QAR", new Currency("QAR", "ر.ق.‏", null, ",", ".", 2, FLAG_SYMBOL_LEFT | FLAG_SPACE_BETWEEN));
    map.put("RON", new Currency("RON", "RON", null, ".", ",", 2, FLAG_SPACE_BETWEEN));
    map.put("RSD", new Currency("RSD", "дин.", null, ".", ",", 2, FLAG_SPACE_BETWEEN));
    map.put("RUB", new Currency("RUB", "₽", null, " ", ",", 2, FLAG_SPACE_BETWEEN));
    map.put("SAR", new Currency("SAR", "ر.س.‏", "﷼", ",", ".", 2, FLAG_SYMBOL_LEFT | FLAG_SPACE_BETWEEN));
    map.put("SEK", new Currency("SEK", "kr", null, ".", ",", 2, FLAG_SPACE_BETWEEN));
    map.put("SGD", new Currency("SGD", "$", null, ",", ".", 2, FLAG_SYMBOL_LEFT));
    map.put("THB", new Currency("฿", "฿", null, ",", ".", 2, FLAG_SYMBOL_LEFT));
    map.put("TJS", new Currency("TJS", "TJS", null, " ", ";", 2, FLAG_SPACE_BETWEEN));
    map.put("TRY", new Currency("TRY", "TL", null, ".", ",", 2, FLAG_SPACE_BETWEEN));
    map.put("TTD", new Currency("TTD", "$", null, ",", ".", 2, FLAG_SYMBOL_LEFT));
    map.put("TWD", new Currency("NT$", "NT$", null, ",", ".", 2, FLAG_SYMBOL_LEFT));
    map.put("TZS", new Currency("TZS", "TSh", null, ",", ".", 2, FLAG_SYMBOL_LEFT));
    map.put("UAH", new Currency("UAH", "₴", null, " ", ",", 2, 0));
    map.put("UGX", new Currency("UGX", "USh", null, ",", ".", 0, FLAG_SYMBOL_LEFT));
    map.put("USD", new Currency("$", "$", null, ",", ".", 2, FLAG_SYMBOL_LEFT));
    map.put("UYU", new Currency("UYU", "$", null, ".", ",", 2, FLAG_SYMBOL_LEFT | FLAG_SPACE_BETWEEN));
    map.put("UZS", new Currency("UZS", "UZS", null, " ", ",", 2, FLAG_SPACE_BETWEEN));
    map.put("VND", new Currency("₫", "₫", null, ".", ",", 0, FLAG_SPACE_BETWEEN));
    map.put("YER", new Currency("YER", "ر.ي.‏", "﷼", ",", ".", 2, FLAG_SYMBOL_LEFT | FLAG_SPACE_BETWEEN));
    map.put("ZAR", new Currency("ZAR", "R", null, ",", ".", 2, FLAG_SYMBOL_LEFT | FLAG_SPACE_BETWEEN));

    return map;
  }

  public static Currency getCurrency (String currency) {
    if (!StringUtils.isEmpty(currency)) {
      Currency data = MAP.get(currency);
      if (data != null) {
        return data;
      }
    }
    return DEFAULT_CURRENCY;
  }

  public static String getCurrencyChar (String currency) {
    if (StringUtils.isEmpty(currency)) {
      return currency;
    }

    Currency data = getCurrency(currency);
    return StringUtils.isEmpty(data.iconSymbol) ? data.shortSymbol : data.iconSymbol;

    /*switch (currency) {
      case "AUD": return "$";
      case "AZN": return "₼";
      case "BSD": return "$";
      case "BBD": return "$";
      case "BYN": return "Br";
      case "BZD": return "BZ$";
      case "BMD": return "$";
      case "BOB": return "$b";
      case "BAM": return "KM";
      case "BWP": return "P";
      case "BGN": return "лв";
      case "BRL": return "R$";
      case "BND": return "$";
      case "KHR": return "៛";
      case "CAD": return "$";
      case "KYD": return "$";
      case "CLP": return "$";
      case "CNY": return "¥";
      case "COP": return "$";
      case "CRC": return "₡";
      case "HRK": return "kn";
      case "CUP": return "₱";
      case "CZK": return "Kč";
      case "DKK": return "kr";
      case "DOP": return "RD$";
      case "XCD": return "$";
      case "EGP": return "£";
      case "SVC": return "$";
      case "EUR": return "€";
      case "FKP": return "£";
      case "FJD": return "$";
      case "GHS": return "¢";
      case "GIP": return "£";
      case "GTQ": return "Q";
      case "GGP": return "£";
      case "GYD": return "$";
      case "HNL": return "L";
      case "HKD": return "$";
      case "HUF": return "Ft";
      case "ISK": return "kr";
      case "IDR": return "Rp";
      case "IRR": return "﷼";
      case "IMP": return "£";
      case "ILS": return "₪";
      case "JMD": return "J$";
      case "JPY": return "¥";
      case "JEP": return "£";
      case "KZT": return "лв";
      case "KPW": return "₩";
      case "KRW": return "₩";
      case "KGS": return "лв";
      case "LAK": return "₭";
      case "LBP": return "£";
      case "LRD": return "$";
      case "MKD": return "ден";
      case "MYR": return "RM";
      case "MUR": return "₨";
      case "MXN": return "$";
      case "MNT": return "₮";
      case "MZN": return "MT";
      case "NAD": return "$";
      case "NPR": return "₨";
      case "ANG": return "ƒ";
      case "NZD": return "$";
      case "NIO": return "C$";
      case "NGN": return "₦";
      case "NOK": return "kr";
      case "OMR": return "﷼";
      case "PKR": return "₨";
      case "PAB": return "B/.";
      case "PYG": return "Gs";
      case "PEN": return "S/.";
      case "PHP": return "₱";
      case "PLN": return "zł";
      case "QAR": return "﷼";
      case "RON": return "lei";
      case "RUB": return "₽";
      case "SHP": return "£";
      case "SAR": return "﷼";
      case "RSD": return "Дин.";
      case "SCR": return "₨";
      case "SGD": return "$";
      case "SBD": return "$";
      case "SOS": return "S";
      case "ZAR": return "R";
      case "LKR": return "₨";
      case "SEK": return "kr";
      case "CHF": return "CHF";
      case "SRD": return "$";
      case "SYP": return "£";
      case "TWD": return "NT$";
      case "THB": return "฿";
      case "TTD": return "TT$";
      case "TVD": return "$";
      case "UAH": return "₴";
      case "GBP": return "£";
      case "USD": return "$";
      case "UYU": return "$U";
      case "UZS": return "лв";
      case "VEF": return "Bs";
      case "VND": return "₫";
      case "YER": return "﷼";
      case "ZWD": return "Z$";
    }*/
  }

  public static String buildAmount (String currency, long amount) {
    Currency data = getCurrency(currency);

    StringBuilder b = new StringBuilder();
    String symbol = StringUtils.isEmpty(data.iconSymbol) ? data.shortSymbol : data.iconSymbol;

    if ((data.flags & FLAG_SYMBOL_LEFT) != 0) {
      b.append(symbol);
      if ((data.flags & FLAG_SPACE_BETWEEN) != 0) {
        b.append(' ');
      }
    }

    long decimal = -1;
    long exp = -1;

    if (data.exp != 0) {
      exp = (long) Math.pow(10, data.exp);
      decimal = amount % exp;
      amount /= exp;
    }

    if (amount == 0) {
      b.append('0');
    } else {
      int i = b.length();
      boolean first = true;
      while (amount > 0) {
        long remain = amount % 1000;
        if (first) {
          first = false;
        } else {
          b.insert(i, data.thousandSeparator);
        }
        amount /= 1000;
        b.insert(i, remain);
        if (amount > 0 && remain < 100) {
          b.insert(i, '0');
          if (remain < 10) {
            b.insert(i, '0');
          }
        }
      }
      if (decimal != -1 && decimal != 0) {
        b.append(data.decimalSeparator);
        i = b.length();
        b.append(decimal);
        exp /= 10;
        while ((exp /= 10) != 0) {
          if (decimal < exp) {
            b.insert(i, '0');
          }
        }
      }
    }

    if ((data.flags & FLAG_SYMBOL_LEFT) == 0) {
      if ((data.flags & FLAG_SPACE_BETWEEN) != 0) {
        b.append(' ');
      }
      b.append(symbol);
    }

    return b.toString();
  }

  // pub
}
