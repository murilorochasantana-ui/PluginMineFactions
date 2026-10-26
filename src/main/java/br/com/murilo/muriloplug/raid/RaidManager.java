package br.com.murilo.muriloplug.raid;

import br.com.murilo.muriloplug.claim.Claim;
import br.com.murilo.muriloplug.claim.ClaimManager;
import br.com.murilo.muriloplug.claim.ClaimRaidAccessEvent;
import br.com.murilo.muriloplug.clan.Clan;
import br.com.murilo.muriloplug.clan.ClanCommand;
import br.com.murilo.muriloplug.clan.ClanManager;
import br.com.murilo.muriloplug.clan.base.BaseDamageEvent;
import br.com.murilo.muriloplug.clan.base.ClanBaseManager;
import br.com.murilo.muriloplug.clan.base.ExplosionOwners;
import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import br.com.murilo.muriloplug.clan.base.ClanBase;
import br.com.murilo.muriloplug.economy.EconomyManager;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Registro de raids. Um raid começa quando um INIMIGO do dono de um claim (não o dono, não o clan dele, não quem tem
 * confiança no claim) explode blocos do claim, tira pontos do Núcleo ou do Cofre, ou quebra/abre algo no claim
 * vulnerável/enfraquecido. Há um raid por defensor (o clan do dono, ou o dono sem clan); ele acaba depois de
 * {@code raid.fim-apos-minutos} sem nenhum desses eventos.
 *
 * <p>Raid contra um clan tem uma barra de chefe (vermelha, 10 segmentos) para o clan atacado, os atacantes e os clans
 * deles: recompensa que ainda falta pagar pelo Núcleo, pontos do Núcleo e o escudo. Some no fim do raid ou 5 segundos
 * depois de o Núcleo ser destruído.
 *
 * <p>As contagens ficam só em memória durante o raid; o raids.yml é gravado no fim de cada raid (últimos 10 sofridos
 * e feitos de cada clan, e os resumos guardados para quem estava offline).
 */
public final class RaidManager implements Listener {

    public static final String END_PATH = "raid.fim-apos-minutos";
    public static final String ALERT_PATH = "raid.aviso-a-cada-minutos";
    public static final String NEARBY_PATH = "raid.raio-atacantes";
    private static final int KEEP = 10;

    /** Raid em andamento (só memória). */
    private static final class Raid {
        final String defender;
        String claimName;
        final String world;
        int x;
        int z;
        final long start;
        long lastEvent;
        long lastAlert;
        int blocks;
        int containers;
        int coreDamage;
        int vaultDamage;
        boolean coreDestroyed;
        long moneyLost;
        /** Linhas "Máquina destruída: ..." (uma por máquina). */
        final List<String> machines = new ArrayList<>();
        final Set<UUID> attackers = new LinkedHashSet<>();
        /** Barra de chefe (só raids contra clans). */
        BossBar bar;
        final Set<UUID> barViewers = new java.util.HashSet<>();
        long coreDestroyedAt;
        boolean barClosed;

        Raid(String defender, String claimName, Location location) {
            this.defender = defender;
            this.claimName = claimName;
            this.world = location.getWorld().getName();
            this.x = location.getBlockX();
            this.z = location.getBlockZ();
            this.start = System.currentTimeMillis();
            this.lastEvent = start;
        }
    }

    private final JavaPlugin plugin;
    private final ClaimManager claims;
    private final ClanManager clans;
    private final ClanBaseManager bases;
    private final ExplosionOwners owners;
    private final File file;
    private final Map<String, Raid> active = new HashMap<>();
    private final Map<String, List<RaidRecord>> suffered = new HashMap<>();
    private final Map<String, List<RaidRecord>> made = new HashMap<>();
    private final Map<UUID, List<RaidRecord>> pending = new HashMap<>();

    public RaidManager(JavaPlugin plugin, ClaimManager claims, ClanManager clans, ClanBaseManager bases,
                       ExplosionOwners owners) {
        this.plugin = plugin;
        this.claims = claims;
        this.clans = clans;
        this.bases = bases;
        this.owners = owners;
        this.file = new File(plugin.getDataFolder(), "raids.yml");
    }

