package fr.daliush.shardbound.core.text;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.content.Trigger;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.PlayerId;

/** Small pieces of English shared by the describers, always from one player's point of view. */
final class Wording {

    private final CardCatalog catalog;
    private final PlayerId viewer;

    Wording(CardCatalog catalog, PlayerId viewer) {
        this.catalog = catalog;
        this.viewer = viewer;
    }

    /** "Sprout #61". */
    String card(CardInstance card) {
        return name(card.card()) + " #" + card.id().value();
    }

    String name(CardId card) {
        return catalog.card(card).name();
    }

    /** "You" or "Your opponent", as the subject of a sentence. */
    String subject(PlayerId player) {
        return player == viewer ? "You" : "Your opponent";
    }

    /** "you" or "your opponent", inside a sentence. */
    String object(PlayerId player) {
        return player == viewer ? "you" : "your opponent";
    }

    /** "yourself" or "your opponent", as a target the viewer picks. */
    String reflexive(PlayerId player) {
        return player == viewer ? "yourself" : "your opponent";
    }

    /** "your" or "your opponent's". */
    String possessive(PlayerId player) {
        return player == viewer ? "your" : "your opponent's";
    }

    /** "your" or "their", when the player is already the subject. */
    String own(PlayerId player) {
        return player == viewer ? "your" : "their";
    }

    /** The verb for "You" or for "Your opponent": {@code verb(p, "draw", "draws")}. */
    String verb(PlayerId player, String forYou, String forOpponent) {
        return player == viewer ? forYou : forOpponent;
    }

    static String shards(int amount) {
        return amount + (amount == 1 ? " Shard" : " Shards");
    }

    static String trigger(Trigger trigger) {
        return switch (trigger) {
            case ARRIVAL -> "Arrival";
            case DEATH -> "Death";
            case DEPARTURE -> "Departure";
            case TURN_START -> "Turn start";
            case TURN_END -> "Turn end";
            case CONTINUOUS -> "continuous";
            case ATTACK -> "On attack";
        };
    }
}
