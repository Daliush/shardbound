package fr.daliush.shardbound.api.config;

import fr.daliush.shardbound.api.session.BotRoster;
import fr.daliush.shardbound.api.session.GameRepositoryInMemory;
import fr.daliush.shardbound.api.session.GameSessionService;
import fr.daliush.shardbound.api.session.GameUpdates;
import fr.daliush.shardbound.api.session.GameUpdatesInMemory;
import fr.daliush.shardbound.api.session.PlayerConnections;
import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.rules.GameEngine;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * The session server with its in-memory adapters: one process only (spec §13.4). A shared store replaces
 * both adapters at deployment, without touching the service.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SessionConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    BotRoster botRoster() {
        return new BotRoster();
    }

    @Bean
    GameRepositoryInMemory gameRepository() {
        return new GameRepositoryInMemory();
    }

    @Bean
    GameUpdatesInMemory gameUpdates() {
        return new GameUpdatesInMemory();
    }

    @Bean
    GameSessionService gameSessionService(Content content, GameEngine engine, BotRoster bots,
                                          GameRepositoryInMemory repository, GameUpdates updates, Clock clock,
                                          ShardboundProperties properties) {
        String instance = UUID.randomUUID().toString().substring(0, 8);
        return new GameSessionService(content, engine, bots, repository, updates, clock,
                new GameSessionService.Settings(properties.bots().stepDelay(), instance));
    }

    @Bean
    PlayerConnections playerConnections(GameSessionService sessions, GameUpdates updates) {
        return new PlayerConnections(sessions, updates);
    }

    @Bean
    SessionEviction sessionEviction(GameRepositoryInMemory repository, Clock clock,
                                    ShardboundProperties properties) {
        return new SessionEviction(repository, clock, properties.sessions());
    }

    /** The in-memory store expires games itself; a shared store will use its own expiry. */
    record SessionEviction(GameRepositoryInMemory repository, Clock clock, ShardboundProperties.Sessions ttl) {

        @Scheduled(fixedDelayString = "${shardbound.sessions.eviction-interval:1m}")
        void evict() {
            repository.evictExpired(clock.instant(), ttl.finishedTtl(), ttl.idleTtl());
        }
    }
}
