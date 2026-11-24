# Building and running this MCGUI fork

This fork keeps MCGUI's Java script engine separate by design. A normal local build
embeds the engine for this build, so JavaScript pages work without matching CDN
availability.

## Requirements

- JDK 21 for normal builds.
- JDK 25 only for `paperTest`.
- Linux shell commands use `sh gradlew ...`.
- Run Gradle with JDK 21 in shells that currently select JDK 25:

```bash
export JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.13.0+2-ms
export PATH="$JAVA_HOME/bin:$PATH"
```

## Give fork builds a distinct script-engine version

The plugin and script engine are paired by the project version and SHA-256. Do not
keep the upstream `0.8.6` version after changing engine/plugin code if the fork will
use the upstream CDN.

Set a fork-specific version in `build.gradle`, for example:

```groovy
version = '0.8.6-fork.1'
```

For a dev build, pass a complete build version:

```bash
sh gradlew clean build -PbuildVersion='0.8.6-fork.1+dev.1'
```

The script engine jar name will contain that version, and the plugin embeds that
engine for local builds.

## Build and verify locally

```bash
sh gradlew clean build -x test
sh gradlew test --tests ai.resourcepack.ui.framework.ViewSettingsTest
sh gradlew test --tests ai.resourcepack.ui.framework.world.ViewsStandingTest
sh gradlew test
```

For JavaScript engine verification, also run the relevant isolated test:

```bash
sh gradlew test --tests ai.resourcepack.ui.ScriptTest.modernJavaScriptIsConverted
```

Create the plugin jar:

```bash
sh gradlew clean shadowJar
```

The plugin artifact is:

```text
build/libs/MCGUI-<version>.jar
```

Verify that the engine is embedded:

```bash
jar tf build/libs/MCGUI-<version>.jar | grep 'embedded/MCGUI-scripts.jar'
```

Verify that the embedded engine and plugin metadata have the same hash:

```bash
unzip -p build/libs/MCGUI-<version>.jar script-engine.properties
unzip -p build/libs/MCGUI-<version>.jar embedded/MCGUI-scripts.jar \
  > /tmp/MCGUI-scripts.jar
sha256sum /tmp/MCGUI-scripts.jar build/engine/MCGUI-scripts-<version>.jar
```

The two SHA-256 values must match the `sha256=` line in
`script-engine.properties`.

## Dev and release behavior

- Local builds embed the engine automatically.
- Dev workflow builds with `-PembedScripts=true`.
- Release builds do not embed by default. They produce
  `MCGUI-scripts-<version>.jar` in `build/engine/` and expect that exact jar at
  `https://cdn.resourcepack.ai/MCGUI-scripts-<version>.jar`.
- If a release fork uses its own CDN, update `CDN` in `ScriptEngine.java`.
- On release servers, upload the exact engine jar after every script-engine build.

## Manual script-engine fallback

Copy the matching engine into:

```text
plugins/MCGUI/libs/MCGUI-scripts-<version>.jar
```

Restart or reopen a scripted page. A mismatched SHA-256 is intentionally rejected.
Do not copy an engine jar made for a different plugin build or fork.

## Known baseline test

Before shipping, run `sh gradlew test`. This repository currently has a known baseline
failure in:

```text
OverlayTest.hoverRepaintsOfAnOverlayMatchAFreshPicture
```

The failure compares a partial repaint with a fresh overlay and differs by 4 pixels.
It was present before the standing-view change. Record 284/286 test totals only if
the newly added tests change the count.

The JavaScript test can be timing-sensitive in a full suite on a loaded machine.
Always rerun `modernJavaScriptIsConverted` in isolation; it passed in isolation
after the embedded-engine fix.

## Paper test

With a prepared server directory and JDK 25:

```bash
sh gradlew paperTest -PpaperServer=/path/to/server
```
