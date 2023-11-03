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
 *
 * File created on 03/05/2015 at 11:02
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.EmojiInfo;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifBridge;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.telegram.AnimatedEmojiListener;
import org.thunderdog.challegram.telegram.TdlibEmojiManager;
import org.thunderdog.challegram.telegram.TdlibThread;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.NonBubbleEmojiLayout;
import org.thunderdog.challegram.util.text.TextMedia;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import me.vkryl.core.MathUtils;
import me.vkryl.core.collection.IntSet;
import me.vkryl.core.collection.LongSet;
import me.vkryl.td.Td;
import me.vkryl.td.TdConstants;

public class TGMessageSticker extends TGMessage implements AnimatedEmojiListener, TdlibEmojiManager.Watcher {
  private @Nullable TdApi.DiceStickers sticker;
  private @Nullable TdApi.FormattedText formattedText;
  private @Nullable NonBubbleEmojiLayout multiEmojiLayout;

  private List<Representation> representation;

  private class Representation {
    public final long stickerId;
    public final String emoji;
    public final @Nullable EmojiInfo emojiInfo;

    public @Nullable TdApi.Sticker sticker;
    public float xIndex;
    public int yIndex;
    public int width, height;
    public Path outline;

    @Nullable
    private ImageFile preview;

    @Nullable
    private ImageFile staticFile;
    @Nullable
    private GifFile animatedFile;

    public boolean needThemedColorFilter;

    public Representation (@NonNull TdApi.Sticker sticker, int fitzpatrickType, boolean allowNoLoop, boolean forcePlayOnce) {
      this(sticker.id, sticker.emoji, sticker, fitzpatrickType, allowNoLoop, forcePlayOnce);
    }

    public Representation (long stickerId, String emoji, @Nullable TdApi.Sticker sticker, int fitzpatrickType, boolean allowNoLoop, boolean forcePlayOnce) {
      this.stickerId = stickerId;
      this.emoji = emoji;
      this.emojiInfo = stickerId == 0 ? Emoji.instance().getEmojiInfo(emoji) : null;
      setSticker(sticker, fitzpatrickType, allowNoLoop, forcePlayOnce);
    }

    public void setSticker (@Nullable TdApi.Sticker sticker, int fitzpatrickType, boolean allowNoLoop, boolean forcePlayOnce) {
      if (sticker == null || sticker.id != stickerId) {
        return;
      }
      this.sticker = sticker;
      this.needThemedColorFilter = TD.needThemedColorFilter(sticker);
      this.animatedFile = null;
      this.staticFile = null;
      this.preview = null;

      if (fitzpatrickType == 0 || !Td.isAnimated(sticker.format)) {
        this.preview = TD.toImageFile(tdlib, sticker.thumbnail);
        if (this.preview != null) {
          this.preview.setScaleType(ImageFile.FIT_CENTER);
        }
      }

      if (Td.isAnimated(sticker.format)) {
        this.animatedFile = new GifFile(tdlib, sticker);
        this.animatedFile.setScaleType(GifFile.CENTER_CROP);
        this.animatedFile.setFitzpatrickType(fitzpatrickType);
        if (allowNoLoop) {
          this.animatedFile.setPlayOnce(
            forcePlayOnce ||
            (specialType != SPECIAL_TYPE_NONE && !(specialType == SPECIAL_TYPE_ANIMATED_EMOJI && Td.customEmojiId(sticker) != 0)) ||
            Settings.instance().getNewSetting(Settings.SETTING_FLAG_NO_ANIMATED_STICKERS_LOOP)
          );
          if (specialType == SPECIAL_TYPE_DICE) {
            if (!isBeingAdded() || isForward()) {
              this.animatedFile.setDecodeLastFrame(true);
              this.animatedFile.setIsStill(true);
              this.preview = null;
            } else if (dice.value != 0) {
              GifFile prevFile = animatedFile;
              prevFile.addLoopListener(() -> {
                int index = representation != null ? representation.indexOf(Representation.this) : -1;
                if (index != -1) {
                  this.preview = null;
                  // TODO invalidate preview receiver
                  // invalidatePreviewReceiver();
                }
              });
            }
          }
        } else if (forcePlayOnce) {
          this.animatedFile.setPlayOnce(true);
        }
      } else {
        this.staticFile = new ImageFile(tdlib, sticker.sticker);
        this.staticFile.setScaleType(ImageFile.FIT_CENTER);
        this.staticFile.setWebp();
      }

      if (width > 0 && height > 0) {
        setSize(width, height);
      }
    }

