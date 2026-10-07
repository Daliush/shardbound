package fr.daliush.shardbound.api.config;

import fr.daliush.shardbound.api.dao.game.GameDaoInMemory;
import fr.daliush.shardbound.api.domain.services.game.GameSettings;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * The clock, the game settings, and the eviction of the in-memory games. The DAOs in use are the in-memory ones
 * (spec §13.4): one process only, until a shared store replaces them.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class GameConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    GameSettings gameSettings(ShardboundProperties properties) {
        return new GameSettings(properties.bots().stepDelay(), UUID.randomUUID().toString().substring(0, 8));
    }

    @Bean
    InMemoryEviction inMemoryEviction(GameDaoInMemory dao, Clock clock, ShardboundProperties properties) {
        return new InMemoryEviction(dao, clock, properties.sessions());
    }

    /** The in-memory DAO expires games itself; a shared store will use its own expiry. */
    record InMemoryEviction(GameDaoInMemory dao, Clock clock, ShardboundProperties.Sessions ttl) {

        @Scheduled(fixedDelayString = "${shardbound.sessions.eviction-interval:1m}")
        void evict() {
            dao.evictExpired(clock.instant(), ttl.finishedTtl(), ttl.idleTtl());
        }
    }
}
