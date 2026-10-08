package fr.daliush.shardbound.core.rules;

import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.testing.TestContent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every rule of sections 1 to 11 of the rulebook has a test whose display name starts with its ID (spec §16), unless
 * the allowlist says why it cannot have one. The engine is the executable rulebook: this keeps the two in step.
 */
class RulebookCoverageTest {

    /** Purely descriptive rules: what each one describes is tested under the rules that give it effect. */
    private static final Map<String, String> ALLOWLIST = Map.of(
            "3.1", "names the four zones; the rules of each zone are 3.2 to 3.6",
            "6.1", "lists what every card has; the content loader refuses a card without it",
            "6.2", "describes the three card types; what they do is tested under 6.3, 6.5, 7.3 and 8.2",
            "10.1", "lists the kinds of targets; each is tested with its effects (section 8) and under 10.2 to 10.6");

    private static final int LAST_SECTION = 11;
    private static final Pattern RULE = Pattern.compile("\\*\\*(\\d+\\.\\d+(?:\\.\\d+)?)\\*\\*");
    private static final Pattern HEADING = Pattern.compile("(?m)^#+ (\\d+\\.\\d+(?:\\.\\d+)?) ");
    private static final Pattern DISPLAY_NAME = Pattern.compile("@DisplayName\\(\\s*\"(\\d+\\.\\d+(?:\\.\\d+)?) —");

    private final Path root = TestContent.contentDir().getParent();

    @Test
    void everyRuleHasATest() {
        Set<String> tested = cited(DISPLAY_NAME, testSources());

        assertThat(rules().stream().filter(rule -> !tested.contains(rule) && !ALLOWLIST.containsKey(rule)))
                .as("rules of sections 1 to %s without a test", LAST_SECTION)
                .isEmpty();
    }

    @Test
    void theAllowlistHoldsOnlyRulesWithoutATest() {
        assertThat(rules()).containsAll(ALLOWLIST.keySet());
        assertThat(cited(DISPLAY_NAME, testSources())).as("a tested rule leaves the allowlist")
                .doesNotContainAnyElementsOf(ALLOWLIST.keySet());
    }

    /** Catches a test that cites a rule or a section which does not exist, such as a mistyped ID. */
    @Test
    void everyTestCitesARuleOfTheRulebook() {
        Set<String> known = new TreeSet<>(cited(RULE, rulebook()));
        known.addAll(cited(HEADING, rulebook()));

        assertThat(known).containsAll(cited(DISPLAY_NAME, testSources()));
    }

    /** The rule IDs of sections 1 to 11, written {@code **N.N**} or {@code **N.N.N**}. */
    private List<String> rules() {
        return cited(RULE, rulebook()).stream()
                .filter(rule -> Integer.parseInt(rule.substring(0, rule.indexOf('.'))) <= LAST_SECTION)
                .sorted(Comparator.comparing(RulebookCoverageTest::sortKey))
                .toList();
    }

    private List<Path> rulebook() {
        return files(root.resolve("docs/rules"), ".md");
    }

    private List<Path> testSources() {
        return files(root.resolve("engine/core/src/test/java"), ".java");
    }

    private static Set<String> cited(Pattern pattern, List<Path> files) {
        Set<String> ids = new TreeSet<>();
        for (Path file : files) {
            Matcher matcher = pattern.matcher(read(file));
            while (matcher.find()) {
                ids.add(matcher.group(1));
            }
        }
        return ids;
    }

    private static List<Path> files(Path dir, String extension) {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(file -> file.toString().endsWith(extension)).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** "5.10" after "5.9". */
    private static String sortKey(String rule) {
        StringBuilder key = new StringBuilder();
        for (String part : rule.split("\\.")) {
            key.append("%03d".formatted(Integer.parseInt(part)));
        }
        return key.toString();
    }
}
