package fr.daliush.shardbound.core.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.content.TextTemplates;
import fr.daliush.shardbound.core.content.json.CardParser;
import fr.daliush.shardbound.core.testing.TestCards;
import fr.daliush.shardbound.core.testing.TestContent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

/** Card texts written from the card data (spec §9). */
class CardTextRendererTest {

    private static final TextTemplates TEMPLATES = TestContent.content().textTemplates();
    private static final String JAB = """
            "attacks": [{ "name": "Jab", "cost": 1, "effects": [{ "effect": "damage", "amount": 1, "target": "attack_target" }] }]""";

    private final CardTextRenderer renderer = new CardTextRenderer(TestCards.CATALOG, TEMPLATES);

    @Test
    void writesTheTwoExamplesOfTheCardFormatExactlyAsTheReadmeShowsThem() throws IOException {
        for (String card : List.of("ember.ash-warden", "tide.moonpull")) {
            CardDefinition definition = TestCards.CATALOG.card(new CardId(card));

            assertThat(renderer.render(definition).texts()).as(card).isEqualTo(readmeText(definition.name()));
        }
    }

    @Test
    void saysWhatEachLineIs() {
        CardText ashWarden = renderer.render(TestCards.CATALOG.card(new CardId("ember.ash-warden")));
        CardText moonpull = renderer.render(TestCards.CATALOG.card(new CardId("tide.moonpull")));
        CardText zealot = renderer.render(TestCards.CATALOG.card(new CardId("ember.flamebound-zealot")));
        CardText cinderling = renderer.render(TestCards.CATALOG.card(new CardId("ember.cinderling")));
        CardText sap = renderer.render(TestCards.CATALOG.card(new CardId("root.renewing-sap")));

        assertThat(ashWarden.lines()).extracting(CardText.Line::kind)
                .containsExactly(CardText.Kind.ATTACK, CardText.Kind.ATTACK);
        assertThat(moonpull.lines()).extracting(CardText.Line::kind).containsOnly(CardText.Kind.FRACTURE);
        assertThat(zealot.lines()).extracting(CardText.Line::kind)
                .containsExactly(CardText.Kind.SACRIFICE_COST, CardText.Kind.ATTACK);
        assertThat(cinderling.lines()).extracting(CardText.Line::kind)
                .containsExactly(CardText.Kind.ATTACK, CardText.Kind.ABILITY);
        assertThat(sap.texts()).containsExactly("Heal yourself for 4 HP. Summon 1 Sprout token.");
        assertThat(sap.lines()).extracting(CardText.Line::kind).containsExactly(CardText.Kind.EFFECT);
    }

    @Test
    void writesEveryCardOfTheCatalog() {
        for (CardDefinition card : TestContent.catalog().all()) {
            List<String> lines = renderer.render(card).texts();

            assertThat(lines).as(card.id().value()).isNotEmpty()
                    .allSatisfy(line -> assertThat(line).doesNotContain("{", "}", "|").endsWith("."));
        }
    }

