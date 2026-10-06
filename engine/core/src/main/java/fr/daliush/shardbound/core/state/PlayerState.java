package fr.daliush.shardbound.core.state;

import fr.daliush.shardbound.core.content.Deck;
import fr.daliush.shardbound.core.content.Faction;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * One player's side of the game. {@code decklist} is the deck they registered;
 * {@code deck} is their draw pile, top card first, hidden from everyone (3.2).
 */
public record PlayerState(
        PlayerId id,
        Faction faction,
        Deck decklist,
        int hp,
        int fatigue,
        Shards shards,
        int turnsTaken,
        boolean mulliganDecided,
        List<CardInstance> deck,
        List<HandCard> hand,
        List<Unit> units,
        List<Relic> relics,
        List<CardInstance> graveyard) {

    public static final int STARTING_HP = 50;
    public static final int MAX_HP = 50;
    public static final int MAX_HAND = 10;
    public static final int MAX_UNIT_PLACES = 6;
    public static final int MAX_RELICS = 3;

    public PlayerState {
        deck = List.copyOf(deck);
        hand = List.copyOf(hand);
        units = List.copyOf(units);
        relics = List.copyOf(relics);
        graveyard = List.copyOf(graveyard);
    }

    public static PlayerState starting(PlayerId id, Deck decklist, List<CardInstance> deck) {
        return new PlayerState(id, decklist.faction(), decklist, STARTING_HP, 0, Shards.none(), 0, false,
                deck, List.of(), List.of(), List.of(), List.of());
    }

    public Optional<Unit> unit(InstanceId unitId) {
        return units.stream().filter(unit -> unit.id().equals(unitId)).findFirst();
    }

    public Optional<Relic> relic(InstanceId relicId) {
        return relics.stream().filter(relic -> relic.id().equals(relicId)).findFirst();
    }

    public Optional<HandCard> handCard(InstanceId cardId) {
        return hand.stream().filter(card -> card.id().equals(cardId)).findFirst();
    }

    public boolean handIsFull() {
        return hand.size() >= MAX_HAND;
    }

    public PlayerState withHp(int newHp) {
        return change(draft -> draft.hp = newHp);
    }

    public PlayerState withFatigue(int newFatigue) {
        return change(draft -> draft.fatigue = newFatigue);
    }

    public PlayerState withShards(Shards newShards) {
        return change(draft -> draft.shards = newShards);
    }

    public PlayerState withTurnsTaken(int count) {
        return change(draft -> draft.turnsTaken = count);
    }

    public PlayerState withMulliganDecided() {
        return change(draft -> draft.mulliganDecided = true);
    }

    public PlayerState withDeck(List<CardInstance> newDeck) {
        return change(draft -> draft.deck = newDeck);
    }

    public PlayerState withHand(List<HandCard> newHand) {
        return change(draft -> draft.hand = newHand);
    }

    public PlayerState withUnits(List<Unit> newUnits) {
        return change(draft -> draft.units = newUnits);
    }

    public PlayerState withRelics(List<Relic> newRelics) {
        return change(draft -> draft.relics = newRelics);
    }

    public PlayerState withGraveyard(List<CardInstance> newGraveyard) {
        return change(draft -> draft.graveyard = newGraveyard);
    }

    public PlayerState addToHand(HandCard card) {
        return withHand(append(hand, card));
    }

    public PlayerState addToGraveyard(CardInstance card) {
        return withGraveyard(append(graveyard, card));
    }

    public PlayerState removeFromHand(InstanceId cardId) {
        return withHand(hand.stream().filter(card -> !card.id().equals(cardId)).toList());
    }

    public PlayerState addUnit(Unit unit) {
        return withUnits(append(units, unit));
    }

    public PlayerState removeUnit(InstanceId unitId) {
        return withUnits(units.stream().filter(unit -> !unit.id().equals(unitId)).toList());
    }

    public PlayerState replaceUnit(Unit changed) {
        return withUnits(units.stream().map(unit -> unit.id().equals(changed.id()) ? changed : unit).toList());
    }

    public PlayerState addRelic(Relic relic) {
        return withRelics(append(relics, relic));
    }

    public PlayerState removeRelic(InstanceId relicId) {
        return withRelics(relics.stream().filter(relic -> !relic.id().equals(relicId)).toList());
    }

    private static <T> List<T> append(List<T> list, T item) {
        List<T> copy = new ArrayList<>(list);
        copy.add(item);
        return copy;
    }

    private PlayerState change(Consumer<Draft> edit) {
        Draft draft = new Draft(this);
        edit.accept(draft);
        return draft.toPlayerState();
    }

    /** A mutable copy, so each change above only names the fields it touches. */
    private static final class Draft {
        private final PlayerState player;
        int hp;
        int fatigue;
        Shards shards;
        int turnsTaken;
        boolean mulliganDecided;
        List<CardInstance> deck;
        List<HandCard> hand;
        List<Unit> units;
        List<Relic> relics;
        List<CardInstance> graveyard;

        Draft(PlayerState player) {
            this.player = player;
            hp = player.hp;
            fatigue = player.fatigue;
            shards = player.shards;
            turnsTaken = player.turnsTaken;
            mulliganDecided = player.mulliganDecided;
            deck = player.deck;
            hand = player.hand;
            units = player.units;
            relics = player.relics;
            graveyard = player.graveyard;
        }

        PlayerState toPlayerState() {
            return new PlayerState(player.id, player.faction, player.decklist, hp, fatigue, shards, turnsTaken,
                    mulliganDecided, deck, hand, units, relics, graveyard);
        }
    }
}
