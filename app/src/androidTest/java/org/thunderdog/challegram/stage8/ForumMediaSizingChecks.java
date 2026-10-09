package org.thunderdog.challegram.stage8;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.ForumMessageLayout;
import org.thunderdog.challegram.data.MediaWrapper;
import org.thunderdog.challegram.data.MosaicWrapper;
import org.thunderdog.challegram.stage8.Stage8SyntheticInstrumentation.Case;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.tool.Screen;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.thunderdog.challegram.stage8.SyntheticEnvironment.allocate;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.equal;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.get;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.require;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.set;

/**
 * Actual MosaicWrapper.build/MediaWrapper.buildContent, with file-free synthetic media.
 * Only MediaWrapper construction is bypassed: dimensions and photo/video identity are seeded,
 * while duration, files, path, file progress, source message and TDLib stay null. No overrides
 * replace production sizing. A normal null-parent MosaicWrapper owns the real layout state.
 * Captions are integer widths, never TextWrapper text (which initializes persistent Emoji
 * settings). No drawing, decoding, account binding, animation or full TGMessage lifecycle is
 * exercised. The existing isolated-install and no-account/no-Settings guards remain mandatory.
 */
public final class ForumMediaSizingChecks {
  private ForumMediaSizingChecks () { }

  public static void register (List<Case> cases) {
    for (boolean video : new boolean[] {false, true}) {
      String kind = video ? "video" : "photo";
      cases.add(new Case("forum_media_portrait_" + kind, env -> single(env, video, 720, 1280)));
      cases.add(new Case("forum_media_landscape_" + kind, env -> single(env, video, 1280, 720)));
      cases.add(new Case("forum_media_square_" + kind, env -> single(env, video, 960, 960)));
      cases.add(new Case("forum_media_tall_bounded_" + kind, env -> single(env, video, 720, 4000)));
      cases.add(new Case("forum_media_repeated_panel_widths_" + kind, env -> repeatedWidths(env, video)));
    }
    for (int count : new int[] {2, 3, 4}) {
      int[][] sizes = Arrays.copyOf(new int[][] {{1280, 720}, {720, 1280}, {1280, 720}, {960, 960}}, count);
      cases.add(new Case("forum_media_album_" + count, env -> album(env, sizes)));
    }
    cases.add(new Case("forum_media_album_two_squares", env -> album(env, new int[][] {{960, 960}, {960, 960}})));
    for (int count : new int[] {2, 3, 4}) {
      int[][] sizes = new int[count][];
      for (int i = 0; i < count; i++) sizes[i] = new int[] {720, i == 0 ? 2400 : 1280};
      cases.add(new Case("forum_media_album_portrait_first_" + count, env -> album(env, sizes)));
    }
    for (int count : new int[] {5, 10}) {
      int[][] sizes = new int[count][];
      int[][] sequence = {{1600, 600}, {720, 1280}, {960, 960}};
      for (int i = 0; i < count; i++) sizes[i] = sequence[i % sequence.length];
      cases.add(new Case("forum_media_album_general_grid_" + count, env -> album(env, sizes)));
    }
  }

  private static MediaWrapper media (boolean video, int width, int height) throws Exception {
    MediaWrapper result = allocate(MediaWrapper.class);
    set(result, "contentWidth", width);
    set(result, "contentHeight", height);
    if (video) {
      TdApi.Video payload = new TdApi.Video();
      payload.width = width;
      payload.height = height;
      set(result, "video", payload);
    } else {
      set(result, "photo", new TdApi.Photo());
    }
    equal(width, result.getContentWidth(), "Synthetic source width uses the production field");
    equal(height, result.getContentHeight(), "Synthetic source height uses the production field");
    require(result.isVideo() == video && result.isPhoto() != video, "Photo/video identity is explicit");
    assertFileFree(result);
    return result;
  }

