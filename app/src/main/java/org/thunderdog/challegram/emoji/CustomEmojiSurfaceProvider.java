/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 05/09/2022, 18:16.
 */

package org.thunderdog.challegram.emoji;

import org.thunderdog.challegram.data.ComplexMediaItem;
import org.thunderdog.challegram.loader.ComplexReceiver;

public interface CustomEmojiSurfaceProvider {
  EmojiSpan onCreateNewSpan (CharSequence emojiCode, EmojiInfo info, long customEmojiId);
  void onInvalidateSpan (EmojiSpan span, boolean requiresLayoutUpdate);
  ComplexReceiver provideComplexReceiverForSpan (EmojiSpan span);
  int getDuplicateMediaItemCount (EmojiSpan span, ComplexMediaItem mediaItem);
  long attachToReceivers (EmojiSpan span, ComplexMediaItem complexMediaItem);
  void detachFromReceivers (EmojiSpan span, ComplexMediaItem complexMediaItem, long mediaKey);
}
