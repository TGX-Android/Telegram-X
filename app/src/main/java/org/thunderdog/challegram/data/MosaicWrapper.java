package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.mediaview.MediaViewThumbLocation;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.DrawableProvider;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.MultipleViewProvider;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.reference.ReferenceUtils;

/**
 * Date: 11/30/17
 * Author: default
 */

public class MosaicWrapper implements FactorAnimator.Target, ComplexReceiver.KeyFilter {
  private final ArrayList<MediaWrapper> items;
  private final @Nullable TGMessage parent;
  private MultipleViewProvider viewProvider;

  private boolean needTopRound = true, needBottomRound = true;

  public MosaicWrapper (MediaWrapper baseWrapper, @Nullable TGMessage parent) {
    this.items = new ArrayList<>();
    this.items.add(baseWrapper);
    this.parent = parent;
    this.sumAspectRatio += (float) baseWrapper.getContentWidth() / (float) baseWrapper.getContentHeight();
    this.listeners = new ArrayList<>();
  }

  public void setSelectionAnimator (long messageId, FactorAnimator animator) {
    for (MediaWrapper item : items) {
      if (item.getSourceMessageId() == messageId) {
        item.setSelectionAnimator(animator);
        break;
      }
    }
  }

  public void setNeedDefaultRounds (boolean top, boolean bottom) {
    if (this.needTopRound != top || this.needBottomRound != bottom) {
      this.needTopRound = top;
      this.needBottomRound = bottom;
      if (mosaicItems != null) {
        for (MosaicItemInfo info : mosaicItems) {
          info.target.setNeedRound(info.needTopLeftRounding(), info.needTopRightRounding(), info.needBottomRightRounding(), info.needBottomLeftRounding());
        }
      }
      if (addedMosaicItems != null) {
        for (MosaicItemInfo info : addedMosaicItems) {
          info.target.setNeedRound(info.needTopLeftRounding(), info.needTopRightRounding(), info.needBottomRightRounding(), info.needBottomLeftRounding());
        }
      }
    }
  }

  public void setViewProvider (MultipleViewProvider viewProvider) {
    this.viewProvider = viewProvider;
  }

  public void addItem (MediaWrapper wrapper, boolean toEnd) {
    if (toEnd) {
      items.add(wrapper);
    } else {
      items.add(0, wrapper);
    }
    sumAspectRatio += (float) wrapper.getContentWidth() / (float) wrapper.getContentHeight();
    built = false;
  }

  public int removeItem (long messageId, int approximateIndex) {
    if (mosaicItems == null) {
      return MOSAIC_NOT_CHANGED;
    }
    if (approximateIndex >= 0 && approximateIndex < mosaicItems.length) {
      MediaWrapper wrapper = mosaicItems[approximateIndex].target;
      if (wrapper.getFileProgress().getMessageId() == messageId) {
        return removeItem(wrapper, true);
      }
    }
    for (MosaicItemInfo item : mosaicItems) {
      if (item.target.getFileProgress().getMessageId() == messageId) {
        return removeItem(item.target, true);
      }
    }
    return MOSAIC_NOT_CHANGED;
  }

  public int removeItem (MediaWrapper wrapper, boolean animateChanges) {
    if (items.remove(wrapper)) {
      sumAspectRatio -= (float) wrapper.getContentWidth() / (float) wrapper.getContentHeight();
      if (built) {
        built = false;
        return build(layoutWidth, layoutHeight, fitMode, animateChanges);
      }
    }
    return MOSAIC_NOT_CHANGED;
  }

  public MediaWrapper getSingularItem () {
    return items.get(0);
  }

  public boolean isSingular () {
    return items.size() == 1;
  }

  private static final int POSITION_NONE = 0;
  private static final int POSITION_TOP = 1;
  private static final int POSITION_BOTTOM = 1 << 1;
  private static final int POSITION_LEFT = 1 << 2;
  private static final int POSITION_RIGHT = 1 << 3;
  private static final int POSITION_INSIDE = 1 << 4;
  // private static final int POSITION_UNKNOWN = 1 << 30;

  public static class MosaicItemInfo {
    MosaicWrapper context;

    MediaWrapper target;
    int index;

    int imageWidth, imageHeight;
    float aspectRatio;

    int position;
    int x, y, width, height;

    public MosaicItemInfo (MosaicWrapper context, MediaWrapper target, int index, int imageWidth, int imageHeight, float aspectRatio) {
      this.context = context;
      this.target = target;
      this.index = index;
      this.imageWidth = imageWidth;
      this.imageHeight = imageHeight;
      this.aspectRatio = aspectRatio;
    }

    boolean compare (MosaicItemInfo info) {
      return info.target == target && info.position == position && info.x == x && info.y == y && info.width == width && info.height == height;
    }

    // Animations

    static final int ANIMATION_REVERSE_ALPHA = 1;

    private MosaicItemInfo newItem;
    int animationOptions;

    void setNewItem (MosaicItemInfo info) {
      this.newItem = info;
    }

    void applyAnimationState () {
      if (context.factor != 0f) {
        this.x = getX();
        this.y = getY();
        this.width = getWidth();
        this.height = getHeight();
      }
      animationOptions = 0;
    }

    // Position

    public float getAlpha () {
      float factor = context.factor;
      if (animationOptions == ANIMATION_REVERSE_ALPHA) {
        if (factor == 0f || newItem != null) {
          return 0f;
        } else {
          return factor;
        }
      } else {
        if (factor == 0f || newItem != null) {
          return 1f;
        } else {
          return 1f - factor;
        }
      }
    }

    public int getX () {
      float factor = context.factor;
      if (factor == 0f || newItem == null) {
        return x;
      } else {
        return x + (int) ((newItem.x - x) * factor);
      }
    }

    public int getY () {
      float factor = context.factor;
      if (factor == 0f || newItem == null) {
        return y;
      } else {
        return y + (int) ((newItem.y - y) * factor);
      }
    }

