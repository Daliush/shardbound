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
- **8.10** **Freeze**: the unit can neither attack nor intercept for the rest of the current turn and during the next turn. It thaws at the start of the turn after that.

  > *Example*: during my turn, I freeze an enemy unit. It cannot intercept for the rest of my turn, nor attack during my opponent's next turn. It thaws at the start of my following turn.
- **8.11** **Link**: links any two units, allied or enemy, including one from each side (11.5). This effect only exists on neutral spells.
- **8.12** **Gain Shards**: +X Shards usable this turn only, or +1 max Shard (cap 10).
- **8.13** **Recall**: a unit from the graveyard returns to its owner's hand, without its modifications (6.7). A token cannot be recalled (3.6). This effect is reserved for Ember, on a few expensive cards. The unit is chosen by the Recall's controller, from their own graveyard.
- **8.14** **Aura**: a continuous effect while the card is on the board (e.g. "your spells cost 1 less"). An aura is one of two kinds:
  - a stat bonus or malus (+X/+Y, as in 8.5) for a group: all your units, all enemy units or all units;
  - a cost change for one card type (units, spells, relics or all cards), for you or for your opponent. A cost never drops below 0.

  When a stat aura stops applying, its defense bonus ends like an expiring bonus (8.5).
- **8.15** Damage never goes below 0: a malus can bring an attack's damage down to 0, not lower. An effect that deals 0 damage still hits its target; it simply removes nothing.
- **8.16** A sacrifice is never partial. A card can only be played, and an attack ability only used, if its controller can make every sacrifice it asks for: the card's sacrifice cost (6.3), the Sacrifice effects of a spell, and those of the attack ability and of the unit's "Attack" abilities (9.9). When a Sacrifice effect resolves and its controller has more units than it asks for, they choose which. An anchored unit can be sacrificed: it counts but stays (11.3.3).
- **8.17** When a temporary defense malus (−Y until end of turn) expires, the unit gets back what it lost: its max defense and its current defense both go up by Y. Current defense never ends above max defense.

  > *Example*: a 5/5 unit gets −0/−2 until end of turn (3/3), then takes 2 damage (1/3). When the malus expires, it has 3/5.
- **8.18** An attack damage modification (+X or −X, from Modify or from an aura) applies to every damage effect of each of the unit's attack abilities. It does not apply to its other abilities (for example "Attack" abilities, 9.9), nor to its echoes (11.1.9).
- **8.19** "Until end of turn" means until the end of the current turn, whoever's turn it is (5.4.2).
- **8.20** If Recall would put a card into a full hand (3.3), it does nothing: the card stays in the graveyard.
- **8.21** When a stat aura that gives a defense malus stops applying, the unit gets back what it lost, as when a temporary malus expires (8.17).
- **8.22** A Sacrifice effect that resolves while its controller does not have enough units does nothing: no unit is sacrificed (8.16). This happens in abilities triggered by something else than playing a card or attacking (Death, Turn start, an echo…), or when units died earlier in the same resolution. The other effects of the ability apply normally (10.3).
