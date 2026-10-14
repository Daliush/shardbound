package fr.daliush.shardbound.api.domain.services.evaluation;

import fr.daliush.shardbound.api.domain.bo.error.EvaluationException;
import fr.daliush.shardbound.api.domain.bo.evaluation.EvaluationDocument;
import fr.daliush.shardbound.api.domain.ports.EvaluationPort;
import java.util.List;
import org.springframework.stereotype.Service;

/** The evaluations of a dataset split: their timestamps to choose from, then the one chosen. */
@Service
public class EvaluationService {

    private final EvaluationPort evaluations;

    public EvaluationService(EvaluationPort evaluations) {
        this.evaluations = evaluations;
    }

    /** Oldest first; none for a split never evaluated. */
    public List<String> listTimestamps(String version, String split) {
        return evaluations.listTimestamps(version, split);
    }

    public EvaluationDocument load(String version, String split, String timestamp) {
        return evaluations.find(version, split, timestamp)
                .orElseThrow(() -> new EvaluationException.UnknownEvaluation(version, split, timestamp));
    }
}
