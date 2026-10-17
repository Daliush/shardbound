package fr.daliush.shardbound.api.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The {@code shardbound.*} properties (spec §3.4). {@code contentDir} is found by walking up when not set, and
 * {@code reportsDir} and {@code datasetsDir} are then the repository's {@code evals/reports} and
 * {@code evals/datasets}.
 */
@ConfigurationProperties("shardbound")
public record ShardboundProperties(
        Path contentDir,
        Path reportsDir,
        Path datasetsDir,
        @DefaultValue Sessions sessions,
        @DefaultValue Bots bots,
        @DefaultValue Websocket websocket) {

    public record Sessions(
            @DefaultValue("30m") Duration finishedTtl,
            @DefaultValue("2h") Duration idleTtl,
            @DefaultValue("1m") Duration evictionInterval) {
    }

    /** {@code stepDelay}: an optional pause between two bot moves; the client paces them already. */
    public record Bots(@DefaultValue("0ms") Duration stepDelay) {
    }

    /** The origin patterns a browser may open a game's WebSocket from: local dev servers, whatever their port. */
    public record Websocket(@DefaultValue({"http://localhost:*", "http://127.0.0.1:*", "http://[::1]:*"})
                            List<String> allowedOrigins) {
    }
}