  private static void assertFileFree (MediaWrapper media) throws Exception {
    for (String field : new String[] {"tdlib", "source", "targetFile", "targetImageFile", "targetGifFile",
        "miniThumbnail", "previewFile", "path", "fileProgress", "duration", "animation"}) {
      require(get(media, field) == null, "Unsafe synthetic media seam: " + field + " must stay null");
    }
  }

  private static int build (SyntheticEnvironment env, MosaicWrapper mosaic, int width, int height, int mode) throws Exception {
    env.assertNoAccountInitialization();
    try {
      return mosaic.build(width, height, Math.min(width, Screen.dp(MosaicWrapper.MIN_LAYOUT_WIDTH)),
        Math.min(height, Screen.dp(MosaicWrapper.MIN_LAYOUT_HEIGHT)), mode, false);
    } finally {
      // Fail immediately on initialization, also if a future production path throws first.
      env.assertNoAccountInitialization();
    }
  }

  private static void assertLayout (MosaicWrapper mosaic, MediaWrapper[] media, int width, int maxHeight) throws Exception {
    equal(width, mosaic.getWidth(), "Measured mosaic fills exactly the requested content width");
    require(mosaic.getHeight() > 0 && mosaic.getHeight() <= maxHeight, "Measured mosaic height stays bounded");
    MosaicWrapper.MosaicItemInfo[] cells = (MosaicWrapper.MosaicItemInfo[]) get(mosaic, "mosaicItems");
    equal(media.length, cells.length, "All media cells survive the rebuild");
    int right = 0, bottom = 0;
    for (int i = 0; i < cells.length; i++) {
      MosaicWrapper.MosaicItemInfo cell = cells[i];
      require(get(cell, "target") == media[i], "Album order and target identity stay intact at " + i);
      require(cell.getX() >= 0 && cell.getY() >= 0 && cell.getWidth() > 0 && cell.getHeight() > 0,
        "Every cell has positive local bounds at " + i);
      equal(cell.getWidth(), media[i].getCellWidth(), "Real MediaWrapper cell width at " + i);
      equal(cell.getHeight(), media[i].getCellHeight(), "Real MediaWrapper cell height at " + i);
      require(cell.getX() + cell.getWidth() <= width && cell.getY() + cell.getHeight() <= mosaic.getHeight(),
        "Cell stays inside the measured mosaic at " + i);
      right = Math.max(right, cell.getX() + cell.getWidth());
      bottom = Math.max(bottom, cell.getY() + cell.getHeight());
      for (int j = 0; j < i; j++) {
        MosaicWrapper.MosaicItemInfo other = cells[j];
        require(cell.getX() >= other.getX() + other.getWidth() || other.getX() >= cell.getX() + cell.getWidth() ||
          cell.getY() >= other.getY() + other.getHeight() || other.getY() >= cell.getY() + cell.getHeight(),
          "Album cells must not overlap: " + j + "/" + i);
      }
      assertFileFree(media[i]);
    }
    equal(width, right, "Actual cell extent, not only the container, reaches the desired width");
    equal(mosaic.getHeight(), bottom, "Actual cell extent matches the reported height");
    if (media.length == 1) equal(width, media[0].getCellWidth(), "Single media itself fills the caption-expanded bubble");
    require(get(mosaic, "parent") == null && get(mosaic, "changeAnimator") == null,
      "Sizing must not attach a message or start an animation");
  }

