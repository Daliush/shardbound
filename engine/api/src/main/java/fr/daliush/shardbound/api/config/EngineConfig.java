package fr.daliush.shardbound.api.config;

import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.content.ContentLoader;
import fr.daliush.shardbound.core.rules.GameEngine;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The game content and the rules engine, loaded once at startup. */
@Configuration(proxyBeanMethods = false)
class EngineConfig {

    @Bean
    Content content(ShardboundProperties properties) {
        Path here = Path.of("").toAbsolutePath();
        Path dir = Optional.ofNullable(properties.contentDir())
                .or(() -> ContentLoader.find(here))
                .orElseThrow(() -> new IllegalStateException("No content/ folder found above " + here
                        + ": set shardbound.content-dir to the repository's content/ folder"));
        return ContentLoader.load(dir);
    }

    @Bean
    GameEngine gameEngine(Content content) {
        return new GameEngine(content.catalog());
    }
}
