#!/bin/sh

set -x -e

INPUT_DIR="${1}"; shift
OUTPUT_DIR="${1}"; shift
DFL_LANG="${1}"; shift
MSGFMT=msgfmt

for INPUT in "${INPUT_DIR}"/*.po
do
  INPUT_BASENAME="$(basename "${INPUT}")"
  LANG="${INPUT_BASENAME%.po}"
  ${MSGFMT} --java2 -r dbusjava_localized -d "${OUTPUT_DIR}" -l "${LANG}" "${INPUT}"
done
${MSGFMT} --java2 -r dbusjava_localized -d "${OUTPUT_DIR}" "${INPUT_DIR}/${DFL_LANG}.po"

