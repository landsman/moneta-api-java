# moneta-api-spring-boot-starter

Spring Boot auto-configuration for the client: add the dependency, set `moneta.*`, inject `MonetaClient`.
Works on Spring Boot 3.5 and 4; [testing/spring-boot](../../testing/README.md) checks each.

```yaml
moneta:
  token: ${MONETA_TOKEN}
```

That is the whole setup for an application reading one account: inject `MonetaClient` and `AccessToken`. One
serving many merchants leaves `moneta.token` unset and passes each one's token with the call. Keep the token in
the environment, never in a committed file.

A `MonetaClient` bean of the application's own replaces the auto-configured one. A token the client would refuse
stops the application at startup, not at the first call. IntelliJ completes the properties from the metadata the
starter ships.

---

## Optional properties

None is needed against production.

| Property | Default | |
|----------|---------|---|
| `moneta.base-uri` | production, `MonetaClient.PRODUCTION` | the sandbox's host once the portal gives it |
| `moneta.request-timeout` | the client's, 30 s | one call to MONETA |
| `moneta.partner.identification-number`, `.business-name`, `.application-name` | — | the registered application's headers, all three or none, ASCII only |

MONETA does not ask for the partner headers: every call so far went through without ([how it
behaves](../../.docs/moneta-api.md)). Likely for an application approved on the developer portal; set them once the
portal says so.
