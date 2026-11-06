# moneta-api-spring-boot-starter

Spring Boot auto-configuration for the client: add the dependency, set `moneta.*`, inject `MonetaClient`.
Works on Spring Boot 3.5 and 4; [testing/spring-boot](../testing/README.md) checks each.

| Property | Default | |
|----------|---------|---|
| `moneta.token` | — | one account's token; set it and an `AccessToken` bean appears. Leave it unset when the app serves many merchants and passes each one's token with the call. From the environment only. |
| `moneta.base-uri` | `https://api.moneta.cz/` | the sandbox's host once the portal gives it |
| `moneta.request-timeout` | `30s` | one call to MONETA |
| `moneta.partner.identification-number`, `.business-name`, `.application-name` | — | the registered application's headers, all three or none, ASCII only |

```yaml
moneta:
  token: ${MONETA_TOKEN}
```

A `MonetaClient` bean of the application's own replaces the auto-configured one. A token or partner value the
client would refuse stops the application at startup, not at the first call. IntelliJ completes the properties
from the metadata the starter ships.
