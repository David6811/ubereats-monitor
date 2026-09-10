# rules.json

The whole decision, in one file. Edited on the Mac, pushed to the phone with
`adb push`, read by the app. Nothing in it is invented by the app.

```json
{
  "version": 1,
  "anchor": {
    "lat": -37.9695,
    "lon": 145.1767,
    "maxKm": 15.0,
    "label": "家"
  },
  "suburbs": {
    "allow": ["Noble Park", "Keysborough", "..."],
    "deny":  ["Heidelberg West"]
  },
  "stores": {
    "deny": ["Westfield Southland", "Chadstone"]
  },
  "addresses": {
    "deny": ["Cnr Springvale and Cheltenham Rds"]
  }
}
```

## How a card is judged

In order. The first rule that says no, wins, and its reason is what the chip
shows.

| Rule | Says no when |
|---|---|
| `stores.deny` | the pickup contains any denied name |
| `addresses.deny` | the dropoff contains any denied fragment |
| `suburbs.deny` | the dropoff's suburb is listed |
| `suburbs.allow` | the dropoff's suburb is not listed (when the list is non-empty) |
| `anchor.maxKm` | the dropoff is further than `maxKm` from the anchor |

Nothing about money. The driver reads the payout off the card himself.
