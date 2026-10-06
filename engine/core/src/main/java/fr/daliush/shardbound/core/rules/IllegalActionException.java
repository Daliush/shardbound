package fr.daliush.shardbound.core.rules;

/** The action is not one of the pending decision's actions. */
public class IllegalActionException extends RuntimeException {

    public IllegalActionException(String message) {
        super(message);
    }
}
