package fr.daliush.shardbound.core.bot;

/**
 * A program that plays. Its only memory is its random generator: a game server stores {@link #rngState()}
 * after each move and rebuilds the bot from it for the next one (spec §13.4).
 */
public interface Bot extends Player {

    long rngState();
}
