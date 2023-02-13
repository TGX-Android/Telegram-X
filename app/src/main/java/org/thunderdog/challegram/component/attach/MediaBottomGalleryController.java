/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 19/10/2016
 */
package org.thunderdog.challegram.component.attach;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.CircleCounterBadgeView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.core.Media;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.mediaview.MediaSelectDelegate;
import org.thunderdog.challegram.mediaview.MediaSendDelegate;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.mediaview.MediaViewDelegate;
import org.thunderdog.challegram.mediaview.MediaViewThumbLocation;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.mediaview.data.MediaStack;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.MenuMoreWrap;
import org.thunderdog.challegram.navigation.ToggleHeaderView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.util.HapticMenuHelper;
import org.thunderdog.challegram.v.RtlGridLayoutManager;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.td.Td;

public class MediaBottomGalleryController extends MediaBottomBaseController<MediaBottomGalleryController.Arguments> implements Media.GalleryCallback, MediaGalleryAdapter.Callback, Menu, View.OnClickListener, MediaBottomGalleryBucketAdapter.Callback, MediaViewDelegate, MediaSelectDelegate, MediaSendDelegate {
  public static class Arguments {
    public boolean allowVideos;

    public Arguments (boolean allowVideos) {
      this.allowVideos = allowVideos;
    }
  }

  public MediaBottomGalleryController (MediaLayout context) {
    super(context, R.string.Gallery);
  }

  @Override
  public int getId () {
    return R.id.controller_media_gallery;
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_more;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    switch (id) {
      case R.id.menu_more: {
        header.addSearchButton(menu, this);
        header.addMoreButton(menu, this);
        break;
      }
      case R.id.menu_clear: {
        header.addClearButton(menu, this);
        break;
      }
    }
  }

  @Override
  protected int getRecyclerBackgroundColorId () {
    return R.id.theme_color_chatBackground;
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    switch (id) {
      case R.id.menu_btn_clear: {
        clearSearchInput();
        break;
      }
      case R.id.menu_btn_search: {
        if (true) {
          mediaLayout.chooseInlineBot(tdlib.getPhotoSearchBotUsername());
        } else {
          if (galleryShown || !hasGalleryAccess) {
            mediaLayout.getHeaderView().openSearchMode();
            headerView = mediaLayout.getHeaderView();
          }
        }
        break;
      }
      case R.id.menu_btn_more: {
        mediaLayout.openGallery(false);
        break;
      }
    }
  }

  @Override
  public View getCustomHeaderCell () {
    if (headerCell == null && gallery != null) {
      headerCell = mediaLayout.getHeaderView().genToggleTitle(context(), this, this);
      FrameLayout.LayoutParams params = ((FrameLayout.LayoutParams) headerCell.getLayoutParams());
      params.width = ViewGroup.LayoutParams.MATCH_PARENT;
      if (Lang.rtl()) {
        params.leftMargin = Screen.dp(49f) * 2;
      } else {
        params.rightMargin = Screen.dp(49f) * 2;
      }
      updateHeaderText();
    }
    return headerCell;
  }

