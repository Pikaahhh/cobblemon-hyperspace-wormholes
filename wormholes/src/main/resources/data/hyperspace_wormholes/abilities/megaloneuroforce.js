{
    name: "Megalo Neuroforce",
    onModifyAtkPriority: 5,
    onModifyAtk(atk) {
        return this.chainModify(1.5);
    },
    onModifyDefPriority: 6,
    onModifyDef(def) {
        return this.chainModify(1.5);
    },
    onModifySpAPriority: 5,
    onModifySpA(spa) {
        return this.chainModify(1.5);
    },
    onModifySpDPriority: 6,
    onModifySpD(spd) {
        return this.chainModify(1.5);
    },
    onModifySpe(spe) {
        return this.chainModify(1.5);
    },
    onModifyDamage(damage, source, target, move) {
        if (move && target.getMoveHitData(move).typeMod > 0) {
            return this.chainModify([5120, 4096]);
        }
    },
    flags: { failroleplay: 1, noreceiver: 1, noentrain: 1, notrace: 1, failskillswap: 1, cantsuppress: 1 },
    rating: 3.5
}
