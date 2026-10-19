You are releasing Car Sound Kit Control (package com.akrapovic.soundkit.community) to the Google Play Console INTERNAL TESTING track only. It is an independent app that controls a Car Sound Kit BLE exhaust-valve receiver; it is not an Akrapovič product.

SOURCE
- Branch main (confirm with: git log --oneline -3 — the recent commits mention 0.3.8). Tag v0.3.8 marks the release commit.
- Phone: versionName 0.3.8, versionCode 11. Wear OS: versionName 0.3.8, versionCode 1000011, same package. Play already holds codes 10 and 1000010 from 0.3.7; do not reuse them.

WHAT CHANGED IN 0.3.8 AND WHY
- Android Auto: Open and Close now work while driving (they act in the car and do not send the driver to the phone). The car screen is one PaneTemplate: valve art, receiver name, a live status line, and two large buttons (none while connecting, one Connect button when the link is down).
- The car valve image uses the same detailed art as the phone.
- Safety copy in onboarding and the store listing now says to change the valves only when safe and legal, using the Android Auto buttons while driving. Update the full description from play/listing/en-US/full-description.txt.

BUILD
1. Set ANDROID_RELEASE_STORE_FILE, ANDROID_RELEASE_STORE_PASSWORD, ANDROID_RELEASE_KEY_ALIAS and ANDROID_RELEASE_KEY_PASSWORD in the shell environment only. Never write them to gradle.properties, local.properties, any tracked file, logs or commit messages. The phone and watch must be signed with the same upload key.
2. Run: ./gradlew :app:testDebugUnitTest :app:verifyPaparazziDebug :app:bundleRelease :wear:bundleRelease
   Expect all unit tests to pass with 0 failures. Stop and report if anything fails.
3. Artifacts: app/build/outputs/bundle/release/app-release.aab and wear/build/outputs/bundle/release/wear-release.aab.

PLAY CONSOLE STEPS
1. Testing → Internal testing → Create new release named "0.3.8 (11)". Upload app-release.aab. Upload wear-release.aab to the Wear OS internal testing track (Play rejects it on the phone release because it requires android.hardware.type.watch, as happened for 0.3.6). Leave the Wear track's tester list as it is.
2. Release notes (en-US): copy play/listing/en-US/release-notes.txt exactly.
3. Roll out to internal testing only; keep the existing tester list. Tell the owner to update via https://play.google.com/apps/internaltest/4699579818900841316 on the phone signed in as a tester.
4. Main store listing (en-US): apply play/listing/en-US/full-description.txt exactly (its safety wording changed). Data safety and the hosted privacy policy are unchanged. Do not send anything for production review.

HARD CONSTRAINTS (do not violate)
- Release builds validate Android Auto hosts only against app/src/main/res/values/car_app_hosts.xml (7 digests). Never use HostValidator.ALLOW_ALL_HOSTS_VALIDATOR or the AndroidX sample allowlist in release. Do not edit car_app_hosts.xml.
- No production release: no production, open-testing or closed-testing release, and do not promote this release out of internal testing.
- No public Android Auto: do not opt in to Android Auto distribution or submit for Android Auto/Automotive review.
- Do not commit signing material, service-account keys, or Play Console credentials to the repo.

REPORT BACK
- Internal release status, the version codes Play shows for phone (11) and watch (1000011), any pre-launch report warnings, and whether the Wear bundle was accepted on the same release.
