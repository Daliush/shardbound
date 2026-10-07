package fr.daliush.shardbound.core.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.daliush.shardbound.core.content.json.CardParser;
import fr.daliush.shardbound.core.content.json.TextTemplatesParser;
import fr.daliush.shardbound.core.testing.TestContent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class ContentLoaderTest {

    private final CardCatalog catalog = TestContent.catalog();

    @Test
    void loadsEveryCardAndDeckOfTheRepository() throws IOException {
        try (Stream<Path> cardFiles = Files.list(TestContent.contentDir().resolve("cards"))) {
            long fileCount = cardFiles.filter(Files::isDirectory)
                    .flatMap(ContentLoaderTest::jsonFiles)
                    .count();
            assertThat(catalog.all()).hasSize((int) fileCount);
        }
        assertThat(TestContent.content().decks()).containsKeys(new DeckId("ember-starter"), new DeckId("root-starter"));
    }

    @Test
    void readsAUnitWithTwoAttacks() {
        UnitCard ashWarden = catalog.unit(new CardId("ember.ash-warden"));

        assertThat(ashWarden.cost()).hasValue(3);
        assertThat(ashWarden.defense()).isEqualTo(6);
        assertThat(ashWarden.attacks()).hasSize(2);
        assertThat(ashWarden.attacks().get(0).echo()).hasValue(50);
        assertThat(ashWarden.attacks().get(0).hasTarget()).isTrue();
        assertThat(ashWarden.attacks().get(1).hasTarget()).isFalse();
        assertThat(ashWarden.flavor()).hasValue("The ashes remember.");
    }

    @Test
    void readsAFractureSpell() {
        SpellCard moonpull = (SpellCard) catalog.card(new CardId("tide.moonpull"));

        assertThat(moonpull.isFracture()).isTrue();
        assertThat(moonpull.cost()).isEmpty();
        assertThat(moonpull.fracture()).extracting(FractureStep::cost).containsExactly(1, 2, 3);
    }

    @Test
    void readsATokenWithoutCost() {
        UnitCard sprout = catalog.unit(new CardId("root.sprout"));

        assertThat(sprout.token()).isTrue();
        assertThat(sprout.cost()).isEmpty();
    }

    @Test
    void readsTheWordingOfCardTextsByDottedKey() {
        TextTemplates templates = TestContent.content().textTemplates();

        assertThat(templates.get("layout.attack_named")).isEqualTo("{name} ({cost} {Shard|Shards}): {effects}");
        assertThat(templates.get("effects.discard.opponent.random"))
                .isEqualTo("Your opponent discards {amount} {card|cards} at random.");
        assertThat(templates.keys()).noneMatch(key -> key.startsWith("$comment"));
        assertThatThrownBy(() -> templates.get("effects.teleport")).isInstanceOf(ContentException.class)
                .hasMessageContaining("no wording for 'effects.teleport'");
    }

    @Test
    void rejectsWordingThatIsNotText() {
        assertThatThrownBy(() -> new TextTemplatesParser().parse(
                JsonMapper.builder().build().readTree("{ \"effects\": { \"damage\": 3 } }"), "text-templates.json"))
                .isInstanceOf(ContentException.class)
                .hasMessageContaining("'effects.damage' must be an object of wordings");
    }

    @Test
    void rejectsAnUnknownField() {
        assertThatThrownBy(() -> parse("""
                { "id": "neutral.x", "name": "X", "faction": "neutral", "type": "spell", "cost": 1,
                  "effects": [{ "effect": "draw", "amount": 1, "target": "you" }], "rarity": "rare" }"""))
                .isInstanceOf(ContentException.class)
                .hasMessageContaining("unknown field 'rarity'");
    }

    @Test
    void rejectsAnUnknownEnumValue() {
        assertThatThrownBy(() -> parse("""
                { "id": "neutral.x", "name": "X", "faction": "neutral", "type": "spell", "cost": 1,
                  "effects": [{ "effect": "draw", "amount": 1, "target": "everyone" }] }"""))
                .isInstanceOf(ContentException.class)
                .hasMessageContaining("unknown value 'everyone'");
    }

    @Test
    void rejectsAMissingRequiredField() {
        assertThatThrownBy(() -> parse("""
                { "id": "neutral.x", "name": "X", "faction": "neutral", "type": "unit", "cost": 1,
                  "attacks": [{ "cost": 1, "effects": [{ "effect": "damage", "amount": 1, "target": "attack_target" }] }] }"""))
                .isInstanceOf(ContentException.class)
                .hasMessageContaining("missing field 'defense'");
    }

    @Test
    void rejectsAnAttackTargetOutsideAttacks() {
        assertThatThrownBy(() -> parse("""
                { "id": "neutral.x", "name": "X", "faction": "neutral", "type": "spell", "cost": 1,
                  "effects": [{ "effect": "damage", "amount": 1, "target": "attack_target" }] }"""))
                .isInstanceOf(ContentException.class)
                .hasMessageContaining("only exists inside attack abilities");
    }

    @Test
    void rejectsASummonOfACardThatIsNotAToken(@TempDir Path contentDir) throws IOException {
        write(contentDir.resolve("cards/neutral/caller.json"), """
                { "id": "neutral.caller", "name": "Caller", "faction": "neutral", "type": "spell", "cost": 1,
                  "effects": [{ "effect": "summon", "token": "neutral.caller" }] }""");
        Files.createDirectories(contentDir.resolve("decks"));

        assertThatThrownBy(() -> ContentLoader.load(contentDir))
                .isInstanceOf(ContentException.class)
                .hasMessageContaining("which is not a token");
    }

    private static CardDefinition parse(String json) {
        return new CardParser().parse(JsonMapper.builder().build().readTree(json), "test");
    }

    private static void write(Path file, String json) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, json);
    }

    private static Stream<Path> jsonFiles(Path dir) {
        try {
            return Files.list(dir).filter(file -> file.toString().endsWith(".json")).toList().stream();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
