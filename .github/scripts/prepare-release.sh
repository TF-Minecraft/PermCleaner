#!/usr/bin/env bash
set -euo pipefail
: "${GH_TOKEN:?Set DEPS_TOKEN with Contents read access to TF-Minecraft/ServerAssets}"
ref=726208728d6b3b66d09e5efcdfab9a63b8e39228
mkdir -p libs
curl --fail --location --silent --show-error --retry 3 -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github.raw+json" "https://api.github.com/repos/TF-Minecraft/ServerAssets/contents/jars/b7156eab5677/spigot-api.jar?ref=$ref" > "libs/spigot-api.jar"
sha256sum --check .github/dependencies.sha256
