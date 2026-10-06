package fr.daliush.shardbound.core.state;

import fr.daliush.shardbound.core.decision.Decision;
import fr.daliush.shardbound.core.resolution.Resolution;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The whole game at one moment, hidden information included: it never leaves the server.
 * {@code turn} is the global turn number, 1 for the first turn after mulligans and 0 during setup.
 * {@code rng} is the state of the game's random generator.
 */
public record GameState(
        int turn,
        PlayerId active,
        PlayerId firstPlayer,
        PlayerState p1,
        PlayerState p2,
        Resolution resolution,
        Optional<Decision> pending,
        long rng,
        int nextInstanceId,
        int nextArrivalSeq,
        int decisionSeq,
        Optional<GameResult> result) {

    public PlayerState player(PlayerId id) {
        return id == PlayerId.P1 ? p1 : p2;
    }

    public boolean isOver() {
        return result.isPresent();
    }

    public Optional<Unit> unit(InstanceId id) {
        return p1.unit(id).or(() -> p2.unit(id));
    }

    /** Every unit on the board, oldest arrival first: the canonical order of targets (spec §6.2). */
    public List<Unit> unitsByArrival() {
        return Stream.concat(p1.units().stream(), p2.units().stream())
                .sorted(Comparator.comparingInt(Unit::arrivalSeq))
                .toList();
    }
}
