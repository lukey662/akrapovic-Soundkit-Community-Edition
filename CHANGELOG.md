# Changelog

Android releases of Car Sound Kit Control. Store text lives in `play/listing/en-US/`.

## 0.3.4 (versionCode 7) — Play internal testing

### Fixed

- Tapping the Quick Settings tile while disconnected no longer crashes on Android 14+; the tile no longer crashes on Android 8 and 9.
- The connection service no longer crashes when started while the “Left the car” card is showing, or before Bluetooth permission is granted (including on the Android Auto “Allow Bluetooth on phone” screen).
- After a missed confirmation, the app re-checks the valve position before allowing another tap, so a retry cannot reverse the valve.
- A BLE reconnect no longer reopens valves you closed; drive mode applies once per drive.
- Open valves, Close valves, and Valve status launcher shortcuts now launch.
- Shortcuts wait for the receiver's first status instead of failing right after connecting.
- The home-screen widget clears its live valve state when the service stops.

### Changed

- Android Auto shows one stable screen (Status, Open, Close) with the title Car Sound Kit Control. Open and Close work only while parked; Status offers Retry when disconnected.
- The saved car Bluetooth address is excluded from Android backup and device transfer.
- Store listing wording now leads with “independent, unofficial” and says the app is not made by Akrapovič.

## 0.3.3 (versionCode 6) — Play internal testing

### Fixed

- Accepts the Android Auto host certificate installed on the Pixel (Android Auto 17.8.663874), fixing the unexpected error when opening the app on the car display. Release builds still allow only the hosts in `car_app_hosts.xml`.
