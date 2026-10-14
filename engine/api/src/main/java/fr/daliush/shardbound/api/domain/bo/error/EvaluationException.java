package fr.daliush.shardbound.api.domain.bo.error;

/** A request for an evaluation that the evaluation service refuses. */
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
}
