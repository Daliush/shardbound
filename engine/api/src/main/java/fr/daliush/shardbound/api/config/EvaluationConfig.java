package fr.daliush.shardbound.api.config;

import fr.daliush.shardbound.api.dao.evaluation.DatasetDao;
import fr.daliush.shardbound.api.dao.evaluation.DatasetDaoFile;
import fr.daliush.shardbound.api.dao.evaluation.EvaluationDao;
import fr.daliush.shardbound.api.dao.evaluation.EvaluationDaoFile;
import java.nio.file.Path;
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
        return new EvaluationDaoFile(RepositoryFolders.find(properties.reportsDir(), REPORTS,
                "shardbound.reports-dir"));
    }

    @Bean
    DatasetDao datasetDao(ShardboundProperties properties) {
        return new DatasetDaoFile(RepositoryFolders.find(properties.datasetsDir(), DATASETS,
                "shardbound.datasets-dir"));
    }
}
