package org.thunderdog.challegram.component.thread;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.util.Log;
import android.view.animation.LinearInterpolator;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.ReceiverUpdateListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.lambda.Destroyable;

public class UserAvatarStack implements Destroyable, FactorAnimator.Target {
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
  private final Tdlib mTdLib;
  private int mStrokeColor;
  private final ReceiverUpdateListener receiverUpdateListener = new ReceiverUpdateListener() {
    @Override
    public void onRequestInvalidate (Receiver receiver) {
      if (mParent != null) mParent.invalidate();
    }
  };
  private int currentWidth, currentHeight;
  private float animatedFactor = 1f;
  private int debugIndex = 0;

  public UserAvatarStack (TGCommentButton parent, Tdlib tdlib) {
    mTdLib = tdlib;
    mParent = parent;
    mTrash = new ArrayList<>();
    mAvatars = new LinkedHashMap<>();
    mBounds = new Rect();
    mOldBounds = new Rect();
    mAnimator = new FactorAnimator(ADD_ANIMATOR, this, LINEAR_INTERPOLATOR, 280);
  }

  public void setStrokeColor(int color) {
    mStrokeColor = color;
  }

  public void update(TdApi.MessageSender[] senders, boolean animated) {
    clearTrash();
    Set<Long> userIdsList = new HashSet<>();
    if (senders != null) {
      for (TdApi.MessageSender sender: senders) {
        if (sender.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR) {
          long userId = ((TdApi.MessageSenderUser) sender).userId;
          userIdsList.add(userId);
        } else if (sender.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR) {
          long chatId = ((TdApi.MessageSenderChat) sender).chatId;
          userIdsList.add(chatId);
        }
      }
    }
    boolean changed = userIdsList.size() != mAvatars.size();
    for (Long userId : userIdsList) {
      ImageFile imageFile = mTdLib.cache().userAvatar(userId);
      if (imageFile == null) {
        imageFile = TD.getAvatar(mTdLib, mTdLib.chat(userId));
      }
      if (!mAvatars.containsKey(userId)) {
        ImageReceiver imageReceiver = new ImageReceiver(null, sAvatarSizeDp / 2);
        imageReceiver.setUpdateListener(receiverUpdateListener);
        if (imageFile != null) {
          imageFile.setSize(sAvatarSizeDp);
          imageReceiver.requestFile(imageFile);
        }
        Avatar avatar = new Avatar(imageReceiver);
        avatar.placeholder = mTdLib.cache().userPlaceholder(userId, false, sAvatarSize / 2f, null);
        avatar.debugIndex = debugIndex++;
        changed = true;
        mAvatars.put(userId, avatar);
      }
    }

    Iterator<Map.Entry<Long, Avatar>> iterator = mAvatars.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<Long, Avatar> entry = iterator.next();
      if (!userIdsList.contains(entry.getKey())) {
        Avatar avatar = entry.getValue();
        avatar.pendingRemove = true;
        mTrash.add(avatar);
        changed = true;
        iterator.remove();
      }
    }
    currentWidth = !mAvatars.isEmpty() ? sAvatarSizeDp + (mAvatars.size() - 1) * sOffset : 0;
    currentHeight = sAvatarSizeDp;
    mOldBounds.set(
      mBounds.left,
      mBounds.top,
      mBounds.right,
      mBounds.bottom
    );
    if (animated && changed) {
      animatedFactor = 0f;
      Log.e("Avatars", "animate changes " + mAnimator.getFactor());
      mAnimator.animateTo(1f);
    } else {
      Log.e("Avatars", "update changes");
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

  public void draw(Canvas canvas, int endX, int endY) {
    mBounds.set(
      endX - currentWidth,
      endY,
      endX,
      endY + currentHeight
    );
    if (DEBUG) {
      canvas.drawRect(mBounds, Paints.strokeSmallPaint(Color.RED));
    }
    for (Avatar avatar : mTrash) {
      avatar.scale = 1 - animatedFactor;
      avatar.positionY = mOldBounds.top;
      avatar.strokeColor = mStrokeColor;
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
      avatar.positionX = startPosX - animatedOffset;
      avatar.positionY = mBounds.top;
      avatar.strokeColor = mStrokeColor;
      avatar.draw(canvas);
    });
  }

  public void attach() {
    forEachAvatar((pos, avatar) -> avatar.imageReceiver.attach());
  }

  public void detach() {
    forEachAvatar((pos, avatar) -> avatar.imageReceiver.detach());
  }

  @Override
  public void performDestroy () {
    forEachAvatar((pos, avatar) -> avatar.imageReceiver.destroy());
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
      avatar.imageReceiver.detach();
      avatar.imageReceiver.destroy();
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

  private static class Avatar {
    int debugIndex = 0;
    ImageReceiver imageReceiver;
    AvatarPlaceholder placeholder;
    int positionX = 0;
    int positionY = 0;
    boolean pendingRemove = false;
    private float scale = 0f;
    private final int height = sAvatarSizeDp;
    private final int width = sAvatarSizeDp;
    private int strokeColor = 0;

    Avatar (ImageReceiver imageReceiver) {
      this.imageReceiver = imageReceiver;
    }

    void draw(Canvas canvas) {
      int center = height / 2;
      final int saveCount = canvas.save();
      canvas.translate(positionX + center, positionY + center);
      canvas.scale(scale, scale);
      if (imageReceiver.getCurrentFile() != null) {
        imageReceiver.setBounds(-center, -center, width - center, height - center);
        imageReceiver.draw(canvas);
      } else {
        placeholder.draw(canvas, 0, 0, 1f, height / 2f);
      }
      canvas.drawCircle(
        0,
        0,
        height / 2f,
        Paints.strokeSmallPaint(strokeColor)
      );
      canvas.restoreToCount(saveCount);
    }
  }
}
