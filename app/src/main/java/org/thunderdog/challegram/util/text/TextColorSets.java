/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
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

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;

public final class TextColorSets {
  public static final TextColorSetThemed WHITE = () -> R.id.theme_color_white;
  public static final TextColorSetThemed PLACEHOLDER = () -> R.id.theme_color_textPlaceholder;

  // Instant View
  public interface InstantView extends TextColorSetThemed {
    @Override
    default int clickableTextColorId (boolean isPressed) {
      return R.id.theme_color_iv_textLink;
    }

    @Override
    default int pressedBackgroundColorId () {
      return R.id.theme_color_iv_textLinkPressHighlight;
    }

    @Override
    default int backgroundPadding () {
      return Screen.dp(2f);
    }

    InstantView NORMAL = () -> R.id.theme_color_iv_text,
      CHAT_LINK_OVERLAY = () -> R.id.theme_color_white,
      REFERENCE = new InstantView() {
        @Override
        public int defaultTextColorId () {
          return R.id.theme_color_iv_textReference;
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
          return R.id.theme_color_iv_textReferenceBackgroundPressed;
        }

        @Override
        public int staticBackgroundColorId () {
          return R.id.theme_color_iv_textReferenceBackground;
        }

        @Override
        public int outlineColorId (boolean isPressed) {
          return isPressed ? R.id.theme_color_iv_textReferenceOutlinePressed : R.id.theme_color_iv_textReferenceOutline;
        }
      },
      FOOTER = () -> R.id.theme_color_iv_pageFooter,
      CAPTION = () -> R.id.theme_color_iv_caption,
      PULL_QUOTE = () -> R.id.theme_color_iv_pullQuote,
      BLOCK_QUOTE = () -> R.id.theme_color_iv_blockQuote,
      TITLE = () -> R.id.theme_color_iv_pageTitle,
      SUBTITLE = () -> R.id.theme_color_iv_pageSubtitle,
      HEADER = TITLE, SUBHEADER = TITLE,
      AUTHOR = () -> R.id.theme_color_iv_pageAuthor;
    interface Marked extends InstantView {
      default int defaultTextColorId () {
        return R.id.theme_color_iv_textMarked;
      }

      default int clickableTextColorId (boolean isPressed) {
        return R.id.theme_color_iv_textMarkedLink;
      }

      default int pressedBackgroundColorId () {
        return R.id.theme_color_iv_textMarkedLinkPressHighlight;
      }

      default int staticBackgroundColorId () {
        return R.id.theme_color_iv_textMarkedBackground;
      }

      Marked NORMAL = new Marked() { };
    }
    interface Monospace extends InstantView {
      @Override
      default int defaultTextColorId () {
        return R.id.theme_color_iv_textCode;
      }

      @Override
      default int clickableTextColorId (boolean isPressed) {
        return R.id.theme_color_iv_textCode;
      }

      @Override
      default int pressedBackgroundColorId () {
        return R.id.theme_color_iv_textCodeBackgroundPressed;
      }

      @Override
      default int staticBackgroundColorId () {
        return R.id.theme_color_iv_textCodeBackground;
      }

      Monospace
        NORMAL = new Monospace() { };
    }
  }

  // NORMAL
  public interface Regular extends TextColorSetThemed {
    @Override
    default int defaultTextColorId () {
      return R.id.theme_color_text;
    }

    @Override
    default int iconColorId () {
      return R.id.theme_color_icon;
    }

    @Override
    default int clickableTextColorId (boolean isPressed) {
      return R.id.theme_color_textLink;
    }

    @Override
    default int pressedBackgroundColorId () {
      return R.id.theme_color_textLinkPressHighlight;
    }

    Regular
      NORMAL = new Regular() { },
      LIGHT = new Regular() {
        @Override
        public int defaultTextColorId () {
          return R.id.theme_color_textLight;
        }

        @Override
        public int iconColorId () {
          return R.id.theme_color_iconLight;
        }
      },
      SEARCH_HIGHLIGHT = new Regular() {
        @Override
        public int defaultTextColorId () {
          return R.id.theme_color_textSearchQueryHighlight;
        }
      },
      MESSAGE_SEARCH_HIGHLIGHT = new Regular() {
        @Override
        public int backgroundColor (boolean isPressed) {
          return Theme.getColor(R.id.theme_color_textLinkPressHighlight);
        }
        @Override
        public int backgroundColorId (boolean isPressed) {
          return R.id.theme_color_textLinkPressHighlight;
        }
      },
      LINK = new Regular() {
        @Override
        public int defaultTextColorId () {
          return R.id.theme_color_textLink;
        }
      },
      SECURE = new Regular() {
        @Override
        public int defaultTextColorId () {
          return R.id.theme_color_textSecure;
        }

        @Override
        public int iconColorId () {
          return R.id.theme_color_textSecure;
        }
      },
      NEGATIVE = new Regular() {
        @Override
        public int defaultTextColorId () {
          return R.id.theme_color_textNegative;
        }

        @Override
        public int iconColorId () {
          return R.id.theme_color_iconNegative;
        }
      },
      NEUTRAL = new Regular() {
        @Override
        public int defaultTextColorId () {
          return R.id.theme_color_textNeutral;
        }
      },
      MESSAGE_AUTHOR = new Regular() {
        /*@Override
        public int defaultTextColorId () {
          return R.id.theme_color_messageAuthor;
        }*/

        @Override
        public int clickableTextColorId (boolean isPressed) {
          return R.id.theme_color_messageAuthor;
        }
      },
      MESSAGE_AUTHOR_PSA = new Regular() {
        @Override
        public int defaultTextColorId () {
          return R.id.theme_color_text;
        }

        @Override
        public int clickableTextColorId (boolean isPressed) {
          return R.id.theme_color_messageAuthorPsa;
        }
      },
      AVATAR_CONTENT = new Regular() {
        @Override
        public int defaultTextColorId () {
          return R.id.theme_color_avatar_content;
        }
      };
  }

