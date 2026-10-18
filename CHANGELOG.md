# Changelog

Android releases of Car Sound Kit Control. Store text lives in `play/listing/en-US/`.

## 0.3.6 (versionCode 9) — Play internal testing

### Fixed

- Home while the receiver is not connected now uses the same design as the connected screen (header with receiver chip, dimmed valve, one Connect action, Drive mode / Quiet start / Receiver rows); the old "Connect" screen and the duplicate "Sound Kit" bar above Home are gone.
- Android Auto tiles send small, immutable valve images (56dp, at most 144px) and cache them per theme, keeping every refresh well under the binder transaction budget. The car screen never throws out of `onGetTemplate`: a failure shows a message template and is recorded.
- Diagnostics export gains a "LAST CAR ERROR" section and the car host package and API level, so a head-unit failure can be read from the phone.

The watch build is 1000009.

## 0.3.5 (versionCode 8) — Play internal testing

Republish of the 0.3.4 feature set. Play already had version code 7 from an earlier internal build, so the phone could not receive this binary until the version code moved to 8. The watch build is 1000008.

## 0.3.4 (versionCode 7) — Play internal testing

### Fixed

- Tapping the Quick Settings tile while disconnected no longer crashes on Android 14+; the tile no longer crashes on Android 8 and 9.
- The connection service no longer crashes when started while the “Left the car” card is showing, or before Bluetooth permission is granted (including on the Android Auto “Allow Bluetooth on phone” screen).
- After a missed confirmation, the app re-checks the valve position before allowing another tap, so a retry cannot reverse the valve.
- A BLE reconnect no longer reopens valves you closed; drive mode applies once per drive.
- Open valves, Close valves, and Valve status launcher shortcuts now launch.
- Shortcuts wait for the receiver's first status instead of failing right after connecting.
- The home-screen widget clears its live valve state when the service stops, and now refreshes while connected (its update flow was never collected).
- Bluetooth callbacks run on the main thread, removing a race that could report a false "did not confirm".
- Notification errors use plain language and offer Retry.
- Failed commands explain the cause; an unconfirmed toggle reads as "checking".

### Added

- New flat black and white design with brand-accurate garage themes and a smaller type scale.
- Detailed exhaust-tip valve art, also used for Android Auto tiles and the widget.
- Activity: every valve change with its source.
- Theme suggestion from the car's Bluetooth name.
- "Wake when the receiver is nearby" (Android 12+ companion pairing).
- Quiet-start progress countdown on Android 16.
- Wear OS tile that shows the valves and changes them through the phone (watch build 1000007).

### Changed

- Android Auto shows one stable screen (Status, Open, Close) with the title Car Sound Kit Control. Open and Close work only while parked; Status offers Retry when disconnected.
- The saved car Bluetooth address is excluded from Android backup and device transfer.
- Store listing wording now leads with “independent, unofficial” and says the app is not made by Akrapovič.

## 0.3.3 (versionCode 6) — Play internal testing

### Fixed

- Accepts the Android Auto host certificate installed on the Pixel (Android Auto 17.8.663874), fixing the unexpected error when opening the app on the car display. Release builds still allow only the hosts in `car_app_hosts.xml`.
