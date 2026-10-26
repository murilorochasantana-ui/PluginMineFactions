package br.com.murilo.muriloplug.eventos;

import br.com.murilo.muriloplug.auth.AuthManager;
import br.com.murilo.muriloplug.clan.ClanManager;
import br.com.murilo.muriloplug.economy.EconomyManager;
import br.com.murilo.muriloplug.shop.ShopUi;
import br.com.murilo.muriloplug.stats.PlayerKillEvent;
import br.com.murilo.muriloplug.teste.StatusRegistry;
import br.com.murilo.muriloplug.teste.TestMode;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Recompensas na cabeça de jogadores, criadas pelos Contratos de Recompensa. O servidor cria o dinheiro (quem usa o
 * contrato não paga). O alvo fica "Procurado" (status, "$" vermelho no nome e linha no sidebar); outro contrato no
 * mesmo alvo soma. Quem matar o alvo (killer do evento de kill, inclusive o creditado sem golpe final) ganha em
 * maço, menos membros do clan do alvo. A recompensa expira depois de {@code duracao-horas} de tempo ONLINE do alvo.
 */
public final class BountyManager implements Listener {

    public static final String VALUES_PATH = "eventos.recompensa.valores";
    public static final String DURATION_PATH = "eventos.recompensa.duracao-horas";
    public static final String MODEL_PATH = "eventos.recompensa.modelo-contrato";
    public static final String STATUS = "Procurado";
    private static final List<Long> DEFAULT_VALUES = List.of(1_000L, 3_000L, 8_000L, 20_000L, 50_000L);

    private static final class Bounty {
        long value;
        long remainingMillis;

        Bounty(long value, long remainingMillis) {
            this.value = value;
            this.remainingMillis = remainingMillis;
        }
    }

    private final JavaPlugin plugin;
    private final EconomyManager economy;
    private final ClanManager clans;
    private final AuthManager auth;
    private final StatusRegistry statuses;
    private final File file;
    private final Map<UUID, Bounty> bounties = new HashMap<>();
    private TestMode test = TestMode.inactive();
    private ContractItems contracts;
    private Consumer<UUID> changeListener = id -> { };
    private int ticks;

    public BountyManager(JavaPlugin plugin, EconomyManager economy, ClanManager clans, AuthManager auth,
                         StatusRegistry statuses) {
        this.plugin = plugin;
        this.economy = economy;
        this.clans = clans;
        this.auth = auth;
        this.statuses = statuses;
        this.file = new File(plugin.getDataFolder(), "recompensas.yml");
    }

    public static boolean addMissing(FileConfiguration config) {
        boolean changed = false;
        if (!config.contains(VALUES_PATH)) {
            config.set(VALUES_PATH, DEFAULT_VALUES);
            changed = true;
        }
        if (!config.contains(DURATION_PATH)) {
            config.set(DURATION_PATH, 2);
            changed = true;
        }
        if (!config.contains(MODEL_PATH)) {
            config.set(MODEL_PATH, "minecraft:map");
            changed = true;
        }
        return changed;
    }

    public void setTestMode(TestMode test) {
        this.test = test;
    }

    public void setContracts(ContractItems contracts) {
        this.contracts = contracts;
    }