    public int getWidth () {
      float factor = context.factor;
      if (factor == 0f || newItem == null) {
        return width;
      } else {
        return width + (int) ((newItem.width - width) * factor);
      }
    }

    public int getHeight () {
      float factor = context.factor;
      if (factor == 0f || newItem == null) {
        return height;
      } else {
        return height + (int) ((newItem.height - height) * factor);
      }
    }

    // Rounding

    public boolean needTopLeftRounding () {
      return (position & POSITION_LEFT) != 0 && (position & POSITION_TOP) != 0 && context.needTopRound;
    }

    public boolean needTopRightRounding () {
      return (position & POSITION_RIGHT) != 0 && (position & POSITION_TOP) != 0 && context.needTopRound;
    }

    public boolean needBottomLeftRounding () {
      return (position & POSITION_LEFT) != 0 && (position & POSITION_BOTTOM) != 0 && context.needBottomRound;
    }

    public boolean needBottomRightRounding () {
      return (position & POSITION_RIGHT) != 0 && (position & POSITION_BOTTOM) != 0 && context.needBottomRound;
    }
  }

  private static class MosaicLayoutAttempt {
    int[] lineCounts;
    float[] heights;

    public MosaicLayoutAttempt (int[] lineCounts, float[] heights) {
      this.lineCounts = lineCounts;
      this.heights = heights;
    }
  }

  private static int floorToScreenPixels (float x) {
    return (int) Math.floor(x);
  }

  private static final float SPACING_SIZE_DP = 2f;

  private boolean built;
  private int layoutWidth, layoutHeight;
  private int fitMode;
  private float sumAspectRatio;

  private MosaicItemInfo[] mosaicItems;
  private int mosaicWidth, mosaicHeight;

  private MosaicItemInfo[] addedMosaicItems;

  public float getAspectRatio () {
    return items.isEmpty() ? 1.0f : sumAspectRatio / (float) items.size();
  }

  public int build (final int layoutWidth, final int layoutHeight, int fitMode, final boolean animateChanges) {
    return build(layoutWidth, layoutHeight, fitMode, animateChanges, false, 1f);
  }

