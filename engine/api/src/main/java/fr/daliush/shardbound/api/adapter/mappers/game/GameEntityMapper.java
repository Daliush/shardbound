package fr.daliush.shardbound.api.adapter.mappers.game;

import fr.daliush.shardbound.api.dao.entities.game.GameEntity;
import fr.daliush.shardbound.api.dao.entities.game.SeatEntity;
import fr.daliush.shardbound.api.dao.entities.game.SeatUpdateEntity;
import fr.daliush.shardbound.api.domain.bo.game.GameId;
import fr.daliush.shardbound.api.domain.bo.game.GameSession;
import fr.daliush.shardbound.api.domain.bo.game.GameStatus;
import fr.daliush.shardbound.api.domain.bo.game.Outbox;
import fr.daliush.shardbound.api.domain.bo.game.Seat;
import fr.daliush.shardbound.api.domain.bo.game.SeatSnapshot;
import fr.daliush.shardbound.api.domain.bo.game.SeatUpdate;
import fr.daliush.shardbound.core.content.DeckId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** A game session to the entity the game DAO stores, and back. */
@Component
public class GameEntityMapper {

    private static final String HUMAN = "human";
    private static final String BOT = "bot";
    private static final String OPEN = "open";

    public GameEntity toEntity(GameSession session) {
        return new GameEntity(session.id().value(), session.version(), session.status().name(),
                session.lastActivity(), session.seed(), List.of(seat(session.p1()), seat(session.p2())),
                session.state().orElse(null), session.events(), session.actions(),
                session.outbox().updates().stream().map(GameEntityMapper::update).toList());
    }

    public GameSession toSession(GameEntity entity) {
        return new GameSession(new GameId(entity.id()), GameStatus.valueOf(entity.status()), entity.seed(),
                seat(entity.seats().get(0)), seat(entity.seats().get(1)), Optional.ofNullable(entity.state()),
                entity.version(), entity.events(), entity.actions(),
                new Outbox(entity.outbox().stream().map(GameEntityMapper::update).toList()), entity.lastActivity());
    }

    private static SeatEntity seat(Seat seat) {
        return switch (seat) {
            case Seat.Human human -> new SeatEntity(HUMAN, human.player(), human.deck().value(), human.tokenHash(),
                    null, 0, null);
            case Seat.Bot bot -> new SeatEntity(BOT, bot.player(), bot.deck().value(), null, bot.name(),
                    bot.rngState(), null);
            case Seat.Open open -> new SeatEntity(OPEN, open.player(), null, null, null, 0, open.joinCodeHash());
        };
    }

    private static Seat seat(SeatEntity seat) {
        return switch (seat.kind()) {
            case HUMAN -> new Seat.Human(seat.player(), new DeckId(seat.deck()), seat.tokenHash());
            case BOT -> new Seat.Bot(seat.player(), new DeckId(seat.deck()), seat.bot(), seat.rngState());
            case OPEN -> new Seat.Open(seat.player(), seat.joinCodeHash());
            default -> throw new IllegalStateException("Unknown seat kind " + seat.kind());
        };
    }

    private static SeatUpdateEntity update(SeatUpdate update) {
        Optional<SeatSnapshot.DecisionText> text = update.snapshot().decisionText();
        return new SeatUpdateEntity(update.version(), update.seat(), update.snapshot().view(),
                update.snapshot().handCosts(), text.map(SeatSnapshot.DecisionText::prompt).orElse(null),
                text.map(SeatSnapshot.DecisionText::labels).orElse(List.of()), update.events());
    }

    private static SeatUpdate update(SeatUpdateEntity entity) {
        Optional<SeatSnapshot.DecisionText> text = Optional.ofNullable(entity.prompt())
                .map(prompt -> new SeatSnapshot.DecisionText(prompt, entity.labels()));
        SeatSnapshot snapshot = new SeatSnapshot(entity.view(), entity.handCosts(), text);
        return new SeatUpdate(entity.version(), entity.seat(), snapshot, entity.events());
    }
}
