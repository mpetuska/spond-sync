# Spond Sync

A cli utility to synchronise events from various sources to [Spond](https://spond.com).

Implemented sources:

- [SportPress](https://wordpress.org/plugins/sportspress)
- [VolleyZone](https://competitions.volleyzone.co.uk)

## Configuration

All configurations are defined in [Config](./lib/config/src/Config.kt).

Configs can be split into multiple files and merged together using [ConfigMerger](./lib/config/src/ConfigMerger.kt).
CLI app also accepts multiple config files for merging.

```json5
{
  "volleyzone": {
    "leagues": {
      "League name": "https://competitions.volleyzone.co.uk/league/000000",
    },
    // Optional
    "addresses": {
      "VZ Address": "Full Address, City, POSTCODE, Country",
    }
  },
  "spond": {
    "group": "My Group Name",
    "subGroups": {
      "Spond subgroup name": "Source team name",
    },
    // Optional
    "syncResults": true,
    // Optional
    "forceUpdate": false,
    "api": {
      "username": "obfuscated",
      "password": "obfuscated",
      // Optional
      "apiUrl": "https://api.spond.com/core/v1",
    },
    // Optional
    "events": {
      "opponentColourHex": "#FFFFFF",
      "invitationDayBeforeStart": 6,
      "rsvpDeadlineBeforeStart": 2,
      "maxAccepted": 10,
      "descriptionByline": "Generated event."
    },
  }
}
```

## References

- [Spond API](https://api.spond.com/core/v1)
- [Sportpress API](https://<wordpress_host>/wp-json/sportspress/v2)
