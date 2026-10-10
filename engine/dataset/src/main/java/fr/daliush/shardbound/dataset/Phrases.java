package fr.daliush.shardbound.dataset;

import fr.daliush.shardbound.core.content.Faction;
import java.util.Locale;

/** Small pieces of English the template questions and their key facts share. */
public final class Phrases {

    private Phrases() {
    }

    /** "1 Shard", "3 Shards". */
    public static String count(int amount, String singular, String plural) {
        return amount + " " + (amount == 1 ? singular : plural);
    }

    public static String shards(int amount) {
        return count(amount, "Shard", "Shards");
    }

    /** "Ember", "Tide", "Root", "neutral": a faction's name, as the rulebook writes it in a sentence. */
    public static String faction(Faction faction) {
        String name = faction.name().toLowerCase(Locale.ROOT);
        return faction == Faction.NEUTRAL ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** "an Ember", "a Tide". */
    public static String withArticle(String word) {
        return ("AEIOUaeiou".indexOf(word.charAt(0)) >= 0 ? "an " : "a ") + word;
    }
}
