package org.thunderdog.challegram.loader;

import android.view.View;

import org.thunderdog.challegram.data.TGReactions;
import org.thunderdog.challegram.loader.gif.GifReceiver;

import java.util.HashMap;
import java.util.Map;

public class ReceiversPool<T> {
  private final HashMap<T, ImageReceiver> imageReceiverHashMap;
  private final HashMap<T, GifReceiver> gifReceiverHashMap;
  private final View view;
  private boolean attached = false;

  public ReceiversPool (View view) {
    imageReceiverHashMap = new HashMap<>();
    gifReceiverHashMap = new HashMap<>();
    this.view = view;
  }

  public GifReceiver getGifReceiver (T key) {
    GifReceiver gifReceiver = gifReceiverHashMap.get(key);
    if (gifReceiver != null) {
      return gifReceiver;
    }

    gifReceiver = new GifReceiver(view);
    if (attached) {
      gifReceiver.attach();
    }
    gifReceiverHashMap.put(key, gifReceiver);
    return gifReceiver;
  }

  public ImageReceiver getImageReceiver (T key) {
    ImageReceiver imageReceiver = imageReceiverHashMap.get(key);
    if (imageReceiver != null) {
      return imageReceiver;
    }

    imageReceiver = new ImageReceiver(view, 0);
    if (attached) {
      imageReceiver.attach();
    }
    imageReceiverHashMap.put(key, imageReceiver);
    return imageReceiver;
  }

  public void attach () {
    attached = true;
    for (Map.Entry<T, ImageReceiver> pair : imageReceiverHashMap.entrySet()) {
      pair.getValue().attach();
    }
    for (Map.Entry<T, GifReceiver> pair : gifReceiverHashMap.entrySet()) {
      pair.getValue().attach();
    }
  }

  public void detach () {
    attached = false;
    for (Map.Entry<T, ImageReceiver> pair : imageReceiverHashMap.entrySet()) {
      pair.getValue().detach();
    }
    for (Map.Entry<T, GifReceiver> pair : gifReceiverHashMap.entrySet()) {
      pair.getValue().detach();
    }
  }

  public void destroy () {
    for (Map.Entry<T, ImageReceiver> pair : imageReceiverHashMap.entrySet()) {
      pair.getValue().destroy();
    }
    for (Map.Entry<T, GifReceiver> pair : gifReceiverHashMap.entrySet()) {
      pair.getValue().destroy();
    }
    imageReceiverHashMap.clear();
    gifReceiverHashMap.clear();
  }
}
