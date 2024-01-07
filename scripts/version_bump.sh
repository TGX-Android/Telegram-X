#!/bin/bash
set -e

PROPERTY_NAME=${1:-version.app}
VERSION_FILE=${2:-version.properties}

if [[ ! -f "$VERSION_FILE" ]]; then
  echo "version.properties not found! (Are you in the root folder?)" >&2; exit 1
fi

CURRENT_VERSION=$(cat "$VERSION_FILE" | grep "${PROPERTY_NAME}=" | cut -c 13-)
re='^[0-9]+$'
if ! [[ $CURRENT_VERSION =~ $re ]]; then
  echo "Error: $PROPERTY_NAME is not a number: \"$CURRENT_VERSION\"" >&2; exit 1
fi

NEW_VERSION=$((${CURRENT_VERSION}+1))
sed -i.bak "s/${PROPERTY_NAME}=${CURRENT_VERSION}.*/${PROPERTY_NAME}=${NEW_VERSION}/" "$VERSION_FILE"

UPDATED_VERSION=$(cat "$VERSION_FILE" | grep "${PROPERTY_NAME}=" | cut -c 13-)
if ! [ "$NEW_VERSION" -eq "$UPDATED_VERSION" ]; then
  if [ -f "${VERSION_FILE}.bak" ]; then
    rm "$VERSION_FILE" && mv "${VERSION_FILE}.bak" "$VERSION_FILE"
  fi
  echo "Error: $PROPERTY_NAME is not updated: \"$UPDATED_VERSION\", expected: \"$NEW_VERSION\"" >&2; exit 1
fi
rm "$VERSION_FILE.bak"

COMMIT_MESSAGE=
if [[ "$PROPERTY_NAME" == "version.app" ]]; then
  COMMIT_MESSAGE="Version bump to \`${NEW_VERSION}\`"
else
  COMMIT_MESSAGE="Bump \`${PROPERTY_NAME}\` to \`${NEW_VERSION}\`"
fi

echo "$COMMIT_MESSAGE"

git add "$VERSION_FILE"
git commit -m "$COMMIT_MESSAGE"

if [ "$#" -eq 4 ]; then
  USERNAME=$3
  PASSWORD=$4
  REMOTE="https://${USERNAME}:${PASSWORD}@$(git remote get-url origin | cut -c 9-)"
  git push "$REMOTE" > /dev/null 2>&1
fi