({
  name: "Hyperspace Guard",
  onStart(pokemon) {
    if (pokemon.m.hyperspaceBoss) return;
    pokemon.m.hyperspaceBoss = true;
    pokemon.maxhp *= 2;
    pokemon.hp = Math.min(pokemon.maxhp, pokemon.hp * 2);
    this.add('-heal', pokemon, pokemon.getHealth, '[silent]');
  },
  onDamagePriority: -100,
  onDamage(damage, target, source, effect) {
    if (damage >= target.hp && !(target.hp === 1 && effect && effect.effectType === 'Move' && source && source !== target)) {
      this.add('-activate', target, 'move: Endure', '[hyperspace_wormholes]');
      return target.hp - 1;
    }
  },
  flags: { failroleplay: 1, noreceiver: 1, noentrain: 1, notrace: 1, failskillswap: 1, cantsuppress: 1, notransform: 1 },
  rating: 5,
  num: -9001
})
