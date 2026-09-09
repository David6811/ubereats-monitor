# UberEatsMonitor

Reads Uber Driver's offer notification, scores it against your own bar, and drops a
big Accept / Decline card on top of the screen before the timer runs out.

## How it is built

Three layers, no framework in the middle two.

| Layer | Package | Contains |
|---|---|---|
| Data | `domain` | `Offer`, `Thresholds`, `Verdict`, `ParseResult` — all immutable, all sum/product types |
| Calculation | `domain` | `OfferParser`, `OfferEvaluator`, `VerdictText` — pure, no Android import, fully unit tested |
| Action | `action`, `ui` | `OfferListenerService`, `OverlayController`, `SettingsStore`, `OfferLog`, Compose screen |

`Verdict` has three cases, not two. A missing distance is `Uncertain`, never a silent pass.

## Setup on the phone

1. Install: `./gradlew installDebug`
2. Open the app, grant **Notification access** and **Display over other apps**.
3. Set your bar: min payout, min $/mi, min $/h, max miles.

## Known limits — read this before trusting it

- **Uber has no public API.** The only data source is the notification Uber Driver posts.
  When the driver app is in the foreground, Android may deliver no notification at all,
  so the overlay will not fire on every offer.
- **The parser is regex over English notification text.** Uber changes wording between
  releases. Turn on *record all notifications* in the app, take a few real offers, and
  read the raw text in the log; then adjust the regexes in `OfferParser.kt` and the
  fixtures in `OfferParserTest.kt`.
- **It never touches the Uber app.** No auto-accept, no tapping, no scraping the screen.
  It reads a notification and draws a card. The decision stays yours.

## Tests

```
./gradlew testDebugUnitTest
```

18 tests over the parser and the evaluator, all with fixed data.
