package fr.daliush.shardbound.api.dao.evaluation;

import fr.daliush.shardbound.api.dao.entities.evaluation.EvaluationEntity;
import java.util.List;
import java.util.Optional;

/** Where the evaluations live. Implementations: {@code EvaluationDaoFile}, later a bucket. */
public interface EvaluationDao {

    /** The timestamps of a dataset split's evaluations, oldest first; none for an unknown split. */
    List<String> listTimestamps(String version, String split);

    Optional<EvaluationEntity> find(String version, String split, String timestamp);
}
