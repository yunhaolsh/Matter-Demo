#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly DEMO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
readonly DEFAULT_MATTER_ROOT="$(cd "$DEMO_ROOT/../connectedhomeip" && pwd)"
readonly MATTER_ROOT="${MATTER_ROOT:-$DEFAULT_MATTER_ROOT}"
readonly MATTER_OUT="${MATTER_OUT:-$MATTER_ROOT/out/android-arm64-chip-tool}"
readonly EXPECTED_COMMIT="b961bbb56e7b9496453e39ed3f6420fe7e644865"
readonly SDK_MODULE="$DEMO_ROOT/android/matter-app-sdk"
readonly JAR_DEST="$SDK_MODULE/libs"
readonly JNI_DEST="$SDK_MODULE/src/main/jniLibs/arm64-v8a"
readonly MANIFEST_DEST="$SDK_MODULE/matter-sdk.properties"

readonly -a JARS=(
  "lib/src/controller/java/CHIPClusterID.jar"
  "lib/src/controller/java/CHIPClusters.jar"
  "lib/src/controller/java/CHIPController.jar"
  "lib/src/controller/java/CHIPInteractionModel.jar"
  "lib/src/controller/java/OnboardingPayload.jar"
  "lib/src/controller/java/libMatterJson.jar"
  "lib/src/controller/java/libMatterTlv.jar"
  "lib/src/platform/android/AndroidPlatform.jar"
  "lib/src/app/server/java/CHIPAppServer.jar"
)

readonly -a NATIVE_LIBS=(
  "lib/jni/arm64-v8a/libCHIPController.so"
  "lib/jni/arm64-v8a/libc++_shared.so"
)

fail() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

[[ -d "$MATTER_ROOT/.git" ]] || fail "Matter checkout not found: $MATTER_ROOT"

actual_commit="$(git -C "$MATTER_ROOT" rev-parse HEAD)"
if [[ "$actual_commit" != "$EXPECTED_COMMIT" && "${ALLOW_MATTER_COMMIT_MISMATCH:-0}" != "1" ]]; then
  fail "Matter commit is $actual_commit, expected $EXPECTED_COMMIT. Set ALLOW_MATTER_COMMIT_MISMATCH=1 only after reviewing compatibility."
fi

for relative_path in "${JARS[@]}" "${NATIVE_LIBS[@]}"; do
  [[ -s "$MATTER_OUT/$relative_path" ]] || fail "Missing artifact: $MATTER_OUT/$relative_path"
done

mkdir -p "$JAR_DEST" "$JNI_DEST"

for relative_path in "${JARS[@]}"; do
  install -m 0644 "$MATTER_OUT/$relative_path" "$JAR_DEST/$(basename "$relative_path")"
done

for relative_path in "${NATIVE_LIBS[@]}"; do
  install -m 0644 "$MATTER_OUT/$relative_path" "$JNI_DEST/$(basename "$relative_path")"
done

{
  printf 'matter.commit=%s\n' "$actual_commit"
  printf 'matter.source=%s\n' "$MATTER_ROOT"
  printf 'matter.output=%s\n' "$MATTER_OUT"
  printf 'matter.abi=arm64-v8a\n'
} > "$MANIFEST_DEST"

printf 'Prepared Matter Android artifacts from %s\n' "$actual_commit"
printf '  JARs: %s\n' "$JAR_DEST"
printf '  JNI:  %s\n' "$JNI_DEST"
