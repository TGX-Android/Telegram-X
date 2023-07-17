package org.thunderdog.challegram.telegram;

import androidx.annotation.NonNull;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.TD;

public class TdlibException extends RuntimeException {
  private final TdApi.Error error;

  public TdlibException (@NonNull TdApi.Error error) {
    super(TD.toErrorString(error));
    this.error = error;
  }

  public TdApi.Error getError () {
    return error;
  }

  @Override
  @NonNull
  public String toString () {
    return TD.toErrorString(error);
  }
}