  private int build (final int layoutWidth, final int layoutHeight, int fitMode, final boolean animateChanges, boolean isRetry, float sizeScale) {
    cancelTouch();

    if (layoutWidth <= 0 || layoutHeight <= 0) {
      return MOSAIC_NOT_CHANGED;
    }

    if (built && this.layoutWidth == layoutWidth && this.layoutHeight == layoutHeight && this.fitMode == fitMode) {
      return MOSAIC_NOT_CHANGED;
    }

    this.layoutWidth = layoutWidth;
    this.layoutHeight = layoutHeight;
    this.fitMode = fitMode;
    this.built = false;

    final int itemCount = this.items.size();

    final int minLayoutWidth = Math.min(layoutWidth, Screen.dp(160f));
    final int minLayoutHeight = Screen.dp(120f);

    if (itemCount == 1) {
      MediaWrapper item = items.get(0);
      int width = item.getContentWidth();
      int height = item.getContentHeight();
      MosaicItemInfo info = new MosaicItemInfo(this, item, 0, width, height, (float) width / (float) height);

      switch (fitMode) {
        case MODE_FIT_WIDTH: {
          float scale = (float) layoutWidth / (float) width;
          width = layoutWidth;
          height = Math.min(layoutHeight, (int) ((float) height * scale));
          break;
        }
        case MODE_FIT_HEIGHT: {
          float scale = (float) layoutHeight / (float) height;
          width = Math.min(layoutWidth, (int) ((float) width * scale));
          height = layoutHeight;
          break;
        }
        case MODE_FIT_AS_IS:
        default: {
          float scale = Math.min((float) layoutWidth / (float) width, (float) layoutHeight / (float) height);
          width *= scale;
          height *= scale;

          if (width < minLayoutWidth) {
            scale = (float) minLayoutWidth / (float) width;
            width = minLayoutWidth;
            height = (int) Math.min(layoutHeight, height * scale);
          } else if (height < minLayoutHeight) {
            scale = (float) minLayoutHeight / (float) height;
            height = minLayoutHeight;
            width = (int) Math.min(layoutWidth, width * scale);
          }
          break;
        }
      }

      info.width = width;
      info.height = height;
      info.position = POSITION_LEFT | POSITION_TOP | POSITION_RIGHT | POSITION_BOTTOM;

      info.target.buildContent(width, height);
      info.target.setImageScaling(0);
      info.target.setNeedRound(info.needTopLeftRounding(), info.needTopRightRounding(), info.needBottomRightRounding(), info.needBottomLeftRounding());

      return setMosaic(width, height, new MosaicItemInfo[] {info}, fitMode, animateChanges, false);
    }

    float spacing = Screen.dp(SPACING_SIZE_DP);

    StringBuilder _proportions = new StringBuilder();
    float averageAspectRatio = 1.0f;
    boolean forceCalc = false;

    MosaicItemInfo[] itemInfos = new MosaicItemInfo[itemCount];
    int i = 0;
    for (MediaWrapper item : this.items) {
      int width = item.getContentWidth();
      int height = item.getContentHeight();

      float aspectRatio = (float) width / (float) height;
      if (aspectRatio > 1.2f) {
        _proportions.append('w');
      } else if (aspectRatio < 0.8f) {
        _proportions.append('n');
      } else {
        _proportions.append('q');
      }

      if (aspectRatio > 2.0f) {
        forceCalc = true;
      }
      averageAspectRatio += aspectRatio;

      itemInfos[i] = new MosaicItemInfo(this, item, i, width, height, aspectRatio);

      i++;
    }

    float minWidth = Screen.dp(68f);
    float maxAspectRatio = (float) layoutWidth / (float) layoutHeight;
    if (itemInfos.length > 0) {
      averageAspectRatio = averageAspectRatio / (float) itemInfos.length;
    }
    String proportions = _proportions.toString();

    if (!forceCalc) {
      if (itemInfos.length == 2) {
        if (proportions.equals("ww") && averageAspectRatio > 1.4 * maxAspectRatio && itemInfos[1].aspectRatio - itemInfos[0].aspectRatio < 0.2) {
          float width = layoutWidth;
          float height = floorToScreenPixels(Math.min(width / itemInfos[0].aspectRatio, Math.min(width / itemInfos[1].aspectRatio, (layoutHeight - spacing) / 2.0f)));

          width *= sizeScale;
          height *= sizeScale;

          itemInfos[0].width = (int) width; itemInfos[0].height = (int) height;
          itemInfos[0].position = POSITION_TOP | POSITION_LEFT | POSITION_RIGHT;

          itemInfos[1].y = (int) (height + spacing);
          itemInfos[1].width = (int) width;
          itemInfos[1].height = (int) height;
          itemInfos[1].position = POSITION_BOTTOM | POSITION_LEFT | POSITION_RIGHT;
        } else if (proportions.equals("ww") || proportions.equals("qq")) {
          float width = (layoutWidth - spacing) / 2.0f;
          float height = floorToScreenPixels(Math.min(width / itemInfos[0].aspectRatio, Math.min(width / itemInfos[1].aspectRatio, layoutHeight)));

          width *= sizeScale;
          height *= sizeScale;

          itemInfos[0].width = (int) width; itemInfos[0].height = (int) height;
          itemInfos[0].position = POSITION_TOP | POSITION_LEFT | POSITION_BOTTOM;

          itemInfos[1].x = (int) (width + spacing);
          itemInfos[1].width = (int) width;
          itemInfos[1].height = (int) height;
          itemInfos[1].position = POSITION_TOP | POSITION_RIGHT | POSITION_BOTTOM;
        } else {
          float secondWidth = floorToScreenPixels(Math.min(0.5f * (layoutWidth - spacing), Math.round((layoutWidth - spacing) / itemInfos[0].aspectRatio / (1.0f / itemInfos[0].aspectRatio + 1.0f / itemInfos[1].aspectRatio))));
          float firstWidth = layoutWidth - secondWidth - spacing;
          float height = floorToScreenPixels(Math.min(layoutHeight, Math.round(Math.min(firstWidth / itemInfos[0].aspectRatio, secondWidth / itemInfos[1].aspectRatio))));

          firstWidth *= sizeScale;
          secondWidth *= sizeScale;
          height *= sizeScale;

          itemInfos[0].width = (int) firstWidth;
          itemInfos[0].height = (int) height;
          itemInfos[0].position = POSITION_TOP | POSITION_LEFT | POSITION_BOTTOM;

          itemInfos[1].x = (int) (firstWidth + spacing);
          itemInfos[1].width = (int) secondWidth;
          itemInfos[1].height = (int) height;
          itemInfos[1].position = POSITION_TOP | POSITION_RIGHT | POSITION_BOTTOM;
        }
      } else if (itemInfos.length == 3) {
        if (proportions.startsWith("n")) {
          float firstHeight = layoutHeight;

          float thirdHeight = Math.min((layoutHeight - spacing) * 0.5f, Math.round(itemInfos[1].aspectRatio * (layoutWidth - spacing) / (itemInfos[2].aspectRatio + itemInfos[1].aspectRatio)));
          float secondHeight = layoutHeight - thirdHeight - spacing;
          float rightWidth = Math.max(minWidth, Math.min((layoutWidth - spacing) * 0.5f, Math.round(Math.min(thirdHeight * itemInfos[2].aspectRatio, secondHeight * itemInfos[1].aspectRatio))));

          float leftWidth = Math.round(Math.min(firstHeight * itemInfos[0].aspectRatio, (layoutWidth - spacing - rightWidth)));

          firstHeight *= sizeScale;
          thirdHeight *= sizeScale;
          secondHeight *= sizeScale;
          rightWidth *= sizeScale;
          leftWidth *= sizeScale;

          itemInfos[0].width = (int) leftWidth;
          itemInfos[0].height = (int) firstHeight;
          itemInfos[0].position = POSITION_TOP | POSITION_LEFT | POSITION_BOTTOM;

          itemInfos[1].x = (int) (leftWidth + spacing);
          itemInfos[1].width = (int) rightWidth;
          itemInfos[1].height = (int) secondHeight;
          itemInfos[1].position = POSITION_RIGHT | POSITION_TOP;

          itemInfos[2].x = (int) (leftWidth + spacing);
          itemInfos[2].y = (int) (secondHeight + spacing);
          itemInfos[2].width = (int) rightWidth;
          itemInfos[2].height = (int) thirdHeight;
          itemInfos[2].position = POSITION_RIGHT | POSITION_BOTTOM;
        } else {
          float firstWidth = layoutWidth;
          float firstHeight = floorToScreenPixels(Math.min(firstWidth / itemInfos[0].aspectRatio, (layoutHeight - spacing) * 0.66f));

          float secondWidth = (layoutWidth - spacing) / 2.0f;
          float secondHeight = Math.min(layoutHeight - firstHeight - spacing, Math.round(Math.min(secondWidth / itemInfos[1].aspectRatio, secondWidth / itemInfos[2].aspectRatio)));

          firstWidth *= sizeScale;
          firstHeight *= sizeScale;
          secondWidth *= sizeScale;
          secondHeight *= sizeScale;

          itemInfos[0].width = (int) firstWidth;
          itemInfos[0].height = (int) firstHeight;
          itemInfos[0].position = POSITION_TOP | POSITION_LEFT | POSITION_RIGHT;

          itemInfos[1].y = (int) (firstHeight + spacing);
          itemInfos[1].width = (int) secondWidth;
          itemInfos[1].height = (int) secondHeight;
          itemInfos[1].position = POSITION_LEFT | POSITION_BOTTOM;

          itemInfos[2].x = (int) (secondWidth + spacing);
          itemInfos[2].y = (int) (firstHeight + spacing);
          itemInfos[2].width = (int) secondWidth;
          itemInfos[2].height = (int) secondHeight;
          itemInfos[2].position = POSITION_RIGHT | POSITION_BOTTOM;
        }
      } else if (itemInfos.length == 4) {
        if (proportions.equals("wwww") || proportions.startsWith("w")) {
          float w = layoutWidth;
          float h0 = Math.round(Math.min(w / itemInfos[0].aspectRatio, (layoutHeight - spacing) * 0.66f));

          float h = Math.round((layoutWidth - 2 * spacing) / (itemInfos[1].aspectRatio + itemInfos[2].aspectRatio + itemInfos[3].aspectRatio));
          float w0 = Math.max(minWidth, Math.min((layoutWidth - 2 * spacing) * 0.4f, h * itemInfos[1].aspectRatio));
          float w2 = Math.max(Math.max(minWidth, (layoutWidth - 2 * spacing) * 0.33f), h * itemInfos[3].aspectRatio);
          float w1 = w - w0 - w2 - 2 * spacing;
          h = Math.min(layoutHeight - h0 - spacing, h);

          w *= sizeScale;
          h0 *= sizeScale;
          h *= sizeScale;
          w0 *= sizeScale;
          w2 *= sizeScale;
          w1 *= sizeScale;

          itemInfos[0].width = (int) w;
          itemInfos[0].height = (int) h0;
          itemInfos[0].position = POSITION_TOP | POSITION_LEFT | POSITION_RIGHT;

          itemInfos[1].y = (int) (h0 + spacing);
          itemInfos[1].width = (int) w0;
          itemInfos[1].height = (int) h;
          itemInfos[1].position = POSITION_LEFT | POSITION_BOTTOM;

          itemInfos[2].x = (int) (w0 + spacing);
          itemInfos[2].y = (int) (h0 + spacing);
          itemInfos[2].width = (int) w1;
          itemInfos[2].height = (int) h;
          itemInfos[2].position = POSITION_BOTTOM;

          itemInfos[3].x = (int) (w0 + w1 + 2 * spacing);
          itemInfos[3].y = (int) (h0 + spacing);
          itemInfos[3].width = (int) w2;
          itemInfos[3].height = (int) h;
          itemInfos[3].position = POSITION_RIGHT | POSITION_BOTTOM;
        } else {
          float h = layoutHeight;
          float w0 = Math.round(Math.min(h * itemInfos[0].aspectRatio, (layoutWidth - spacing) * 0.6f));

          float w = Math.round((layoutHeight - 2 * spacing) / (1.0f / itemInfos[1].aspectRatio + 1.0f /  itemInfos[2].aspectRatio + 1.0f / itemInfos[3].aspectRatio));
          float h0 = floorToScreenPixels(w / itemInfos[1].aspectRatio);
          float h1 = floorToScreenPixels(w / itemInfos[2].aspectRatio);
          float h2 = h - h0 - h1 - 2.0f * spacing;
          w = Math.max(minWidth, Math.min(layoutWidth - w0 - spacing, w));

          h *= sizeScale;
          w0 *= sizeScale;
          w *= sizeScale;
          h0 *= sizeScale;
          h1 *= sizeScale;
          h2 *= sizeScale;

          itemInfos[0].width = (int) w0;
          itemInfos[0].height = (int) h;
          itemInfos[0].position = POSITION_TOP | POSITION_LEFT | POSITION_BOTTOM;

          itemInfos[1].x = (int) (w0 + spacing);
          itemInfos[1].width = (int) w;
          itemInfos[1].height = (int) h0;
          itemInfos[1].position = POSITION_RIGHT | POSITION_TOP;

          itemInfos[2].x = (int) (w0 + spacing);
          itemInfos[2].y = (int) (h0 + spacing);
          itemInfos[2].width = (int) w;
          itemInfos[2].height = (int) h1;
          itemInfos[2].position = POSITION_RIGHT;

          itemInfos[3].x = (int) (w0 + spacing);
          itemInfos[3].y = (int) (h0 + h1 + 2 * spacing);
          itemInfos[3].width = (int) w;
          itemInfos[3].height = (int) h2;
          itemInfos[3].position = POSITION_RIGHT | POSITION_BOTTOM;
        }
      }
    }

    if (forceCalc || itemInfos.length >= 5) {
      float[] croppedRatios = new float[itemInfos.length];

      i = 0;
      for (MosaicItemInfo itemInfo : itemInfos) {
        float aspectRatio = itemInfo.aspectRatio;
        float croppedRatio = aspectRatio;
        if (averageAspectRatio > 1.1f) {
          croppedRatio = Math.max(1.0f, aspectRatio);
        } else {
          croppedRatio = Math.min(1.0f, aspectRatio);
        }

        croppedRatio = Math.max(0.66667f, Math.min(1.7f, croppedRatio));
        croppedRatios[i] = croppedRatio;
        i++;
      }

      ArrayList<MosaicLayoutAttempt> attempts = new ArrayList<>(itemInfos.length);

      for (int firstLine = 1; firstLine < croppedRatios.length; firstLine++) {
        int secondLine = croppedRatios.length - firstLine;
        if (firstLine > 3 || secondLine > 3) {
          continue;
        }

        attempts.add(new MosaicLayoutAttempt(new int[] {firstLine, secondLine}, new float[] {multiHeight(croppedRatios, 0, firstLine, layoutWidth, spacing), multiHeight(croppedRatios, firstLine, croppedRatios.length, layoutWidth, spacing)}));

        //addAttempt(@[@(firstLine), @(croppedRatios.count - firstLine)], @[multiHeight([croppedRatios subarrayWithRange:NSMakeRange(0, firstLine)]), multiHeight([croppedRatios subarrayWithRange:NSMakeRange(firstLine, croppedRatios.count - firstLine)])])
      }

      for (int firstLine = 1; firstLine < croppedRatios.length - 1; firstLine++) {
        for (int secondLine = 1; secondLine < croppedRatios.length - firstLine; secondLine++) {
          int thirdLine = croppedRatios.length - firstLine - secondLine;
          if (firstLine > 3 || secondLine > (averageAspectRatio < 0.85f ? 4 : 3) || thirdLine > 3) {
            continue;
          }

          attempts.add(new MosaicLayoutAttempt(new int[] {firstLine, secondLine, thirdLine}, new float[] {multiHeight(croppedRatios, 0, firstLine, layoutWidth, spacing), multiHeight(croppedRatios, firstLine, croppedRatios.length - thirdLine, layoutWidth, spacing), multiHeight(croppedRatios, firstLine + secondLine, croppedRatios.length, layoutWidth, spacing)}));
        }
      }

      if (croppedRatios.length - 2 >= 1) {
        for (int firstLine = 1; firstLine < croppedRatios.length - 2; firstLine++) {
          if (croppedRatios.length - firstLine < 1) {
            continue;
          }
          for (int secondLine = 1; secondLine < croppedRatios.length - firstLine; secondLine++) {
            for (int thirdLine = 1; thirdLine < croppedRatios.length - firstLine - secondLine; thirdLine++) {
              int fourthLine = croppedRatios.length - firstLine - secondLine - thirdLine;
              if (firstLine > 3 || secondLine > 3 || thirdLine > 3 || fourthLine > 3) {
                continue;
              }

              attempts.add(new MosaicLayoutAttempt(new int[] {firstLine, secondLine, thirdLine, fourthLine}, new float[] {multiHeight(croppedRatios, 0, firstLine, layoutWidth, spacing), multiHeight(croppedRatios, firstLine, croppedRatios.length - thirdLine - fourthLine, layoutWidth, spacing), multiHeight(croppedRatios, firstLine + secondLine, croppedRatios.length - fourthLine, layoutWidth, spacing), multiHeight(croppedRatios, firstLine + secondLine + thirdLine, croppedRatios.length, layoutWidth, spacing)}));

              //addAttempt(@[@(firstLine), @(secondLine), @(thirdLine), @(fourthLine)], @[multiHeight([croppedRatios subarrayWithRange:NSMakeRange(0, firstLine)]), multiHeight([croppedRatios subarrayWithRange:NSMakeRange(firstLine, croppedRatios.count - firstLine - thirdLine - fourthLine)]), multiHeight([croppedRatios subarrayWithRange:NSMakeRange(firstLine + secondLine, croppedRatios.count - firstLine - secondLine - fourthLine)]), multiHeight([croppedRatios subarrayWithRange:NSMakeRange(firstLine + secondLine + thirdLine, croppedRatios.count - firstLine - secondLine - thirdLine)])])
            }
          }
        }
      }

      float maxHeight = (float) layoutWidth / 3.0f * 4.0f;
      MosaicLayoutAttempt optimal = null;
      float optimalDiff = 0.0f;
      for (MosaicLayoutAttempt attempt : attempts) {
        float totalHeight = spacing * (float) (attempt.heights.length - 1);
        float minLineHeight = Float.MAX_VALUE;
        float maxLineHeight = 0.0f;
        for (float h : attempt.heights) {
          totalHeight += h;
          if (totalHeight < minLineHeight) {
            minLineHeight = totalHeight;
          }
          if (totalHeight > maxLineHeight) {
            maxLineHeight = totalHeight;
          }
        }

        float diff = Math.abs(totalHeight - maxHeight);

        if (attempt.lineCounts.length > 1) {
          if ((attempt.lineCounts[0] > attempt.lineCounts[1]) || (attempt.lineCounts.length > 2 && attempt.lineCounts[1] > attempt.lineCounts[2]) || (attempt.lineCounts.length > 3 && attempt.lineCounts[2] > attempt.lineCounts[3])) {
            diff *= 1.5f;
          }
        }

        if (minLineHeight < minWidth) {
          diff *= 1.5f;
        }

        if (optimal == null || diff < optimalDiff) {
          optimal = attempt;
          optimalDiff = diff;
        }
      }

      int index = 0;
      float y = 0.0f;
      if (optimal != null) {
        for (i = 0; i < optimal.lineCounts.length; i++) {
          int count = optimal.lineCounts[i];
          float lineHeight = optimal.heights[i] * sizeScale;
          float x = 0.0f;

          int positionFlags = POSITION_NONE;
          if (i == 0) {
            positionFlags |= POSITION_TOP;
          }
          if (i == optimal.lineCounts.length - 1) {
            positionFlags |= POSITION_BOTTOM;
          }

          for (int k = 0; k < count; k++) {
            int innerPositionFlags = positionFlags;

            if (k == 0) {
              innerPositionFlags |= POSITION_LEFT;
            }
            if (k == count - 1) {
              innerPositionFlags |= POSITION_RIGHT;
            }

            if (positionFlags == POSITION_NONE) {
              innerPositionFlags |= POSITION_INSIDE;
            }

            float ratio = croppedRatios[index];
            float width = ratio * lineHeight * sizeScale;
            itemInfos[index].x = (int) x;
            itemInfos[index].y = (int) y;
            itemInfos[index].width = (int) width;
            itemInfos[index].height = (int) lineHeight;
            itemInfos[index].position = innerPositionFlags;

            x += width + spacing;
            index += 1;
          }

          y += lineHeight + spacing;
        }
      }
    }

    int mosaicWidth = 0;
    int mosaicHeight = 0;
    for (MosaicItemInfo info : itemInfos) {
      mosaicWidth = Math.max(mosaicWidth, Math.round(info.x + info.width));
      mosaicHeight = Math.max(mosaicHeight, Math.round(info.y + info.height));
      info.target.buildContent(info.width, info.height);
      info.target.setImageScaling(Math.min(info.width, info.height));
      info.target.setNeedRound(info.needTopLeftRounding(), info.needTopRightRounding(), info.needBottomRightRounding(), info.needBottomLeftRounding());
    }

    return setMosaic(mosaicWidth, mosaicHeight, itemInfos, fitMode, animateChanges, isRetry);
  }

