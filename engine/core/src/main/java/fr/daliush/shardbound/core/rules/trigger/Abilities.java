package fr.daliush.shardbound.core.rules.trigger;

import fr.daliush.shardbound.core.content.Trigger;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.resolution.EffectList;
import fr.daliush.shardbound.core.resolution.EffectSource;
import fr.daliush.shardbound.core.resolution.QueuedTrigger;
import fr.daliush.shardbound.core.resolution.Step;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.List;
import java.util.Optional;

/** Starts triggered abilities and echoes: from the queue, or right away for "Attack" abilities (9.9). */
public final class Abilities {

    private Abilities() {
    }

    public static void start(Game game, QueuedTrigger trigger) {
        switch (trigger) {
            case QueuedTrigger.TriggeredAbility ability -> game.push(begin(game, ability.source(),
                    ability.controller(), ability.trigger(), ability.abilityIndex()));
            case QueuedTrigger.Echoes echoes -> game.push(new Step.StartEchoes(echoes.source(), echoes.controller(),
                    echoes.attackIndexes()));
        }
    }

    /** Emits the trigger and returns the first step of the ability: picking its targets (10.6). */
    public static Step begin(Game game, CardInstance source, PlayerId controller, Trigger trigger, int abilityIndex) {
        game.emit(new GameEvent.AbilityTriggered(source, trigger, List.of(Triggers.ruleOf(trigger))));
        EffectList effects = new EffectList.AbilityEffects(source.card(), abilityIndex);
        return new Step.ChooseTargets(new EffectSource(effects, source.id(), controller, Optional.empty()), List.of());
    }
}