    public static boolean addMissing(FileConfiguration config) {
        boolean changed = false;
        for (Object[] entry : new Object[][]{{END_PATH, 5}, {ALERT_PATH, 2}, {NEARBY_PATH, 64}}) {
            if (!config.contains((String) entry[0])) {
                config.set((String) entry[0], entry[1]);
                changed = true;
            }
        }
        return changed;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L * 20, 20L * 20);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::barTick, 20L, 20L);
    }

    public void registerOn(ClanCommand command) {
        command.register("raids", new ClanCommand.Extra("últimos raids sofridos e feitos",
                (player, args) -> open(player, false), (player, args) -> List.of()));
    }

    /** Se a base do clan está sendo atacada agora (há um raid em andamento contra ele). */
    public boolean underRaid(String clanTag) {
        return clanTag != null && active.containsKey(clanTag);
    }

    private long endMillis() {
        return Math.max(1, plugin.getConfig().getInt(END_PATH, 5)) * 60_000L;
    }

    private long alertMillis() {
        return Math.max(0, plugin.getConfig().getInt(ALERT_PATH, 2)) * 60_000L;
    }

    private double nearbyRadius() {
        return Math.max(8, plugin.getConfig().getDouble(NEARBY_PATH, 64));
    }

    // ------------------------------------------------------------ quem é quem

    /** Chave do defensor: a TAG do clan do dono, ou "p:<uuid>" para quem não tem clan. */
    private String defenderKey(UUID owner) {
        Clan clan = clans.getClan(owner);
        return clan != null ? clan.tag() : "p:" + owner;
    }

    private String attackerKey(UUID attacker) {
        return defenderKey(attacker);
    }

    private String keyName(String key) {
        if (key.startsWith("p:")) {
            try {
                return ClanManager.nameOf(UUID.fromString(key.substring(2)));
            } catch (IllegalArgumentException e) {
                return key;
            }
        }
        return "[" + key + "]";
    }

    private boolean isEnemy(UUID player, Claim claim) {
        return !player.equals(claim.owner()) && !clans.isSameClan(player, claim.owner()) && !claim.isTrusted(player);
    }

    /** Inimigos do dono do claim online por perto (para explosões sem dono conhecido). */
    private List<UUID> enemiesNear(Claim claim, Location location) {
        List<UUID> list = new ArrayList<>();
        double radius = nearbyRadius();
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getGameMode() != GameMode.SPECTATOR && isEnemy(player.getUniqueId(), claim)
                    && player.getLocation().distanceSquared(location) <= radius * radius) {
                list.add(player.getUniqueId());
            }
        }
        return list;
    }

    /** Inimigos que estão dentro do claim agora (também contam como atacantes). */
    private List<UUID> enemiesInside(Claim claim) {
        List<UUID> list = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() != GameMode.SPECTATOR && claim.contains(player.getLocation())
                    && isEnemy(player.getUniqueId(), claim)) {
                list.add(player.getUniqueId());
            }
        }
        return list;
    }

    // ------------------------------------------------------------ eventos que contam

    /** Explosão que destrói blocos de claims (depois das proteções, então a lista é a final). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (event.blockList().isEmpty()) {
            return;
        }
        Map<Claim, int[]> perClaim = new HashMap<>();
        for (Block block : event.blockList()) {
            Claim claim = claims.getAt(block.getLocation());
            if (claim != null) {
                int[] counts = perClaim.computeIfAbsent(claim, c -> new int[2]);
                counts[0]++;
                if (block.getState(false) instanceof Container) {
                    counts[1]++;
                }
            }
        }
        if (perClaim.isEmpty()) {
            return;
        }
        Entity entity = event.getEntity();
        UUID igniter = owners.ownerOf(entity);
        boolean tnt = entity instanceof TNTPrimed || entity instanceof ExplosiveMinecart;
        for (Map.Entry<Claim, int[]> entry : perClaim.entrySet()) {
            Claim claim = entry.getKey();
            List<UUID> attackers;
            if (igniter != null) {
                if (!isEnemy(igniter, claim)) {
                    continue; // explosão do próprio dono/clan: não é raid
                }
                attackers = List.of(igniter);
            } else if (tnt) {
                attackers = enemiesNear(claim, event.getLocation());
                if (attackers.isEmpty()) {
                    continue;
                }
            } else {
                continue; // creeper e afins não são raid
            }
            int[] counts = entry.getValue();
            event(claim, event.getLocation(), attackers, raid -> {
                raid.blocks += counts[0];
                raid.containers += counts[1];
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBaseDamage(BaseDamageEvent event) {
        Clan clan = clans.getByTag(event.getClanTag());
        Claim claim = claims.getAt(event.getLocation());
        if (clan == null || claim == null) {
            return;
        }
        List<UUID> attackers;
        if (event.getAttacker() != null) {
            if (clan.isMember(event.getAttacker()) || !isEnemy(event.getAttacker(), claim)) {
                return;
            }
            attackers = List.of(event.getAttacker());
        } else {
            attackers = enemiesNear(claim, event.getLocation());
            if (attackers.isEmpty()) {
                return;
            }
        }
        event(claim, event.getLocation(), attackers, raid -> {
            if (event.getKind() == BaseDamageEvent.Kind.CORE) {
                raid.coreDamage += event.getDamage();
                raid.coreDestroyed |= event.isDestroyed();
                raid.moneyLost += event.getMoneyLost(); // roubado do Cofre pelos pontos do Núcleo
            } else if (event.getKind() == BaseDamageEvent.Kind.CHEST) {
                if (event.isDestroyed()) {
                    raid.containers++;
                }
            } else if (event.getKind() == BaseDamageEvent.Kind.MACHINE) {
                if (event.isDestroyed() && event.getDetail() != null) {
                    raid.machines.add(event.getDetail());
                }
            } else {
                raid.vaultDamage += event.getDamage();
                raid.moneyLost += event.getMoneyLost();
            }
        });
        Raid raid = active.get(clan.tag());
        if (raid != null && raid.bar != null && event.getKind() == BaseDamageEvent.Kind.CORE && event.isDestroyed()) {
            raid.coreDestroyedAt = System.currentTimeMillis();
            raid.bar.name(ShopUi.text("§c§lNÚCLEO DESATIVADO!"));
            raid.bar.progress(0f);
        }
        if (raid != null) {
            updateBar(raid); // atualiza na hora (ponto tirado, escudo)
        }
    }

    // ------------------------------------------------------------ barra de chefe

    private void barTick() {
        for (Raid raid : List.copyOf(active.values())) {
            updateBar(raid);
        }
    }

    private void updateBar(Raid raid) {
        if (raid.bar == null || raid.barClosed) {
            return;
        }
        if (raid.coreDestroyedAt > 0) {
            if (System.currentTimeMillis() - raid.coreDestroyedAt >= 5_000L) {
                closeBar(raid);
            } else {
                showBar(raid);
            }
            return;
        }
        Clan clan = clans.getByTag(raid.defender);
        if (clan == null) {
            closeBar(raid);
            return;
        }
        ClanBase base = bases.of(clan);
        if (base.core() != null && base.isCoreDisabled()) {
            raid.bar.name(ShopUi.text("§c⚔ Raid em [" + clan.tag() + "] §8| §cNúcleo desativado ("
                    + String.format(java.util.Locale.ROOT, "%02d:%02d", Math.max(0, base.coreDisabledUntil()
                    - System.currentTimeMillis()) / 60_000, Math.max(0, base.coreDisabledUntil()
                    - System.currentTimeMillis()) / 1000 % 60) + ")"
                    + (raid.machines.isEmpty() ? "" : " §8| §c⚙ Máquinas destruídas: " + raid.machines.size())));
            raid.bar.progress(0f);
        } else if (base.core() == null) {
            raid.bar.name(ShopUi.text("§c⚔ Raid em [" + clan.tag() + "] §8| §7Sem Núcleo"
                    + (raid.machines.isEmpty() ? "" : " §8| §c⚙ Máquinas destruídas: " + raid.machines.size())));
            raid.bar.progress(1f);
        } else {
            // Estimativa (com o saldo de agora) do que ainda dá para roubar do Cofre pelo Núcleo.
            long remaining = bases.stealRemaining(clan);
            long total = bases.stealTotal(clan);
            String title = "§c⚔ Raid em [" + clan.tag() + "] §8| §eRecompensa restante: " + EconomyManager.format(remaining)
                    + " §8| §6Núcleo " + base.coreHp() + "/" + bases.settings().coreHp();
            int shield = bases.getEscudoRestante(clan);
            if (shield > 0) {
                title += " §8| §b🛡 Escudo " + shield + "s";
            }
            if (!raid.machines.isEmpty()) {
                title += " §8| §c⚙ Máquinas destruídas: " + raid.machines.size();
            }
            raid.bar.name(ShopUi.text(title));
            float progress = total > 0 ? remaining / (float) total : base.lowestHp() / (float) bases.settings().coreHp();
            raid.bar.progress(Math.max(0f, Math.min(1f, progress)));
        }
        showBar(raid);
    }

    /** Mostra a barra para o clan atacado, os atacantes e os clans deles (quem voltou ao servidor também). */
    private void showBar(Raid raid) {
        Set<UUID> targets = new LinkedHashSet<>(recipients(raid.defender));
        for (UUID attacker : raid.attackers) {
            targets.add(attacker);
            Clan clan = clans.getClan(attacker);
            if (clan != null) {
                targets.addAll(clan.members());
            }
        }
        for (UUID id : targets) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && raid.barViewers.add(id)) {
                player.showBossBar(raid.bar);
            }
        }
    }

    private void closeBar(Raid raid) {
        if (raid.bar == null || raid.barClosed) {
            return;
        }
        raid.barClosed = true;
        for (UUID id : raid.barViewers) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.hideBossBar(raid.bar);
            }
        }
        raid.barViewers.clear();
    }

    /** Quem sai deixa de contar como "já vendo": ao voltar, a barra aparece de novo. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        for (Raid raid : active.values()) {
            if (raid.barViewers.remove(event.getPlayer().getUniqueId()) && raid.bar != null) {
                event.getPlayer().hideBossBar(raid.bar);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRaidAccess(ClaimRaidAccessEvent event) {
        Claim claim = event.getClaim();
        if (!isEnemy(event.getPlayer().getUniqueId(), claim)) {
            return;
        }
        event(claim, event.getBlock().getLocation(), List.of(event.getPlayer().getUniqueId()), raid -> {
            if (event.getAction() == ClaimRaidAccessEvent.Action.BREAK) {
                raid.blocks++;
                if (event.isContainer()) {
                    raid.containers++;
                }
            } else if (event.getAction() == ClaimRaidAccessEvent.Action.OPEN) {
                raid.containers++;
            }
        });
    }

    private void event(Claim claim, Location location, Collection<UUID> attackers, java.util.function.Consumer<Raid> counts) {
        String key = defenderKey(claim.owner());
        long now = System.currentTimeMillis();
        Raid raid = active.get(key);
        if (raid == null) {
            raid = new Raid(key, claim.name(), location);
            active.put(key, raid);
            if (!key.startsWith("p:")) {
                raid.bar = BossBar.bossBar(ShopUi.text("§c⚔ Raid em [" + key + "]"), 1f, BossBar.Color.RED,
                        BossBar.Overlay.NOTCHED_10);
            }
        }
        raid.claimName = claim.name();
        raid.x = location.getBlockX();
        raid.z = location.getBlockZ();
        raid.lastEvent = now;
        raid.attackers.addAll(attackers);
        raid.attackers.addAll(enemiesInside(claim));
        counts.accept(raid);
        if (raid.lastAlert == 0 || now - raid.lastAlert >= alertMillis()) {
            raid.lastAlert = now;
            alert(raid, claim);
        }
    }

    private void alert(Raid raid, Claim claim) {
        Title title = Title.title(ShopUi.text("§c§lBASE SOB ATAQUE!"), ShopUi.text("§7" + claim.name()),
                Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(3), Duration.ofMillis(750)));
        for (UUID id : recipients(raid.defender)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.showTitle(title);
                player.sendMessage("§cSeu claim " + claim.name() + " está sendo atacado em " + raid.x + ", " + raid.z + "!");
                player.playSound(player.getLocation(), Sound.EVENT_RAID_HORN, 1f, 1f);
            }
        }
    }

    /** Quem recebe os avisos e o resumo: os membros do clan, ou o dono sem clan. */
    private List<UUID> recipients(String key) {
        if (key.startsWith("p:")) {
            try {
                return List.of(UUID.fromString(key.substring(2)));
            } catch (IllegalArgumentException e) {
                return List.of();
            }
        }
        Clan clan = clans.getByTag(key);
        return clan == null ? List.of() : List.copyOf(clan.members());
    }

    // ------------------------------------------------------------ fim do raid

    private void tick() {
        long now = System.currentTimeMillis();
        for (Raid raid : List.copyOf(active.values())) {
            if (now - raid.lastEvent >= endMillis()) {
                finish(raid, true);
            }
        }
    }

    private void finish(Raid raid, boolean announce) {
        active.remove(raid.defender);
        closeBar(raid);
        List<String> attackerNames = new ArrayList<>();
        Set<String> attackerKeys = new LinkedHashSet<>();
        for (UUID attacker : raid.attackers) {
            Clan clan = clans.getClan(attacker);
            attackerNames.add(ClanManager.nameOf(attacker) + (clan == null ? "" : " [" + clan.tag() + "]"));
            attackerKeys.add(attackerKey(attacker));
        }
        RaidRecord record = new RaidRecord(raid.defender, keyName(raid.defender), raid.claimName, raid.world, raid.x,
                raid.z, raid.start, raid.lastEvent, raid.blocks, raid.containers, raid.coreDamage, raid.vaultDamage,
                raid.coreDestroyed, raid.moneyLost, attackerNames, List.copyOf(attackerKeys), raid.machines);
        keep(suffered, raid.defender, record);
        for (String key : attackerKeys) {
            if (!key.equals(raid.defender)) {
                keep(made, key, record);
            }
        }
        for (UUID id : recipients(raid.defender)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && announce) {
                send(player, record);
            } else {
                List<RaidRecord> list = pending.computeIfAbsent(id, k -> new ArrayList<>());
                list.add(record);
                while (list.size() > KEEP) {
                    list.removeFirst();
                }
            }
        }
        // Raid sofrido sem perder o Núcleo: o clan defensor ganha Poder.
        Clan defender = raid.defender.startsWith("p:") ? null : clans.getByTag(raid.defender);
        if (defender != null && !raid.coreDestroyed && bases.settings().powerRaidDefended() != 0) {
            bases.addPower(defender, bases.settings().powerRaidDefended());
            clans.broadcast(defender, "§a+" + bases.settings().powerRaidDefended() + " de Poder: o Núcleo resistiu ao raid.");
        }
        save();
    }

    private static <K> void keep(Map<K, List<RaidRecord>> map, K key, RaidRecord record) {
        List<RaidRecord> list = map.computeIfAbsent(key, k -> new ArrayList<>());
        list.add(record);
        while (list.size() > KEEP) {
            list.removeFirst();
        }
    }

    private void send(Player player, RaidRecord record) {
        player.sendMessage(ShopUi.text("§6§l--- Resumo do raid em " + record.claimName() + " ---"));
        record.lines().forEach(line -> player.sendMessage(ShopUi.text(line)));
    }

    /** onDisable: fecha os raids em andamento (o resumo fica guardado para todos verem ao entrar). */
    public void shutdown() {
        for (Raid raid : List.copyOf(active.values())) {
            finish(raid, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        List<RaidRecord> list = pending.remove(player.getUniqueId());
        if (list == null || list.isEmpty()) {
            return;
        }
        save();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.sendMessage("§c§lEnquanto você estava fora, sua base sofreu " + list.size() + " raid(s):");
                list.forEach(record -> send(player, record));
            }
        }, 60L);
    }

    // ------------------------------------------------------------ menu

    private void open(Player player, boolean showMade) {
        String key = defenderKey(player.getUniqueId());
        RaidsMenu menu = new RaidsMenu(key, keyName(key));
        menu.render(showMade ? made.getOrDefault(key, List.of()) : suffered.getOrDefault(key, List.of()), showMade);
        player.openInventory(menu.getInventory());
    }

    /** O listener dos menus do clan já cancelou o clique; aqui só a troca de aba. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof RaidsMenu menu)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        event.setCancelled(true);
        if (event.getSlot() == RaidsMenu.SUFFERED_SLOT) {
            menu.render(suffered.getOrDefault(menu.key(), List.of()), false);
        } else if (event.getSlot() == RaidsMenu.MADE_SLOT) {
            menu.render(made.getOrDefault(menu.key(), List.of()), true);
        }
    }

    // ------------------------------------------------------------ arquivo

    public void load() {
        suffered.clear();
        made.clear();
        pending.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        readSection(yaml.getConfigurationSection("sofridos"), suffered, false);
        readSection(yaml.getConfigurationSection("feitos"), made, false);
        Map<String, List<RaidRecord>> raw = new HashMap<>();
        readSection(yaml.getConfigurationSection("pendentes"), raw, true);
        raw.forEach((key, list) -> {
            try {
                pending.put(UUID.fromString(key), list);
            } catch (IllegalArgumentException ignored) {
                // chave inválida
            }
        });
    }

    private void readSection(ConfigurationSection section, Map<String, List<RaidRecord>> into, boolean any) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            List<RaidRecord> list = new ArrayList<>();
            for (Map<?, ?> map : section.getMapList(key)) {
                try {
                    list.add(RaidRecord.deserialize(map));
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Raid inválido em raids.yml, ignorado: " + e.getMessage());
                }
            }
            if (!list.isEmpty() || any) {
                into.put(key, list);
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        suffered.forEach((key, list) -> yaml.set("sofridos." + key, list.stream().map(RaidRecord::serialize).toList()));
        made.forEach((key, list) -> yaml.set("feitos." + key, list.stream().map(RaidRecord::serialize).toList()));
        pending.forEach((id, list) -> yaml.set("pendentes." + id, list.stream().map(RaidRecord::serialize).toList()));
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar raids.yml: " + e.getMessage());
        }
    }

    /** Apaga os resumos de raid guardados para o jogador ver ao entrar. Devolve quantos eram (reset de conta). */
    public int forget(UUID player) {
        List<RaidRecord> removed = pending.remove(player);
        if (removed == null) {
            return 0;
        }
        save();
        return removed.size();
    }
}
