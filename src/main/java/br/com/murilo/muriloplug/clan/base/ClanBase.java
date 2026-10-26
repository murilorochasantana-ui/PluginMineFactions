package br.com.murilo.muriloplug.clan.base;

/** Dados da base de um clan (Núcleo, Cofre, Poder, dívida). Só o {@link ClanBaseManager} altera. */
public final class ClanBase {

    final String tag;
    BaseLoc core;
    int coreHp;
    /** Último dano no Núcleo (para a regeneração e para o /clan nucleo recolher). */
    long lastCoreAttack;
    long lastCoreRegen;
    /** Menor vida que o Núcleo atual já teve: só pontos abaixo dela pagam recompensa (anti-farm). */
    int lowestHp;
    /** Até quando o Núcleo está imune a explosões (escudo depois de cada ponto tirado). */
    long shieldUntil;
    BaseLoc vault;
    int vaultHp;
    long vaultShieldUntil;
    /** Baús do Clan colocados (id -> baú). */
    final java.util.Map<String, ClanChest> chests = new java.util.LinkedHashMap<>();
    /** Dinheiro guardado no Cofre (continua guardado mesmo sem o bloco do Cofre). */
    long vaultBalance;
    /** Parte decimal dos juros que ainda não virou dinheiro. */
    double interestFraction;
    int power;
    long vulnerableUntil;
    /** Núcleo em 0 de vida: continua no lugar, DESATIVADO, até este momento (0 = ativo). */
    long coreDisabledUntil;
    /** Núcleo recolhido: precisa ser colocado de novo até este momento (0 = sem prazo correndo). */
    long coreReplaceDeadline;
    /** Quem recolheu o Núcleo (vê a contagem do prazo na action bar). */
    java.util.UUID corePickedBy;
    /** O prazo acabou sem o Núcleo colocado: vulnerável até ele voltar (e pelo menos até {@link #vulnerableUntil}). */
    boolean coreMissingPenalty;
    boolean freeCoreTaken;
    /** Id do Núcleo/Cofre que vale agora (só o item com este id pode ser colocado; null = nenhum entregue). */
    String coreItemId;
    String vaultItemId;
    /** Último dano no Cofre (para o /clan cofre recolher). Só em memória. */
    long lastVaultAttack;
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

    public int lowestHp() {
        return lowestHp;
    }

    public long shieldUntil() {
        return shieldUntil;
    }

    public BaseLoc vault() {
        return vault;
    }

    public int vaultHp() {
        return vaultHp;
    }

    public java.util.Collection<ClanChest> chests() {
        return java.util.List.copyOf(chests.values());
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

    /** Vulnerável: Núcleo desativado (até reativar) ou punição por não recolocar o Núcleo a tempo. */
    public boolean isVulnerable() {
        return coreMissingPenalty || vulnerableUntil > System.currentTimeMillis();
    }

    public boolean isCoreDisabled() {
        return coreDisabledUntil > 0;
    }

    public long coreDisabledUntil() {
        return coreDisabledUntil;
    }

    public long coreReplaceDeadline() {
        return coreReplaceDeadline;
    }

    public boolean coreMissingPenalty() {
        return coreMissingPenalty;
    }

    public long vulnerableUntil() {
        return vulnerableUntil;
    }
}
