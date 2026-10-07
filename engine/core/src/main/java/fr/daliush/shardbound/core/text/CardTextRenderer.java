package fr.daliush.shardbound.core.text;

import fr.daliush.shardbound.core.content.Ability;
import fr.daliush.shardbound.core.content.AttackAbility;
import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.FractureStep;
import fr.daliush.shardbound.core.content.RelicCard;
import fr.daliush.shardbound.core.content.SpellCard;
import fr.daliush.shardbound.core.content.TargetSpec;
import fr.daliush.shardbound.core.content.TextTemplates;
import fr.daliush.shardbound.core.content.Trigger;
import fr.daliush.shardbound.core.content.UnitCard;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Writes a card's rules text from its data with the wording of {@code text-templates.json} (spec §9), so the text
 * players and the Arbiter read always matches what the engine plays.
 */
public final class CardTextRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_]+)}");
    private static final Pattern PLURAL = Pattern.compile("\\{([^{}|]+)\\|([^{}|]+)}");
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private final CardCatalog catalog;
    private final TextTemplates templates;

    public CardTextRenderer(CardCatalog catalog, TextTemplates templates) {
        this.catalog = catalog;
        this.templates = templates;
    }

    /** Keywords, sacrifice cost, attacks with their Echo, Fracture steps or spell effects, then abilities. */
    public CardText render(CardDefinition card) {
        List<CardText.Line> lines = new ArrayList<>();
        if (!card.keywords().isEmpty()) {
            lines.add(line(CardText.Kind.KEYWORDS, keywords(card)));
        }
        if (card.sacrificeCost() > 0) {
            lines.add(line(CardText.Kind.SACRIFICE_COST,
                    fill("layout.sacrifice_cost", Map.of("count", card.sacrificeCost()))));
        }
        switch (card) {
            case UnitCard unit -> {
                unit.attacks().forEach(attack -> lines.add(line(CardText.Kind.ATTACK, attack(attack, card))));
                lines.addAll(abilities(unit.abilities(), card));
            }
            case SpellCard spell -> lines.addAll(spell.isFracture() ? fracture(spell)
                    : List.of(line(CardText.Kind.EFFECT, effects(spell.effects(), card))));
            case RelicCard relic -> lines.addAll(abilities(relic.abilities(), card));
        }
        return new CardText(lines);
    }

    /** "Anchor.", "Anchor. Overcharge.": each keyword is its own sentence. */
    private String keywords(CardDefinition card) {
        return card.keywords().stream()
                .map(keyword -> fill("layout.keywords", Map.of("keywords", templates.get("keywords." + key(keyword)))))
                .collect(Collectors.joining(" "));
    }

    private String attack(AttackAbility attack, CardDefinition card) {
        Map<String, Object> values = new HashMap<>(Map.of("cost", attack.cost(), "effects",
                effects(attack.effects(), card)));
        attack.name().ifPresent(name -> values.put("name", name));
        String text = fill(attack.name().isPresent() ? "layout.attack_named" : "layout.attack_unnamed", values);
        return attack.echo().isPresent() ? text + fill("layout.echo", Map.of("x", attack.echo().getAsInt())) : text;
    }

    private List<CardText.Line> fracture(SpellCard spell) {
        List<CardText.Line> lines = new ArrayList<>();
        lines.add(line(CardText.Kind.FRACTURE, fill("layout.fracture_header", Map.of("n", spell.fracture().size()))));
        for (int index = 0; index < spell.fracture().size(); index++) {
            FractureStep step = spell.fracture().get(index);
            lines.add(line(CardText.Kind.FRACTURE, fill("layout.fracture_step",
                    Map.of("n", index + 1, "cost", step.cost(), "effects", effects(step.effects(), spell)))));
        }
        return lines;
    }

    /** Continuous abilities hold only auras and have no trigger label (9.7). */
    private List<CardText.Line> abilities(List<Ability> abilities, CardDefinition card) {
        return abilities.stream().map(ability -> line(CardText.Kind.ABILITY, ability.trigger() == Trigger.CONTINUOUS
                ? fill("layout.continuous_ability", Map.of("effects", effects(ability.effects(), card)))
                : fill("layout.ability", Map.of("trigger", templates.get("triggers." + key(ability.trigger())),
                        "effects", effects(ability.effects(), card)))))
                .toList();
    }

    private String effects(List<Effect> effects, CardDefinition card) {
        return effects.stream().map(effect -> capitalized(effect(effect, card))).collect(Collectors.joining(" "));
    }

    private String effect(Effect effect, CardDefinition card) {
        return switch (effect) {
            case Effect.Damage damage -> fill("effects.damage",
                    Map.of("amount", damage.amount(), "target", target(damage.target(), card)));
            case Effect.Destroy destroy -> fill("effects.destroy", Map.of("target", target(destroy.target(), card)));
            case Effect.Sacrifice sacrifice -> fill("effects.sacrifice", Map.of("count", sacrifice.count()));
            case Effect.Heal heal -> fill("effects.heal." + (isPlayer(heal.target()) ? "player" : "unit"),
                    Map.of("amount", heal.amount(), "target", target(heal.target(), card)));
            case Effect.Modify modify -> fill("effects.modify." + key(modify.duration()),
                    Map.of("target", target(modify.target(), card), "attack_damage",
                            Wording.signed(modify.attackDamage()), "defense", Wording.signed(modify.defense())));
            case Effect.Draw draw -> fill("effects.draw." + key(draw.target()), Map.of("amount", draw.amount()));
            case Effect.Discard discard -> fill("effects.discard." + key(discard.target()) + "."
                    + key(discard.choice()), Map.of("amount", discard.amount()));
            case Effect.ReturnToHand back -> fill("effects.return_to_hand."
                    + (back.target().isGroup() ? "group" : "single"), Map.of("target", target(back.target(), card)));
            case Effect.Summon summon -> fill("effects.summon",
                    Map.of("count", summon.count(), "token", catalog.card(summon.token()).name()));
            case Effect.Freeze freeze -> fill("effects.freeze", Map.of("target", target(freeze.target(), card)));
            case Effect.Link ignored -> fill("effects.link", Map.of());
            case Effect.GainShards gain -> fill("effects.gain_shards." + key(gain.mode()),
                    Map.of("amount", gain.amount()));
            case Effect.Recall ignored -> fill("effects.recall", Map.of());
            case Effect.StatAura aura -> fill("effects.aura.stats", Map.of("target", target(aura.target(), card),
                    "attack_damage", Wording.signed(aura.attackDamage()), "defense", Wording.signed(aura.defense())));
            case Effect.CostAura aura -> fill("effects.aura.cost." + key(aura.player()), Map.of(
                    "cards", templates.get("aura_cards." + key(aura.cardType())), "change", change(aura.change())));
        };
    }

    /** {@code self} reads "this unit" or "this relic". */
    private String target(TargetSpec target, CardDefinition card) {
        return target == TargetSpec.SELF
                ? fill("targets.self", Map.of("card_type", key(card.type())))
                : templates.get("targets." + key(target));
    }

    private String change(int change) {
        return fill(change < 0 ? "aura_change.negative" : "aura_change.positive", Map.of("amount", Math.abs(change)));
    }

    /** The template with its placeholders filled, then each {@code {word|words}} after the number before it. */
    private String fill(String key, Map<String, ?> values) {
        Matcher placeholder = PLACEHOLDER.matcher(templates.get(key));
        String filled = placeholder.replaceAll(match -> {
            Object value = values.get(match.group(1));
            if (value == null) {
                throw new IllegalStateException("No value for {" + match.group(1) + "} in '" + key + "'");
            }
            return Matcher.quoteReplacement(value.toString());
        });
        return PLURAL.matcher(filled).replaceAll(match -> Matcher.quoteReplacement(
                isOne(lastNumberBefore(filled, match.start())) ? match.group(1) : match.group(2)));
    }

    private static String lastNumberBefore(String text, int end) {
        Matcher number = NUMBER.matcher(text.substring(0, end));
        String last = "";
        while (number.find()) {
            last = number.group();
        }
        return last;
    }

    private static boolean isOne(String number) {
        return number.equals("1");
    }

    private static boolean isPlayer(TargetSpec target) {
        return target == TargetSpec.YOU || target == TargetSpec.OPPONENT || target == TargetSpec.ANY_PLAYER;
    }

    private static String capitalized(String sentence) {
        return Character.toUpperCase(sentence.charAt(0)) + sentence.substring(1);
    }

    private static String key(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static CardText.Line line(CardText.Kind kind, String text) {
        return new CardText.Line(kind, text);
    }
}
