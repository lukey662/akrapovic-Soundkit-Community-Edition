# iOS distribution preparation

Sound Kit Community has no TestFlight or App Store release at this time. This
guide prepares an owner-controlled distribution; it does not claim that Apple
has accepted the app or that testing is complete.

## Current blockers

- An authorized Apple Developer Account holder must create signing,
  provisioning, and App Store Connect records. Repository access alone is not
  sufficient.
- A physical iPhone and compatible Sound Kit receiver must complete the
  parked BLE smoke test in `TESTING.md`. The Simulator cannot validate
  CoreBluetooth, receiver readiness, background restoration, or Siri command
  behavior.
- CarPlay remains disabled. It needs separate Apple approval for
  `com.apple.developer.carplay-driving-task`; this is not a TestFlight
  prerequisite for the Siri and phone-only fallback.

## Ad Hoc preparation

Use Ad Hoc only for identified testers, not public distribution:

1. An Apple Developer Account holder registers each tester device UDID and
   creates an Ad Hoc provisioning profile for the app identifier.
2. In Xcode, select the distribution team and archive the
   `SoundKitCommunity` scheme.
3. Distribute the archive as **Ad Hoc** using the matching profile, then
   provide it only to the registered testers through an approved private
   channel.
4. Each tester runs the physical-iPhone and receiver smoke checks, records
   device/OS/build results, and exports diagnostics only with the user's
   review. Do not circulate receiver identifiers or personal data in public
   issue reports.

## TestFlight preparation

Before an Apple Developer Account holder uploads a build:

1. Increment the iOS build number and archive a Release build signed by the
   authorized team.
2. Complete the physical iPhone/receiver smoke, including Siri Open, Close,
   and Status with safe failure when BLE is not ready.
3. Prepare App Store Connect metadata, review notes, support contact, and
   demo instructions. Describe valve controls accurately and include the
   parked-only safety constraints.
4. Complete App Store Connect privacy disclosures using the committed
   [`PrivacyInfo.xcprivacy`](ios/SoundKitCommunity/PrivacyInfo.xcprivacy) as
   the code-level privacy manifest reference. Re-check the answers against
   the actual release build and third-party SDKs; the manifest alone does not
   submit App Store Connect answers.
5. Upload the archive to App Store Connect and wait for Apple processing and
   any required review. Do not announce TestFlight availability until the
   build is visible to its intended tester group.

## CarPlay and Siri distribution boundary

Keep `CARPLAY_ENABLED=NO`, the CarPlay scene declaration absent, and the
driving-task entitlement absent unless Apple has approved the entitlement and
the selected provisioning profile contains it. The entitlement request package
and approval path are documented in `DOCS.md`.

Until then, distribute only the phone UI and Siri App Intents. Siri commands
must still require a ready receiver and notification-confirmed result; a
locked or backgrounded phone that cannot restore BLE readiness must fail safely
and direct the user to unlock and open the app.
