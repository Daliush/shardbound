package fr.daliush.shardbound.dataset.deck;

import static fr.daliush.shardbound.dataset.Phrases.faction;
import static fr.daliush.shardbound.dataset.Phrases.withArticle;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.content.Deck;
import fr.daliush.shardbound.core.content.DeckEntry;
import fr.daliush.shardbound.core.content.DeckProblem;
import fr.daliush.shardbound.core.content.DeckValidator;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The deck-building questions: "is this deck legal?" about each starter deck and its mutations. The engine's
 * {@link DeckValidator} answers; each problem it finds becomes a key fact and its rule an essential rule. A mutation
 * that breaks another rule than the one it was made for is a mistake here, so it stops the build.
 */
public final class DeckQuestions {

    public static final String CATEGORY = "deck-building";
    /** A legal deck passes every rule of section 2, so a correct answer cites them all. */
    public static final List<String> DECK_RULES = List.of("2.1", "2.2", "2.3", "2.4");

    private static final List<String> TEMPLATES = List.of(
            "Is this deck legal?",
            "Can I play this deck?",
            "Is this a legal deck?",
            "Am I allowed to play with this deck?");

    private final CardCatalog catalog;
    private final DeckValidator validator;
    private final long seed;

    public DeckQuestions(CardCatalog catalog, long seed) {
        this.catalog = catalog;
        this.validator = new DeckValidator(catalog);
        this.seed = seed;
    }

    /** The questions about these starter decks and their mutations, the decks sorted by id. */
    public List<DeckQuestion> all(Collection<Deck> starters) {
        DeckMutations mutations = new DeckMutations(catalog, seed);
        List<MutatedDeck> decks = starters.stream()
                .sorted(Comparator.comparing(deck -> deck.id().value()))
                .flatMap(starter -> mutations.of(starter).stream())
                .toList();
        return Stream.iterate(0, index -> index < decks.size(), index -> index + 1)
                .map(index -> question(decks.get(index), TEMPLATES.get(index % TEMPLATES.size())))
                .toList();
    }

    DeckQuestion question(MutatedDeck mutated, String template) {
        List<DeckProblem> problems = validator.check(mutated.deck());
        List<String> broken = problems.stream().map(problem -> problem.rule().orElseThrow(() ->
                new IllegalStateException(mutated.id() + ": " + problem.message()))).distinct().toList();
        if (!broken.equals(mutated.mutation().breaks().stream().toList())) {
            throw new IllegalStateException(mutated.id() + " breaks " + broken + " instead of only "
                    + mutated.mutation().breaks().orElse("nothing"));
        }
        List<String> keyFacts = problems.isEmpty() ? List.of("The deck is legal.")
                : Stream.concat(Stream.of("The deck is not legal."), problems.stream().map(this::fact)).toList();
        return new DeckQuestion(mutated.id(), CATEGORY, mutated.mutation().label(), template,
                decklist(mutated.deck()), cards(mutated.deck()), keyFacts,
                problems.isEmpty() ? DECK_RULES : broken);
    }

    private String fact(DeckProblem problem) {
        return switch (problem) {
            case DeckProblem.WrongSize wrong -> "The deck has " + wrong.size() + " cards, but a deck must contain "
                    + "exactly " + DeckValidator.DECK_SIZE + ".";
            case DeckProblem.TooManyCopies copies -> "The deck has " + copies.count() + " copies of "
                    + name(copies.card()) + ", but a deck can contain at most " + DeckValidator.MAX_COPIES
                    + " copies of a card.";
            case DeckProblem.OtherFaction other -> name(other.card()) + " is "
                    + withArticle(faction(other.cardFaction())) + " card, and " + withArticle(faction(
                    other.deckFaction())) + " deck can only contain " + faction(other.deckFaction())
                    + " and neutral cards.";
            case DeckProblem.Token token -> name(token.card()) + " is a token, and tokens are never part of a deck.";
            case DeckProblem.ListedTwice ignored -> throw new IllegalStateException("A decklist shown to a player "
                    + "has one line per card: " + problem.message());
            case DeckProblem.UnknownCard ignored -> throw new IllegalStateException(problem.message());
        };
    }

    /** "Faction: Ember", then one line per card, "2× Ash Warden", sorted by name so no line stands out. */
    private String decklist(Deck deck) {
        return Stream.concat(Stream.of("Faction: " + faction(deck.faction())),
                        deck.cards().stream().sorted(Comparator.comparing(entry -> name(entry.card())))
                                .map(entry -> entry.count() + "× " + name(entry.card())))
                .collect(Collectors.joining("\n"));
    }

    private static List<String> cards(Deck deck) {
        return deck.cards().stream().map(DeckEntry::card).map(CardId::value).sorted().toList();
    }

    private String name(CardId card) {
        return catalog.card(card).name();
    }
}
