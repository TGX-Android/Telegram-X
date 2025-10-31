package org.thunderdog.challegram.telegram;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;

import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.StringUtils;

public class TdlibOutlineManager extends TdlibDataManager<String, TdApi.Outline, TdlibOutlineManager.Entry> {
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(value = {
    StickerOutlineOption.FOR_ANIMATED_EMOJI,
    StickerOutlineOption.FOR_CLICKED_ANIMATED_EMOJI_MESSAGE
  }, flag = true)
  public @interface StickerOutlineOption {
    int
      FOR_ANIMATED_EMOJI = 1,
      FOR_CLICKED_ANIMATED_EMOJI_MESSAGE = 1 << 1;
  }

  public static class Entry extends AbstractEntry<String, TdApi.Outline> {
    private final ParsedKey parsedKey;

    public Entry (@NonNull String key, @Nullable TdApi.Outline value, @Nullable TdApi.Error error) {
      super(key, value, error);
      parsedKey = ParsedKey.parse(key);
    }

    public boolean isStickerOutline () {
      return parsedKey.type == ParsedKey.TYPE_STICKER_OUTLINE;
    }

    public boolean isWebAppPlaceholder () {
      return parsedKey.type == ParsedKey.TYPE_WEB_APP_PLACEHOLDER;
    }
  }

  public interface Watcher extends TdlibDataManager.Watcher<String, TdApi.Outline, TdlibOutlineManager.Entry> {
    void onStickerOutlineLoaded (TdlibOutlineManager context, TdlibOutlineManager.Entry entry);

    @SuppressWarnings("ClassEscapesDefinedScope")
    @Override
    default void onEntryLoaded (TdlibDataManager<String, TdApi.Outline, TdlibOutlineManager.Entry> context, TdlibOutlineManager.Entry entry) {
      onStickerOutlineLoaded((TdlibOutlineManager) context, entry);
    }
  }

  public TdlibOutlineManager (Tdlib tdlib) {
    super(tdlib);
  }

  public void requestStickerOutline (int stickerFileId, boolean forAnimatedEmoji, boolean forClickedAnimatedEmojiMessage, Watcher watcher) {
    requestStickerOutline(stickerFileId, forAnimatedEmoji, forClickedAnimatedEmojiMessage, watcher, false);
  }

  public void requestStickerOutline (int stickerFileId, boolean forAnimatedEmoji, boolean forClickedAnimatedEmojiMessage, Watcher watcher, boolean strongReference) {
    @StickerOutlineOption int option =
      BitwiseUtils.optional(StickerOutlineOption.FOR_ANIMATED_EMOJI, forAnimatedEmoji) |
        BitwiseUtils.optional(StickerOutlineOption.FOR_CLICKED_ANIMATED_EMOJI_MESSAGE, forClickedAnimatedEmojiMessage);
    String key = new ParsedKey(ParsedKey.TYPE_STICKER_OUTLINE, stickerFileId, option).serializeToKey();
    findOrPostponeRequest(key, watcher, strongReference);
  }

  public void requestWebAppPlaceholder (long botUserId, Watcher watcher) {
    requestWebAppPlaceholder(botUserId, watcher, false);
  }

  public void requestWebAppPlaceholder (long botUserId, Watcher watcher, boolean strongReference) {
    String key = new ParsedKey(ParsedKey.TYPE_WEB_APP_PLACEHOLDER, botUserId, 0).serializeToKey();
    findOrPostponeRequest(key, watcher, strongReference);
    performPostponedRequestsDelayed();
  }

  @Override
  protected Entry newEntry (@NonNull String key, @Nullable TdApi.Outline value, @Nullable TdApi.Error error) {
    return new Entry(key, value, error);
  }

  private static class ParsedKey {
    public static final int TYPE_STICKER_OUTLINE = 1;
    public static final int TYPE_WEB_APP_PLACEHOLDER = 2;

    public final int type;
    public final long arg1;
    public final int options;

    public ParsedKey (int type, long arg1, int options) {
      this.type = type;
      this.arg1 = arg1;
      this.options = options;
    }

    public String serializeToKey () {
      return type + "_" + arg1 + "_" + options;
    }

    public static ParsedKey parse (String key) {
      String[] data = key.split("_");
      final int type = StringUtils.parseInt(data[0]);
      final long arg1 = StringUtils.parseLong(data[1]);
      final int options = StringUtils.parseInt(data[2]);
      return new ParsedKey(type, arg1, options);
    }

    public TdApi.Function<TdApi.Outline> toRequest () {
      switch (type) {
        case TYPE_STICKER_OUTLINE: {
          return new TdApi.GetStickerOutline((int) arg1,
            BitwiseUtils.hasFlag(options, StickerOutlineOption.FOR_ANIMATED_EMOJI),
            BitwiseUtils.hasFlag(options, StickerOutlineOption.FOR_CLICKED_ANIMATED_EMOJI_MESSAGE)
          );
        }
        case TYPE_WEB_APP_PLACEHOLDER: {
          return new TdApi.GetWebAppPlaceholder(arg1);
        }
        default:
          throw new IllegalStateException();
      }
    }
  }

  @Override
  protected void requestData (int contextId, Collection<String> keysToRequest) {
    for (String key : keysToRequest) {
      ParsedKey parsedKey = ParsedKey.parse(key);
      tdlib.send(parsedKey.toRequest(), (outline, error) -> {
        if (isCancelled(contextId)) {
          return;
        }
        if (error != null) {
          processError(contextId, key, error);
        } else {
          processData(contextId, key, outline);
        }
      });
    }
  }
}
