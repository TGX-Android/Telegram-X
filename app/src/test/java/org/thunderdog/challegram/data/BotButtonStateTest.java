package org.thunderdog.challegram.data;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.theme.ThemeSet;

import tgx.td.Td;

import static org.junit.Assert.*;

public class BotButtonStateTest {
  private static TdApi.InlineKeyboardButton button (TdApi.ButtonStyle style, long icon, int data) {
    return new TdApi.InlineKeyboardButton("Run 🩷", icon, style, new TdApi.InlineKeyboardButtonTypeCallback(new byte[] {(byte) data}));
  }

  @Test public void presentationEditsPreservePendingAction () {
    BotButtonState state = new BotButtonState();
    assertTrue(state.update(button(new TdApi.ButtonStyleDefault(), 0, 1), "Run 🩷"));
    assertFalse(state.update(button(new TdApi.ButtonStylePrimary(), 11, 1), "Run 🩷"));
    assertEquals(ColorId.botButtonPrimary, state.backgroundColorId);
    assertEquals(11, state.iconCustomEmojiId);
    assertFalse(state.update(button(new TdApi.ButtonStyleDanger(), 22, 1), "Run 🩷"));
    assertEquals(ColorId.botButtonDanger, state.backgroundColorId);
    assertEquals(22, state.iconCustomEmojiId);
    assertFalse(state.update(button(new TdApi.ButtonStyleSuccess(), 0, 1), "Run 🩷"));
    assertEquals(0, state.iconCustomEmojiId);
    assertEquals(ColorId.botButtonSuccess, state.backgroundColorId);
  }

  @Test public void resetToDefaultClearsPresentation () {
    BotButtonState state = new BotButtonState();
    state.update(button(new TdApi.ButtonStyleSuccess(), 42, 1), "Run");
    assertFalse(state.update(button(new TdApi.ButtonStyleDefault(), 0, 1), "Run"));
    assertEquals(ColorId.NONE, state.backgroundColorId);
    assertEquals(0, state.iconCustomEmojiId);
    state.update(button(null, 0, 1), "Run");
    assertEquals(ColorId.NONE, state.backgroundColorId);
    state.update(button(new TdApi.ButtonStyleLink(), 0, 1), "Run");
    assertEquals(ColorId.NONE, state.backgroundColorId);
  }

  @Test public void actionAndLabelChangesInvalidateOldCallback () {
    BotButtonState state = new BotButtonState();
    state.update(button(null, 0, 1), "Run");
    assertTrue(state.update(button(null, 0, 2), "Run"));
    assertFalse(state.update(button(null, 0, 2), "Run"));
    assertTrue(state.update(button(null, 0, 2), "Stop"));
    TdApi.InlineKeyboardButton url = new TdApi.InlineKeyboardButton("Stop", 0, null, new TdApi.InlineKeyboardButtonTypeUrl("https://telegram.org"));
    assertTrue(state.update(url, "Stop"));
    assertSame(url.type, state.type);
  }

  @Test public void markupEqualityDetectsStyleAndIconOnlyEdits () {
    TdApi.ReplyMarkupInlineKeyboard original = new TdApi.ReplyMarkupInlineKeyboard(new TdApi.InlineKeyboardButton[][] {{button(new TdApi.ButtonStyleDefault(), 0, 1)}}, false);
    assertFalse(Td.equalsTo(original, new TdApi.ReplyMarkupInlineKeyboard(new TdApi.InlineKeyboardButton[][] {{button(new TdApi.ButtonStylePrimary(), 0, 1)}}, false)));
    assertFalse(Td.equalsTo(original, new TdApi.ReplyMarkupInlineKeyboard(new TdApi.InlineKeyboardButton[][] {{button(new TdApi.ButtonStyleDefault(), 11, 1)}}, false)));
    assertTrue(Td.equalsTo(original, new TdApi.ReplyMarkupInlineKeyboard(new TdApi.InlineKeyboardButton[][] {{button(new TdApi.ButtonStyleDefault(), 0, 1)}}, false)));
  }

  @Test public void accentThemesInheritSemanticBotColors () {
    ThemeDelegate blue = ThemeSet.getBuiltinTheme(ThemeId.BLUE);
    for (int themeId : new int[] {ThemeId.PINK, ThemeId.CYAN, ThemeId.NIGHT_BLACK, ThemeId.NIGHT_BLUE}) {
      ThemeDelegate theme = ThemeSet.getBuiltinTheme(themeId);
      assertEquals(blue.getColor(ColorId.botButtonPrimary), theme.getColor(ColorId.botButtonPrimary));
      assertEquals(blue.getColor(ColorId.botButtonDanger), theme.getColor(ColorId.botButtonDanger));
      assertEquals(blue.getColor(ColorId.botButtonSuccess), theme.getColor(ColorId.botButtonSuccess));
      assertEquals(0xffffffff, theme.getColor(ColorId.botButtonText));
      assertEquals(0x33ffffff, theme.getColor(ColorId.botButtonRipple));
      assertNotEquals(theme.getColor(ColorId.botButtonSuccess), theme.getColor(ColorId.fillingPositive));
    }
  }

  @Test public void replyButtonRetainsOriginalCommandAndDetectsEdits () {
    String command = "👩🏽‍💻 Run 🩷";
    TdApi.KeyboardButton original = new TdApi.KeyboardButton(command, 11, new TdApi.ButtonStylePrimary(), new TdApi.KeyboardButtonTypeText());
    TdApi.KeyboardButton recolored = new TdApi.KeyboardButton(command, 11, new TdApi.ButtonStyleSuccess(), new TdApi.KeyboardButtonTypeText());
    TdApi.KeyboardButton reiconed = new TdApi.KeyboardButton(command, 22, new TdApi.ButtonStylePrimary(), new TdApi.KeyboardButtonTypeText());
    assertFalse(Td.equalsTo(original, recolored));
    assertFalse(Td.equalsTo(original, reiconed));
    assertEquals(command, recolored.text);
    assertEquals(command, reiconed.text);
  }
}
