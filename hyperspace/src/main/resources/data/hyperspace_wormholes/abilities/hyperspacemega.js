({
  name: "Hyperspace Mega",
  onStart(pokemon) {
    if (!pokemon.m.hyperspaceBoss) {
      pokemon.m.hyperspaceBoss = true;
      pokemon.maxhp *= 2;
      pokemon.hp = Math.min(pokemon.maxhp, pokemon.hp * 2);
      this.add('-heal', pokemon, pokemon.getHealth, '[silent]');
    }
    const real = pokemon.species.abilities['0'];
    if (real && real.toLowerCase().replace(/[^a-z0-9]/g, '') !== 'hyperspacemega') pokemon.setAbility(real);
  },
  flags: { failroleplay: 1, noreceiver: 1, noentrain: 1, notrace: 1, failskillswap: 1, notransform: 1 },
  rating: 3,
  num: -9002
})
