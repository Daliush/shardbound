# 8. Effects (closed list)

*Part of the [Shardbound rulebook](README.md).*

- **8.1** **Deal X damage**: removes X defense from a unit, or X HP from a player.
- **8.2** **Destroy**: sends a unit or relic to the graveyard, whatever its defense.
- **8.3** **Sacrifice**: the controller destroys one of their own units, as the cost of a card or as an effect. The sacrificed unit dies.
- **8.4** **Heal X**: restores X defense to a unit (up to its max defense), or X HP to a player (up to their maximum, 1.1).
- **8.5** **Modify +X/+Y** (or −X/−Y):
  - +X to the damage of each of the unit's attack abilities that deals damage (damage never goes below 0);
  - +Y to its max defense and its current defense. A debuff that brings current defense to 0 destroys it (6.6);
  - the modification is permanent, or lasts until end of turn;
  - when a temporary defense bonus expires, max defense drops by Y. Current defense stays the same, unless it is now above the new max, in which case it is lowered to the new max. Expiration alone therefore never kills the unit.

  > *Example*: a unit with 3 max defense gets +2 until end of turn (current 5, max 5), then takes 1 damage (current 4, max 5). When the bonus expires, it has current 3, max 3. Had it taken 4 damage instead (current 1, max 5), it would end at current 1, max 3.
- **8.6** **Draw X**: the player draws X cards.
- **8.7** **Discard X**: a player discards X cards from their hand. Depending on the card, that player chooses them, or they are picked at random.
- **8.8** **Return to hand**: a unit or relic returns to its owner's hand and loses its modifications and damage (6.7). It does not die. A token vanishes (3.6). If the owner's hand is full, the card goes to the graveyard instead; it still does not die, so Echo does not trigger (11.1.5).
- **8.9** **Summon**: creates a token, defined by the card, on the controller's board. If the board is full, nothing happens.
- **8.10** **Freeze**: until the end of its controller's next turn, the unit can neither attack nor intercept.
- **8.11** **Link**: links any two units, allied or enemy, including one from each side (11.5). This effect only exists on neutral spells.
- **8.12** **Gain Shards**: +X Shards usable this turn only, or +1 max Shard (cap 10).
- **8.13** **Recall**: a unit from the graveyard returns to its owner's hand, without its modifications (6.7). A token cannot be recalled (3.6). This effect is reserved for Ember, on a few expensive cards. The unit is chosen by the Recall's controller, from their own graveyard.
- **8.14** **Aura**: a continuous effect while the card is on the board (e.g. "your spells cost 1 less"). An aura is one of two kinds:
  - a stat bonus or malus (+X/+Y, as in 8.5) for a group: all your units, all enemy units or all units;
  - a cost change for one card type (units, spells, relics or all cards), for you or for your opponent. A cost never drops below 0.

  When a stat aura stops applying, its defense bonus ends like an expiring bonus (8.5).
