package org.thunderdog.challegram.util.text;

public class TextColorSetOverride implements TextColorSet {
  private final TextColorSet colorSet;

  public TextColorSetOverride (TextColorSet colorSet) {
    this.colorSet = colorSet;
  }

  public TextColorSet originalColorSet () {
    return colorSet;
  }

  @Override
  public int defaultTextColor () {
    return colorSet.defaultTextColor();
  }

  @Override
  public int iconColor () {
    return colorSet.iconColor();
  }

  @Override
  public int clickableTextColor (boolean isPressed) {
    return colorSet.clickableTextColor(isPressed);
  }

  @Override
  public int backgroundColor (boolean isPressed) {
    return colorSet.backgroundColor(isPressed);
  }

  @Override
  public int outlineColor (boolean isPressed) {
    return colorSet.outlineColor(isPressed);
  }

  @Override
  public int backgroundColorId (boolean isPressed) {
    return colorSet.backgroundColorId(isPressed);
  }

  @Override
  public int outlineColorId (boolean isPressed) {
    return colorSet.outlineColorId(isPressed);
  }
}
