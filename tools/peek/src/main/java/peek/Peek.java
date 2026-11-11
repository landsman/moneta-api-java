package peek;

import cz.insuit.moneta.AccessToken;
import cz.insuit.moneta.Account;
import cz.insuit.moneta.MonetaClient;
import cz.insuit.moneta.Transaction;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Reads the live account the way MONETA sends it, for finding out what the API does: not shipped, not API. The
 * token comes from {@code MONETA_TOKEN}, which {@code make peek} fills from 1Password. People are masked in
 * everything it prints; {@code record} writes a fixture with them replaced. Run through {@code make}, see
 * .docs/development.md.
 */
public final class Peek {

    static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String FIXTURES = "modules/moneta-api/src/test/resources/moneta/live/";

    public static void main(String[] args) throws IOException {
        var arguments = new ArrayList<>(Arrays.asList(args));
        boolean shape = arguments.remove("--shape");
        var command = arguments.isEmpty() ? "transactions" : arguments.removeFirst();
        var token = System.getenv("MONETA_TOKEN");
        if (token == null || token.isBlank()) {
            fail("MONETA_TOKEN is empty: run it through make, which reads it from 1Password through op run");
        }
        try (var moneta = new MonetaClient()) {
            var access = new AccessToken(token.strip());
            switch (command) {
                case "accounts" -> moneta.accounts(access).forEach(System.out::println);
                case "balance" -> {
                    for (var account : moneta.accounts(access)) {
                        System.out.println(account.id() + ": " + moneta.balance(access, account.id()));
                    }
                }
                case "transactions" -> {
                    var range = range(arguments, 0);
                    int number = 0;
                    for (var account : moneta.accounts(access)) {
                        var movements = moneta.transactions(access, account.id(), range.from(), range.to());
                        System.out.printf(
                                "== %s, %s to %s: %d%n", account.id(), range.from(), range.to(), movements.size());
                        for (var movement : movements) {
                            var masked = Masking.mask(JSON.readTree(movement.raw()), shape);
                            System.out.println("-- " + number++ + " (make record-fixture N=" + (number - 1) + ")");
                            System.out.println(
                                    JSON.writerWithDefaultPrettyPrinter().writeValueAsString(masked));
                        }
                    }
                }
                case "token" -> System.out.println("expires " + moneta.tokenExpiry(access));
                case "record" -> record(moneta, access, arguments);
                default -> fail("unknown command " + command + "; accounts, balance, token, transactions or record");
            }
        }
    }

    /**
     * {@code record <n> <name> [from] [to]}: the movement {@code transactions} numbered n over the same range,
     * people replaced, as live/<name>.json. By number, since the reference is masked in what peek prints.
     */
    private static void record(MonetaClient moneta, AccessToken access, List<String> arguments) throws IOException {
        if (arguments.size() != 4) {
            // Both dates, the ones make peek numbered with: a payment arriving or midnight passing in between would
            // renumber a range that ends today, and the masked output would not show it was another movement.
            fail("usage: record <number from peek> <fixture name> <from> <to>, the dates make peek used");
        }
        int wanted = Integer.parseInt(arguments.get(0));
        var file = Path.of(FIXTURES + arguments.get(1) + ".json");
        if (Files.exists(file)) {
            fail(file + " exists; a recorded fixture is not overwritten, pick another name");
        }
        var range = range(arguments, 2);
        int number = 0;
        for (Account account : moneta.accounts(access)) {
            for (Transaction movement : moneta.transactions(access, account.id(), range.from(), range.to())) {
                if (number++ == wanted) {
                    var fixture = Anonymiser.fixture((ObjectNode) JSON.readTree(movement.raw()), account.id());
                    Files.writeString(
                            file, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(fixture) + "\n");
                    System.out.println("wrote " + file + "; read it whole before committing, the repository is public");
                    return;
                }
            }
        }
        fail("no movement " + wanted + " between " + range.from() + " and " + range.to() + "; peek numbers them");
    }

    private record Range(LocalDate from, LocalDate to) {}

    /** The two dates at {@code at}, or the last week up to today when neither is given. */
    private static Range range(List<String> arguments, int at) {
        int given = arguments.size() - at;
        if (given == 1) {
            fail("give both dates or neither: one alone is ambiguous");
        }
        var to = given >= 2 ? LocalDate.parse(arguments.get(at + 1)) : LocalDate.now();
        var from = given >= 2 ? LocalDate.parse(arguments.get(at)) : to.minusDays(7);
        return new Range(from, to);
    }

    private static void fail(String message) {
        System.err.println("peek: " + message);
        System.exit(1);
    }

    private Peek() {}
}
