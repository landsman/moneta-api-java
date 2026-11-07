package cz.insuit.moneta.play;

import com.typesafe.config.Config;
import cz.insuit.moneta.AccessToken;
import cz.insuit.moneta.MonetaClient;
import java.util.ArrayList;
import java.util.List;
import play.Environment;
import play.inject.Binding;
import play.inject.Module;

/**
 * One {@link MonetaClient} for the application, built from {@code moneta.*} when the application starts and closed
 * when it stops, a dev-mode reload included. An {@link AccessToken} too, with {@code moneta.token} set. Enabled by
 * the jar's {@code reference.conf}; an application that builds its own client disables it with {@code
 * play.modules.disabled}.
 */
public final class MonetaModule extends Module {

    @Override
    public List<Binding<?>> bindings(Environment environment, Config config) {
        var bindings = new ArrayList<Binding<?>>();
        // Eager is a singleton too: one client, one stop hook, and a value the client refuses stops the start.
        bindings.add(bindClass(MonetaClient.class)
                .toProvider(MonetaClientProvider.class)
                .eagerly());
        if (config.hasPath("moneta.token")) {
            // A blank token is refused here, before the application serves anything.
            bindings.add(bindClass(AccessToken.class).toInstance(new AccessToken(config.getString("moneta.token"))));
        }
        return bindings;
    }
}
