package fr.daliush.shardbound.core.testing;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.json.CardParser;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;

/**
 * The real catalog plus a few test-only cards, for rules that no real card exercises yet
 * (a "Turn end" ability, an "Attack" ability, a spell with two chosen targets…).
 */
public final class TestCards {

    private static final String[] CARDS = {
        // A spell that hits the opposing player directly (7.7).
        """
        { "id": "test.bolt", "name": "Bolt", "faction": "neutral", "type": "spell", "cost": 1,
          "effects": [{ "effect": "damage", "amount": 3, "target": "opponent" }] }""",
        // Two chosen targets on one spell (10.5).
        """
        { "id": "test.double-strike", "name": "Double Strike", "faction": "neutral", "type": "spell", "cost": 1,
          "effects": [{ "effect": "destroy", "target": "enemy_unit" },
                      { "effect": "damage", "amount": 2, "target": "enemy_unit" }] }""",
        // Draws two cards (8.6).
        """
        { "id": "test.scholar", "name": "Scholar's Insight", "faction": "neutral", "type": "spell", "cost": 1,
          "effects": [{ "effect": "draw", "amount": 2, "target": "you" }] }""",
        // Destroys a relic (8.2).
        """
        { "id": "test.shatter", "name": "Shatter", "faction": "neutral", "type": "spell", "cost": 1,
          "effects": [{ "effect": "destroy", "target": "enemy_relic" }] }""",
        // Death, Departure and Turn end abilities (9.4, 9.6, 9.10).
        """
        { "id": "test.guard", "name": "Old Guard", "faction": "neutral", "type": "unit", "cost": 2, "defense": 3,
          "attacks": [{ "name": "Jab", "cost": 1, "effects": [{ "effect": "damage", "amount": 1, "target": "attack_target" }] }],
          "abilities": [
            { "trigger": "turn_end", "effects": [{ "effect": "heal", "amount": 2, "target": "you" }] },
            { "trigger": "departure", "effects": [{ "effect": "draw", "amount": 1, "target": "you" }] },
            { "trigger": "death", "effects": [{ "effect": "damage", "amount": 1, "target": "opponent" }] }] }""",
        // An Arrival ability with two chosen targets (10.2, 10.6).
        """
        { "id": "test.watcher", "name": "Watcher", "faction": "neutral", "type": "unit", "cost": 1, "defense": 3,
          "attacks": [{ "cost": 1, "effects": [{ "effect": "damage", "amount": 1, "target": "attack_target" }] }],
          "abilities": [{ "trigger": "arrival", "effects": [
            { "effect": "damage", "amount": 2, "target": "enemy_unit" },
            { "effect": "damage", "amount": 1, "target": "enemy_unit" }] }] }""",
        // An "Attack" ability that hits every enemy unit before the intercept decision (7.9, 9.9).
        """
        { "id": "test.charger", "name": "Charger", "faction": "neutral", "type": "unit", "cost": 2, "defense": 5,
          "attacks": [{ "name": "Gore", "cost": 1, "effects": [
            { "effect": "damage", "amount": 2, "target": "attack_target" },
            { "effect": "heal", "amount": 1, "target": "you" }] }],
          "abilities": [{ "trigger": "attack", "effects": [{ "effect": "damage", "amount": 3, "target": "all_enemy_units" }] }] }""",
        // An "Attack" ability that destroys the attacker itself (7.10).
        """
        { "id": "test.reckless", "name": "Reckless Imp", "faction": "neutral", "type": "unit", "cost": 1, "defense": 3,
          "attacks": [{ "name": "Lunge", "cost": 1, "effects": [{ "effect": "damage", "amount": 5, "target": "attack_target" }] }],
          "abilities": [{ "trigger": "attack", "effects": [{ "effect": "destroy", "target": "self" }] }] }""",
        // A Sacrifice effect on a spell (8.3, 8.16).
        """
        { "id": "test.ritual", "name": "Blood Ritual", "faction": "neutral", "type": "spell", "cost": 1,
          "effects": [{ "effect": "sacrifice", "count": 1 }, { "effect": "draw", "amount": 2, "target": "you" }] }""",
        // A sacrifice that its own first effect can make impossible (8.22).
        """
        { "id": "test.cataclysm", "name": "Cataclysm", "faction": "neutral", "type": "spell", "cost": 1,
          "effects": [{ "effect": "damage", "amount": 9, "target": "all_units" }, { "effect": "sacrifice" },
                      { "effect": "draw", "amount": 2, "target": "you" }] }""",
        // An Arrival ability that asks for two sacrifices (8.22).
        """
        { "id": "test.altar", "name": "Bone Altar", "faction": "neutral", "type": "unit", "cost": 1, "defense": 3,
          "attacks": [{ "name": "Jab", "cost": 1, "effects": [{ "effect": "damage", "amount": 1, "target": "attack_target" }] }],
          "abilities": [{ "trigger": "arrival", "effects": [{ "effect": "sacrifice", "count": 2 },
                                                             { "effect": "damage", "amount": 5, "target": "opponent" }] }] }""",
        // A permanent defense bonus (8.5).
        """
        { "id": "test.bulwark", "name": "Bulwark", "faction": "neutral", "type": "spell", "cost": 1,
          "effects": [{ "effect": "modify", "attack_damage": 0, "defense": 3, "duration": "permanent", "target": "ally_unit" }] }""",
        // A temporary defense bonus on any unit (8.5).
        """
        { "id": "test.ward", "name": "Ward", "faction": "neutral", "type": "spell", "cost": 1,
          "effects": [{ "effect": "modify", "attack_damage": 0, "defense": 2, "duration": "end_of_turn", "target": "any_unit" }] }""",
        // A temporary malus (8.5, 8.17).
        """
        { "id": "test.hex", "name": "Hex", "faction": "neutral", "type": "spell", "cost": 1,
          "effects": [{ "effect": "modify", "attack_damage": -1, "defense": -2, "duration": "end_of_turn", "target": "enemy_unit" }] }""",
        // A "Death" ability that buffs its controller's units until end of turn, whoever's turn it is (8.19).
        """
        { "id": "test.martyr", "name": "Martyr", "faction": "neutral", "type": "unit", "cost": 1, "defense": 1,
          "attacks": [{ "name": "Jab", "cost": 1, "effects": [{ "effect": "damage", "amount": 1, "target": "attack_target" }] }],
          "abilities": [{ "trigger": "death", "effects": [
            { "effect": "modify", "attack_damage": 1, "defense": 2, "duration": "end_of_turn", "target": "all_ally_units" }] }] }""",
        // The opponent discards two cards of their choice (8.7).
        """
        { "id": "test.mind-rot", "name": "Mind Rot", "faction": "neutral", "type": "spell", "cost": 1,
          "effects": [{ "effect": "discard", "amount": 2, "target": "opponent", "choice": "player" }] }""",
        // The opponent discards a card at random (8.7).
        """
        { "id": "test.purge", "name": "Purge", "faction": "neutral", "type": "spell", "cost": 1,
          "effects": [{ "effect": "discard", "amount": 1, "target": "opponent", "choice": "random" }] }""",
        // Returns an enemy unit to its owner's hand (8.8).
        """
        { "id": "test.recede", "name": "Recede", "faction": "neutral", "type": "spell", "cost": 1,
          "effects": [{ "effect": "return_to_hand", "target": "enemy_unit" }] }""",
        // Returns every unit to its owner's hand (8.8).
        """
        { "id": "test.flood", "name": "Flood", "faction": "neutral", "type": "spell", "cost": 1,
          "effects": [{ "effect": "return_to_hand", "target": "all_units" }] }""",
        // Returns an enemy relic to its owner's hand (8.8, 10.4).
        """
        { "id": "test.uproot", "name": "Uproot", "faction": "neutral", "type": "spell", "cost": 1,
          "effects": [{ "effect": "return_to_hand", "target": "enemy_relic" }] }""",
        // Freezes an enemy unit (8.10).
        """
        { "id": "test.frost", "name": "Frost", "faction": "neutral", "type": "spell", "cost": 1,
          "effects": [{ "effect": "freeze", "target": "enemy_unit" }] }""",
        // An attack ability that asks for two sacrifices (8.16).
        """
        { "id": "test.blood-knight", "name": "Blood Knight", "faction": "neutral", "type": "unit", "cost": 2, "defense": 5,
          "attacks": [{ "name": "Blood Strike", "cost": 1, "effects": [{ "effect": "sacrifice", "count": 2 },
                        { "effect": "damage", "amount": 8, "target": "attack_target" }] }] }"""
    };

    public static final CardCatalog CATALOG = build();

    private TestCards() {
    }

    private static CardCatalog build() {
        CardParser parser = new CardParser();
        JsonMapper mapper = JsonMapper.builder().build();
        List<CardDefinition> cards = new ArrayList<>(TestContent.catalog().all());
        for (String json : CARDS) {
            cards.add(parser.parse(mapper.readTree(json), "test card"));
        }
        return new CardCatalog(cards);
    }
}
