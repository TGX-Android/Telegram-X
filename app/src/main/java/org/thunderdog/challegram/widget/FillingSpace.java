package org.thunderdog.challegram.widget;

import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.core.lambda.Destroyable;

public class FillingSpace extends View implements Destroyable {
  public FillingSpace (Context context) {
    super(context);
  }

  public void setLayoutHeight (int height) {
    Views.setLayoutHeight(this, height);
  }

  private ViewController<?> themeProvider;

  public void setThemedBackground (@ColorId int colorId, @Nullable ViewController<?> themeProvider) {
    this.themeProvider = themeProvider;
    ViewSupport.setThemedBackground(this, colorId, themeProvider);
  }

  @Override
  public void performDestroy () {
    if (themeProvider != null) {
      themeProvider.removeThemeListenerByTarget(this);
      themeProvider = null;
    }
  }
}
