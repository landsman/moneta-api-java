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
<!-- in <dependencies> -->
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
serving many account holders leaves `moneta.token` unset and passes each one's token with the call. Keep the token in
the environment, never in a committed file.

```java
import cz.insuit.moneta.AccessToken;
import cz.insuit.moneta.AccountHolder;
import cz.insuit.moneta.MonetaClient;
import cz.insuit.moneta.Transaction;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class Payments {
    private final AccountHolder me;

    public Payments(MonetaClient moneta, AccessToken token) {
        this.me = moneta.with(token);
    }

    /** Money received over the last week, booked. */
    public List<Transaction> receivedThisWeek() {
        var today = LocalDate.now(ZoneId.of("Europe/Prague"));
        return me.transactions().between(today.minusDays(7), today).incoming().booked().list();
    }
}
```

<details><summary>Kotlin</summary>

```kotlin
import cz.insuit.moneta.AccessToken
import cz.insuit.moneta.MonetaClient
import cz.insuit.moneta.Transaction
import java.time.LocalDate
import java.time.ZoneId
import org.springframework.stereotype.Service

@Service
class Payments(moneta: MonetaClient, token: AccessToken) {
    private val me = moneta.with(token)

    /** Money received over the last week, booked. */
    fun receivedThisWeek(): List<Transaction> {
        val today = LocalDate.now(ZoneId.of("Europe/Prague"))
        return me.transactions().between(today.minusDays(7), today).incoming().booked().list()
    }
}
```

</details>

It reads the only account the token opens and throws `IllegalStateException` when there are more; then, or with
one token per account holder, see [More than one account](../../.docs/usage.md#more-than-one-account).
Without `moneta.token` there is no `AccessToken` bean to inject.

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
| `moneta.partner.identification-number`, `.business-name`, `.application-name` | none | the registered application's headers, all three or none, ASCII only |

MONETA does not ask for the partner headers: every call so far went through without ([how it
behaves](../../.docs/moneta-api.md)). Likely for an application approved on the developer portal; set them once the
portal says so.
