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
import android.graphics.SurfaceTexture;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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

import tgx.td.Td;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.ui.ShareController;
import org.thunderdog.challegram.telegram.TdlibFilesManager;
import org.thunderdog.challegram.telegram.TdlibSender;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.widget.AvatarView;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.RootFrameLayout;

import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;

public class StoryViewController extends ViewController<StoryViewController.Args> implements
  PopupLayout.AnimatedPopupProvider, FactorAnimator.Target, View.OnClickListener,
  PopupLayout.TouchSectionProvider, Player.Listener, RootFrameLayout.InsetsChangeListener {

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
  private StoryReplyInputView storyReplyInputView;
  private TextView captionView;
  private FrameLayout loadingContainer;
  private android.widget.ProgressBar loadingView;

  // Double-tap detection for reactions
  private long lastTapTime;
  private static final long DOUBLE_TAP_TIMEOUT = 300;

  // Animation
  private FactorAnimator revealAnimator;
  private float revealFactor;

  // Progress animation
  private FactorAnimator progressAnimator;
  private boolean isPaused;

  // ExoPlayer for video stories
  private ExoPlayer exoPlayer;
  private TextureView videoTextureView;
  private Surface videoSurface;

  // Navigation bar insets
  private int navigationBarHeight = 0;

  // Status bar insets (using RootFrameLayout listener)
  private RootFrameLayout rootView;
  private int topInsetOffset = 0;

  // Views that need status bar offset
  private ImageView closeButton;
  private ImageView moreButton;

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
      private boolean isSliding; // Vertical slide (close gesture)
      private boolean isHorizontalSwipe; // Horizontal swipe (switch users)
      private boolean gestureDecided;

      @Override
      protected void onAttachedToWindow () {
        super.onAttachedToWindow();
        setRootView(Views.findAncestor(this, RootFrameLayout.class, false));
      }

      @Override
      protected void onDetachedFromWindow () {
        super.onDetachedFromWindow();
        setRootView(null);
      }

      @Override
      public boolean onInterceptTouchEvent (MotionEvent event) {
        // Only intercept touches in the story content area (middle of screen)
        // Allow header (top ~100dp) and footer (bottom ~60dp) to receive touches for buttons
        float y = event.getY();
        int height = getMeasuredHeight();
        int headerHeight = topInsetOffset + Screen.dp(80f); // Status bar + header with buttons
        int footerHeight = navigationBarHeight + Screen.dp(56f); // Nav bar + reply input

        boolean inHeader = y < headerHeight;
        boolean inFooter = y > height - footerHeight;

        // Don't intercept if touch is on header or footer (let buttons handle it)
        if (inHeader || inFooter) {
          return false;
        }

        // Intercept touches in content area for swipe gestures
        return true;
      }

      @Override
      public boolean onTouchEvent (MotionEvent event) {
        switch (event.getAction()) {
          case MotionEvent.ACTION_DOWN: {
            startX = event.getX();
            startY = event.getY();
            isSliding = false;
            isHorizontalSwipe = false;
            gestureDecided = false;
            // Request parent to not intercept touch events
            if (getParent() != null) {
              getParent().requestDisallowInterceptTouchEvent(true);
            }
            pauseProgress();
            return true;
          }
          case MotionEvent.ACTION_MOVE: {
            float dx = event.getX() - startX;
            float dy = event.getY() - startY;

            if (!gestureDecided) {
              float absDx = Math.abs(dx);
              float absDy = Math.abs(dy);
              float touchSlop = Screen.getTouchSlop();

              if (absDy > touchSlop || absDx > touchSlop) {
                gestureDecided = true;
                if (absDy > absDx) {
                  // Vertical gesture - slide to close
                  isSliding = true;
                } else {
                  // Horizontal gesture - swipe between users
                  isHorizontalSwipe = true;
                }
              }
            }

            if (isSliding) {
              float translationY = Math.max(0, dy);
              contentView.setTranslationY(translationY);
              float factor = 1f - Math.min(1f, translationY / (getMeasuredHeight() * 0.3f));
              contentView.setAlpha(factor);
            } else if (isHorizontalSwipe) {
              // Visual feedback for horizontal swipe
              contentView.setTranslationX(dx * 0.3f);
            }
            return true;
          }
          case MotionEvent.ACTION_UP:
          case MotionEvent.ACTION_CANCEL: {
            boolean cancelled = event.getAction() == MotionEvent.ACTION_CANCEL;

            if (isSliding) {
              float dy = event.getY() - startY;
              if (dy > getMeasuredHeight() * 0.2f && !cancelled) {
                close();
              } else {
                contentView.animate().translationY(0).alpha(1f).setDuration(150).start();
                resumeProgress();
              }
            } else if (isHorizontalSwipe) {
              float dx = event.getX() - startX;
              contentView.animate().translationX(0).setDuration(150).start();

              if (!cancelled && Math.abs(dx) > getMeasuredWidth() * 0.15f) {
                if (dx > 0) {
                  // Swipe right - previous user
                  navigateToPreviousUser();
                } else {
                  // Swipe left - next user
                  navigateToNextUser();
                }
              } else {
                resumeProgress();
              }
            } else {
              // Handle tap for navigation or double-tap for reaction
              long now = System.currentTimeMillis();
              float x = event.getX();
              float width = getMeasuredWidth();
              float centerWidth = width * 0.4f;
              float centerStart = (width - centerWidth) / 2f;
              boolean isInCenter = x >= centerStart && x <= centerStart + centerWidth;

              if (isInCenter && now - lastTapTime < DOUBLE_TAP_TIMEOUT) {
                // Double tap in center - send heart reaction
                sendHeartReaction();
                lastTapTime = 0; // Reset to prevent triple-tap
              } else {
                // Single tap - navigate between story segments
                if (x < width * 0.3f) {
                  navigatePrevious();
                } else if (x > width * 0.7f) {
                  navigateNext();
                } else {
                  resumeProgress();
                }
                lastTapTime = now;
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

    // Get navigation bar height directly from system resources
    // (WindowInsets callback doesn't work because PopupLayout uses setIgnoreAllInsets(true))
    navigationBarHeight = Screen.getNavigationBarHeight();

    // Get initial status bar height - will be updated dynamically via RootFrameLayout.InsetsChangeListener
    topInsetOffset = HeaderView.getTopOffset();

    // Story content view (displays photo/video thumbnail)
    storyContentView = new StoryContentView(context);
    storyContentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    contentView.addView(storyContentView);

    // TextureView for video playback
    videoTextureView = new TextureView(context);
    videoTextureView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    videoTextureView.setVisibility(View.GONE);
    videoTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
      @Override
      public void onSurfaceTextureAvailable (@NonNull SurfaceTexture surface, int width, int height) {
        videoSurface = new Surface(surface);
        if (exoPlayer != null) {
          exoPlayer.setVideoSurface(videoSurface);
        }
      }

      @Override
      public void onSurfaceTextureSizeChanged (@NonNull SurfaceTexture surface, int width, int height) {}

      @Override
      public boolean onSurfaceTextureDestroyed (@NonNull SurfaceTexture surface) {
        if (videoSurface != null) {
          videoSurface.release();
          videoSurface = null;
        }
        return true;
      }

      @Override
      public void onSurfaceTextureUpdated (@NonNull SurfaceTexture surface) {}
    });
    contentView.addView(videoTextureView);

    // Progress bar at top (offset by status bar height)
    storyProgressView = new StoryProgressView(context);
    FrameLayoutFix.LayoutParams progressParams = FrameLayoutFix.newParams(
      ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(2f));
    progressParams.topMargin = Screen.dp(16f) + topInsetOffset;
    progressParams.leftMargin = Screen.dp(8f);
    progressParams.rightMargin = Screen.dp(8f);
    storyProgressView.setLayoutParams(progressParams);
    contentView.addView(storyProgressView);

    // Header with avatar and name (offset by status bar height)
    storyHeaderView = new StoryHeaderView(context);
    FrameLayoutFix.LayoutParams headerParams = FrameLayoutFix.newParams(
      ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56f));
    headerParams.topMargin = Screen.dp(24f) + topInsetOffset;
    storyHeaderView.setLayoutParams(headerParams);
    contentView.addView(storyHeaderView);

    // Close button (rightmost, offset by status bar height)
    closeButton = new ImageView(context);
    closeButton.setImageResource(R.drawable.baseline_close_24);
    closeButton.setColorFilter(Color.WHITE);
    closeButton.setOnClickListener(v -> close());
    closeButton.setPadding(Screen.dp(12f), Screen.dp(12f), Screen.dp(12f), Screen.dp(12f));
    FrameLayoutFix.LayoutParams closeParams = FrameLayoutFix.newParams(
      Screen.dp(48f), Screen.dp(48f), Gravity.TOP | Gravity.RIGHT);
    closeParams.topMargin = Screen.dp(24f) + topInsetOffset;
    closeParams.rightMargin = Screen.dp(8f);
    closeButton.setLayoutParams(closeParams);
    contentView.addView(closeButton);

    // More button (three dots menu, left of close, offset by status bar height)
    moreButton = new ImageView(context);
    moreButton.setImageResource(R.drawable.baseline_more_vert_24);
    moreButton.setColorFilter(Color.WHITE);
    moreButton.setOnClickListener(v -> showStoryOptions());
    moreButton.setPadding(Screen.dp(12f), Screen.dp(12f), Screen.dp(12f), Screen.dp(12f));
    FrameLayoutFix.LayoutParams moreParams = FrameLayoutFix.newParams(
      Screen.dp(48f), Screen.dp(48f), Gravity.TOP | Gravity.RIGHT);
    moreParams.topMargin = Screen.dp(24f) + topInsetOffset;
    moreParams.rightMargin = Screen.dp(56f);
    moreButton.setLayoutParams(moreParams);
    contentView.addView(moreButton);

    // Caption view (above reply input) with gradient background for readability
    captionView = new TextView(context);
    captionView.setTextColor(Color.WHITE);
    captionView.setTextSize(14f);
    captionView.setMaxLines(3);
    captionView.setEllipsize(android.text.TextUtils.TruncateAt.END);
    captionView.setPadding(Screen.dp(16f), Screen.dp(12f), Screen.dp(16f), Screen.dp(12f));
    captionView.setBackgroundColor(ColorUtils.alphaColor(0.4f, Color.BLACK));
    captionView.setShadowLayer(Screen.dp(1f), 0, Screen.dp(1f), ColorUtils.alphaColor(0.3f, Color.BLACK));
    captionView.setVisibility(View.GONE);
    FrameLayoutFix.LayoutParams captionParams = FrameLayoutFix.newParams(
      ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
      Gravity.BOTTOM);
    captionParams.bottomMargin = Screen.dp(80f) + navigationBarHeight; // Above reply input
    captionView.setLayoutParams(captionParams);
    contentView.addView(captionView);

    // Reply input at bottom
    storyReplyInputView = new StoryReplyInputView(context);
    FrameLayoutFix.LayoutParams replyParams = FrameLayoutFix.newParams(
      ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56f),
      Gravity.BOTTOM);
    replyParams.leftMargin = Screen.dp(8f);
    replyParams.rightMargin = Screen.dp(8f);
    replyParams.bottomMargin = Screen.dp(16f) + navigationBarHeight;
    storyReplyInputView.setLayoutParams(replyParams);
    contentView.addView(storyReplyInputView);

    // Loading indicator (centered) with container
    loadingContainer = new FrameLayout(context);
    loadingContainer.setBackgroundColor(ColorUtils.alphaColor(0.6f, Color.BLACK));
    FrameLayoutFix.LayoutParams loadingContainerParams = FrameLayoutFix.newParams(
      ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
    loadingContainer.setLayoutParams(loadingContainerParams);
    loadingContainer.setVisibility(View.GONE);

    // Create inner container for spinner and text
    FrameLayout loadingInner = new FrameLayout(context);
    FrameLayout.LayoutParams innerParams = new FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
    loadingInner.setLayoutParams(innerParams);

    loadingView = new android.widget.ProgressBar(context);
    loadingView.setIndeterminate(true);
    loadingView.getIndeterminateDrawable().setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
    FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(
      Screen.dp(64f), Screen.dp(64f), Gravity.CENTER_HORIZONTAL | Gravity.TOP);
    loadingView.setLayoutParams(loadingParams);
    loadingInner.addView(loadingView);

    // Loading text below spinner
    TextView loadingText = new TextView(context);
    loadingText.setText(R.string.LoadingMessageSeen);
    loadingText.setTextColor(Color.WHITE);
    loadingText.setTextSize(14f);
    FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP);
    textParams.topMargin = Screen.dp(72f); // Below spinner
    loadingText.setLayoutParams(textParams);
    loadingInner.addView(loadingText);

    loadingContainer.addView(loadingInner);
    contentView.addView(loadingContainer);

    // Fetch fresh ChatActiveStories to ensure we have all stories
    refreshActiveStories();

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

  private void refreshActiveStories () {
    // Fetch fresh ChatActiveStories from TdLib to ensure we have all stories
    tdlib.client().send(new TdApi.GetChatActiveStories(currentChatId), result -> {
      if (result.getConstructor() == TdApi.ChatActiveStories.CONSTRUCTOR) {
        TdApi.ChatActiveStories freshStories = (TdApi.ChatActiveStories) result;
        UI.post(() -> {
          if (!isDestroyed()) {
            // Update the active stories list for this chat
            if (activeStoriesList != null) {
              // Find and update the entry for this chat
              for (int i = 0; i < activeStoriesList.size(); i++) {
                if (activeStoriesList.get(i).chatId == currentChatId) {
                  activeStoriesList.set(i, freshStories);
                  break;
                }
              }
            } else {
              // Create a new list with just this chat's stories
              activeStoriesList = new ArrayList<>();
              activeStoriesList.add(freshStories);
              currentUserIndex = 0;
            }
            // Update progress view with fresh story count
            if (storyProgressView != null) {
              int storyCount = freshStories.stories.length;
              // Find current story index in fresh data
              currentStoryIndex = 0;
              for (int i = 0; i < freshStories.stories.length; i++) {
                if (freshStories.stories[i].storyId == currentStoryId) {
                  currentStoryIndex = i;
                  break;
                }
              }
              storyProgressView.setStoryCount(storyCount, currentStoryIndex);
            }
          }
        });
      }
    });
  }

  private void loadStory () {
    loadingContainer.setVisibility(View.VISIBLE);
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

    // Hide loading indicator
    loadingContainer.setVisibility(View.GONE);

    // Notify TDLib that we're viewing this story
    openStory();

    // Update heart button state
    updateHeartButtonState();

    // Update header
    TdApi.Chat chat = tdlib.chat(story.posterChatId);
    if (chat != null) {
      storyHeaderView.setChat(chat, story.date);
      // Set viewer count for own stories
      if (story.interactionInfo != null) {
        storyHeaderView.setViewerCount(story.interactionInfo.viewCount);
      }
    }

    // Display caption if present
    if (story.caption != null && story.caption.text != null && !story.caption.text.isEmpty()) {
      captionView.setText(story.caption.text);
      captionView.setVisibility(View.VISIBLE);
    } else {
      captionView.setVisibility(View.GONE);
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
        // Hide video texture view for photo stories
        if (videoTextureView != null) {
          videoTextureView.setVisibility(View.GONE);
        }
        releasePlayer();
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
    showExpiredError();
  }

  private void showExpiredError () {
    UI.showToast(R.string.StoryExpired, Toast.LENGTH_SHORT);
    // Auto-navigate to next story after a short delay
    UI.post(() -> {
      if (!isDestroyed()) {
        navigateNext();
      }
    }, 1500);
  }

  private void openStory () {
    if (currentStory != null) {
      // Send silently - don't show error toast if this fails
      tdlib.client().send(new TdApi.OpenStory(currentChatId, currentStoryId), result -> {
        // Silently ignore errors - this is just for marking as viewed
      });
    }
  }

  private void closeStory () {
    if (currentStory != null) {
      // Send silently - ignore errors (story might already be closed or not exist)
      tdlib.client().send(new TdApi.CloseStory(currentChatId, currentStoryId), result -> {
        // Silently ignore - this is just for cleanup
      });
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

    // Show video texture view and connect surface
    if (videoTextureView != null) {
      videoTextureView.setVisibility(View.VISIBLE);
      if (videoSurface != null) {
        exoPlayer.setVideoSurface(videoSurface);
      }
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

  // Update bottom insets for navigation bar
  private void updateBottomInsets () {
    int bottomMargin = Screen.dp(16f) + navigationBarHeight;

    // Update reply input margin
    if (storyReplyInputView != null) {
      FrameLayoutFix.LayoutParams replyParams = (FrameLayoutFix.LayoutParams) storyReplyInputView.getLayoutParams();
      replyParams.bottomMargin = bottomMargin;
      storyReplyInputView.setLayoutParams(replyParams);
    }

    // Update caption margin (above reply input)
    if (captionView != null) {
      FrameLayoutFix.LayoutParams captionParams = (FrameLayoutFix.LayoutParams) captionView.getLayoutParams();
      captionParams.bottomMargin = Screen.dp(80f) + navigationBarHeight;
      captionView.setLayoutParams(captionParams);
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
        // Go to next user's first story - fetch fresh data for that user
        closeStory();
        currentUserIndex++;
        activeStories = activeStoriesList.get(currentUserIndex);
        currentChatId = activeStories.chatId;
        currentStoryIndex = 0;
        currentStory = null;
        // Fetch fresh stories for this user before loading
        loadNextUserStories(activeStories.chatId);
        return;
      }
    }
    // No more stories, close viewer
    close();
  }

  private void loadNextUserStories (long chatId) {
    loadingContainer.setVisibility(View.VISIBLE);
    tdlib.client().send(new TdApi.GetChatActiveStories(chatId), result -> {
      if (result.getConstructor() == TdApi.ChatActiveStories.CONSTRUCTOR) {
        TdApi.ChatActiveStories freshStories = (TdApi.ChatActiveStories) result;
        UI.post(() -> {
          if (!isDestroyed() && freshStories.stories.length > 0) {
            // Update the list with fresh data
            if (currentUserIndex < activeStoriesList.size()) {
              activeStoriesList.set(currentUserIndex, freshStories);
            }
            currentStoryId = freshStories.stories[0].storyId;
            storyProgressView.setStoryCount(freshStories.stories.length, 0);
            loadStory();
          } else {
            // No stories available, try next user or close
            navigateNext();
          }
        });
      } else {
        UI.post(() -> {
          // Failed to get stories, try next user or close
          navigateNext();
        });
      }
    });
  }

  // Navigate to previous user (horizontal swipe right)
  private void navigateToPreviousUser () {
    if (currentUserIndex > 0 && activeStoriesList != null) {
      closeStory();
      currentUserIndex--;
      TdApi.ChatActiveStories activeStories = activeStoriesList.get(currentUserIndex);
      currentChatId = activeStories.chatId;
      currentStoryIndex = 0; // Start from first story of previous user
      currentStoryId = activeStories.stories[0].storyId;
      currentStory = null;
      loadStory();
    } else {
      // Already at first user, just resume
      resumeProgress();
    }
  }

  // Navigate to next user (horizontal swipe left)
  private void navigateToNextUser () {
    if (activeStoriesList != null && currentUserIndex < activeStoriesList.size() - 1) {
      closeStory();
      currentUserIndex++;
      TdApi.ChatActiveStories activeStories = activeStoriesList.get(currentUserIndex);
      currentChatId = activeStories.chatId;
      currentStoryIndex = 0; // Start from first story of next user
      currentStory = null;
      loadNextUserStories(activeStories.chatId);
    } else {
      // Already at last user, close viewer
      close();
    }
  }

  // Reactions
  private boolean hasHeartReaction () {
    if (currentStory == null || currentStory.chosenReactionType == null) {
      return false;
    }
    if (currentStory.chosenReactionType.getConstructor() == TdApi.ReactionTypeEmoji.CONSTRUCTOR) {
      return "❤".equals(((TdApi.ReactionTypeEmoji) currentStory.chosenReactionType).emoji);
    }
    return false;
  }

  private void toggleHeartReaction () {
    if (currentStory == null) return;

    boolean currentlyLiked = hasHeartReaction();

    if (currentlyLiked) {
      // Remove reaction
      tdlib.client().send(new TdApi.SetStoryReaction(currentChatId, currentStoryId, null, false), result -> {
        UI.post(() -> {
          if (result.getConstructor() == TdApi.Ok.CONSTRUCTOR) {
            // Update local state
            currentStory.chosenReactionType = null;
            updateHeartButtonState();
          }
        });
      });
    } else {
      // Add heart reaction
      TdApi.ReactionTypeEmoji heartReaction = new TdApi.ReactionTypeEmoji("❤");
      tdlib.client().send(new TdApi.SetStoryReaction(currentChatId, currentStoryId, heartReaction, false), result -> {
        UI.post(() -> {
          if (result.getConstructor() == TdApi.Ok.CONSTRUCTOR) {
            // Update local state
            currentStory.chosenReactionType = heartReaction;
            updateHeartButtonState();
          }
        });
      });
    }
  }

  private void sendHeartReaction () {
    toggleHeartReaction();
  }

  // Show reaction picker with common reactions
  private void showReactionPicker () {
    pauseProgress();

    // Common reaction emojis
    String[] reactions = {"❤", "🔥", "👍", "👎", "😂", "😢", "🎉", "😮"};
    String[] labels = {"❤️", "🔥", "👍", "👎", "😂", "😢", "🎉", "😮"};

    java.util.ArrayList<Integer> ids = new java.util.ArrayList<>();
    java.util.ArrayList<String> strings = new java.util.ArrayList<>();
    java.util.ArrayList<Integer> colors = new java.util.ArrayList<>();
    java.util.ArrayList<Integer> icons = new java.util.ArrayList<>();

    for (int i = 0; i < reactions.length; i++) {
      ids.add(i + 1); // Use index + 1 as ID
      strings.add(labels[i]);
      colors.add(OptionColor.NORMAL);
      icons.add(0); // No icon, emoji in text
    }

    // Add remove reaction option if there's a current reaction
    if (currentStory != null && currentStory.chosenReactionType != null) {
      ids.add(R.id.btn_delete);
      strings.add(Lang.getString(R.string.RemoveReaction));
      colors.add(OptionColor.RED);
      icons.add(R.drawable.baseline_delete_24);
    }

    // Cancel option
    ids.add(R.id.btn_cancel);
    strings.add(Lang.getString(R.string.Cancel));
    colors.add(OptionColor.NORMAL);
    icons.add(R.drawable.baseline_cancel_24);

    showOptions(
      null,
      ids.stream().mapToInt(i -> i).toArray(),
      strings.toArray(new String[0]),
      colors.stream().mapToInt(i -> i).toArray(),
      icons.stream().mapToInt(i -> i).toArray(),
      (itemView, id) -> {
        if (id == R.id.btn_cancel) {
          resumeProgress();
        } else if (id == R.id.btn_delete) {
          // Remove reaction
          setStoryReaction(null);
        } else if (id >= 1 && id <= reactions.length) {
          // Set selected reaction
          String emoji = reactions[id - 1];
          setStoryReaction(new TdApi.ReactionTypeEmoji(emoji));
        }
        return true;
      }
    );
  }

  private void setStoryReaction (@Nullable TdApi.ReactionType reactionType) {
    if (currentStory == null) {
      resumeProgress();
      return;
    }

    tdlib.client().send(new TdApi.SetStoryReaction(currentChatId, currentStoryId, reactionType, false), result -> {
      runOnUiThreadOptional(() -> {
        if (result.getConstructor() == TdApi.Ok.CONSTRUCTOR) {
          // Update local state
          currentStory.chosenReactionType = reactionType;
          updateHeartButtonState();
        } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          TdApi.Error error = (TdApi.Error) result;
          UI.showToast("Error: " + error.message, Toast.LENGTH_SHORT);
        }
        resumeProgress();
      });
    });
  }

  private void updateHeartButtonState () {
    if (storyReplyInputView != null) {
      storyReplyInputView.updateHeartState(hasHeartReaction());
    }
  }

  private void sendReply (String text) {
    if (currentStory == null || text == null || text.trim().isEmpty()) return;

    String trimmedText = text.trim();
    TdApi.InputMessageReplyToStory replyTo = new TdApi.InputMessageReplyToStory(currentChatId, currentStoryId);
    TdApi.InputMessageText content = new TdApi.InputMessageText(
      new TdApi.FormattedText(trimmedText, null),
      null,
      false
    );

    // Get chat name for feedback message
    TdApi.Chat chat = tdlib.chat(currentChatId);
    String chatName = chat != null ? chat.title : null;

    tdlib.client().send(new TdApi.SendMessage(
      currentChatId,
      null,
      replyTo,
      null,
      null,
      content
    ), result -> {
      UI.post(() -> {
        if (result.getConstructor() == TdApi.Message.CONSTRUCTOR) {
          // Show more informative toast with recipient name
          String toastMessage = chatName != null
            ? Lang.getString(R.string.StoryReplySentTo, chatName)
            : Lang.getString(R.string.StoryReplySent);
          UI.showToast(toastMessage, Toast.LENGTH_SHORT);
        } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          TdApi.Error error = (TdApi.Error) result;
          UI.showToast("Error: " + error.message, Toast.LENGTH_SHORT);
        }
      });
    });

    if (storyReplyInputView != null) {
      storyReplyInputView.clearInput();
    }
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
    setRootView(null);
  }

  // RootFrameLayout.InsetsChangeListener - register/unregister with RootFrameLayout
  private void setRootView (RootFrameLayout rootView) {
    if (this.rootView != rootView) {
      if (this.rootView != null) {
        this.rootView.removeInsetsChangeListener(this);
      }
      this.rootView = rootView;
      if (rootView != null) {
        rootView.addInsetsChangeListener(this);
        updateTopInsetOffset(rootView.getSystemInsets().top);
      }
    }
  }

  @Override
  public void onInsetsChanged (RootFrameLayout viewGroup, Rect effectiveInsets,
      Rect effectiveInsetsWithoutIme, Rect systemInsets,
      Rect systemInsetsWithoutIme, boolean isUpdate) {
    updateTopInsetOffset(systemInsets.top);
  }

  private void updateTopInsetOffset (int newOffset) {
    if (topInsetOffset != newOffset) {
      topInsetOffset = newOffset;

      // Update all views that need status bar offset
      if (storyProgressView != null) {
        FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) storyProgressView.getLayoutParams();
        params.topMargin = Screen.dp(16f) + topInsetOffset;
        storyProgressView.setLayoutParams(params);
      }

      if (storyHeaderView != null) {
        FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) storyHeaderView.getLayoutParams();
        params.topMargin = Screen.dp(24f) + topInsetOffset;
        storyHeaderView.setLayoutParams(params);
      }

      if (closeButton != null) {
        FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) closeButton.getLayoutParams();
        params.topMargin = Screen.dp(24f) + topInsetOffset;
        closeButton.setLayoutParams(params);
      }

      if (moreButton != null) {
        FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) moreButton.getLayoutParams();
        params.topMargin = Screen.dp(24f) + topInsetOffset;
        moreButton.setLayoutParams(params);
      }
    }
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
    private TextView viewersButton;
    private boolean isOwnStory;

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

      // Viewers button (only for own stories)
      viewersButton = new TextView(context);
      viewersButton.setTextColor(Color.WHITE);
      viewersButton.setTextSize(13f);
      viewersButton.setSingleLine();
      viewersButton.setCompoundDrawablePadding(Screen.dp(4f));
      viewersButton.setVisibility(GONE);
      viewersButton.setOnClickListener(v -> openViewers());
      LayoutParams viewersParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
      viewersParams.gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
      viewersParams.rightMargin = Screen.dp(56f); // Leave space for close button
      addView(viewersButton, viewersParams);
    }

    public void setChat (TdApi.Chat chat, int date) {
      avatarView.setChat(tdlib, chat);
      nameView.setText(chat.title);
      timeView.setText(Lang.timeOrDateShort(date, java.util.concurrent.TimeUnit.SECONDS));

      // Check if this is own story
      isOwnStory = chat.id == tdlib.selfChatId();
      viewersButton.setVisibility(isOwnStory ? VISIBLE : GONE);
    }

    public void setViewerCount (int count) {
      if (isOwnStory && count >= 0) {
        viewersButton.setText(Lang.getString(R.string.StoryViews, count));
        viewersButton.setVisibility(VISIBLE);
      }
    }

    private void openViewers () {
      if (currentStoryId <= 0) return;
      pauseProgress();
      
      tdlib.client().send(new TdApi.GetStoryInteractions(currentStoryId, null, false, false, false, "", 50), result -> {
        UI.post(() -> {
          if (result.getConstructor() == TdApi.StoryInteractions.CONSTRUCTOR) {
            TdApi.StoryInteractions interactions = (TdApi.StoryInteractions) result;
            if (interactions.interactions.length > 0) {
              StringBuilder viewers = new StringBuilder();
              viewers.append("Story Viewers (").append(interactions.totalCount).append("):\n");
              for (int i = 0; i < Math.min(interactions.interactions.length, 10); i++) {
                TdApi.StoryInteraction interaction = interactions.interactions[i];
                TdApi.Chat viewerChat = tdlib.chat(Td.getSenderId(interaction.actorId));
                if (viewerChat != null) {
                  viewers.append("• ").append(viewerChat.title).append("\n");
                }
              }
              if (interactions.interactions.length > 10) {
                viewers.append("... and ").append(interactions.interactions.length - 10).append(" more");
              }
              UI.showToast(viewers.toString(), Toast.LENGTH_LONG);
            } else {
              UI.showToast("No viewers yet", Toast.LENGTH_SHORT);
            }
          } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
            TdApi.Error error = (TdApi.Error) result;
            UI.showToast("Error: " + error.message, Toast.LENGTH_SHORT);
          }
          resumeProgress();
        });
      });
    }
  }

  // Story options menu
  private void showStoryOptions () {
    pauseProgress();

    if (currentStory == null) {
      resumeProgress();
      return;
    }

    // Determine capabilities based on story flags
    boolean canEdit = currentStory.canBeEdited;
    boolean canDelete = currentStory.canBeDeleted;
    boolean canShare = currentStory.canBeForwarded;
    boolean canToggleProfile = currentStory.canToggleIsPostedToChatPage;
    boolean canGetStats = currentStory.canGetStatistics;
    boolean isOwnStory = currentChatId == tdlib.myUserId();

    java.util.ArrayList<Integer> ids = new java.util.ArrayList<>();
    java.util.ArrayList<String> strings = new java.util.ArrayList<>();
    java.util.ArrayList<Integer> colors = new java.util.ArrayList<>();
    java.util.ArrayList<Integer> icons = new java.util.ArrayList<>();

    // Share option (if story can be forwarded)
    if (canShare) {
      ids.add(R.id.btn_share);
      strings.add(Lang.getString(R.string.Share));
      colors.add(OptionColor.NORMAL);
      icons.add(R.drawable.baseline_share_24);
    }

    // Edit caption option (if editable)
    if (canEdit) {
      ids.add(R.id.btn_editStoryCaption);
      strings.add(Lang.getString(R.string.EditStoryCaption));
      colors.add(OptionColor.NORMAL);
      icons.add(R.drawable.baseline_edit_24);

      ids.add(R.id.btn_storyPrivacy);
      strings.add(Lang.getString(R.string.StoryPrivacy));
      colors.add(OptionColor.NORMAL);
      icons.add(R.drawable.baseline_lock_24);
    }

    // Statistics option (if available)
    if (canGetStats) {
      ids.add(R.id.btn_storyStatistics);
      strings.add(Lang.getString(R.string.StoryStatistics));
      colors.add(OptionColor.NORMAL);
      icons.add(R.drawable.baseline_bar_chart_24);
    }

    // Add to album option (only for own stories)
    if (isOwnStory) {
      ids.add(R.id.btn_addToAlbum);
      strings.add(Lang.getString(R.string.AddToAlbum));
      colors.add(OptionColor.NORMAL);
      icons.add(R.drawable.baseline_bookmark_24);
    }

    // Pin to profile option
    if (canToggleProfile) {
      ids.add(R.id.btn_saveToProfile);
      strings.add(Lang.getString(currentStory.isPostedToChatPage ? R.string.RemoveFromProfile : R.string.SaveToProfile));
      colors.add(OptionColor.NORMAL);
      icons.add(R.drawable.deproko_baseline_pin_24);
    }

    // Stealth mode option (for other's stories)
    if (!isOwnStory) {
      ids.add(R.id.btn_stealthMode);
      strings.add(Lang.getString(R.string.StealthMode));
      colors.add(OptionColor.NORMAL);
      icons.add(R.drawable.infanf_baseline_incognito_24);
    }

    // Report option (for other's stories)
    if (!isOwnStory) {
      ids.add(R.id.btn_reportStory);
      strings.add(Lang.getString(R.string.ReportStory));
      colors.add(OptionColor.RED);
      icons.add(R.drawable.baseline_report_24);
    }

    // Delete option (if story can be deleted - works for both own stories and channel admin)
    if (canDelete) {
      ids.add(R.id.btn_deleteStory);
      strings.add(Lang.getString(R.string.DeleteStory));
      colors.add(OptionColor.RED);
      icons.add(R.drawable.baseline_delete_24);
    }

    // Cancel option
    ids.add(R.id.btn_cancel);
    strings.add(Lang.getString(R.string.Cancel));
    colors.add(OptionColor.NORMAL);
    icons.add(R.drawable.baseline_cancel_24);

    showOptions(
      null,
      ids.stream().mapToInt(i -> i).toArray(),
      strings.toArray(new String[0]),
      colors.stream().mapToInt(i -> i).toArray(),
      icons.stream().mapToInt(i -> i).toArray(),
      (itemView, id) -> {
        if (id == R.id.btn_share) {
          shareStory();
        } else if (id == R.id.btn_deleteStory) {
          confirmDeleteStory();
        } else if (id == R.id.btn_editStoryCaption) {
          showEditCaptionDialog();
        } else if (id == R.id.btn_storyPrivacy) {
          showPrivacyOptions();
        } else if (id == R.id.btn_saveToProfile) {
          toggleSaveToProfile();
        } else if (id == R.id.btn_storyStatistics) {
          showStoryStatistics();
        } else if (id == R.id.btn_addToAlbum) {
          showAlbumPicker();
        } else if (id == R.id.btn_reportStory) {
          reportStory();
        } else if (id == R.id.btn_stealthMode) {
          activateStealthMode();
        } else {
          resumeProgress();
        }
        return true;
      }
    );
  }

  private void confirmDeleteStory () {
    showOptions(
      Lang.getString(R.string.DeleteStoryConfirm),
      new int[] { R.id.btn_deleteStory, R.id.btn_cancel },
      new String[] { Lang.getString(R.string.DeleteStory), Lang.getString(R.string.Cancel) },
      new int[] { OptionColor.RED, OptionColor.NORMAL },
      new int[] { R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24 },
      (itemView, id) -> {
        if (id == R.id.btn_deleteStory) {
          deleteStory();
        }
        return true;
      }
    );
  }

  private void deleteStory () {
    if (currentStory == null) return;

    tdlib.client().send(new TdApi.DeleteStory(currentChatId, currentStoryId), result -> {
      runOnUiThreadOptional(() -> {
        if (result.getConstructor() == TdApi.Ok.CONSTRUCTOR) {
          UI.showToast(R.string.StoryDeleted, Toast.LENGTH_SHORT);
          close();
        } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          TdApi.Error error = (TdApi.Error) result;
          UI.showToast("Error: " + error.message, Toast.LENGTH_SHORT);
        }
      });
    });
  }

  private void reportStory () {
    if (currentStory == null) return;

    // Use empty optionId for initial report request - server will return available options
    tdlib.client().send(new TdApi.ReportStory(currentChatId, currentStoryId, new byte[0], ""), result -> {
      runOnUiThreadOptional(() -> {
        if (result.getConstructor() == TdApi.ReportStoryResultOk.CONSTRUCTOR) {
          UI.showToast(R.string.StoryReported, Toast.LENGTH_SHORT);
        } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          TdApi.Error error = (TdApi.Error) result;
          UI.showToast("Error: " + error.message, Toast.LENGTH_SHORT);
        }
      });
    });
  }

  private void shareStory () {
    if (currentStory == null || !currentStory.canBeForwarded) {
      resumeProgress();
      return;
    }

    // Store story info before closing
    final long chatId = currentChatId;
    final int storyId = currentStoryId;

    // Close the story viewer first, then show share dialog
    close();

    // Create InputMessageStory to share as a forward
    TdApi.InputMessageStory content = new TdApi.InputMessageStory(chatId, storyId);

    // Use post to ensure story viewer is fully closed before opening share
    UI.post(() -> {
      ShareController shareController = new ShareController(context(), tdlib);
      shareController.setArguments(new ShareController.Args(content));
      shareController.show();
    });
  }

  private void activateStealthMode () {
    // Check if user has premium
    tdlib.client().send(new TdApi.ActivateStoryStealthMode(), result -> {
      runOnUiThreadOptional(() -> {
        if (result.getConstructor() == TdApi.Ok.CONSTRUCTOR) {
          UI.showToast(R.string.StealthModeActivated, Toast.LENGTH_SHORT);
        } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          TdApi.Error error = (TdApi.Error) result;
          if (error.message.contains("PREMIUM")) {
            UI.showToast(R.string.StealthModePremiumRequired, Toast.LENGTH_SHORT);
          } else if (error.message.contains("Too Many Requests") || error.message.contains("FLOOD_WAIT")) {
            // Parse rate limit duration from error message
            int waitSeconds = parseRateLimitSeconds(error.message);
            if (waitSeconds > 0) {
              int waitMinutes = (waitSeconds + 59) / 60; // Round up to minutes
              UI.showToast(Lang.getString(R.string.StealthModeWait, waitMinutes), Toast.LENGTH_LONG);
            } else {
              UI.showToast(R.string.StealthModeAlreadyActive, Toast.LENGTH_SHORT);
            }
          } else {
            UI.showToast("Error: " + error.message, Toast.LENGTH_SHORT);
          }
        }
      });
    });
  }

  private int parseRateLimitSeconds (String message) {
    // Parse "retry after X" or "FLOOD_WAIT_X" format
    try {
      String[] parts = message.split("\\s+");
      for (int i = 0; i < parts.length; i++) {
        if (parts[i].equalsIgnoreCase("after") && i + 1 < parts.length) {
          return Integer.parseInt(parts[i + 1]);
        }
        if (parts[i].startsWith("FLOOD_WAIT_")) {
          return Integer.parseInt(parts[i].substring("FLOOD_WAIT_".length()));
        }
      }
      // Also try just extracting any number
      for (String part : parts) {
        try {
          int num = Integer.parseInt(part);
          if (num > 0 && num < 86400) { // Reasonable wait time (< 1 day)
            return num;
          }
        } catch (NumberFormatException ignored) {}
      }
    } catch (Exception e) {
      // Parsing failed
    }
    return 0;
  }

  private void showEditCaptionDialog () {
    if (currentStory == null) {
      resumeProgress();
      return;
    }

    String currentCaption = currentStory.caption != null ? currentStory.caption.text : "";

    android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context());
    builder.setTitle(Lang.getString(R.string.EditStoryCaption));

    final android.widget.EditText input = new android.widget.EditText(context());
    input.setText(currentCaption);
    input.setSelection(currentCaption.length());
    int padding = Screen.dp(16f);
    android.widget.FrameLayout container = new android.widget.FrameLayout(context());
    android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
      android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
      android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
    );
    params.leftMargin = padding;
    params.rightMargin = padding;
    input.setLayoutParams(params);
    container.addView(input);
    builder.setView(container);

    builder.setPositiveButton(Lang.getString(R.string.Save), (dialog, which) -> {
      String newCaption = input.getText().toString();
      editStoryCaption(newCaption);
    });
    builder.setNegativeButton(Lang.getString(R.string.Cancel), (dialog, which) -> {
      resumeProgress();
    });
    builder.setOnCancelListener(dialog -> resumeProgress());

    builder.show();
  }

  private void editStoryCaption (String newCaption) {
    if (currentStory == null) {
      resumeProgress();
      return;
    }

    TdApi.FormattedText caption = new TdApi.FormattedText(newCaption, new TdApi.TextEntity[0]);
    tdlib.client().send(new TdApi.EditStory(currentChatId, currentStoryId, null, null, caption), result -> {
      runOnUiThreadOptional(() -> {
        if (result.getConstructor() == TdApi.Ok.CONSTRUCTOR) {
          UI.showToast(R.string.StoryCaptionUpdated, Toast.LENGTH_SHORT);
          // Update local caption display
          if (captionView != null && currentStory != null) {
            currentStory.caption = caption;
            captionView.setText(newCaption);
            captionView.setVisibility(newCaption.isEmpty() ? View.GONE : View.VISIBLE);
          }
        } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          TdApi.Error error = (TdApi.Error) result;
          UI.showToast("Error: " + error.message, Toast.LENGTH_SHORT);
        }
        resumeProgress();
      });
    });
  }

  private void showPrivacyOptions () {
    if (currentStory == null) {
      resumeProgress();
      return;
    }

    showOptions(
      Lang.getString(R.string.StoryPrivacy),
      new int[] { R.id.btn_privacyEveryone, R.id.btn_privacyContacts, R.id.btn_privacyCloseFriends, R.id.btn_cancel },
      new String[] {
        Lang.getString(R.string.StoryPrivacyEveryone),
        Lang.getString(R.string.StoryPrivacyContacts),
        Lang.getString(R.string.StoryPrivacyCloseFriends),
        Lang.getString(R.string.Cancel)
      },
      new int[] { OptionColor.NORMAL, OptionColor.NORMAL, OptionColor.NORMAL, OptionColor.NORMAL },
      new int[] { R.drawable.baseline_public_24, R.drawable.baseline_group_24, R.drawable.baseline_star_24, R.drawable.baseline_cancel_24 },
      (itemView, id) -> {
        if (id == R.id.btn_privacyEveryone) {
          setStoryPrivacy(new TdApi.StoryPrivacySettingsEveryone(new long[0]));
        } else if (id == R.id.btn_privacyContacts) {
          setStoryPrivacy(new TdApi.StoryPrivacySettingsContacts(new long[0]));
        } else if (id == R.id.btn_privacyCloseFriends) {
          setStoryPrivacy(new TdApi.StoryPrivacySettingsCloseFriends());
        } else {
          resumeProgress();
        }
        return true;
      }
    );
  }

  private void setStoryPrivacy (TdApi.StoryPrivacySettings privacySettings) {
    if (currentStory == null) {
      resumeProgress();
      return;
    }

    tdlib.client().send(new TdApi.SetStoryPrivacySettings(currentStoryId, privacySettings), result -> {
      runOnUiThreadOptional(() -> {
        if (result.getConstructor() == TdApi.Ok.CONSTRUCTOR) {
          UI.showToast(R.string.StoryPrivacyUpdated, Toast.LENGTH_SHORT);
        } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          TdApi.Error error = (TdApi.Error) result;
          UI.showToast("Error: " + error.message, Toast.LENGTH_SHORT);
        }
        resumeProgress();
      });
    });
  }

  private void toggleSaveToProfile () {
    if (currentStory == null) {
      resumeProgress();
      return;
    }

    boolean newState = !currentStory.isPostedToChatPage;
    tdlib.client().send(new TdApi.ToggleStoryIsPostedToChatPage(currentChatId, currentStoryId, newState), result -> {
      runOnUiThreadOptional(() -> {
        if (result.getConstructor() == TdApi.Ok.CONSTRUCTOR) {
          currentStory.isPostedToChatPage = newState;
          UI.showToast(newState ? R.string.StorySavedToProfile : R.string.StoryRemovedFromProfile, Toast.LENGTH_SHORT);
        } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          TdApi.Error error = (TdApi.Error) result;
          UI.showToast("Error: " + error.message, Toast.LENGTH_SHORT);
        }
        resumeProgress();
      });
    });
  }

  // Story Statistics
  private void showStoryStatistics () {
    if (currentStory == null || !currentStory.canGetStatistics) {
      resumeProgress();
      return;
    }

    boolean isDark = Theme.isDark();
    tdlib.client().send(new TdApi.GetStoryStatistics(currentChatId, currentStoryId, isDark), result -> {
      runOnUiThreadOptional(() -> {
        if (result.getConstructor() == TdApi.StoryStatistics.CONSTRUCTOR) {
          TdApi.StoryStatistics stats = (TdApi.StoryStatistics) result;
          showStatisticsDialog(stats);
        } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          TdApi.Error error = (TdApi.Error) result;
          if (error.message.contains("PREMIUM")) {
            UI.showToast(R.string.StoryStatisticsPremiumRequired, Toast.LENGTH_SHORT);
          } else {
            UI.showToast("Error: " + error.message, Toast.LENGTH_SHORT);
          }
          resumeProgress();
        }
      });
    });
  }

  private void showStatisticsDialog (TdApi.StoryStatistics stats) {
    StringBuilder message = new StringBuilder();
    message.append(Lang.getString(R.string.StoryStatisticsTitle)).append("\n\n");

    // Story interaction graph info
    if (stats.storyInteractionGraph != null) {
      message.append("📊 ").append(Lang.getString(R.string.StoryInteractions)).append("\n");
      appendGraphInfo(message, stats.storyInteractionGraph);
      message.append("\n");
    }

    // Story reaction graph info
    if (stats.storyReactionGraph != null) {
      message.append("❤️ ").append(Lang.getString(R.string.StoryReactions)).append("\n");
      appendGraphInfo(message, stats.storyReactionGraph);
    }

    if (stats.storyInteractionGraph == null && stats.storyReactionGraph == null) {
      message.append(Lang.getString(R.string.StoryNoStatistics));
    }

    android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context());
    builder.setTitle(Lang.getString(R.string.StoryStatistics));
    builder.setMessage(message.toString());
    builder.setPositiveButton(Lang.getString(R.string.OK), (dialog, which) -> resumeProgress());
    builder.setOnCancelListener(dialog -> resumeProgress());
    builder.show();
  }

  private void appendGraphInfo (StringBuilder sb, TdApi.StatisticalGraph graph) {
    switch (graph.getConstructor()) {
      case TdApi.StatisticalGraphData.CONSTRUCTOR: {
        TdApi.StatisticalGraphData data = (TdApi.StatisticalGraphData) graph;
        sb.append("Data available: ").append(data.jsonData.length()).append(" bytes\n");
        break;
      }
      case TdApi.StatisticalGraphAsync.CONSTRUCTOR: {
        sb.append("Loading...\n");
        break;
      }
      case TdApi.StatisticalGraphError.CONSTRUCTOR: {
        TdApi.StatisticalGraphError error = (TdApi.StatisticalGraphError) graph;
        sb.append("Error: ").append(error.errorMessage).append("\n");
        break;
      }
    }
  }

  // Story Albums (Highlights)
  private void showAlbumPicker () {
    if (currentStory == null) {
      resumeProgress();
      return;
    }

    // Fetch existing albums
    tdlib.client().send(new TdApi.GetChatStoryAlbums(currentChatId), result -> {
      runOnUiThreadOptional(() -> {
        if (result.getConstructor() == TdApi.StoryAlbums.CONSTRUCTOR) {
          TdApi.StoryAlbums albums = (TdApi.StoryAlbums) result;
          showAlbumPickerDialog(albums.albums);
        } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          TdApi.Error error = (TdApi.Error) result;
          UI.showToast("Error: " + error.message, Toast.LENGTH_SHORT);
          resumeProgress();
        }
      });
    });
  }

  private void showAlbumPickerDialog (TdApi.StoryAlbum[] albums) {
    java.util.ArrayList<Integer> ids = new java.util.ArrayList<>();
    java.util.ArrayList<String> strings = new java.util.ArrayList<>();
    java.util.ArrayList<Integer> colors = new java.util.ArrayList<>();
    java.util.ArrayList<Integer> icons = new java.util.ArrayList<>();

    // Add "Create New Album" option first
    ids.add(R.id.btn_createAlbum);
    strings.add(Lang.getString(R.string.CreateNewAlbum));
    colors.add(OptionColor.BLUE);
    icons.add(R.drawable.baseline_add_24);

    // Add existing albums
    for (TdApi.StoryAlbum album : albums) {
      ids.add(album.id);
      strings.add(album.name);
      colors.add(OptionColor.NORMAL);
      icons.add(R.drawable.baseline_bookmark_24);
    }

    // Cancel option
    ids.add(R.id.btn_cancel);
    strings.add(Lang.getString(R.string.Cancel));
    colors.add(OptionColor.NORMAL);
    icons.add(R.drawable.baseline_cancel_24);

    showOptions(
      Lang.getString(R.string.AddToAlbum),
      ids.stream().mapToInt(i -> i).toArray(),
      strings.toArray(new String[0]),
      colors.stream().mapToInt(i -> i).toArray(),
      icons.stream().mapToInt(i -> i).toArray(),
      (itemView, id) -> {
        if (id == R.id.btn_createAlbum) {
          showCreateAlbumDialog();
        } else if (id != R.id.btn_cancel) {
          addToAlbum(id);
        } else {
          resumeProgress();
        }
        return true;
      }
    );
  }

  private void showCreateAlbumDialog () {
    android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context());
    builder.setTitle(Lang.getString(R.string.CreateNewAlbum));

    final android.widget.EditText input = new android.widget.EditText(context());
    input.setHint(Lang.getString(R.string.AlbumName));
    int padding = Screen.dp(16f);
    android.widget.FrameLayout container = new android.widget.FrameLayout(context());
    android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
      android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
      android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
    );
    params.leftMargin = padding;
    params.rightMargin = padding;
    input.setLayoutParams(params);
    container.addView(input);
    builder.setView(container);

    builder.setPositiveButton(Lang.getString(R.string.Create), (dialog, which) -> {
      String name = input.getText().toString().trim();
      if (!name.isEmpty()) {
        createNewAlbum(name);
      } else {
        resumeProgress();
      }
    });
    builder.setNegativeButton(Lang.getString(R.string.Cancel), (dialog, which) -> resumeProgress());
    builder.setOnCancelListener(dialog -> resumeProgress());

    builder.show();
  }

  private void createNewAlbum (String name) {
    if (currentStory == null) {
      resumeProgress();
      return;
    }

    int[] storyIds = new int[] { currentStoryId };
    tdlib.client().send(new TdApi.CreateStoryAlbum(currentChatId, name, storyIds), result -> {
      runOnUiThreadOptional(() -> {
        if (result.getConstructor() == TdApi.StoryAlbum.CONSTRUCTOR) {
          UI.showToast(R.string.AlbumCreated, Toast.LENGTH_SHORT);
        } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          TdApi.Error error = (TdApi.Error) result;
          UI.showToast("Error: " + error.message, Toast.LENGTH_SHORT);
        }
        resumeProgress();
      });
    });
  }

  private void addToAlbum (int albumId) {
    if (currentStory == null) {
      resumeProgress();
      return;
    }

    int[] storyIds = new int[] { currentStoryId };
    tdlib.client().send(new TdApi.AddStoryAlbumStories(currentChatId, albumId, storyIds), result -> {
      runOnUiThreadOptional(() -> {
        if (result.getConstructor() == TdApi.StoryAlbum.CONSTRUCTOR) {
          UI.showToast(R.string.StoryAddedToAlbum, Toast.LENGTH_SHORT);
        } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          TdApi.Error error = (TdApi.Error) result;
          UI.showToast("Error: " + error.message, Toast.LENGTH_SHORT);
        }
        resumeProgress();
      });
    });
  }

  private class StoryReplyInputView extends FrameLayout {
    private EditText editText;
    private ImageView sendButton;
    private ImageView heartButton;
    private LinearLayout quickReactionsRow;
    private boolean isLiked = false;
    private boolean quickReactionsVisible = false;

    private static final String[] QUICK_REACTIONS = {"❤", "🔥", "👍", "👎", "😂", "😢", "🎉"};

    public StoryReplyInputView (Context context) {
      super(context);

      // Semi-transparent background
      setBackgroundColor(ColorUtils.alphaColor(0.5f, Color.BLACK));
      setPadding(Screen.dp(12f), Screen.dp(8f), Screen.dp(8f), Screen.dp(8f));

      // Heart reaction button on left (starts as outline)
      heartButton = new ImageView(context);
      heartButton.setImageResource(R.drawable.baseline_favorite_border_24);
      heartButton.setColorFilter(Color.WHITE);
      heartButton.setOnClickListener(v -> handleReactionClick());
      heartButton.setPadding(Screen.dp(8f), Screen.dp(8f), Screen.dp(8f), Screen.dp(8f));
      LayoutParams heartParams = new LayoutParams(Screen.dp(40f), Screen.dp(40f));
      heartParams.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
      addView(heartButton, heartParams);

      // Send button on right
      sendButton = new ImageView(context);
      sendButton.setImageResource(R.drawable.baseline_send_24);
      sendButton.setColorFilter(Color.WHITE);
      sendButton.setOnClickListener(v -> {
        String text = editText.getText().toString();
        if (!text.trim().isEmpty()) {
          sendReply(text);
        }
      });
      sendButton.setPadding(Screen.dp(8f), Screen.dp(8f), Screen.dp(8f), Screen.dp(8f));
      LayoutParams sendParams = new LayoutParams(Screen.dp(40f), Screen.dp(40f));
      sendParams.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
      addView(sendButton, sendParams);

      // EditText in center
      editText = new EditText(context);
      editText.setHint(R.string.ReplyToStory);
      editText.setHintTextColor(ColorUtils.alphaColor(0.5f, Color.WHITE));
      editText.setTextColor(Color.WHITE);
      editText.setTextSize(14f);
      editText.setBackgroundColor(Color.TRANSPARENT);
      editText.setSingleLine(true);
      editText.setImeOptions(EditorInfo.IME_ACTION_SEND);
      editText.setOnEditorActionListener((v, actionId, event) -> {
        if (actionId == EditorInfo.IME_ACTION_SEND) {
          String text = editText.getText().toString();
          if (!text.trim().isEmpty()) {
            sendReply(text);
          }
          return true;
        }
        return false;
      });
      editText.setOnFocusChangeListener((v, hasFocus) -> {
        if (hasFocus) {
          pauseProgress();
        } else {
          resumeProgress();
        }
      });
      LayoutParams editParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
      editParams.leftMargin = Screen.dp(48f);
      editParams.rightMargin = Screen.dp(48f);
      editParams.gravity = Gravity.CENTER_VERTICAL;
      addView(editText, editParams);

      // Quick reactions row (initially hidden)
      quickReactionsRow = new LinearLayout(context);
      quickReactionsRow.setOrientation(LinearLayout.HORIZONTAL);
      quickReactionsRow.setGravity(Gravity.CENTER);
      quickReactionsRow.setBackgroundColor(ColorUtils.alphaColor(0.8f, Color.BLACK));
      quickReactionsRow.setPadding(Screen.dp(8f), Screen.dp(4f), Screen.dp(8f), Screen.dp(4f));
      quickReactionsRow.setVisibility(View.GONE);

      for (String emoji : QUICK_REACTIONS) {
        TextView emojiView = new TextView(context);
        emojiView.setText(emoji);
        emojiView.setTextSize(24f);
        emojiView.setPadding(Screen.dp(8f), Screen.dp(4f), Screen.dp(8f), Screen.dp(4f));
        emojiView.setOnClickListener(v -> {
          setStoryReaction(new TdApi.ReactionTypeEmoji(emoji));
          hideQuickReactions();
        });
        quickReactionsRow.addView(emojiView);
      }

      // Add remove reaction button if there's a reaction
      TextView removeView = new TextView(context);
      removeView.setText("✕");
      removeView.setTextSize(20f);
      removeView.setTextColor(0xFFFF6666);
      removeView.setPadding(Screen.dp(12f), Screen.dp(4f), Screen.dp(8f), Screen.dp(4f));
      removeView.setOnClickListener(v -> {
        setStoryReaction(null);
        hideQuickReactions();
      });
      quickReactionsRow.addView(removeView);

      LayoutParams quickParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
      quickParams.gravity = Gravity.CENTER;
      addView(quickReactionsRow, quickParams);
    }

    private void handleReactionClick () {
      if (Settings.instance().useStoryQuickReactions()) {
        toggleQuickReactions();
      } else {
        showReactionPicker();
      }
    }

    private void toggleQuickReactions () {
      if (quickReactionsVisible) {
        hideQuickReactions();
      } else {
        showQuickReactions();
      }
    }

    private void showQuickReactions () {
      quickReactionsVisible = true;
      quickReactionsRow.setVisibility(View.VISIBLE);
      editText.setVisibility(View.INVISIBLE);
      sendButton.setVisibility(View.INVISIBLE);
      pauseProgress();
    }

    private void hideQuickReactions () {
      quickReactionsVisible = false;
      quickReactionsRow.setVisibility(View.GONE);
      editText.setVisibility(View.VISIBLE);
      sendButton.setVisibility(View.VISIBLE);
      resumeProgress();
    }

    public void clearInput () {
      editText.setText("");
      editText.clearFocus();
    }

    public void updateHeartState (boolean liked) {
      this.isLiked = liked;
      if (liked) {
        heartButton.setImageResource(R.drawable.baseline_favorite_24);
        heartButton.setColorFilter(0xFFFF4444); // Red color for liked
      } else {
        heartButton.setImageResource(R.drawable.baseline_favorite_border_24);
        heartButton.setColorFilter(Color.WHITE);
      }
    }
  }
}
