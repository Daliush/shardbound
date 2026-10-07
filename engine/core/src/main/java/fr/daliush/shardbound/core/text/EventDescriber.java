package fr.daliush.shardbound.core.text;

import fr.daliush.shardbound.core.content.AttackAbility;
import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.Duration;
import fr.daliush.shardbound.core.event.EventTarget;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.GameResult;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.Shards;
import java.util.OptionalInt;

/** Turns an event into an English sentence for one player: "You draw Spark Dart.", "Sprout #61 takes 3 damage." */
public final class EventDescriber {

    private final CardCatalog catalog;

    public EventDescriber(CardCatalog catalog) {
        this.catalog = catalog;
    }

    public String describe(GameEvent event, PlayerId viewer) {
        Wording w = new Wording(catalog, viewer);
        return switch (event) {
            case GameEvent.GameStarted e -> w.subject(e.firstPlayer()) + " " + w.verb(e.firstPlayer(), "play", "plays")
                    + " first.";
            case GameEvent.CardDrawn e -> e.card()
                    .map(card -> w.subject(e.player()) + " " + w.verb(e.player(), "draw", "draws") + " "
                            + w.name(card.card()) + ".")
                    .orElse(w.subject(e.player()) + " " + w.verb(e.player(), "draw", "draws") + " a card.");
            case GameEvent.HandKept e -> w.subject(e.player()) + " " + w.verb(e.player(), "keep", "keeps") + " "
                    + w.own(e.player()) + " hand.";
            case GameEvent.MulliganTaken e -> w.subject(e.player()) + " " + w.verb(e.player(), "take", "takes")
                    + " a mulligan.";
            case GameEvent.TurnStarted e -> "Turn " + e.turn() + ": " + w.possessive(e.player()) + " turn.";
            case GameEvent.ShardsRefilled e -> w.subject(e.player()) + " " + w.verb(e.player(), "have", "has") + " "
                    + Wording.shards(e.available()) + " (max " + e.max()
                    + (e.locked() > 0 ? ", " + e.locked() + " locked" : "") + ").";
            case GameEvent.AnchorProtectionEnded e -> w.card(e.unit()) + " is no longer anchored.";
            case GameEvent.HpLost e -> w.subject(e.player()) + " " + w.verb(e.player(), "draw", "draws")
                    + " from an empty deck and " + w.verb(e.player(), "lose", "loses") + " " + e.amount()
                    + " HP (fatigue).";
            case GameEvent.CardDiscarded e -> switch (e.reason()) {
                case OVERDRAW -> capitalize(w.possessive(e.player())) + " hand is full: " + w.name(e.card().card())
                        + " goes to the graveyard.";
                case EFFECT -> w.subject(e.player()) + " " + w.verb(e.player(), "discard", "discards") + " "
                        + w.name(e.card().card()) + ".";
            };
            case GameEvent.TurnEnded e -> w.subject(e.player()) + " " + w.verb(e.player(), "end", "ends") + " "
                    + w.own(e.player()) + " turn.";
            case GameEvent.GameEnded e -> ending(e.result(), viewer);
            case GameEvent.CardPlayed e -> w.subject(e.player()) + " " + w.verb(e.player(), "play", "plays") + " "
                    + w.name(e.card().card()) + (e.overcharged() ? " overcharged" : "")
                    + ownStep(e.player(), e.fractureStep(), viewer) + " (" + Wording.shards(e.cost()) + ").";
            case GameEvent.FractureAdvanced e -> w.card(e.card()) + " returns to " + w.possessive(e.card().owner())
                    + " hand" + ownStep(e.card().owner(), OptionalInt.of(e.nextStep()), viewer) + ".";
            case GameEvent.ShardsLocked e -> capitalize(w.possessive(e.player())) + " next turn will have "
                    + Wording.shards(e.amount()) + " more locked (" + e.total() + " in all).";
            case GameEvent.UnitArrived e -> w.card(e.unit()) + " arrives on " + w.possessive(e.controller())
                    + " board.";
            case GameEvent.RelicArrived e -> w.card(e.relic()) + " arrives on " + w.possessive(e.controller())
                    + " board.";
            case GameEvent.SpellResolved e -> w.name(e.spell().card()) + " goes to " + w.possessive(e.spell().owner())
                    + " graveyard.";
            case GameEvent.TokenSummoned e -> w.card(e.unit()) + " is summoned on " + w.possessive(e.controller())
                    + " board.";
            case GameEvent.SummonFailed e -> capitalize(w.possessive(e.player())) + " board is full: no "
                    + w.name(e.token()) + " is summoned.";
            case GameEvent.AbilityTriggered e -> w.card(e.source()) + " triggers its " + Wording.trigger(e.trigger())
                    + " ability.";
            case GameEvent.EchoTriggered e -> w.card(e.unit()) + " died: " + attackName(e.unit(), e.attackIndex())
                    + " echoes at " + e.percent() + "%.";
            case GameEvent.AttackDeclared e -> attack(w, e);
            case GameEvent.AttackIntercepted e -> w.card(e.interceptor()) + " intercepts the attack aimed at "
                    + w.card(e.originalTarget()) + ".";
            case GameEvent.InterceptDeclined e -> w.subject(e.defender()) + " " + w.verb(e.defender(), "do", "does")
                    + " not intercept.";
            case GameEvent.AttackCancelled e -> w.card(e.attacker()) + " left the board: its attack does not happen.";
            case GameEvent.UnitDamaged e -> w.card(e.unit()) + " takes " + e.amount() + " damage.";
            case GameEvent.DamageShared e -> w.card(e.to()) + " takes " + e.amount() + " damage through its link with "
                    + w.card(e.from()) + ".";
            case GameEvent.PlayerDamaged e -> w.subject(e.player()) + " " + w.verb(e.player(), "take", "takes") + " "
                    + e.amount() + " damage.";
            case GameEvent.UnitHealed e -> w.card(e.unit()) + " heals " + e.amount() + " defense.";
            case GameEvent.PlayerHealed e -> w.subject(e.player()) + " " + w.verb(e.player(), "heal", "heals") + " "
                    + e.amount() + " HP.";
            case GameEvent.UnitDestroyed e -> w.card(e.unit()) + " is destroyed.";
            case GameEvent.RelicDestroyed e -> w.card(e.relic()) + " is destroyed.";
            case GameEvent.TokenVanished e -> w.card(e.unit()) + " vanishes.";
            case GameEvent.Modified e -> w.card(e.unit()) + " gets " + Wording.stats(e.attackDamage(), e.defense())
                    + (e.duration() == Duration.END_OF_TURN ? " until end of turn." : ".");
            case GameEvent.ModifierExpired e -> "The " + Wording.stats(e.attackDamage(), e.defense()) + " on "
                    + w.card(e.unit()) + " ends.";
            case GameEvent.ShardsGained e -> shardsGained(w, e);
            case GameEvent.AuraApplied e -> w.card(e.source()) + " gives " + w.card(e.unit()) + " "
                    + Wording.stats(e.attackDamage(), e.defense()) + ".";
            case GameEvent.AuraRemoved e -> w.card(e.unit()) + " loses the "
                    + Wording.stats(e.attackDamage(), e.defense()) + " of " + w.card(e.source()) + ".";
            case GameEvent.Recalled e -> w.card(e.card()) + " returns from " + w.possessive(e.card().owner())
                    + " graveyard to " + w.possessive(e.card().owner()) + " hand.";
            case GameEvent.RecallFailed e -> capitalize(w.possessive(e.card().owner())) + " hand is full: "
                    + w.card(e.card()) + " stays in the graveyard.";
            case GameEvent.Linked e -> w.card(e.first()) + " and " + w.card(e.second()) + " are linked.";
            case GameEvent.LinkBroken e -> w.card(e.left()) + " left the board: its link with " + w.card(e.partner())
                    + " breaks.";
            case GameEvent.Frozen e -> w.card(e.unit()) + " is frozen until the end of turn " + e.throughTurn() + ".";
            case GameEvent.UnitThawed e -> w.card(e.unit()) + " thaws.";
            case GameEvent.ReturnedToHand e -> w.card(e.card()) + " returns to " + w.possessive(e.card().owner())
                    + " hand.";
            case GameEvent.SentToGraveyardHandFull e -> capitalize(w.possessive(e.card().owner())) + " hand is full: "
                    + w.card(e.card()) + " goes to the graveyard.";
            case GameEvent.UnitSacrificed e -> w.card(e.unit()) + " is sacrificed.";
            case GameEvent.AnchorPrevented e -> w.card(e.unit()) + " is anchored: " + switch (e.attempt()) {
                case DESTROY -> "it is not destroyed.";
                case RETURN_TO_HAND -> "it does not return to hand.";
                case SACRIFICE -> "it stays on the board, and the sacrifice counts as paid.";
            };
            case GameEvent.UnitDoomed e -> w.card(e.unit()) + " is at 0 defense but anchored: it is doomed.";
            case GameEvent.DoomLifted e -> w.card(e.unit()) + " is no longer doomed.";
            case GameEvent.SacrificeFailed e -> w.subject(e.player()) + " cannot sacrifice "
                    + Wording.count(e.needed(), "unit", "units") + " (only " + e.available() + " on "
                    + w.own(e.player()) + " board): nothing more happens.";
        };
    }

