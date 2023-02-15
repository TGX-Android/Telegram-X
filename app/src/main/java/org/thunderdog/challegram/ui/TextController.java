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
 * File created on 17/03/2016 at 15:53
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.MoreDelegate;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.TGMimeType;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextWrapper;
import org.thunderdog.challegram.widget.SectionedRecyclerView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;

import me.vkryl.android.util.MultipleViewProvider;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;

public class TextController extends ViewController<TextController.Arguments> implements OptionDelegate, Handler.Callback, Menu, MoreDelegate {
  private static final int MODE_RAW_TEXT = 1;
  private static final int MODE_FILE = 2;

  public static class Arguments {
    private int mode, views;
    private String title;
    private String string;
    private String mimeType;

    private Arguments () { }

    public static Arguments fromFile (String title, String filePath, String mimeType) {
      Arguments args;
      args = new Arguments();
      args.mode = MODE_FILE;
      args.title = title;
      args.string = filePath;
      args.mimeType = mimeType;
      return args;
    }

    public static Arguments fromRawText (String title, String rawText, String mimeType) {
      Arguments args;
      args = new Arguments();
      args.mode = MODE_RAW_TEXT;
      args.title = title;
      args.string = rawText;
      args.mimeType = mimeType;
      return args;
    }

    public Arguments setViews (int views) {
      this.views = views;
      return this;
    }
  }

  public TextController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_text;
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_text;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    switch (id) {
      case R.id.menu_text: {
        header.addMoreButton(menu, this);
        break;
      }
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    switch (id) {
      case R.id.menu_btn_more: {
        showMore();
        break;
      }
    }
  }

  private void showMore () {
    IntList ids = new IntList(2);
    StringList strings = new StringList(2);

    /* TODO
    ids.append(R.id.btn_search);
    strings.append(R.string.Search);*/

    if (filePath != null && U.canOpenFile(new File(filePath), mimeType)) {
      ids.append(R.id.btn_openLink);
      strings.append(R.string.OpenInExternalApp);
    }

    if (canCopyText()) {
      ids.append(R.id.btn_copyText);
      strings.append(R.string.CopyText);

      if (getArgumentsStrict().mode == MODE_RAW_TEXT) {
        ids.append(R.id.btn_share);
        strings.append(R.string.Share);
      }
    }

    showMore(ids.get(), strings.get(), 0);
  }

  @Override
  public void onMoreItemPressed (int id) {
    switch (id) {
      case R.id.btn_openLink: {
        File file = new File(filePath);
        Intents.openFile(context, file, mimeType);
        break;
      }
      case R.id.btn_share: {
        Intents.shareText(rawText);
        break;
      }
      case R.id.btn_copyText: {
        copyText();
        break;
      }
      case R.id.btn_search: {
        openSearchMode();
        break;
      }
    }
  }

  private int views;

  private SectionedRecyclerView contentView;
  private Handler handler;
  private DoubleHeaderView headerCell;

  @Override
  protected View onCreateView (Context context) {
    headerCell = new DoubleHeaderView(context);
    headerCell.setThemedTextColor(this);
    headerCell.initWithMargin(Screen.dp(49f), true);

    contentView = new SectionedRecyclerView(context);
    contentView.setItemAnimator(null);
    contentView.setHideSections();
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    contentView.setSectionedAdapter(adapter = new TextAdapter(contentView, this));

    handler = new Handler(Looper.getMainLooper(), this);

    loadText();

    FrameLayoutFix wrapperView = new FrameLayoutFix(context);
    ViewSupport.setThemedBackground(wrapperView, R.id.theme_color_filling, this);
    wrapperView.addView(contentView);

    return wrapperView;
  }

  @Override
  public View getViewForApplyingOffsets () {
    return contentView;
  }

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  @Override
  public void onConfigurationChanged (Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    contentView.invalidateScrollbarFactor();
    contentView.layoutSectionStuff();
    adapter.prepareTexts();
  }

  private boolean canCopyText () {
    return rawText != null;
  }

