package fr.daliush.shardbound.api.domain.mappers.view;

import fr.daliush.shardbound.api.domain.bo.game.SeatSnapshot;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.core.text.ActionDescriber;
import fr.daliush.shardbound.core.text.DecisionDescriber;
import fr.daliush.shardbound.core.view.PlayerView;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * A state as one seat may see it: the engine's view, plus the prompt and labels of the seat's own decision,
 * written now because the describers read the full state.
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
        return new SeatSnapshot(view, view.decision().map(decision -> text(decision, state)));
    }

    private SeatSnapshot.DecisionText text(Decision decision, GameState state) {
        return new SeatSnapshot.DecisionText(prompts.describe(decision, state), decision.actions().stream()
                .map(action -> labels.describe(action, state, decision.player()))
                .toList());
    }
}