  @Override
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    if (Views.setGravity(headerCell, Gravity.TOP | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT))) {
      FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) headerCell.getLayoutParams();
      if (Lang.rtl()) {
        params.leftMargin = Screen.dp(49f) * 2;
        params.rightMargin = Screen.dp(68f);
      } else {
        params.rightMargin = Screen.dp(49f) * 2;
        params.leftMargin = Screen.dp(68f);
      }
      Views.updateLayoutParams(headerCell);
    }
  }

  @Override
  public void onClick (View v) {
    openSections();
  }

  @Override
  protected int getSearchHint () {
    return R.string.SearchForImages;
  }

  @Override
  protected int getSearchMenuId () {
    return R.id.menu_clear;
  }

  private boolean galleryLoaded;
  private boolean hasGalleryAccess;
  private Media.Gallery gallery;
  private GridSpacingItemDecoration decoration;
  private @Nullable CircleCounterBadgeView cameraBadgeView;
  private int spanCount;

  private @Nullable ToggleHeaderView headerCell;

  private void updateHeaderText () {
    if (headerCell != null) {
      headerCell.setText(getCurrentBucketName());
    }
  }

  private String getCurrentBucketName () {
    return currentBucket != null ? currentBucket.getName() : Lang.getString(R.string.AllMedia);
  }

  @Override
  protected View onCreateView (Context context) {
    buildContentView(false);
    recyclerView.setItemAnimator(null);

    int spanCount = calculateSpanCount(Screen.currentWidth(), Screen.currentHeight());

    decoration = new GridSpacingItemDecoration(spanCount, Screen.dp(4f), true, true, true);
    GridLayoutManager manager = new RtlGridLayoutManager(context(), spanCount);

    int options = MediaGalleryAdapter.OPTION_SELECTABLE | MediaGalleryAdapter.OPTION_ALWAYS_SELECTABLE;
    /*if (U.deviceHasAnyCamera(context)) {
      options |= MediaGalleryAdapter.OPTION_CAMERA_AVAILABLE;
    }*/
    adapter = new MediaGalleryAdapter(context(), recyclerView, manager, this, options);
    setLayoutManager(manager);
    setAdapter(adapter);
    addItemDecoration(decoration);

    if (galleryLoaded) {
      if (gallery == null/* && !U.deviceHasAnyCamera(context)*/) {
        showError(getErrorString(hasGalleryAccess), false);
      } else {
        showGallery(false);
      }
    } else {
      loadGalleryPhotos(null);
    }

    if (mediaLayout.needCameraButton()) {
      cameraBadgeView = new CircleCounterBadgeView(this, R.id.btn_camera, this::onCameraButtonClick, null);
      cameraBadgeView.init(R.drawable.deproko_baseline_camera_26, 48f, 4f, R.id.theme_color_circleButtonChat, R.id.theme_color_circleButtonChatIcon);
      cameraBadgeView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(CircleCounterBadgeView.BUTTON_WRAPPER_WIDTH), Screen.dp(74f), Gravity.BOTTOM | Gravity.RIGHT, 0, 0, Screen.dp(12), Screen.dp(12 + 60)));
      contentView.addView(cameraBadgeView);
    }

    return contentView;
  }

  @Override
  protected void onUpdateBottomBarFactor (float bottomBarFactor, float counterFactor, float y) {
    float factor = Math.min(bottomBarFactor, 1f - counterFactor);
    if (cameraBadgeView != null) {
      cameraBadgeView.setAlpha(factor);
      cameraBadgeView.setTranslationY(y);
    }
  }

  private void onCameraButtonClick (View v) {
    MessagesController c = mediaLayout.parentMessageController();
    if (c == null) return;

    if (!c.showRestriction(v, R.id.right_sendMedia, R.string.ChatDisabledMedia, R.string.ChatRestrictedMedia, R.string.ChatRestrictedMediaUntil)) {
      mediaLayout.hidePopupAndOpenCamera(new CameraOpenOptions().anchor(v).noTrace(c.isSecretChat()));
    }
  }

  @Override
  public boolean supportsMediaGrouping () {
    return true;
  }

  private static int calculateSpanCount (int width, int height) {
    int minSide = Math.min(width, height);
    int minWidth = minSide / 3;

    if (width > height) {
      return Math.max(5, width / minWidth);
    } else {
      return minWidth == 0 ? 3 : width / minWidth;
    }
  }

  @Override
  public void onViewportChanged (int width, int height) {
    super.onViewportChanged(width, height);
    int spanCount = calculateSpanCount(width, height);
    if (this.spanCount != spanCount) {
      this.spanCount = spanCount;
      decoration.setSpanCount(spanCount);
      recyclerView.invalidateItemDecorations();
      ((GridLayoutManager) getLayoutManager()).setSpanCount(spanCount);
    }
  }

  /*@Override
  protected int getInitialContentHeight () {
    return getMaxStartHeight();
    // Screen.dp(48f) * 3;
  }*/

  // Gallery

  private boolean galleryLoading;
  private Runnable onGalleryComplete;

  public void loadGalleryPhotos (Runnable onComplete) {
    if (galleryLoaded) {
      if (onComplete != null) {
        onComplete.run();
      }
      return;
    }
    if (galleryLoading) {
      onGalleryComplete = onComplete;
      return;
    }
    galleryLoading = true;
    onGalleryComplete = onComplete;
    Media.instance().getGalleryPhotos(0L, this, getArguments() == null || getArguments().allowVideos);
  }

  private long requestTime;

  @Override
  protected void preload (final Runnable after, long timeout) {
    requestTime = SystemClock.uptimeMillis();
    final CancellableRunnable cancellable = new CancellableRunnable() {
      @Override
      public void act () {
        after.run();
      }
    };
    loadGalleryPhotos(() -> {
      if (cancellable.isPending()) {
        cancellable.cancel();
        after.run();
      }
    });
    // UI.postDelayed(cancellable, timeout);
  }

  @Override
  public void displayPhotosAndVideos (Cursor cursor, final boolean hasAccess) {
    Log.i("Received gallery in %dms", SystemClock.uptimeMillis() - requestTime);
    requestTime = SystemClock.uptimeMillis();
    final Media.Gallery gallery = hasAccess && cursor != null && cursor.getCount() > 0 ? Media.instance().parseGallery(cursor, true, ImageFile.CENTER_CROP) : null;
    Log.i("Parsed gallery in %dms", SystemClock.uptimeMillis() - requestTime);
    UI.post(() -> {
      if ((gallery == null || gallery.isEmpty()) /*&& !U.deviceHasAnyCamera(context())*/) {
        setError(hasAccess);
      } else {
        setGallery(gallery);
      }
      if (onGalleryComplete != null) {
        onGalleryComplete.run();
        onGalleryComplete = null;
      }
      galleryLoaded = true;
    });
  }

  private void setGallery (Media.Gallery gallery) {
    this.gallery = gallery;
    this.currentBucket = gallery != null ? gallery.getDefaultBucket() : null;
    showGallery(true);
  }

  private static String getErrorString (boolean hasAccess) {
    return hasAccess ? Lang.getString(R.string.NoMediaYet) : Lang.getString(R.string.NoGalleryAccess);
  }

  private void setError (boolean hasAccess) {
    hasGalleryAccess = hasAccess;
    showError(getErrorString(hasAccess), true);
  }

  private boolean galleryShown;
  private MediaGalleryAdapter adapter;

  private void showGallery (boolean animated) {
    if (gallery == null || galleryShown) {
      return;
    }
    galleryShown = true;
    currentBucket = gallery.getDefaultBucket();
    showCurrentBucketImages();

    if (animated) {
      // expandStartHeight(adapter);
    }
  }

  @Override
  protected boolean canMinimizeHeight () {
    List<ImageFile> selectedImages = adapter.getSelectedPhotosAndVideosAsList(false);
    if (selectedImages != null && !selectedImages.isEmpty()) {
      for (ImageFile file : selectedImages) {
        if (file.hasAnyChanges())
          return false;
        if (file instanceof ImageGalleryFile && !Td.isEmpty(((ImageGalleryFile) file).getCaption(false, false)))
          return false;
      }
    }
    return true;
  }

  @Override
  public boolean showExitWarning (boolean isExitingSelection) {
    List<ImageFile> selectedImages = adapter.getSelectedPhotosAndVideosAsList(false);
    if (selectedImages != null && !selectedImages.isEmpty()) {
      boolean hasChanges = false, hasCaptions = false;
      for (ImageFile file : selectedImages) {
        if (file.hasAnyChanges())
          hasChanges = true;
        if (file instanceof ImageGalleryFile && !Td.isEmpty(((ImageGalleryFile) file).getCaption(false, false)))
          hasCaptions = true;
      }
      if (hasChanges || hasCaptions) {
        int res = hasChanges && hasCaptions ? R.string.DiscardMediaHint3 : hasCaptions ? R.string.DiscardMediaHint2 : R.string.DiscardMediaHint;
        int res2 = hasChanges && hasCaptions ? R.string.DiscardMediaMsg3 : hasCaptions ? R.string.DiscardMediaMsg2 : R.string.DiscardMediaMsg;
        showUnsavedChangesPromptBeforeLeaving(Lang.getMarkdownString(this, res), Lang.getString(res2), () -> {
          mediaLayout.onConfirmExit(isExitingSelection);
        });
        return true;
      }
    }
    return false;
  }

  @Override
  protected void onCompleteShow (boolean isPopup) {
    if (isPopup) {
      adapter.setAnimationsEnabled(true, (LinearLayoutManager) getLayoutManager());
    }
  }

  // Callbacks

  @Override
  public void onCameraRequested () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (context().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
        context().requestCameraPermission();
        return;
      }
    }
    mediaLayout.openCamera();
  }

  @Override
  public void onPhotoOrVideoPicked (ImageFile image) {
    mediaLayout.sendImage(image, showingFoundImages);
  }

  @Override
  public void onPhotoOrVideoSelected (int selectedCount, ImageFile image, int selectionIndex) {
    hideSoftwareKeyboard();
    mediaLayout.setCounter(selectedCount);
  }

  @Override
  protected void onMultiSendPress (@NonNull TdApi.MessageSendOptions options, boolean disableMarkdown) {
    // TODO delete other
    mediaLayout.sendPhotosOrVideos(adapter.getSelectedPhotosAndVideosAsList(true), showingFoundImages, options, disableMarkdown, false);
  }

  @Override
  protected void addCustomItems (@NonNull List<HapticMenuHelper.MenuItem> hapticItems) {
    if (canSendAsFile()) {
      List<ImageFile> files = adapter.getSelectedPhotosAndVideosAsList(false);
      boolean allVideo = files != null;
      if (allVideo) {
        for (ImageFile file : files) {
          if (!(file instanceof ImageGalleryFile) || !((ImageGalleryFile) file).isVideo()) {
            allVideo = false;
            break;
          }
        }
      }
      int count = files != null ? files.size() : 0;
      hapticItems.add(new HapticMenuHelper.MenuItem(R.id.btn_sendAsFile, count <= 1 ? Lang.getString(allVideo ? R.string.SendOriginal : R.string.SendAsFile) : Lang.plural(allVideo ? R.string.SendXOriginals : R.string.SendAsXFiles, count), R.drawable.baseline_insert_drive_file_24).setOnClickListener(v -> {
        if (v.getId() == R.id.btn_sendAsFile) {
          mediaLayout.pickDateOrProceed((sendOptions, disableMarkdown) ->
            mediaLayout.sendPhotosOrVideos(adapter.getSelectedPhotosAndVideosAsList(true), showingFoundImages, sendOptions, disableMarkdown, true)
          );
        }
      }));
    }
  }

  @Override
  public long getOutputChatId () {
    return mediaLayout.getTargetChatId();
  }

  @Override
  public boolean canDisableMarkdown () {
    List<ImageFile> imageFiles = adapter.getSelectedPhotosAndVideosAsList(false);
    if (imageFiles != null) {
      for (ImageFile imageFile : imageFiles) {
        if (imageFile instanceof ImageGalleryFile && ((ImageGalleryFile) imageFile).canDisableMarkdown()) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean canSendAsFile () {
    List<ImageFile> imageFiles = adapter.getSelectedPhotosAndVideosAsList(false);
    if (imageFiles == null || imageFiles.isEmpty())
      return false;
    for (ImageFile imageFile : imageFiles) {
      if (!(imageFile instanceof ImageGalleryFile) || !((ImageGalleryFile) imageFile).canSendAsFile()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void sendSelectedItems (ArrayList<ImageFile> images, TdApi.MessageSendOptions options, boolean disableMarkdown, boolean asFiles) {
    // TODO delete other
    mediaLayout.forceHide();
    mediaLayout.sendPhotosOrVideos(images, false, options, disableMarkdown, asFiles);
  }

  @Override
  protected void onCancelMultiSelection () {
    adapter.clearSelectedImages((GridLayoutManager) getLayoutManager());
  }

  @Override
  public boolean onPhotoOrVideoOpenRequested (ImageFile fromFile) {
    if (fromFile instanceof ImageGalleryFile && currentBucket != null) {
      MediaStack stack = new MediaStack(context, tdlib);
      final List<ImageFile> files = currentBucket.getMedia();
      final long time = SystemClock.elapsedRealtime();
      stack.set(fromFile, files);
      Log.i("stack.set complete for %d files in %dms", stack.getCurrentSize(), SystemClock.elapsedRealtime() - time);

      MediaViewController controller = new MediaViewController(context, tdlib);
      controller.setArguments(MediaViewController.Args.fromGallery(this, this, this, this, stack, mediaLayout.areScheduledOnly()).setReceiverChatId(mediaLayout.getTargetChatId()));
      controller.open();

      return true;
    }

    return false;
  }

  @Override
  public boolean isMediaItemSelected (int index, MediaItem item) {
    return adapter.getSelectionIndex(item.getSourceGalleryFile()) >= 0;
  }

  @Override
  public void setMediaItemSelected (int index, MediaItem item, boolean isSelected) {
    adapter.setSelected(item.getSourceGalleryFile(), isSelected);
  }

  @Override
  public int getSelectedMediaCount () {
    return adapter.getSelectedCount();
  }

  // Media view stuff

  private final MediaViewThumbLocation location = new MediaViewThumbLocation();

  @Override
  public MediaViewThumbLocation getTargetLocation (int indexInStack, MediaItem item) {
    if (MediaItem.isGalleryType(item.getType()) && !mediaLayout.isHidden()) {
      View view = adapter.findViewForImage(item.getSourceGalleryFile(), (LinearLayoutManager) getLayoutManager());
      if (view != null) {
        int viewTop = view.getTop();
        int viewBottom = view.getBottom();
        int top = viewTop + Math.round(recyclerView.getTranslationY()) + recyclerView.getTop();
        int bottom = top + view.getMeasuredHeight();
        int left = view.getLeft();
        int right = view.getRight();

        int receiverOffset = ((MediaGalleryImageView) view).getReceiverOffset();

        top += receiverOffset;
        bottom -= receiverOffset;
        left += receiverOffset;
        right -= receiverOffset;

        int clipTop = viewTop < 0 ? -viewTop : 0;
        int clipBottom = viewBottom < 0 ? -viewBottom : 0;

        int bottomBarHeight = mediaLayout.getCurrentBottomBarHeight();
        int bottomBarBottom = mediaLayout.getMeasuredHeight();

        if (bottomBarHeight > 0 && bottom > bottomBarBottom - bottomBarHeight) {
          clipBottom += bottom - (bottomBarBottom - bottomBarHeight);
        }

        location.set(left, top, right, bottom);
        location.setClip(0, clipTop, 0, clipBottom);

        return location;
      }
    }
    return null;
  }

  @Override
  public void setMediaItemVisible (int index, MediaItem item, boolean isVisible) {
    if (MediaItem.isGalleryType(item.getType())) {
      adapter.setImageVisible(item.getSourceGalleryFile(), isVisible, getLayoutManager());
    }
  }

  @Override
  public ArrayList<ImageFile> getSelectedMediaItems (boolean copy) {
    return adapter.getSelectedPhotosAndVideosAsList(copy);
  }

  // sections

  private View sectionHelperView;
  private RecyclerView sectionsView;
  //private MediaBottomGalleryBucketAdapter sectionsAdapter;

  private void openSections () {
    if (gallery == null || gallery.isEmpty()) {
      return;
    }
    MediaBottomGalleryBucketAdapter sectionsAdapter = sectionsView != null ?
      (MediaBottomGalleryBucketAdapter) sectionsView.getAdapter() :
      new MediaBottomGalleryBucketAdapter(context(), this, gallery);
    // int sectionsHeight = Math.round((Screen.dp(9f) + Screen.dp(9f) + Screen.dp(30f)) * 5.5f);
    // TODO: update automatically on orientation change without reopening
    int sectionsHeight = sectionsAdapter.measureHeight(recyclerView.getMeasuredHeight() + HeaderView.getSize(false) - Screen.dp(8f) * 2) + Screen.dp(8f) * 2;
    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(Screen.dp(210f) + Screen.dp(8f), sectionsHeight, Gravity.TOP | Gravity.LEFT);
    params.leftMargin = Screen.dp(50f);
    params.topMargin = HeaderView.getTopOffset();

    if (sectionsView == null) {
      sectionHelperView = new View(context()) {
        @Override
        public boolean onTouchEvent (MotionEvent event) {
          if (event.getAction() == MotionEvent.ACTION_DOWN && sectionsFactor != 0f) {
            closeSections();
            return true;
          }
          return super.onTouchEvent(event);
        }
      };
      sectionHelperView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

      sectionsView = (RecyclerView) Views.inflate(context, R.layout.recycler, mediaLayout);
      sectionsView.setLayoutParams(params);
      sectionsView.setBackgroundResource(R.drawable.bg_popup_fixed);
      sectionsView.setLayoutManager(new LinearLayoutManager(context(), RecyclerView.VERTICAL, false));
      sectionsView.setAdapter(sectionsAdapter);
      sectionsView.setOverScrollMode(View.OVER_SCROLL_NEVER);
      sectionsView.setAlpha(0f);
      sectionsView.setScaleX(MenuMoreWrap.START_SCALE);
      sectionsView.setScaleY(MenuMoreWrap.START_SCALE);
    } else {
      sectionsView.setLayoutParams(params);
    }

    if (currentBucket != null) {
      int i = gallery.indexOfBucket(currentBucket.getId());
      if (i != -1) {
        // ((LinearLayoutManager) sections.getLayoutManager()).scrollToPositionWithOffset(i, 0);
      }
    }

    if (sectionsView.getParent() == null) {
      mediaLayout.addView(sectionHelperView);
      mediaLayout.addView(sectionsView);
    }

    animateSectionFactor(1f);
  }

  private void closeSections () {
    if (sectionsView != null) {
      animateSectionFactor(0f);
    }
  }

  private boolean animatingSections;
  private ValueAnimator sectionsAnimator;

  private void animateSectionFactor (float toFactor) {
    if (animatingSections) {
      animatingSections = false;
      if (sectionsAnimator != null) {
        sectionsAnimator.cancel();
        sectionsAnimator = null;
      }
    }
    if (sectionsFactor == toFactor) {
      return;
    }
    animatingSections = true;
    sectionsAnimator = AnimatorUtils.simpleValueAnimator();
    sectionsAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    sectionsAnimator.setDuration(135);

    final float fromFactor = this.sectionsFactor;
    final float factorDiff = toFactor - fromFactor;
    sectionsAnimator.addUpdateListener(animation -> setSectionsFactor(fromFactor + factorDiff * AnimatorUtils.getFraction(animation)));
    sectionsAnimator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        if (animatingSections) {
          if (sectionsFactor == 0f) {
            mediaLayout.removeView(sectionsView);
            mediaLayout.removeView(sectionHelperView);
          }
          animatingSections = false;
        }
      }
    });
    sectionsAnimator.start();
  }

  private float sectionsFactor;

  private void setSectionsFactor (float factor) {
    if (this.sectionsFactor != factor && animatingSections) {
      this.sectionsFactor = factor;
      sectionsView.setAlpha(factor);
      float scale = MenuMoreWrap.START_SCALE + (1f - MenuMoreWrap.START_SCALE) * factor;
      sectionsView.setScaleX(scale);
      sectionsView.setScaleY(scale);
      sectionsView.setPivotX(Screen.dp(17f));
      sectionsView.setPivotY(Screen.dp(8f));
    }
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    if (sectionsFactor != 0f) {
      closeSections();
      return true;
    }
    return super.onBackPressed(fromTop);
  }

  private @Nullable Media.GalleryBucket currentBucket;

  @Override
  public void onBucketSelected (Media.GalleryBucket bucket) {
    if (!animatingSections) {
      closeSections();

      if (currentBucket != bucket && (currentBucket == null || currentBucket.getId() != bucket.getId())) {
        currentBucket = bucket;
        showCurrentBucketImages();
        updateHeaderText();
      }
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    if (sectionsView != null) {
      Views.destroyRecyclerView(sectionsView);
    }
  }

  private boolean showingFoundImages;

  private void showCurrentBucketImages () {
    if (showingFoundImages) {
      mediaLayout.clearCounter();
      showingFoundImages = false;
    }
    if (currentBucket != null) {
      // recyclerView.setItemAnimator(null);
      adapter.setImages(currentBucket.getMedia(), currentBucket.isCameraBucket() || currentBucket.isAllPhotosBucket());
    } else {
      adapter.setImages(null, true);
    }
    ((LinearLayoutManager) recyclerView.getLayoutManager()).scrollToPositionWithOffset(0, 0);
  }

  private void showFoundImages (ArrayList<ImageFile> images) {
    showingFoundImages = true;
    mediaLayout.clearCounter();
    // recyclerView.setItemAnimator(new CustomItemAnimator(Anim.DECELERATE_INTERPOLATOR, 180l));
    adapter.setImages(images, false);
  }

  @Override
  protected void onLeaveSearchMode () {
    searchImages("");
  }

  @Override
  protected void onSearchInputChanged (String query) {
    searchImages(query.trim().toLowerCase());
  }

  private String lastQuery = "";
  private CancellableRunnable searchTask;

  private void searchImages (final String q) {
    if (lastQuery.equals(q)) {
      return;
    }

    setClearButtonSearchInProgress(false);

    if (searchTask != null) {
      searchTask.cancel();
      searchTask = null;
    }

    lastQuery = q;
    if (q.isEmpty()) {
      if (showingFoundImages) {
        showCurrentBucketImages();
      }
      return;
    }

    searchTask = new CancellableRunnable() {
      @Override
      public void act () {
        if (lastQuery.equals(q)) {
          searchInternal(q);
        }
      }
    };
    UI.post(searchTask, 500);
  }

  private String awaitingQuery;
  private long bingUserId;
  private boolean bingUserLoading;

  private void searchInternal (final String q) {
    setClearButtonSearchInProgress(true);
    if (bingUserId == 0) {
      awaitingQuery = q;
      if (!bingUserLoading) {
        bingUserLoading = true;
        tdlib.client().send(new TdApi.SearchPublicChat(tdlib.getPhotoSearchBotUsername()), object -> {
          switch (object.getConstructor()) {
            case TdApi.Chat.CONSTRUCTOR: {
              TdApi.User user = tdlib.chatUser((TdApi.Chat) object);
              if (user != null) {
                bingUserId = user.id;
                UI.post(() -> {
                  if (lastQuery.equals(awaitingQuery)) {
                    searchInternal(awaitingQuery);
                  }
                });
              }
              break;
            }
            case TdApi.Error.CONSTRUCTOR: {
              UI.showError(object);
              break;
            }
            default: {
              Log.unexpectedTdlibResponse(object, TdApi.SearchPublicChat.class, TdApi.Chat.class);
              break;
            }
          }
        });
      }
      return;
    }
    tdlib.client().send(new TdApi.GetInlineQueryResults(bingUserId, mediaLayout.getTargetChatId(), null, q, null), object -> {
      if (object.getConstructor() == TdApi.InlineQueryResults.CONSTRUCTOR) {
        TdApi.InlineQueryResults results = (TdApi.InlineQueryResults) object;
        final ArrayList<ImageFile> result = new ArrayList<>(results.results.length);
        for (TdApi.InlineQueryResult rawResult : results.results) {
          if (rawResult.getConstructor() == TdApi.InlineQueryResultPhoto.CONSTRUCTOR) {
            TdApi.InlineQueryResultPhoto photo = (TdApi.InlineQueryResultPhoto) rawResult;
            ImageFile imageFile = createFile(tdlib, photo.photo, results.inlineQueryId, photo.id);
            if (imageFile != null) {
              imageFile.setScaleType(ImageFile.CENTER_CROP);
              imageFile.setSize(Screen.dp(76f));
              result.add(imageFile);
            }
          }
        }
        UI.post(() -> {
          if (lastQuery.equals(q)) {
            showFoundImages(result);
          }
        });
      }
    });
  }

  private static ImageFile createFile (Tdlib tdlib, TdApi.Photo photo, long queryId, String resultId) {
    TdApi.PhotoSize nearest = TD.findClosest(photo, Screen.dp(76f), Screen.dp(76f));

    if (nearest != null) {
      return new MediaImageFile(tdlib, nearest.photo, queryId, resultId);
    }

    return null;
  }

}