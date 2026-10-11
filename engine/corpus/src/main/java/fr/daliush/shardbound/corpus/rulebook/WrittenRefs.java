package fr.daliush.shardbound.corpus.rulebook;

import java.util.List;
import java.util.regex.Pattern;

/** The rule and section numbers a text cites, in order: "(5.4.3)", "rule 9.8", "(11.3)", "(7)". */
final class WrittenRefs {

    private static final Pattern REF = Pattern.compile("\\b(\\d+\\.\\d+(?:\\.\\d+)?)\\b|\\((\\d+)\\)");

    private WrittenRefs() {
    }

    static List<String> findRefsInText(String text) {
        return REF.matcher(text).results()
                .map(match -> match.group(1) != null ? match.group(1) : match.group(2))
                .toList();
    }
}
