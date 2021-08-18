@file:JvmName("SdkVersion")

package me.vkryl.android

import android.os.Build

@JvmOverloads fun getPrettyName (apiLevel: Int = Build.VERSION.SDK_INT): String {
  return when (apiLevel) {
    Build.VERSION_CODES.HONEYCOMB -> "Honeycomb"
    Build.VERSION_CODES.HONEYCOMB_MR1 -> "Honeycomb MR1"
    Build.VERSION_CODES.HONEYCOMB_MR2 -> "Honeycomb MR2"
    Build.VERSION_CODES.ICE_CREAM_SANDWICH -> "Ice Cream Sandwich"
    Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1 -> "Ice Cream Sandwich MR1"
    Build.VERSION_CODES.JELLY_BEAN -> "Jelly Bean"
    Build.VERSION_CODES.JELLY_BEAN_MR1 -> "Jelly Bean MR1"
    Build.VERSION_CODES.JELLY_BEAN_MR2 -> "Jelly Bean MR2"
    Build.VERSION_CODES.KITKAT -> "Kitkat"
    Build.VERSION_CODES.KITKAT_WATCH -> "Kitkat Watch"
    Build.VERSION_CODES.LOLLIPOP -> "Lollipop"
    Build.VERSION_CODES.LOLLIPOP_MR1 -> "Lollipop MR1"
    Build.VERSION_CODES.M -> "Marshmallow"
    Build.VERSION_CODES.N -> "Nougat"
    Build.VERSION_CODES.N_MR1 -> "Nougat MR1"
    Build.VERSION_CODES.O -> "Oreo"
    Build.VERSION_CODES.O_MR1 -> "Oreo MR1"
    Build.VERSION_CODES.P -> "Pie"
    Build.VERSION_CODES.Q -> "Q"
    Build.VERSION_CODES.R -> "R"
    Build.VERSION_CODES.CUR_DEVELOPMENT -> "Magic Version"
    else -> "Unknown"
  }
}

fun getPrettyVersionCode (apiLevel: Int): String {
  return when (apiLevel) {
    Build.VERSION_CODES.HONEYCOMB -> "3.0"
    Build.VERSION_CODES.HONEYCOMB_MR1 -> "3.1"
    Build.VERSION_CODES.HONEYCOMB_MR2 -> "3.2"
    Build.VERSION_CODES.ICE_CREAM_SANDWICH -> "4.0"
    Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1 -> "4.0.3"
    Build.VERSION_CODES.JELLY_BEAN -> "4.1"
    Build.VERSION_CODES.JELLY_BEAN_MR1 -> "4.2"
    Build.VERSION_CODES.JELLY_BEAN_MR2 -> "4.3"
    Build.VERSION_CODES.KITKAT -> "4.4"
    Build.VERSION_CODES.KITKAT_WATCH -> "4.4W"
    Build.VERSION_CODES.LOLLIPOP -> "5.0"
    Build.VERSION_CODES.LOLLIPOP_MR1 -> "5.1"
    Build.VERSION_CODES.M -> "6.0"
    Build.VERSION_CODES.N -> "7.0"
    Build.VERSION_CODES.N_MR1 -> "7.1"
    Build.VERSION_CODES.O -> "8.0"
    Build.VERSION_CODES.O_MR1 -> "8.1"
    Build.VERSION_CODES.P -> "9.0"
    Build.VERSION_CODES.Q -> "10"
    Build.VERSION_CODES.R -> "11"
    Build.VERSION_CODES.CUR_DEVELOPMENT -> "Magic Version"
    else -> "Unknown"
  }
}