# rules.json

The whole decision, in one file. Edited on the Mac, pushed to the phone with
`adb push`, read by the app. Nothing in it is invented by the app.

```json
{
  "version": 1,
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

Nothing about money. The driver reads the payout off the card himself.

There is no radius rule. A radius needs the dropoff's coordinates, and those
come from recognising its suburb - so anything a radius could decide, the
suburb list already decides, and running both would just mean the stricter one
wins while the other looked like it mattered. The circle survives in the editor
as a drawing tool: drop a centre, drag a radius, fill the suburbs inside it in
one click, then adjust by hand. Only the resulting list is saved.
