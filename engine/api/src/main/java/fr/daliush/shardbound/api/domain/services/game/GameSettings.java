package fr.daliush.shardbound.api.domain.services.game;

import java.time.Duration;

/** {@code botStepDelay}: an optional pause between two bot moves. {@code instance}: this server's name in logs. */
public record GameSettings(Duration botStepDelay, String instance) {
}
