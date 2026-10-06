package fr.daliush.shardbound.core.content.json;

import fr.daliush.shardbound.core.content.Ability;
import fr.daliush.shardbound.core.content.AttackAbility;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.content.CardType;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.Faction;
import fr.daliush.shardbound.core.content.FractureStep;
import fr.daliush.shardbound.core.content.Keyword;
import fr.daliush.shardbound.core.content.RelicCard;
import fr.daliush.shardbound.core.content.SpellCard;
import fr.daliush.shardbound.core.content.TargetSpec;
import fr.daliush.shardbound.core.content.Trigger;
import fr.daliush.shardbound.core.content.UnitCard;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import tools.jackson.databind.JsonNode;

/** Turns a card file of {@code content/cards/} into a {@link CardDefinition}. */
public final class CardParser {

    private static final int MAX_COST = 10;
    private static final int MAX_ECHO = 1000;
    private static final int MAX_BOARD_UNITS = 6;

    private final EffectParser effects = new EffectParser();

    public CardDefinition parse(JsonNode node, String location) {
        JsonObjectReader json = new JsonObjectReader(node, location);
        CardDefinition card = switch (json.enumValue("type", CardType.class)) {
            case UNIT -> unit(json);
            case SPELL -> spell(json);
            case RELIC -> relic(json);
        };
        json.finish();
        return card;
    }

    private UnitCard unit(JsonObjectReader json) {
        boolean token = json.trueFlag("token");
        if (token && (json.has("cost") || json.has("sacrifice_cost"))) {
            throw json.error("a token has no cost and no sacrifice cost");
        }
        OptionalInt cost = token ? OptionalInt.empty() : OptionalInt.of(cost(json));
        List<JsonObjectReader> attacks = json.objects("attacks");
        if (attacks.isEmpty() || attacks.size() > 2) {
            throw json.error("a unit has one or two attack abilities (7.1)");
        }
        return new UnitCard(id(json), json.string("name"), faction(json), cost, token,
                json.integer("defense", 1, Integer.MAX_VALUE),
                json.enumList("keywords", Keyword.class), sacrificeCost(json),
                attacks.stream().map(this::attack).toList(),
                abilities(json, true), flavor(json));
    }

    private SpellCard spell(JsonObjectReader json) {
        List<Keyword> keywords = keywordsWithoutAnchor(json);
        if (json.has("fracture")) {
            if (json.has("cost") || json.has("effects")) {
                throw json.error("a Fracture spell has step costs and effects, not its own (11.2)");
            }
            List<JsonObjectReader> steps = json.objects("fracture");
            if (steps.size() < 2 || steps.size() > 5) {
                throw json.error("Fracture has from 2 to 5 steps (11.2.1)");
            }
            return new SpellCard(id(json), json.string("name"), faction(json), OptionalInt.empty(), keywords,
                    sacrificeCost(json), List.of(), steps.stream().map(this::fractureStep).toList(), flavor(json));
        }
        return new SpellCard(id(json), json.string("name"), faction(json), OptionalInt.of(cost(json)), keywords,
                sacrificeCost(json), effectList(json, "effects"), List.of(), flavor(json));
    }

    private RelicCard relic(JsonObjectReader json) {
        List<Ability> abilities = abilities(json, false);
        if (abilities.isEmpty()) {
            throw json.error("a relic has at least one ability (6.2)");
        }
        return new RelicCard(id(json), json.string("name"), faction(json), cost(json), keywordsWithoutAnchor(json),
                sacrificeCost(json), abilities, flavor(json));
    }

    private AttackAbility attack(JsonObjectReader json) {
        AttackAbility attack = new AttackAbility(json.optionalString("name"), cost(json),
                json.objects("effects").stream().map(effects::parse).toList(),
                json.optionalInteger("echo", 1, MAX_ECHO));
        if (attack.effects().isEmpty()) {
            throw json.error("an attack ability has at least one effect");
        }
        json.finish();
        return attack;
    }

    private FractureStep fractureStep(JsonObjectReader json) {
        FractureStep step = new FractureStep(cost(json), effectList(json, "effects"));
        json.finish();
        return step;
    }

    private List<Ability> abilities(JsonObjectReader json, boolean isUnit) {
        return json.optionalObjects("abilities").stream().map(ability -> ability(ability, isUnit)).toList();
    }

    private Ability ability(JsonObjectReader json, boolean isUnit) {
        Trigger trigger = json.enumValue("trigger", Trigger.class);
        if (trigger == Trigger.ATTACK && !isUnit) {
            throw json.error("only units have \"Attack\" abilities (9.9)");
        }
        List<Effect> abilityEffects = effectList(json, "effects");
        boolean onlyAuras = abilityEffects.stream()
                .allMatch(effect -> effect instanceof Effect.StatAura || effect instanceof Effect.CostAura);
        boolean someAura = abilityEffects.stream()
                .anyMatch(effect -> effect instanceof Effect.StatAura || effect instanceof Effect.CostAura);
        if (trigger == Trigger.CONTINUOUS ? !onlyAuras : someAura) {
            throw json.error("auras, and only auras, go in continuous abilities (9.7)");
        }
        json.finish();
        return new Ability(trigger, abilityEffects);
    }

    /** Effects outside attack abilities, where there is no attack target. */
    private List<Effect> effectList(JsonObjectReader json, String field) {
        List<Effect> list = json.objects(field).stream().map(effects::parse).toList();
        if (list.isEmpty()) {
            throw json.error("'" + field + "' needs at least one effect");
        }
        if (list.stream().anyMatch(effect -> effect.targets(TargetSpec.ATTACK_TARGET))) {
            throw json.error("'attack_target' only exists inside attack abilities");
        }
        return list;
    }

    private static List<Keyword> keywordsWithoutAnchor(JsonObjectReader json) {
        List<Keyword> keywords = json.enumList("keywords", Keyword.class);
        if (keywords.contains(Keyword.ANCHOR)) {
            throw json.error("only units can have Anchor (11.3)");
        }
        return keywords;
    }

    private static CardId id(JsonObjectReader json) {
        return new CardId(json.string("id"));
    }

    private static Faction faction(JsonObjectReader json) {
        return json.enumValue("faction", Faction.class);
    }

    private static int cost(JsonObjectReader json) {
        return json.integer("cost", 0, MAX_COST);
    }

    private static int sacrificeCost(JsonObjectReader json) {
        return json.optionalInteger("sacrifice_cost", 1, MAX_BOARD_UNITS).orElse(0);
    }

    private static Optional<String> flavor(JsonObjectReader json) {
        return json.optionalString("flavor");
    }
}
