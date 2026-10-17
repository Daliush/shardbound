package fr.daliush.shardbound.api.dao.evaluation;

import fr.daliush.shardbound.api.dao.entities.evaluation.DatasetEntity;
import java.util.Optional;

/** Where the evaluation datasets live. Implementations: {@code DatasetDaoFile}, later a bucket. */
public interface DatasetDao {

    /** A dataset version's split; none for a version or a split that was never built. */
    Optional<DatasetEntity> find(String version, String split);
}
