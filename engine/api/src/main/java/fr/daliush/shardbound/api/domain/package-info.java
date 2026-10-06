/**
 * The domain layer: business objects ({@code bo}), the services that create, join and play games
 * ({@code services}), and the mappers between engine data, business objects and stored entities
 * ({@code mappers}). It knows no transport and no JSON; it calls the repository layer, never the controllers.
 */
package fr.daliush.shardbound.api.domain;
