# FreshLeaf

FreshLeaf is an Android RSS reader for FreshRSS. It uses FreshRSS's Google Reader-compatible API, caches feeds and articles locally, and keeps remote categories/tags separate from local folder organization.

## FreshRSS setup

1. Enable **Allow API access** in FreshRSS.
2. Set a dedicated **API password** for the account.
3. Enter the FreshRSS server URL, username, and API password in FreshLeaf. The app accepts either the server root or the complete `/api/greader.php` URL.

## Build in the Arch environment

```sh
unset LD_PRELOAD
export ANDROID_HOME=/home/s/Android/Sdk
export ANDROID_SDK_ROOT=/home/s/Android/Sdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

The release signing key is intentionally not checked into this repository. Copy `keystore.properties.example` to `keystore.properties`, create the referenced private keystore, then run `./gradlew :app:assembleRelease` before publishing.