  public void showCopy () {
    if (rawText != null && rawText.trim().length() > 0) {
      showOptions(new int[] {R.id.btn_copyText}, new String[] {Lang.getString(R.string.CopyText)});
    }
  }

  private String copyLine;

  public void showCopy (int index, String line) {
    if (line == null) {
      return;
    }
    String trimmed = line.trim();
    if (trimmed.length() == 0) {
      return;
    }
    copyLine = line;
    showOptions(trimmed, new int[] {R.id.btn_copyLine}, new String[] {Lang.getString(R.string.CopyLine) + " " + (index + 1)}, null, new int[] {R.drawable.baseline_content_copy_24});
  }

  private void copyText () {
    if (rawText == null) {
      UI.showToast(R.string.TextNotLoadedHint, Toast.LENGTH_SHORT);
    } else {
      UI.copyText(rawText, R.string.CopiedText);
    }
  }

  @Override
  public boolean onOptionItemPressed (View optionItemView, int id) {
    switch (id) {
      case R.id.btn_copyLine: {
        UI.copyText(copyLine, R.string.CopiedText);
        break;
      }
      case R.id.btn_copyText: {
        copyText();
        break;
      }
    }
    return true;
  }

  private String mimeType;
  private String filePath;

  private void loadText () {
    Arguments args = getArguments();
    if (args == null) {
      return;
    }
    mimeType = args.mimeType;
    views = args.views;
    headerCell.setTitle(args.title);
    switch (args.mode) {
      case MODE_FILE: {
        loadFile(filePath = args.string);
        break;
      }
      case MODE_RAW_TEXT: {
        displayText(args.string);
        break;
      }
    }
  }

  private static final int READ_PROGRESS = 0;
  private static final int DISPLAY_LINES = 1;
  private static final int READ_COMPLETE = 2;
  private static final int POST_INVALIDATE = 3;

  private void displayProgress (int readCount, boolean loading) {
    if (loading) {
      headerCell.setSubtitle(Lang.format(R.string.ReadingXLine, readCount + 1));
    } else if (views > 0) {
      headerCell.setSubtitle(Lang.getCharSequence(R.string.format_linesAndViews, Lang.pluralBold(R.string.xLines, readCount), Lang.pluralBold(R.string.xViews, views)));
    } else {
      headerCell.setSubtitle(Lang.pluralBold(R.string.xLines, readCount));
    }
  }

  private void onLineProgress (int readCount) {
    handler.sendMessage(Message.obtain(handler, READ_PROGRESS, readCount, 0));
  }

  private void postLines (int lineCount, ArrayList<LineCell> lines) {
    handler.sendMessage(Message.obtain(handler, DISPLAY_LINES, lineCount, 0, lines));
  }

  private void onLinesLoaded (int totalCount, ArrayList<LineCell> lines) {
    handler.sendMessage(Message.obtain(handler, READ_COMPLETE, totalCount, 0, lines));
  }

  private void postInvalidate () {
    handler.sendMessage(Message.obtain(handler, POST_INVALIDATE));
  }

  @Override
  public boolean handleMessage (Message msg) {
    switch (msg.what) {
      case READ_PROGRESS: {
        displayProgress(msg.arg1, true);
        return true;
      }
      case DISPLAY_LINES: {
        //noinspection unchecked
        displayLines((ArrayList<LineCell>) msg.obj, msg.arg1);
        return true;
      }
      case READ_COMPLETE: {
        displayProgress(msg.arg1, false);
        //noinspection unchecked
        displayLines((ArrayList<LineCell>) msg.obj, msg.arg1);
        return true;
      }
      case POST_INVALIDATE: {
        invalidateAll();
        return true;
      }
    }
    return false;
  }

