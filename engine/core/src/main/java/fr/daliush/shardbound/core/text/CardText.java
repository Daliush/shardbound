package fr.daliush.shardbound.core.text;

import java.util.List;

/**
 * A card's rules text, line by line (spec §9). Each line says what it is, so a client can leave out what it already
 * shows another way, such as a unit's attacks.
 */
public record CardText(List<Line> lines) {

    public CardText {
        lines = List.copyOf(lines);
    }

    /** {@code FRACTURE} is the Fracture header and each step; {@code EFFECT} is a spell's effects, on one line. */
    public enum Kind {
        KEYWORDS, SACRIFICE_COST, ATTACK, FRACTURE, EFFECT, ABILITY
    }

    public record Line(Kind kind, String text) {}

    public List<String> texts() {
        return lines.stream().map(Line::text).toList();
    }
}
