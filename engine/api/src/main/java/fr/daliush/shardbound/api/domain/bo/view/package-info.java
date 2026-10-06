/**
 * What one seat may see of a game (spec §13.3), as business objects: views, decisions, actions and events,
 * built from what the engine lets that seat see. Optional values are empty, never null, except in
 * {@link ActionView}, {@link TargetView} and {@link EventTargetView}, whose fields exist only for some kinds.
 */
package fr.daliush.shardbound.api.domain.bo.view;