  private void loadFile (final String file) {
    final int orientation = UI.getOrientation();
    final int width = Screen.currentWidth();
    Background.instance().post(() -> {
      final ArrayList<LineCell> lines = new ArrayList<>();
      int i = 0;
      BufferedReader br = null;
      try {
        br = new BufferedReader(new FileReader(file));
        StringBuilder b = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
          checkProgress(i);
          if (i != 0) {
            b.append('\n');
          }
          b.append(line);
          lines.add(new LineCell(TextController.this, i++, line));
          if (isDestroyed()) {
            break;
          }
          if (i % 100 == 0) {
            postLines(i, lines);
          }
          if (i == LINE_COUNT_LIMIT) {
            break;
          }
        }
        br.close();
        rawText = b.toString();
      } catch (Throwable t) {
        lines.add(new LineCell(TextController.this, i++, "CHALLEGRAM TEXT READER: Error reading file:"));
        lines.add(new LineCell(TextController.this, i++, Log.toString(Log.generateException())));
        if (br != null) {
          try {
            br.close();
          } catch (Throwable t2) {
            Log.w("Cannot close BufferedReader", t2);
          }
        }
      }
      onLinesLoaded(i, lines);
    });
  }

  private long lastProgressNotify;
  private static final long LINE_PROGRESS_TIME_THRESHOLD = 40l;
  private static final int LINE_COUNT_LIMIT = 100000;

  private String rawText;

  private void checkProgress (int lineId) {
    long time = System.currentTimeMillis();
    if (lineId == 0 || time - lastProgressNotify >= LINE_PROGRESS_TIME_THRESHOLD) {
      lastProgressNotify = time;
      onLineProgress(lineId);
    }
  }

  private void displayText (final String str) {
    if (isDestroyed()) {
      return;
    }

    if (!StringUtils.isEmpty(getArgumentsStrict().title))
      headerCell.setTitle(getArgumentsStrict().title);
    else
      headerCell.setTitle(R.string.Text);
    rawText = str;

    final int orientation = UI.getOrientation();
    final int width = Screen.currentWidth();

    Background.instance().post(() -> {
      final String[] raw = str.split("\n");
      final ArrayList<LineCell> lines = new ArrayList<>();
      int i = 0;
      for (String line : raw) {
        checkProgress(i);
        lines.add(new LineCell(TextController.this, i, line));
        i++;
        if (isDestroyed()) {
          return;
        }
        if (i % 100 == 0) {
          postLines(i, lines);
        }
        if (i == LINE_COUNT_LIMIT) {
          break;
        }
      }
      onLinesLoaded(i, lines);
    });
  }

  private void invalidateAll () {
    if (!isDestroyed()) {
      contentView.invalidateScrollbarFactor();
      contentView.layoutSectionStuff();
    }
  }

  private void displayLines (ArrayList<LineCell> lines, int lineCount) {
    if (!isDestroyed()) {
      adapter.displayLines(lines, lineCount);
    }
  }

  // Adapter stuff

  private TextAdapter adapter;

  private static class LineCell {
    private int id;
    private String idStr;
    private TextWrapper wrapper;
    private final int horizontalPadding, verticalPadding;
    private final MultipleViewProvider holder = new MultipleViewProvider();

    public LineCell (ViewController<?> context, int id, String line) {
      this.horizontalPadding = Screen.dp(6f);
      this.verticalPadding = Screen.dp(3f);

      this.id = id;
      this.idStr = Integer.toString(id + 1);
      this.wrapper = new TextWrapper(line, TGMessage.getTextStyleProvider(), TextColorSets.Regular.NORMAL)
        .setEntities(Text.makeEntities(line, Text.ENTITY_FLAGS_EXTERNAL, null, context.tdlib(), null), null)
        .addTextFlags(Text.FLAG_CUSTOM_LONG_PRESS | Text.FLAG_NO_TRIM | Text.FLAG_ARTICLE)
        .setViewProvider(holder);
      this.wrapper.prepare(Screen.currentWidth());
    }

    public void attach (View view) {
      holder.attachToView(view);
    }

    public void detach (View view) {
      holder.detachFromView(view);
    }

    private boolean prepared;

    public void prepareText (int width) {
      if (width > 0) {
        wrapper.prepare(width - horizontalPadding * 2);
        prepared = true;
      }
    }

    public long getId () {
      return id;
    }

    public String getIdStr () {
      return idStr;
    }

    public int getHeight (int width) {
      if (width > 0)
        prepareText(width);
      else if (!prepared)
        prepareText(Screen.currentWidth()); // FIXME
      return wrapper.getHeight() + verticalPadding * 2;
    }

    public boolean onTouchEvent (View view, MotionEvent e) {
      return wrapper.onTouchEvent(view, e);
    }

    public void draw (View view, Canvas c) {
      wrapper.draw(c, horizontalPadding, view.getMeasuredWidth() - horizontalPadding, 0, verticalPadding);
    }
  }

  private static class LineView extends View {
    private LineCell cell;

    public LineView (Context context) {
      super(context);
    }

    public LineCell getCell () {
      return cell;
    }

    public void setCell (LineCell cell) {
      if (this.cell == null || cell == null || cell.getId() != this.cell.getId()) {
        if (this.cell != null) {
          this.cell.detach(this);
        }
        this.cell = cell;
        if (cell != null) {
          cell.attach(this);
        }
        int height = cell == null ? 0 : cell.getHeight(getMeasuredWidth());
        int currentHeight = getMeasuredHeight();
        if (currentHeight != height && currentHeight > 0) {
          requestLayout();
          invalidate();
        }
      }
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec), cell == null ? getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec) : MeasureSpec.makeMeasureSpec(cell.getHeight(MeasureSpec.getSize(widthMeasureSpec)), MeasureSpec.EXACTLY));
    }

    @Override
    public boolean onTouchEvent (MotionEvent event) {
      return (cell != null && cell.onTouchEvent(this, event)) || super.onTouchEvent(event);
    }

    @Override
    protected void onDraw (Canvas c) {
      if (cell != null) {
        cell.draw(this, c);
      }
    }
  }

  public static boolean isAcceptableMimeType (@NonNull String mimeType) {
    return TGMimeType.isPlainTextMimeType(mimeType);
  }

  private static class TextAdapter extends SectionedRecyclerView.SectionedAdapter implements View.OnClickListener {
    private TextController controller;
    private ArrayList<LineCell> lines;
    private int lineCount;

    public TextAdapter (SectionedRecyclerView parentView, TextController controller) {
      super(parentView);
      this.controller = controller;
    }

    public void displayLines (ArrayList<LineCell> lines, int lineCount) {
      int oldItemCount = this.lineCount;
      this.lines = lines;
      this.lineCount = lineCount;
      int added = lineCount - oldItemCount;
      if (added > 0) {
        updateSections();
        notifyItemRangeInserted(oldItemCount, added);
      }
    }

    public void prepareTexts () {
      if (lines != null) {
        final ArrayList<LineCell> cells = lines;
        final int lineCount = this.lineCount;
        final int width = Screen.currentWidth();
        Background.instance().post(() -> {
          for (int i = 0; i < lineCount; i++) {
            cells.get(i).prepareText(width);
            if (controller.isDestroyed()) {
              return;
            }
          }
          controller.postInvalidate();
        });
      }
    }

    @Override
    public int getSectionCount () {
      return lineCount;
    }

    @Override
    public String getSectionName (int section) {
      return lines.get(section).getIdStr();
    }

    @Override
    public int getRowsInSection (int section) {
      return 1;
    }

    @Override
    public int getItemHeight () {
      return Screen.dp(25f);
    }

    public int getItemHeight (int adapterPosition) {
      return adapterPosition < 0 || adapterPosition >= lines.size() ? 0 : lines.get(adapterPosition).getHeight(0);
    }

    @Override
    public void onClick (View v) {
      if (lines.size() == 1) {
        controller.showCopy();
      } else {
        LineCell cell = ((LineView) v).getCell();
        if (cell != null) {
          controller.showCopy(cell.id, cell.wrapper.getText());
        }
      }
    }

    @Override
    public View createView (int viewType) {
      LineView view;

      view = new LineView(context);
      view.setOnClickListener(this);
      Views.setClickable(view);
      RippleSupport.setTransparentSelector(view);
      view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      controller.addThemeInvalidateListener(view);

      return view;
    }

    @Override
    public void updateView (SectionedRecyclerView.SectionViewHolder holder, int position) {
      ((LineView) holder.itemView).setCell(lines.get(position));
    }
  }
}
