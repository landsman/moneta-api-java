# moneta-api-micronaut

Micronaut wiring for the client: add the dependency, set `moneta.*`, inject `MonetaClient`. One jar for
Micronaut 4.10 and 5; [testing/micronaut](../../testing/README.md) checks each.

| Property | Default | |
|----------|---------|---|
| `moneta.token` | — | one account's token; set it and an `AccessToken` bean appears. Leave it unset when the app serves many merchants and passes each one's token with the call. From the environment only. |
| `moneta.base-uri` | production, `MonetaClient.PRODUCTION` | the sandbox's host once the portal gives it |
| `moneta.request-timeout` | the client's, 30 s | one call to MONETA |
| `moneta.partner.identification-number`, `.business-name`, `.application-name` | — | the registered application's headers, all three or none, ASCII only |

```yaml
moneta:
  token: ${MONETA_TOKEN}
```

The same names the Spring Boot starter reads, without `moneta.ssl.bundle`, which names a Spring Boot SSL bundle.
For mTLS, a proxy or a trust store, define an `HttpClientCustomizer` bean; an `HttpClientDecorator` bean wraps the
HTTP client, both in their `@Order`:

```java
@Singleton
HttpClientCustomizer monetaMtls(SSLContext moneta) {
    return http -> http.sslContext(moneta);
}
```

With `micrometer-java11` on the classpath and a `MeterRegistry` bean, every call is timed as
`http.client.requests`, tagged by `MonetaClient::uriTemplate`, as under the starter; a bean that `@Replaces` the
one named `monetaHttpClientObservation` changes it.

A `MonetaClient` bean of the application's own replaces the one built here. A token or partner value the client
would refuse stops the application at startup, not at the first call, and the client is closed with the context.

Not in a GraalVM native image yet: the client binds MONETA's JSON by reflection and ships no reachability
metadata ([#19](https://github.com/landsman/moneta-api-java/issues/19)).
