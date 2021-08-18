#!/bin/bash
set -e
# shellcheck source=set-env.sh
source "$(pwd)/scripts/set-env.sh"

while [[ ! ($TELEGRAM_API_ID =~ ^[0-9]+$) ]]; do
  echo "Telegram API credentials required."
  echo "To obtain them, see https://core.telegram.org/api/obtaining_api_id"
  read -r -p "Telegram API_ID: " TELEGRAM_API_ID
done
while [[ ! ($TELEGRAM_API_HASH =~ ^[0-9a-z]+$) ]]; do
  read -r -p "Telegram API_HASH: " TELEGRAM_API_HASH
done
while [[ ! ($YOUTUBE_API_KEY =~ ^[0-9a-zA-Z_\-]+$) ]]; do
  echo "YouTube API credentials required."
  echo "To obtain them, see https://developers.google.com/youtube/android/player/register"
  read -r -p "YouTube API_KEY: " YOUTUBE_API_KEY
done
while [[ ! -f $KEYSTORE_FILE ]]; do
  read -e -r -p "Enter a path to keystore settings file: " KEYSTORE_FILE
done
while [[ ! ($APP_ID =~ ^[a-z.]+$) ]]; do
  read -r -p "Enter package identifier: " APP_ID
done
while [[ ! ($APP_NAME =~ ^.+$) ]]; do
  read -r -p "Enter app name: " APP_NAME
done
while [[ ! ($APP_DOWNLOAD_URL =~ ^https?://.+$) ]]; do
  read -r -p "Enter download url: " APP_DOWNLOAD_URL
done
while [[ ! ($APP_SOURCES_URL =~ ^https?://.+$) ]]; do
  read -r -p "Enter sources url ($(git config --get remote.origin.url): " APP_SOURCES_URL
done


cat <<EOF > local.properties
sdk.dir=$ANDROID_SDK_ROOT
org.gradle.workers.max=$CPU_COUNT
keystore.file=$KEYSTORE_FILE
app.id=$APP_ID
app.name=$APP_NAME
app.download_url=$APP_DOWNLOAD_URL
app.sources_url=$APP_SOURCES_URL
telegram.api_id=$TELEGRAM_API_ID
telegram.api_hash=$TELEGRAM_API_HASH
youtube.api_key=$YOUTUBE_API_KEY
EOF