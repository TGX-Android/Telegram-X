package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.attach.MediaBottomFilesController;
import org.thunderdog.challegram.component.chat.MediaPreview;
import org.thunderdog.challegram.component.inline.CustomResultView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibFilesManager;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.util.text.Letters;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.widget.FileProgressComponent;
import org.thunderdog.challegram.widget.SimplestCheckBox;

import java.io.File;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;

/**
 * Date: 03/12/2016
 * Author: default
 */

public class InlineResultCommon extends InlineResult<TdApi.InlineQueryResult> implements FileProgressComponent.SimpleListener, FactorAnimator.Target {
  private final String title;
  private String description;

  private AvatarPlaceholder avatarPlaceholder;

  private FileProgressComponent fileProgress;
  private boolean disableProgressInteract;
  private TdApi.File targetFile;

  private static final float AVATAR_PLACEHOLDER_RADIUS = 25f;

  public InlineResultCommon (BaseActivity context, Tdlib tdlib, TdApi.InlineQueryResultVideo data) {
    super(context, tdlib, TYPE_VIDEO, data.id, data);

    this.title = data.title.isEmpty() ? data.video.fileName : data.title;
    StringBuilder b = new StringBuilder(5);
    Strings.buildDuration(data.video.duration, TimeUnit.SECONDS, false, b);
    if (!data.description.isEmpty()) {
      b.append(", ");
      b.append(data.description);
    }
    this.description = b.toString();

    setMediaPreview(MediaPreview.valueOf(tdlib, data.video, Screen.dp(50f), Screen.dp(3f)));
    if (getMediaPreview() == null) {
      int placeholderColorId = TD.getColorIdForString(data.video.fileName.isEmpty() ? data.id : data.video.fileName);
      avatarPlaceholder = new AvatarPlaceholder(AVATAR_PLACEHOLDER_RADIUS, new AvatarPlaceholder.Metadata(placeholderColorId, TD.getLetters(title)), null);
    }
  }

  public InlineResultCommon (BaseActivity context, Tdlib tdlib, TdApi.InlineQueryResultVenue data) {
    super(context, tdlib, TYPE_VENUE, data.id, data);

    this.title = data.venue.title;
    this.description = data.venue.address;

    setMediaPreview(MediaPreview.valueOf(tdlib, data.venue.location, data.thumbnail, Screen.dp(50f), Screen.dp(3f)));
  }

  public InlineResultCommon (BaseActivity context, Tdlib tdlib, TdApi.InlineQueryResultLocation data) {
    super(context, tdlib, TYPE_LOCATION, data.id, data);

    this.title = data.title.isEmpty() ? Lang.getString(R.string.Location) : data.title;
    this.description = MathUtils.roundDouble(data.location.latitude) + ", " + MathUtils.roundDouble(data.location.longitude);
    setMediaPreview(MediaPreview.valueOf(tdlib, data.location, null, Screen.dp(50f), Screen.dp(3f)));
  }

  public InlineResultCommon (BaseActivity context, Tdlib tdlib, TdApi.InlineQueryResultContact data) {
    super(context, tdlib, TYPE_CONTACT, data.id, data);

    this.title = TD.getUserName(data.contact.firstName, data.contact.lastName);
    this.description = Strings.formatPhone(data.contact.phoneNumber);

    TdApi.User user = data.contact.userId != 0 ? tdlib.cache().user(data.contact.userId) : null;
    setMediaPreview(MediaPreview.valueOf(tdlib, user != null ? user.profilePhoto : null, data.thumbnail, Screen.dp(50f), Screen.dp(50f) / 2));

    if (getMediaPreview() == null) {
      Letters letters = TD.getLetters(data.contact.firstName, data.contact.lastName);
      int placeholderColorId = data.contact.userId != 0 ? TD.getAvatarColorId(data.contact.userId, tdlib.myUserId()) : R.id.theme_color_avatarInactive; //TD.getColorIdForString(TD.getUserName(data.contact.firstName, data.contact.lastName));
      avatarPlaceholder = new AvatarPlaceholder(AVATAR_PLACEHOLDER_RADIUS, new AvatarPlaceholder.Metadata(placeholderColorId, letters), null);
    }
  }

