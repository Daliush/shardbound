package fr.daliush.shardbound.core.content;

import java.util.Locale;
import java.util.Optional;

/**
 * One way a deck breaks the deck-building rules (section 2). {@code message()} is the sentence both content checks
 * report, the Python content tests and the engine, rule ID included.
 */
public sealed interface DeckProblem {

    /** The rule it breaks; empty for a card that does not exist, which no rule covers. */
    Optional<String> rule();

    String message();

    /** 2.1: a deck holds exactly 30 cards. */
    record WrongSize(int size) implements DeckProblem {

        @Override
        public Optional<String> rule() {
            return Optional.of("2.1");
        }

        @Override
        public String message() {
            return "has " + size + " cards, needs exactly " + DeckValidator.DECK_SIZE + " (2.1)";
        }
    }

    /** 2.3: a card's copies sit in one entry, with a count. */
    record ListedTwice(CardId card, int times) implements DeckProblem {

        @Override
        public Optional<String> rule() {
            return Optional.of("2.3");
        }

        @Override
        public String message() {
            return "lists " + card + " " + times + " times; use one entry with a count (2.3)";
        }
    }

    record UnknownCard(CardId card) implements DeckProblem {

        @Override
        public Optional<String> rule() {
            return Optional.empty();
        }

        @Override
        public String message() {
            return "contains " + card + ", which does not exist";
        }
    }

    /** 2.3: at most 2 copies of a card. */
    record TooManyCopies(CardId card, int count) implements DeckProblem {

        @Override
        public Optional<String> rule() {
            return Optional.of("2.3");
        }

        @Override
        public String message() {
            return "has " + count + " copies of " + card + ", max " + DeckValidator.MAX_COPIES + " (2.3)";
        }
    }

    /** 2.4: tokens are never part of a deck. */
    record Token(CardId card) implements DeckProblem {

        @Override
        public Optional<String> rule() {
            return Optional.of("2.4");
        }

        @Override
        public String message() {
            return "contains the token " + card + "; tokens are never in a deck (2.4)";
        }
    }

    /** 2.2: a deck holds cards of its faction and neutral cards only. */
    record OtherFaction(CardId card, Faction cardFaction, Faction deckFaction) implements DeckProblem {

        @Override
        public Optional<String> rule() {
            return Optional.of("2.2");
        }

        @Override
        public String message() {
            return "contains " + card + ", a " + lower(cardFaction) + " card, in a deck of faction "
                    + lower(deckFaction) + " (2.2)";
        }

        private static String lower(Faction faction) {
            return faction.name().toLowerCase(Locale.ROOT);
        }
    }
}
