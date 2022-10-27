package org.thunderdog.challegram.component.thread;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Region;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.ReceiverUpdateListener;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import me.vkryl.android.animator.FactorAnimator;

public class UserAvatarStack implements FactorAnimator.Target {
  private static final long ANIMATION_DURATION = 250L;
  private static final boolean DEBUG = false;
  private static final int ADD_ANIMATOR = 1;
  private static final LinearInterpolator LINEAR_INTERPOLATOR = new LinearInterpolator();
  private static final int sAvatarSize = 24;
  private static final int sAvatarSizeDp = Screen.dp(sAvatarSize);
  private static final int sOffset = Screen.dp(16);
  private final LinkedHashMap<Long, Avatar> mAvatars;
  private final List<Avatar> mTrash;
  private final TGCommentButton mParent;
  private final FactorAnimator mAnimator;
  private final Rect mBounds;
  private final Rect mOldBounds;
  private final ReceiverUpdateListener receiverUpdateListener = new ReceiverUpdateListener() {
    @Override
    public void onRequestInvalidate (Receiver receiver) {
      if (mParent != null) mParent.invalidate();
    }
  };
  private ComplexReceiver complexReceiver;
  private int currentWidth, currentHeight;
  private float animatedFactor = 1f;

  public static final class AvatarInfo {
    private final long userId;
    @Nullable private final ImageFile imageFile;
    @Nullable private final AvatarPlaceholder placeholder;

    public AvatarInfo (long userId, @Nullable ImageFile imageFile, @Nullable AvatarPlaceholder placeholder) {
      this.userId = userId;
      this.imageFile = imageFile;
      this.placeholder = placeholder;
    }
  }

  public static int getDefaultAvatarSize() {
    return sAvatarSizeDp;
  }

  public UserAvatarStack (TGCommentButton parent) {
    mParent = parent;
    mTrash = new ArrayList<>();
    mAvatars = new LinkedHashMap<>();
    mBounds = new Rect();
    mOldBounds = new Rect();
    mAnimator = new FactorAnimator(ADD_ANIMATOR, this, LINEAR_INTERPOLATOR, ANIMATION_DURATION);
  }

  public void setReceiversPool (ComplexReceiver complexReceiver) {
    this.complexReceiver = complexReceiver;
    forEachAvatar((pos, avatar) -> {
      avatar.setReceiver(this.complexReceiver, this.receiverUpdateListener);
    });
    mParent.invalidate();
  }

  public void update(Map<Long, AvatarInfo> avatarInfo, boolean animated) {
    clearTrash();
    boolean changed = updateInfo(avatarInfo);
    measure();
    if (animated && changed) {
      animatedFactor = 0f;
      mAnimator.animateTo(1f);
    } else {
      animatedFactor = 1f;
      clearTrash();
      mParent.invalidate();
    }
  }

  public int getCurrentWidth () {
    return currentWidth;
  }

  public int getCurrentHeight () {
    return currentHeight;
  }

  public void setEndEdge (int endX, int endY) {
    if (mBounds.right != endX || mBounds.top != endY) {
      mBounds.set(
        endX - currentWidth,
        endY,
        endX,
        endY + currentHeight
      );
    }
  }

  public void draw(Canvas canvas) {
    if (DEBUG) {
      canvas.drawRect(mBounds, Paints.strokeSmallPaint(Color.RED));
    }
    for (Avatar avatar : mTrash) {
      avatar.scale = 1 - animatedFactor;
      avatar.updatePosition(avatar.positionX, mOldBounds.top);
      avatar.shouldClip = true;
      avatar.draw(canvas);
    }
    final int lastPosition = mAvatars.size() - 1;
    forEachAvatar((pos, avatar) -> {
      if (avatar == null) return;
      if (avatar.scale != 1f) {
        avatar.scale = animatedFactor;
      }
      int startPosX = mBounds.right - sAvatarSizeDp;
      int animatedOffset = (int) (sOffset * (lastPosition - pos) * animatedFactor);
      avatar.updatePosition(startPosX - animatedOffset, mBounds.top);
      avatar.shouldClip = pos != lastPosition;
      avatar.draw(canvas);
    });
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    animatedFactor = factor;
    mParent.invalidate();
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    clearTrash();
    mAnimator.forceFactor(0f);
  }

