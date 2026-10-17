package fr.daliush.shardbound.api.domain.bo.error;

/** A request for an evaluation or its dataset that the evaluation services refuse. */
public abstract sealed class EvaluationException extends RuntimeException {

    private EvaluationException(String message) {
        super(message);
    }

    /** No evaluation of this dataset split ran at this time. */
    public static final class UnknownEvaluation extends EvaluationException {
        public UnknownEvaluation(String version, String split, String timestamp) {
            super("Unknown evaluation: " + version + "/" + split + "/" + timestamp);
        }
    }

    /** No dataset of this version has this split. */
    public static final class UnknownDataset extends EvaluationException {
        public UnknownDataset(String version, String split) {
            super("Unknown dataset: " + version + "/" + split);
        }
    }
}
