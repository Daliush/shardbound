package fr.daliush.shardbound.api.dao.entities.evaluation;

/** One evaluation of a dataset split, named by the time it ran; {@code json} is the document as written. */
public record EvaluationEntity(String version, String split, String timestamp, String json) {
}
