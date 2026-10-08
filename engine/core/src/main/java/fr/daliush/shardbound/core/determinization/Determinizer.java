package fr.daliush.shardbound.core.determinization;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.random.SplitMix64;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.HandCard;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.PlayerState;
import fr.daliush.shardbound.core.view.PlayerView;
import java.util.List;

/**
 * Builds a complete, plausible game from one player's view alone (spec §10, design doc §6.5): what the view shows is
 * copied as it is, and what it hides is guessed. It never reads the real game, so a bot that simulates with it cannot
 * cheat. The package's only entry point.
 */
public final class Determinizer {

    private final CardCatalog catalog;

    public Determinizer(CardCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * A game the viewer cannot tell from the real one, paused on the viewer's decision:
     * <ol>
     *     <li>the opponent's cards the history saw go back to their hand stay as they are ({@link KnownHand});</li>
     *     <li>every card the view shows is set aside ({@link SeenCards}), and so are the ids nobody has seen
     *     ({@link InstanceIds});</li>
     *     <li>the viewer's deck is their decklist minus their cards seen elsewhere, shuffled ({@link OwnDeck});</li>
     *     <li>the rest of the opponent's hand and their deck are drawn among the copies their faction and the neutral
     *     cards have left ({@link RemainingCopies});</li>
     *     <li>the fields a view does not carry are derived ({@link DerivedFields}), each side is put together
     *     ({@link Sides}), and the game gets a fresh generator drawn from {@code seed}.</li>
     * </ol>
     * The same view and the same seed always give the same game.
     *
     * @throws IllegalArgumentException if the viewer is not deciding, or the history does not start with the game
     */
    public GameState determinize(PlayerView view, long seed) {
        if (view.decision().isEmpty()) {
            throw new IllegalArgumentException("Only a view whose player is deciding can be determinized");
        }
        SplitMix64 rng = new SplitMix64(seed);
        PlayerId opponentId = view.viewer().opponent();

        List<HandCard> knownHand = KnownHand.of(view);
        SeenCards seen = SeenCards.of(view, knownHand);
        InstanceIds ids = InstanceIds.of(view, seen);
        List<CardInstance> ownDeck = OwnDeck.shuffled(view, seen, ids, rng);
        List<CardInstance> unknownCards = RemainingCopies.of(catalog, view, seen).draw(ids.unseenOf(opponentId), rng);
        DerivedFields derived = DerivedFields.of(view);

        PlayerState viewer = Sides.viewer(view, ownDeck, derived);
        PlayerState opponent = Sides.opponent(view, seen, knownHand, unknownCards, derived);
        return new GameState(view.turn(), view.active(), derived.firstPlayer(),
                view.viewer() == PlayerId.P1 ? viewer : opponent, view.viewer() == PlayerId.P1 ? opponent : viewer,
                view.resolution(), view.decision(), rng.nextLong(), ids.next(), derived.nextArrivalSeq(),
                derived.decisionSeq(), view.result());
    }
}
