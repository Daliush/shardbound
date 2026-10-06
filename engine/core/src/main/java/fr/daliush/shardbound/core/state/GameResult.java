package fr.daliush.shardbound.core.state;

/** How a game ended (1.2, 1.3, 1.5). */
public sealed interface GameResult {

    enum EndReason {
        HP, DOUBLE_KO, TURN_LIMIT
    }

    EndReason reason();

    record Win(PlayerId winner, EndReason reason) implements GameResult {}

    record Draw(EndReason reason) implements GameResult {}
}
