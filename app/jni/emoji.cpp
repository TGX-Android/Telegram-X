//
// Created by default on 8/1/17.
//

#include <thirdparty/emoji_suggestions/emoji_suggestions.h>
#include <emoji.h>
#include <log.h>
#include <sstream>
#include <cmath>
#include <jni_method.h>

#include "bridge.h"

JNI_FUNC(jint, getEmojiSuggestionMaxLength) {
  return Ui::Emoji::GetSuggestionMaxLength();
}

JNI_FUNC(jobjectArray, getEmojiSuggestions, jstring query) {
  const jchar *queryRaw = env->GetStringChars(query, nullptr);
  if (queryRaw == nullptr) {
    return nullptr;
  }

  jsize len = env->GetStringLength(query);
  std::vector<Ui::Emoji::Suggestion> suggestions = Ui::Emoji::GetSuggestions(Ui::Emoji::utf16string(queryRaw, len));
  env->ReleaseStringChars(query, queryRaw);

  if (suggestions.empty()) {
    return nullptr;
  }

  jclass jclass_Suggestion = nullptr;
  jmethodID jnew_Suggestion = jni_method::get(env,
                                              "org/thunderdog/challegram/N$Suggestion",
                                              "<init>",
                                              "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
                                              &jclass_Suggestion);
  if (jclass_Suggestion == nullptr || jnew_Suggestion == nullptr) {
    return nullptr;
  }

  jobjectArray array = (*env).NewObjectArray(suggestions.size(), jclass_Suggestion, nullptr);
  size_t i = 0;
  for (auto const &suggestion : suggestions) {
    jstring suggestionEmoji = (*env).NewString(suggestion.emoji().data(), suggestion.emoji().size());
    jstring suggestionLabel = (*env).NewString(suggestion.label().data(), suggestion.label().size());
    jstring suggestionReplacement = (*env).NewString(suggestion.replacement().data(), suggestion.replacement().size());

    jobject suggestionObj = (*env).NewObject(jclass_Suggestion, jnew_Suggestion, suggestionEmoji, suggestionLabel, suggestionReplacement);
    (*env).SetObjectArrayElement(array, i, suggestionObj);
    (*env).DeleteLocalRef(suggestionEmoji);

    (*env).DeleteLocalRef(suggestionLabel);
    (*env).DeleteLocalRef(suggestionReplacement);
    (*env).DeleteLocalRef(suggestionObj);
    i++;
  }

  return array;
}

/*JNI_FUNC(jstring, getSuggestionEmoji, jbyteArray replacement) {
  jbyte *replacementRaw = (*env).GetByteArrayElements(replacement, false);
  if (replacementRaw == nullptr) {
    return nullptr;
  }

  jsize len = (*env).GetArrayLength(replacement);
  Ui::Emoji::utf16string utfQuery((const Ui::Emoji::utf16char *) replacementRaw, (size_t) (size_t) ceilf(len / 2.0f));
  Ui::Emoji::utf16string utfEmoji = Ui::Emoji::GetSuggestionEmoji(utfQuery);
  (*env).ReleaseByteArrayElements(replacement, replacementRaw, JNI_ABORT);

  if (utfEmoji.size() == 0) {
    return nullptr;
  }

  return (*env).NewString(utfEmoji.data(), utfEmoji.size());
}*/