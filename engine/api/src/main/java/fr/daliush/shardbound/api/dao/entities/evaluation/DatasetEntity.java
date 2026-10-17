package fr.daliush.shardbound.api.dao.entities.evaluation;

/** One split of an evaluation dataset; {@code jsonl} is the file as written, one question per line. */
public record DatasetEntity(String version, String split, String jsonl) {
}
