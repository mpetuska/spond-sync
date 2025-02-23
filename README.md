# Spond Sync

A cli utility to synchronise events from various sources
to [Spond](https://spond.com).

Implemented sources:

- [Sportpress](https://wordpress.org/plugins/sportspress)
- [VolleyZone](https://competitions.volleyzone.co.uk)

## Configuration

All configuration is defined in [WorkerConfig](./worker/src/main/kotlin/WorkerConfig.kt)

```json5
{
  "spond": {
    "group": "My Group Name",
    "username": "obfuscated",
    "password": "obfuscated",
    // Optional
    "apiUrl": "https://api.spond.com/core/v1",
    // Optional
    "opponentColourHex": "#FFFFFF",
    // Optional
    "invitationDayBeforeStart": 6,
    // Optional
    "rsvpDeadlineBeforeStart": 2,
    // Optional
    "maxAccepted": 10,
    // Optional
    "syncResults": true,
    // Optional
    "forceUpdate": false
  },
  "source": "volleyzone",
  "sportpress": {
    "club": "My Club Name",
    "apiUrl": "https://<wp-host>/wp-json/sportspress/v2"
  },
  "volleyzone": {
    "leagues": {
      "League name": "https://competitions.volleyzone.co.uk/league/000000"
    },
    // Optional
    "addresses": {
      "VZ Address": "Full Address, City, POSTCODE, Country"
    },
    // Optional
    "attemptToFixBrokenTriangles": false,
  },
  "teams": {
    "Source team name": "Spond subgroup name"
  },
  // Optional
  "yearOffset": 0,
  // Optional
  "debug": false,
}
```

## References

- [Spond API](https://api.spond.com/core/v1)
- [Sportpress API](https://<wordpress_host>/wp-json/sportspress/v2)