    /** Chamado quando a recompensa de um jogador muda (nametag e sidebar). */
    public void setChangeListener(Consumer<UUID> changeListener) {
        this.changeListener = changeListener;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /** Valor da recompensa de um contrato do nível dado. */
    public long value(int level) {
        List<Long> values = plugin.getConfig().getLongList(VALUES_PATH);
        if (values.isEmpty()) {
            values = DEFAULT_VALUES;
        }
        return Math.max(0, values.get(Math.max(0, Math.min(values.size() - 1, level - 1))));
    }

    private long durationMillis() {
        return (long) (Math.max(0.01, plugin.getConfig().getDouble(DURATION_PATH, 2)) * 3_600_000L);
    }

    /** Valor da recompensa na cabeça do jogador (0 = nenhuma). */
    public long bountyOf(UUID player) {
        Bounty bounty = bounties.get(player);
        return bounty == null ? 0 : bounty.value;
    }

    // ------------------------------------------------------------ contrato

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (contracts == null || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack item = event.getItem();
        int level = contracts.level(item);
        if (level == 0) {
            return;
        }
        event.setCancelled(true);
        Player user = event.getPlayer();
        List<Player> targets = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(user) && !clans.isSameClan(user.getUniqueId(), online.getUniqueId())
                    && auth.isLoggedIn(online.getUniqueId()) && !test.isActive(online)) {
                targets.add(online);
            }
        }
        if (targets.isEmpty()) {
            user.sendMessage("§cNão há nenhum alvo válido online agora. O contrato não foi gasto.");
            return;
        }
        // Gasta o contrato (direto no slot da mão usada) antes de criar a recompensa.
        EquipmentSlot hand = event.getHand() == null ? EquipmentSlot.HAND : event.getHand();
        ItemStack inHand = user.getInventory().getItem(hand);
        if (contracts.level(inHand) != level) {
            return;
        }
        inHand.setAmount(inHand.getAmount() - 1);
        user.getInventory().setItem(hand, inHand.getAmount() > 0 ? inHand : null);
        Player target = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
        long value = value(level);
        add(target, value);
        user.sendMessage("§aSeu contrato colocou uma recompensa de " + EconomyManager.format(value) + " em §e"
                + target.getName() + "§a.");
    }

    private void add(Player target, long value) {
        Bounty bounty = bounties.get(target.getUniqueId());
        if (bounty == null) {
            bounty = new Bounty(0, durationMillis());
            bounties.put(target.getUniqueId(), bounty);
        }
        bounty.value += value;
        bounty.remainingMillis = Math.max(bounty.remainingMillis, durationMillis());
        statuses.add(target, STATUS, StatusRegistry.INDEFINITE);
        save();
        Bukkit.broadcast(ShopUi.text("§c§lRECOMPENSA! §e" + target.getName() + " vale "
                + EconomyManager.format(bounty.value) + "! Mate ele para ganhar."));
        target.playSound(target.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.4f);
        changeListener.accept(target.getUniqueId());
    }

    // ------------------------------------------------------------ morte do alvo

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(PlayerKillEvent event) {
        Bounty bounty = bounties.get(event.getVictim());
        if (bounty == null) {
            return;
        }
        UUID killer = event.getKiller();
        String victimName = ClanManager.nameOf(event.getVictim());
        if (clans.isSameClan(killer, event.getVictim())) {
            Player online = Bukkit.getPlayer(killer);
            if (online != null) {
                online.sendMessage("§cVocê não pode ganhar a recompensa de um membro do seu clan. Ela continua valendo.");
            }
            return;
        }
        bounties.remove(event.getVictim());
        statuses.remove(event.getVictim(), STATUS);
        save();
        boolean notes = economy.pagarGanho(killer, bounty.value);
        Bukkit.broadcast(ShopUi.text("§c§lRECOMPENSA! §e" + ClanManager.nameOf(killer) + " matou " + victimName
                + " e ganhou " + EconomyManager.format(bounty.value) + (notes ? " em maço" : "") + "!"));
        changeListener.accept(event.getVictim());
    }

    // ------------------------------------------------------------ tempo (só conta com o alvo online)

    private void tick() {
        ticks++;
        boolean changed = false;
        for (Map.Entry<UUID, Bounty> entry : List.copyOf(bounties.entrySet())) {
            Player target = Bukkit.getPlayer(entry.getKey());
            if (target == null) {
                continue; // offline: o tempo pausa
            }
            Bounty bounty = entry.getValue();
            bounty.remainingMillis -= 1000;
            if (ticks % 60 == 0) {
                statuses.add(target, STATUS, StatusRegistry.INDEFINITE); // a marca dura no máximo 1h
            }
            if (bounty.remainingMillis <= 0) {
                bounties.remove(entry.getKey());
                statuses.remove(entry.getKey(), STATUS);
                changed = true;
                Bukkit.broadcast(ShopUi.text("§eA recompensa de " + EconomyManager.format(bounty.value) + " por §f"
                        + target.getName() + " §eexpirou."));
                changeListener.accept(entry.getKey());
            }
        }
        if (changed || (ticks % 60 == 0 && !bounties.isEmpty())) {
            save();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bounty bounty = bounties.get(event.getPlayer().getUniqueId());
        if (bounty != null) {
            statuses.add(event.getPlayer(), STATUS, StatusRegistry.INDEFINITE);
            event.getPlayer().sendMessage("§cHá uma recompensa de " + EconomyManager.format(bounty.value)
                    + " na sua cabeça!");
        }
    }

    // ------------------------------------------------------------ interface

    /** Linha do sidebar do alvo: "Recompensa: $X" (null sem recompensa). */
    public Component sidebarLine(Player player) {
        long value = bountyOf(player.getUniqueId());
        return value <= 0 ? null : ShopUi.text("§cRecompensa: " + EconomyManager.format(value));
    }

    /** Sufixo extra do nome (" $" vermelho) para quem tem recompensa. */
    public Component nameSuffix(UUID player) {
        return bountyOf(player) > 0 ? ShopUi.text(" §c$") : Component.empty();
    }

    // ------------------------------------------------------------ arquivo

    public void load() {
        bounties.clear();
        if (!file.exists()) {
            return;
        }
        ConfigurationSection section = YamlConfiguration.loadConfiguration(file).getConfigurationSection("recompensas");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                long value = section.getLong(key + ".valor");
                long remaining = section.getLong(key + ".restante-ms");
                if (value > 0 && remaining > 0) {
                    bounties.put(UUID.fromString(key), new Bounty(value, remaining));
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("UUID inválido em recompensas.yml, ignorado: " + key);
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        bounties.forEach((id, bounty) -> {
            yaml.set("recompensas." + id + ".valor", bounty.value);
            yaml.set("recompensas." + id + ".restante-ms", bounty.remainingMillis);
        });
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar recompensas.yml: " + e.getMessage());
        }
    }

    /** Cancela a recompensa na cabeça do jogador. Devolve o valor cancelado (reset de conta). */
    public long cancel(UUID player) {
        Bounty bounty = bounties.remove(player);
        if (bounty == null) {
            return 0;
        }
        statuses.remove(player, STATUS);
        save();
        changeListener.accept(player);
        return bounty.value;
    }
}