  private static float multiHeight (float[] ratios, int start, int end, int maxWidth, float spacing) {
    float ratioSum = 0.0f;
    if (start == 0 && end == ratios.length) {
      for (float ratio : ratios) {
        ratioSum += ratio;
      }
      return ((float) maxWidth - (ratios.length - 1) * spacing) / ratioSum;
    } else {
      for (int i = start; i < end; i++) {
        ratioSum += ratios[i];
      }
      return ((float) maxWidth - (end - start - 1) * spacing) / ratioSum;
    }
  }

  private void clearAddedItems () {
    if (addedMosaicItems != null) {
      for (MosaicItemInfo newItem : addedMosaicItems) {
        newItem.animationOptions = 0;
      }
      addedMosaicItems = null;
    }
  }

  private boolean isAnimating () {
    return factor != 0f || (changeAnimator != null && changeAnimator.isAnimating());
  }

  private void clearAnimation () {
    if (changeAnimator != null && (changeAnimator.isAnimating() || changeAnimator.getFactor() != 0f)) {
      changeAnimator.forceFactor(0f);
      for (MosaicItemInfo existingItem : mosaicItems) {
        existingItem.applyAnimationState();
      }
      clearAddedItems();
      factor = 0f;
    }
  }

  public static final int MOSAIC_NOT_CHANGED = 0;
  public static final int MOSAIC_INVALIDATED = 1;
  public static final int MOSAIC_CHANGED = 2;

