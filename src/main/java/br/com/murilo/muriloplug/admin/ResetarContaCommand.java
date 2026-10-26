package br.com.murilo.muriloplug.admin;

import br.com.murilo.muriloplug.auth.Account;
import br.com.murilo.muriloplug.auth.AuthManager;
import br.com.murilo.muriloplug.claim.Claim;
import br.com.murilo.muriloplug.claim.ClaimManager;
import br.com.murilo.muriloplug.clan.Clan;
import br.com.murilo.muriloplug.clan.ClanInvites;
import br.com.murilo.muriloplug.clan.ClanManager;
import br.com.murilo.muriloplug.clan.base.BaseBlocks;
import br.com.murilo.muriloplug.clan.base.ClanBase;
import br.com.murilo.muriloplug.clan.base.ClanBaseManager;
import br.com.murilo.muriloplug.clan.base.ClanChest;
import br.com.murilo.muriloplug.clan.base.ClanChestStore;
import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.engenheiro.BuildManager;
import br.com.murilo.muriloplug.classes.engenheiro.GolemManager;
import br.com.murilo.muriloplug.classes.engenheiro.SentinelaManager;
import br.com.murilo.muriloplug.classes.mago.GrimoireManager;
import br.com.murilo.muriloplug.classes.suporte.TrocaEquivalente;
import br.com.murilo.muriloplug.economy.EconomyManager;
import br.com.murilo.muriloplug.eventos.BountyManager;
import br.com.murilo.muriloplug.home.Home;
import br.com.murilo.muriloplug.home.HomeManager;
import br.com.murilo.muriloplug.market.Listing;
import br.com.murilo.muriloplug.market.MarketManager;
import br.com.murilo.muriloplug.raid.RaidManager;
import br.com.murilo.muriloplug.stats.StatsManager;
import br.com.murilo.muriloplug.teste.TestMode;
import br.com.murilo.muriloplug.tpa.TpaManager;
import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /resetarconta &lt;jogador&gt; [confirmar [--inventario] [--senha]]: apaga os dados de um jogador em todos os
 * sistemas, depois de salvar um backup completo em plugins/MinePlug/backups/reset-&lt;nick&gt;-&lt;data-hora&gt;.yml.
 * A confirmação vale 30 segundos; resetar a própria conta pede uma segunda confirmação. Funciona pelo console e com
 * jogador offline. O inventário de quem está offline é limpo quando ele entrar (com um backup próprio).
 */
public final class ResetarContaCommand implements CommandExecutor, TabCompleter, Listener {

    public static final String PERMISSION = "mineplug.admin.resetar";
    private static final long CONFIRM_MILLIS = 30_000L;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final String CONSOLE = "console";

    /** Tudo o que o reset precisa (um campo por sistema que guarda dados do jogador). */
    public record Systems(AuthManager auth, ClaimManager claims, HomeManager homes, ClanManager clans,
                          ClanInvites invites, ClanBaseManager bases, BaseBlocks baseBlocks, ClanChestStore chests,
                          EconomyManager economy, StatsManager stats, ClassManager classes, GrimoireManager grimoire,
                          TrocaEquivalente troca, MarketManager market, TpaManager tpa, SentinelaManager sentinels,
                          BuildManager builds, GolemManager golems, BountyManager bounties, RaidManager raids,
                          TestMode test) {
    }

    private record Pending(UUID target, long expiresAt, boolean selfWarned) {
    }

    private final JavaPlugin plugin;
    private final Systems s;
    private final File backups;
    private final File pendingWipesFile;
    private final Map<String, Pending> pending = new HashMap<>();
    /** Jogadores offline que tiveram o reset com --inventario: limpos ao entrar. */
    private final List<UUID> pendingWipes = new ArrayList<>();

