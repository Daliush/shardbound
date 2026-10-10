package fr.daliush.shardbound.dataset;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The rulebook of {@code docs/rules/}: the rule IDs, written {@code **N.N**} or {@code **N.N.N**}, each rule's text,
 * and the whole text. It catches a mistyped ID, a rule changed since a question was written about it, and an invented
 * name that the rulebook does use.
 */
public final class Rulebook {

    private static final Pattern RULE = Pattern.compile("\\*\\*(\\d+\\.\\d+(?:\\.\\d+)?)\\*\\*");
    private static final Pattern RULE_START =
            Pattern.compile("^\\s*(?:-|\\d+\\.)\\s+\\*\\*(\\d+\\.\\d+(?:\\.\\d+)?)\\*\\*\\s*(.*)$");
    private static final String OPEN_POINTS = "12-open-points.md";

    private final Map<String, String> texts;
    private final String whole;
    private final String stillOpen;

    private Rulebook(Map<String, String> texts, String whole, String stillOpen) {
        this.texts = Map.copyOf(texts);
        this.whole = whole;
        this.stillOpen = stillOpen;
    }

    public static Rulebook load(Path rulesDir) {
        try (Stream<Path> files = Files.list(rulesDir)) {
            Map<String, String> markdown = files.filter(file -> file.toString().endsWith(".md")).sorted()
                    .collect(Collectors.toMap(file -> file.getFileName().toString(), Rulebook::read,
                            (first, second) -> first, LinkedHashMap::new));
            Map<String, String> texts = new HashMap<>();
            markdown.values().forEach(file -> texts.putAll(ruleTexts(file)));
            markdown.values().stream().flatMap(Rulebook::ruleIds).forEach(id -> texts.putIfAbsent(id, ""));
            String openPoints = markdown.getOrDefault(OPEN_POINTS, "");
            return new Rulebook(texts, String.join("\n", markdown.values()), stillOpenSection(openPoints));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean has(String id) {
        return texts.containsKey(id);
    }

    /** The rule's text without its ID, bold or examples, its lines (list markers included) joined by spaces. */
    public Optional<String> text(String id) {
        return Optional.ofNullable(texts.get(id));
    }

    /** Whether the rulebook uses this word or name anywhere, ignoring case. */
    public boolean mentions(String name) {
        return Pattern.compile("\\b" + Pattern.quote(name) + "\\b", Pattern.CASE_INSENSITIVE).matcher(whole).find();
    }

    /** Whether {@code 12-open-points.md} still lists this point, by its title, under "Still open". */
    public boolean isStillOpen(String title) {
        return stillOpen.contains("**" + title + ".**");
    }

    private static Stream<String> ruleIds(String markdown) {
        Matcher matcher = RULE.matcher(markdown);
        return matcher.results().map(match -> match.group(1)).toList().stream();
    }

    /**
     * A rule starts a bullet or a numbered item ("- **7.5** …", "1. **5.2.1** …") and goes on over the indented and
     * blank lines after it. Quoted examples explain a rule but are not part of it.
     */
    private static Map<String, String> ruleTexts(String markdown) {
        Map<String, String> texts = new HashMap<>();
        String id = null;
        List<String> lines = new ArrayList<>();
        for (String line : markdown.split("\\R")) {
            Matcher start = RULE_START.matcher(line);
            if (start.matches()) {
                if (id != null) {
                    texts.put(id, plain(lines));
                }
                id = start.group(1);
                lines = new ArrayList<>(List.of(start.group(2)));
            } else if (id != null && (line.startsWith(" ") || line.isBlank())) {
                lines.add(line);
            } else if (id != null) {
                texts.put(id, plain(lines));
                id = null;
            }
        }
        if (id != null) {
            texts.put(id, plain(lines));
        }
        return texts;
    }

    private static String plain(List<String> lines) {
        return lines.stream()
                .filter(line -> !line.isBlank() && !line.strip().startsWith(">"))
                .map(line -> line.replace("**", "").replace("*", "").strip())
                .collect(Collectors.joining(" "));
    }

    private static String stillOpenSection(String openPoints) {
        int start = openPoints.indexOf("## Still open");
        if (start < 0) {
            return "";
        }
        int end = openPoints.indexOf("\n## ", start + 1);
        return openPoints.substring(start, end < 0 ? openPoints.length() : end);
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
