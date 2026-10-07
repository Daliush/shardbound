package fr.daliush.shardbound.core.rules;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.event.Redaction;
import fr.daliush.shardbound.core.rules.game.Game;
import fr.daliush.shardbound.core.rules.game.Resolver;
import fr.daliush.shardbound.core.rules.game.StateCheck;
import fr.daliush.shardbound.core.rules.game.StepRunner;
import fr.daliush.shardbound.core.rules.setup.GameFactory;
import fr.daliush.shardbound.core.rules.setup.Mulligans;
import fr.daliush.shardbound.core.rules.turn.MainPhase;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.view.PlayerView;
import fr.daliush.shardbound.core.view.PlayerViews;
import java.util.List;
import java.util.Optional;

/**
 * The rules engine. It never decides anything: it applies the rules and lists the legal actions.
 * States are immutable: every call returns a new state with the events that led to it.
 */
public final class GameEngine {

    private final CardCatalog catalog;

    public GameEngine(CardCatalog catalog) {
        this.catalog = catalog;
    }

    public CardCatalog catalog() {
        return catalog;
    }

    /** Validates the decks, sets the game up (5.1) and stops at the first decision. */
    public Transition newGame(GameSetup setup) {
        Game game = GameFactory.create(catalog, setup);
        return new Transition(game.toState(), game.events());
    }

    /** Runs a state built outside a game, such as a scenario, until its next decision. It is checked first. */
    public Transition resume(GameState state) {
        Game game = Game.of(state, catalog);
        StateCheck.run(game);
        Resolver.run(game);
        return new Transition(game.toState(), game.events());
    }

    /** The decision the game is waiting for; empty once the game is over. */
    public Optional<Decision> decision(GameState state) {
        return state.isOver() ? Optional.empty() : state.pending();
    }

    /** Applies one of the pending decision's actions and runs until the next decision or the end of the game. */
    public Transition apply(GameState state, Action action) {
        Decision decision = decision(state)
                .orElseThrow(() -> new IllegalActionException("No decision is pending: the game is over"));
        if (!decision.actions().contains(action)) {
            throw new IllegalActionException(action + " is not a legal answer to decision " + decision.id());
        }
        Game game = Game.of(state, catalog);
        game.clearPending();
        if (decision.kind().pausesAStep()) {
            StepRunner.resume(game, game.popStep(), action);
        } else {
            startNewWork(game, decision, action);
        }
        Resolver.run(game);
        return new Transition(game.toState(), game.events());
    }

    /** Answering MULLIGAN or MAIN starts new work; it does not resume a paused step. */
    private static void startNewWork(Game game, Decision decision, Action action) {
        switch (decision.kind()) {
            case MULLIGAN -> Mulligans.answer(game, decision.player(), action);
            case MAIN -> MainPhase.answer(game, decision.player(), action);
            default -> throw new IllegalStateException(decision.kind() + " resumes a paused step");
        }
    }

    /** What {@code viewer} may see; {@code history} is the game's event log so far. */
    public PlayerView view(GameState state, PlayerId viewer, List<GameEvent> history) {
        return PlayerViews.of(state, viewer, eventsFor(history, viewer));
    }

    /** The same events, as {@code viewer} is allowed to see them. */
    public List<GameEvent> eventsFor(List<GameEvent> events, PlayerId viewer) {
        return Redaction.forViewer(events, viewer);
    }
}