    public boolean isAnimated () {
      return sticker != null && Td.isAnimated(sticker.format);
    }

    public boolean hasAnimationEnded () {
      return (isAnimated() && animatedFile != null && animatedFile.isPlayOnce() && animatedFile.hasLooped() && !animatedFile.needDecodeLastFrame());
    }

    public void requestFiles (int key, ComplexReceiver receiver, boolean invalidate) {
      if (sticker == null) {
        receiver.getGifReceiver(key).requestFile(null);
        receiver.getImageReceiver(key).requestFile(null);
        receiver.getPreviewReceiver(key).clear();
        return;
      }
      //if (!invalidate) {
        DoubleImageReceiver previewReceiver = receiver.getPreviewReceiver(key);
        if (preview == null || hasAnimationEnded()) {
          previewReceiver.clear();
        } else if (!isAnimated() && TD.isFileLoaded(sticker.sticker)) {
          previewReceiver.clear();
          preview = null;
        } else {
          previewReceiver.requestFile(null, preview);
        }
      //}
      GifFile file = receiver.getGifReceiver(key).getCurrentFile();
      if (file != animatedFile) {
        receiver.getGifReceiver(key).requestFile(null);         // The new file may have the same id as
        receiver.getGifReceiver(key).requestFile(animatedFile); // old file, but a different requestedSize
      }
      receiver.getImageReceiver(key).requestFile(staticFile);
    }

    public void setSize (int width, int height) {
      this.width = width;
      this.height = height;
      if (outline != null) {
        outline.reset();
      }
      if (sticker != null) {
        outline = Td.buildOutline(sticker, width, height, outline);
        if (staticFile != null) {
          staticFile.setSize(Math.max(width, height));
        }
        if (animatedFile != null) {
          animatedFile.setRequestedSize(Math.max(width, height));
        }
      }
    }
  }

  private static final int SPECIAL_TYPE_NONE = 0;
  private static final int SPECIAL_TYPE_ANIMATED_EMOJI = 1;
  private static final int SPECIAL_TYPE_DICE = 2;

  private int stickerWidth, stickerHeight;
  private int stickerRowsCount;
  private float stickersMaxRowSize;
  private final int specialType;
  private TdApi.MessageDice dice;
  private TdApi.MessageContent currentEmoji, animatedEmoji, pendingEmoji;

  public TGMessageSticker (MessagesManager context, TdApi.Message msg, TdApi.MessageDice dice) {
    super(context, msg);
    this.specialType = SPECIAL_TYPE_DICE;
    setDice(dice, false);
    tdlib.listeners().subscribeToAnimatedEmojiUpdates(this);
  }

  public TGMessageSticker (MessagesManager context, TdApi.Message msg, TdApi.MessageContent content, TdApi.MessageContent pendingContent) {
    super(context, msg);
    this.animatedEmoji = checkContent(content);
    this.pendingEmoji = checkContent(pendingContent);
    this.specialType = SPECIAL_TYPE_ANIMATED_EMOJI;
    updateAnimatedEmoji();
  }

  private boolean updateAnimatedEmoji () {
    TdApi.MessageContent messageContent = pendingEmoji != null ? pendingEmoji : animatedEmoji;
    if (this.currentEmoji != messageContent && !(this.currentEmoji != null && messageContent == null)) {
      this.currentEmoji = messageContent;
      if (messageContent.getConstructor() == TdApi.MessageAnimatedEmoji.CONSTRUCTOR) {
        TdApi.MessageAnimatedEmoji emoji = (TdApi.MessageAnimatedEmoji) messageContent;
        if (emoji.animatedEmoji.sticker != null) {
          setSticker(new TdApi.DiceStickersRegular(emoji.animatedEmoji.sticker), emoji.animatedEmoji.fitzpatrickType, false, true);
          return true;
        }
      } else if (messageContent.getConstructor() == TdApi.MessageText.CONSTRUCTOR) {
        TdApi.MessageText text = (TdApi.MessageText) messageContent;
        setStickers(text.text);
        return true;
      }

      // wait for updateMessageContent
      setSticker(null, 0, false, true);
      return true;
    }

    return false;
  }

  private void setDice (TdApi.MessageDice dice, boolean isUpdate) {
    this.dice = dice;
    TdApi.DiceStickers targetSticker = tdlib.findDiceEmoji(dice.emoji, dice.value, dice.finalState != null ? dice.finalState : dice.initialState);
    if (targetSticker != null) {
      setSticker(targetSticker, 0, isUpdate, dice.finalState != null);
    }
  }

