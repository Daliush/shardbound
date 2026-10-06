package fr.daliush.shardbound.core.content.json;

import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.content.CardTypeFilter;
import fr.daliush.shardbound.core.content.DiscardChoice;
import fr.daliush.shardbound.core.content.Duration;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.GainMode;
import fr.daliush.shardbound.core.content.PlayerSide;
import fr.daliush.shardbound.core.content.TargetSpec;

/** Parses one effect object, keyed by its {@code effect} field (rulebook section 8). */
final class EffectParser {

    private static final int MAX_AMOUNT = 1000;

    Effect parse(JsonObjectReader json) {
        String name = json.string("effect");
        Effect effect = switch (name) {
            case "damage" -> new Effect.Damage(amount(json), target(json));
            case "destroy" -> new Effect.Destroy(target(json));
            case "sacrifice" -> new Effect.Sacrifice(count(json));
            case "heal" -> new Effect.Heal(amount(json), target(json));
            case "modify" -> new Effect.Modify(signed(json, "attack_damage"), signed(json, "defense"),
                    json.enumValue("duration", Duration.class), target(json));
            case "draw" -> new Effect.Draw(amount(json), target(json));
            case "discard" -> new Effect.Discard(amount(json), target(json),
                    json.enumValue("choice", DiscardChoice.class));
            case "return_to_hand" -> new Effect.ReturnToHand(target(json));
            case "summon" -> new Effect.Summon(new CardId(json.string("token")), count(json));
            case "freeze" -> new Effect.Freeze(target(json));
            case "link" -> new Effect.Link();
            case "gain_shards" -> gainShards(json);
            case "recall" -> new Effect.Recall();
            case "aura" -> aura(json);
            default -> throw json.error("unknown effect '" + name + "'");
        };
        json.finish();
        return effect;
    }

    private Effect gainShards(JsonObjectReader json) {
        GainMode mode = json.enumValue("mode", GainMode.class);
        if (mode == GainMode.MAX) {
            if (json.has("amount")) {
                throw json.error("'amount' is not allowed with mode 'max'");
            }
            return new Effect.GainShards(mode, 1);
        }
        return new Effect.GainShards(mode, amount(json));
    }

    private Effect aura(JsonObjectReader json) {
        String kind = json.string("kind");
        return switch (kind) {
            case "stats" -> new Effect.StatAura(target(json), signed(json, "attack_damage"), signed(json, "defense"));
            case "cost" -> new Effect.CostAura(json.enumValue("player", PlayerSide.class),
                    json.enumValue("card_type", CardTypeFilter.class), nonZero(json, "change"));
            default -> throw json.error("unknown aura kind '" + kind + "'");
        };
    }

    private static TargetSpec target(JsonObjectReader json) {
        return json.enumValue("target", TargetSpec.class);
    }

    private static int amount(JsonObjectReader json) {
        return json.integer("amount", 1, MAX_AMOUNT);
    }

    private static int count(JsonObjectReader json) {
        return json.optionalInteger("count", 1, MAX_AMOUNT).orElse(1);
    }

    private static int signed(JsonObjectReader json, String field) {
        return json.integer(field, -MAX_AMOUNT, MAX_AMOUNT);
    }

    private static int nonZero(JsonObjectReader json, String field) {
        int value = signed(json, field);
        if (value == 0) {
            throw json.error("'" + field + "' cannot be 0");
        }
        return value;
    }
}
