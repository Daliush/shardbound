package fr.daliush.shardbound.api.dto;

import fr.daliush.shardbound.core.content.AttackAbility;
import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.Deck;
import fr.daliush.shardbound.core.content.SpellCard;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.rules.combat.AttackDamage;
import fr.daliush.shardbound.core.rules.play.Costs;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.HandCard;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.PlayerState;
import fr.daliush.shardbound.core.state.Relic;
import fr.daliush.shardbound.core.state.Unit;
import fr.daliush.shardbound.core.view.OpponentState;
import fr.daliush.shardbound.core.view.PlayerView;
import fr.daliush.shardbound.core.view.SelfState;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;

/**
 * Builds a seat's {@link GameView}. Its data comes from the engine's {@link PlayerView}; the full state is only
 * read by the engine's describers, for the labels of the seat's own decision.
 */
public final class GameViews {

    private final GameEngine engine;
    private final CardCatalog catalog;
    private final DecisionViews decisions;

    public GameViews(GameEngine engine) {
        this.engine = engine;
        this.catalog = engine.catalog();
        this.decisions = new DecisionViews(catalog);
    }

    public GameView of(String gameId, int version, GameState state, PlayerId seat) {
        PlayerView view = engine.view(state, seat, List.of());
        return new GameView(gameId, version, state.isOver() ? GameView.FINISHED : GameView.IN_PROGRESS, view.turn(),
                Optional.of(Wire.side(seat, view.active())), view.yourTurn(), self(view.self(), seat, view.turn()),
                Optional.of(opponent(view.opponent(), seat, view.turn())),
                view.decision().map(decision -> decisions.of(decision, state)),
                view.waitingFor().map(waiting -> new GameView.WaitingForView(Wire.side(seat, waiting.player()),
                        Wire.name(waiting.kind()))),
                view.result().map(result -> Wire.result(result, seat)));
    }

    /** A game against a human, before the opponent has joined: only the creator's deck is known. */
    public GameView waiting(String gameId, Deck deck) {
        SelfView you = new SelfView(Wire.name(deck.faction()), PlayerState.STARTING_HP, PlayerState.MAX_HP, 0, 0, 0,
                0, deck.size(), List.of(), false, List.of(), List.of(), List.of());
        return new GameView(gameId, 0, GameView.WAITING_FOR_OPPONENT, 0, Optional.empty(), false, you,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private SelfView self(SelfState self, PlayerId seat, int turn) {
        return new SelfView(Wire.name(self.faction()), self.hp(), self.maxHp(), self.shards().available(),
                self.shards().max(), self.shards().lockedNextTurn(), self.fatigue(), self.deckCount(),
                self.hand().stream().map(this::handCard).toList(), self.mulliganDecided(),
                units(self.units(), seat, turn), relics(self.relics(), seat),
                self.graveyard().stream().map(CardRef::of).toList());
    }

    private OpponentView opponent(OpponentState opponent, PlayerId seat, int turn) {
        return new OpponentView(Wire.name(opponent.faction()), opponent.hp(), opponent.maxHp(),
                opponent.shards().available(), opponent.shards().max(), opponent.shards().lockedNextTurn(),
                opponent.fatigue(), opponent.deckCount(), opponent.handCount(), units(opponent.units(), seat, turn),
                relics(opponent.relics(), seat), opponent.graveyard().stream().map(CardRef::of).toList());
    }

    private HandCardView handCard(HandCard inHand) {
        CardDefinition card = catalog.card(inHand.card().card());
        OptionalInt fractureStep = card instanceof SpellCard spell && spell.isFracture()
                ? OptionalInt.of(inHand.fractureStep() + 1)
                : OptionalInt.empty();
        return new HandCardView(inHand.id().value(), card.id().value(), Costs.toPlay(card, inHand, false),
                fractureStep);
    }

    private List<UnitView> units(List<Unit> units, PlayerId seat, int turn) {
        return units.stream().map(unit -> unit(unit, seat, turn)).toList();
    }

    private UnitView unit(Unit unit, PlayerId seat, int turn) {
        UnitCard card = catalog.unit(unit.card());
        List<UnitView.AttackView> attacks = IntStream.range(0, card.attacks().size())
                .mapToObj(index -> attack(index, card.attacks().get(index), unit))
                .toList();
        OptionalInt linkedTo = unit.linkedTo().map(id -> OptionalInt.of(id.value())).orElse(OptionalInt.empty());
        List<UnitView.ModifierView> modifiers = unit.modifiers().stream()
                .map(modifier -> new UnitView.ModifierView(modifier.attackDamage(), modifier.defense(),
                        Wire.name(modifier.duration())))
                .toList();
        return new UnitView(unit.id().value(), unit.card().value(), Wire.side(seat, unit.controller()), unit.token(),
                unit.defense(), unit.maxDefense(), attacks, unit.arrivedOn(turn), unit.hasAttackedThisTurn(),
                unit.hasInterceptedThisTurn(), unit.isFrozen(turn), unit.anchorProtected(), unit.doomed(), linkedTo,
                modifiers);
    }

    private static UnitView.AttackView attack(int index, AttackAbility attack, Unit attacker) {
        return new UnitView.AttackView(index, attack.name(), Costs.toAttack(attack),
                AttackDamage.toTarget(attack, attacker), attack.hasTarget(), attack.echo());
    }

    private static List<RelicView> relics(List<Relic> relics, PlayerId seat) {
        return relics.stream()
                .map(relic -> new RelicView(relic.id().value(), relic.card().value(),
                        Wire.side(seat, relic.controller())))
                .toList();
    }
}
