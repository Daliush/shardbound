package fr.daliush.shardbound.core.event;

import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.content.Duration;
import fr.daliush.shardbound.core.content.GainMode;
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

    /** {@code locked}: the Shards Overcharge kept from the refill (11.4.2); beyond the refill, they are lost (11.4.4). */
    record ShardsRefilled(PlayerId player, int max, int available, int locked, List<String> rules)
            implements GameEvent {
        public ShardsRefilled(PlayerId player, int max, int available, int locked) {
            this(player, max, available, locked, locked == 0 ? List.of("4.1", "4.2")
                    : locked > max ? List.of("4.1", "4.2", "11.4.2", "11.4.4") : List.of("4.1", "4.2", "11.4.2"));
        }
    }

    /** 5.2.1: the unit can leave the board again (11.3.6). */
    record AnchorProtectionEnded(CardInstance unit, List<String> rules) implements GameEvent {
        public AnchorProtectionEnded(CardInstance unit) {
            this(unit, List.of("5.2.1", "11.3.1"));
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
            this(player, card, cost, overcharged, OptionalInt.empty(),
                    overcharged ? List.of("6.3", "11.4.1") : List.of("6.3"));
        }
    }

    /** 11.4.2, 11.4.3: {@code amount} more of the player's Shards locked on their next turn, {@code total} in all. */
    record ShardsLocked(PlayerId player, int amount, int total, List<String> rules) implements GameEvent {
        public ShardsLocked(PlayerId player, int amount, int total) {
            this(player, amount, total, List.of("11.4.2", "11.4.3"));
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

    record Modified(CardInstance unit, int attackDamage, int defense, Duration duration, List<String> rules)
            implements GameEvent {
        public Modified(CardInstance unit, int attackDamage, int defense, Duration duration) {
            this(unit, attackDamage, defense, duration, List.of("8.5"));
        }
    }

    /** 5.4.2: an "until end of turn" modifier ends; a malus gives back what it took (8.17). */
    record ModifierExpired(CardInstance unit, int attackDamage, int defense, List<String> rules) implements GameEvent {
        public ModifierExpired(CardInstance unit, int attackDamage, int defense) {
            this(unit, attackDamage, defense, List.of("5.4.2", defense < 0 ? "8.17" : "8.5"));
        }
    }

    /** 8.12: {@code amount} is what the player got: 0 for a max Shard above the cap of 10. */
    record ShardsGained(PlayerId player, int amount, GainMode mode, List<String> rules) implements GameEvent {
        public ShardsGained(PlayerId player, int amount, GainMode mode) {
            this(player, amount, mode, List.of("8.12"));
        }
    }

    /** 8.14: a stat aura of {@code source} now gives the unit its bonus or malus. */
    record AuraApplied(CardInstance unit, CardInstance source, int attackDamage, int defense, List<String> rules)
            implements GameEvent {
        public AuraApplied(CardInstance unit, CardInstance source, int attackDamage, int defense) {
            this(unit, source, attackDamage, defense, List.of("8.14"));
        }
    }

    /** 8.14: it stops applying; a defense malus gives back what it took (8.21). */
    record AuraRemoved(CardInstance unit, CardInstance source, int attackDamage, int defense, List<String> rules)
            implements GameEvent {
        public AuraRemoved(CardInstance unit, CardInstance source, int attackDamage, int defense) {
            this(unit, source, attackDamage, defense, defense < 0 ? List.of("8.14", "8.21") : List.of("8.14"));
        }
    }

    /** 8.13: a unit card back from its controller's graveyard to their hand. */
    record Recalled(CardInstance card, List<String> rules) implements GameEvent {
        public Recalled(CardInstance card) {
            this(card, List.of("8.13"));
        }
    }

    /** 8.20: the hand was full, so the card stays in the graveyard. */
    record RecallFailed(CardInstance card, List<String> rules) implements GameEvent {
        public RecallFailed(CardInstance card) {
            this(card, List.of("8.13", "8.20"));
        }
    }

    /** 8.10: the unit can neither attack nor intercept until the end of turn {@code throughTurn}. */
    record Frozen(CardInstance unit, int throughTurn, List<String> rules) implements GameEvent {
        public Frozen(CardInstance unit, int throughTurn) {
            this(unit, throughTurn, List.of("8.10"));
        }
    }

    record UnitThawed(CardInstance unit, List<String> rules) implements GameEvent {
        public UnitThawed(CardInstance unit) {
            this(unit, List.of("8.10"));
        }
    }

    /** 8.8: a unit or a relic back in its owner's hand, reset (6.7). It did not die. */
    record ReturnedToHand(CardInstance card, List<String> rules) implements GameEvent {
        public ReturnedToHand(CardInstance card) {
            this(card, List.of("8.8", "6.7"));
        }
    }

    /** 8.8: a card returned to a full hand (3.3) goes to the graveyard instead. It did not die. */
    record SentToGraveyardHandFull(CardInstance card, List<String> rules) implements GameEvent {
        public SentToGraveyardHandFull(CardInstance card) {
            this(card, List.of("8.8", "3.3"));
        }
    }

    /** {@code rules} say whether the unit paid a sacrifice cost (6.3, 8.3) or a Sacrifice effect (8.3). */
    record UnitSacrificed(CardInstance unit, PlayerId controller, List<String> rules) implements GameEvent {}

    /** 11.3.2, 11.3.3: the anchored unit stays on the board; a sacrifice still counts as paid. */
    record AnchorPrevented(CardInstance unit, Removal attempt, List<String> rules) implements GameEvent {
        public AnchorPrevented(CardInstance unit, Removal attempt) {
            this(unit, attempt, List.of("11.3.2", "11.3.3"));
        }
    }

    /** 11.3.4: an anchored unit at 0 defense stays on the board. */
    record UnitDoomed(CardInstance unit, List<String> rules) implements GameEvent {
        public UnitDoomed(CardInstance unit) {
            this(unit, List.of("11.3.4"));
        }
    }

    /** 11.3.4: its defense went back above 0. */
    record DoomLifted(CardInstance unit, List<String> rules) implements GameEvent {
        public DoomLifted(CardInstance unit) {
            this(unit, List.of("11.3.4"));
        }
    }

    /** 8.22: {@code player} had {@code available} sacrifices to make out of {@code needed}, so nothing more applies. */
    record SacrificeFailed(PlayerId player, int needed, int available, List<String> rules) implements GameEvent {
        public SacrificeFailed(PlayerId player, int needed, int available) {
            this(player, needed, available, List.of("8.22"));
        }
    }

    enum HpLossReason {
        FATIGUE
    }

    /** What tried to make an anchored unit leave the board (11.3.2). */
    enum Removal {
        DESTROY, RETURN_TO_HAND, SACRIFICE
    }

    /** A card drawn into a full hand (3.3), or a Discard effect (8.7). */
    enum DiscardReason {
        OVERDRAW, EFFECT
    }
}
