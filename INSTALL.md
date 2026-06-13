# Install Sound Kit Community (Android)

Plain-language guide for Akrapovič Sound Kit owners. No developer tools required.

## What you need

- An Android phone running **Android 8.0 or newer** (Android 12+ recommended)
- The **Car Sound Kit** Bluetooth receiver installed in your vehicle (not the motorcycle Sound Kit Custom)
- A few minutes to grant Bluetooth permissions during first launch

## Download the app

1. Open the latest **[GitHub Release](https://github.com/lukey662/akrapovic-Soundkit-Community-Edition/releases/latest)** for this project.
2. Download **`app-debug.apk`** (or the release APK when signed builds are published).
3. If you do not see a release yet, ask the maintainer for the latest CI build artifact or use the link on [appsforgood.net](https://appsforgood.net).

## Install the APK

1. Open the downloaded file on your phone.
2. If Android warns about **unknown apps**, allow installs from your browser or Files app (Settings → Security → Install unknown apps).
3. Tap **Install**, then **Open**.

## First launch

1. Read the **risk notice** and accept it — the app will not work until you do.
2. Choose your **vehicle** (Audi RS3 is fully supported; other platforms are Beta).
3. Grant **Bluetooth** permissions when asked (Nearby devices / Bluetooth on Android 12+).
4. Optionally allow **notifications** so the app can stay connected in the background.
5. On the **Home** tab, tap **Scan nearby**, select your Sound Kit receiver, and enter the **PIN** shown on the receiver if prompted.

## Daily use

- **Home** — open/close valves when parked (never while driving).
- **More → Settings** — saved receivers, auto-reconnect, drive mode.
- **More → Appearance** — garage themes.
- **More → Advanced → Diagnostics** — export logs and email **support@appsforgood.net** if something fails.

## Troubleshooting

| Problem | What to try |
|--------|-------------|
| No receivers in scan | Stand near the car with ignition/accessory on; confirm Bluetooth is on; grant all Bluetooth permissions. |
| Connect fails | Power-cycle the receiver; forget the device in Android Bluetooth settings and scan again. |
| Valves do not move | Wait until status shows Open/Closed; only toggle when parked. Export diagnostics and email support. |

## Two phones in one car

If you and a partner both have the app:

1. Leave **Head unit priority** on (Settings → Connection).
2. Use **Android Auto on the driver’s phone** — that phone becomes primary and auto-connects.
3. The passenger phone stays idle unless they tap **Take control** on Home (confirms it may disconnect the other phone).
4. Turn off head-unit priority on a phone only if you want the old “both phones race to connect” behavior.

On **iOS** (dev builds), contention yield and Take control work; full driver priority requires a future CarPlay session hook.

## iOS

An iPhone companion (**dev v1**) is in the repository for **developers with Xcode and a registered device** — it is **not available for owners** yet (no TestFlight or App Store build).

1. See **`ios/SoundKitCommunity/README.md`** for open → sign → run on device.
2. Physical receiver testing checklist: **`TESTING.md`** § iOS device smoke.

Public owner install will follow RS3 hardware validation; see `ROADMAP.md`.

## Disclaimer

Sound Kit Community is an **independent open-source project**, not affiliated with Akrapovič. Use at your own risk. See the in-app notice and `README.md` for full terms.
