package fr.daliush.shardbound.api.adapter.evaluation;

import fr.daliush.shardbound.api.dao.entities.evaluation.EvaluationEntity;
import fr.daliush.shardbound.api.dao.evaluation.EvaluationDao;
import fr.daliush.shardbound.api.domain.bo.evaluation.EvaluationDocument;
import fr.daliush.shardbound.api.domain.ports.EvaluationPort;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Evaluations through the evaluation DAO: entities stored, business objects out. */
@Component
public class EvaluationAdapter implements EvaluationPort {

    private final EvaluationDao dao;

    public EvaluationAdapter(EvaluationDao dao) {
        this.dao = dao;
    }

    @Override
    public List<String> listTimestamps(String version, String split) {
        return dao.listTimestamps(version, split);
    }

    @Override
    public Optional<EvaluationDocument> find(String version, String split, String timestamp) {
        return dao.find(version, split, timestamp).map(EvaluationAdapter::toDocument);
    }

    private static EvaluationDocument toDocument(EvaluationEntity entity) {
        return new EvaluationDocument(entity.version(), entity.split(), entity.timestamp(), entity.json());
    }
}
