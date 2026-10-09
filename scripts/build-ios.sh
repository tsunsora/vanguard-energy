#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ "$(uname -s)" != Darwin ]]; then
  echo "iOS builds require macOS and Xcode. Use the Native builds GitHub workflow from Windows." >&2
  exit 1
fi
command -v xcodegen >/dev/null || { echo 'Install XcodeGen: brew install xcodegen' >&2; exit 1; }
xcodegen generate --spec iosApp/project.yml
xcodebuild -project iosApp/VanguardEnergy.xcodeproj -scheme VanguardEnergy -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath iosApp/build/DerivedData CODE_SIGNING_ALLOWED=NO build
xcodebuild -project iosApp/VanguardEnergy.xcodeproj -scheme VanguardEnergy -configuration Release \
  -sdk iphoneos -destination 'generic/platform=iOS' \
  -archivePath iosApp/build/VanguardEnergy.xcarchive CODE_SIGNING_ALLOWED=NO archive