    /** 11.2.5: Fracture steps are public, but only shown to the card's owner; the opponent has to remember them. */
    private static String ownStep(PlayerId owner, OptionalInt step, PlayerId viewer) {
        return owner == viewer && step.isPresent() ? ", step " + step.getAsInt() : "";
    }

    private String attack(Wording w, GameEvent.AttackDeclared e) {
        AttackAbility ability = catalog.unit(e.attacker().card()).attacks().get(e.attackIndex());
        String name = ability.name().orElse("its attack");
        return e.target()
                .map(target -> w.card(e.attacker()) + " attacks " + target(w, target) + " with " + name + ".")
                .orElse(w.card(e.attacker()) + " uses " + name + ".");
    }

    private String attackName(CardInstance unit, int attackIndex) {
        return catalog.unit(unit.card()).attacks().get(attackIndex).name().orElse("its attack");
    }

    private static String shardsGained(Wording w, GameEvent.ShardsGained e) {
        String gain = w.subject(e.player()) + " " + w.verb(e.player(), "gain", "gains") + " ";
        return switch (e.mode()) {
            case THIS_TURN -> gain + Wording.shards(e.amount()) + " this turn.";
            case MAX -> e.amount() > 0 ? gain + "1 max Shard."
                    : capitalize(w.possessive(e.player())) + " max Shards are already at " + Shards.CAP + ".";
        };
    }

    private static String target(Wording w, EventTarget target) {
        return switch (target) {
            case EventTarget.UnitHit unit -> w.card(unit.unit());
            case EventTarget.PlayerHit player -> w.object(player.player());
        };
    }

    private static String ending(GameResult result, PlayerId viewer) {
        return switch (result) {
            case GameResult.Win win when win.winner() == viewer -> "You win: your opponent is at 0 HP.";
            case GameResult.Win ignored -> "You lose: you are at 0 HP.";
            case GameResult.Draw draw when draw.reason() == GameResult.EndReason.TURN_LIMIT ->
                    "Draw: each player has played 50 turns.";
            case GameResult.Draw ignored -> "Draw: both players are at 0 HP.";
        };
    }

    private static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
