# Release Checklist

This project is distributed as:

- a raw `install.sh` bootstrapper for host setup;
- a GitHub Release APK asset for Android installation.

## Version

Update the Android version in `app/build.gradle.kts`:

```kotlin
versionCode = 1
versionName = "0.0.1"
```

## Build Locally

```bash
./scripts/build-android-apk
```

The APK is copied to:

```text
dist/bclaw-android-debug.apk
```

The `0.0.1` APK is debug-signed and intended as a preview build.

## Verify

```bash
./gradlew :app:testDebugUnitTest
cd host-agent && npm ci --ignore-scripts && node --check server.js
```

## Publish

Create and push a tag:

```bash
git tag v0.0.1
git push origin v0.0.1
```

The `android-apk` GitHub Actions workflow builds the APK and attaches it to the GitHub Release as:

```text
bclaw-android-debug.apk
```

The public install command always points at `main`:

```bash
curl -fsSL https://raw.githubusercontent.com/boomyao/bclaw/main/install.sh | bash
```

To install a specific release:

```bash
curl -fsSL https://raw.githubusercontent.com/boomyao/bclaw/main/install.sh | bash -s -- --ref v0.0.1
```