  public static final int MODE_FIT_AS_IS = 0;
  public static final int MODE_FIT_WIDTH = 1;
  public static final int MODE_FIT_HEIGHT = 2;

  private int setMosaic (int width, int height, MosaicItemInfo[] items, int fitMode, boolean animate, boolean isRetry) {
    if (!isRetry) {
      int prevLayoutWidth = this.layoutWidth;
      int prevLayoutHeight = this.layoutHeight;

      float scale = 1f;
      if (fitMode == MODE_FIT_WIDTH && width < layoutWidth) {
        scale = (float) prevLayoutWidth / (float) width;
      } else if (fitMode == MODE_FIT_HEIGHT && height < layoutHeight) {
        scale = (float) prevLayoutHeight / (float) height;
      }

      if (scale != 1f) {
        int result = build(layoutWidth, layoutHeight, fitMode, animate, true, scale);

        this.layoutWidth = prevLayoutWidth;
        this.layoutHeight = prevLayoutHeight;

        return result;
      }
    }
    boolean firstBuild = this.mosaicItems == null;
    boolean foundChanges = firstBuild || items.length != mosaicItems.length;
    if (!foundChanges) {
      int i = 0;
      for (MosaicItemInfo existingItem : mosaicItems) {
        if (!existingItem.compare(items[i])) {
          foundChanges = true;
          break;
        }
        i++;
      }
    }
    if (!animate || firstBuild || !foundChanges || listeners.isEmpty()) {
      clearAnimation();
      boolean rectChanged = false;
      if (mosaicWidth != width || mosaicHeight != height) {
        mosaicWidth = width;
        mosaicHeight = height;
        rectChanged = true;
      }
      mosaicItems = items;
      built = true;
      if (!firstBuild) {
        notifyMosaicChanged(width, height, rectChanged);
      }
      return rectChanged ? MOSAIC_CHANGED : foundChanges ? MOSAIC_INVALIDATED : MOSAIC_NOT_CHANGED;
    }

    if (changeAnimator == null) {
      changeAnimator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
    } else {
      clearAnimation();
    }

    toMosaicItems = items;
    toMosaicWidth = width;
    toMosaicHeight = height;

    // First, detect common items
    final MediaWrapper[] intersection = new MediaWrapper[Math.min(mosaicItems.length, items.length)];
    int intersectionSize = 0;
    for (MosaicItemInfo existingItem : mosaicItems) {
      for (MosaicItemInfo newItem : items) {
        if (existingItem.target == newItem.target) {
          intersection[intersectionSize++] = newItem.target;
          existingItem.setNewItem(newItem);
          break;
        }
      }
    }
    ArrayList<MosaicItemInfo> newItems = null;
    // Second, detect new added items
    boolean downloadAutomatically = autoDownloadChatType != null && viewProvider != null && viewProvider.hasAnyTargetToInvalidate();
    for (MosaicItemInfo newItem : items) {
      if (ArrayUtils.indexOf(intersection, newItem.target) == -1) {
        if (newItems == null) {
          newItems = new ArrayList<>();
        }
        newItems.add(newItem);
        newItem.animationOptions |= MosaicItemInfo.ANIMATION_REVERSE_ALPHA;
        if (downloadAutomatically) {
          newItem.target.getFileProgress().downloadAutomatically(autoDownloadChatType);
        }
      }
    }
    if (newItems != null) {
      this.addedMosaicItems = new MosaicItemInfo[newItems.size()];
      newItems.toArray(addedMosaicItems);
    } else {
      this.addedMosaicItems = null;
    }

    changeAnimator.animateTo(1f);

    return toMosaicWidth != mosaicWidth || toMosaicHeight != mosaicHeight ? MOSAIC_CHANGED : foundChanges ? MOSAIC_INVALIDATED : MOSAIC_NOT_CHANGED;
  }

