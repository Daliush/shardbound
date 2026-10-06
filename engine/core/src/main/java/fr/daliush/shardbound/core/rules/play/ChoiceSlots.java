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
 * A spell's choice slots: the effects whose target its player picks when playing it (10.5).
 * A slot with no valid option is skipped: the card stays playable and that effect does nothing.
 */
public final class ChoiceSlots {

    private ChoiceSlots() {
    }

    /** Every way to fill the slots, in canonical order (spec §6.2). */
    public static List<List<TargetRef>> combinations(Game game, PlayerId player, List<Effect> effects) {
        List<List<TargetRef>> combinations = List.of(List.of());
        for (Effect effect : effects) {
            List<TargetRef> options = TargetOptions.forEffect(game, player, effect);
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
            boolean filledSlot = !TargetOptions.forEffect(game, player, effect).isEmpty();
            perEffect.add(filledSlot ? List.of(next.next()) : List.of());
        }
        return perEffect;
    }

    private static List<List<TargetRef>> extend(List<List<TargetRef>> combinations, List<TargetRef> options) {
        List<List<TargetRef>> extended = new ArrayList<>();
        for (List<TargetRef> combination : combinations) {
            for (TargetRef option : options) {
                List<TargetRef> longer = new ArrayList<>(combination);
                longer.add(option);
                extended.add(List.copyOf(longer));
            }
        }
        return extended;
    }
}
