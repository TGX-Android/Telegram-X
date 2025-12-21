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
 * File created on 21/12/2024
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibFilesManager;
import org.thunderdog.challegram.telegram.TdlibSender;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.AvatarView;
import org.thunderdog.challegram.widget.PopupLayout;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;

public class StoryViewController extends ViewController<StoryViewController.Args> implements
  PopupLayout.AnimatedPopupProvider, FactorAnimator.Target, View.OnClickListener,
  PopupLayout.TouchSectionProvider, Player.Listener {

  private static final long REVEAL_ANIMATION_DURATION = 280;
  private static final int ANIMATOR_REVEAL = 0;
  private static final int ANIMATOR_PROGRESS = 1;

  private static final long DEFAULT_PHOTO_DURATION_MS = 5000;

  public static class Args {
    public final long storySenderChatId;
    public final int storyId;
    public final @Nullable TdApi.Story preloadedStory;
    public final @Nullable List<TdApi.ChatActiveStories> activeStoriesList;
    public final int initialUserIndex;

    public Args (long storySenderChatId, int storyId) {
      this(storySenderChatId, storyId, null, null, 0);
    }

    public Args (long storySenderChatId, int storyId, @Nullable TdApi.Story preloadedStory) {
      this(storySenderChatId, storyId, preloadedStory, null, 0);
    }

    public Args (long storySenderChatId, int storyId, @Nullable TdApi.Story preloadedStory,
                 @Nullable List<TdApi.ChatActiveStories> activeStoriesList, int initialUserIndex) {
      this.storySenderChatId = storySenderChatId;
      this.storyId = storyId;
      this.preloadedStory = preloadedStory;
      this.activeStoriesList = activeStoriesList;
      this.initialUserIndex = initialUserIndex;
    }
  }

  public StoryViewController (@NonNull Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_storyView;
  }

  // State
  private long currentChatId;
  private int currentStoryId;
  private TdApi.Story currentStory;
  private List<TdApi.ChatActiveStories> activeStoriesList;
  private int currentUserIndex;
  private int currentStoryIndex;

  // Views
  private PopupLayout popupView;
  private FrameLayoutFix contentView;
  private StoryContentView storyContentView;
  private StoryProgressView storyProgressView;
  private StoryHeaderView storyHeaderView;

  // Animation
  private FactorAnimator revealAnimator;
  private float revealFactor;

  // Progress animation
  private FactorAnimator progressAnimator;
  private boolean isPaused;

  // ExoPlayer for video stories
  private ExoPlayer exoPlayer;

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.currentChatId = args.storySenderChatId;
    this.currentStoryId = args.storyId;
    this.currentStory = args.preloadedStory;
    this.activeStoriesList = args.activeStoriesList != null ? new ArrayList<>(args.activeStoriesList) : null;
    this.currentUserIndex = args.initialUserIndex;
  }

  @Override
  protected View onCreateView (Context context) {
    popupView = new PopupLayout(context);
    popupView.setOverlayStatusBar(true);
    popupView.setTouchProvider(this);
    popupView.setNeedRootInsets();
    popupView.init(true);
    popupView.setIgnoreAllInsets(true);
    popupView.setBoundController(this);

    contentView = new FrameLayoutFix(context) {
      private float startX, startY;
      private boolean isSliding;

      @Override
      public boolean onTouchEvent (MotionEvent event) {
        switch (event.getAction()) {
          case MotionEvent.ACTION_DOWN: {
            startX = event.getX();
            startY = event.getY();
            isSliding = false;
            pauseProgress();
            return true;
          }
          case MotionEvent.ACTION_MOVE: {
            float dx = event.getX() - startX;
            float dy = event.getY() - startY;
            if (!isSliding && Math.abs(dy) > Screen.getTouchSlopBig() && Math.abs(dx) < Screen.getTouchSlop()) {
              isSliding = true;
            }
            if (isSliding) {
              float translationY = Math.max(0, dy);
              contentView.setTranslationY(translationY);
              float factor = 1f - Math.min(1f, translationY / (getMeasuredHeight() * 0.3f));
              contentView.setAlpha(factor);
            }
            return true;
          }
          case MotionEvent.ACTION_UP:
          case MotionEvent.ACTION_CANCEL: {
            if (isSliding) {
              float dy = event.getY() - startY;
              if (dy > getMeasuredHeight() * 0.2f) {
                close();
              } else {
                contentView.animate().translationY(0).alpha(1f).setDuration(150).start();
                resumeProgress();
              }
            } else {
              // Handle tap for navigation
              float x = event.getX();
              float width = getMeasuredWidth();
              if (x < width * 0.3f) {
                navigatePrevious();
              } else if (x > width * 0.7f) {
                navigateNext();
              } else {
                resumeProgress();
              }
            }
            return true;
          }
        }
        return super.onTouchEvent(event);
      }
    };
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    contentView.setBackgroundColor(0xFF000000);

    // Story content view (displays photo/video)
    storyContentView = new StoryContentView(context);
    storyContentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    contentView.addView(storyContentView);

    // Progress bar at top
    storyProgressView = new StoryProgressView(context);
    FrameLayoutFix.LayoutParams progressParams = FrameLayoutFix.newParams(
      ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(2f));
    progressParams.topMargin = Screen.dp(16f);
    progressParams.leftMargin = Screen.dp(8f);
    progressParams.rightMargin = Screen.dp(8f);
    storyProgressView.setLayoutParams(progressParams);
    contentView.addView(storyProgressView);

    // Header with avatar and name
    storyHeaderView = new StoryHeaderView(context);
    FrameLayoutFix.LayoutParams headerParams = FrameLayoutFix.newParams(
      ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56f));
    headerParams.topMargin = Screen.dp(24f);
    storyHeaderView.setLayoutParams(headerParams);
    contentView.addView(storyHeaderView);

    // Close button
    ImageView closeButton = new ImageView(context);
    closeButton.setImageResource(R.drawable.baseline_close_24);
    closeButton.setColorFilter(Color.WHITE);
    closeButton.setOnClickListener(v -> close());
    closeButton.setPadding(Screen.dp(12f), Screen.dp(12f), Screen.dp(12f), Screen.dp(12f));
    FrameLayoutFix.LayoutParams closeParams = FrameLayoutFix.newParams(
      Screen.dp(48f), Screen.dp(48f), Gravity.TOP | Gravity.RIGHT);
    closeParams.topMargin = Screen.dp(24f);
    closeParams.rightMargin = Screen.dp(8f);
    closeButton.setLayoutParams(closeParams);
    contentView.addView(closeButton);

    // Load story
    if (currentStory != null) {
      displayStory(currentStory);
    } else {
      loadStory();
    }

    return contentView;
  }

  public void open () {
    getValue();
    popupView.showAnimatedPopupView(contentView, this);
  }

  public void close () {
    closeStory();
    popupView.hideWindow(true);
  }

  private void loadStory () {
    tdlib.client().send(new TdApi.GetStory(currentChatId, currentStoryId, false), result -> {
      if (result.getConstructor() == TdApi.Story.CONSTRUCTOR) {
        TdApi.Story story = (TdApi.Story) result;
        UI.post(() -> displayStory(story));
      } else {
        UI.post(() -> showError());
      }
    });
  }

  private void displayStory (TdApi.Story story) {
    this.currentStory = story;

    // Notify TDLib that we're viewing this story
    openStory();

    // Update header
    TdApi.Chat chat = tdlib.chat(story.posterChatId);
    if (chat != null) {
      storyHeaderView.setChat(chat, story.date);
    }

    // Determine story count for progress indicator
    int storyCount = 1;
    if (activeStoriesList != null && currentUserIndex < activeStoriesList.size()) {
      TdApi.ChatActiveStories activeStories = activeStoriesList.get(currentUserIndex);
      storyCount = activeStories.stories.length;
      // Find current story index
      for (int i = 0; i < activeStories.stories.length; i++) {
        if (activeStories.stories[i].storyId == currentStoryId) {
          currentStoryIndex = i;
          break;
        }
      }
    }
    storyProgressView.setStoryCount(storyCount, currentStoryIndex);

    // Display content
    switch (story.content.getConstructor()) {
      case TdApi.StoryContentPhoto.CONSTRUCTOR: {
        TdApi.StoryContentPhoto photoContent = (TdApi.StoryContentPhoto) story.content;
        storyContentView.setPhoto(tdlib, photoContent.photo);
        startProgressTimer(DEFAULT_PHOTO_DURATION_MS);
        break;
      }
      case TdApi.StoryContentVideo.CONSTRUCTOR: {
        TdApi.StoryContentVideo videoContent = (TdApi.StoryContentVideo) story.content;
        storyContentView.setVideo(tdlib, videoContent.video);
        prepareVideoPlayer(videoContent.video);
        break;
      }
      case TdApi.StoryContentUnsupported.CONSTRUCTOR: {
        showError();
        break;
      }
    }
  }

  private void showError () {
    // TODO: Show error message
  }

  private void openStory () {
    if (currentStory != null) {
      tdlib.client().send(new TdApi.OpenStory(currentChatId, currentStoryId), tdlib.okHandler());
    }
  }

  private void closeStory () {
    if (currentStory != null) {
      tdlib.client().send(new TdApi.CloseStory(currentChatId, currentStoryId), tdlib.okHandler());
    }
    stopProgress();
    releasePlayer();
  }

  private void prepareVideoPlayer (TdApi.StoryVideo video) {
    TdApi.File file = video.video;
    if (TD.isFileLoaded(file)) {
      startVideoPlayback(file.local.path);
    } else {
      tdlib.files().downloadFile(file, TdlibFilesManager.PRIORITY_USER_REQUEST_DOWNLOAD, (downloadedFile, error) -> {
        if (error == null && downloadedFile != null && TD.isFileLoaded(downloadedFile)) {
          UI.post(() -> startVideoPlayback(downloadedFile.local.path));
        }
      });
    }
  }

  private void startVideoPlayback (String path) {
    if (exoPlayer == null) {
      exoPlayer = U.newExoPlayer(context(), true);
      exoPlayer.addListener(this);
      exoPlayer.setVolume(1f);
    }
    exoPlayer.setMediaSource(U.newMediaSource(new java.io.File(path)));
    exoPlayer.prepare();
    exoPlayer.setPlayWhenReady(true);
  }

  @Override
  public void onPlaybackStateChanged (@Player.State int playbackState) {
    if (playbackState == Player.STATE_READY && exoPlayer != null) {
      long duration = exoPlayer.getDuration();
      if (duration != C.TIME_UNSET) {
        startProgressTimer(duration);
      }
    } else if (playbackState == Player.STATE_ENDED) {
      navigateNext();
    }
  }

  private void releasePlayer () {
    if (exoPlayer != null) {
      exoPlayer.release();
      exoPlayer = null;
    }
  }

  // Progress animation
  private void startProgressTimer (long durationMs) {
    stopProgress();
    progressAnimator = new FactorAnimator(ANIMATOR_PROGRESS, this, AnimatorUtils.LINEAR_INTERPOLATOR, durationMs, 0f);
    progressAnimator.animateTo(1f);
  }

  private void pauseProgress () {
    isPaused = true;
    if (progressAnimator != null) {
      progressAnimator.cancel();
    }
    if (exoPlayer != null) {
      exoPlayer.setPlayWhenReady(false);
    }
  }

  private void resumeProgress () {
    isPaused = false;
    if (progressAnimator != null && progressAnimator.getFactor() < 1f) {
      progressAnimator.animateTo(1f);
    }
    if (exoPlayer != null) {
      exoPlayer.setPlayWhenReady(true);
    }
  }

  private void stopProgress () {
    if (progressAnimator != null) {
      progressAnimator.cancel();
      progressAnimator = null;
    }
  }

  // Navigation
  private void navigatePrevious () {
    if (currentStoryIndex > 0 && activeStoriesList != null && currentUserIndex < activeStoriesList.size()) {
      TdApi.ChatActiveStories activeStories = activeStoriesList.get(currentUserIndex);
      currentStoryIndex--;
      closeStory();
      currentStoryId = activeStories.stories[currentStoryIndex].storyId;
      currentStory = null;
      loadStory();
    } else if (currentUserIndex > 0 && activeStoriesList != null) {
      // Go to previous user's last story
      closeStory();
      currentUserIndex--;
      TdApi.ChatActiveStories activeStories = activeStoriesList.get(currentUserIndex);
      currentChatId = activeStories.chatId;
      currentStoryIndex = activeStories.stories.length - 1;
      currentStoryId = activeStories.stories[currentStoryIndex].storyId;
      currentStory = null;
      loadStory();
    }
  }

  private void navigateNext () {
    if (activeStoriesList != null && currentUserIndex < activeStoriesList.size()) {
      TdApi.ChatActiveStories activeStories = activeStoriesList.get(currentUserIndex);
      if (currentStoryIndex < activeStories.stories.length - 1) {
        currentStoryIndex++;
        closeStory();
        currentStoryId = activeStories.stories[currentStoryIndex].storyId;
        currentStory = null;
        loadStory();
        return;
      } else if (currentUserIndex < activeStoriesList.size() - 1) {
        // Go to next user's first story
        closeStory();
        currentUserIndex++;
        activeStories = activeStoriesList.get(currentUserIndex);
        currentChatId = activeStories.chatId;
        currentStoryIndex = 0;
        currentStoryId = activeStories.stories[0].storyId;
        currentStory = null;
        loadStory();
        return;
      }
    }
    // No more stories, close viewer
    close();
  }

  // PopupLayout.AnimatedPopupProvider
  @Override
  public void prepareShowAnimation () {
    revealAnimator = new FactorAnimator(ANIMATOR_REVEAL, this, AnimatorUtils.DECELERATE_INTERPOLATOR, REVEAL_ANIMATION_DURATION);
    contentView.setAlpha(0f);
    contentView.setScaleX(0.9f);
    contentView.setScaleY(0.9f);
  }

  @Override
  public void launchShowAnimation (PopupLayout popup) {
    revealAnimator.animateTo(1f);
  }

  @Override
  public boolean launchHideAnimation (PopupLayout popup, FactorAnimator originalAnimator) {
    revealAnimator.animateTo(0f);
    return true;
  }

  // FactorAnimator.Target
  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (id == ANIMATOR_REVEAL) {
      this.revealFactor = factor;
      contentView.setAlpha(factor);
      float scale = 0.9f + (0.1f * factor);
      contentView.setScaleX(scale);
      contentView.setScaleY(scale);
    } else if (id == ANIMATOR_PROGRESS) {
      storyProgressView.setProgress(currentStoryIndex, factor);
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (id == ANIMATOR_REVEAL) {
      if (finalFactor == 0f) {
        popupView.onCustomHideAnimationComplete();
      } else if (finalFactor == 1f) {
        popupView.onCustomShowComplete();
      }
    } else if (id == ANIMATOR_PROGRESS) {
      if (finalFactor == 1f && !isPaused) {
        navigateNext();
      }
    }
  }

  // View.OnClickListener
  @Override
  public void onClick (View v) {
    // Handle clicks
  }

  // PopupLayout.TouchSectionProvider
  @Override
  public boolean shouldTouchOutside (float x, float y) {
    return false;
  }

  @Override
  public void destroy () {
    super.destroy();
    closeStory();
    releasePlayer();
  }

  // Inner classes for views

  private class StoryContentView extends View {
    private ImageReceiver imageReceiver;
    private ComplexReceiver complexReceiver;

    public StoryContentView (Context context) {
      super(context);
      imageReceiver = new ImageReceiver(this, 0);
      complexReceiver = new ComplexReceiver(this);
    }

    public void setPhoto (Tdlib tdlib, TdApi.Photo photo) {
      TdApi.PhotoSize largest = tgx.td.Td.findBiggest(photo.sizes);
      if (largest != null) {
        ImageFile imageFile = new ImageFile(tdlib, largest.photo);
        imageFile.setScaleType(ImageFile.FIT_CENTER);
        imageFile.setNeedCancellation(true);
        imageReceiver.requestFile(imageFile);
      }
    }

    public void setVideo (Tdlib tdlib, TdApi.StoryVideo video) {
      // Show thumbnail while video loads
      if (video.thumbnail != null) {
        ImageFile thumbnailFile = new ImageFile(tdlib, video.thumbnail.file);
        thumbnailFile.setScaleType(ImageFile.FIT_CENTER);
        thumbnailFile.setNeedCancellation(true);
        imageReceiver.requestFile(thumbnailFile);
      }
    }

    @Override
    protected void onDraw (Canvas canvas) {
      imageReceiver.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
      if (imageReceiver.needPlaceholder()) {
        canvas.drawColor(0xFF000000);
      }
      imageReceiver.draw(canvas);
    }

    @Override
    protected void onAttachedToWindow () {
      super.onAttachedToWindow();
      imageReceiver.attach();
      complexReceiver.attach();
    }

    @Override
    protected void onDetachedFromWindow () {
      super.onDetachedFromWindow();
      imageReceiver.detach();
      complexReceiver.detach();
    }

    public void destroy () {
      imageReceiver.destroy();
      complexReceiver.performDestroy();
    }
  }

  private class StoryProgressView extends View {
    private Paint backgroundPaint;
    private Paint progressPaint;
    private int storyCount = 1;
    private int currentIndex = 0;
    private float currentProgress = 0f;
    private RectF segmentRect = new RectF();
    private float cornerRadius;

    public StoryProgressView (Context context) {
      super(context);
      backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      backgroundPaint.setColor(ColorUtils.alphaColor(0.3f, Color.WHITE));

      progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      progressPaint.setColor(Color.WHITE);

      cornerRadius = Screen.dp(1f);
    }

    public void setStoryCount (int count, int currentIndex) {
      this.storyCount = Math.max(1, count);
      this.currentIndex = currentIndex;
      this.currentProgress = 0f;
      invalidate();
    }

    public void setProgress (int index, float progress) {
      if (index == currentIndex) {
        this.currentProgress = progress;
        invalidate();
      }
    }

    @Override
    protected void onDraw (Canvas canvas) {
      int width = getMeasuredWidth();
      int height = getMeasuredHeight();
      float gap = Screen.dp(4f);
      float segmentWidth = (width - (gap * (storyCount - 1))) / storyCount;

      for (int i = 0; i < storyCount; i++) {
        float left = i * (segmentWidth + gap);
        float right = left + segmentWidth;

        // Draw background
        segmentRect.set(left, 0, right, height);
        canvas.drawRoundRect(segmentRect, cornerRadius, cornerRadius, backgroundPaint);

        // Draw progress
        if (i < currentIndex) {
          // Fully completed
          canvas.drawRoundRect(segmentRect, cornerRadius, cornerRadius, progressPaint);
        } else if (i == currentIndex) {
          // Current story - partial progress
          float progressRight = left + (segmentWidth * currentProgress);
          segmentRect.set(left, 0, progressRight, height);
          canvas.drawRoundRect(segmentRect, cornerRadius, cornerRadius, progressPaint);
        }
      }
    }
  }

  private class StoryHeaderView extends FrameLayout {
    private AvatarView avatarView;
    private TextView nameView;
    private TextView timeView;

    public StoryHeaderView (Context context) {
      super(context);

      avatarView = new AvatarView(context);
      avatarView.setId(R.id.avatar);
      LayoutParams avatarParams = new LayoutParams(Screen.dp(40f), Screen.dp(40f));
      avatarParams.leftMargin = Screen.dp(12f);
      avatarParams.gravity = Gravity.CENTER_VERTICAL;
      addView(avatarView, avatarParams);

      nameView = new TextView(context);
      nameView.setTextColor(Color.WHITE);
      nameView.setTextSize(15f);
      nameView.setTypeface(Fonts.getRobotoMedium());
      nameView.setSingleLine();
      LayoutParams nameParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
      nameParams.leftMargin = Screen.dp(60f);
      nameParams.topMargin = Screen.dp(6f);
      addView(nameView, nameParams);

      timeView = new TextView(context);
      timeView.setTextColor(ColorUtils.alphaColor(0.7f, Color.WHITE));
      timeView.setTextSize(13f);
      timeView.setSingleLine();
      LayoutParams timeParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
      timeParams.leftMargin = Screen.dp(60f);
      timeParams.topMargin = Screen.dp(28f);
      addView(timeView, timeParams);
    }

    public void setChat (TdApi.Chat chat, int date) {
      avatarView.setChat(tdlib, chat);
      nameView.setText(chat.title);
      timeView.setText(Lang.timeOrDateShort(date, java.util.concurrent.TimeUnit.SECONDS));
    }
  }
}