  @Override
  protected void onMessageContainerDestroyed () {
    if (specialType != SPECIAL_TYPE_NONE) {
      tdlib.listeners().unsubscribeFromAnimatedEmojiUpdates(this);
    }
  }

  @Override
  public void onAnimatedEmojiChanged (int type) {
    if (specialType == SPECIAL_TYPE_DICE && type == AnimatedEmojiListener.TYPE_DICE) {
      tdlib.ui().post(() -> {
        if (!isDestroyed()) {
          setDice(dice, true);
          invalidateContentReceiver();
        }
      });
    } else if (specialType == SPECIAL_TYPE_ANIMATED_EMOJI && type == AnimatedEmojiListener.TYPE_EMOJI) {
      // TODO
    }
  }

  @Override
  protected int onMessagePendingContentChanged (long chatId, long messageId, int oldHeight) {
    if (specialType == SPECIAL_TYPE_ANIMATED_EMOJI) {
      TdApi.MessageContent content = tdlib.getPendingMessageText(chatId, messageId);
      if ((content == null && animatedEmoji == null)) {
        return MESSAGE_REPLACE_REQUIRED;
      }
      if (content != null) {
        if (animatedEmoji != null && content.getConstructor() != animatedEmoji.getConstructor()) {
          return MESSAGE_REPLACE_REQUIRED;
        }
        if (content.getConstructor() == TdApi.MessageText.CONSTRUCTOR && !NonBubbleEmojiLayout.isValidEmojiText(((TdApi.MessageText) content).text)) {
          return MESSAGE_REPLACE_REQUIRED;
        }
        if (content.getConstructor() != TdApi.MessageAnimatedEmoji.CONSTRUCTOR && content.getConstructor() != TdApi.MessageText.CONSTRUCTOR) {
          return MESSAGE_REPLACE_REQUIRED;
        }
      }
      this.pendingEmoji = checkContent(content);
      if (updateAnimatedEmoji()) {
        rebuildContent();
        invalidateContentReceiver();
        return (getHeight() == oldHeight ? MESSAGE_INVALIDATED : MESSAGE_CHANGED);
      }
    }
    return super.onMessagePendingContentChanged(chatId, messageId, oldHeight);
  }

  @Override
  protected boolean updateMessageContent (TdApi.Message message, TdApi.MessageContent newContent, boolean isBottomMessage) {
    if (specialType == SPECIAL_TYPE_DICE && Td.isDice(newContent)) {
      List<Representation> prevRepresentation = this.representation;
      TdApi.MessageDice newDice = (TdApi.MessageDice) newContent;
      boolean hadFinalState = this.dice != null && this.dice.finalState != null;
      boolean hadInitialState = this.dice != null && this.dice.initialState != null;
      boolean hasFinalState = newDice.finalState != null;
      setDice(newDice, true);
      if (hadInitialState && !hadFinalState && hasFinalState && sticker != null) {
        IntSet fileIds = new IntSet();
        switch (sticker.getConstructor()) {
          case TdApi.DiceStickersRegular.CONSTRUCTOR: {
            TdApi.DiceStickersRegular regular = (TdApi.DiceStickersRegular) sticker;
            fileIds.add(regular.sticker.sticker.id);
            break;
          }
          case TdApi.DiceStickersSlotMachine.CONSTRUCTOR: {
            TdApi.DiceStickersSlotMachine slotMachine = (TdApi.DiceStickersSlotMachine) sticker;
            fileIds.add(slotMachine.background.sticker.id);
            fileIds.add(slotMachine.leftReel.sticker.id);
            fileIds.add(slotMachine.centerReel.sticker.id);
            fileIds.add(slotMachine.rightReel.sticker.id);
            fileIds.add(slotMachine.lever.sticker.id);
            break;
          }
          default: {
            Td.assertDiceStickers_bd2aa513();
            throw Td.unsupported(sticker);
          }
        }
        AtomicInteger finished = new AtomicInteger(fileIds.size());
        Client.ResultHandler handler = result -> {
          if (finished.decrementAndGet() == 0) {
            tdlib.ui().post(() -> {
              if (!isDestroyed()) {
                if (prevRepresentation == null || !hasAnyTargetToInvalidate() || representation == null || prevRepresentation.size() != representation.size()) {
                  invalidateContentReceiver();
                  return;
                }

                if (prevRepresentation != null && hasAnyTargetToInvalidate()) {
                  for (int index = 0; index < prevRepresentation.size(); index++) {
                    Representation prevFile = prevRepresentation.get(index);
                    Representation newFile = representation != null && index < representation.size() ? representation.get(index) : null;
                    final int indexFinal = index;
                    if (prevFile.animatedFile == null) {
                      invalidateContentReceiver(index);
                    } else if (newFile == null || newFile.animatedFile == null) {
                      prevFile.animatedFile.addLoopListener(() -> invalidateContentReceiver(indexFinal));
                    } else {
                      GifBridge.instance().loadFile(newFile.animatedFile, reference -> {
                        if (isDestroyed() || !hasAnyTargetToInvalidate()) {
                          GifBridge.instance().removeWatcher(reference);
                          invalidateContentReceiver(indexFinal);
                          return;
                        }
                        prevFile.animatedFile.addLoopListener(() -> {
                          if (newFile.animatedFile.hasFrame(dice.successAnimationFrameNumber)) {
                            newFile.animatedFile.setFrameChangeListener((file, frameNo, frameDelta) -> {
                              if (dice.successAnimationFrameNumber >= frameNo && dice.successAnimationFrameNumber < frameNo + frameDelta) {
                                tdlib.ui().post(() -> {
                                  if (!isDestroyed()) {
                                    View view = currentViews.findAnyTarget();
                                    if (view != null) {
                                      GifReceiver receiver = ((MessageView) view).getComplexReceiver().getGifReceiver(0);
                                      if (receiver != null) {
                                        performConfettiAnimation(receiver.centerX(), receiver.centerY());
                                      }
                                    }
                                  }
                                });
                                file.setFrameChangeListener(null);
                              }
                            });
                          }
                          invalidateContentReceiver(indexFinal);
                          GifBridge.instance().removeWatcher(reference);
                        });
                      });
                    }
                  }
                }
              }
            });
          }
        };
        for (Integer fileId : fileIds) {
          tdlib.client().send(new TdApi.DownloadFile(fileId, 1, 0, 0, true), handler);
        }
      } else {
        invalidateContentReceiver();
      }
    } else if (specialType == SPECIAL_TYPE_ANIMATED_EMOJI) {
      this.animatedEmoji = checkContent(newContent);
      if (updateAnimatedEmoji()) {
        rebuildContent();
        invalidateContentReceiver();
      }
      return true;
    }
    return false;
  }

