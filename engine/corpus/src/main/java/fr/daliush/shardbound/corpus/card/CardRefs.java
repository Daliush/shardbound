package fr.daliush.shardbound.corpus.card;

import fr.daliush.shardbound.core.content.Ability;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.Keyword;
import fr.daliush.shardbound.core.content.RelicCard;
import fr.daliush.shardbound.core.content.SpellCard;
import fr.daliush.shardbound.core.content.Trigger;
import fr.daliush.shardbound.core.content.UnitCard;
import java.util.ArrayList;
import java.util.List;

/**
 * What a card's text relies on, read from its data, never from its words: its keywords as their sections ("11.3"),
 * its triggers and effects as their rules, and the card of a token it summons. The switches have no default, so a new
 * keyword, trigger or effect does not compile until it is mapped here.
 */
final class CardRefs {

    private static final String SACRIFICE_RULE = "8.3";

    private CardRefs() {
    }

    /** Keywords first, then the sacrifice cost, the triggers and the effects. */
    static List<String> findRefsOfCard(CardDefinition card) {
        List<String> refs = new ArrayList<>(findKeywordSections(card));
        if (card.sacrificeCost() > 0) {
            refs.add(SACRIFICE_RULE);
        }
        listAbilitiesOf(card).forEach(ability -> refs.add(ruleOfTrigger(ability.trigger())));
        card.allEffects().forEach(effect -> refs.addAll(refsOfEffect(effect)));
        return refs;
    }

    /**
     * Only Anchor and Overcharge are in the card's keyword list. Echo sits on an attack, Fracture is a spell's steps,
     * and Link is an effect (see {@link #refsOfEffect}).
     */
    private static List<String> findKeywordSections(CardDefinition card) {
        List<String> sections = new ArrayList<>();
        card.keywords().forEach(keyword -> sections.add(sectionOfKeyword(keyword)));
        if (card instanceof UnitCard unit && unit.attacks().stream().anyMatch(attack -> attack.echo().isPresent())) {
            sections.add("11.1");
        }
        if (card instanceof SpellCard spell && spell.isFracture()) {
            sections.add("11.2");
        }
        return sections;
    }

    private static List<Ability> listAbilitiesOf(CardDefinition card) {
        return switch (card) {
            case UnitCard unit -> unit.abilities();
            case RelicCard relic -> relic.abilities();
            case SpellCard ignored -> List.of();
        };
    }

    private static String sectionOfKeyword(Keyword keyword) {
        return switch (keyword) {
            case ANCHOR -> "11.3";
            case OVERCHARGE -> "11.4";
        };
    }

    private static String ruleOfTrigger(Trigger trigger) {
        return switch (trigger) {
            case ARRIVAL -> "9.2";
            case DEATH -> "9.3";
            case DEPARTURE -> "9.4";
            case TURN_START -> "9.5";
            case TURN_END -> "9.6";
            case CONTINUOUS -> "9.7";
            case ATTACK -> "9.9";
        };
    }

    /** An effect's rule, plus the Link section for Link and the token's card for Summon. */
    private static List<String> refsOfEffect(Effect effect) {
        return switch (effect) {
            case Effect.Damage ignored -> List.of("8.1");
            case Effect.Destroy ignored -> List.of("8.2");
            case Effect.Sacrifice ignored -> List.of(SACRIFICE_RULE);
            case Effect.Heal ignored -> List.of("8.4");
            case Effect.Modify ignored -> List.of("8.5");
            case Effect.Draw ignored -> List.of("8.6");
            case Effect.Discard ignored -> List.of("8.7");
            case Effect.ReturnToHand ignored -> List.of("8.8");
            case Effect.Summon summon -> List.of("8.9", summon.token().value());
            case Effect.Freeze ignored -> List.of("8.10");
            case Effect.Link ignored -> List.of("8.11", "11.5");
            case Effect.GainShards ignored -> List.of("8.12");
            case Effect.Recall ignored -> List.of("8.13");
            case Effect.StatAura ignored -> List.of("8.14");
            case Effect.CostAura ignored -> List.of("8.14");
        };
    }
}
