package fr.daliush.shardbound.api.config;

import fr.daliush.shardbound.api.dao.evaluation.DatasetDao;
import fr.daliush.shardbound.api.dao.evaluation.DatasetDaoFile;
import fr.daliush.shardbound.api.dao.evaluation.EvaluationDao;
import fr.daliush.shardbound.api.dao.evaluation.EvaluationDaoFile;
import fr.daliush.shardbound.core.content.ContentLoader;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The evaluations' stores, folders read at every request: the reports, the repository's {@code evals/reports} unless
 * {@code shardbound.reports-dir} says otherwise, and the datasets, its {@code evals/datasets} unless
 * {@code shardbound.datasets-dir} does. Buckets will take their place when the server runs online.
 */
@Configuration(proxyBeanMethods = false)
class EvaluationConfig {

    private static final Path REPORTS = Path.of("evals", "reports");
    private static final Path DATASETS = Path.of("evals", "datasets");

    @Bean
    EvaluationDao evaluationDao(ShardboundProperties properties) {
        return new EvaluationDaoFile(folder(properties.reportsDir(), REPORTS, "shardbound.reports-dir"));
    }

    @Bean
    DatasetDao datasetDao(ShardboundProperties properties) {
        return new DatasetDaoFile(folder(properties.datasetsDir(), DATASETS, "shardbound.datasets-dir"));
    }

    /** The folder a property names, or else the one at {@code inRepository} in the repository found above. */
    private static Path folder(Path configured, Path inRepository, String property) {
        Path here = Path.of("").toAbsolutePath();
        return Optional.ofNullable(configured)
                .or(() -> ContentLoader.find(here).map(content -> content.getParent().resolve(inRepository)))
                .orElseThrow(() -> new IllegalStateException("No repository found above " + here
                        + ": set " + property + " to the folder of " + inRepository));
    }
}
