# moneta-api-spring-boot-starter

Spring Boot auto-configuration for the client: add the dependency, set `moneta.*`, inject `MonetaClient`.
Works on Spring Boot 3.5 and 4; [testing/spring-boot](../../testing/README.md) checks each.

## Install

```kotlin
// build.gradle.kts
dependencies {
    implementation("cz.insuit:moneta-api-spring-boot-starter:0.2.0")
}
```

<details><summary>Maven</summary>

```xml
<dependency>
    <groupId>cz.insuit</groupId>
    <artifactId>moneta-api-spring-boot-starter</artifactId>
    <version>0.2.0</version>
</dependency>
```

</details>

GitHub Packages asks for a token even for a public package: the repository block and the token are in
[Installing](../../.docs/install.md).

## Configure

```yaml
moneta:
  token: ${MONETA_TOKEN}
```

That is the whole setup for an application reading one account: inject `MonetaClient` and `AccessToken`. One
serving many merchants leaves `moneta.token` unset and passes each one's token with the call. Keep the token in
the environment, never in a committed file.

```java
@Service
class Payments {
    private final AccountHolder me;

    Payments(MonetaClient moneta, AccessToken token) {
        this.me = moneta.with(token);
    }

    List<Transaction> paidToday() {
        var today = LocalDate.now(ZoneId.of("Europe/Prague"));
        return me.transactions().between(today, today).incoming().list();
    }
}
```

<details><summary>Kotlin</summary>

```kotlin
@Service
class Payments(moneta: MonetaClient, token: AccessToken) {
    private val me = moneta.with(token)

    fun paidToday(): List<Transaction> {
        val today = LocalDate.now(ZoneId.of("Europe/Prague"))
        return me.transactions().between(today, today).incoming().list()
    }
}
```

</details>

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
