package br.com.murilo.muriloplug.clan.base;

/** Dados da base de um clan (Núcleo, Cofre, Poder, dívida). Só o {@link ClanBaseManager} altera. */
public final class ClanBase {

    final String tag;
    BaseLoc core;
    int coreHp;
    /** Último dano no Núcleo (para a regeneração e para o /clan nucleo recolher). */
    long lastCoreAttack;
    long lastCoreRegen;
    BaseLoc vault;
    int vaultHp;
    /** Dinheiro guardado no Cofre (continua guardado mesmo sem o bloco do Cofre). */
    long vaultBalance;
    /** Parte decimal dos juros que ainda não virou dinheiro. */
    double interestFraction;
    int power;
    long vulnerableUntil;
    /** Antes disso não pode colocar outro Núcleo (depois de um Núcleo destruído). */
    long replaceAllowedAt;
    boolean freeCoreTaken;
    /** Dívida da manutenção (0 = em dia). */
    long debt;

    ClanBase(String tag) {
        this.tag = tag;
    }

    public String tag() {
        return tag;
    }

    public BaseLoc core() {
        return core;
    }

    public int coreHp() {
        return coreHp;
    }

    public BaseLoc vault() {
        return vault;
    }

    public int vaultHp() {
        return vaultHp;
    }

    public long vaultBalance() {
        return vaultBalance;
    }

    public int power() {
        return power;
    }

    public long debt() {
        return debt;
    }

    public boolean isVulnerable() {
        return vulnerableUntil > System.currentTimeMillis();
    }

    public long vulnerableUntil() {
        return vulnerableUntil;
    }
}