  private void clearTrash() {
    for (Avatar avatar : mTrash) {
      avatar.destroy();
      if (complexReceiver != null) {
        complexReceiver.clearReceivers(avatar.id);
      }
    }
    mTrash.clear();
  }

  private void forEachAvatar(BiConsumer<Integer, Avatar> consumer) {
    int position = 0;
    for (Long key : mAvatars.keySet()) {
      Avatar avatar = mAvatars.get(key);
      consumer.accept(position++, avatar);
    }
  }

  private boolean updateInfo(Map<Long, AvatarInfo> newInfo) {
    boolean changed = false;
    for (Long userId : newInfo.keySet()) {
      AvatarInfo info = newInfo.get(userId);
      if (!mAvatars.containsKey(userId) && info != null) {
        Avatar avatar = new Avatar(info);
        if (complexReceiver != null) {
          avatar.setReceiver(complexReceiver, receiverUpdateListener);
        }
        changed = true;
        mAvatars.put(userId, avatar);
      }
    }
    Iterator<Map.Entry<Long, Avatar>> iterator = mAvatars.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<Long, Avatar> entry = iterator.next();
      Avatar avatar = entry.getValue();
      if (complexReceiver != null && avatar.imageReceiver == null) {
        avatar.setReceiver(complexReceiver, receiverUpdateListener);
      }
      if (!newInfo.containsKey(entry.getKey())) {
        mTrash.add(avatar);
        changed = true;
        iterator.remove();
      }
    }
    return changed;
  }

  private void measure() {
    currentWidth = !mAvatars.isEmpty() ? sAvatarSizeDp + (mAvatars.size() - 1) * sOffset : 0;
    currentHeight = sAvatarSizeDp;
    mOldBounds.set(
      mBounds.left,
      mBounds.top,
      mBounds.right,
      mBounds.bottom
    );
    setEndEdge(mBounds.right, mBounds.top);
  }

  private static final class Avatar {
    private final long id;
    private final ImageFile imageFile;
    private final AvatarPlaceholder placeholder;
    private final int height = sAvatarSizeDp;
    private final int width = sAvatarSizeDp;
    private ImageReceiver imageReceiver;
    private int positionX = 0;
    private int positionY = 0;
    private float scale = 0f;
    private final Path clipPath;
    private boolean shouldClip = false;

    Avatar (AvatarInfo avatarInfo) {
      this.id = avatarInfo.userId;
      this.imageFile = avatarInfo.imageFile;
      this.placeholder = avatarInfo.placeholder;
      this.clipPath = new Path();
    }

    void setReceiver(ComplexReceiver receiver, ReceiverUpdateListener receiverUpdateListener) {
      imageReceiver = receiver.getImageReceiver(this.id);
      imageReceiver.setRadius(sAvatarSizeDp / 2);
      imageReceiver.setUpdateListener(receiverUpdateListener);
      if (imageFile != null) {
        imageFile.setSize(sAvatarSizeDp);
        imageReceiver.requestFile(imageFile);
      }
    }

    void updatePosition(int x, int y) {
      if (positionX != x || positionY != y) {
        positionX = x;
        positionY = y;
        clipPath.reset();
        clipPath.addCircle(sOffset, 0, height / 2f + Screen.dp(2), Path.Direction.CW);
        clipPath.close();
      }
    }

    void draw(Canvas canvas) {
      int center = height / 2;
      int radius = height / 2;
      final int saveCount = canvas.save();
      canvas.translate(positionX + center, positionY + center);
      canvas.scale(scale, scale);
      if (needBorder()) {
        canvas.clipPath(clipPath, Region.Op.DIFFERENCE);
      }
      if (imageReceiver != null && imageFile != null) {
        imageReceiver.setBounds(-center, -center, width - center, height - center);
        if (imageReceiver.needPlaceholder()) {
          imageReceiver.drawPlaceholderRounded(canvas, radius);
        }
        imageReceiver.draw(canvas);
      } else if (placeholder != null) {
        placeholder.draw(canvas, 0, 0, 1f, radius);
      }
      canvas.restoreToCount(saveCount);
    }

    boolean needBorder() {
      return shouldClip && ((imageReceiver != null && imageFile != null) || placeholder != null);
    }

    void destroy() {
      imageReceiver = null;
    }
  }
}
