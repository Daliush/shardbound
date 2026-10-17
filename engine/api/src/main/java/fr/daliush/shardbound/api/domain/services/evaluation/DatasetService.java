package fr.daliush.shardbound.api.domain.services.evaluation;

import fr.daliush.shardbound.api.domain.bo.error.EvaluationException;
import fr.daliush.shardbound.api.domain.bo.evaluation.DatasetDocument;
import fr.daliush.shardbound.api.domain.ports.DatasetPort;
import org.springframework.stereotype.Service;

/** The questions an evaluation scored, with their wording and answer key. */
@Service
public class DatasetService {

    private final DatasetPort datasets;

    public DatasetService(DatasetPort datasets) {
        this.datasets = datasets;
    }

    public DatasetDocument load(String version, String split) {
        return datasets.find(version, split)
                .orElseThrow(() -> new EvaluationException.UnknownDataset(version, split));
    }
}
