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
  "noGo": [
    {"label": "Springvale 西", "south": -38.0, "west": 145.1, "north": -37.93, "east": 145.14}
  ]
}
```

## How a card is judged

In order. The first rule that says no, wins, and its reason is what the chip
shows.

| Rule | Says no when |
|---|---|
| `stores.deny` | the pickup contains any denied name |
| `roads` | the dropoff names a lane or a highway, and the switch is on |
| `noGo` | the pickup shop, or a dropoff placed by its street, is inside a box |
| `suburbs.deny` | the dropoff's suburb is listed |
| `suburbs.allow` | the dropoff's suburb is not listed (when the list is non-empty) |

Nothing about money. The driver reads the payout off the card himself.

There is no radius rule. A radius needs the dropoff's coordinates, and those
come from recognising its suburb - so anything a radius could decide, the
suburb list already decides, and running both would just mean the stricter one
wins while the other looked like it mattered. The circle survives in the editor
as a drawing tool: drop a centre, drag a radius, fill the suburbs inside it in
one click, then adjust by hand. Only the resulting list is saved.

## No-go boxes

Rectangles dragged on the editor's map, edges due north-south and east-west.
The pickup counts when its shop is in the bundled shop table; the dropoff counts
when the card names a street the road table knows. A dropoff placed only by its
suburb is left to the suburb list: the middle of a suburb is kilometres from the
door, too rough to put on one side of a box's edge.
