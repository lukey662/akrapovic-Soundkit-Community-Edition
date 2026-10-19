You are releasing Car Sound Kit Control (package com.akrapovic.soundkit.community) to the Google Play Console INTERNAL TESTING track only. It is an independent app that controls a Car Sound Kit BLE exhaust-valve receiver; it is not an Akrapovič product.

SOURCE
- Branch main (confirm with: git log --oneline -3 — the recent commits mention 0.3.7). Tag v0.3.7 marks the release commit.
- Phone: versionName 0.3.7, versionCode 10. Wear OS: versionName 0.3.7, versionCode 1000010, same package. Play already holds codes 9 and 1000009 from 0.3.6; do not reuse them.

WHAT CHANGED IN 0.3.7 AND WHY
- Fixes the Android Auto "has encountered an unexpected error" screen: the androidx.car.app.minCarApiLevel meta-data now sits on <application>, where the Car App Library reads it (it was on the service, so the host failed in CarAppService.getAppInfo before any screen existed).
- From 0.3.6:
- The Home screen now matches the new design when the receiver is not connected (the 0.3.5 build still showed the old "Connect" screen there), and Home no longer has a duplicate top bar.
- Android Auto: tile images are small immutable bitmaps cached per theme, and the car screen never throws out of onGetTemplate; a failure shows a message and is recorded so the phone's Export diagnostics includes it ("LAST CAR ERROR"). This targets the "has encountered an unexpected error" screen seen on 0.3.5.
- Everything from 0.3.4/0.3.5 is unchanged: black design with brand garage themes, exhaust-tip valve art, Activity history, theme suggestion, "Wake when the receiver is nearby", quiet-start countdown, Wear OS tile.

BUILD
1. Set ANDROID_RELEASE_STORE_FILE, ANDROID_RELEASE_STORE_PASSWORD, ANDROID_RELEASE_KEY_ALIAS and ANDROID_RELEASE_KEY_PASSWORD in the shell environment only. Never write them to gradle.properties, local.properties, any tracked file, logs or commit messages. The phone and watch must be signed with the same upload key.
2. Run: ./gradlew :app:testDebugUnitTest :app:verifyPaparazziDebug :app:bundleRelease :wear:bundleRelease
   Expect all unit tests to pass with 0 failures. Stop and report if anything fails.
3. Artifacts: app/build/outputs/bundle/release/app-release.aab and wear/build/outputs/bundle/release/wear-release.aab.

PLAY CONSOLE STEPS
1. Testing → Internal testing → Create new release named "0.3.7 (10)". Upload app-release.aab. Upload wear-release.aab to the Wear OS internal testing track (Play rejects it on the phone release because it requires android.hardware.type.watch, as happened for 0.3.6). Leave the Wear track's tester list as it is.
2. Release notes (en-US): copy play/listing/en-US/release-notes.txt exactly.
3. Roll out to internal testing only; keep the existing tester list. Tell the owner to update via https://play.google.com/apps/internaltest/4699579818900841316 on the phone signed in as a tester.
4. Store listing, Data safety and the hosted privacy policy are unchanged since 0.3.5; do not send anything for production review.

HARD CONSTRAINTS (do not violate)
- Release builds validate Android Auto hosts only against app/src/main/res/values/car_app_hosts.xml (7 digests). Never use HostValidator.ALLOW_ALL_HOSTS_VALIDATOR or the AndroidX sample allowlist in release. Do not edit car_app_hosts.xml.
- No production release: no production, open-testing or closed-testing release, and do not promote this release out of internal testing.
- No public Android Auto: do not opt in to Android Auto distribution or submit for Android Auto/Automotive review.
- Do not commit signing material, service-account keys, or Play Console credentials to the repo.

REPORT BACK
- Internal release status, the version codes Play shows for phone (10) and watch (1000010), any pre-launch report warnings, and whether the Wear bundle was accepted on the same release.