  public InlineResultCommon (BaseActivity context, Tdlib tdlib, TdApi.InlineQueryResultAudio data, TGPlayerController.PlayListBuilder builder) {
    this(context, tdlib, data.id, null, data.audio, builder);
  }

  public InlineResultCommon (BaseActivity context, Tdlib tdlib, TdApi.PageBlockAudio audio, TGPlayerController.PlayListBuilder builder) {
    this(context, tdlib, null, null, audio.audio, builder);
  }

  public InlineResultCommon (BaseActivity context, Tdlib tdlib, TdApi.PageBlockVoiceNote voiceNote, String title) {
    this(context, tdlib, null, null, title, voiceNote.voiceNote);
  }

  public InlineResultCommon (BaseActivity context, Tdlib tdlib, TdApi.Message msg, TdApi.MessageAudio audio, TGPlayerController.PlayListBuilder builder) {
    this(context, tdlib, null, msg, audio.audio, builder);
  }

  private TdApi.Audio audio;

  private InlineResultCommon (BaseActivity context, Tdlib tdlib, String id, @Nullable TdApi.Message msg, TdApi.Audio audio, TGPlayerController.PlayListBuilder builder) {
    super(context, tdlib, TYPE_AUDIO, id, null);

    this.title = TD.getTitle(audio);
    this.description = TD.getSubtitle(audio);
    this.audio = audio;
    this.targetFile = audio.audio;

    setMediaPreview(MediaPreview.valueOf(tdlib, audio, Screen.dp(50f), Screen.dp(50f) / 2));
    if (getMediaPreview() == null && msg != null) {
      setMediaPreview(MediaPreview.valueOf(tdlib, msg, null, Screen.dp(50f), Screen.dp(50f) / 2));
    }

    this.fileProgress = new FileProgressComponent(context, tdlib, TdlibFilesManager.DOWNLOAD_FLAG_MUSIC, getMediaPreview() != null, msg != null ? msg.chatId : 0, msg != null ? msg.id : 0);
    this.fileProgress.setViewProvider(currentViews);
    if (msg == null) {
      this.fileProgress.setPausedIconRes(FileProgressComponent.PLAY_ICON);
    }
    this.fileProgress.setSimpleListener(this);
    this.fileProgress.setDownloadedIconRes(FileProgressComponent.PLAY_ICON);
    if (getMediaPreview() != null) {
      this.fileProgress.setBackgroundColor(0x44000000);
    } else {
      this.fileProgress.setBackgroundColorId(R.id.theme_color_file);
    }
    if (msg != null) {
      this.fileProgress.setPlayPauseFile(msg, builder);
    } else {
      this.fileProgress.setPlayPauseFile(TD.newFakeMessage(audio), builder);
    }
  }

  private float trackPlayFactor;
  private boolean trackCurrent;
  private FactorAnimator trackPlayAnimator;
  private static final int ANIMATOR_PLAY = 0;

  private String trackDuration;
  private float trackDurationWidth;
  private boolean trackIsQueued;

  public void setIsTrack (boolean isQueueSong) {
    this.trackIsQueued = isQueueSong;
    this.fileProgress.setIsTrack(isQueueSong);
    if (this.getMediaPreview() != null) {
      this.getMediaPreview().setCornerRadius(isQueueSong ? Screen.dp(4f) : Screen.dp(50f) / 2);
    }
    this.fileProgress.setBackgroundColorId(R.id.theme_color_file);
    this.trackDuration = Strings.buildDuration(audio.duration);
    this.trackDurationWidth = U.measureText(trackDuration, Paints.getRegularTextPaint(11f));
  }

  private void setPlayFactor (float factor) {
    if (this.trackPlayFactor != factor) {
      this.trackPlayFactor = factor;
      invalidate();
    }
  }

  public void setIsTrackPlaying (boolean isPlaying) {
    if (trackCurrent || !trackIsQueued) {
      fileProgress.setIsPlaying(isPlaying, trackPlayFactor == 1f);
    }
  }

  public void setIsTrackCurrent (boolean isCurrent) {
    boolean animated = hasAnyTargetToInvalidate();
    if (this.trackCurrent != isCurrent || !animated) {
      this.trackCurrent = isCurrent;
      fileProgress.setIsTrackCurrent(isCurrent);
      final float toFactor = isCurrent ? 1f : 0f;
      if (animated) {
        if (trackPlayAnimator == null) {
          trackPlayAnimator = new FactorAnimator(ANIMATOR_PLAY, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.trackPlayFactor);
        }
        trackPlayAnimator.animateTo(toFactor);
      } else {
        if (trackPlayAnimator != null) {
          trackPlayAnimator.forceFactor(toFactor);
        }
        setPlayFactor(toFactor);
      }
    }
  }