  // OUTGOING BUBBLE
  public interface BubbleOut extends TextColorSetThemed {
    @Override
    default int iconColorId () {
      return R.id.theme_color_bubbleOut_time;
    }

    @Override
    default int clickableTextColorId (boolean isPressed) {
      return R.id.theme_color_bubbleOut_textLink;
    }

    @Override
    default int pressedBackgroundColorId () {
      return R.id.theme_color_bubbleOut_textLinkPressHighlight;
    }

    BubbleOut
      NORMAL = () -> R.id.theme_color_bubbleOut_text,
      LIGHT = () -> R.id.theme_color_bubbleOut_time,
      LINK = () -> R.id.theme_color_bubbleOut_textLink,
      MESSAGE_AUTHOR = new BubbleOut() {
        @Override
        public int defaultTextColorId () {
          return R.id.theme_color_bubbleOut_text;
        }

        @Override
        public int clickableTextColorId (boolean isPressed) {
          return R.id.theme_color_bubbleOut_messageAuthor;
        }
      },
      MESSAGE_AUTHOR_PSA = new BubbleOut() {
        @Override
        public int defaultTextColorId () {
          return R.id.theme_color_bubbleOut_text;
        }

        @Override
        public int clickableTextColorId (boolean isPressed) {
          return R.id.theme_color_bubbleOut_messageAuthorPsa;
        }
      },
      MESSAGE_SEARCH_HIGHLIGHT = new BubbleOut() {
        @Override
        public int defaultTextColorId () {
          return R.id.theme_color_bubbleOut_text;
        }
        @Override
        public int backgroundColor (boolean isPressed) {
          return Theme.getColor(R.id.theme_color_bubbleOut_textLinkPressHighlight);
        }
        @Override
        public int backgroundColorId (boolean isPressed) {
          return R.id.theme_color_bubbleOut_textLinkPressHighlight;
        }
      };
  }

  public interface BubbleIn extends TextColorSetThemed {
    @Override
    default int iconColorId () {
      return R.id.theme_color_bubbleIn_time;
    }

    @Override
    default int clickableTextColorId (boolean isPressed) {
      return R.id.theme_color_bubbleIn_textLink;
    }

    @Override
    default int pressedBackgroundColorId () {
      return R.id.theme_color_bubbleIn_textLinkPressHighlight;
    }

    BubbleIn
      NORMAL = () -> R.id.theme_color_bubbleIn_text,
      LIGHT = () -> R.id.theme_color_bubbleIn_time,
      LINK = () -> R.id.theme_color_bubbleIn_textLink,
      MESSAGE_AUTHOR = new BubbleIn() {
        @Override
        public int defaultTextColorId () {
          return R.id.theme_color_bubbleIn_text;
        }

        @Override
        public int clickableTextColorId (boolean isPressed) {
          return R.id.theme_color_messageAuthor;
        }
      },
      MESSAGE_AUTHOR_PSA = new BubbleIn() {
        @Override
        public int defaultTextColorId () {
          return R.id.theme_color_bubbleIn_text;
        }

        @Override
        public int clickableTextColorId (boolean isPressed) {
          return R.id.theme_color_messageAuthorPsa;
        }
      },
      MESSAGE_SEARCH_HIGHLIGHT = new BubbleIn() {
        @Override
        public int defaultTextColorId () {
          return R.id.theme_color_bubbleIn_text;
        }
        @Override
        public int backgroundColor (boolean isPressed) {
          return Theme.getColor(R.id.theme_color_bubbleIn_textLinkPressHighlight);
        }
        @Override
        public int backgroundColorId (boolean isPressed) {
          return R.id.theme_color_bubbleIn_textLinkPressHighlight;
        }
      };
  }
}
