package example.cafe;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** A café checking which payments reached its MONETA account. */
@OpenAPIDefinition(
        info =
                @Info(
                        title = "Café payments",
                        version = "example",
                        description = "Issue a bill as a QR payment, then find it paid among the incoming payments"
                                + " on the café's MONETA account. Calls the real MONETA API with MONETA_TOKEN."),
        // In the order a café works: issue a bill, then look at what came in.
        tags = {
            @Tag(name = "Bills", description = "Bills the café issues, and whether they are paid"),
            @Tag(name = "Payments", description = "Incoming payments on the café's account, by period or range")
        })
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class CafeApplication {

    public static void main(String[] args) {
        SpringApplication.run(CafeApplication.class, args);
    }
}