  protected boolean isSupportedMessageContent (TdApi.Message message, TdApi.MessageContent messageContent) {
    final @EmojiMessageContentType int contentType = getEmojiMessageContentType(messageContent);
    if (contentType == EmojiMessageContentType.NOT_EMOJI) {
      return false;
    }
    return super.isSupportedMessageContent(message, messageContent);
  }

  public TGMessageSticker (MessagesManager context, TdApi.Message msg, TdApi.Sticker sticker, boolean isAnimatedEmoji, int fitzpatrickType) {
    super(context, msg);
    this.specialType = isAnimatedEmoji ? SPECIAL_TYPE_ANIMATED_EMOJI : SPECIAL_TYPE_NONE;
    setSticker(new TdApi.DiceStickersRegular(sticker), fitzpatrickType, false, true);
  }

  @Override
  public void markAsBeingAdded (boolean isBeingAdded) {
    super.markAsBeingAdded(isBeingAdded);
    if (dice != null && dice.finalState != null) {
      setSticker(sticker, 0, false, true);
    }
  }

  private void setStickers (TdApi.FormattedText text) {
    this.formattedText = text;
    this.sticker = null;
    this.representation = new ArrayList<>();
    this.multiEmojiLayout = NonBubbleEmojiLayout.create(text);
    if (multiEmojiLayout != null) {
      for (NonBubbleEmojiLayout.Item emojiR: multiEmojiLayout.items) {
        if (emojiR.type == NonBubbleEmojiLayout.Item.EMOJI) {
          TdlibEmojiManager.Entry entry = emojiR.customEmojiId != 0 ?
            tdlib.emoji().findOrPostponeRequest(emojiR.customEmojiId, this) : null;
          representation.add(new Representation(emojiR.customEmojiId, emojiR.emoji, entry != null ? entry.value : null, 0, true, false));
        }
      }
      tdlib.emoji().performPostponedRequests();
    }
  }

