# Sportpress to Spond

A cli utility to synchronise [Sportpress](https://wordpress.org/plugins/sportspress) events
to [Spond](https://spond.com)

## Configuration

```json5
{
  "sportpress": {
    "club": "My Club Name",
    "apiUrl": "https://<wp-host>/wp-json/sportspress/v2"
  },
  "spond": {
    "group": "My Group Name",
    "username": "obfuscated",
    "password": "obfuscated",
    // Optional
    "apiUrl": "https://api.spond.com/core/v1"
  },
  "teams": {
    "Source team": "Spond subgroup"
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
