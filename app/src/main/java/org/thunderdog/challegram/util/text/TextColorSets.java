/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.util.text;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.PorterDuffColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.ColorUtils;

public final class TextColorSets {
  public static final TextColorSetThemed WHITE = () -> ColorId.white;
  public static final TextColorSetThemed PLACEHOLDER = () -> ColorId.textPlaceholder;

  // Instant View
  public interface InstantView extends TextColorSetThemed {
    @Override
    default int clickableTextColorId (boolean isPressed) {
      return ColorId.iv_textLink;
    }

    @Override
    default int pressedBackgroundColorId () {
      return ColorId.iv_textLinkPressHighlight;
    }

    @Override
    default int backgroundPadding () {
      return Screen.dp(2f);
    }

    InstantView NORMAL = () -> ColorId.iv_text,
      CHAT_LINK_OVERLAY = () -> ColorId.white,
      REFERENCE = new InstantView() {
        @Override
        public int defaultTextColorId () {
          return ColorId.iv_textReference;
        }

        @Override
        public int iconColorId () {
          return defaultTextColorId();
        }

        @Override
        public int clickableTextColorId (boolean isPressed) {
          return defaultTextColorId();
        }

        @Override
        public int pressedBackgroundColorId () {
          return ColorId.iv_textReferenceBackgroundPressed;
        }

        @Override
        public int staticBackgroundColorId () {
          return ColorId.iv_textReferenceBackground;
        }

        @Override
        public int outlineColorId (boolean isPressed) {
          return isPressed ? ColorId.iv_textReferenceOutlinePressed : ColorId.iv_textReferenceOutline;
        }
      },
      FOOTER = () -> ColorId.iv_pageFooter,
      CAPTION = () -> ColorId.iv_caption,
      PULL_QUOTE = () -> ColorId.iv_pullQuote,
      BLOCK_QUOTE = () -> ColorId.iv_blockQuote,
      TITLE = () -> ColorId.iv_pageTitle,
      SUBTITLE = () -> ColorId.iv_pageSubtitle,
      HEADER = TITLE, SUBHEADER = TITLE,
      AUTHOR = () -> ColorId.iv_pageAuthor;
    interface Marked extends InstantView {
      default int defaultTextColorId () {
        return ColorId.iv_textMarked;
      }

      default int clickableTextColorId (boolean isPressed) {
        return ColorId.iv_textMarkedLink;
      }

      default int pressedBackgroundColorId () {
        return ColorId.iv_textMarkedLinkPressHighlight;
      }

      default int staticBackgroundColorId () {
        return ColorId.iv_textMarkedBackground;
      }

      Marked NORMAL = new Marked() { };
    }
    interface Monospace extends InstantView {
      @Override
      default int defaultTextColorId () {
        return ColorId.iv_textCode;
      }

      @Override
      default int clickableTextColorId (boolean isPressed) {
        return ColorId.iv_textCode;
      }

      @Override
      default int pressedBackgroundColorId () {
        return ColorId.iv_textCodeBackgroundPressed;
      }

      @Override
      default int staticBackgroundColorId () {
        return ColorId.iv_textCodeBackground;
      }

      Monospace
        NORMAL = new Monospace() { };
    }
  }

  // NORMAL
  public interface Regular extends TextColorSetThemed {
    @Override
    @PorterDuffColorId
    default int defaultTextColorId () {
      return ColorId.text;
    }

    @Override
    default long mediaTextComplexColor () {
      return Theme.newComplexColor(true, defaultTextColorId());
    }

    @Override
    default int iconColorId () {
      return ColorId.icon;
    }

    @Override
    default int clickableTextColorId (boolean isPressed) {
      return ColorId.textLink;
    }

    @Override
    default int pressedBackgroundColorId () {
      return ColorId.textLinkPressHighlight;
    }

