package fr.daliush.shardbound.corpus.rulebook;

import fr.daliush.shardbound.corpus.chunk.Chunk;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One chunk per term of the vocabulary table, "**Owner**: The player whose deck contained the card.", with the id
 * {@code vocabulary.owner}. Some terms are defined nowhere else, such as owner and controller.
 */
final class VocabularyChunker {

    private static final Pattern TABLE_ROW = Pattern.compile("^\\|\\s*\\*\\*(.+?)\\*\\*\\s*\\|\\s*(.+?)\\s*\\|\\s*$");

    private VocabularyChunker() {
    }

    static List<Chunk> chunkTerms(String markdown, String source) {
        return markdown.lines().map(TABLE_ROW::matcher).filter(Matcher::matches)
                .map(row -> buildTermChunk(row.group(1), row.group(2), source))
                .toList();
    }

    /** The row's Markdown as written, the term in bold as the table prints it. */
    private static Chunk buildTermChunk(String term, String meaning, String source) {
        String text = "**" + term + "**: " + meaning;
        return Chunk.term(buildTermId(term), term, text, WrittenRefs.findRefsInText(text), source);
    }

    /** "Attack ability" gives "vocabulary.attack-ability". */
    private static String buildTermId(String term) {
        return "vocabulary." + term.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
