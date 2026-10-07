package fr.daliush.shardbound.api.domain.mappers.view;

import fr.daliush.shardbound.api.domain.bo.game.GameId;
import fr.daliush.shardbound.api.domain.bo.game.SeatSnapshot;
import fr.daliush.shardbound.api.domain.bo.view.CardRef;
import fr.daliush.shardbound.api.domain.bo.view.GameView;
import fr.daliush.shardbound.api.domain.bo.view.HandCardView;
import fr.daliush.shardbound.api.domain.bo.view.OpponentView;
import fr.daliush.shardbound.api.domain.bo.view.RelicView;
import fr.daliush.shardbound.api.domain.bo.view.SelfView;
import fr.daliush.shardbound.api.domain.bo.view.UnitView;
import fr.daliush.shardbound.core.content.AttackAbility;
import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.Deck;
import fr.daliush.shardbound.core.content.SpellCard;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.rules.combat.AttackDamage;
import fr.daliush.shardbound.core.rules.play.Costs;
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
import org.springframework.stereotype.Component;

/** Builds a seat's {@link GameView} from its snapshot: the engine's view, and the texts of its own decision. */
@Component
public class GameViewMapper {

    private final CardCatalog catalog;
    private final DecisionViewMapper decisions;

    public GameViewMapper(GameEngine engine, DecisionViewMapper decisions) {
        this.catalog = engine.catalog();
        this.decisions = decisions;
    }

    public GameView toView(GameId game, int version, SeatSnapshot snapshot) {
        PlayerView view = snapshot.view();
        PlayerId seat = view.viewer();
        return new GameView(game.toString(), version,
                view.result().isPresent() ? GameView.FINISHED : GameView.IN_PROGRESS, view.turn(),
                Optional.of(Wire.side(seat, view.active())), view.yourTurn(),
                self(view.self(), snapshot.handCosts(), seat, view.turn()),
                Optional.of(opponent(view.opponent(), seat, view.turn())),
                view.decision().map(decision -> decisions.toView(decision, snapshot.decisionText().orElseThrow())),
                view.waitingFor().map(waiting -> new GameView.WaitingForView(Wire.side(seat, waiting.player()),
                        Wire.name(waiting.kind()))),
                view.result().map(result -> Wire.result(result, seat)));
    }

    /** A game against a human, before the opponent has joined: only the creator's deck is known. */
    public GameView waiting(GameId game, Deck deck) {
        SelfView you = new SelfView(Wire.name(deck.faction()), PlayerState.STARTING_HP, PlayerState.MAX_HP, 0, 0, 0,
                0, deck.size(), List.of(), false, List.of(), List.of(), List.of());
        return new GameView(game.toString(), 0, GameView.WAITING_FOR_OPPONENT, 0, Optional.empty(), false, you,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private SelfView self(SelfState self, List<Integer> handCosts, PlayerId seat, int turn) {
        List<HandCardView> hand = IntStream.range(0, self.hand().size())
                .mapToObj(index -> handCard(self.hand().get(index), handCosts.get(index)))
                .toList();
        return new SelfView(Wire.name(self.faction()), self.hp(), self.maxHp(), self.shards().available(),
                self.shards().max(), self.shards().lockedNextTurn(), self.fatigue(), self.deckCount(), hand,
                self.mulliganDecided(),
                units(self.units(), seat, turn), relics(self.relics(), seat),
                self.graveyard().stream().map(CardRef::of).toList());
    }

    private OpponentView opponent(OpponentState opponent, PlayerId seat, int turn) {
        return new OpponentView(Wire.name(opponent.faction()), opponent.hp(), opponent.maxHp(),
                opponent.shards().available(), opponent.shards().max(), opponent.shards().lockedNextTurn(),
                opponent.fatigue(), opponent.deckCount(), opponent.handCount(), units(opponent.units(), seat, turn),
                relics(opponent.relics(), seat), opponent.graveyard().stream().map(CardRef::of).toList());
    }

    /** {@code cost} comes from the snapshot: cost auras need the full state. */
    private HandCardView handCard(HandCard inHand, int cost) {
        CardDefinition card = catalog.card(inHand.card().card());
        OptionalInt fractureStep = card instanceof SpellCard spell && spell.isFracture()
                ? OptionalInt.of(inHand.fractureStep() + 1)
                : OptionalInt.empty();
        return new HandCardView(inHand.id().value(), card.id().value(), cost, fractureStep);
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
