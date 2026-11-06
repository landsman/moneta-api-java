package example.cafe;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class CafeConfiguration {

    /** The clock "today" is read from; a test can hand the controller a fixed one instead. */
    @Bean
    Clock clock(CafeProperties properties) {
        return Clock.system(properties.timeZone());
    }
}
