package org.thunderdog.challegram.mediaview.disposable;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.widget.PopupLayout;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;

abstract class DisposableMediaViewController extends ViewController<DisposableMediaViewController.Args> implements
  PopupLayout.AnimatedPopupProvider, FactorAnimator.Target, Player.Listener, PopupLayout.TouchSectionProvider {

  public DisposableMediaViewController (@NonNull Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private PopupLayout popupView;
  private ExoPlayer exoPlayer;
  protected FrameLayoutFix contentView;

  @Override
  protected final View onCreateView (Context context) {
    popupView = new PopupLayout(context);
    popupView.setOverlayStatusBar(true);
    popupView.setTouchProvider(this);
    popupView.setNeedRootInsets();
    popupView.init(true);
    popupView.setIgnoreAllInsets(true);
    popupView.setBoundController(this);
    popupView.setDisableCancelOnTouchDown(true);

    contentView = onCreateContentView(context);

    exoPlayer = U.newExoPlayer(context, true);
    TdlibManager.instance().player().proximityManager().modifyExoPlayer(exoPlayer, C.AUDIO_CONTENT_TYPE_MOVIE);
    exoPlayer.addListener(this);
    exoPlayer.setVolume(1f);
    exoPlayer.setPlayWhenReady(false);
    exoPlayer.setMediaSource(U.newMediaSource(contentFile));
    onExoPlayerCreated(exoPlayer);
    exoPlayer.prepare();

    return contentView;
  }

  @Override
  public boolean shouldTouchOutside (float x, float y) {
    return false;
  }

  @Override
  public void onPlaybackStateChanged (@Player.State int playbackState) {
    if (playbackState == Player.STATE_ENDED) {
      hide();
    }
  }

  abstract protected FrameLayoutFix onCreateContentView (Context context);

  abstract protected void onExoPlayerCreated (ExoPlayer exoPlayer);

  public final void open () {
    getValue();
    popupView.showAnimatedPopupView(contentView, this);
  }

  public final void hide () {
    popupView.hideWindow(true);
  }

  public final long getExoPlayerDuration () {
    return exoPlayer != null ? exoPlayer.getDuration() : C.TIME_UNSET;
  }

  public final long getExoPlayerCurrentPosition () {
    return exoPlayer != null ? exoPlayer.getCurrentPosition() : C.TIME_UNSET;
  }

  @Override
  public void prepareShowAnimation () {
    revealAnimator = new FactorAnimator(ANIMATOR_REVEAL, this, AnimatorUtils.DECELERATE_INTERPOLATOR, REVEAL_ANIMATION_DURATION);
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

  private static final long REVEAL_ANIMATION_DURATION = 280;
  private static final int ANIMATOR_REVEAL = -1;
  private FactorAnimator revealAnimator;
  private float revealFactor;

  private void setRevealFactor (float revealFactor) {
    if (this.revealFactor != revealFactor) {
      this.revealFactor = revealFactor;
      onChangeRevealFactor(revealFactor);
    }
  }

  protected final float getRevealFactor () {
    return revealFactor;
  }

  abstract protected void onChangeRevealFactor (float factor);

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (id == ANIMATOR_REVEAL) {
      setRevealFactor(factor);
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (id == ANIMATOR_REVEAL) {
      if (finalFactor == 0f) {
        popupView.onCustomHideAnimationComplete();
      }
      if (finalFactor == 1f) {
        tgMessage.readContent();
        popupView.onCustomShowComplete();
        exoPlayer.setPlayWhenReady(true);
        checkProgressTimer(true);
      }
    }
  }


  /* Args */

  protected MessagesController controller;
  protected TGMessage tgMessage;
  protected View anchorView;
  private RandomAccessFile contentFile;

  public static class Args {
    private final MessagesController controller;
    private final TGMessage message;
    private final View anchorView;
    private final RandomAccessFile contentFile;

    public Args(@NonNull TGMessage message) {
      this.message = message;
      this.controller = message.messagesController();
      this.anchorView = controller != null ? controller.getManager().findMessageView(message.getChatId(), message.getId()) : null;

      final TdApi.File file = TD.getFile(message);
      try {
        this.contentFile = new RandomAccessFile(new File(file.local.path), "r");
      } catch (FileNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.controller = args.controller;
    this.tgMessage = args.message;
    this.anchorView = args.anchorView;
    this.contentFile = args.contentFile;
  }

  @Override
  public void destroy () {
    super.destroy();

    checkProgressTimer(false);

    exoPlayer.release();
    exoPlayer = null;

    U.closeFile(contentFile);
  }





  /* Visual Progress */

  private static final int ACTION_PROGRESS_TICK = 1;

  private final VideoHandler handler = new VideoHandler(this);

  private static class VideoHandler extends Handler {
    private final DisposableMediaViewController controller;

    public VideoHandler (DisposableMediaViewController controller) {
      super(Looper.getMainLooper());
      this.controller = controller;
    }

    @Override
    public void handleMessage (@NonNull Message msg) {
      controller.onProgressTick();
    }
  }

  private boolean progressTimerStarted;

  private void checkProgressTimer (boolean isPlayed) {
    if (this.progressTimerStarted != isPlayed) {
      this.progressTimerStarted = isPlayed;
      Log.i(Log.TAG_VIDEO, "progressTimerStarted -> %b", isPlayed);
      handler.removeMessages(ACTION_PROGRESS_TICK);
      onProgressTick();
    }
  }

  private float progress;
  private long playPosition = -1;
  private long playDuration = -1;

  private void setPlayProgress (float progress, long playPosition, long playDuration) {
    if (this.progress != progress || this.playPosition != playPosition || this.playDuration != playDuration) {
      // boolean reset = this.remainingSeconds != remainingSeconds || this.totalSeconds != totalSeconds;

      this.progress = progress;
      this.playDuration = playDuration;
      if (this.playPosition != playPosition) {
        this.playPosition = playPosition;
      }

      setVisualProgress(MathUtils.clamp(progress));
    }
  }

  private float visualProgress;

  public float getVisualProgress () {
    return visualProgress;
  }

  private void setVisualProgress (float progress) {
    if (this.visualProgress != progress) {
      this.visualProgress = progress;
      onChangeVisualProgress(progress);
    }
  }

  private void onProgressTick () {
    final long duration = getExoPlayerDuration();
    final long position = getExoPlayerCurrentPosition();
    if (duration != C.TIME_UNSET && position != C.TIME_UNSET) {
      float progress = duration != 0 ? MathUtils.clamp((float) position / (float) duration) : 0f;
      setPlayProgress(progress, position, duration);
    }

    if (progressTimerStarted) {
      long delay = calculateProgressTickDelay(playDuration);
      handler.sendMessageDelayed(Message.obtain(handler, ACTION_PROGRESS_TICK), delay);
    }
  }

  protected abstract void onChangeVisualProgress (float progress);

  protected abstract long calculateProgressTickDelay (long playDuration);

}