  private void setSticker (@Nullable TdApi.DiceStickers sticker, int fitzpatrickType, boolean isUpdate, boolean allowNoLoop) {
    this.sticker = sticker;
    this.formattedText = null;
    final List<Representation> representation = new ArrayList<>();
    if (sticker != null) {
      switch (sticker.getConstructor()) {
        case TdApi.DiceStickersRegular.CONSTRUCTOR: {
          TdApi.DiceStickersRegular regular = (TdApi.DiceStickersRegular) sticker;
          representation.add(new Representation(regular.sticker, fitzpatrickType, allowNoLoop, false));
          break;
        }
        case TdApi.DiceStickersSlotMachine.CONSTRUCTOR: {
          TdApi.DiceStickersSlotMachine slotMachine = (TdApi.DiceStickersSlotMachine) sticker;
          representation.add(new Representation(slotMachine.background, fitzpatrickType, allowNoLoop, true));
          representation.add(new Representation(slotMachine.leftReel, fitzpatrickType, allowNoLoop, false));
          representation.add(new Representation(slotMachine.centerReel, fitzpatrickType, allowNoLoop, false));
          representation.add(new Representation(slotMachine.rightReel, fitzpatrickType, allowNoLoop, false));
          representation.add(new Representation(slotMachine.lever, fitzpatrickType, allowNoLoop, true));
          break;
        }
        default: {
          Td.assertDiceStickers_bd2aa513();
          throw Td.unsupported(sticker);
        }
      }
    }
    this.multiEmojiLayout = null;
    this.representation = representation;
  }

  public static final float MAX_STICKER_FORWARD_SIZE = 120f;
  public static final float MAX_STICKER_SIZE = 190f;

  @Nullable
  private TdApi.Sticker getBaseSticker () {
    if (sticker == null)
      return null;
    switch (sticker.getConstructor()) {
      case TdApi.DiceStickersRegular.CONSTRUCTOR:
        return ((TdApi.DiceStickersRegular) sticker).sticker;
      case TdApi.DiceStickersSlotMachine.CONSTRUCTOR:
        return ((TdApi.DiceStickersSlotMachine) sticker).background;
    }
    Td.assertDiceStickers_bd2aa513();
    throw Td.unsupported(sticker);
  }

  private long getStickerSetId () {
    TdApi.Sticker sticker = specialType == SPECIAL_TYPE_NONE ? getBaseSticker() : null;
    return sticker != null ? sticker.setId : 0;
  }

  @Override
  protected void buildContent (int origMaxWidth) {
    final TdApi.Sticker sticker = getBaseSticker();
    float max = Screen.dp(useForward() ? MAX_STICKER_FORWARD_SIZE : MAX_STICKER_SIZE);
    if (specialType != SPECIAL_TYPE_NONE || (sticker != null && sticker.setId == TdConstants.TELEGRAM_ANIMATED_EMOJI_STICKER_SET_ID)) { // TODO check for dice sticker set id
      max *= tdlib.emojiesAnimatedZoom();
    }
    if (sticker != null) {
      float ratio = Math.min(max / (float) sticker.width, max / (float) sticker.height);
      stickerWidth = (int) (sticker.width * ratio);
      stickerHeight = (int) (sticker.height * ratio);
    } else {
      stickerWidth = stickerHeight = 0;
    }

    if (stickerWidth == 0 && stickerHeight == 0) {
      stickerWidth = stickerHeight = (int) max;
    }

    if (specialType == SPECIAL_TYPE_ANIMATED_EMOJI && currentEmoji != null && currentEmoji.getConstructor() == TdApi.MessageText.CONSTRUCTOR) {
      if (multiEmojiLayout == null) {
        throw new IllegalArgumentException();
      }

      final int minEmojiSize = Screen.dp(30);
      final int maxRowSize = origMaxWidth / minEmojiSize;
      NonBubbleEmojiLayout.LayoutBuildResult layout = multiEmojiLayout.layout(maxRowSize, 0.2f);

      for (int a = 0; a < layout.representations.size(); a++) {
        NonBubbleEmojiLayout.Representation emojiR = layout.representations.get(a);
        if (representation != null && representation.size() > a) {
          Representation repr = representation.get(a);
          repr.xIndex = emojiR.x;
          repr.yIndex = emojiR.y;
        }
      }

      stickersMaxRowSize = layout.maxLineSize;
      stickerRowsCount = layout.linesCount;

      float realMaxWidth = MathUtils.fromTo(max, origMaxWidth, MathUtils.clamp(stickersMaxRowSize / maxRowSize));
      stickerWidth = stickerHeight = (int) Math.min(realMaxWidth / stickersMaxRowSize, Math.max(max / stickerRowsCount, minEmojiSize));
      if (layout.hasClassicEmoji) {
        stickerWidth = stickerHeight = Math.min(stickerWidth, Screen.dp(40));
      }
    } else {
      stickersMaxRowSize = stickerRowsCount = 1;
    }
    if (representation != null) {
      for (Representation obj : representation) {
        obj.setSize(stickerWidth, stickerHeight);
      }
    }
  }

  @Override
  protected int getContentWidth () {
    return (int) (stickerWidth * stickersMaxRowSize);
  }

