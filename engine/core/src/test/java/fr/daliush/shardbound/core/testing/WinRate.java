package fr.daliush.shardbound.core.testing;

import java.util.Locale;

/** Games won out of games played, with the Wilson score interval at 95%. Draws count as games not won. */
public record WinRate(long wins, long draws, long games) {

    private static final double Z_95 = 1.959964;

    public double rate() {
        return games == 0 ? 0 : (double) wins / games;
    }

    public double low() {
        return center() - margin();
    }

    public double high() {
        return center() + margin();
    }

    private double center() {
        double z2 = Z_95 * Z_95;
        return (rate() + z2 / (2 * games)) / (1 + z2 / games);
    }

    private double margin() {
        double z2 = Z_95 * Z_95;
        return Z_95 / (1 + z2 / games) * Math.sqrt(rate() * (1 - rate()) / games + z2 / (4.0 * games * games));
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%.1f%% [%.1f%%, %.1f%%] (%d wins, %d draws, %d games)",
                100 * rate(), 100 * low(), 100 * high(), wins, draws, games);
    }
}