  private static void single (SyntheticEnvironment env, boolean video, int sourceWidth, int sourceHeight) throws Exception {
    env.configure(1f, false, ThemeId.BLUE);
    MediaWrapper item = media(video, sourceWidth, sourceHeight);
    MediaWrapper[] items = {item};
    MosaicWrapper mosaic = new MosaicWrapper(item, null);
    int naturalBudget = Screen.dp(176), maxHeight = Screen.dp(320), available = Screen.dp(288), padding = Screen.dp(24);
    build(env, mosaic, naturalBudget, maxHeight, MosaicWrapper.MODE_FIT_AS_IS);
    int naturalWidth = mosaic.getWidth(), naturalHeight = mosaic.getHeight();
    require(naturalWidth <= naturalBudget && naturalWidth < available, "Fixture begins narrower than the caption budget");
    assertLayout(mosaic, items, naturalWidth, maxHeight);
    for (int captionWidth : new int[] {0, available * 2}) {
      equal(naturalWidth, ForumMessageLayout.expandedMediaWidth(false, naturalWidth, captionWidth, padding, available),
        "No-caption and legacy callers do not expand real media");
    }
    equal(naturalWidth, ForumMessageLayout.expandedMediaWidth(true, naturalWidth, Screen.dp(32), padding, available),
      "Short caption leaves the natural cell width alone");

    // Measured caption, inset, expected width: saturated, clamped, partial, forwarded (no inset).
    int[][] captions = {{available - padding, padding, available}, {available * 2, padding, available},
      {Screen.dp(200), padding, Screen.dp(200) + padding}, {Screen.dp(200), 0, Screen.dp(200)}};
    for (int[] caption : captions) {
      build(env, mosaic, naturalBudget, maxHeight, MosaicWrapper.MODE_FIT_AS_IS);
      int desiredWidth = ForumMessageLayout.expandedMediaWidth(true, mosaic.getWidth(), caption[0], caption[1], available);
      equal(caption[2], desiredWidth, "Caption expansion policy chooses the requested real-media width");
      require(desiredWidth > naturalWidth, "This fixture must exercise the bounded FIT_WIDTH second pass");
      build(env, mosaic, desiredWidth, maxHeight, MosaicWrapper.MODE_FIT_WIDTH_BOUNDED);
      assertLayout(mosaic, items, desiredWidth, maxHeight);
      if ((long) sourceHeight * desiredWidth >= (long) sourceWidth * maxHeight) {
        equal(maxHeight, item.getCellHeight(), "Tall source reaches the height cap without narrowing its cell");
      } else {
        require(Math.abs(item.getCellHeight() - (double) sourceHeight * desiredWidth / sourceWidth) <= 1,
          "Uncapped landscape/square media retains its aspect-ratio height");
      }
      equal(MosaicWrapper.MOSAIC_NOT_CHANGED, build(env, mosaic, desiredWidth, maxHeight, MosaicWrapper.MODE_FIT_WIDTH_BOUNDED),
        "Identical second pass is stable");
      assertLayout(mosaic, items, desiredWidth, maxHeight);
    }
    build(env, mosaic, naturalBudget, maxHeight, MosaicWrapper.MODE_FIT_AS_IS);
    assertLayout(mosaic, items, naturalWidth, maxHeight);
    equal(naturalHeight, item.getCellHeight(), "FIT_AS_IS restores the original height after caption expansion");
    equal(sourceWidth, item.getContentWidth(), "Rebuild never mutates source width");
    equal(sourceHeight, item.getContentHeight(), "Rebuild never mutates source height");
  }

