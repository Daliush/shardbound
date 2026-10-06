package fr.daliush.shardbound.core.bot;

import static org.assertj.core.api.Assertions.assertThat;

import fr.daliush.shardbound.core.action.Action;
import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.decision.DecisionKind;
import fr.daliush.shardbound.core.state.InstanceId;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RandomBotTest {

    private final Decision decision = new Decision("d-1", PlayerId.P2, DecisionKind.INTERCEPT,
            IntStream.range(1, 11).<Action>mapToObj(id -> new Action.Intercept(new InstanceId(id))).toList());

    @Test
    void aBotRebuiltFromItsGeneratorStatePicksWhatTheOriginalWouldHavePicked() {
        RandomBot original = new RandomBot(42);
        original.choose(null, decision);
        original.choose(null, decision);

        RandomBot rebuilt = new RandomBot(original.rngState());

        List<Action> next = IntStream.range(0, 20).mapToObj(i -> original.choose(null, decision)).toList();
        assertThat(IntStream.range(0, 20).mapToObj(i -> rebuilt.choose(null, decision)).toList()).isEqualTo(next);
    }
}