  @Override
  protected int getBubbleTimePartOffsetY () {
    return Screen.dp(4f);
  }

  @Override
  protected int getContentHeight () {
    final boolean isInMultiEmojiMode = stickersMaxRowSize > 1 || stickerRowsCount > 1;
    return Math.max(isInMultiEmojiMode ? 0 : Screen.dp(56f), stickerHeight * stickerRowsCount)
      + ((specialType == SPECIAL_TYPE_DICE || isInMultiEmojiMode) && useBubbles() && !useForward() ?
      getBubbleTimePartOffsetY() + getBubbleTimePartHeight() + Screen.dp(2f) : 0);
  }

  @Override
  public boolean needComplexReceiver () {
    return true;
  }

  @Override
  protected boolean disableBubble () {
    return true;
  }

  @Override
  protected int getBubbleContentPadding () {
    return 0;
  }

  @Override
  protected boolean drawBubbleTimeOverContent () {
    return true;
  }

  @Override
  protected int getAbsolutelyRealRightContentEdge (View view, int timePartWidth) {
    if (msg.forwardInfo != null) {
      return super.getAbsolutelyRealRightContentEdge(view, timePartWidth);
    } else {
      int left = findStickerLeft();
      int desiredLeft = (int) (left + stickerWidth * stickersMaxRowSize - timePartWidth);
      if (!useBubbles() || isOutgoingBubble()) {
        return desiredLeft;
      } else {
        return Math.max(left, desiredLeft);
      }
    }
  }

  private int findStickerLeft () {
    return isOutgoingBubble() ? (int) (useBubble() ? getContentX() : getActualRightContentEdge() - stickerWidth * stickersMaxRowSize) : getContentX();
  }

