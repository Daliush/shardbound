package fr.daliush.shardbound.api.domain.ports;

/** A watch on one game, kept until it is stopped. */
public interface Watch {

    void stop();
}
