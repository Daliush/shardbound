package fr.daliush.shardbound.api.config;

import fr.daliush.shardbound.api.dao.evaluation.EvaluationDao;
import fr.daliush.shardbound.api.dao.evaluation.EvaluationDaoInMemory;
import fr.daliush.shardbound.core.content.ContentLoader;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The evaluations' store: the reports folder loaded in memory, the repository's {@code evals/reports} unless
 * {@code shardbound.reports-dir} says otherwise. A bucket will take its place when the server runs online.
 */
@Configuration(proxyBeanMethods = false)
class EvaluationConfig {

    private static final Path REPORTS = Path.of("evals", "reports");

    @Bean
    EvaluationDao evaluationDao(ShardboundProperties properties) {
        Path here = Path.of("").toAbsolutePath();
        Path reportsDir = Optional.ofNullable(properties.reportsDir())
                .or(() -> ContentLoader.find(here).map(content -> content.getParent().resolve(REPORTS)))
                .orElseThrow(() -> new IllegalStateException("No repository found above " + here
                        + ": set shardbound.reports-dir to the folder of the evaluations"));
        return new EvaluationDaoInMemory(reportsDir);
    }
}
