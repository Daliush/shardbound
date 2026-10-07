package fr.daliush.shardbound.api.domain.mappers.view;

import fr.daliush.shardbound.api.domain.bo.game.SeatSnapshot;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.rules.play.Costs;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.state.PlayerState;
import fr.daliush.shardbound.core.text.ActionDescriber;
import fr.daliush.shardbound.core.text.DecisionDescriber;
import fr.daliush.shardbound.core.view.PlayerView;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * A state as one seat may see it: the engine's view, plus what its hand costs and the prompt and labels of its own
 * decision, written now because cost auras and the describers read the full state.
 */
@Component
public class SeatSnapshotMapper {

    private final GameEngine engine;
    private final ActionDescriber labels;
    private final DecisionDescriber prompts;

    public SeatSnapshotMapper(GameEngine engine) {
        this.engine = engine;
        this.labels = new ActionDescriber(engine.catalog());
        this.prompts = new DecisionDescriber(engine.catalog());
    }

    public SeatSnapshot toSnapshot(GameState state, PlayerId seat) {
        PlayerView view = engine.view(state, seat, List.of());
        return new SeatSnapshot(view, handCosts(state, seat), view.decision().map(decision -> text(decision, state)));
    }

    /** 6.8: with the cost auras of both boards, without Overcharge. */
    private List<Integer> handCosts(GameState state, PlayerId seat) {
        PlayerState player = state.player(seat);
        return player.hand().stream()
                .map(inHand -> Costs.toPlay(engine.catalog(), player, state.player(seat.opponent()), inHand, false))
                .toList();
    }

    private SeatSnapshot.DecisionText text(Decision decision, GameState state) {
        return new SeatSnapshot.DecisionText(prompts.describe(decision, state), decision.actions().stream()
                .map(action -> labels.describe(action, state, decision.player()))
                .toList());
    }
}
