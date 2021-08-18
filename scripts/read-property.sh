#!/bin/bash
set -e

FILE=$1
VAR=$2

while [ ! -f "$FILE" ]; do
  read -e -r -p "Properties file: " FILE
done

while [[ ! "$VAR" =~ ^[a-z][a-zA-Z0-9_.]*$ ]]; do
  read -r -p "Property name: " VAR
done

while IFS='=' read -r key value; do
  if [[ "$key" == "$VAR" ]]; then
    echo "$value"
    exit
  fi
done < "$FILE"
echo "$VAR not found in $FILE!"
exit 1