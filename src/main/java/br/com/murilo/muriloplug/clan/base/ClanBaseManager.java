package br.com.murilo.muriloplug.clan.base;

import br.com.murilo.muriloplug.claim.ClaimManager;
import br.com.murilo.muriloplug.clan.Clan;
import br.com.murilo.muriloplug.clan.ClanManager;
import br.com.murilo.muriloplug.economy.EconomyManager;
import br.com.murilo.muriloplug.stats.PlayerKillEvent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Dados das bases dos clans (Núcleo, Cofre, Poder, vulnerabilidade, dívida) e as regras que não dependem dos blocos:
 * Poder, juros do Cofre e manutenção diária dos claims (só de clans: quem não tem clan não paga nada). Tudo é gravado no clans.yml (seção "bases"), junto com os
 * clans, pelo {@link ClanManager#save()}. Os blocos (colocar, explosões, menus) ficam em {@link BaseBlocks}.
 */
public final class ClanBaseManager implements Listener {

    private static final long HOUR_MILLIS = 3_600_000L;

    private final JavaPlugin plugin;
    private final ClanManager clans;
    private final ClaimManager claims;
    private final EconomyManager economy;
    private final BaseSettings settings;
    private final Map<String, ClanBase> bases = new LinkedHashMap<>();
    private String lastUpkeepDate;
    private long lastInterest;
    private Consumer<UUID> changeListener = id -> { };

    public ClanBaseManager(JavaPlugin plugin, ClanManager clans, ClaimManager claims, EconomyManager economy,
                           BaseSettings settings) {
        this.plugin = plugin;
        this.clans = clans;
        this.claims = claims;
        this.economy = economy;
        this.settings = settings;
    }

    /** Chamado quando algo que aparece no sidebar (manutenção, dívida) muda para o jogador. */
    public void setChangeListener(Consumer<UUID> changeListener) {
        this.changeListener = changeListener;
    }

    public BaseSettings settings() {
        return settings;
    }

    public ClanManager clans() {
        return clans;
    }

    public EconomyManager economy() {
        return economy;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::minuteTick, 20L * 60, 20L * 60);
    }

    // ------------------------------------------------------------ consulta

    /** Dados da base do clan (criados vazios na primeira vez). */
    public ClanBase of(Clan clan) {
        return bases.computeIfAbsent(clan.tag(), tag -> {
            ClanBase base = new ClanBase(tag);
            base.coreHp = settings.coreHp();
            base.lowestHp = settings.coreHp();
            base.vaultHp = settings.vaultHp();
            return base;
        });
    }

    /** Dados da base pela TAG, ou null se o clan não existir. */
    public ClanBase byTag(String tag) {
        Clan clan = tag == null ? null : clans.getByTag(tag);
        return clan == null ? null : of(clan);
    }

    public Collection<ClanBase> all() {
        return List.copyOf(bases.values());
    }

    /** Situação dos claims de um dono (para a proteção do claim). */
    public ClaimManager.Weakness weakness(UUID owner) {
        Clan clan = clans.getClan(owner);
        if (clan != null) {
            ClanBase base = bases.get(clan.tag());
            if (base == null) {
                return ClaimManager.Weakness.NONE;
            }
            if (base.isVulnerable()) {
                return ClaimManager.Weakness.VULNERABLE;
            }
            return weakened(clan) ? ClaimManager.Weakness.IN_DEBT : ClaimManager.Weakness.NONE;
        }
        return ClaimManager.Weakness.NONE; // sem clan não há manutenção, nem dívida
    }

    /** Saldo mínimo do Cofre do clan (cresce com o Poder). */
    public long minimum(Clan clan) {
        return settings.vaultMinimum(of(clan).power);
    }

    /**
     * Enfraquecido: em dívida OU com o Cofre abaixo do mínimo. Inimigos abrem baús nos claims e o Núcleo leva o dobro
     * de dano por explosão.
     */
    public boolean weakened(Clan clan) {
        ClanBase base = of(clan);
        return base.debt > 0 || base.vaultBalance < minimum(clan);
    }

    /** Vulnerável (Núcleo desativado ou não recolocado) ou enfraquecido: inimigos podem abrir os Baús do Clan. */
    public boolean openToEnemies(Clan clan) {
        return of(clan).isVulnerable() || weakened(clan);
    }

    // ------------------------------------------------------------ escudo e recompensa do Núcleo

    /** Segundos de escudo que faltam no Núcleo do clan (0 = sem escudo). */
    public int getEscudoRestante(Clan clan) {
        long left = of(clan).shieldUntil - System.currentTimeMillis();
        return left <= 0 ? 0 : (int) Math.ceil(left / 1000.0);
    }

    /** Tira {@code fraction} do saldo ATUAL do Cofre (roubo do raid) e devolve quanto saiu. */
    public long steal(Clan clan, double fraction) {
        ClanBase base = of(clan);
        long amount = (long) Math.floor(base.vaultBalance * Math.max(0, Math.min(1, fraction)));
        if (amount <= 0) {
            return 0;
        }
        base.vaultBalance -= amount;
        save();
        return amount;
    }

    /** O que sobraria no Cofre depois de roubar {@code points} pontos e a destruição. */
    private double keepFraction(int points) {
        return Math.pow(1 - settings.stealPerPoint(), Math.max(0, points)) * (1 - settings.stealOnDestroy());
    }

    /** Estimativa (com o saldo de agora) de quanto ainda dá para roubar pelo Núcleo atual do clan. */
    public long stealRemaining(Clan clan) {
        ClanBase base = of(clan);
        if (base.isCoreDisabled()) {
            return 0; // desativado não rouba mais nada
        }
        return (long) Math.floor(base.vaultBalance * (1 - keepFraction(base.lowestHp)));
    }

    /** Estimativa do roubo de um Núcleo inteiro (com o saldo de agora), para o progresso da barra do raid. */
    public long stealTotal(Clan clan) {
        return (long) Math.floor(of(clan).vaultBalance * (1 - keepFraction(settings.coreHp())));
    }

    /** Clans por Poder (maior primeiro). */
    public List<Clan> ranking() {
        List<Clan> list = new ArrayList<>(clans.all());
        list.sort(Comparator.comparingInt((Clan clan) -> of(clan).power).reversed().thenComparing(Clan::tag));
        return list;
    }

    // ------------------------------------------------------------ Poder

    /** Soma (ou tira) Poder, sem passar de 0 para baixo. */
    public void addPower(Clan clan, int delta) {
        ClanBase base = of(clan);
        base.power = Math.max(0, base.power + delta);
        save();
        clan.members().forEach(changeListener);
    }

    /** +Poder por kill de inimigo (quem não é do clan do killer). */
    @EventHandler
    public void onKill(PlayerKillEvent event) {
        Clan clan = clans.getClan(event.getKiller());
        if (clan != null && !clans.isSameClan(event.getKiller(), event.getVictim()) && settings.powerKill() != 0) {
            addPower(clan, settings.powerKill());
        }
    }

    // ------------------------------------------------------------ manutenção

    /** Área total (blocos²) dos claims dos membros do clan. */
    public long totalArea(Clan clan) {
        long area = 0;
        for (UUID member : clan.members()) {
            area += claims.totalArea(member);
        }
        return area;
    }

    /** Custo de uma área com o Poder atual do clan: área × por-bloco × (1 + Poder × peso). */
    public long upkeepForArea(Clan clan, long area) {
        double cost = area * settings.upkeepPerBlock() * (1 + of(clan).power * settings.upkeepPowerWeight());
        return (long) Math.ceil(cost - 1.0E-9);
    }

    /** Custo da manutenção diária do clan (todos os claims dos membros). */
    public long upkeepCost(Clan clan) {
        return upkeepForArea(clan, totalArea(clan));
    }

    /**
     * Para o menu do /claim: {custo de hoje, custo com mais {@code extraArea}}, ou null se o jogador não tiver clan
     * (sem clan não há manutenção).
     */
    public long[] upkeepPreview(Player player, long extraArea) {
        Clan clan = clans.getClan(player.getUniqueId());
        if (clan == null) {
            return null;
        }
        long area = totalArea(clan);
        return new long[]{upkeepForArea(clan, area), upkeepForArea(clan, area + extraArea)};
    }

    /** Linhas "claim (dono) LxP = área → $custo" de cada claim que entra na manutenção do clan. */
    public List<String> upkeepBreakdown(Clan clan) {
        List<String> lines = new ArrayList<>();
        for (UUID member : clan.members()) {
            for (br.com.murilo.muriloplug.claim.Claim claim : claims.getByOwner(member)) {
                long area = (long) claim.width() * claim.depth();
                lines.add("§f" + claim.name() + " §7(" + ClanManager.nameOf(member) + ") " + claim.width() + "x"
                        + claim.depth() + " = " + area + " blocos §8→ §e" + EconomyManager.format(upkeepForArea(clan, area)));
            }
        }
        return lines;
    }

    /** Cobra a manutenção de todos agora (às 20h, ou pelo /clan admin manutencao). */
    public void runUpkeep() {
        for (Clan clan : clans.all()) {
            ClanBase base = of(clan);
            long cost = upkeepCost(clan);
            if (cost > 0) {
                if (base.vault != null && base.vaultBalance >= cost) {
                    base.vaultBalance -= cost;
                    clans.broadcast(clan, "§7Manutenção dos claims paga pelo Cofre: §a" + EconomyManager.format(cost)
                            + " §7(sobrou " + EconomyManager.format(base.vaultBalance) + ")");
                } else {
                    base.debt += cost;
                    clans.broadcast(clan, "§cO clan não pôde pagar a manutenção dos claims ("
                            + EconomyManager.format(cost) + (base.vault == null ? ", sem Cofre" : ", Cofre sem saldo")
                            + ").");
                }
            }
            if (base.debt > 0) {
                clans.broadcast(clan, "§c§lEM DÍVIDA: §c" + EconomyManager.format(base.debt)
                        + ". Os claims estão enfraquecidos (inimigos abrem baús e o Núcleo leva o dobro de dano). "
                        + "Use /clan pagar.");
            }
            clan.members().forEach(changeListener);
        }
        save();
    }

    /**
     * Paga a dívida do clan de quem chamou (com o saldo do Cofre, se bastar, senão com o dinheiro do jogador).
     * Retorna a mensagem do resultado.
     */
    public String payDebt(Player player) {
        Clan clan = clans.getClan(player.getUniqueId());
        if (clan == null) {
            return "§eSó clans pagam manutenção: você não tem clan, então não deve nada.";
        }
        ClanBase base = of(clan);
        long debt = base.debt;
        if (debt <= 0) {
            return "§eO clan não está em dívida.";
        }
        String source;
        if (base.vaultBalance >= debt) {
            base.vaultBalance -= debt;
            source = "com o Cofre";
        } else if (economy.cobrar(player, debt)) {
            source = "por " + player.getName();
        } else {
            return "§cNem o Cofre (" + EconomyManager.format(base.vaultBalance) + ") nem você ("
                    + EconomyManager.format(economy.available(player)) + ") têm os " + EconomyManager.format(debt)
                    + " da dívida.";
        }
        base.debt = 0;
        save();
        clans.broadcast(clan, "§aA dívida do clan (" + EconomyManager.format(debt) + ") foi paga " + source
                + ". Os claims voltaram ao normal.");
        clan.members().forEach(changeListener);
        return "§aDívida paga.";
    }

    // ------------------------------------------------------------ Cofre

    /** Coloca dinheiro no Cofre (já tirado do jogador antes). */
    public void depositToVault(Clan clan, long amount) {
        ClanBase base = of(clan);
        base.vaultBalance = saturatedAdd(base.vaultBalance, amount);
        save();
    }

    /** Tira do Cofre. Retorna false, sem mudar nada, se o saldo ficaria abaixo do mínimo. */
    public boolean withdrawFromVault(Clan clan, long amount) {
        ClanBase base = of(clan);
        if (amount <= 0 || base.vaultBalance - amount < minimum(clan)) {
            return false;
        }
        base.vaultBalance -= amount;
        save();
        return true;
    }

    /** Uma hora de juros para todos os clans com Cofre colocado (juros do dia / 24, até o teto). */
    public void applyInterestHour() {
        double rate = settings.interestPerDay() / 24.0;
        for (ClanBase base : bases.values()) {
            if (base.vault == null || base.vaultBalance <= 0 || rate <= 0) {
                continue;
            }
            base.interestFraction += Math.min(base.vaultBalance, settings.interestCap()) * rate;
            long whole = (long) Math.floor(base.interestFraction);
            if (whole > 0) {
                base.interestFraction -= whole;
                base.vaultBalance = saturatedAdd(base.vaultBalance, whole);
            }
        }
        save();
    }

    // ------------------------------------------------------------ tempo

    private void minuteTick() {
        long now = System.currentTimeMillis();
        int hours = 0;
        while (now - lastInterest >= HOUR_MILLIS && hours < 24) {
            lastInterest += HOUR_MILLIS;
            hours++;
        }
        if (now - lastInterest >= HOUR_MILLIS) {
            lastInterest = now;
        }
        for (int i = 0; i < hours; i++) {
            applyInterestHour();
        }
        LocalDate today = LocalDate.now();
        if (!today.toString().equals(lastUpkeepDate) && !LocalTime.now().isBefore(settings.upkeepTime())) {
            lastUpkeepDate = today.toString();
            runUpkeep();
        }
    }

    // ------------------------------------------------------------ clan desfeito / entrar

    /** Clan desfeito: os blocos são tirados por {@link BaseBlocks}; o saldo do Cofre vai para o saldo do líder. */
    public ClanBase forget(Clan clan) {
        ClanBase base = bases.remove(clan.tag());
        if (base != null && base.vaultBalance > 0) {
            economy.deposit(clan.leader(), base.vaultBalance);
            Player leader = Bukkit.getPlayer(clan.leader());
            if (leader != null) {
                leader.sendMessage("§eO saldo do Cofre (" + EconomyManager.format(base.vaultBalance)
                        + ") foi para o seu saldo.");
            }
        }
        return base;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Clan clan = clans.getClan(event.getPlayer().getUniqueId());
        long debt = clan == null ? 0 : of(clan).debt;
        if (debt > 0) {
            event.getPlayer().sendMessage("§c§lEM DÍVIDA: §c" + EconomyManager.format(debt)
                    + " de manutenção dos claims. Use /clan pagar.");
        }
    }

    // ------------------------------------------------------------ arquivo

    public void load() {
        bases.clear();
        YamlConfiguration yaml = clans.file().exists() ? YamlConfiguration.loadConfiguration(clans.file())
                : new YamlConfiguration();
        ConfigurationSection section = yaml.getConfigurationSection("bases");
        if (section != null) {
            for (String tag : section.getKeys(false)) {
                Clan clan = clans.getByTag(tag);
                ConfigurationSection data = section.getConfigurationSection(tag);
                if (clan == null || data == null) {
                    continue;
                }
                ClanBase base = of(clan);
                base.core = BaseLoc.parse(data.getString("nucleo"));
                base.coreHp = data.getInt("nucleo-vida", settings.coreHp());
                base.lowestHp = data.getInt("nucleo-menor-vida", base.coreHp);
                base.lastCoreAttack = data.getLong("nucleo-ultimo-ataque");
                base.lastCoreRegen = data.getLong("nucleo-ultima-regeneracao");
                base.vault = BaseLoc.parse(data.getString("cofre"));
                base.vaultHp = data.getInt("cofre-vida", settings.vaultHp());
                base.vaultBalance = Math.max(0, data.getLong("cofre-saldo"));
                base.interestFraction = data.getDouble("juros-fracao");
                base.power = Math.max(0, data.getInt("poder"));
                base.vulnerableUntil = data.getLong("vulneravel-ate");
                base.coreDisabledUntil = data.getLong("nucleo-desativado-ate");
                base.coreReplaceDeadline = data.getLong("nucleo-prazo-recolocar");
                String pickedBy = data.getString("nucleo-recolhido-por");
                try {
                    base.corePickedBy = pickedBy == null ? null : UUID.fromString(pickedBy);
                } catch (IllegalArgumentException e) {
                    base.corePickedBy = null;
                }
                base.coreMissingPenalty = data.getBoolean("nucleo-punicao");
                base.freeCoreTaken = data.getBoolean("nucleo-gratis-usado");
                base.coreItemId = data.getString("nucleo-item-id");
                base.vaultItemId = data.getString("cofre-item-id");
                base.debt = Math.max(0, data.getLong("divida"));
                for (Map<?, ?> map : data.getMapList("baus")) {
                    BaseLoc loc = BaseLoc.parse(String.valueOf(map.get("local")));
                    String id = String.valueOf(map.get("id"));
                    if (loc != null && map.get("id") != null) {
                        int hp = map.get("vida") instanceof Number n ? n.intValue() : settings.chestHp();
                        base.chests.put(id, new ClanChest(id, loc, hp));
                    }
                }
            }
        }
        lastInterest = yaml.getLong("juros-ultimo", System.currentTimeMillis());
        lastUpkeepDate = yaml.getString("manutencao-ultima-data");
        if (lastUpkeepDate == null) {
            // Primeira vez: a primeira cobrança é no próximo horário (hoje se ainda não passou, senão amanhã).
            boolean passed = !LocalTime.now().isBefore(settings.upkeepTime());
            lastUpkeepDate = (passed ? LocalDate.now() : LocalDate.now().minusDays(1)).toString();
        }
    }

    /** Escreve as bases no clans.yml (chamado pelo ClanManager a cada save). */
    public void write(FileConfiguration yaml) {
        for (ClanBase base : bases.values()) {
            String path = "bases." + base.tag + ".";
            yaml.set(path + "nucleo", base.core == null ? null : base.core.serialize());
            yaml.set(path + "nucleo-vida", base.coreHp);
            yaml.set(path + "nucleo-menor-vida", base.lowestHp);
            yaml.set(path + "nucleo-ultimo-ataque", base.lastCoreAttack);
            yaml.set(path + "nucleo-ultima-regeneracao", base.lastCoreRegen);
            yaml.set(path + "cofre", base.vault == null ? null : base.vault.serialize());
            yaml.set(path + "cofre-vida", base.vaultHp);
            yaml.set(path + "cofre-saldo", base.vaultBalance);
            yaml.set(path + "juros-fracao", base.interestFraction);
            yaml.set(path + "poder", base.power);
            yaml.set(path + "vulneravel-ate", base.vulnerableUntil);
            yaml.set(path + "nucleo-desativado-ate", base.coreDisabledUntil);
            yaml.set(path + "nucleo-prazo-recolocar", base.coreReplaceDeadline);
            yaml.set(path + "nucleo-recolhido-por", base.corePickedBy == null ? null : base.corePickedBy.toString());
            yaml.set(path + "nucleo-punicao", base.coreMissingPenalty);
            yaml.set(path + "nucleo-gratis-usado", base.freeCoreTaken);
            yaml.set(path + "nucleo-item-id", base.coreItemId);
            yaml.set(path + "cofre-item-id", base.vaultItemId);
            yaml.set(path + "divida", base.debt);
            List<Map<String, Object>> chests = new ArrayList<>();
            for (ClanChest chest : base.chests.values()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", chest.id);
                map.put("local", chest.loc.serialize());
                map.put("vida", chest.hp);
                chests.add(map);
            }
            yaml.set(path + "baus", chests);
        }
        yaml.set("juros-ultimo", lastInterest);
        yaml.set("manutencao-ultima-data", lastUpkeepDate);
    }

    public void save() {
        clans.save();
    }

    static String formatDuration(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return seconds >= 3600
                ? String.format(Locale.ROOT, "%dh%02dm", seconds / 3600, (seconds % 3600) / 60)
                : String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private static long saturatedAdd(long a, long b) {
        long result = a + b;
        return ((a ^ result) & (b ^ result)) < 0 ? Long.MAX_VALUE : result;
    }
}