  private boolean isTrack () {
    return fileProgress != null && fileProgress.isTrack();
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_PLAY: {
        setPlayFactor(factor);
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {

  }

  public String getTrackTitle () {
    return title;
  }

  public String getTrackSubtitle () {
    return TD.getSubtitle(audio);
  }

  public TdApi.File getTrackFile () {
    return targetFile;
  }

  public TdApi.Audio getTrackAudio () {
    return audio;
  }

  /*private ImageFile createAudioFullPreview () {
    if (audio != null && preview != null && TD.isFileLoaded(audio.audio)) {
      fullPreview = new ImageMp3File(audio.audio.local.path);
      fullPreview.setSize(Screen.dp(80f, 3f));
      fullPreview.setScaleType(ImageFile.CENTER_CROP);
      fullPreview.setDecodeSquare(true);
    }
    return fullPreview;
  }*/

  public InlineResultCommon (BaseActivity context, Tdlib tdlib, TdApi.InlineQueryResultVoiceNote result) {
    this(context, tdlib, result.id, null, result.title, result.voiceNote);
  }

  public InlineResultCommon (BaseActivity context, Tdlib tdlib, TdApi.Message message, TdApi.VoiceNote voiceNote) {
    this(context, tdlib, null, message, tdlib.messageAuthor(message), voiceNote);
  }

  private TdApi.VoiceNote voiceNote;

  public InlineResultCommon (BaseActivity context, Tdlib tdlib, String id, TdApi.Message message, String title, TdApi.VoiceNote voiceNote) {
    super(context, tdlib, TYPE_VOICE, id, null);

    this.voiceNote = voiceNote;
    this.title = title;
    this.description = voiceNote.duration != 0 ? Strings.buildDuration(voiceNote.duration) : Strings.buildSize(voiceNote.voice.size);

    this.fileProgress = new FileProgressComponent(context, tdlib, TdlibFilesManager.DOWNLOAD_FLAG_VOICE, false, message != null ? message.chatId : 0, message != null ? message.id : 0);
    this.fileProgress.setViewProvider(currentViews);
    this.fileProgress.setDownloadedIconRes(FileProgressComponent.PLAY_ICON);
    this.fileProgress.setBackgroundColorId(R.id.theme_color_file);
    this.fileProgress.setPlayPauseFile(message != null ? message : TD.newFakeMessage(voiceNote), null);
  }

  // custom

  private boolean isCustom;
  private int customColorId;
  private Drawable customIcon;

  public InlineResultCommon (BaseActivity context, Tdlib tdlib, String path, @ThemeColorId int colorId, @DrawableRes int icon, String title, String subtitle) {
    super(context, tdlib, TYPE_DOCUMENT, path, null);

    this.title = title;
    this.description = subtitle;

    this.isCustom = true;
    this.customIcon = Drawables.get(context.getResources(), icon);
    this.customColorId = colorId;
  }

  // local doc

  private boolean ignoreDescriptionUpdates;
  private Object tag;

  public InlineResultCommon (BaseActivity context, Tdlib tdlib, File file, String title, String subtitle, Object tag, boolean isFolder) {
    super(context, tdlib, TYPE_DOCUMENT, file.getPath(), null);

    this.title = title;
    this.description = subtitle;
    this.tag = tag;
    this.ignoreDescriptionUpdates = true;

    this.targetFile = TD.newFile(file);

    String mimeType = U.resolveMimeType(file.getPath());
    setMediaPreview(MediaPreview.valueOf(tdlib, file, mimeType, Screen.dp(50f), Screen.dp(50f) / 2));

    this.fileProgress = new FileProgressComponent(context, tdlib, TdlibFilesManager.DOWNLOAD_FLAG_FILE, false, 0, 0);
    this.fileProgress.setViewProvider(currentViews);
    this.fileProgress.setSimpleListener(this);
    if (this.getMediaPreview() == null) {
      this.fileProgress.setDownloadedIconRes(isFolder ? R.drawable.baseline_folder_24 : R.drawable.baseline_insert_drive_file_24);
      this.fileProgress.setBackgroundColorId(TD.getFileColorId(file.getName(), mimeType, false));
    } else {
      if (isFolder) {
        this.fileProgress.setBackgroundColor(0x66000000);
        this.fileProgress.setDownloadedIconRes(R.drawable.baseline_folder_24);
      } else {
        this.fileProgress.setBackgroundColor(0x44000000);
      }
    }
    this.fileProgress.setIsLocal();
    this.fileProgress.setFile(targetFile);
    this.fileProgress.setMimeType(mimeType);
  }

  public Object getTag () {
    return tag;
  }

  public InlineResultCommon (BaseActivity context, Tdlib tdlib, MediaBottomFilesController.FileEntry entry) {
    super(context, tdlib, TYPE_DOCUMENT, Long.toString(entry.getId()), null);

    this.tag = entry;

    this.title = entry.getDisplayName();
    this.description = Strings.buildSize(entry.getSize());
    this.ignoreDescriptionUpdates = true;

    this.targetFile = TD.newFile(-1, Long.toString(entry.getId()), entry.getData(), (int) entry.getSize());

    this.fileProgress = new FileProgressComponent(context, tdlib, TdlibFilesManager.DOWNLOAD_FLAG_MUSIC, false, 0, 0);
    this.fileProgress.setViewProvider(currentViews);
    this.fileProgress.setSimpleListener(this);
    this.fileProgress.setIsLocal();
    this.fileProgress.setDownloadedIconRes(FileProgressComponent.PLAY_ICON);
    if (getMediaPreview() != null) {
      this.fileProgress.setBackgroundColor(0x44000000);
    } else {
      this.fileProgress.setBackgroundColorId(R.id.theme_color_file);
    }
  }

  public InlineResultCommon (BaseActivity context, Tdlib tdlib, MediaBottomFilesController.MusicEntry entry, TGPlayerController.PlayListBuilder builder) {
    super(context, tdlib, TYPE_AUDIO, Long.toString(entry.getId()), null);

    File file = new File(entry.getPath());

    // this.musicEntry = entry;
    this.tag = entry;

    this.title = StringUtils.isEmpty(entry.getTitle()) ? Lang.getString(R.string.UnknownTrack) : entry.getTitle();
    this.description = StringUtils.isEmpty(entry.getArtist()) ? Lang.getString(R.string.AudioUnknownArtist) : entry.getArtist();
    this.ignoreDescriptionUpdates = true;

    final int fileSize = (int) file.length();
    this.targetFile = TD.newFile(-1, Long.toString(entry.getId()), file.getPath(), fileSize);

    this.fileProgress = new FileProgressComponent(context, tdlib, TdlibFilesManager.DOWNLOAD_FLAG_MUSIC, false, 0, 0);
    this.fileProgress.setViewProvider(currentViews);
    this.fileProgress.setSimpleListener(this);
    this.fileProgress.setIsLocal();
    this.fileProgress.setDownloadedIconRes(FileProgressComponent.PLAY_ICON);
    if (getMediaPreview() != null) {
      this.fileProgress.setBackgroundColor(0x44000000);
    } else {
      this.fileProgress.setBackgroundColorId(R.id.theme_color_file);
    }
    String fileName = U.getFileName(entry.getPath());
    TdApi.Audio audio = new TdApi.Audio((int) (entry.getDuration() / 1000l), entry.getTitle(), entry.getArtist(), fileName, U.resolveMimeType(U.getExtension(fileName)), null, null, targetFile);
    TdApi.Message message = TD.newFakeMessage(audio);
    message.id = entry.getId();
    this.fileProgress.setPlayPauseFile(message, builder);
  }

  public TdApi.Message getPlayPauseMessage () {
    return fileProgress != null ? fileProgress.getPlayPauseFile() : null;
  }

  // doc

  public InlineResultCommon (BaseActivity context, Tdlib tdlib, TdApi.Message message, TdApi.Document document) {
    super(context, tdlib, TYPE_DOCUMENT, null, null);

    this.title = StringUtils.isEmpty(document.fileName) ? StringUtils.isEmpty(document.mimeType) ? Lang.getString(R.string.File) : ("image/gif".equals(document.mimeType) ? "GIF File" : document.mimeType) : document.fileName;
    this.description = Strings.buildSize(document.document.size);
    this.targetFile = document.document;

    setMediaPreview(MediaPreview.valueOf(tdlib, message, null, Screen.dp(50f), Screen.dp(50f) / 2));

    this.fileProgress = new FileProgressComponent(context, tdlib, TdlibFilesManager.DOWNLOAD_FLAG_FILE, getMediaPreview() != null, message.chatId, message.id);
    this.fileProgress.setViewProvider(currentViews);
    this.fileProgress.setSimpleListener(this);
    if (this.getMediaPreview() == null) {
      this.fileProgress.setDownloadedIconRes(document);
      this.fileProgress.setBackgroundColorId(TD.getFileColorId(document, false));
    } else {
      this.fileProgress.setBackgroundColor(0x44000000);
    }
    this.fileProgress.setFile(document.document);
    this.fileProgress.setMimeType(document.mimeType);
  }

  public InlineResultCommon (BaseActivity context, Tdlib tdlib, TdApi.InlineQueryResultDocument data) {
    super(context, tdlib, TYPE_DOCUMENT, data.id, data);

    this.title = data.title.isEmpty() ? data.document.fileName : data.title;
    this.description = data.description.isEmpty() ? Strings.buildSize(data.document.document.size) : Lang.getString(R.string.format_fileSizeAndDescription, Strings.buildSize(data.document.document.size), data.description);

    /*if (TGUtils.isHttpFile(data.document.document)) {
      disableProgressInteract = true;
    } else {
      targetFile = data.document.document;
    }*/
    targetFile = data.document.document;

    setMediaPreview(MediaPreview.valueOf(tdlib, data.document, Screen.dp(50f), Screen.dp(50f) / 2));

    this.fileProgress = new FileProgressComponent(this.context, tdlib, TdlibFilesManager.DOWNLOAD_FLAG_FILE, false, 0, 0);
    this.fileProgress.setViewProvider(currentViews);
    if (targetFile != null) {
      this.fileProgress.setSimpleListener(this);
    }
    this.fileProgress.setPausedIconRes(R.drawable.baseline_insert_drive_file_24);
    if (this.getMediaPreview() == null) {
      this.fileProgress.setDownloadedIconRes(data.document);
      this.fileProgress.setBackgroundColorId(TD.getFileColorId(data.document, false));
    } else {
      this.fileProgress.setBackgroundColor(0x44000000);
    }
    this.fileProgress.setFile(data.document.document);
    this.fileProgress.setMimeType(data.document.mimeType);
  }

  public boolean performClick (View v) {
    return fileProgress != null && fileProgress.performClick(v);
  }

  @Override
  protected int getContentHeight () {
    return Screen.dp(isTrack() && trackIsQueued ? 65f : 72f);
  }

  @Override
  public void requestContent (ComplexReceiver receiver, boolean isInvalidate) {
    if (getMediaPreview() != null) {
      getMediaPreview().requestFiles(receiver, isInvalidate);
    } else {
      receiver.clear();
    }
  }

  private int getPaddingVertical () {
    return Screen.dp(isTrack() && trackIsQueued ? 7.5f : 11f);
  }

  @Override
  protected void onResultAttachedToView (@NonNull View view, boolean isAttached) {
    if (fileProgress != null) {
      fileProgress.notifyInvalidateTargetsChanged();
    }
  }

  private Text trimmedTitle, trimmedDesc;
  private int lastAvailWidth;

  @Override
  protected void layoutInternal (int contentWidth) {
    lastAvailWidth = contentWidth - Screen.dp(11f) * 2 - Screen.dp(50f) - Screen.dp(15f);
    if (isTrack()) {
      lastAvailWidth -= Screen.dp(16f) + Screen.dp(23f) + Screen.dp(9f);
    }
    trimmedTitle = !StringUtils.isEmpty(title) ? new Text.Builder(title, lastAvailWidth, Paints.getTitleStyleProvider(), TextColorSets.Regular.NORMAL).singleLine().allBold().build() : null;
    trimmedDesc = !StringUtils.isEmpty(description) ? new Text.Builder(description, lastAvailWidth, Paints.getSubtitleStyleProvider(), TextColorSets.Regular.LIGHT).singleLine().build() : null;
  }

  @Override
  public boolean onTouchEvent (View view, MotionEvent e) {
    return !disableProgressInteract && fileProgress != null && fileProgress.onTouchEvent(view, e);
  }

  @Override
  protected void drawInternal (CustomResultView view, Canvas c, ComplexReceiver receiver, int viewWidth, int viewHeight, int startY) {
    int radius = trackIsQueued ? Screen.dp(3f) : Screen.dp(50f) / 2;

    RectF rectF = Paints.getRectF();
    rectF.set(Screen.dp(11f), getPaddingVertical(), Screen.dp(11f) + Screen.dp(50f), viewHeight - getPaddingVertical());

    final int cx = (int) rectF.centerX();
    final int cy = (int) rectF.centerY();

    if (fileProgress != null) {
      if (fileProgress.isTrack()) {
        fileProgress.setBounds(viewWidth - Screen.dp(16f) * 2 - Screen.dp(18f), startY, viewWidth + Screen.dp(4f), startY + getContentHeight() + Screen.dp(20f));
      } else {
        fileProgress.setBounds((int) rectF.left, (int) rectF.top, (int) rectF.right, (int) rectF.bottom);
      }
    }

    if (isCustom) {
      if (customColorId != 0) {
        c.drawCircle(cx, cy, Screen.dp(25f), Paints.fillingPaint(Theme.getColor(customColorId)));
      }
      if (customIcon != null) {
        Drawables.draw(c, customIcon, cx - customIcon.getMinimumWidth() / 2f, cy - customIcon.getMinimumHeight() / 2f, Paints.getPorterDuffPaint(0xffffffff));
      }
    } else if (fileProgress == null || getMediaPreview() != null) {
      if (getMediaPreview() != null) {
        getMediaPreview().draw(view, c, receiver, (int) rectF.left, (int) rectF.top, (int) rectF.width(), (int) rectF.height(), getMediaPreview().getCornerRadius(), 1f);
      } else {
        c.drawRoundRect(rectF, rectF.width() / 2f, rectF.height() / 2f, Paints.fillingPaint(Theme.getColor(avatarPlaceholder.metadata.colorId)));
        avatarPlaceholder.draw(c, rectF.centerX(), rectF.centerY(), 1f, avatarPlaceholder.getRadius(), false);
      }
    }

    if (fileProgress != null) {
      if (fileProgress.isTrack()) {
        if (!trackIsQueued && getMediaPreview() == null) {
          c.drawCircle(rectF.centerX(), rectF.centerY(), radius, Paints.fillingPaint(Theme.getColor(R.id.theme_color_file)));
        } else {
          if (getMediaPreview() == null || getMediaPreview().needPlaceholder(receiver)) {
            c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(Theme.getColor(R.id.theme_color_playerCoverPlaceholder)));
            Drawable drawable = view.getSparseDrawable(R.drawable.baseline_music_note_24, 0);
            Drawables.draw(c, drawable, rectF.centerX() - drawable.getMinimumWidth() / 2f, rectF.centerY() - drawable.getMinimumHeight() / 2f, Paints.getNotePorterDuffPaint());
          }
          if (getMediaPreview() != null) {
            getMediaPreview().draw(view, c, receiver, (int) rectF.left, (int) rectF.top, (int) rectF.width(), (int) rectF.height(), getMediaPreview().getCornerRadius(), 1f);
          }
        }
        if (trackIsQueued) {
          if (trackPlayFactor > 0f) {
            c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(ColorUtils.alphaColor(trackPlayFactor, Config.COVER_OVERLAY_QUEUE)));
            fileProgress.drawPlayPause(c, (int) rectF.centerX(), (int) rectF.centerY(), trackPlayFactor, trackCurrent);
          }
        } else {
          if (getMediaPreview() != null) {
            c.drawCircle(cx, cy, radius, Paints.fillingPaint(Config.COVER_OVERLAY));
          }
          fileProgress.drawPlayPause(c, cx, cy, 1f, true);
        }
      }
      fileProgress.draw(view, c);
    }

    if (trimmedTitle != null) {
      int textLeft = Screen.dp(11f) + Screen.dp(50f) + Screen.dp(15f);
      trimmedTitle.draw(c, textLeft, startY + getPaddingVertical() + Screen.dp(5f));
    }

    if (trackDuration != null) {
      c.drawText(trackDuration, viewWidth - Screen.dp(11f) - trackDurationWidth, startY + getPaddingVertical() + Screen.dp(18f), Paints.getRegularTextPaint(11f, Theme.textDecentColor()));
    }

    if (trimmedDesc != null) {
      int textLeft = Screen.dp(11f) + Screen.dp(50f) + Screen.dp(15f);
      trimmedDesc.draw(c, textLeft, startY + getPaddingVertical() + Screen.dp(29f));
    }
  }

