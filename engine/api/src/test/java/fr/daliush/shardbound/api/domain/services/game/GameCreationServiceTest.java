package fr.daliush.shardbound.api.domain.services.game;

import static fr.daliush.shardbound.core.state.PlayerId.P1;
import static fr.daliush.shardbound.core.state.PlayerId.P2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.daliush.shardbound.api.dao.game.GameDaoInMemory;
import fr.daliush.shardbound.api.domain.bo.command.NewGame;
import fr.daliush.shardbound.api.domain.bo.command.SeatAccess;
import fr.daliush.shardbound.api.domain.bo.error.GameException;
import fr.daliush.shardbound.api.domain.bo.game.GameId;
import fr.daliush.shardbound.api.domain.bo.game.GameSession;
import fr.daliush.shardbound.api.domain.bo.game.GameStatus;
import fr.daliush.shardbound.api.domain.bo.game.SeatUpdate;
import fr.daliush.shardbound.api.testing.RecordingNotificationDao;
import fr.daliush.shardbound.api.testing.TestInstance;
import fr.daliush.shardbound.core.event.GameEvent;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class GameCreationServiceTest {

    private final RecordingNotificationDao notifications = new RecordingNotificationDao();
    private final TestInstance server = new TestInstance(new GameDaoInMemory(), notifications, "a");

    @Test
    void aGameAgainstAHumanWaitsForTheJoinWhichIsSavedAsVersionOne() {
        SeatAccess creator = server.creation.create(TestInstance.humanGame(5));
        GameId id = creator.game();
        assertThat(server.session(id).status()).isEqualTo(GameStatus.WAITING_FOR_OPPONENT);
        assertThat(creator.joinCode()).isPresent();

        SeatAccess joiner = server.creation.join(id, creator.joinCode().orElseThrow(), "root-starter");

        GameSession started = server.session(id);
        assertThat(started.version()).isEqualTo(1);
        assertThat(notifications.versions(id.value())).containsExactly(1);
        SeatUpdate setup = started.outbox().after(0, 1, P1).orElseThrow().getFirst();
        assertThat(setup.events().getFirst()).isInstanceOf(GameEvent.GameStarted.class);
        assertThat(setup.snapshot().view().turn()).as("the mulligans come first").isZero();
        assertThat(server.authentication.authenticate(id, creator.playerToken())).isEqualTo(P1);
        assertThat(server.authentication.authenticate(id, joiner.playerToken())).isEqualTo(P2);
    }

    @Test
    void refusesAWrongJoinCodeAndASecondJoin() {
        SeatAccess creator = server.creation.create(TestInstance.humanGame(5));
        GameId id = creator.game();

        assertThatThrownBy(() -> server.creation.join(id, "wrong", "root-starter"))
                .isInstanceOf(GameException.JoinRefused.class);
        server.creation.join(id, creator.joinCode().orElseThrow(), "root-starter");
        assertThatThrownBy(() -> server.creation.join(id, creator.joinCode().orElseThrow(), "root-starter"))
                .isInstanceOf(GameException.JoinRefused.class);
    }

    @Test
    void refusesUnknownDecksBotsGamesAndTokens() {
        assertThatThrownBy(() -> server.creation.create(new NewGame("emberr-starter", new NewGame.Opponent.Human(),
                OptionalLong.empty()))).isInstanceOf(GameException.InvalidRequest.class)
                .hasMessage("Unknown deck: emberr-starter");
        assertThatThrownBy(() -> server.creation.create(new NewGame("ember-starter",
                new NewGame.Opponent.Bot("genius", "root-starter"), OptionalLong.empty())))
                .isInstanceOf(GameException.InvalidRequest.class);
        assertThatThrownBy(() -> server.creation.join(GameId.random(), "code", "root-starter"))
                .isInstanceOf(GameException.UnknownGame.class);
        GameId id = server.creation.create(TestInstance.botGame(1)).game();
        assertThatThrownBy(() -> server.authentication.authenticate(id, "not-a-token"))
                .isInstanceOf(GameException.InvalidToken.class);
    }
}