  // Animation

  private FactorAnimator changeAnimator;
  private int toMosaicWidth, toMosaicHeight;
  private MosaicItemInfo[] toMosaicItems;
  private float factor;

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    this.factor = factor;

    int width = mosaicWidth + (int) ((float) (toMosaicWidth - mosaicWidth) * factor);
    int height = mosaicHeight + (int) ((float) (toMosaicHeight - mosaicHeight) * factor);
    notifyMosaicChanged(width, height, width != reportedMosaicWidth || height != reportedMosaicHeight);
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (finalFactor == 1f) {
      changeAnimator.forceFactor(0f);

      clearAddedItems();

      this.factor = 0f;
      this.mosaicWidth = toMosaicWidth;
      this.mosaicHeight = toMosaicHeight;
      this.mosaicItems = toMosaicItems;

      this.toMosaicWidth = this.toMosaicHeight = 0;
      this.toMosaicItems = null;
    }
  }

  // Bounds

  public int getWidth () {
    if (changeAnimator != null && changeAnimator.isAnimating()) {
      return toMosaicWidth;
    } else {
      return mosaicWidth;
    }
  }

  public int getHeight () {
    if (changeAnimator != null && changeAnimator.isAnimating()) {
      return toMosaicHeight;
    } else {
      return mosaicHeight;
    }
  }

  // Notification

  public interface MosaicChangeListener {
    void onMosaicChanged (boolean boundChanged);
  }

  private final List<Reference<MosaicChangeListener>> listeners;

  public void addListener (MosaicChangeListener listener) {
    ReferenceUtils.addReference(listeners, listener);
  }

  public void removeListener (MosaicChangeListener listener) {
    ReferenceUtils.removeReference(listeners, listener);
  }

  private int reportedMosaicWidth, reportedMosaicHeight;

  private void notifyMosaicChanged (int width, int height, boolean boundsChanged) {
    reportedMosaicWidth = width;
    reportedMosaicHeight = height;
    final int size = listeners.size();
    for (int i = size - 1; i >= 0; i--) {
      MosaicChangeListener listener = listeners.get(i).get();
      if (listener != null) {
        listener.onMosaicChanged(boundsChanged);
      } else {
        listeners.remove(i);
      }
    }
  }

  // Updates

  public int rebuild () {
    built = false;
    return build(layoutWidth, layoutHeight, fitMode, true);
  }

  public int replaceMediaWrapper (MediaWrapper mediaWrapper) {
    int i = 0;
    for (MediaWrapper existingWrapper : items) {
      if (mediaWrapper.getSourceMessageId() == existingWrapper.getSourceMessageId()) {
        items.set(i, mediaWrapper);
        if (existingWrapper.getContentWidth() != mediaWrapper.getContentWidth() ||
            existingWrapper.getContentHeight() != mediaWrapper.getContentHeight()) {
          return rebuild();
        }
        break;
      }
      i++;
    }
    return MOSAIC_NOT_CHANGED;
  }

  // ImageReceiver

  @Override
  public boolean filterKey (int receiverType, Receiver receiver, int key) {
    return receiver.getTag() == this;
  }

  private static int requestFiles (MosaicWrapper context, ComplexReceiver complexReceiver, boolean invalidate, MosaicItemInfo[] items, int startKey) {
    if (items != null) {
      for (MosaicItemInfo item : items) {
        int key = startKey == -1 ? item.target.getReceiverKey() : startKey++;
        DoubleImageReceiver preview = complexReceiver.getPreviewReceiver(key);
        preview.setTag(context);
        if (!invalidate || item.target.showPreview()) {
          item.target.requestPreview(preview);
        }
        item.target.setPreviewReceiverReference(preview);
        Receiver receiver;
        if (item.target.needGif()) {
          GifReceiver gifReceiver = complexReceiver.getGifReceiver(key);
          item.target.requestGif(gifReceiver);
          receiver = gifReceiver;
        } else {
          ImageReceiver imageReceiver = complexReceiver.getImageReceiver(key);
          item.target.requestImage(imageReceiver);
          receiver = imageReceiver;
        }
        receiver.setTag(context);
        item.target.setTargetReceiverReference(receiver);
      }
    }
    return startKey;
  }

  public void requestFiles (ComplexReceiver complexReceiver, boolean invalidate) {
    /*int i = requestFiles(this, complexReceiver, invalidate, mosaicItems, 0);
    i = requestFiles(this, complexReceiver, invalidate, addedMosaicItems, i);
    complexReceiver.clearReceiversWithHigherKey(i);*/

    requestFiles(this, complexReceiver, invalidate, mosaicItems, -1);
    requestFiles(this, complexReceiver, invalidate, addedMosaicItems, -1);
    complexReceiver.clearReceivers(this);
  }

  // Touch events

  public MediaWrapper findMediaWrapperByMessageId (long messageId) {
    MosaicItemInfo info = findItemInfoByMessageId(messageId);
    return info != null ? info.target : null;
  }

  public MosaicItemInfo findItemInfoByMessageId (long messageId) {
    if (mosaicItems != null) {
      for (MosaicItemInfo item : mosaicItems) {
        if (item.target.getSourceMessageId() == messageId) {
          return item;
        }
      }
    }
    if (addedMosaicItems != null) {
      for (MosaicItemInfo item : addedMosaicItems) {
        if (item.target.getSourceMessageId() == messageId) {
          return item;
        }
      }
    }
    return null;
  }

  public MediaWrapper findMediaWrapperUnder (float x, float y) {
    if (mosaicItems == null) {
      return null;
    }
    x -= lastStartX;
    y -= lastStartY;
    int spacing = Screen.dp(SPACING_SIZE_DP);
    for (MosaicItemInfo info : mosaicItems) {
      int cellWidth = info.target.getCellWidth();
      int cellHeight = info.target.getCellHeight();
      int maxX = info.x + cellWidth;
      if ((info.position & POSITION_RIGHT) == 0) {
        maxX += spacing;
      }
      int maxY = info.y + cellHeight;
      if ((info.position & POSITION_BOTTOM) == 0) {
        maxY += spacing;
      }
      if (x >= info.x && x <= maxX && y >= info.y && y <= maxY) {
        return info.target;
      }
    }
    return null;
  }

  public MediaViewThumbLocation getMediaThumbLocation (long messageId, View view, int viewTop, int viewBottom, int top) {
    MosaicItemInfo info = findItemInfoByMessageId(messageId);
    if (info == null) {
      return null;
    }
    MediaViewThumbLocation location = info.target.getMediaThumbLocation(view, viewTop, viewBottom, top);
    if (location != null) {
      if ((info.position & POSITION_TOP) == 0 || (info.position & POSITION_LEFT) == 0) {
        location.setTopLeftRadius(0);
      }
      if ((info.position & POSITION_TOP) == 0 || (info.position & POSITION_RIGHT) == 0) {
        location.setTopRightRadius(0);
      }
      if ((info.position & POSITION_BOTTOM) == 0 || (info.position & POSITION_RIGHT) == 0) {
        location.setBottomRightRadius(0);
      }
      if ((info.position & POSITION_BOTTOM) == 0 || (info.position & POSITION_LEFT) == 0) {
        location.setBottomLeftRadius(0);
      }
    }
    return location;
  }

  private MediaWrapper caughtItem;

  private void cancelTouch () {
    caughtItem = null;
  }

  public boolean onTouchEvent (View view, MotionEvent e) {
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        caughtItem = null;
        if (!isAnimating() && mosaicItems != null) {
          for (MosaicItemInfo item : mosaicItems) {
            if (item.target.onTouchEvent(view, e)) {
              caughtItem = item.target;
              break;
            }
          }
        }
        return caughtItem != null;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        if (caughtItem != null) {
          boolean result = caughtItem.onTouchEvent(view, e);
          caughtItem = null;
          return result;
        }
        return false;
      }
      default: {
        return caughtItem != null && caughtItem.onTouchEvent(view, e);
      }
    }
  }

  public boolean performLongPress (View view) {
    if (caughtItem != null) {
      boolean result = caughtItem.performLongPress(view);
      caughtItem = null;
      return result;
    }
    return false;
  }

  // Updates

  public void updateMessageId (long oldMessageId, long newMessageId, boolean success) {
    if (mosaicItems != null) {
      for (MosaicItemInfo item : mosaicItems) {
        item.target.updateMessageId(oldMessageId, newMessageId, success);
      }
    }
    if (addedMosaicItems != null) {
      for (MosaicItemInfo item : addedMosaicItems) {
        item.target.updateMessageId(oldMessageId, newMessageId, success);
      }
    }
  }

  public void notifyInvalidateTargetsChanged () {
    if (mosaicItems != null) {
      for (MosaicItemInfo item : mosaicItems) {
        item.target.getFileProgress().notifyInvalidateTargetsChanged();
      }
    }
    if (addedMosaicItems != null) {
      for (MosaicItemInfo item : addedMosaicItems) {
        item.target.getFileProgress().notifyInvalidateTargetsChanged();
      }
    }
  }

  private TdApi.ChatType autoDownloadChatType;

  public void autoDownloadContent (TdApi.ChatType chatType) {
    this.autoDownloadChatType = chatType;
    if (mosaicItems != null) {
      for (MosaicItemInfo item : mosaicItems) {
        item.target.getFileProgress().downloadAutomatically(chatType);
      }
    }
    if (addedMosaicItems != null) {
      for (MosaicItemInfo item : addedMosaicItems) {
        item.target.getFileProgress().downloadAutomatically(chatType);
      }
    }
  }

  public void destroy () {
    if (mosaicItems != null) {
      for (MosaicItemInfo item : mosaicItems) {
        item.target.destroy();
      }
    }
    if (addedMosaicItems != null) {
      for (MosaicItemInfo item : addedMosaicItems) {
        item.target.destroy();
      }
    }
  }

  private int lastStartX, lastStartY;

  public <T extends View & DrawableProvider> void draw (T view, Canvas c, int startX, int startY, ComplexReceiver complexReceiver, boolean needSeparators) {
    lastStartX = startX;
    lastStartY = startY;
    draw(view, c, startX, startY, complexReceiver, mosaicItems, needSeparators);
    draw(view, c, startX, startY, complexReceiver, addedMosaicItems, needSeparators);
  }

  private static float[] lines = new float[8];

  private static <T extends View & DrawableProvider> void draw (T view, Canvas c, int startX, int startY, ComplexReceiver complexReceiver, MosaicItemInfo[] items, boolean needSeparators) {
    if (items != null) {
      for (MosaicItemInfo item : items) {
        // int key = item.target.getReceiverKey();
        DoubleImageReceiver preview = item.target.getPreviewReceiverReference(); // complexReceiver.getPreviewReceiver(key);
        Receiver receiver = item.target.getTargetReceiverReference(); // complexReceiver.getReceiver(key, item.target.needGif());
        if (preview == null || receiver == null) {
          continue;
        }
        int x = startX + item.getX();
        int y = startY + item.getY();
        int width = item.getWidth();
        int height = item.getHeight();
        final float alpha = item.getAlpha();
        final int saveCount;
        final boolean needRestore = alpha != 1f;
        if (needRestore) {
          saveCount = Views.save(c);
          float scale = .6f + .4f * alpha;
          c.scale(scale, scale, x + width / 2, y + height / 2);
          preview.setPaintAlpha(alpha * preview.getAlpha());
        } else {
          saveCount = -1;
        }

        item.target.buildContent(width, height);
        item.target.draw(view, c, x, y, preview, receiver, 1f);
        if (needSeparators) {
          Paint paint = Paints.strokeSeparatorPaint(ColorUtils.alphaColor(alpha, 0x1a000000));
          lines[0] = lines[4] = x;
          lines[2] = lines[6] = x + width;
          lines[1] = lines[3] = y;
          lines[5] = lines[7] = y + height;
          c.drawLine(lines[0], lines[1], lines[2], lines[3], paint);
          c.drawLine(lines[4], lines[5], lines[6], lines[7], paint);
        }

        if (needRestore) {
          Views.restore(c, saveCount);
          preview.restorePaintAlpha();
        }
      }
    }
  }
}
