package fr.daliush.shardbound.api.domain.bo.evaluation;

/**
 * One evaluation of a dataset split, named by the time it ran. {@code json} is the document the Python evaluation
 * wrote (ai/rag/evaluation/evaluation_json.py), served as it is: its format belongs to the evaluation.
 */
public record EvaluationDocument(String version, String split, String timestamp, String json) {
}
