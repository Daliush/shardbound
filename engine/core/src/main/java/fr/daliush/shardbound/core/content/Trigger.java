package fr.daliush.shardbound.core.content;

/** When an ability fires (rulebook section 9). Cast (9.1) is implicit: it is a spell's own effects. */
public enum Trigger {
    ARRIVAL, DEATH, DEPARTURE, TURN_START, TURN_END, CONTINUOUS, ATTACK
}