  @Override
  public void onDrawSelectionOver (Canvas c, ComplexReceiver receiver, int viewWidth, int viewHeight, float anchorTouchX, float anchorTouchY, float selectFactor, String counter, @Nullable SimplestCheckBox checkBox) {
    final double radians = Math.toRadians(45f);

    final int x = Screen.dp(11f) + Screen.dp(50f) / 2 + (int) ((float) Screen.dp(50f) / 2f * Math.sin(radians));
    final int y = getPaddingVertical() + Screen.dp(50f) / 2 + (int) ((float) Screen.dp(50f) / 2f * Math.cos(radians));

    SimplestCheckBox.draw(c, x, y, selectFactor, counter, checkBox);

    RectF rectF = Paints.getRectF();
    int radius = Screen.dp(11f);
    rectF.set(x - radius, y - radius, x + radius, y + radius);

    c.drawArc(rectF, 135f, 170f * selectFactor, false, Paints.getOuterCheckPaint(ColorUtils.compositeColor(Theme.fillingColor(), Theme.chatSelectionColor())));
  }

  // Files stuff

  private void setDescription (String description) {
    if (!ignoreDescriptionUpdates && (this.description == null || !this.description.equals(description))) {
      this.description = description;
      if (lastAvailWidth > 0) {
        trimmedDesc = new Text.Builder(description, lastAvailWidth, Paints.getSubtitleStyleProvider(), TextColorSets.Regular.LIGHT).singleLine().build();
        invalidate();
      }
    }
  }

