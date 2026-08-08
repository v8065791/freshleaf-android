# FreshLeaf

FreshLeaf is an Android RSS reader for FreshRSS. It uses FreshRSS's Google Reader-compatible API, caches feeds and articles locally, and keeps remote categories/tags separate from local folder organization.

## FreshRSS setup

1. Enable **Allow API access** in FreshRSS.
2. Set a dedicated **API password** for the account.
3. Enter the FreshRSS server URL, username, and API password in FreshLeaf. The app accepts either the server root or the complete `/api/greader.php` URL.

### Tailscale / MagicDNS servers

For a FreshRSS server with a `.ts.net` name, install and connect the official Android Tailscale app before signing in. FreshLeaf uses that existing VPN; it does not embed Tailscale or create another VPN connection.

MagicDNS lookups and HTTPS sockets are bound to the active Android VPN, so both go through the same Tailscale tunnel. In Tailscale's app-based split-tunneling settings, ensure **FreshLeaf** is not excluded. The FreshLeaf account screen offers **Open Tailscale** when the app is installed.

FreshLeaf accepts HTTPS endpoints only. System and user-installed certificate authorities remain trusted, which supports private PKI deployments without weakening HTTPS.

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