  private final static Rect tmpRect = new Rect();

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth, ComplexReceiver receiver) {
    int leftDefault = findStickerLeft();
    int topDefault = getContentY();

    if (representation != null) {
      int index = 0;
      for (Representation representation : representation) {
        int left = (int) (leftDefault + stickerWidth * (representation.xIndex));
        int top = topDefault + stickerHeight * (representation.yIndex);

      }

      boolean needScale = representation.size() > 1 && specialType == SPECIAL_TYPE_ANIMATED_EMOJI;
      for (int a = 0; a < 3; a++) {
        index = 0;
        for (Representation representation : representation) {
          final boolean isTgsSticker = representation.sticker != null && representation.sticker.format.getConstructor() == TdApi.StickerFormatTgs.CONSTRUCTOR;
          final float scale = needScale && representation.sticker != null ? TextMedia.getScale(representation.sticker, stickerWidth) : 1f;
          int left = (int) (leftDefault + stickerWidth * (representation.xIndex));
          int top = topDefault + stickerHeight * (representation.yIndex);
          int right = left + stickerWidth;
          int bottom = top + stickerHeight;

          final int saveScaleToCount;
          boolean needRestore = scale != 1f;
          if (needRestore) {
            saveScaleToCount = Views.save(c);
            c.scale(scale, scale, left + stickerWidth / 2f, top + stickerHeight / 2f);
          } else {
            saveScaleToCount = -1;
          }

          if (a == 0 && representation.outline != null) {
            DoubleImageReceiver preview = receiver.getPreviewReceiver(index);
            Receiver target = representation.isAnimated() ? receiver.getGifReceiver(index) : receiver.getImageReceiver(index);
            if (target.needPlaceholder() && preview.needPlaceholder()) {
              final int saveCount = Views.save(c);
              c.translate(left, top);
              c.drawPath(representation.outline, Paints.getPlaceholderPaint());
              Views.restore(c, saveCount);
            }
          }

          if (isTgsSticker && a == 2 || !isTgsSticker && a == 1) {
            if (representation.sticker == null && representation.emojiInfo != null) {
              tmpRect.set(left + Screen.dp(2), top + Screen.dp(2), right - Screen.dp(2), bottom - Screen.dp(2));
              Emoji.instance().draw(c, representation.emojiInfo, tmpRect);
            } else {
              DoubleImageReceiver preview = receiver.getPreviewReceiver(index);
              final Receiver target;
              if (representation.isAnimated()) {
                target = receiver.getGifReceiver(index);
              } else {
                target = receiver.getImageReceiver(index);
              }
              if (representation.needThemedColorFilter) {
                int color = getTextColorSet().mediaTextColorOrId();
                if (getTextColorSet().mediaTextColorIsId()) {
                  preview.setThemedPorterDuffColorId(color);
                  target.setThemedPorterDuffColorId(color);
                } else {
                  preview.setPorterDuffColorFilter(color);
                  target.setPorterDuffColorFilter(color);
                }
              } else {
                preview.disablePorterDuffColorFilter();
                target.disablePorterDuffColorFilter();
              }
              DrawAlgorithms.drawReceiver(c, preview, target, !representation.isAnimated(), false, left, top, right, bottom);
            }
          }
          if (a == 2) {
            if (Config.DEBUG_STICKER_OUTLINES) {
              if (representation.outline != null) {
                final int saveCount = Views.save(c);
                c.translate(left, top);
                c.drawPath(representation.outline, Paints.fillingPaint(0x99ff0000));
                Views.restore(c, saveCount);
              }
            }
          }
          if (needRestore) {
            Views.restore(c, saveScaleToCount);
          }
          index++;
        }
      }
    }
  }

  private void checkDice () {
    if (specialType == SPECIAL_TYPE_DICE && hasAnimationEnded() && dice != null && dice.value != 0) {
      super.markAsBeingAdded(false);
      setSticker(sticker, 0, false, true);
    }
  }

  private boolean hasAnimationEnded () {
    if (representation != null && !representation.isEmpty()) {
      for (Representation representation : representation) {
        if (!representation.hasAnimationEnded()) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  @Override
  public void requestMediaContent (ComplexReceiver receiver, boolean invalidate, int invalidateArg) {
    if (representation != null && !representation.isEmpty()) {
      if (invalidateArg >= 0 && invalidateArg < representation.size()) {
        representation.get(invalidateArg).requestFiles(invalidateArg, receiver, invalidate);
      } else {
        int index = 0;
        for (Representation representation : representation) {
          representation.requestFiles(index, receiver, invalidate);
          index++;
        }
        receiver.clearReceiversWithHigherKey(index);
      }
    } else {
      receiver.clear();
    }
  }

  private boolean isCaught;
  private float startX, startY;

  @Override
  public boolean performLongPress (View view, float x, float y) {
    boolean res = super.performLongPress(view, x, y);
    isCaught = false;
    return res;
  }

  public boolean needSuggestOpenStickerPack () {
    return getBaseSticker() != null && getStickerSetId() != 0 && Td.isAnimated(getBaseSticker().format) && Settings.instance().getNewSetting(Settings.SETTING_FLAG_NO_ANIMATED_STICKERS_LOOP) && getStickerSetId() != 0;
  }

  public void openStickerSet () {
    TdApi.Sticker sticker = getBaseSticker();
    if (sticker == null) {
      return;
    }
    tdlib.ui().showStickerSet(controller(), sticker.setId, null);
  }

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    if (super.onTouchEvent(view, e)) {
      return true;
    }

    float x = e.getX();
    float y = e.getY();

    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        int left = findStickerLeft();
        int top = getContentY();
        int right = (int) (left + stickerWidth * stickersMaxRowSize);
        int bottom = getContentY() + stickerHeight * stickerRowsCount;
        if (isCaught = (sticker != null && (x >= left && x < right && y >= top && y < bottom))) {
          startX = x;
          startY = y;
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        if (isCaught) {
          isCaught = false;
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (isCaught && Math.max(Math.abs(x - startX), Math.abs(y - startY)) > Screen.getTouchSlop()) {
          isCaught = false;
        }
        return isCaught;
      }
      case MotionEvent.ACTION_UP: {
        if (isCaught) {
          isCaught = false;
          boolean tapProcessed = false;
          TdApi.Sticker sticker = getBaseSticker();
          switch (specialType) {
            case SPECIAL_TYPE_DICE: {
              tapProcessed = true;
              ImageFile imageFile;
              GifFile gifFile;
              if (sticker != null && Td.isAnimated(sticker.format) && this.sticker != null && this.sticker.getConstructor() != TdApi.DiceStickersSlotMachine.CONSTRUCTOR) {
                gifFile = new GifFile(tdlib, sticker);
                gifFile.setOptimizationMode(GifFile.OptimizationMode.STICKER_PREVIEW);
                gifFile.setScaleType(GifFile.CENTER_CROP);
                gifFile.setUnique(true);
                gifFile.setPlayOnce(dice.value != 0);
                imageFile = TD.toImageFile(tdlib, sticker.thumbnail);
              } else {
                gifFile = null;
                imageFile = null;
              }
              context().tooltipManager().builder(view, currentViews)
                .locate((targetView, outRect) -> {
                  GifReceiver receiver = ((MessageView) targetView).getComplexReceiver().getGifReceiver(0);
                  if (receiver != null) {
                    outRect.set(receiver.getLeft(), receiver.getTop(), receiver.getRight(), receiver.getBottom());
                    outRect.top += outRect.height() * (ContentPreview.EMOJI_DICE.textRepresentation.equals(dice.emoji) ? .35f : .20f);
                  }
                })
                .gif(gifFile, imageFile)
                .controller(controller())
                .show(tdlib, Lang.getString(ContentPreview.EMOJI_DART.textRepresentation.equals(dice.emoji) ? R.string.SendDartHint : ContentPreview.EMOJI_DICE.textRepresentation.equals(dice.emoji) ? R.string.SendDiceHint : R.string.SendUnknownDiceHint, dice.emoji));
              break;
            }
            case SPECIAL_TYPE_ANIMATED_EMOJI:
            default: {
              tapProcessed = openOrLoopSticker(view, sticker, Settings.instance().getNewSetting(Settings.SETTING_FLAG_NO_ANIMATED_STICKERS_LOOP));
              break;
            }
          }
          if (tapProcessed) {
            performClickSoundFeedback();
            return true;
          }
        }
        break;
      }
    }

    return isCaught;
  }

  private boolean openOrLoopSticker (MessageView view, TdApi.Sticker sticker, boolean noLoopSettingEnabled) {
    final boolean isAnimatedEmojiStickerSet = sticker != null && sticker.setId == TdConstants.TELEGRAM_ANIMATED_EMOJI_STICKER_SET_ID;

    GifFile animatedFile = view.getComplexReceiver().getGifReceiver(0).getCurrentFile();
    if (animatedFile != null && (noLoopSettingEnabled || isAnimatedEmojiStickerSet) && animatedFile.setLooped(false)) {
      invalidate();
      return true;
    }
    if (sticker != null && sticker.setId != 0 && (!isAnimatedEmojiStickerSet || specialType != SPECIAL_TYPE_ANIMATED_EMOJI)) {
      openStickerSet();
      return true;
    }
    return false;
  }

  @Override
  public long getFirstEmojiId () {
    if (formattedText != null && formattedText.entities != null) {
      for (TdApi.TextEntity entity: formattedText.entities) {
        if (entity.type.getConstructor() == TdApi.TextEntityTypeCustomEmoji.CONSTRUCTOR) {
          return ((TdApi.TextEntityTypeCustomEmoji) entity.type).customEmojiId;
        }
      }
    }
    if (sticker != null && sticker.getConstructor() == TdApi.DiceStickersRegular.CONSTRUCTOR) {
      TdApi.Sticker sticker1 = ((TdApi.DiceStickersRegular) sticker).sticker;
      return Td.customEmojiId(sticker1);
    }
    return 0;
  }

  @Override
  public long[] getUniqueEmojiPackIdList () {
    if (formattedText != null) {
      long[] emojiIds = TD.getUniqueEmojiIdList(formattedText);
      LongSet emojiSets = new LongSet();
      for (long emojiId : emojiIds) {
        TdlibEmojiManager.Entry entry = tdlib().emoji().find(emojiId);
        if (entry == null || entry.value == null) continue;
        emojiSets.add(entry.value.setId);
      }
      return emojiSets.toArray();
    }

    if (sticker != null && sticker.getConstructor() == TdApi.DiceStickersRegular.CONSTRUCTOR) {
      TdApi.Sticker sticker1 = ((TdApi.DiceStickersRegular) sticker).sticker;
      if (Td.customEmojiId(sticker1) != 0) {
        return new long[] {
          sticker1.setId
        };
      }
    }

    return new long[0];
  }

  @TdlibThread
  @Override
  public void onCustomEmojiLoaded (TdlibEmojiManager context, TdlibEmojiManager.Entry entry) {
    final TdApi.Sticker sticker = entry.value;
    if (sticker == null) return;

    UI.post(() -> {
      if (representation != null) {
        boolean needInvalidate = false;
        for (Representation representation: representation) {
          if (sticker.id == representation.stickerId) {
            representation.setSticker(entry.value, 0, true, false);
            needInvalidate = true;
          }
        }
        if (needInvalidate) {
          invalidateContentReceiver();
        }
      }
    });
  }

  private static TdApi.MessageContent checkContent (TdApi.MessageContent content) {
    final boolean allowAnimatedEmoji = !Settings.instance().getNewSetting(Settings.SETTING_FLAG_NO_ANIMATED_EMOJI);
    return !allowAnimatedEmoji && TD.isStickerFromAnimatedEmojiPack(content) ?
      new TdApi.MessageText(Td.textOrCaption(content), null): content;
  }
}
