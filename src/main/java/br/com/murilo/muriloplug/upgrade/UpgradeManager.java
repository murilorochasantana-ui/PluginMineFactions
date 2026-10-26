package br.com.murilo.muriloplug.upgrade;

import br.com.murilo.muriloplug.clan.Clan;
import br.com.murilo.muriloplug.clan.base.ClanBaseManager;

/** Upgrades da Base: por enquanto só a cobrança no Cofre do clan, para os upgrades que ainda vão ser feitos. */
public final class UpgradeManager {

    private final ClanBaseManager bases;

    public UpgradeManager(ClanBaseManager bases) {
        this.bases = bases;
    }

    /** Cobra o upgrade do Cofre do clan. Retorna false, sem mudar nada, se o saldo ficaria abaixo do mínimo. */
    public boolean charge(Clan clan, long cost) {
        return bases.withdrawFromVault(clan, cost);
    }
}
