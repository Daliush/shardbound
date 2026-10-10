package fr.daliush.shardbound.dataset.unanswerable;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.dataset.FrozenQuestions;
import fr.daliush.shardbound.dataset.Rulebook;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The unanswerable questions (design doc §6.1), written once and kept in {@code questions/unanswerable.jsonl}. Their
 * answer key, an abstention, holds only while the Arbiter's sources say nothing about them: an invented name that a
 * card or the rulebook now uses, or an open point now settled, stops the build.
 */
public final class UnanswerableQuestions {

    public static final String RESOURCE = "/questions/unanswerable.jsonl";

    private final CardCatalog catalog;
    private final Rulebook rulebook;

    public UnanswerableQuestions(CardCatalog catalog, Rulebook rulebook) {
        this.catalog = catalog;
        this.rulebook = rulebook;
    }

    public List<UnanswerableQuestion> all() {
        return checked(FrozenQuestions.read(RESOURCE, UnanswerableQuestion.class));
    }

    public List<UnanswerableQuestion> checked(List<UnanswerableQuestion> questions) {
        for (UnanswerableQuestion question : questions) {
            switch (question.source()) {
                case "invented" -> usedBy(question.name()).ifPresent(source -> {
                    throw new IllegalStateException(question.id() + ": " + source + " uses " + question.name()
                            + ", so the question has an answer now");
                });
                case "open-point" -> {
                    if (!rulebook.isStillOpen(question.name())) {
                        throw new IllegalStateException(question.id() + ": the open point " + question.name()
                                + " is settled or gone, so the question has an answer now");
                    }
                }
                default -> throw new IllegalStateException(question.id() + ": unknown source " + question.source());
            }
        }
        return questions;
    }

    /** A card whose name holds the invented name or is held in it, or the rulebook if it uses the name. */
    private Optional<String> usedBy(String name) {
        String invented = name.toLowerCase(Locale.ROOT);
        Optional<String> card = catalog.all().stream().map(CardDefinition::name)
                .filter(cardName -> cardName.toLowerCase(Locale.ROOT).contains(invented)
                        || invented.contains(cardName.toLowerCase(Locale.ROOT)))
                .findFirst();
        if (card.isPresent()) {
            return card;
        }
        return rulebook.mentions(name) ? Optional.of("the rulebook") : Optional.empty();
    }
}
