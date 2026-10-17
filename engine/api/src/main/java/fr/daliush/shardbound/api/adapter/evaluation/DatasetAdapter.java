package fr.daliush.shardbound.api.adapter.evaluation;

import fr.daliush.shardbound.api.dao.entities.evaluation.DatasetEntity;
import fr.daliush.shardbound.api.dao.evaluation.DatasetDao;
import fr.daliush.shardbound.api.domain.bo.evaluation.DatasetDocument;
import fr.daliush.shardbound.api.domain.ports.DatasetPort;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Datasets through the dataset DAO: entities stored, business objects out. */
@Component
public class DatasetAdapter implements DatasetPort {

    private final DatasetDao dao;

    public DatasetAdapter(DatasetDao dao) {
        this.dao = dao;
    }

    @Override
    public Optional<DatasetDocument> find(String version, String split) {
        return dao.find(version, split).map(DatasetAdapter::toDocument);
    }

    private static DatasetDocument toDocument(DatasetEntity entity) {
        return new DatasetDocument(entity.version(), entity.split(), entity.jsonl());
    }
}
