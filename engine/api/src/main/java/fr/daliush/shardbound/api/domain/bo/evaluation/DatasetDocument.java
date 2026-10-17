package fr.daliush.shardbound.api.domain.bo.evaluation;

/**
 * One split of an evaluation dataset. {@code jsonl} is the file the Python split wrote (ai/dataset), one question
 * per line, served as it is: its format belongs to the dataset.
 */
public record DatasetDocument(String version, String split, String jsonl) {
}
