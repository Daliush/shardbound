package fr.daliush.shardbound.api.domain.ports;

import fr.daliush.shardbound.api.domain.bo.evaluation.DatasetDocument;
import java.util.Optional;

/** What the domain needs to read the evaluation datasets, whatever stores them. */
public interface DatasetPort {

    Optional<DatasetDocument> find(String version, String split);
}