  private static void repeatedWidths (SyntheticEnvironment env, boolean video) throws Exception {
    env.configure(1f, false, ThemeId.BLUE);
    MediaWrapper[] items = {media(video, 720, 1280)};
    MosaicWrapper mosaic = new MosaicWrapper(items[0], null);
    int maxHeight = Screen.dp(320);
    Map<Integer, int[]> seen = new HashMap<>();
    for (int padding : new int[] {Screen.dp(24), 0}) {
      for (int panelWidth : new int[] {248, 348, 288, 248, 288, 348, 248}) {
        int available = Screen.dp(panelWidth);
        build(env, mosaic, available, maxHeight, MosaicWrapper.MODE_FIT_AS_IS);
        int naturalWidth = mosaic.getWidth(), naturalHeight = mosaic.getHeight();
        require(naturalWidth < available, "Portrait must require expansion at every panel width");
        int captionWidth = ForumMessageLayout.captionMaxWidth(true, true, available, naturalWidth, padding);
        int desiredWidth = ForumMessageLayout.expandedMediaWidth(true, naturalWidth, captionWidth, padding, available);
        equal(available, desiredWidth, "Full caption follows the current panel width, including forwarded captions");
        build(env, mosaic, desiredWidth, maxHeight, MosaicWrapper.MODE_FIT_WIDTH_BOUNDED);
        assertLayout(mosaic, items, available, maxHeight);
        int[] dimensions = {naturalWidth, naturalHeight, mosaic.getWidth(), mosaic.getHeight()};
        int[] previous = seen.get(available);
        if (previous != null) require(Arrays.equals(previous, dimensions), "Revisited panel restores identical natural and expanded bounds");
        else seen.put(available, dimensions);
        build(env, mosaic, available, maxHeight, MosaicWrapper.MODE_FIT_AS_IS);
        equal(naturalWidth, ForumMessageLayout.expandedMediaWidth(true, mosaic.getWidth(), Screen.dp(32), padding, available),
          "Replacing a long caption with a short one does not retain the expanded width");
        assertLayout(mosaic, items, naturalWidth, maxHeight);
        equal(naturalHeight, items[0].getCellHeight(), "Short-caption restoration keeps the natural height");
      }
    }
  }

  private static void album (SyntheticEnvironment env, int[][] sizes) throws Exception {
    env.configure(1f, false, ThemeId.BLUE);
    int count = sizes.length;
    MediaWrapper[] items = new MediaWrapper[count];
    for (int i = 0; i < count; i++) items[i] = media(i % 2 == 0, sizes[i][0], sizes[i][1]);
    MosaicWrapper mosaic = new MosaicWrapper(items[0], null);
    int naturalBudget = Screen.dp(176), maxHeight = Screen.dp(320);
    build(env, mosaic, naturalBudget, maxHeight, MosaicWrapper.MODE_FIT_AS_IS);
    for (int i = 1; i < count; i++) mosaic.addItem(items[i], true);
    require(!mosaic.isSingular(), "Album uses real addItem, not seeded mosaic cells");
    for (int padding : new int[] {Screen.dp(24), 0}) {
      for (int panelWidth : new int[] {248, 348, 288, 248}) {
        int available = Screen.dp(panelWidth);
        build(env, mosaic, naturalBudget, maxHeight, MosaicWrapper.MODE_FIT_AS_IS);
        int naturalWidth = mosaic.getWidth();
        // Legacy general-grid fitting can exceed its height budget. The new bounded
        // mode must enforce it even for that input, not silently avoid such albums.
        assertLayout(mosaic, items, naturalWidth, mosaic.getHeight());
        equal(naturalWidth, ForumMessageLayout.expandedMediaWidth(false, naturalWidth, available, padding, available),
          "Legacy album remains at its natural size");
        int captionWidth = ForumMessageLayout.captionMaxWidth(true, true, available, naturalWidth, padding);
        int desiredWidth = ForumMessageLayout.expandedMediaWidth(true, naturalWidth, captionWidth, padding, available);
        equal(available, desiredWidth, "Album caption selects the current bounded width");
        require(desiredWidth > naturalWidth, "Album must exercise actual expansion");
        build(env, mosaic, desiredWidth, maxHeight, MosaicWrapper.MODE_FIT_WIDTH_BOUNDED);
        assertLayout(mosaic, items, desiredWidth, maxHeight);
        equal(MosaicWrapper.MOSAIC_NOT_CHANGED, build(env, mosaic, desiredWidth, maxHeight, MosaicWrapper.MODE_FIT_WIDTH_BOUNDED),
          "Repeated album expansion is stable");
        assertLayout(mosaic, items, desiredWidth, maxHeight);
      }
    }
    for (int i = 0; i < count; i++) {
      equal(sizes[i][0], items[i].getContentWidth(), "Album source width is not overwritten at " + i);
      equal(sizes[i][1], items[i].getContentHeight(), "Album source height is not overwritten at " + i);
    }
  }
}
