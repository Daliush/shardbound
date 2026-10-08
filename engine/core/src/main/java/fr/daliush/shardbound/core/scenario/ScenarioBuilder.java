package fr.daliush.shardbound.core.scenario;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.content.Deck;
import fr.daliush.shardbound.core.content.DeckId;
import fr.daliush.shardbound.core.content.Faction;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.resolution.Resolution;
import fr.daliush.shardbound.core.rules.board.BoardSpace;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.HandCard;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.PlayerState;
import fr.daliush.shardbound.core.state.Relic;
import fr.daliush.shardbound.core.state.Shards;
import fr.daliush.shardbound.core.state.Unit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Builds any legal game state without playing a game (design doc §3.4): a board, hands, decks and counters.
 * Instance ids and arrival order follow the order of the calls.
 */
public final class ScenarioBuilder {

    private final CardCatalog catalog;
    private final Map<PlayerId, PlayerState> players = new EnumMap<>(PlayerId.class);
    private int turn = 3;
    private PlayerId active = PlayerId.P1;
    private PlayerId firstPlayer = PlayerId.P1;
    private long seed = 1;
    private int nextInstanceId = 1;
    private int nextArrivalSeq = 1;

    private ScenarioBuilder(CardCatalog catalog) {
        this.catalog = catalog;
        for (PlayerId id : PlayerId.values()) {
            Deck decklist = new Deck(new DeckId("scenario-" + id), "Scenario", Optional.empty(), Faction.NEUTRAL,
                    List.of());
            players.put(id, PlayerState.starting(id, decklist, List.of()).withMulliganDecided());
        }
    }

    public static ScenarioBuilder of(CardCatalog catalog) {
        return new ScenarioBuilder(catalog);
    }

    /** The global turn number: turn 1 is the first player's first turn. */
    public ScenarioBuilder turn(int number) {
        turn = number;
        return this;
    }

    public ScenarioBuilder active(PlayerId player) {
        active = player;
        return this;
    }

    public ScenarioBuilder firstPlayer(PlayerId player) {
        firstPlayer = player;
        return this;
    }

    public ScenarioBuilder seed(long value) {
        seed = value;
        return this;
    }

    public ScenarioBuilder hp(PlayerId player, int hp) {
        return change(player, state -> state.withHp(hp));
    }

    public ScenarioBuilder fatigue(PlayerId player, int fatigue) {
        return change(player, state -> state.withFatigue(fatigue));
    }

    /** Available Shards this turn; the maximum is at least as high. */
    public ScenarioBuilder shards(PlayerId player, int available) {
        return change(player, state -> state.withShards(new Shards(
                Math.max(available, state.shards().max()), available, state.shards().lockedNextTurn())));
    }

    /** Shards Overcharge locks on the player's next turn (11.4.2). */
    public ScenarioBuilder lockedShards(PlayerId player, int locked) {
        return change(player, state -> state.withShards(
                new Shards(state.shards().max(), state.shards().available(), locked)));
    }

    public ScenarioBuilder maxShards(PlayerId player, int max) {
        return change(player, state -> state.withShards(
                new Shards(max, state.shards().available(), state.shards().lockedNextTurn())));
    }

    public ScenarioBuilder decklist(PlayerId player, Deck deck) {
        PlayerState state = players.get(player);
        players.put(player, new PlayerState(player, deck.faction(), deck, state.hp(), state.fatigue(),
                state.shards(), state.turnsTaken(), state.mulliganDecided(), state.deck(), state.hand(),
                state.units(), state.relics(), state.graveyard()));
        return this;
    }

    /** The draw pile, top card first. */
    public ScenarioBuilder deck(PlayerId player, String... cards) {
        List<CardInstance> deck = new ArrayList<>(players.get(player).deck());
        for (String card : cards) {
            deck.add(newCard(card, player));
        }
        return change(player, state -> state.withDeck(deck));
    }

    public ScenarioBuilder hand(PlayerId player, String... cards) {
        for (String card : cards) {
            CardInstance instance = newCard(card, player);
            change(player, state -> state.addToHand(HandCard.fresh(instance)));
        }
        return this;
    }

    /** A Fracture card in hand whose next step is {@code nextStep}, 1-based, its last step played on an earlier turn. */
    public ScenarioBuilder handAtStep(PlayerId player, String card, int nextStep) {
        CardInstance instance = newCard(card, player);
        return change(player, state -> state.addToHand(new HandCard(instance, nextStep - 1, 0)));
    }

