package example.play;

import cz.insuit.moneta.HttpClientCustomizer;
import cz.insuit.moneta.HttpClientDecorator;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Hooks named in moneta.http.*, recording into one list in the order the module applied them. */
final class Hooks {

    @Singleton
    static final class Calls {
        final List<String> names = new ArrayList<>();
    }

    static final class Proxy implements HttpClientCustomizer {
        private final Calls calls;

        @Inject
        Proxy(Calls calls) {
            this.calls = calls;
        }

        @Override
        public void customize(HttpClient.Builder builder) {
            calls.names.add("customize");
        }
    }

    static final class Metrics implements HttpClientDecorator {
        private final Calls calls;

        @Inject
        Metrics(Calls calls) {
            this.calls = calls;
        }

        @Override
        public HttpClient decorate(HttpClient client) {
            calls.names.add("decorate");
            return client;
        }
    }

    private Hooks() {}
}
