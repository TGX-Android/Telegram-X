package org.thunderdog.challegram.data;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.util.TranslationCounterDrawable;

import java.util.HashMap;

import me.vkryl.core.StringUtils;
import me.vkryl.td.Td;

public final class TranslationsManager {

  private final Tdlib tdlib;
  private final Translatable message;
  private final OnChangeTranslatedStatus statusDelegate;
  private final OnChangeTranslatedResult resultDelegate;
  private final OnNewTranslatedError errorDelegate;
  private String currentTranslatedLanguage;
  private String lastTranslatedLanguage;

  public interface OnNewTranslatedError {
    void onError (String string);
  }

  public interface OnChangeTranslatedStatus {
    void setTranslatedStatus (int status, boolean animated);
  }

  public interface OnChangeTranslatedResult {
    void setTranslationResult (TdApi.FormattedText translationResult);
  }

  public interface Translatable {
    @Nullable
    String getOriginalMessageLanguage ();

    TdApi.FormattedText getTextToTranslate ();
  }

  public TranslationsManager (Tdlib tdlib, Translatable message, OnChangeTranslatedStatus statusDelegate, OnChangeTranslatedResult resultDelegate, OnNewTranslatedError errorDelegate) {
    this.tdlib = tdlib;
    this.message = message;
    this.statusDelegate = statusDelegate;
    this.resultDelegate = resultDelegate;
    this.errorDelegate = errorDelegate;
  }

  public void stopTranslation () {
    requestTranslation(null);
  }

  private int lastDispatchedStatus = TranslationCounterDrawable.TRANSLATE_STATUS_DEFAULT;
  private TdApi.FormattedText lastDispatchedResult;

  private void dispatchStatus (int status, boolean animated) {
    if (lastDispatchedStatus != status) {
      this.lastDispatchedStatus = status;
      statusDelegate.setTranslatedStatus(status, animated);
    }
  }

  private void dispatchResult (int status, @Nullable TdApi.FormattedText result, boolean animated) {
    dispatchStatus(status, animated);
    if (!Td.equalsTo(lastDispatchedResult, result)) {
      this.lastDispatchedResult = result;
      resultDelegate.setTranslationResult(result);
    }
  }

  public void requestTranslation (String language) {
    currentTranslatedLanguage = language;
    if (language == null || StringUtils.equalsOrBothEmpty(language, message.getOriginalMessageLanguage())) {
      dispatchResult(TranslationCounterDrawable.TRANSLATE_STATUS_DEFAULT, null, true);
      currentTranslatedLanguage = null;
      return;
    }

    if (currentTranslatedLanguage != null) {
      lastTranslatedLanguage = currentTranslatedLanguage;
    }

    TdApi.FormattedText textToTranslate = prepareTextToTranslate(message.getTextToTranslate());
    if (textToTranslate == null) return;

    TdApi.FormattedText cachedText = getCachedTextTranslation(textToTranslate.text, language);
    if (cachedText != null) {
      dispatchResult(TranslationCounterDrawable.TRANSLATE_STATUS_SUCCESS, cachedText, true);
      return;
    }

    dispatchStatus(TranslationCounterDrawable.TRANSLATE_STATUS_LOADING, true);
    tdlib.ui().post(() -> requestTranslationImpl(textToTranslate, language, object -> tdlib.ui().post(() -> {
      if (object instanceof TdApi.FormattedText) {
        TdApi.FormattedText text = prepareTranslatedText((TdApi.FormattedText) object);
        saveCachedTextTranslation(textToTranslate.text, language, text);
        if (StringUtils.equalsOrBothEmpty(currentTranslatedLanguage, language)) {
          dispatchResult(TranslationCounterDrawable.TRANSLATE_STATUS_SUCCESS, text, true);
        }
      } else {
        if (StringUtils.equalsOrBothEmpty(currentTranslatedLanguage, language)) {
          dispatchStatus(TranslationCounterDrawable.TRANSLATE_STATUS_ERROR, true);
          if (object instanceof TdApi.Error) {
            errorDelegate.onError(TD.toErrorString(object));
          }
        }
      }
    })));
  }

  public String getCurrentTranslatedLanguage () {
    return currentTranslatedLanguage;
  }

