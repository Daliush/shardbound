package fr.daliush.shardbound.dataset;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The rule IDs of {@code docs/rules/}, written {@code **N.N**} or {@code **N.N.N**}, to catch a mistyped ID. */
public final class Rulebook {

    private static final Pattern RULE = Pattern.compile("\\*\\*(\\d+\\.\\d+(?:\\.\\d+)?)\\*\\*");

    private final Set<String> ids;

    private Rulebook(Set<String> ids) {
        this.ids = Set.copyOf(ids);
    }

    public static Rulebook load(Path rulesDir) {
        try (Stream<Path> files = Files.list(rulesDir)) {
            return new Rulebook(files.filter(file -> file.toString().endsWith(".md"))
                    .flatMap(Rulebook::ruleIds)
                    .collect(Collectors.toSet()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean has(String id) {
        return ids.contains(id);
    }

    private static Stream<String> ruleIds(Path file) {
        try {
            Matcher matcher = RULE.matcher(Files.readString(file));
            return matcher.results().map(match -> match.group(1)).toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