    public ResetarContaCommand(JavaPlugin plugin, Systems systems) {
        this.plugin = plugin;
        this.s = systems;
        this.backups = new File(plugin.getDataFolder(), "backups");
        this.pendingWipesFile = new File(plugin.getDataFolder(), "resets-pendentes.yml");
        loadPendingWipes();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("§cVocê não tem permissão.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§cUso: /" + label + " <jogador> [confirmar [--inventario] [--senha]]");
            return true;
        }
        OfflinePlayer target = findPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§cJogador " + args[0] + " não encontrado (precisa estar online ou já ter entrado no servidor).");
            return true;
        }
        String key = sender instanceof Player player ? player.getUniqueId().toString() : CONSOLE;
        boolean self = sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
        if (args.length == 1) {
            summary(sender, target).forEach(sender::sendMessage);
            if (self) {
                sender.sendMessage("§c§lAtenção: você está resetando a SUA conta.");
            }
            sender.sendMessage("§eDigite /" + label + " " + nameOf(target) + " confirmar em até 30 segundos. "
                    + "§7(opções: --inventario, --senha)");
            pending.put(key, new Pending(target.getUniqueId(), System.currentTimeMillis() + CONFIRM_MILLIS, false));
            return true;
        }
        if (!args[1].equalsIgnoreCase("confirmar")) {
            sender.sendMessage("§cUso: /" + label + " <jogador> [confirmar [--inventario] [--senha]]");
            return true;
        }
        boolean inventory = false;
        boolean password = false;
        for (int i = 2; i < args.length; i++) {
            switch (args[i].toLowerCase(Locale.ROOT)) {
                case "--inventario" -> inventory = true;
                case "--senha" -> password = true;
                default -> {
                    sender.sendMessage("§cOpção desconhecida: " + args[i] + " (use --inventario e/ou --senha).");
                    return true;
                }
            }
        }
        Pending entry = pending.get(key);
        if (entry == null || !entry.target().equals(target.getUniqueId()) || entry.expiresAt() < System.currentTimeMillis()) {
            pending.remove(key);
            sender.sendMessage("§cNenhuma confirmação válida para " + nameOf(target) + ". Use /" + label + " "
                    + nameOf(target) + " primeiro (a confirmação vale 30 segundos).");
            return true;
        }
        if (self && !entry.selfWarned()) {
            pending.put(key, new Pending(target.getUniqueId(), System.currentTimeMillis() + CONFIRM_MILLIS, true));
            sender.sendMessage("§c§lVocê está resetando a SUA conta! §cDigite o mesmo comando de novo em até 30 "
                    + "segundos para confirmar.");
            return true;
        }
        pending.remove(key);
        reset(sender, target, inventory, password);
        return true;
    }

    // ------------------------------------------------------------ resumo

    private List<String> summary(CommandSender viewer, OfflinePlayer target) {
        UUID id = target.getUniqueId();
        List<String> lines = new ArrayList<>();
        lines.add("§6§lResumo do reset de " + nameOf(target) + (target.isOnline() ? " §a(online)" : " §7(offline)"));
        lines.add("§7Claims: §f" + s.claims().getByOwner(id).size() + " §7| trust em claims de outros: §f"
                + s.claims().trustedIn(id).size());
        lines.add("§7Homes: §f" + s.homes().getByOwner(id).size());
        Clan clan = s.clans().getClan(id);
        if (clan == null) {
            lines.add("§7Clan: §fnenhum");
        } else if (!clan.leader().equals(id)) {
            lines.add("§7Clan: §f[" + clan.tag() + "] membro §7(sai do clan)");
        } else if (clan.size() > 1) {
            lines.add("§7Clan: §f[" + clan.tag() + "] líder §7(a liderança passa para " + ClanManager.nameOf(oldestMember(clan))
                    + ")");
        } else {
            ClanBase base = s.bases().of(clan);
            lines.add("§7Clan: §f[" + clan.tag() + "] líder sozinho §c(o clan é desfeito: Núcleo, Cofre com "
                    + EconomyManager.format(base.vaultBalance()) + " e " + base.chests().size() + " Baú(s) somem)");
        }
        lines.add("§7Saldo: §f" + EconomyManager.format(s.economy().getBalance(id)));
        lines.add("§7K/D/A: §f" + s.stats().getKills(id) + "/" + s.stats().getDeaths(id) + "/" + s.stats().getAssists(id));
        PlayerClass playerClass = s.classes().getClass(id);
        lines.add("§7Classe: §f" + (playerClass == null ? "nenhuma" : playerClass.displayName())
                + " §7| magias aprendidas: §f" + s.grimoire().learned(id).size()
                + (s.classes().getManaMax(id) != null ? " §7| mana máxima própria: §f" + s.classes().getManaMax(id) : ""));
        lines.add("§7Anúncios no mercado: §f" + s.market().bySeller(id).size());
        lines.add("§7Sentinelas: §f" + s.sentinels().count(id) + " §7| construção aberta: §f"
                + (s.builds().hasOpenBuild(id) ? "sim" : "não"));
        long bounty = s.bounties().bountyOf(id);
        lines.add("§7Recompensa na cabeça: §f" + (bounty > 0 ? EconomyManager.format(bounty) : "nenhuma")
                + " §7| TPA bloqueado: §f" + (s.tpa().isBlocked(id) ? "sim" : "não")
                + " §7| Modo Teste: §f" + (s.test().isActive(id) ? "ligado" : "desligado"));
        lines.add("§7Login registrado: §f" + (s.auth().isRegistered(id) ? "sim §7(só apaga com --senha)" : "não"));
        return lines;
    }

    private static UUID oldestMember(Clan clan) {
        for (UUID member : clan.members()) {
            if (!member.equals(clan.leader())) {
                return member;
            }
        }
        return null;
    }

    // ------------------------------------------------------------ reset

    private void reset(CommandSender admin, OfflinePlayer target, boolean wipeInventory, boolean wipePassword) {
        UUID id = target.getUniqueId();
        String nick = nameOf(target);
        Player online = target.getPlayer();

        // 1. Backup de TUDO antes de qualquer mudança.
        String backupName;
        try {
            backupName = writeBackup(target, wipeInventory, wipePassword);
        } catch (IOException e) {
            admin.sendMessage("§cNão foi possível salvar o backup (" + e.getMessage() + "). NADA foi apagado.");
            return;
        }

        List<String> done = new ArrayList<>();
        // 2. Inventário (online agora; offline ao entrar).
        if (wipeInventory) {
            if (online != null) {
                wipeInventory(online);
                done.add("inventário, armadura, ender chest e XP limpos");
            } else {
                pendingWipes.add(id);
                savePendingWipes();
                done.add("inventário será limpo quando ele entrar (está offline)");
            }
        }
        // 3. Claims e trusts.
        int claims = s.claims().removeAll(id).size();
        int trusts = s.claims().removeTrustEverywhere(id);
        done.add(claims + " claim(s) e " + trusts + " trust(s) em claims de outros");
        // 4. Homes.
        done.add(s.homes().removeAll(id).size() + " home(s)");
        // 5. Clan.
        s.invites().clear(id);
        done.add(resetClan(id, nick));
        // 6. Dinheiro e KDA.
        long balance = s.economy().getBalance(id);
        s.economy().setBalance(id, 0);
        done.add("saldo de " + EconomyManager.format(balance) + " zerado");
        s.stats().reset(id);
        done.add("K/D/A zerado");
        // 7. Classe (dispara a troca de classe: golems, totens, voo, vida extra etc. saem), Grimório e estados.
        s.golems().removeAll(id);
        s.classes().removeClass(id);
        s.classes().setManaMax(id, null);
        s.grimoire().forget(id);
        s.troca().forget(id);
        done.add("classe, Grimório, mana máxima própria e Troca Equivalente");
        // 8. Mercado (os itens ficam só no backup).
        int listings = 0;
        for (Listing listing : s.market().bySeller(id)) {
            if (s.market().remove(listing.id()) != null) {
                listings++;
            }
        }
        done.add(listings + " anúncio(s) do mercado (itens no backup)");
        // 9. TPA, Engenheiro, recompensa, raids pendentes, Modo Teste.
        s.tpa().forget(id);
        done.add(s.sentinels().removeAll(id).size() + " sentinela(s)");
        if (s.builds().hasOpenBuild(id)) {
            s.builds().cancel(id, null);
            done.add("construção aberta cancelada");
        }
        long bounty = s.bounties().cancel(id);
        if (bounty > 0) {
            done.add("recompensa de " + EconomyManager.format(bounty) + " cancelada");
        }
        int raids = s.raids().forget(id);
        if (raids > 0) {
            done.add(raids + " resumo(s) de raid pendente(s)");
        }
        if (s.test().deactivate(id)) {
            done.add("Modo Teste desligado");
        }
        // 10. Expulsa (entra de novo com tudo limpo) e, se pedido, apaga o login.
        if (online != null) {
            online.kick(ShopUi.text("§cSua conta foi resetada por um administrador."));
        }
        if (wipePassword) {
            Account account = s.auth().account(id);
            if (account != null && s.auth().reset(account.nick())) {
                done.add("registro de login apagado (precisa usar /register)");
            }
        }

        admin.sendMessage("§a§lConta de " + nick + " resetada:");
        done.forEach(line -> admin.sendMessage("§7- " + line));
        admin.sendMessage("§7Backup: §fplugins/MinePlug/backups/" + backupName);
        plugin.getLogger().info(admin.getName() + " resetou a conta de " + nick + " (backup: " + backupName + ")");
    }

    private String resetClan(UUID id, String nick) {
        Clan clan = s.clans().getClan(id);
        if (clan == null) {
            return "sem clan";
        }
        if (!clan.leader().equals(id)) {
            s.clans().removeMember(clan, id);
            s.clans().broadcast(clan, "§e" + nick + " §7saiu do clan (conta resetada).");
            return "saiu do clan [" + clan.tag() + "]";
        }
        UUID heir = oldestMember(clan);
        if (heir != null) {
            s.clans().transferLeader(clan, heir);
            s.clans().removeMember(clan, id);
            s.clans().broadcast(clan, "§e" + ClanManager.nameOf(heir) + " §7é o novo líder do clan ("
                    + nick + " teve a conta resetada).");
            return "liderança de [" + clan.tag() + "] passou para " + ClanManager.nameOf(heir);
        }
        // Líder sozinho: o clan acaba. Núcleo, Cofre e Baús saem do mundo e dos arquivos; o dinheiro do Cofre some.
        long lost = s.bases().of(clan).vaultBalance();
        s.baseBlocks().purge(clan);
        s.clans().disband(clan);
        plugin.getLogger().info("Clan [" + clan.tag() + "] desfeito pelo reset de " + nick + " (Cofre perdido: "
                + EconomyManager.format(lost) + "; itens dos Baús no backup).");
        return "clan [" + clan.tag() + "] desfeito (Núcleo, Cofre e Baús removidos)";
    }

    private static void wipeInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.getEnderChest().clear();
        player.setLevel(0);
        player.setExp(0);
        player.setTotalExperience(0);
    }

    // ------------------------------------------------------------ backup

    private String writeBackup(OfflinePlayer target, boolean wipeInventory, boolean wipePassword) throws IOException {
        UUID id = target.getUniqueId();
        String nick = nameOf(target);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("jogador.nick", nick);
        yaml.set("jogador.uuid", id.toString());
        yaml.set("data", LocalDateTime.now().toString());
        yaml.set("opcoes.inventario", wipeInventory);
        yaml.set("opcoes.senha", wipePassword);

        List<Map<String, Object>> claims = new ArrayList<>();
        for (Claim claim : s.claims().getByOwner(id)) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", claim.name());
            map.put("world", claim.world());
            map.put("minX", claim.minX());
            map.put("minZ", claim.minZ());
            map.put("maxX", claim.maxX());
            map.put("maxZ", claim.maxZ());
            map.put("trusted", claim.trusted().stream().map(UUID::toString).toList());
            claims.add(map);
        }
        yaml.set("claims", claims);
        yaml.set("trust-em-claims-de-outros", s.claims().trustedIn(id).stream()
                .map(claim -> claim.owner() + ":" + claim.name()).toList());

        List<Map<String, Object>> homes = new ArrayList<>();
        for (Home home : s.homes().getByOwner(id)) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", home.name());
            map.put("world", home.world());
            map.put("x", home.x());
            map.put("y", home.y());
            map.put("z", home.z());
            map.put("yaw", home.yaw());
            map.put("pitch", home.pitch());
            homes.add(map);
        }
        yaml.set("homes", homes);

        Clan clan = s.clans().getClan(id);
        if (clan != null) {
            yaml.set("clan.tag", clan.tag());
            yaml.set("clan.nome", clan.name());
            yaml.set("clan.cargo", clan.leader().equals(id) ? "lider" : "membro");
            yaml.set("clan.membros", clan.members().stream().map(UUID::toString).toList());
            if (clan.leader().equals(id) && clan.size() == 1) {
                ClanBase base = s.bases().of(clan);
                yaml.set("clan.base.nucleo", base.core() == null ? null : base.core().serialize());
                yaml.set("clan.base.nucleo-vida", base.coreHp());
                yaml.set("clan.base.cofre", base.vault() == null ? null : base.vault().serialize());
                yaml.set("clan.base.cofre-saldo", base.vaultBalance());
                yaml.set("clan.base.poder", base.power());
                yaml.set("clan.base.divida", base.debt());
                for (ClanChest chest : base.chests()) {
                    String path = "clan.base.baus." + chest.id();
                    yaml.set(path + ".local", chest.loc().serialize());
                    yaml.set(path + ".itens", encode(s.chests().peek(chest.id())));
                }
            }
        }

        yaml.set("saldo", s.economy().getBalance(id));
        yaml.set("kda.kills", s.stats().getKills(id));
        yaml.set("kda.mortes", s.stats().getDeaths(id));
        yaml.set("kda.assistencias", s.stats().getAssists(id));
        PlayerClass playerClass = s.classes().getClass(id);
        yaml.set("classe", playerClass == null ? null : playerClass.name());
        yaml.set("mana-maxima", s.classes().getManaMax(id));
        yaml.createSection("grimorio", s.grimoire().export(id));
        yaml.set("troca-equivalente-ligada", s.troca().isEnabled(id));

        List<Map<String, Object>> listings = new ArrayList<>();
        for (Listing listing : s.market().bySeller(id)) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", listing.id());
            map.put("price", listing.price());
            map.put("created", listing.createdAt());
            map.put("item", Base64.getEncoder().encodeToString(listing.item().serializeAsBytes()));
            listings.add(map);
        }
        yaml.set("mercado", listings);
        yaml.set("tpa-bloqueado", s.tpa().isBlocked(id));
        yaml.set("sentinelas", s.sentinels().count(id));
        yaml.set("construcao-aberta", s.builds().hasOpenBuild(id));
        yaml.set("recompensa-na-cabeca", s.bounties().bountyOf(id));
        yaml.set("modo-teste", s.test().isActive(id));

        Account account = s.auth().account(id);
        if (account != null) {
            yaml.set("login.nick", account.nick());
            yaml.set("login.algorithm", account.algorithm());
            yaml.set("login.iterations", account.iterations());
            yaml.set("login.salt", account.salt());
            yaml.set("login.hash", account.hash());
            yaml.set("login.registered", account.registeredAt());
        }

        Player online = target.getPlayer();
        if (online != null) {
            writeInventory(yaml, online);
        }
        String name = "reset-" + nick + "-" + LocalDateTime.now().format(STAMP) + ".yml";
        backups.mkdirs();
        yaml.save(new File(backups, name));
        return name;
    }

    private static void writeInventory(YamlConfiguration yaml, Player player) {
        yaml.set("inventario.itens", encodeSlots(player.getInventory().getContents()));
        yaml.set("inventario.ender-chest", encodeSlots(player.getEnderChest().getContents()));
        yaml.set("inventario.nivel", player.getLevel());
        yaml.set("inventario.xp", player.getExp());
    }

    private static List<String> encode(List<ItemStack> items) {
        return items.stream().map(item -> Base64.getEncoder().encodeToString(item.serializeAsBytes())).toList();
    }

    /** "slot:base64" de cada slot ocupado. */
    private static List<String> encodeSlots(ItemStack[] contents) {
        List<String> list = new ArrayList<>();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item != null && !item.getType().isAir()) {
                list.add(slot + ":" + Base64.getEncoder().encodeToString(item.serializeAsBytes()));
            }
        }
        return list;
    }

    // ------------------------------------------------------------ inventário de quem estava offline

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!pendingWipes.remove(player.getUniqueId())) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("jogador.nick", player.getName());
        yaml.set("jogador.uuid", player.getUniqueId().toString());
        writeInventory(yaml, player);
        String name = "reset-" + player.getName() + "-inventario-" + LocalDateTime.now().format(STAMP) + ".yml";
        try {
            backups.mkdirs();
            yaml.save(new File(backups, name));
        } catch (IOException e) {
            plugin.getLogger().severe("Backup do inventário de " + player.getName() + " falhou; inventário NÃO limpo: "
                    + e.getMessage());
            pendingWipes.add(player.getUniqueId());
            return;
        }
        wipeInventory(player);
        savePendingWipes();
        plugin.getLogger().info("Inventário de " + player.getName() + " limpo (reset de conta pendente; backup: " + name + ")");
    }

    private void loadPendingWipes() {
        if (!pendingWipesFile.exists()) {
            return;
        }
        for (String raw : YamlConfiguration.loadConfiguration(pendingWipesFile).getStringList("limpar-inventario")) {
            try {
                pendingWipes.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
                // UUID inválido
            }
        }
    }

    private void savePendingWipes() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("limpar-inventario", pendingWipes.stream().map(UUID::toString).toList());
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(pendingWipesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar resets-pendentes.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ auxiliares

    private static OfflinePlayer findPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached != null && cached.hasPlayedBefore() ? cached : null;
    }

    private static String nameOf(OfflinePlayer player) {
        return player.getName() != null ? player.getName() : player.getUniqueId().toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> options;
        if (args.length == 1) {
            options = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        } else if (args.length == 2) {
            options = List.of("confirmar");
        } else if (args[1].equalsIgnoreCase("confirmar")) {
            options = List.of("--inventario", "--senha");
        } else {
            options = List.of();
        }
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
    }
}
