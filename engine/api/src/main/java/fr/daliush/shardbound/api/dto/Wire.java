package fr.daliush.shardbound.api.dto;

import fr.daliush.shardbound.core.state.GameResult;
import fr.daliush.shardbound.core.state.PlayerId;
import java.util.Locale;

/** How engine values are spelled on the wire: players as sides, names in snake_case. */
final class Wire {

    private Wire() {
    }

    /** "you" or "opponent". */
    static String side(PlayerId viewer, PlayerId player) {
        return player == viewer ? "you" : "opponent";
    }

    /** {@code DOUBLE_KO} → "double_ko". */
    static String name(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    /** {@code UnitDamaged} → "unit_damaged". */
    static String snake(String camelCase) {
        return camelCase.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    static GameView.ResultView result(GameResult result, PlayerId viewer) {
        String outcome = switch (result) {
            case GameResult.Win win -> win.winner() == viewer ? "win" : "loss";
            case GameResult.Draw ignored -> "draw";
        };
        return new GameView.ResultView(outcome, name(result.reason()));
    }
}
