package fr.daliush.shardbound.api.domain.ports;

import fr.daliush.shardbound.api.domain.bo.evaluation.EvaluationDocument;
import java.util.List;
import java.util.Optional;

/** What the domain needs to read the Arbiter's evaluations, whatever stores them. */
public interface EvaluationPort {

    /** The timestamps of a dataset split's evaluations, oldest first. */
    List<String> listTimestamps(String version, String split);

    Optional<EvaluationDocument> find(String version, String split, String timestamp);
}
