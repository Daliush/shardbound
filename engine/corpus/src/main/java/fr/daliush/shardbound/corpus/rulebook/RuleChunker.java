package fr.daliush.shardbound.corpus.rulebook;

import fr.daliush.shardbound.corpus.chunk.Chunk;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * One chunk per rule of a rulebook file: the rule with its items and examples, in its Markdown, prefixed with the
 * titles of its sections, "[11.3 Anchor] …". A rule rarely names its subject ("While protected, it…"), so its
 * sections give it one.
 */
final class RuleChunker {

    /** "# 5. Game sequence", "## 5.2 Start of turn (in this order)": the section number, then the whole title. */
    private static final Pattern SECTION_HEADING = Pattern.compile("^#+\\s+((\\d+(?:\\.\\d+)*)\\.?\\s.*)$");
    private static final Pattern RULE_START =
            Pattern.compile("^\\s*(?:-|\\d+\\.)\\s+\\*\\*(\\d+\\.\\d+(?:\\.\\d+)?)\\*\\*\\s*(.*)$");

    /** A rule as cut from its file: its id and its Markdown lines. */
    private record RuleBlock(String id, List<String> lines) {}

    private RuleChunker() {
    }

    static List<Chunk> chunkRules(String markdown, String source) {
        Map<String, String> sectionTitles = mapSectionNumbersToTitles(markdown);
        return cutIntoRuleBlocks(markdown).stream()
                .map(rule -> buildRuleChunk(rule, sectionTitles, source))
                .toList();
    }

    /** "5" gives "5. Game sequence", "5.2" gives "5.2 Start of turn (in this order)". */
    private static Map<String, String> mapSectionNumbersToTitles(String markdown) {
        return markdown.lines().map(SECTION_HEADING::matcher).filter(Matcher::matches)
                .collect(Collectors.toMap(heading -> heading.group(2), heading -> heading.group(1).strip()));
    }

    /**
     * A rule starts a bullet or a numbered item ("- **7.5** …", "1. **5.2.1** …") and goes on over the indented and
     * blank lines after it, examples included. Any other line ends it.
     */
    private static List<RuleBlock> cutIntoRuleBlocks(String markdown) {
        List<RuleBlock> blocks = new ArrayList<>();
        RuleBlock currentRule = null;
        for (String line : markdown.lines().toList()) {
            Matcher ruleStart = RULE_START.matcher(line);
            if (ruleStart.matches()) {
                currentRule = new RuleBlock(ruleStart.group(1), new ArrayList<>(List.of(ruleStart.group(2))));
                blocks.add(currentRule);
            } else if (currentRule != null && (line.startsWith(" ") || line.isBlank())) {
                currentRule.lines().add(line);
            } else {
                currentRule = null;
            }
        }
        return blocks;
    }

    private static Chunk buildRuleChunk(RuleBlock rule, Map<String, String> sectionTitles, String source) {
        String body = String.join("\n", rule.lines().stream()
                .filter(line -> !line.isBlank())
                .map(String::strip)
                .toList());
        String text = buildHeadingPrefix(rule.id(), sectionTitles) + " " + body;
        return new Chunk(rule.id(), Chunk.Kind.RULE, text, WrittenRefs.findRefsInText(body), source);
    }

    /**
     * Rule 5.2.1 sits in sections 5 and 5.2: "[5. Game sequence > 5.2 Start of turn (in this order)]". A section with
     * no heading in the file, such as 11 for rule 11.3.5, is left out: "[11.3 Anchor]".
     */
    private static String buildHeadingPrefix(String ruleId, Map<String, String> sectionTitles) {
        String[] numbers = ruleId.split("\\.");
        List<String> titles = IntStream.range(1, numbers.length)
                .mapToObj(length -> String.join(".", Arrays.copyOf(numbers, length)))
                .map(sectionTitles::get)
                .filter(Objects::nonNull)
                .toList();
        return "[" + String.join(" > ", titles) + "]";
    }
}
