package fr.daliush.shardbound.core.event;

import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.content.Trigger;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.GameResult;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * What happened, step by step, with the IDs of the rules applied. This trace is the game log
 * and the answer key for the Arbiter's citations.
 */
public sealed interface GameEvent {

    /** Rule IDs applied, never empty. */
    List<String> rules();

    default Visibility visibility() {
        return Visibility.PUBLIC;
    }

    /** The form that players who cannot see this event receive. */
    default GameEvent redacted() {
        return this;
    }

    /** This event as {@code viewer} may see it: as it is if it is visible to them, redacted otherwise. */
    default GameEvent seenBy(PlayerId viewer) {
        return visibility().isVisibleTo(viewer) ? this : redacted();
    }

    // Setup and turns

    record GameStarted(PlayerId firstPlayer, List<String> rules) implements GameEvent {
        public GameStarted(PlayerId firstPlayer) {
            this(firstPlayer, List.of("5.1.1"));
        }
    }

    /** {@code card} is empty in the redacted form. */
    record CardDrawn(PlayerId player, Optional<CardInstance> card, List<String> rules) implements GameEvent {
        @Override
        public Visibility visibility() {
            return new Visibility.PrivateTo(player);
        }

        @Override
        public GameEvent redacted() {
            return new CardDrawn(player, Optional.empty(), rules);
        }
    }

    record HandKept(PlayerId player, List<String> rules) implements GameEvent {
        public HandKept(PlayerId player) {
            this(player, List.of("5.1.3"));
        }
    }

    record MulliganTaken(PlayerId player, List<String> rules) implements GameEvent {
        public MulliganTaken(PlayerId player) {
            this(player, List.of("5.1.3"));
        }
    }

    record TurnStarted(PlayerId player, int turn, List<String> rules) implements GameEvent {
        public TurnStarted(PlayerId player, int turn) {
            this(player, turn, List.of("5.2"));
        }
    }

    record ShardsRefilled(PlayerId player, int max, int available, int locked, List<String> rules)
            implements GameEvent {
        public ShardsRefilled(PlayerId player, int max, int available, int locked) {
            this(player, max, available, locked, List.of("4.1", "4.2"));
        }
    }

    record HpLost(PlayerId player, int amount, HpLossReason reason, List<String> rules) implements GameEvent {}

    record CardDiscarded(PlayerId player, CardInstance card, DiscardReason reason, List<String> rules)
            implements GameEvent {}

    record TurnEnded(PlayerId player, List<String> rules) implements GameEvent {
        public TurnEnded(PlayerId player) {
            this(player, List.of("5.4"));
        }
    }

    record GameEnded(GameResult result, List<String> rules) implements GameEvent {}

    // Cards

    record CardPlayed(PlayerId player, CardInstance card, int cost, boolean overcharged, OptionalInt fractureStep,
                      List<String> rules) implements GameEvent {
        public CardPlayed(PlayerId player, CardInstance card, int cost, boolean overcharged) {
            this(player, card, cost, overcharged, OptionalInt.empty(), List.of("6.3"));
        }
    }

    record UnitArrived(CardInstance unit, PlayerId controller, List<String> rules) implements GameEvent {
        public UnitArrived(CardInstance unit, PlayerId controller) {
            this(unit, controller, List.of("6.3", "6.5"));
        }
    }

    record RelicArrived(CardInstance relic, PlayerId controller, List<String> rules) implements GameEvent {
        public RelicArrived(CardInstance relic, PlayerId controller) {
            this(relic, controller, List.of("6.3"));
        }
    }

    record SpellResolved(CardInstance spell, List<String> rules) implements GameEvent {
        public SpellResolved(CardInstance spell) {
            this(spell, List.of("6.3", "3.5"));
        }
    }

    record TokenSummoned(CardInstance unit, PlayerId controller, List<String> rules) implements GameEvent {
        public TokenSummoned(CardInstance unit, PlayerId controller) {
            this(unit, controller, List.of("8.9"));
        }
    }

    record SummonFailed(PlayerId player, CardId token, List<String> rules) implements GameEvent {
        public SummonFailed(PlayerId player, CardId token) {
            this(player, token, List.of("8.9", "3.4"));
        }
    }

    record AbilityTriggered(CardInstance source, Trigger trigger, List<String> rules) implements GameEvent {}

    // Combat

    record AttackDeclared(CardInstance attacker, int attackIndex, Optional<EventTarget> target, List<String> rules)
            implements GameEvent {
        public AttackDeclared(CardInstance attacker, int attackIndex, Optional<EventTarget> target) {
            this(attacker, attackIndex, target, List.of("7.4"));
        }
    }

    record AttackIntercepted(CardInstance originalTarget, CardInstance interceptor, List<String> rules)
            implements GameEvent {
        public AttackIntercepted(CardInstance originalTarget, CardInstance interceptor) {
            this(originalTarget, interceptor, List.of("7.5"));
        }
    }

    record InterceptDeclined(PlayerId defender, List<String> rules) implements GameEvent {
        public InterceptDeclined(PlayerId defender) {
            this(defender, List.of("7.5"));
        }
    }

    record AttackCancelled(CardInstance attacker, List<String> rules) implements GameEvent {
        public AttackCancelled(CardInstance attacker) {
            this(attacker, List.of("7.10"));
        }
    }

    // Effects

    record UnitDamaged(CardInstance unit, int amount, List<String> rules) implements GameEvent {
        public UnitDamaged(CardInstance unit, int amount) {
            this(unit, amount, List.of("8.1"));
        }
    }

    record PlayerDamaged(PlayerId player, int amount, List<String> rules) implements GameEvent {
        public PlayerDamaged(PlayerId player, int amount) {
            this(player, amount, List.of("8.1"));
        }
    }

    record UnitHealed(CardInstance unit, int amount, List<String> rules) implements GameEvent {
        public UnitHealed(CardInstance unit, int amount) {
            this(unit, amount, List.of("8.4"));
        }
    }

    record PlayerHealed(PlayerId player, int amount, List<String> rules) implements GameEvent {
        public PlayerHealed(PlayerId player, int amount) {
            this(player, amount, List.of("8.4"));
        }
    }

    record UnitDestroyed(CardInstance unit, PlayerId controller, List<String> rules) implements GameEvent {}

    record RelicDestroyed(CardInstance relic, PlayerId controller, List<String> rules) implements GameEvent {
        public RelicDestroyed(CardInstance relic, PlayerId controller) {
            this(relic, controller, List.of("8.2"));
        }
    }

    record TokenVanished(CardInstance unit, List<String> rules) implements GameEvent {
        public TokenVanished(CardInstance unit) {
            this(unit, List.of("3.6"));
        }
    }

    enum HpLossReason {
        FATIGUE
    }

    enum DiscardReason {
        OVERDRAW
    }
}
