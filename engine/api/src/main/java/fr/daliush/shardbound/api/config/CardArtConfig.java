package fr.daliush.shardbound.api.config;

import fr.daliush.shardbound.api.dao.art.CardArtDao;
import fr.daliush.shardbound.api.dao.art.CardArtDaoFile;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The store of the cards' art, a folder read at every request: the repository's {@code assets/art/web}, which
 * {@code scripts/publish-card-art.py} fills, unless {@code shardbound.art-dir} says otherwise. A bucket will take
 * its place when the server runs online.
 */
@Configuration(proxyBeanMethods = false)
class CardArtConfig {

    private static final Path ART = Path.of("assets", "art", "web");

    @Bean
    CardArtDao cardArtDao(ShardboundProperties properties) {
        return new CardArtDaoFile(RepositoryFolders.find(properties.artDir(), ART, "shardbound.art-dir"));
    }
}
