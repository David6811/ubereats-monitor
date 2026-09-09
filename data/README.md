# stores.db

SQLite. Open it with `sqlite3 data/stores.db`, or any GUI (DB Browser for
SQLite, TablePlus, DataGrip).

## Tables

`store` — 1042 food outlets and shops inside the service area.

| column | meaning |
|---|---|
| `name`, `lat`, `lon` | from OpenStreetMap |
| `kind` | restaurant, fast_food, cafe, supermarket, convenience, … |
| `setting` | MALL / STRIP / STANDALONE_PARKING / STANDALONE |
| `parking_m` | metres to the nearest mapped car park, -1 when none was found |
| `parking_type` | surface, street_side, underground, multi-storey |
| `neighbours_60m` | other outlets within 60 m — the STRIP signal |
| `mall` | the shopping centre it sits in, else NULL |
| `suburb` | nearest of the 32 service-area suburbs |
| `blacklisted` | 0 or 1 — yours to set |
| `note` | yours to write |

`suburb` — 714 Greater Melbourne place names; `in_service_area = 1` for the 32.

## Views

`v_hard_parking`, `v_easy_parking`, `v_mall_size`, `v_blacklist`.

## Blacklisting

```sql
-- a whole shopping centre
UPDATE store SET blacklisted = 1, note = 'car park is a maze'
WHERE mall = 'Westfield Southland';

-- one store
UPDATE store SET blacklisted = 1, note = 'no legal stop out front'
WHERE name = 'Nando''s' AND suburb = 'Dandenong';

-- everything with only street parking
UPDATE store SET blacklisted = 1 WHERE parking_type = 'street_side';

SELECT * FROM v_blacklist;
```

## Caveats

- OSM is not the Uber Eats catalogue. This is a superset of food outlets; some
  are not on the platform, and new stores or ghost kitchens may be missing.
- `STANDALONE` means OSM has no car park nearby, not that parking is bad.
- The STRIP threshold (6 outlets within 60 m) is a guess. Change it and the
  table can be rebuilt.
