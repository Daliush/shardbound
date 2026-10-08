/**
 * Determinization: a complete, plausible game built from one player's view alone, for the bots that simulate (spec
 * §10, design doc §6.5). The entry point is {@code Determinizer}; each other class applies one rule of it. It never
 * reads the real game, so what a bot simulates is what it could know.
 */
package fr.daliush.shardbound.core.determinization;