    @Test
    void everyKeyOfTheTemplatesHasItsTest() {
        assertThat(templateKeys().map(arguments -> (String) arguments.get()[0]).toList())
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(TEMPLATES.keys());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("templateKeys")
    void writesTheWordingOfEachTemplateKey(String key, String card, String line) {
        assertThat(renderer.render(parse(card)).texts()).as(key).contains(line);
    }

    static Stream<Arguments> templateKeys() {
        return Stream.of(
                arguments("layout.keywords", unit("\"keywords\": [\"anchor\"], " + JAB), "Anchor."),
                arguments("layout.sacrifice_cost", spell(2, "{ \"effect\": \"draw\", \"amount\": 1, \"target\": \"you\" }"),
                        "To play this card, sacrifice 2 units."),
                arguments("layout.attack_named", unit(JAB), "Jab (1 Shard): Deal 1 damage to the target."),
                arguments("layout.attack_unnamed", unit("""
                        "attacks": [{ "cost": 2, "effects": [{ "effect": "damage", "amount": 3, "target": "attack_target" }] }]"""),
                        "Attack (2 Shards): Deal 3 damage to the target."),
                arguments("layout.echo", unit("""
                        "attacks": [{ "name": "Jab", "cost": 1, "effects": [{ "effect": "damage", "amount": 1, "target": "attack_target" }],
                                      "echo": 50 }]"""),
                        "Jab (1 Shard): Deal 1 damage to the target. Echo 50."),
                arguments("layout.ability", ability("death", damage(2, "opponent")),
                        "Death: Deal 2 damage to your opponent."),
                arguments("layout.continuous_ability", relic(aura("all_ally_units", 1, 2)), "All your units have +1/+2."),
                arguments("layout.fracture_header", fracture(), "Fracture 2."),
                arguments("layout.fracture_step", fracture(), "Step 2 (0 Shards): Draw 1 card."),
                arguments("keywords.anchor", unit("\"keywords\": [\"anchor\", \"overcharge\"], " + JAB),
                        "Anchor. Overcharge."),
                arguments("keywords.overcharge", """
                        { "id": "test.text", "name": "Sample", "faction": "neutral", "type": "spell", "cost": 1,
                          "keywords": ["overcharge"], "effects": [{ "effect": "recall" }] }""", "Overcharge."),
                arguments("triggers.arrival", ability("arrival", draw(1, "you")), "Arrival: Draw 1 card."),
                arguments("triggers.death", ability("death", draw(1, "you")), "Death: Draw 1 card."),
                arguments("triggers.departure", ability("departure", draw(1, "you")), "Departure: Draw 1 card."),
                arguments("triggers.turn_start", ability("turn_start", draw(1, "you")), "Turn start: Draw 1 card."),
                arguments("triggers.turn_end", ability("turn_end", draw(1, "you")), "Turn end: Draw 1 card."),
                arguments("triggers.attack", ability("attack", draw(1, "you")), "On attack: Draw 1 card."),
                arguments("targets.ally_unit", spell(destroy("ally_unit")), "Destroy an allied unit."),
                arguments("targets.enemy_unit", spell(destroy("enemy_unit")), "Destroy an enemy unit."),
                arguments("targets.any_unit", spell(destroy("any_unit")), "Destroy a unit."),
                arguments("targets.random_ally_unit", spell(destroy("random_ally_unit")), "Destroy a random allied unit."),
                arguments("targets.random_enemy_unit", spell(destroy("random_enemy_unit")),
                        "Destroy a random enemy unit."),
                arguments("targets.self", ability("arrival", damage(1, "self")), "Arrival: Deal 1 damage to this unit."),
                arguments("targets.attack_target", unit(JAB), "Jab (1 Shard): Deal 1 damage to the target."),
                arguments("targets.all_ally_units", spell(destroy("all_ally_units")), "Destroy all your units."),
                arguments("targets.all_enemy_units", spell(destroy("all_enemy_units")), "Destroy all enemy units."),
                arguments("targets.all_units", spell(destroy("all_units")), "Destroy all units."),
                arguments("targets.you", spell(damage(2, "you")), "Deal 2 damage to yourself."),
                arguments("targets.opponent", spell(damage(2, "opponent")), "Deal 2 damage to your opponent."),
                arguments("targets.any_player", spell(damage(2, "any_player")), "Deal 2 damage to a player."),
                arguments("targets.ally_relic", spell(destroy("ally_relic")), "Destroy an allied relic."),
                arguments("targets.enemy_relic", spell(destroy("enemy_relic")), "Destroy an enemy relic."),
                arguments("targets.any_relic", spell(destroy("any_relic")), "Destroy a relic."),
                arguments("effects.damage", spell(damage(3, "enemy_unit")), "Deal 3 damage to an enemy unit."),
                arguments("effects.destroy", spell(destroy("enemy_unit")), "Destroy an enemy unit."),
                arguments("effects.sacrifice", spell("{ \"effect\": \"sacrifice\", \"count\": 1 }"), "Sacrifice 1 unit."),
                arguments("effects.heal.unit", spell("{ \"effect\": \"heal\", \"amount\": 3, \"target\": \"ally_unit\" }"),
                        "Heal an allied unit for 3 defense."),
                arguments("effects.heal.player", spell("{ \"effect\": \"heal\", \"amount\": 4, \"target\": \"you\" }"),
                        "Heal yourself for 4 HP."),
                arguments("effects.modify.permanent", spell(modify(2, 2, "permanent", "ally_unit")),
                        "Give an allied unit +2/+2."),
                arguments("effects.modify.end_of_turn", spell(modify(2, 0, "end_of_turn", "all_ally_units")),
                        "Give all your units +2/+0 until end of turn."),
                arguments("effects.draw.you", spell(draw(2, "you")), "Draw 2 cards."),
                arguments("effects.draw.opponent", spell(draw(1, "opponent")), "Your opponent draws 1 card."),
                arguments("effects.draw.any_player", spell(draw(2, "any_player")),
                        "A player of your choice draws 2 cards."),
                arguments("effects.discard.you.player", spell(discard(1, "you", "player")), "Discard 1 card."),
                arguments("effects.discard.you.random", spell(discard(2, "you", "random")), "Discard 2 cards at random."),
                arguments("effects.discard.opponent.player", spell(discard(2, "opponent", "player")),
                        "Your opponent discards 2 cards of their choice."),
                arguments("effects.discard.opponent.random", spell(discard(1, "opponent", "random")),
                        "Your opponent discards 1 card at random."),
                arguments("effects.discard.any_player.player", spell(discard(1, "any_player", "player")),
                        "A player of your choice discards 1 card of their choice."),
                arguments("effects.discard.any_player.random", spell(discard(2, "any_player", "random")),
                        "A player of your choice discards 2 cards at random."),
                arguments("effects.return_to_hand.single",
                        spell("{ \"effect\": \"return_to_hand\", \"target\": \"enemy_unit\" }"),
                        "Return an enemy unit to its owner's hand."),
                arguments("effects.return_to_hand.group",
                        spell("{ \"effect\": \"return_to_hand\", \"target\": \"all_units\" }"),
                        "Return all units to their owners' hands."),
                arguments("effects.summon", spell("{ \"effect\": \"summon\", \"token\": \"root.sprout\", \"count\": 2 }"),
                        "Summon 2 Sprout tokens."),
                arguments("effects.freeze", spell("{ \"effect\": \"freeze\", \"target\": \"enemy_unit\" }"),
                        "Freeze an enemy unit."),
                arguments("effects.link", spell("{ \"effect\": \"link\" }"), "Link two units."),
                arguments("effects.gain_shards.this_turn",
                        spell("{ \"effect\": \"gain_shards\", \"mode\": \"this_turn\", \"amount\": 2 }"),
                        "Gain 2 Shards this turn."),
                arguments("effects.gain_shards.max", spell("{ \"effect\": \"gain_shards\", \"mode\": \"max\" }"),
                        "Gain 1 max Shard."),
                arguments("effects.recall", spell("{ \"effect\": \"recall\" }"), "Recall a unit from your graveyard."),
                arguments("effects.aura.stats", relic(aura("all_enemy_units", -1, -2)), "All enemy units have -1/-2."),
                arguments("effects.aura.cost.you", relic(costAura("you", "unit", -1)), "Your units cost 1 less."),
                arguments("effects.aura.cost.opponent", relic(costAura("opponent", "spell", 2)),
                        "Your opponent's spells cost 2 more."),
                arguments("aura_cards.unit", relic(costAura("you", "unit", -1)), "Your units cost 1 less."),
                arguments("aura_cards.spell", relic(costAura("opponent", "spell", 2)),
                        "Your opponent's spells cost 2 more."),
                arguments("aura_cards.relic", relic(costAura("you", "relic", -1)), "Your relics cost 1 less."),
                arguments("aura_cards.any", relic(costAura("opponent", "any", 1)), "Your opponent's cards cost 1 more."),
                arguments("aura_change.negative", relic(costAura("you", "unit", -3)), "Your units cost 3 less."),
                arguments("aura_change.positive", relic(costAura("opponent", "spell", 2)),
                        "Your opponent's spells cost 2 more."));
    }

    /** The lines after the card's header in the README's "Generated text", flavor left out. */
    private static List<String> readmeText(String name) throws IOException {
        List<String> readme = Files.readAllLines(TestContent.contentDir().resolve("cards").resolve("README.md"));
        int header = readme.indexOf(readme.stream().filter(line -> line.startsWith("> **" + name + "**"))
                .findFirst().orElseThrow());
        List<String> text = new ArrayList<>();
        for (String line : readme.subList(header + 1, readme.size())) {
            if (!line.startsWith("> ")) {
                break;
            }
            String content = line.substring(2);
            if (!content.startsWith("*")) {
                text.add(content);
            }
        }
        return text;
    }

    private static CardDefinition parse(String card) {
        return new CardParser().parse(JsonMapper.builder().build().readTree(card), "test card");
    }

    private static String unit(String fields) {
        return """
                { "id": "test.text", "name": "Sample", "faction": "neutral", "type": "unit", "cost": 1, "defense": 3, %s }"""
                .formatted(fields);
    }

    private static String ability(String trigger, String effect) {
        return unit(JAB + ", \"abilities\": [{ \"trigger\": \"%s\", \"effects\": [%s] }]".formatted(trigger, effect));
    }

    private static String spell(String effect) {
        return """
                { "id": "test.text", "name": "Sample", "faction": "neutral", "type": "spell", "cost": 1, "effects": [%s] }"""
                .formatted(effect);
    }

    private static String spell(int sacrificeCost, String effect) {
        return """
                { "id": "test.text", "name": "Sample", "faction": "neutral", "type": "spell", "cost": 1,
                  "sacrifice_cost": %d, "effects": [%s] }""".formatted(sacrificeCost, effect);
    }

    private static String fracture() {
        return """
                { "id": "test.text", "name": "Sample", "faction": "neutral", "type": "spell", "fracture": [
                  { "cost": 1, "effects": [%s] }, { "cost": 0, "effects": [%s] }] }"""
                .formatted(damage(1, "opponent"), draw(1, "you"));
    }

    private static String relic(String aura) {
        return """
                { "id": "test.text", "name": "Sample", "faction": "neutral", "type": "relic", "cost": 1,
                  "abilities": [{ "trigger": "continuous", "effects": [%s] }] }""".formatted(aura);
    }

    private static String damage(int amount, String target) {
        return "{ \"effect\": \"damage\", \"amount\": %d, \"target\": \"%s\" }".formatted(amount, target);
    }

    private static String destroy(String target) {
        return "{ \"effect\": \"destroy\", \"target\": \"%s\" }".formatted(target);
    }

    private static String draw(int amount, String target) {
        return "{ \"effect\": \"draw\", \"amount\": %d, \"target\": \"%s\" }".formatted(amount, target);
    }

    private static String discard(int amount, String target, String choice) {
        return "{ \"effect\": \"discard\", \"amount\": %d, \"target\": \"%s\", \"choice\": \"%s\" }"
                .formatted(amount, target, choice);
    }

    private static String modify(int attackDamage, int defense, String duration, String target) {
        return ("{ \"effect\": \"modify\", \"attack_damage\": %d, \"defense\": %d, \"duration\": \"%s\","
                + " \"target\": \"%s\" }").formatted(attackDamage, defense, duration, target);
    }

    private static String aura(String target, int attackDamage, int defense) {
        return ("{ \"effect\": \"aura\", \"kind\": \"stats\", \"target\": \"%s\", \"attack_damage\": %d,"
                + " \"defense\": %d }").formatted(target, attackDamage, defense);
    }

    private static String costAura(String player, String cardType, int change) {
        return ("{ \"effect\": \"aura\", \"kind\": \"cost\", \"player\": \"%s\", \"card_type\": \"%s\","
                + " \"change\": %d }").formatted(player, cardType, change);
    }
}