    public ScenarioBuilder graveyard(PlayerId player, String... cards) {
        for (String card : cards) {
            CardInstance instance = newCard(card, player);
            change(player, state -> state.addToGraveyard(instance));
        }
        return this;
    }

    public ScenarioBuilder unit(PlayerId player, String card) {
        return unit(player, card, setup -> setup);
    }

    public ScenarioBuilder unit(PlayerId player, String card, UnaryOperator<UnitSetup> customize) {
        UnitSetup setup = customize.apply(new UnitSetup());
        UnitCard definition = catalog.unit(new CardId(card));
        int arrivedTurn = setup.arrivedThisTurn ? turn : Math.max(0, turn - 1);
        Unit unit = Unit.arriving(newCard(card, player), player, definition.token(), definition.defense(),
                nextArrivalSeq++, arrivedTurn).frozenThrough(setup.frozenThroughTurn);
        if (setup.defense.isPresent()) {
            unit = unit.withDefense(setup.defense.getAsInt());
        }
        if (setup.hasAttacked) {
            unit = unit.markHasAttacked();
        }
        if (setup.hasIntercepted) {
            unit = unit.markHasIntercepted();
        }
        if (setup.anchorProtected) {
            unit = unit.withAnchorProtection();
        }
        if (setup.doomed) {
            unit = unit.markDoomed();
        }
        Unit placed = unit;
        return change(player, state -> state.addUnit(placed));
    }

    /** Links the first unit with card {@code card} to the first other unit with card {@code other} (11.5.1). */
    public ScenarioBuilder link(String card, String other) {
        List<Unit> units = players.values().stream().flatMap(state -> state.units().stream())
                .sorted(Comparator.comparingInt(Unit::arrivalSeq)).toList();
        Unit first = firstWithCard(units, card, Optional.empty());
        Unit second = firstWithCard(units, other, Optional.of(first.id()));
        change(first.controller(), state -> state.replaceUnit(first.linkedWith(second.id())));
        return change(second.controller(), state -> state.replaceUnit(second.linkedWith(first.id())));
    }

    private static Unit firstWithCard(List<Unit> units, String card, Optional<InstanceId> except) {
        return units.stream()
                .filter(unit -> unit.card().equals(new CardId(card)) && except.filter(unit.id()::equals).isEmpty())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No unit " + card + " to link"));
    }

    public ScenarioBuilder relic(PlayerId player, String card) {
        CardInstance instance = newCard(card, player);
        Relic relic = new Relic(instance.id(), instance.card(), player, player, nextArrivalSeq++);
        return change(player, state -> state.addRelic(relic));
    }

    /** The state, checked against the board and hand limits; no decision is pending yet. */
    public GameState build() {
        for (PlayerId id : PlayerId.values()) {
            check(players.get(id));
        }
        PlayerState p1 = withTurnsTaken(players.get(PlayerId.P1));
        PlayerState p2 = withTurnsTaken(players.get(PlayerId.P2));
        return new GameState(turn, active, firstPlayer, p1, p2, Resolution.idle(), Optional.empty(), seed,
                nextInstanceId, nextArrivalSeq, 0, Optional.empty());
    }

    private PlayerState withTurnsTaken(PlayerState state) {
        int taken = state.id() == firstPlayer ? (turn + 1) / 2 : turn / 2;
        return state.withTurnsTaken(taken);
    }

    private void check(PlayerState state) {
        if (BoardSpace.unitPlacesUsed(state, catalog) > PlayerState.MAX_UNIT_PLACES
                || state.relics().size() > PlayerState.MAX_RELICS
                || state.hand().size() > PlayerState.MAX_HAND) {
            throw new IllegalStateException("The scenario breaks the zone limits of " + state.id() + " (3.3, 3.4)");
        }
        for (Unit unit : state.units()) {
            unit.linkedTo().ifPresent(partner -> {
                if (!partnerOf(partner).flatMap(Unit::linkedTo).equals(Optional.of(unit.id()))) {
                    throw new IllegalStateException("The link of " + unit.id() + " is not mutual (11.5.1)");
                }
            });
        }
    }

    private Optional<Unit> partnerOf(InstanceId id) {
        return players.values().stream().flatMap(state -> state.unit(id).stream()).findFirst();
    }

    private CardInstance newCard(String card, PlayerId owner) {
        CardId id = new CardId(card);
        catalog.card(id);
        return new CardInstance(InstanceId.of(nextInstanceId++), id, owner);
    }

    private ScenarioBuilder change(PlayerId player, UnaryOperator<PlayerState> edit) {
        players.put(player, edit.apply(players.get(player)));
        return this;
    }
}
