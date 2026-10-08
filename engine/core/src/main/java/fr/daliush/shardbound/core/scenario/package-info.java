/**
 * The scenario service, a public API: a game created at any moment instead of from the start
 * ({@code ScenarioBuilder}), played with choices named by card ({@code ScenarioRunner}, {@code Choices},
 * {@code Pick}), and read with the rules applied ({@code ScenarioResult} and its trace). Rule tests and the Arbiter's
 * answer keys rely on it; {@code engine/core/README.md} shows how to use it.
 */
package fr.daliush.shardbound.core.scenario;