  @Override
  public boolean onClick (FileProgressComponent context, View view, TdApi.File file, long messageId) {
    return false;
  }

  @Override
  public InlineResult<TdApi.InlineQueryResult> setMessage (TdApi.Message message) {
    super.setMessage(message);
    updateDescription();
    return this;
  }

  private void updateDescription () {
    switch (getType()) {
      case TYPE_AUDIO: {
        String progress = FileComponent.buildProgressSubtitle(audio.audio, fileProgress != null && fileProgress.isLoading(), false);
        setDescription(progress != null ? progress : TD.getSubtitle(audio));
        break;
      }
      case TYPE_DOCUMENT: {
        String fileSizeStr = FileComponent.buildProgressSubtitle(targetFile, fileProgress != null && fileProgress.isLoading(), false);
        if (fileSizeStr == null) {
          fileSizeStr = Strings.buildSize(targetFile.expectedSize);
        }
        if (getMessage() != null) {
          setDescription(Lang.getString(R.string.format_fileSizeAndModifiedDate, fileSizeStr, Lang.getMessageTimestamp(getMessage().date, TimeUnit.SECONDS)));
        } else if (data instanceof TdApi.InlineQueryResultDocument && !((TdApi.InlineQueryResultDocument) data).description.isEmpty()) {
          setDescription(Lang.getString(R.string.format_fileSizeAndDescription, fileSizeStr, ((TdApi.InlineQueryResultDocument) data).description));
        } else {
          setDescription(fileSizeStr);
        }
        break;
      }
      case TYPE_VOICE: {
        String voiceDuration = Strings.buildDuration(voiceNote.duration);
        if (getMessage() != null) {
          setDescription(Lang.getString(R.string.format_fileSizeAndModifiedDate, voiceDuration, Lang.getMessageTimestamp(getMessage().date, TimeUnit.SECONDS)));
        } else {
          setDescription(voiceDuration);
        }
        break;
      }
    }
  }

  @Override
  public void onStateChanged (TdApi.File file, @TdlibFilesManager.FileDownloadState int state) {
    if (!ignoreDescriptionUpdates) {
      updateDescription();
    }
  }

  @Override
  public void onProgress (TdApi.File file, float progress) {
    updateDescription();
  }
}