    Regular
      NORMAL = new Regular() { },
      LIGHT = new Regular() {
        @Override
        public int defaultTextColorId () {
          return ColorId.textLight;
        }

        @Override
        public int iconColorId () {
          return ColorId.iconLight;
        }
      },
      SEARCH_HIGHLIGHT = new Regular() {
        @Override
        public int defaultTextColorId () {
          return ColorId.textSearchQueryHighlight;
        }
      },
      MESSAGE_SEARCH_HIGHLIGHT = new Regular() {
        @Override
        public int backgroundColor (boolean isPressed) {
          return Theme.getColor(ColorId.textLinkPressHighlight);
        }
        @Override
        public int backgroundColorId (boolean isPressed) {
          return ColorId.textLinkPressHighlight;
        }
      },
      LINK = new Regular() {
        @Override
        public int defaultTextColorId () {
          return ColorId.textLink;
        }
      },
      SECURE = new Regular() {
        @Override
        public int defaultTextColorId () {
          return ColorId.textSecure;
        }

        @Override
        public int iconColorId () {
          return ColorId.textSecure;
        }
      },
      NEGATIVE = new Regular() {
        @Override
        public int defaultTextColorId () {
          return ColorId.textNegative;
        }

        @Override
        public int iconColorId () {
          return ColorId.iconNegative;
        }
      },
      NEUTRAL = new Regular() {
        @Override
        public int defaultTextColorId () {
          return ColorId.textNeutral;
        }
      },
      MESSAGE_AUTHOR = new Regular() {
        /*@Override
        public int defaultTextColorId () {
          return ColorId.messageAuthor;
        }*/

        @Override
        public long mediaTextComplexColor () {
          return Theme.newComplexColor(true, ColorId.messageAuthor);
        }

        @Override
        public int clickableTextColorId (boolean isPressed) {
          return ColorId.messageAuthor;
        }

        @Override
        public int backgroundColor (boolean isPressed) {
          return isPressed ? ColorUtils.alphaColor(.2f, Theme.getColor(ColorId.messageAuthor)) : ColorId.NONE;
        }

        @Override
        public int backgroundColorId (boolean isPressed) {
          return isPressed ? ColorId.messageAuthor : ColorId.NONE;
        }
      },
      MESSAGE_AUTHOR_PSA = new Regular() {
        @Override
        public int defaultTextColorId () {
          return ColorId.text;
        }

        @Override
        public int clickableTextColorId (boolean isPressed) {
          return ColorId.messageAuthorPsa;
        }
      },
      AVATAR_CONTENT = new Regular() {
        @Override
        public int defaultTextColorId () {
          return ColorId.avatar_content;
        }
      };
  }

  // OUTGOING BUBBLE
  public interface BubbleOut extends TextColorSetThemed {
    @Override
    default int iconColorId () {
      return ColorId.bubbleOut_time;
    }

    @Override
    default int clickableTextColorId (boolean isPressed) {
      return ColorId.bubbleOut_textLink;
    }

    @Override
    default int pressedBackgroundColorId () {
      return ColorId.bubbleOut_textLinkPressHighlight;
    }

    BubbleOut
      NORMAL = () -> ColorId.bubbleOut_text,
      LIGHT = () -> ColorId.bubbleOut_time,
      LINK = () -> ColorId.bubbleOut_textLink,
      MESSAGE_AUTHOR = new BubbleOut() {
        @Override
        public int defaultTextColorId () {
          return ColorId.bubbleOut_text;
        }

        @Override
        public long mediaTextComplexColor () {
          return Theme.newComplexColor(true, ColorId.bubbleOut_messageAuthor);
        }

        @Override
        public int clickableTextColorId (boolean isPressed) {
          return ColorId.bubbleOut_messageAuthor;
        }
      },
      MESSAGE_AUTHOR_PSA = new BubbleOut() {
        @Override
        public int defaultTextColorId () {
          return ColorId.bubbleOut_text;
        }

        @Override
        public int clickableTextColorId (boolean isPressed) {
          return ColorId.bubbleOut_messageAuthorPsa;
        }
      },
      MESSAGE_SEARCH_HIGHLIGHT = new BubbleOut() {
        @Override
        public int defaultTextColorId () {
          return ColorId.bubbleOut_text;
        }
        @Override
        public int backgroundColor (boolean isPressed) {
          return Theme.getColor(ColorId.bubbleOut_textLinkPressHighlight);
        }
        @Override
        public int backgroundColorId (boolean isPressed) {
          return ColorId.bubbleOut_textLinkPressHighlight;
        }
      };
  }

  public interface BubbleIn extends TextColorSetThemed {
    @Override
    default int iconColorId () {
      return ColorId.bubbleIn_time;
    }

    @Override
    default int clickableTextColorId (boolean isPressed) {
      return ColorId.bubbleIn_textLink;
    }

    @Override
    default int pressedBackgroundColorId () {
      return ColorId.bubbleIn_textLinkPressHighlight;
    }

    BubbleIn
      NORMAL = () -> ColorId.bubbleIn_text,
      LIGHT = () -> ColorId.bubbleIn_time,
      LINK = () -> ColorId.bubbleIn_textLink,
      MESSAGE_AUTHOR = new BubbleIn() {
        @Override
        public int defaultTextColorId () {
          return ColorId.bubbleIn_text;
        }

        @Override
        public long mediaTextComplexColor () {
          return Theme.newComplexColor(true, ColorId.messageAuthor);
        }

        @Override
        public int clickableTextColorId (boolean isPressed) {
          return ColorId.messageAuthor;
        }
      },
      MESSAGE_AUTHOR_PSA = new BubbleIn() {
        @Override
        public int defaultTextColorId () {
          return ColorId.bubbleIn_text;
        }

        @Override
        public int clickableTextColorId (boolean isPressed) {
          return ColorId.messageAuthorPsa;
        }
      },
      MESSAGE_SEARCH_HIGHLIGHT = new BubbleIn() {
        @Override
        public int defaultTextColorId () {
          return ColorId.bubbleIn_text;
        }
        @Override
        public int backgroundColor (boolean isPressed) {
          return Theme.getColor(ColorId.bubbleIn_textLinkPressHighlight);
        }
        @Override
        public int backgroundColorId (boolean isPressed) {
          return ColorId.bubbleIn_textLinkPressHighlight;
        }
      };
  }
}
