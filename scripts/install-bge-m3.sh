#!/usr/bin/env bash
set -euo pipefail

readonly MODEL_ID='Xenova/bge-m3'
readonly REVISION='4de13258303883538bd53b696b452bf8099f0858'
readonly TARGET_DIRECTORY="${1:?Usage: install-bge-m3.sh /absolute/model/directory}"

case "$TARGET_DIRECTORY" in
  /*) ;;
  *) echo 'Model directory must be absolute.' >&2; exit 2 ;;
esac

mkdir -p "$TARGET_DIRECTORY"
for file in tokenizer.json tokenizer_config.json config.json; do
  curl --fail --location --retry 3 --continue-at - \
    "https://huggingface.co/${MODEL_ID}/resolve/${REVISION}/${file}" \
    --output "${TARGET_DIRECTORY}/${file}"
done
curl --fail --location --retry 3 --continue-at - \
  "https://huggingface.co/${MODEL_ID}/resolve/${REVISION}/onnx/model_quantized.onnx" \
  --output "${TARGET_DIRECTORY}/model.onnx"

printf 'BGE-M3 revision %s installed in %s\n' "$REVISION" "$TARGET_DIRECTORY"
