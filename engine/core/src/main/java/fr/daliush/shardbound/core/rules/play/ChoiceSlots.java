package fr.daliush.shardbound.core.rules.play;

import fr.daliush.shardbound.core.action.TargetRef;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.rules.effect.TargetOptions;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A spell's choice slots: the effects whose target its player picks when playing it (10.5), one slot each, two for
 * Link (8.11). A slot with no valid option is skipped: the card stays playable and that effect does nothing.
 */
public final class ChoiceSlots {

    private ChoiceSlots() {
    }

    /** Every way to fill the slots, in canonical order (spec §6.2). */
    public static List<List<TargetRef>> combinations(Game game, PlayerId player, List<Effect> effects) {
        List<List<TargetRef>> combinations = List.of(List.of());
        for (Effect effect : effects) {
            List<List<TargetRef>> options = options(game, player, effect);
            if (!options.isEmpty()) {
                combinations = extend(combinations, options);
            }
        }
        return combinations;
    }

    /** Spreads the targets of a {@code PlayCard} over the effects: one list per effect, empty when none. */
    public static List<List<TargetRef>> perEffect(Game game, PlayerId player, List<Effect> effects,
                                                  List<TargetRef> targets) {
        Iterator<TargetRef> next = targets.iterator();
        List<List<TargetRef>> perEffect = new ArrayList<>();
        for (Effect effect : effects) {
            List<TargetRef> filled = new ArrayList<>();
            if (!options(game, player, effect).isEmpty()) {
                for (int slot = 0; slot < slots(effect); slot++) {
                    filled.add(next.next());
                }
            }
            perEffect.add(List.copyOf(filled));
        }
        return perEffect;
    }

    /** The ways to fill one effect's slots: one target, or a pair of units for Link. */
    private static List<List<TargetRef>> options(Game game, PlayerId player, Effect effect) {
        return effect instanceof Effect.Link
                ? TargetOptions.linkPairs(game)
                : TargetOptions.forEffect(game, player, effect).stream().map(List::of).toList();
    }

    private static int slots(Effect effect) {
        return effect instanceof Effect.Link ? 2 : 1;
    }

    private static List<List<TargetRef>> extend(List<List<TargetRef>> combinations, List<List<TargetRef>> options) {
        List<List<TargetRef>> extended = new ArrayList<>();
        for (List<TargetRef> combination : combinations) {
            for (List<TargetRef> option : options) {
                List<TargetRef> longer = new ArrayList<>(combination);
                longer.addAll(option);
                extended.add(List.copyOf(longer));
            }
        }
        return extended;
    }
}
