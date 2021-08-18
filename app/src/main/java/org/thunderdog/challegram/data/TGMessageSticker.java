/**
 * File created on 03/05/15 at 11:02
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifBridge;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.telegram.AnimatedEmojiListener;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import me.vkryl.core.collection.IntSet;
import me.vkryl.td.Td;
import me.vkryl.td.TdConstants;

public class TGMessageSticker extends TGMessage implements AnimatedEmojiListener {
  private TdApi.DiceStickers sticker;
  private Path outline;
  private List<Representation> representation;

  private class Representation {
    public final TdApi.Sticker sticker;

    @Nullable
    private ImageFile preview;

    @Nullable
    private ImageFile staticFile;
    @Nullable
    private GifFile animatedFile;

    public Representation (TdApi.Sticker sticker, @Nullable String colorReplacementKey, @Nullable int[] colorReplacement, boolean allowNoLoop, boolean forcePlayOnce) {
      this.sticker = sticker;

      this.preview = TD.toImageFile(tdlib, sticker.thumbnail);
      if (this.preview != null) {
        this.preview.setScaleType(ImageFile.FIT_CENTER);
      }

      if (sticker.isAnimated) {
        this.animatedFile = new GifFile(tdlib, sticker);
        this.animatedFile.setScaleType(GifFile.CENTER_CROP);
        this.animatedFile.setColorReplacement(colorReplacementKey, colorReplacement);
        if (allowNoLoop) {
          this.animatedFile.setPlayOnce(forcePlayOnce || specialType != SPECIAL_TYPE_NONE || Settings.instance().getNewSetting(Settings.SETTING_FLAG_NO_ANIMATED_STICKERS_LOOP));
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
        /*if (baseSticker.width == 0 || baseSticker.height == 0) {
          String path = TD.getFilePath(baseSticker.sticker);
          if (path != null) {
            BitmapFactory.Options opts = ImageReader.getImageWebpSize(path);
            if (opts != null) {
              baseSticker.width = opts.outWidth;
              baseSticker.height = opts.outHeight;
            }
          }
        }*/
      }
    }

    public boolean isAnimated () {
      return sticker.isAnimated;
    }

    public TdApi.ClosedVectorPath[] getOutline () {
      return sticker.outline;
    }

    public boolean hasAnimationEnded () {
      return (sticker.isAnimated && animatedFile != null && animatedFile.isPlayOnce() && animatedFile.hasLooped() && !animatedFile.needDecodeLastFrame());
    }

    public void requestFiles (int key, ComplexReceiver receiver, boolean invalidate) {
      /*@Override
        public void requestPreview (DoubleImageReceiver receiver) {
          if (stickerPreview == null || hasAnimationEnded()) {
            receiver.clear();
          } else if (!isAnimated && TD.isFileLoaded(sticker)) {
            receiver.clear();
            stickerPreview = null;
          } else {
            receiver.requestFile(null, stickerPreview);
          }
        }*/
      if (!invalidate) {
        DoubleImageReceiver previewReceiver = receiver.getPreviewReceiver(key);
        if (preview == null || hasAnimationEnded()) {
          previewReceiver.clear();
        } else if (!isAnimated() && TD.isFileLoaded(sticker.sticker)) {
          previewReceiver.clear();
          preview = null;
        } else {
          previewReceiver.requestFile(null, preview);
        }
      }
      if (isAnimated()) {
        receiver.getGifReceiver(key).requestFile(animatedFile);
      } else {
        receiver.getImageReceiver(key).requestFile(staticFile);
      }
    }
  }

  private static final int SPECIAL_TYPE_NONE = 0;
  private static final int SPECIAL_TYPE_ANIMATED_EMOJI = 1;
  private static final int SPECIAL_TYPE_DICE = 2;

  private int stickerWidth, stickerHeight;
  private final int specialType;
  private TdApi.MessageDice dice;

  public TGMessageSticker (MessagesManager context, TdApi.Message msg, TdApi.MessageDice dice) {
    super(context, msg);
    this.specialType = SPECIAL_TYPE_DICE;
    setDice(dice, false);
    tdlib.listeners().subscribeToAnimatedEmojiUpdates(this);
  }

  private void setDice (TdApi.MessageDice dice, boolean isUpdate) {
    this.dice = dice;
    TdApi.DiceStickers targetSticker = tdlib.findDiceEmoji(dice.emoji, dice.value, dice.finalState != null ? dice.finalState : dice.initialState);
    if (targetSticker != null) {
      setSticker(targetSticker, null, null, isUpdate, dice.finalState != null);
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
  protected boolean updateMessageContent (TdApi.Message message, TdApi.MessageContent newContent, boolean isBottomMessage) {
    if (specialType == SPECIAL_TYPE_DICE && newContent.getConstructor() == TdApi.MessageDice.CONSTRUCTOR) {
      List<Representation> prevRepresentation = this.representation;
      TdApi.MessageDice newDice = (TdApi.MessageDice) newContent;
      boolean hadFinalState = this.dice != null && this.dice.finalState != null;
      boolean hadInitialState = this.dice != null && this.dice.initialState != null;
      boolean hasFinalState = newDice.finalState != null;
      setDice(newDice, true);
      if (hadInitialState && !hadFinalState && hasFinalState) {
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
          default:
            throw new UnsupportedOperationException(sticker.toString());
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
    }
    return false;
  }

  public TGMessageSticker (MessagesManager context, TdApi.Message msg, TdApi.Sticker sticker, boolean isAnimatedEmoji, String colorReplacementKey, int[] colorReplacement) {
    super(context, msg);
    this.specialType = isAnimatedEmoji ? SPECIAL_TYPE_ANIMATED_EMOJI : SPECIAL_TYPE_NONE;
    setSticker(new TdApi.DiceStickersRegular(sticker), colorReplacementKey, colorReplacement, false, true);
  }

  @Override
  public void markAsBeingAdded (boolean isBeingAdded) {
    super.markAsBeingAdded(isBeingAdded);
    if (dice != null && dice.finalState != null) {
      setSticker(sticker, null, null, false, true);
    }
  }

  private void setSticker (TdApi.DiceStickers sticker, String colorReplacementKey, int[] colorReplacement, boolean isUpdate, boolean allowNoLoop) {
    this.sticker = sticker;
    final List<Representation> representation = new ArrayList<>();
    switch (sticker.getConstructor()) {
      case TdApi.DiceStickersRegular.CONSTRUCTOR: {
        TdApi.DiceStickersRegular regular = (TdApi.DiceStickersRegular) sticker;
        representation.add(new Representation(regular.sticker, colorReplacementKey, colorReplacement, allowNoLoop, false));
        break;
      }
      case TdApi.DiceStickersSlotMachine.CONSTRUCTOR: {
        TdApi.DiceStickersSlotMachine slotMachine = (TdApi.DiceStickersSlotMachine) sticker;
        representation.add(new Representation(slotMachine.background, colorReplacementKey, colorReplacement, allowNoLoop, true));
        representation.add(new Representation(slotMachine.leftReel, colorReplacementKey, colorReplacement, allowNoLoop, false));
        representation.add(new Representation(slotMachine.centerReel, colorReplacementKey, colorReplacement, allowNoLoop, false));
        representation.add(new Representation(slotMachine.rightReel, colorReplacementKey, colorReplacement, allowNoLoop, false));
        representation.add(new Representation(slotMachine.lever, colorReplacementKey, colorReplacement, allowNoLoop, true));
        break;
      }
      default: {
        throw new UnsupportedOperationException(sticker.toString());
      }
    }
    this.representation = representation;
  }

  public static final float MAX_STICKER_FORWARD_SIZE = 120f;
  public static final float MAX_STICKER_SIZE = 190f;

  private TdApi.Sticker getBaseSticker () {
    if (sticker == null)
      return null;
    switch (sticker.getConstructor()) {
      case TdApi.DiceStickersRegular.CONSTRUCTOR:
        return ((TdApi.DiceStickersRegular) sticker).sticker;
      case TdApi.DiceStickersSlotMachine.CONSTRUCTOR:
        return ((TdApi.DiceStickersSlotMachine) sticker).background;
    }
    throw new UnsupportedOperationException(sticker.toString());
  }

  private long getStickerSetId () {
    TdApi.Sticker sticker = specialType == SPECIAL_TYPE_NONE ? getBaseSticker() : null;
    return sticker != null ? sticker.setId : 0;
  }

  @Override
  protected void buildContent (int origMaxWidth) {
    final TdApi.Sticker sticker = getBaseSticker();
    float max = Screen.dp(useForward() ? MAX_STICKER_FORWARD_SIZE : MAX_STICKER_SIZE);
    if (specialType != SPECIAL_TYPE_NONE || (sticker.setId == TdConstants.TELEGRAM_ANIMATED_EMOJI_STICKER_SET_ID)) { // TODO check for dice sticker set id
      max *= tdlib.emojiesAnimatedZoom();
    }
    float ratio;
    if (sticker != null) {
      ratio = Math.min(max / (float) sticker.width, max / (float) sticker.height);
      stickerWidth = (int) (sticker.width * ratio);
      stickerHeight = (int) (sticker.height * ratio);
    } else {
      stickerWidth = stickerHeight = 0;
      ratio = 1f;
    }

    if (stickerWidth == 0 && stickerHeight == 0) {
      stickerWidth = stickerHeight = (int) max;
      ratio = max / 512f;
    }

    if (this.outline != null) {
      this.outline.reset();
    }
    if (representation != null) {
      for (Representation obj : representation) {
        this.outline = Td.buildOutline(obj.getOutline(), ratio, outline);
        if (obj.staticFile != null) {
          obj.staticFile.setSize(Math.max(stickerWidth, stickerHeight));
        }
      }
    }
  }

  @Override
  protected int getContentWidth () {
    return stickerWidth;
  }

  @Override
  protected int getBubbleTimePartOffsetY () {
    return Screen.dp(4f);
  }

  @Override
  protected int getContentHeight () {
    return Math.max(Screen.dp(56f), stickerHeight) + (specialType == SPECIAL_TYPE_DICE && useBubbles() && !useForward() ? getBubbleTimePartOffsetY() + getBubbleTimePartHeight() + Screen.dp(2f) : 0);
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
      int desiredLeft = left + stickerWidth - timePartWidth;
      if (!useBubbles() || isOutgoingBubble()) {
        return desiredLeft;
      } else {
        return Math.max(left, desiredLeft);
      }
    }
  }

  private int findStickerLeft () {
    return isOutgoingBubble() ? useBubble() ? getContentX() : getActualRightContentEdge() - stickerWidth : getContentX();
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth, ComplexReceiver receiver) {
    int left = findStickerLeft();
    int top = getContentY();
    int right = left + stickerWidth;
    int bottom = getContentY() + stickerHeight;
    if (representation != null) {
      int index = 0;
      if (this.outline != null) {
        boolean needPlaceholder = false;
        for (Representation representation : representation) {
          DoubleImageReceiver preview = receiver.getPreviewReceiver(index);
          Receiver target = representation.isAnimated() ? receiver.getGifReceiver(index) : receiver.getImageReceiver(index);
          if (target.needPlaceholder() && preview.needPlaceholder()) {
            needPlaceholder = true;
          }
          index++;
        }
        if (needPlaceholder) {
          c.save();
          c.translate(left, top);
          c.drawPath(outline, Paints.getPlaceholderPaint());
          c.restore();
        }
      }

      index = 0;
      for (Representation representation : representation) {
        DoubleImageReceiver preview = receiver.getPreviewReceiver(index);
        Receiver target = representation.isAnimated() ? receiver.getGifReceiver(index) : receiver.getImageReceiver(index);
        DrawAlgorithms.drawReceiver(c, preview, target, !representation.isAnimated(), false, left, top, right, bottom);
        index++;
      }
    }
  }

  private void checkDice () {
    if (specialType == SPECIAL_TYPE_DICE && hasAnimationEnded() && dice != null && dice.value != 0) {
      super.markAsBeingAdded(false);
      setSticker(sticker, null, null, false, true);
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
    return getStickerSetId() != 0 && getBaseSticker().isAnimated && Settings.instance().getNewSetting(Settings.SETTING_FLAG_NO_ANIMATED_STICKERS_LOOP) && getStickerSetId() != 0;
  }

  public void openStickerSet () {
    tdlib.ui().showStickerSet(controller(), getBaseSticker().setId);
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
        int right = left + stickerWidth;
        int bottom = getContentY() + stickerHeight;
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
              if (sticker != null && sticker.isAnimated && this.sticker != null && this.sticker.getConstructor() != TdApi.DiceStickersSlotMachine.CONSTRUCTOR) {
                gifFile = new GifFile(tdlib, sticker);
                gifFile.setOptimize(true);
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
                    outRect.top += outRect.height() * (TD.EMOJI_DICE.textRepresentation.equals(dice.emoji) ? .35f : .20f);
                  }
                })
                .gif(gifFile, imageFile)
                .controller(controller())
                .show(tdlib, Lang.getString(TD.EMOJI_DART.textRepresentation.equals(dice.emoji) ? R.string.SendDartHint : TD.EMOJI_DICE.textRepresentation.equals(dice.emoji) ? R.string.SendDiceHint : R.string.SendUnknownDiceHint, dice.emoji));
              break;
            }
            case SPECIAL_TYPE_ANIMATED_EMOJI: {
              GifFile lottieFile = view.getComplexReceiver() != null ? view.getComplexReceiver().getGifReceiver(0).getCurrentFile() : null;
              if (lottieFile != null) {
                tapProcessed = lottieFile.setVibrationPattern(Emoji.instance().getVibrationPatternType(sticker.emoji));
                if (lottieFile.setLooped(false)) {
                  tapProcessed = true;
                  invalidate();
                }
              }
              break;
            }
            default: {
              GifFile lottieFile = view.getComplexReceiver().getGifReceiver(0).getCurrentFile();
              if (lottieFile != null && Settings.instance().getNewSetting(Settings.SETTING_FLAG_NO_ANIMATED_STICKERS_LOOP) && lottieFile.setLooped(false)) {
                tapProcessed = true;
                invalidate();
              }
              if (!tapProcessed && sticker.setId != 0) {
                openStickerSet();
                tapProcessed = true;
              }
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
}
