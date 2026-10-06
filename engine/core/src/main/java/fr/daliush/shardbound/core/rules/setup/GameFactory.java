package fr.daliush.shardbound.core.rules.setup;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardId;
import fr.daliush.shardbound.core.content.Deck;
import fr.daliush.shardbound.core.content.DeckValidator;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.random.SplitMix64;
import fr.daliush.shardbound.core.resolution.Resolution;
import fr.daliush.shardbound.core.rules.GameSetup;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.rules.turn.CardDraws;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.PlayerState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 5.1: a new game, up to the first mulligan decision. */
public final class GameFactory {

    public static final int OPENING_HAND_SIZE = 5;

    private GameFactory() {
    }

    public static Game create(CardCatalog catalog, GameSetup setup) {
        DeckValidator validator = new DeckValidator(catalog);
        requireLegal(validator, PlayerId.P1, setup.p1Deck());
        requireLegal(validator, PlayerId.P2, setup.p2Deck());

        SplitMix64 rng = new SplitMix64(setup.seed());
        PlayerId first = rng.nextBoolean() ? PlayerId.P1 : PlayerId.P2;
        // Ids follow the decklists, before the shuffle: an id never says more than the card it names.
        List<CardInstance> p1Cards = instances(setup.p1Deck(), PlayerId.P1, 1);
        List<CardInstance> p2Cards = instances(setup.p2Deck(), PlayerId.P2, p1Cards.size() + 1);
        rng.shuffle(p1Cards);
        rng.shuffle(p2Cards);

        GameState initial = new GameState(0, first, first,
                PlayerState.starting(PlayerId.P1, setup.p1Deck(), p1Cards),
                PlayerState.starting(PlayerId.P2, setup.p2Deck(), p2Cards),
                Resolution.idle(), Optional.empty(), rng.state(), p1Cards.size() + p2Cards.size() + 1, 1, 0,
                Optional.empty());
        Game game = Game.of(initial, catalog);
        game.emit(new GameEvent.GameStarted(first));
        drawOpeningHand(game, first);
        drawOpeningHand(game, first.opponent());
        Mulligans.ask(game, first);
        return game;
    }

    private static void requireLegal(DeckValidator validator, PlayerId seat, Deck deck) {
        List<String> problems = validator.problems(deck);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(
                    "Deck " + deck.id() + " of " + seat + " " + String.join("; ", problems));
        }
    }

    private static List<CardInstance> instances(Deck deck, PlayerId owner, int firstId) {
        List<CardInstance> cards = new ArrayList<>();
        int nextId = firstId;
        for (CardId card : deck.cardList()) {
            cards.add(new CardInstance(InstanceId.of(nextId++), card, owner));
        }
        return cards;
    }

    private static void drawOpeningHand(Game game, PlayerId player) {
        for (int i = 0; i < OPENING_HAND_SIZE; i++) {
            CardDraws.draw(game, player, "5.1.2");
        }
    }
}