  public String getLastTranslatedLanguage () {
    return lastTranslatedLanguage;
  }

  private void requestTranslationImpl (TdApi.FormattedText originalText, String toLanguage, Client.ResultHandler callback) {
    tdlib.client().send(new TdApi.TranslateText(originalText, toLanguage), callback);
  }




  private final HashMap<String, TranslatedCachedValue> mTranslationsCache2 = new HashMap<>();

  private static class TranslatedCachedValue {
    public final String originalLanguage;
    public final HashMap<String, TdApi.FormattedText> translationsCache;

    public TranslatedCachedValue (String originalLanguage) {
      this.originalLanguage = originalLanguage;
      this.translationsCache = new HashMap<>();
    }
  }

  public @Nullable String getCachedTextLanguage (String text) {
    TranslatedCachedValue cachedValue = mTranslationsCache2.get(text);
    return (cachedValue != null ? cachedValue.originalLanguage : null);
  }

  public void saveCachedTextLanguage (String text, String language) {
    if (!mTranslationsCache2.containsKey(text)) {
      mTranslationsCache2.put(text, new TranslatedCachedValue(language));
    }
  }

  public @Nullable TdApi.FormattedText getCachedTextTranslation (String text, String language) {
    TranslatedCachedValue cachedValue = mTranslationsCache2.get(text);
    return (cachedValue != null ? cachedValue.translationsCache.get(language) : null);
  }

  public void saveCachedTextTranslation (String text, String language, TdApi.FormattedText translated) {
    TranslatedCachedValue cachedValue = mTranslationsCache2.get(text);
    if (cachedValue != null) {
      cachedValue.translationsCache.put(language, translated);
    }
  }



  public static TdApi.FormattedText prepareTextToTranslate (TdApi.FormattedText text) {
    if (text == null || text.entities == null || text.entities.length == 0) {
      return text;
    }
    try {
      TdApi.TextEntity[] entities = new TdApi.TextEntity[text.entities.length];
      for (int a = 0; a < entities.length; a++) {
        TdApi.TextEntity entity = text.entities[a];
        if (entity.type instanceof TdApi.TextEntityTypeUrl) {
          String url = text.text.substring(entity.offset, entity.offset + entity.length);
          TdApi.TextEntityType newEntityTypeUrl = new TdApi.TextEntityTypeTextUrl(url);
          entities[a] = new TdApi.TextEntity(entity.offset, entity.length, newEntityTypeUrl);
        } else if (entity.type instanceof TdApi.TextEntityTypeMention) {
          String username = text.text.substring(entity.offset + 1, entity.offset + entity.length);
          TdApi.TextEntityType newEntityTypeUrl = new TdApi.TextEntityTypeTextUrl("https://t.me/" + username);
          entities[a] = new TdApi.TextEntity(entity.offset, entity.length, newEntityTypeUrl);
        }  else if (entity.type instanceof TdApi.TextEntityTypeHashtag) {
          TdApi.TextEntityType newEntityTypeUrl = new TdApi.TextEntityTypeCode();
          entities[a] = new TdApi.TextEntity(entity.offset, entity.length, newEntityTypeUrl);
        } else {
          entities[a] = entity;
        }
      }

      return new TdApi.FormattedText(text.text, entities);
    } catch (Exception e) {
      return text;
    }
  }

  public static TdApi.FormattedText prepareTranslatedText (TdApi.FormattedText text) {
    if (text == null || text.entities == null || text.entities.length == 0) {
      return text;
    }
    try {
      TdApi.TextEntity[] entities = new TdApi.TextEntity[text.entities.length];
      for (int a = 0; a < entities.length; a++) {
        TdApi.TextEntity entity = entities[a] = text.entities[a];
        if (entity.type instanceof TdApi.TextEntityTypeCode) {
          String code = text.text.substring(entity.offset, entity.offset + entity.length);
          if (code.startsWith("#")) {
            TdApi.TextEntityType newEntityType = new TdApi.TextEntityTypeHashtag();
            entities[a] = new TdApi.TextEntity(entity.offset, entity.length, newEntityType);
          }
        }
      }

      return new TdApi.FormattedText(text.text, entities);
    } catch (Exception e) {
      return text;
    }
  }
}
