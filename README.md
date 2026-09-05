# Spond Sync

A cli utility to synchronise events from various sources to [Spond](https://spond.com).

Implemented sources:

- [SportPress](https://wordpress.org/plugins/sportspress)
- [VolleyZone](https://competitions.volleyzone.co.uk)

## Configuration

All configurations are defined in [Config](lib/runtime/src/config/Config.kt).

Configs can be split into multiple files and merged together using [ConfigMerger](./lib/config/src/ConfigMerger.kt). CLI
app also accepts multiple config files for merging.

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
      "Spond subgroup name": {
        "team": "Source team name",
        "sources": [
          "League name"
        ],
        // Optional - event mode override. If not set default mode from events config will be used.
        "mode": "triangles",
        // Optional - event hosts for matches
        "hosts": [
          "spond.user@email.com"
        ],
        // Optional
        "events": {
          // "triangles" or "matches"
          "mode": "triangles",
          "opponentColourHex": "#FFFFFF",
          "invitationDayBeforeStart": 6,
          "rsvpDeadlineBeforeStart": 2,
          "maxAccepted": 10,
          "descriptionByline": "Generated event."
        },
      },
    },
    // Optional
    "syncResults": true,
    // Optional
    "forceUpdate": false,
    "api": {
      "username": "obfuscated",
      "password": "obfuscated",
      // Optional
      "apiUrl": "https://api.spond.com",
    },
  }
}
```

### Github Actions

Setup `SYNC_CONFIG` secret with the contents of your json config file. Optionally, you can split the credentials into a
separate `CRED_CONFIG` secret. This makes it easier to update config without having to resubmit credentials each update.
There is also a `LEAGUE_CONFIG` secret that can be used to override league addresses.
Once ready, run `Build` action once and wait for it to complete. Then run `Sync Manual` action to sync events and verify
your configuration. After that you can enable `Sync Schedule` action to have events syncing continuosly.

## References

- [Spond API](https://api.spond.com/core/v1)
- [Sportpress API](https://<wordpress_host>/wp-json/sportspress/v2)
