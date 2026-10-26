package br.com.murilo.muriloplug.clan.base;

import br.com.murilo.muriloplug.claim.Claim;
import br.com.murilo.muriloplug.claim.ClaimManager;
import br.com.murilo.muriloplug.claim.ClaimRaidAccessEvent;
import br.com.murilo.muriloplug.clan.Clan;
import br.com.murilo.muriloplug.clan.ClanManager;
import br.com.murilo.muriloplug.combat.CombatManager;
import br.com.murilo.muriloplug.economy.EconomyManager;
import br.com.murilo.muriloplug.economy.maco.Macos;
import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractWindCharge;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Os blocos da base: Núcleo do Clan (BEACON), Cofre do Clan (VAULT, só dinheiro) e Baús do Clan (BARREL, 54 itens).
 * Colocar (com as regras de cada um), proteção (golpes, picareta, pistões, funis e mobs não mexem; explosões nunca
 * quebram o bloco direto), dano por explosão com escudo entre os pontos, textos em cima, regeneração do Núcleo, o
 * menu do Cofre e o inventário dos Baús.
 *
 * <p>Núcleo: cada ponto NOVO tirado (abaixo do menor que ele já atingiu: anti-farm) rouba {@code roubo-por-ponto} do
 * saldo atual do Cofre do clan e entrega a quem acendeu a TNT (se não for do clan dono). Sem quem acendeu (creeper),
 * tira o ponto mas não rouba. Em 0 de vida rouba mais {@code roubo-destruido} do que sobrou e fica DESATIVADO no lugar
 * por {@code vulneravel-minutos} (base vulnerável; sem dano, roubo, regeneração nem recolher); depois reativa sozinho
 * com {@code vida-ao-reativar}. Recolhido, precisa voltar em {@code prazo-recolocar-segundos}, senão a base fica
 * vulnerável até ele voltar (e por pelo menos {@code vulneravel-minutos} depois do fim do prazo).
 */
public final class BaseBlocks implements Listener {

    private static final long TICK = 20L;
    private static final long SHIELD_TICK = 4L;
    private static final long WARN_COOLDOWN_MILLIS = 10_000L;
    /** Como o Núcleo desativado aparece no mundo (o registro continua o mesmo; volta a ser BEACON ao reativar). */
    private static final Material CORE_DISABLED_BLOCK = Material.REINFORCED_DEEPSLATE;

    private final JavaPlugin plugin;
    private final ClanBaseManager manager;
    private final ClanManager clans;
    private final ClaimManager claims;
    private final CombatManager combat;
    private final BaseItems items;
    private final ExplosionOwners owners;
    private final ClanChestStore chestStore;
    private final BaseSettings settings;
    private final Map<BaseLoc, TextDisplay> displays = new HashMap<>();
    /** Saldo do Cofre em cima dele: só os membros do clan veem. */
    private final Map<BaseLoc, TextDisplay> privateDisplays = new HashMap<>();
    /** Vidro azul em volta do bloco enquanto o escudo está ligado. */
    private final Map<BaseLoc, BlockDisplay> shields = new HashMap<>();
    private final Map<String, Long> lastWarn = new HashMap<>();
    private final Map<UUID, Long> lastBreakMessage = new HashMap<>();
    /** Clans que estavam vulneráveis no último tick (para avisar quando acaba). */
    private final Set<String> vulnerable = new HashSet<>();
    /** Outros blocos protegidos como os da base (as máquinas): pistões, funis, mobs e explosões não mexem. */
    private java.util.function.Predicate<Block> extraProtected = block -> false;
    /** Quem mais toma dano das explosões com as mesmas regras (raio e atacante) do Cofre: as máquinas. */
    private final List<java.util.function.BiConsumer<Location, UUID>> explosionListeners = new ArrayList<>();

    public BaseBlocks(JavaPlugin plugin, ClanBaseManager manager, ClaimManager claims, CombatManager combat,
                      BaseItems items, ExplosionOwners owners, ClanChestStore chestStore) {
        this.plugin = plugin;
        this.manager = manager;
        this.clans = manager.clans();
        this.claims = claims;
        this.combat = combat;
        this.items = items;
        this.owners = owners;
        this.chestStore = chestStore;
        this.settings = manager.settings();
    }

    public void start() {
        verifyAll();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK, TICK);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::shieldTick, SHIELD_TICK, SHIELD_TICK);
    }

    /** onDisable: grava os baús e tira textos e escudos (não são salvos no mundo, mas some na hora). */
    public void shutdown() {
        chestStore.saveAll();
        displays.values().forEach(TextDisplay::remove);
        displays.clear();
        privateDisplays.values().forEach(TextDisplay::remove);
        privateDisplays.clear();
        shields.values().forEach(BlockDisplay::remove);
        shields.clear();
    }

    /** Clan criado: nenhum item é entregue; o líder pega o Núcleo e o Cofre pelo /clan itens. */
    public void onClanCreated(Clan clan) {
        Player leader = Bukkit.getPlayer(clan.leader());
        if (leader != null) {
            leader.sendMessage("§6Use /clan itens para pegar o Núcleo e o Cofre do clan.");
        }
    }

    // ------------------------------------------------------------ /clan itens

    /** Abre o menu "Itens do Clan" (só o líder). */
    public void openClanItems(Player player) {
        Clan clan = clans.getClan(player.getUniqueId());
        if (clan == null) {
            player.sendMessage("§cVocê não está em um clan.");
            return;
        }
        if (!clan.leader().equals(player.getUniqueId())) {
            player.sendMessage("§cSó o líder pode pegar os itens do clan.");
            return;
        }
        ClanItemsMenu menu = new ClanItemsMenu(clan.tag());
        renderClanItems(menu, clan);
        player.openInventory(menu.getInventory());
    }

    private void renderClanItems(ClanItemsMenu menu, Clan clan) {
        ClanBase base = manager.of(clan);
        List<String> coreLore = new ArrayList<>(List.of("§7Só é destruído por explosões.", ""));
        coreLore.add(itemState(clan, BaseItems.Kind.CORE).line());
        List<String> vaultLore = new ArrayList<>(List.of("§7Guarda o dinheiro do clan.",
                "§7Saldo guardado: §a" + EconomyManager.format(base.vaultBalance), ""));
        vaultLore.add(itemState(clan, BaseItems.Kind.VAULT).line());
        menu.render(items.create(BaseItems.Kind.CORE), coreLore, items.create(BaseItems.Kind.VAULT), vaultLore);
    }

    /** Estado de um item do clan no /clan itens: se dá para pegar e a linha da lore. */
    private record ItemState(boolean available, String line) {
    }

    private ItemState itemState(Clan clan, BaseItems.Kind kind) {
        ClanBase base = manager.of(clan);
        boolean core = kind == BaseItems.Kind.CORE;
        if ((core ? base.core : base.vault) != null) {
            return new ItemState(false, "§7Já está colocado na base");
        }
        String holder = holderOf(clan, kind, core ? base.coreItemId : base.vaultItemId);
        if (holder != null) {
            return new ItemState(false, "§7Já está com: " + holder);
        }
        return new ItemState(true, "§aClique para pegar");
    }

    /** Nick do membro online que está com o item de id atual no inventário (ou no cursor), ou null. */
    private String holderOf(Clan clan, BaseItems.Kind kind, String currentId) {
        if (currentId == null) {
            return null;
        }
        for (UUID member : clan.members()) {
            Player player = Bukkit.getPlayer(member);
            if (player == null) {
                continue;
            }
            List<ItemStack> carried = new ArrayList<>(java.util.Arrays.asList(player.getInventory().getContents()));
            carried.add(player.getItemOnCursor());
            for (ItemStack item : carried) {
                if (items.read(item) == kind && currentId.equals(items.ownedId(item))) {
                    return player.getName();
                }
            }
        }
        return null;
    }

    /** Clique no menu "Itens do Clan" (o listener dos menus do clan já cancelou). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onClanItemsClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ClanItemsMenu menu)
                || !(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getSlot();
        if (slot != ClanItemsMenu.CORE_SLOT && slot != ClanItemsMenu.VAULT_SLOT) {
            return;
        }
        Clan clan = clans.getClan(player.getUniqueId());
        if (clan == null || !clan.tag().equals(menu.tag()) || !clan.leader().equals(player.getUniqueId())) {
            player.sendMessage("§cSó o líder pode pegar os itens do clan.");
            plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }
        BaseItems.Kind kind = slot == ClanItemsMenu.CORE_SLOT ? BaseItems.Kind.CORE : BaseItems.Kind.VAULT;
        ItemState state = itemState(clan, kind); // confere de novo na hora do clique
        if (!state.available()) {
            player.sendMessage("§c" + state.line().replaceAll("§.", ""));
            renderClanItems(menu, clan);
            return;
        }
        // Id novo: o item anterior (perdido, duplicado...) deixa de valer.
        ClanBase base = manager.of(clan);
        String id = UUID.randomUUID().toString();
        if (kind == BaseItems.Kind.CORE) {
            base.coreItemId = id;
        } else {
            base.vaultItemId = id;
        }
        manager.save();
        give(player, items.createOwned(kind, clan.tag(), id));
        player.sendMessage(kind == BaseItems.Kind.CORE ? "§aVocê pegou o §6Núcleo do Clan§a."
                : "§aVocê pegou o §eCofre do Clan§a.");
        renderClanItems(menu, clan);
    }

    /** Confere se o Núcleo/Cofre na mão pode ser colocado por este jogador; devolve o problema ou null. */
    private String ownedItemProblem(Player player, ItemStack item, BaseItems.Kind kind) {
        String id = items.ownedId(item);
        if (id == null) {
            return kind == BaseItems.Kind.CORE ? "§cUse /clan itens para pegar o Núcleo do seu clan."
                    : "§cUse /clan itens para pegar o Cofre do seu clan.";
        }
        Clan clan = clans.getClan(player.getUniqueId());
        if (clan == null || !clan.tag().equalsIgnoreCase(String.valueOf(items.ownedTag(item)))) {
            return "§cEste item pertence a outro clan.";
        }
        ClanBase base = manager.of(clan);
        return id.equals(kind == BaseItems.Kind.CORE ? base.coreItemId : base.vaultItemId) ? null
                : "§cEste item não é mais válido.";
    }

    // ------------------------------------------------------------ consulta

    private record Ref(ClanBase base, BaseDamageEvent.Kind kind, ClanChest chest) {
    }

    private Ref find(BaseLoc loc) {
        for (ClanBase base : manager.all()) {
            if (loc.equals(base.core)) {
                return new Ref(base, BaseDamageEvent.Kind.CORE, null);
            }
            if (loc.equals(base.vault)) {
                return new Ref(base, BaseDamageEvent.Kind.VAULT, null);
            }
            for (ClanChest chest : base.chests.values()) {
                if (loc.equals(chest.loc)) {
                    return new Ref(base, BaseDamageEvent.Kind.CHEST, chest);
                }
            }
        }
        return null;
    }

    private Ref find(Block block) {
        return find(BaseLoc.of(block));
    }

    public boolean isBaseBlock(Block block) {
        return find(block) != null || extraProtected.test(block);
    }

    /** Blocos de outros sistemas que ganham a mesma proteção dos blocos da base. */
    public void setExtraProtected(java.util.function.Predicate<Block> extraProtected) {
        this.extraProtected = extraProtected;
    }

    /** Chamado a cada explosão que conta para a base (local e quem causou, null se não der para saber). */
    public void addExplosionListener(java.util.function.BiConsumer<Location, UUID> listener) {
        explosionListeners.add(listener);
    }

    /** Distância (do centro do bloco) até onde uma explosão tira pontos do Núcleo, do Cofre e dos Baús. */
    public double explosionRadius() {
        return settings.coreRadius() + 0.5;
    }

    private boolean chestPlaced(String id) {
        for (ClanBase base : manager.all()) {
            if (base.chests.containsKey(id)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ colocar

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        BaseItems.Kind kind = items.read(event.getItemInHand());
        if (kind == null) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        String problem = kind == BaseItems.Kind.CHEST ? null : ownedItemProblem(player, event.getItemInHand(), kind);
        if (problem == null) {
            problem = switch (kind) {
                case CORE -> placeCore(player, block);
                case VAULT -> placeVault(player, block);
                case CHEST -> placeChest(player, block, items.chestId(event.getItemInHand()));
            };
        }
        if (problem != null) {
            event.setCancelled(true);
            player.sendMessage(problem);
        }
    }

    /** Coloca o Núcleo; devolve o motivo de não poder, ou null se colocou. */
    private String placeCore(Player player, Block block) {
        Clan clan = clans.getClan(player.getUniqueId());
        if (clan == null) {
            return "§cVocê precisa estar em um clan para colocar o Núcleo.";
        }
        if (!clan.leader().equals(player.getUniqueId())) {
            return "§cSó o líder do clan pode colocar o Núcleo.";
        }
        ClanBase base = manager.of(clan);
        if (base.core != null) {
            return "§cSeu clan já tem um Núcleo em " + base.core.coords() + ".";
        }
        Claim claim = claims.getAt(block.getLocation());
        if (claim == null || !clan.isMember(claim.owner())) {
            return "§cO Núcleo só pode ser colocado dentro do claim de um membro do clan.";
        }
        base.core = BaseLoc.of(block);
        base.lastCoreRegen = 0;
        base.shieldUntil = 0;
        boolean onTime = base.coreReplaceDeadline > 0;
        boolean punished = base.coreMissingPenalty;
        base.coreReplaceDeadline = 0;
        base.corePickedBy = null;
        base.coreMissingPenalty = false; // a vulnerabilidade mínima (vulnerableUntil) continua valendo
        manager.save();
        if (onTime) {
            clans.broadcast(clan, "§aNúcleo posicionado.");
        } else if (punished && base.isVulnerable()) {
            clans.broadcast(clan, "§aNúcleo posicionado. §cA base continua vulnerável por mais "
                    + ClanBaseManager.formatDuration(base.vulnerableUntil - System.currentTimeMillis()) + ".");
        } else {
            clans.broadcast(clan, "§6" + player.getName() + " colocou o Núcleo do clan em " + base.core.coords()
                    + ". §7Ele só é desativado por explosões.");
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> updateDisplay(base.core, coreText(base, clan)));
        return null;
    }

    private String placeVault(Player player, Block block) {
        Clan clan = clans.getClan(player.getUniqueId());
        if (clan == null) {
            return "§cVocê precisa estar em um clan para colocar o Cofre.";
        }
        ClanBase base = manager.of(clan);
        if (base.core == null) {
            return "§cO clan precisa de um Núcleo para ter um Cofre.";
        }
        if (base.vault != null) {
            return "§cSeu clan já tem um Cofre em " + base.vault.coords() + ".";
        }
        if (base.core.distance(block.getLocation().add(0.5, 0.5, 0.5)) > settings.vaultDistance()) {
            return "§cO Cofre precisa ficar a até " + (int) settings.vaultDistance() + " blocos do Núcleo ("
                    + base.core.coords() + ").";
        }
        base.vault = BaseLoc.of(block);
        base.vaultHp = settings.vaultHp();
        base.vaultShieldUntil = 0;
        manager.save();
        clans.broadcast(clan, "§e" + player.getName() + " colocou o Cofre do clan. §7Saldo guardado: §a"
                + EconomyManager.format(base.vaultBalance));
        plugin.getServer().getScheduler().runTask(plugin, () -> updateVaultDisplays(base, clan));
        return null;
    }

    private String placeChest(Player player, Block block, String id) {
        Clan clan = clans.getClan(player.getUniqueId());
        if (clan == null) {
            return "§cVocê precisa estar em um clan para colocar o Baú do Clan.";
        }
        Claim claim = claims.getAt(block.getLocation());
        if (claim == null || !clan.isMember(claim.owner())) {
            return "§cO Baú do Clan só pode ser colocado dentro do claim de um membro do clan.";
        }
        if (id != null && chestPlaced(id)) {
            return "§cEste Baú do Clan já está colocado em outro lugar.";
        }
        String chestId = id != null ? id : UUID.randomUUID().toString();
        ClanBase base = manager.of(clan);
        ClanChest chest = new ClanChest(chestId, BaseLoc.of(block), settings.chestHp());
        base.chests.put(chestId, chest);
        manager.save();
        player.sendMessage("§6Baú do Clan colocado. §7Guarda 54 itens; só o clan abre (e inimigos, se o clan estiver"
                + " vulnerável ou enfraquecido).");
        plugin.getServer().getScheduler().runTask(plugin, () -> updateDisplay(chest.loc, chestText(base, chest)));
        return null;
    }

    // ------------------------------------------------------------ proteção

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        Ref ref = find(event.getBlock());
        if (ref == null) {
            return;
        }
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        Long last = lastBreakMessage.get(event.getPlayer().getUniqueId());
        if (last == null || now - last > 2_000L) {
            lastBreakMessage.put(event.getPlayer().getUniqueId(), now);
            event.getPlayer().sendMessage(switch (ref.kind()) {
                case CORE -> "§cO Núcleo do Clan só pode ser destruído por explosões. §7(o líder pode usar /clan nucleo recolher)";
                case VAULT -> "§cO Cofre do Clan só pode ser destruído por explosões.";
                case CHEST -> "§cO Baú do Clan só pode ser destruído por explosões. §7(o líder recolhe com Shift + clique esquerdo)";
                case MACHINE -> "§cEste bloco só pode ser destruído por explosões."; // máquinas têm o próprio listener
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isBaseBlock)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isBaseBlock)) {
            event.setCancelled(true);
        }
    }

    /** Wither, enderman e afins não pegam nem quebram os blocos da base. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (isBaseBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /** Funis não põem nem tiram nada do barril do Baú do Clan (os itens dele ficam no plugin). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHopper(InventoryMoveItemEvent event) {
        Location source = event.getSource().getLocation();
        Location destination = event.getDestination().getLocation();
        if ((source != null && isBaseBlock(source.getBlock())) || (destination != null && isBaseBlock(destination.getBlock()))) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ explosões

    /** A explosão nunca quebra o bloco da base diretamente. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplodeProtect(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isBaseBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockExplodeProtect(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isBaseBlock);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.getEntity() instanceof AbstractWindCharge) {
            return; // carga de vento só empurra
        }
        explosion(event.getLocation(), owners.ownerOf(event.getEntity()));
    }

    /** Cama, âncora de renascimento etc.: tiram ponto, mas não têm quem acendeu. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        explosion(event.getBlock().getLocation().add(0.5, 0.5, 0.5), null);
    }

    /** Cada explosão a até {@code raio} blocos tira pontos do Núcleo, do Cofre e dos Baús por perto. */
    private void explosion(Location location, UUID attacker) {
        double radius = explosionRadius();
        for (ClanBase base : manager.all()) {
            if (base.core != null && base.core.distance(location) <= radius) {
                damageCore(base, attacker, location);
            }
            if (base.vault != null && base.vault.distance(location) <= radius) {
                damageVault(base, attacker, location);
            }
            for (ClanChest chest : List.copyOf(base.chests.values())) {
                if (chest.loc.distance(location) <= radius) {
                    damageChest(base, chest, attacker, location);
                }
            }
        }
        explosionListeners.forEach(listener -> listener.accept(location, attacker));
    }

    private void damageCore(ClanBase base, UUID attacker, Location explosion) {
        Clan clan = clans.getByTag(base.tag);
        if (clan == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (base.isCoreDisabled() || now < base.shieldUntil) {
            return; // desativado ou com escudo: a explosão não tira ponto nem rouba
        }
        int damage = manager.weakened(clan) ? 2 : 1; // enfraquecido: o dobro de dano
        BaseLoc loc = base.core;
        base.coreHp -= damage;
        base.lastCoreAttack = now;
        base.shieldUntil = now + settings.shieldMillis();
        int hp = Math.max(0, base.coreHp);
        // Anti-farm: só os pontos abaixo do menor já atingido roubam.
        int newPoints = Math.max(0, base.lowestHp - hp);
        base.lowestHp = Math.min(base.lowestHp, hp);
        long stolen = 0;
        boolean thief = attacker != null && !clan.isMember(attacker);
        if (newPoints > 0 && thief) {
            for (int i = 0; i < newPoints; i++) {
                stolen += manager.steal(clan, settings.stealPerPoint()); // sai do Cofre, ponto a ponto
            }
            pay(attacker, stolen, "Núcleo de [" + clan.tag() + "] " + hp + "/" + settings.coreHp());
        }
        if (base.coreHp > 0) {
            manager.save();
            updateDisplay(loc, coreText(base, clan));
            warn(clan, "§c⚠ O Núcleo do clan levou dano de explosão! §7(" + base.coreHp + "/" + settings.coreHp() + ")"
                    + (stolen > 0 ? " §c" + EconomyManager.format(stolen) + " foram roubados do Cofre!" : ""));
            Bukkit.getPluginManager().callEvent(new BaseDamageEvent(base.tag, BaseDamageEvent.Kind.CORE, loc.center(),
                    attacker, damage, false, stolen));
            return;
        }
        if (thief) {
            long extra = manager.steal(clan, settings.stealOnDestroy());
            pay(attacker, extra, "Núcleo de [" + clan.tag() + "] destruído");
            stolen += extra;
        }
        disableCore(base, clan, attacker);
        Bukkit.getPluginManager().callEvent(new BaseDamageEvent(base.tag, BaseDamageEvent.Kind.CORE,
                loc.center() != null ? loc.center() : explosion, attacker, damage, true, stolen));
    }

    /** Entrega o dinheiro roubado a quem acendeu a TNT (em dinheiro físico, ou no saldo se estiver offline). */
    private void pay(UUID attacker, long amount, String label) {
        if (amount <= 0) {
            return;
        }
        manager.economy().pagarGanho(attacker, amount);
        Player online = Bukkit.getPlayer(attacker);
        if (online != null) {
            online.sendMessage("§a+" + EconomyManager.format(amount) + " (" + label + ")");
        }
    }

    /**
     * Núcleo em 0 de vida: NÃO sai do mundo. Fica desativado (bloco trocado por {@link #CORE_DISABLED_BLOCK}) por
     * {@code vulneravel-minutos}, com a base vulnerável; o Poder e os avisos acontecem aqui, uma vez só.
     */
    private void disableCore(ClanBase base, Clan clan, UUID attacker) {
        BaseLoc loc = base.core;
        long now = System.currentTimeMillis();
        base.coreHp = 0;
        base.shieldUntil = 0;
        base.coreDisabledUntil = now + settings.vulnerableMillis();
        base.vulnerableUntil = Math.max(base.vulnerableUntil, base.coreDisabledUntil);
        base.power = Math.max(0, base.power - settings.powerCoreLost());
        removeShield(loc);
        setCoreBlock(base);
        Location center = loc.center();
        if (center != null) {
            center.getWorld().playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1.5f, 0.8f);
            center.getWorld().spawnParticle(Particle.EXPLOSION, center, 3, 0.4, 0.4, 0.4, 0);
        }

        Clan attackerClan = attacker == null ? null : clans.getClan(attacker);
        String by;
        if (attackerClan != null) {
            by = "[" + attackerClan.tag() + "]";
        } else if (attacker != null) {
            by = ClanManager.nameOf(attacker);
        } else {
            by = "desconhecidos";
        }
        if (attackerClan != null && attackerClan != clan && settings.powerCoreDestroyed() != 0) {
            manager.addPower(attackerClan, settings.powerCoreDestroyed());
        }
        manager.save();
        Bukkit.broadcast(ShopUi.text("§c§lO Núcleo de [" + clan.tag() + "] foi desativado por " + by + "!"));
        clans.broadcast(clan, "§c§lO Núcleo foi DESATIVADO! Seu clan está VULNERÁVEL por "
                + ClanBaseManager.formatDuration(settings.vulnerableMillis())
                + "! §cInimigos podem quebrar, colocar e abrir baús nos claims dos membros. Depois disso o Núcleo"
                + " volta sozinho com a vida cheia.");
        vulnerable.add(base.tag);
        updateDisplay(loc, coreText(base, clan));
    }

    /** Material que o bloco do Núcleo deve ter agora (desativado ou não). */
    private static Material coreMaterial(ClanBase base) {
        return base.isCoreDisabled() ? CORE_DISABLED_BLOCK : Material.BEACON;
    }

    private static boolean isCoreMaterial(Material material) {
        return material == Material.BEACON || material == CORE_DISABLED_BLOCK;
    }

    /** Troca o bloco do Núcleo para o material do estado atual (só com o chunk carregado; o tick corrige depois). */
    private static void setCoreBlock(ClanBase base) {
        if (base.core == null || !base.core.isLoaded()) {
            return;
        }
        Block block = base.core.block();
        Material material = coreMaterial(base);
        if (block.getType() != material) {
            block.setType(material, false);
        }
    }

    /** "04:59". */
    static String mmss(long millis) {
        long seconds = Math.max(0, (millis + 999) / 1000);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private void damageVault(ClanBase base, UUID attacker, Location explosion) {
        Clan clan = clans.getByTag(base.tag);
        long now = System.currentTimeMillis();
        if (clan == null || now < base.vaultShieldUntil) {
            return;
        }
        BaseLoc loc = base.vault;
        base.vaultHp -= 1;
        base.lastVaultAttack = now;
        base.vaultShieldUntil = now + settings.vaultShieldMillis();
        if (base.vaultHp > 0) {
            manager.save();
            updateVaultDisplays(base, clan);
            warn(clan, "§c⚠ O Cofre do clan levou dano de explosão! §7(" + base.vaultHp + "/" + settings.vaultHp() + ")");
            Bukkit.getPluginManager().callEvent(new BaseDamageEvent(base.tag, BaseDamageEvent.Kind.VAULT, loc.center(),
                    attacker, 1, false, 0));
            return;
        }
        Location center = loc.center() != null ? loc.center() : explosion;
        long loss = (long) Math.floor(base.vaultBalance * settings.vaultLoss(base.isVulnerable()));
        base.vaultBalance -= loss;
        removeBlock(loc, Sound.BLOCK_VAULT_BREAK);
        removeShield(loc);
        removePrivate(loc);
        base.vault = null;
        base.vaultHp = settings.vaultHp();
        base.vaultShieldUntil = 0;
        manager.save();
        Macos macos = manager.economy().macos();
        if (macos != null) {
            macos.drop(center, loss);
        }
        clans.broadcast(clan, "§c§lO Cofre do clan foi destruído! §c" + EconomyManager.format(loss)
                + " caíram no chão em um maço; " + EconomyManager.format(base.vaultBalance)
                + " continuam guardados (voltam com um Cofre novo).");
        Bukkit.getPluginManager().callEvent(new BaseDamageEvent(base.tag, BaseDamageEvent.Kind.VAULT, center,
                attacker, 1, true, loss));
    }

    private void damageChest(ClanBase base, ClanChest chest, UUID attacker, Location explosion) {
        long now = System.currentTimeMillis();
        if (now < chest.shieldUntil) {
            return;
        }
        chest.hp -= 1;
        chest.lastAttack = now;
        chest.shieldUntil = now + settings.chestShieldMillis();
        Location center = chest.loc.center() != null ? chest.loc.center() : explosion;
        if (chest.hp > 0) {
            manager.save();
            updateDisplay(chest.loc, chestText(base, chest));
            Bukkit.getPluginManager().callEvent(new BaseDamageEvent(base.tag, BaseDamageEvent.Kind.CHEST, center,
                    attacker, 1, false, 0));
            return;
        }
        // Destruído: o registro sai antes de os itens caírem (ninguém mais abre este baú).
        base.chests.remove(chest.id);
        manager.save();
        List<ItemStack> contents = chestStore.takeAll(chest.id);
        removeBlock(chest.loc, Sound.BLOCK_BARREL_CLOSE);
        removeShield(chest.loc);
        for (ItemStack item : contents) {
            center.getWorld().dropItemNaturally(center, item);
        }
        Clan clan = clans.getByTag(base.tag);
        if (clan != null) {
            clans.broadcast(clan, "§c§lUm Baú do Clan foi destruído em " + chest.loc.coords() + "! §cOs itens caíram no chão.");
        }
        Bukkit.getPluginManager().callEvent(new BaseDamageEvent(base.tag, BaseDamageEvent.Kind.CHEST, center,
                attacker, 1, true, 0));
    }

    private void warn(Clan clan, String message) {
        long now = System.currentTimeMillis();
        Long last = lastWarn.get(clan.tag());
        if (last == null || now - last >= WARN_COOLDOWN_MILLIS) {
            lastWarn.put(clan.tag(), now);
            clans.broadcast(clan, message);
        }
    }

    private void removeBlock(BaseLoc loc, Sound sound) {
        TextDisplay display = displays.remove(loc);
        if (display != null) {
            display.remove();
        }
        Block block = loc.block();
        if (block != null) {
            block.setType(Material.AIR, false);
            Location center = loc.center();
            block.getWorld().playSound(center, sound, 1.5f, 0.8f);
            block.getWorld().spawnParticle(Particle.EXPLOSION, center, 3, 0.4, 0.4, 0.4, 0);
        }
    }

    // ------------------------------------------------------------ recolher / clan desfeito

    /** /clan nucleo recolher: só o líder, fora de combate e sem ataque recente. Devolve a mensagem. */
    public String pickUpCore(Player player) {
        Clan clan = clans.getClan(player.getUniqueId());
        if (clan == null || !clan.leader().equals(player.getUniqueId())) {
            return "§cSó o líder do clan pode recolher o Núcleo.";
        }
        ClanBase base = manager.of(clan);
        if (base.core == null) {
            return "§cSeu clan não tem um Núcleo colocado.";
        }
        if (base.isCoreDisabled()) {
            return "§cO Núcleo está desativado. Aguarde " + mmss(base.coreDisabledUntil - System.currentTimeMillis()) + ".";
        }
        if (combat.isInCombat(player)) {
            return "§cVocê não pode recolher o Núcleo em combate.";
        }
        long wait = base.lastCoreAttack + settings.pickupWaitMillis() - System.currentTimeMillis();
        if (wait > 0) {
            return "§cO Núcleo foi atacado há pouco. Espere " + ClanBaseManager.formatDuration(wait) + ".";
        }
        removeBlock(base.core, Sound.BLOCK_BEACON_DEACTIVATE);
        removeShield(base.core);
        base.core = null; // a vida e o mínimo do anti-farm continuam: recolher não "reinicia" o Núcleo
        if (base.coreItemId == null) {
            base.coreItemId = UUID.randomUUID().toString(); // Núcleo colocado antes dos ids existirem
        }
        manager.save();
        long deadline = settings.replaceDeadlineMillis();
        base.coreReplaceDeadline = System.currentTimeMillis() + deadline;
        base.corePickedBy = player.getUniqueId();
        manager.save();
        give(player, items.createOwned(BaseItems.Kind.CORE, clan.tag(), base.coreItemId));
        clans.broadcast(clan, "§e" + player.getName() + " recolheu o Núcleo do clan.");
        clans.broadcast(clan, "§eVocê tem " + prazoTexto(deadline)
                + " para posicionar o Núcleo novamente, senão a base ficará vulnerável!");
        return "§aNúcleo recolhido. Coloque-o de novo dentro de um claim do clan.";
    }

    /** /clan cofre recolher: só o líder, fora de combate e sem ataque recente. O dinheiro continua guardado. */
    public String pickUpVault(Player player) {
        Clan clan = clans.getClan(player.getUniqueId());
        if (clan == null || !clan.leader().equals(player.getUniqueId())) {
            return "§cSó o líder do clan pode recolher o Cofre.";
        }
        ClanBase base = manager.of(clan);
        if (base.vault == null) {
            return "§cSeu clan não tem um Cofre colocado.";
        }
        if (combat.isInCombat(player)) {
            return "§cVocê não pode recolher o Cofre em combate.";
        }
        long wait = base.lastVaultAttack + settings.pickupWaitMillis() - System.currentTimeMillis();
        if (wait > 0) {
            return "§cO Cofre foi atacado há pouco. Espere " + ClanBaseManager.formatDuration(wait) + ".";
        }
        removeBlock(base.vault, Sound.BLOCK_VAULT_BREAK);
        removeShield(base.vault);
        removePrivate(base.vault);
        base.vault = null;
        if (base.vaultItemId == null) {
            base.vaultItemId = UUID.randomUUID().toString();
        }
        manager.save();
        give(player, items.createOwned(BaseItems.Kind.VAULT, clan.tag(), base.vaultItemId));
        clans.broadcast(clan, "§e" + player.getName() + " recolheu o Cofre do clan. §7O dinheiro continua guardado.");
        return "§aCofre recolhido. Coloque-o de novo a até " + (int) settings.vaultDistance() + " blocos do Núcleo.";
    }

    /** Shift + clique esquerdo no Baú do Clan (líder): devolve o item com os itens guardados dentro (mesmo id). */
    private void pickUpChest(Player player, ClanBase base, ClanChest chest) {
        Clan clan = clans.getByTag(base.tag);
        if (clan == null || !clan.leader().equals(player.getUniqueId())) {
            player.sendMessage("§cSó o líder do clan pode recolher o Baú do Clan.");
            return;
        }
        if (combat.isInCombat(player)) {
            player.sendMessage("§cVocê não pode recolher o Baú em combate.");
            return;
        }
        long wait = chest.lastAttack + settings.pickupWaitMillis() - System.currentTimeMillis();
        if (wait > 0) {
            player.sendMessage("§cO Baú foi atacado há pouco. Espere " + ClanBaseManager.formatDuration(wait) + ".");
            return;
        }
        chestStore.closeAndSave(chest.id); // fecha para quem estiver mexendo e grava antes de sumir
        base.chests.remove(chest.id);
        manager.save();
        removeBlock(chest.loc, Sound.BLOCK_BARREL_CLOSE);
        removeShield(chest.loc);
        give(player, items.createChest(chest.id));
        player.sendMessage("§aBaú do Clan recolhido com os itens dentro.");
    }

    /** Clan desfeito: tira os blocos do mundo; os itens dos Baús caem no chão; o saldo do Cofre vai para o líder. */
    public void onDisband(Clan clan) {
        ClanBase base = manager.forget(clan);
        if (base == null) {
            return;
        }
        if (base.core != null) {
            removeBlock(base.core, Sound.BLOCK_BEACON_DEACTIVATE);
            removeShield(base.core);
        }
        if (base.vault != null) {
            removeBlock(base.vault, Sound.BLOCK_VAULT_BREAK);
            removeShield(base.vault);
            removePrivate(base.vault);
        }
        for (ClanChest chest : base.chests.values()) {
            List<ItemStack> contents = chestStore.takeAll(chest.id);
            Location center = chest.loc.center();
            removeBlock(chest.loc, Sound.BLOCK_BARREL_CLOSE);
            removeShield(chest.loc);
            if (center != null) {
                contents.forEach(item -> center.getWorld().dropItemNaturally(center, item));
            }
        }
    }

    // ------------------------------------------------------------ clique nos blocos e menu do Cofre

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked == null || event.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }
        Ref ref = find(clicked);
        if (ref == null) {
            return;
        }
        Player player = event.getPlayer();
        ClanBase base = ref.base();
        Clan clan = clans.getByTag(base.tag);
        if (clan == null) {
            return;
        }
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (ref.kind() == BaseDamageEvent.Kind.CHEST && player.isSneaking()) {
                event.setCancelled(true);
                pickUpChest(player, base, ref.chest());
            }
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (player.isSneaking() && !player.getInventory().getItemInMainHand().getType().isAir()
                && ref.kind() == BaseDamageEvent.Kind.CORE) {
            return; // agachado com item no Núcleo: coloca bloco ao lado normalmente
        }
        boolean claimDenied = event.useInteractedBlock() == Event.Result.DENY; // a proteção do claim já avisou
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        switch (ref.kind()) {
            case CORE -> player.sendMessage(base.isCoreDisabled()
                    ? "§6Núcleo de [" + clan.tag() + "] §cDESATIVADO §7- volta em " + mmss(base.coreDisabledUntil
                    - System.currentTimeMillis())
                    : "§6Núcleo de [" + clan.tag() + "] §c" + base.coreHp + "/" + settings.coreHp()
                    + " §7- só é desativado por explosões.");
            case VAULT -> openVault(player, clan, base);
            case CHEST -> openChest(player, clan, ref.chest(), clicked, claimDenied);
        }
    }

    private void openVault(Player player, Clan clan, ClanBase base) {
        if (!clan.isMember(player.getUniqueId())) {
            player.sendMessage("§cEste Cofre pertence ao clan [" + clan.tag() + "].");
            return;
        }
        CofreMenu menu = new CofreMenu(clan.tag(), true);
        menu.render(base, settings, manager.minimum(clan));
        player.openInventory(menu.getInventory());
    }

    private void openChest(Player player, Clan clan, ClanChest chest, Block block, boolean claimDenied) {
        boolean member = clan.isMember(player.getUniqueId());
        if (!member && !manager.openToEnemies(clan)) {
            if (!claimDenied) {
                player.sendMessage("§cEste Baú pertence ao clan [" + clan.tag() + "].");
            }
            return;
        }
        player.openInventory(chestStore.inventory(chest.id, clan.tag()));
        Claim claim = claims.getAt(block.getLocation());
        if (!member && claim != null) {
            Bukkit.getPluginManager().callEvent(new ClaimRaidAccessEvent(player, claim, block,
                    ClaimRaidAccessEvent.Action.OPEN, true));
        }
    }

    /** O listener dos menus do clan já cancelou o clique; aqui só a ação dos botões. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCofreClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CofreMenu menu)
                || !(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        event.setCancelled(true);
        ClickType click = event.getClick();
        if (!click.isLeftClick() && !click.isRightClick()) {
            return;
        }
        // Confere de novo: o jogador ainda é do clan e o Cofre ainda existe.
        Clan clan = clans.getClan(player.getUniqueId());
        ClanBase base = clan == null || !clan.tag().equals(menu.tag()) ? null : manager.of(clan);
        if (base == null || base.vault == null) {
            player.sendMessage("§cEste Cofre não está mais disponível.");
            plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }
        int slot = event.getSlot();
        if (slot == CofreMenu.DEPOSIT_SLOT) {
            deposit(player, clan);
        } else if (slot >= CofreMenu.FIRST_STEP_SLOT && slot < CofreMenu.FIRST_STEP_SLOT + CofreMenu.STEPS.length) {
            long step = CofreMenu.STEPS[slot - CofreMenu.FIRST_STEP_SLOT];
            menu.addAmount(click.isLeftClick() ? step : -step, base.vaultBalance);
        } else if (slot == CofreMenu.RESET_SLOT) {
            menu.resetAmount();
        } else if (slot == CofreMenu.WITHDRAW_SLOT) {
            withdraw(player, clan, menu.amount());
            menu.resetAmount();
        }
        menu.render(base, settings, manager.minimum(clan));
    }

    private void deposit(Player player, Clan clan) {
        Macos macos = manager.economy().macos();
        long total = macos == null ? 0 : macos.takeAll(player); // tira os maços antes de somar no Cofre
        if (total <= 0) {
            player.sendMessage("§cVocê não tem maços de dinheiro no inventário.");
            return;
        }
        manager.depositToVault(clan, total);
        clans.broadcast(clan, "§e" + player.getName() + " depositou §a" + EconomyManager.format(total) + " §eno Cofre.");
    }

    private void withdraw(Player player, Clan clan, long amount) {
        Macos macos = manager.economy().macos();
        if (amount <= 0 || macos == null) {
            player.sendMessage("§cEscolha o valor do saque nos botões +100/+1k/+10k.");
            return;
        }
        if (!manager.withdrawFromVault(clan, amount)) { // tira do Cofre antes de criar o maço
            player.sendMessage("§cO cofre precisa manter no mínimo " + EconomyManager.format(manager.minimum(clan)));
            return;
        }
        macos.give(player, amount);
        clans.broadcast(clan, "§e" + player.getName() + " sacou §a" + EconomyManager.format(amount) + " §edo Cofre.");
    }

    private static void give(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
                .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
    }

    // ------------------------------------------------------------ tick: textos, conferência e regeneração

    private void tick() {
        long now = System.currentTimeMillis();
        for (ClanBase base : manager.all()) {
            Clan clan = clans.getByTag(base.tag);
            if (clan == null) {
                continue;
            }
            coreTimers(base, clan, now); // reativar e prazo de recolocar: correm mesmo com o chunk descarregado
            if (base.core != null && base.core.isLoaded()) {
                if (!isCoreMaterial(base.core.block().getType())) {
                    lostCore(base, clan);
                } else {
                    setCoreBlock(base); // reativou com o chunk descarregado, ou reinício no meio
                    if (base.isCoreDisabled()) {
                        Location center = base.core.center();
                        center.getWorld().spawnParticle(Particle.LARGE_SMOKE, center.clone().add(0, 0.6, 0), 8,
                                0.3, 0.2, 0.3, 0.02);
                    } else if (base.coreHp < settings.coreHp() && now - base.lastCoreAttack >= settings.regenMillis()
                            && now - base.lastCoreRegen >= settings.regenMillis()) {
                        base.coreHp++;
                        base.lastCoreRegen = now;
                        manager.save();
                    }
                    updateDisplay(base.core, coreText(base, clan));
                }
            }
            if (base.vault != null && base.vault.isLoaded()) {
                if (base.vault.block().getType() != Material.VAULT) {
                    lostVault(base, clan);
                } else {
                    updateVaultDisplays(base, clan);
                }
            }
            for (ClanChest chest : List.copyOf(base.chests.values())) {
                if (!chest.loc.isLoaded()) {
                    continue;
                }
                if (chest.loc.block().getType() != Material.BARREL) {
                    lostChest(base, clan, chest);
                } else {
                    updateDisplay(chest.loc, chestText(base, chest));
                }
            }
            if (base.isVulnerable()) {
                vulnerable.add(base.tag);
            } else if (vulnerable.remove(base.tag)) {
                clans.broadcast(clan, "§aSeu clan não está mais vulnerável.");
            }
        }
        cleanup(displays);
        cleanup(privateDisplays);
    }

    /** Reativa o Núcleo desativado na hora certa e cuida do prazo de recolocar (action bar e punição). */
    private void coreTimers(ClanBase base, Clan clan, long now) {
        if (base.isCoreDisabled() && now >= base.coreDisabledUntil) {
            base.coreDisabledUntil = 0;
            base.vulnerableUntil = Math.min(base.vulnerableUntil, now);
            if (base.core != null) {
                int hp = settings.coreReactivateHp();
                base.coreHp = hp;
                base.lowestHp = hp; // Núcleo "novo": o anti-farm recomeça
                base.shieldUntil = 0;
                base.lastCoreRegen = now;
                setCoreBlock(base);
                Location center = base.core.center();
                if (center != null && base.core.isLoaded()) {
                    center.getWorld().playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1f);
                }
            }
            manager.save();
            if (!base.isVulnerable()) {
                vulnerable.remove(base.tag); // o aviso de reativado já diz que a base voltou ao normal
            }
            clans.broadcast(clan, "§aO Núcleo foi reativado!"
                    + (base.isVulnerable() ? "" : " §7A base não está mais vulnerável."));
        }
        if (base.coreReplaceDeadline <= 0) {
            return;
        }
        if (base.core != null) {
            base.coreReplaceDeadline = 0; // já foi colocado
            base.corePickedBy = null;
            manager.save();
            return;
        }
        if (now >= base.coreReplaceDeadline) {
            base.vulnerableUntil = Math.max(base.vulnerableUntil, base.coreReplaceDeadline + settings.vulnerableMillis());
            base.coreReplaceDeadline = 0;
            base.corePickedBy = null;
            base.coreMissingPenalty = true;
            manager.save();
            vulnerable.add(base.tag);
            clans.broadcast(clan, "§cO Núcleo não foi posicionado. Sua base está vulnerável!");
            return;
        }
        Player picker = base.corePickedBy == null ? null : Bukkit.getPlayer(base.corePickedBy);
        if (picker != null) {
            picker.sendActionBar(ShopUi.text("§eRecoloque o Núcleo em §f" + (base.coreReplaceDeadline - now + 999) / 1000
                    + "s §7(senão a base fica vulnerável)"));
        }
    }

    /** "1 minuto", "90 segundos", "2 minutos". */
    private static String prazoTexto(long millis) {
        long seconds = Math.max(0, millis / 1000);
        if (seconds >= 60 && seconds % 60 == 0) {
            long minutes = seconds / 60;
            return minutes + (minutes == 1 ? " minuto" : " minutos");
        }
        return seconds + (seconds == 1 ? " segundo" : " segundos");
    }

    private <T extends Display> void cleanup(Map<BaseLoc, T> map) {
        map.entrySet().removeIf(entry -> {
            boolean stale = !entry.getValue().isValid() || find(entry.getKey()) == null;
            if (stale) {
                entry.getValue().remove();
            }
            return stale;
        });
    }

    /** Os blocos sumiram por outro jeito (comando, edição do mundo): o registro sai sem efeitos de destruição. */
    private void lostCore(ClanBase base, Clan clan) {
        BaseLoc loc = base.core;
        removeDisplay(loc);
        removeShield(loc);
        base.core = null;
        base.coreDisabledUntil = 0; // a vulnerabilidade que já corria (vulnerableUntil) continua
        manager.save();
        lostMessage(clan, "Núcleo", loc);
    }

    private void lostVault(ClanBase base, Clan clan) {
        BaseLoc loc = base.vault;
        removeDisplay(loc);
        removeShield(loc);
        removePrivate(loc);
        base.vault = null;
        base.vaultHp = settings.vaultHp();
        manager.save();
        lostMessage(clan, "Cofre", loc);
    }

    /** Baú sem o bloco: os itens continuam guardados no id e voltam como item para o líder (ou ficam no arquivo). */
    private void lostChest(ClanBase base, Clan clan, ClanChest chest) {
        chestStore.closeAndSave(chest.id);
        base.chests.remove(chest.id);
        removeDisplay(chest.loc);
        removeShield(chest.loc);
        manager.save();
        lostMessage(clan, "Baú do Clan (id " + chest.id + ")", chest.loc);
        Player leader = Bukkit.getPlayer(clan.leader());
        if (leader != null) {
            give(leader, items.createChest(chest.id));
            leader.sendMessage("§eO Baú do Clan sumiu do mundo; você recebeu o item de volta com os itens dentro.");
        }
    }

    private void lostMessage(Clan clan, String what, BaseLoc loc) {
        plugin.getLogger().warning("O " + what + " do clan [" + clan.tag() + "] não existe mais em " + loc.serialize()
                + "; registro removido.");
        clans.broadcast(clan, "§cO " + what + " do clan não está mais no lugar.");
    }

    /** No onEnable: confere se os blocos registrados ainda existem no mundo. */
    private void verifyAll() {
        for (ClanBase base : manager.all()) {
            Clan clan = clans.getByTag(base.tag);
            if (clan == null) {
                continue;
            }
            if (base.core != null && base.core.block() != null && !isCoreMaterial(base.core.block().getType())) {
                lostCore(base, clan);
            }
            if (base.vault != null && base.vault.block() != null && base.vault.block().getType() != Material.VAULT) {
                lostVault(base, clan);
            }
            for (ClanChest chest : List.copyOf(base.chests.values())) {
                if (chest.loc.block() != null && chest.loc.block().getType() != Material.BARREL) {
                    lostChest(base, clan, chest);
                }
            }
        }
    }

    // ------------------------------------------------------------ textos

    /** "Núcleo de [TAG] 7/10", manutenção (ou dívida) e o escudo, em linhas. */
    private String coreText(ClanBase base, Clan clan) {
        if (base.isCoreDisabled()) {
            return "§6Núcleo de [" + base.tag + "]\n§cNÚCLEO DESATIVADO\n§7Volta em §f"
                    + mmss(base.coreDisabledUntil - System.currentTimeMillis());
        }
        String text = "§6Núcleo de [" + base.tag + "] §c" + base.coreHp + "/" + settings.coreHp() + "\n"
                + (base.debt > 0 ? "§c§lEM DÍVIDA: " + EconomyManager.format(base.debt)
                : "§7Manutenção: §e" + EconomyManager.format(manager.upkeepCost(clan)) + "/dia");
        int shield = manager.getEscudoRestante(clan);
        return shield > 0 ? text + "\n§b🛡 Escudo " + shield + "s" : text;
    }

    private String vaultText(ClanBase base) {
        String text = "§eCofre de [" + base.tag + "]\n§c❤ " + base.vaultHp + "/" + settings.vaultHp();
        int shield = seconds(base.vaultShieldUntil);
        return shield > 0 ? text + "\n§b🛡 Escudo " + shield + "s" : text;
    }

    private String chestText(ClanBase base, ClanChest chest) {
        String text = "§6Baú de [" + base.tag + "] §c❤ " + chest.hp + "/" + settings.chestHp();
        int shield = seconds(chest.shieldUntil);
        return shield > 0 ? text + "\n§b🛡 Escudo " + shield + "s" : text;
    }

    private static int seconds(long until) {
        long left = until - System.currentTimeMillis();
        return left <= 0 ? 0 : (int) Math.ceil(left / 1000.0);
    }

    /** Texto público do Cofre (vida e escudo) e, mais alto, o saldo visível só para os membros do clan. */
    private void updateVaultDisplays(ClanBase base, Clan clan) {
        BaseLoc loc = base.vault;
        if (loc == null || !loc.isLoaded()) {
            return;
        }
        updateDisplay(loc, vaultText(base));
        TextDisplay secret = privateDisplays.get(loc);
        if (secret == null || !secret.isValid()) {
            Location at = loc.center().add(0, 1.9, 0);
            secret = at.getWorld().spawn(at, TextDisplay.class, spawned -> {
                spawned.setPersistent(false);
                spawned.setVisibleByDefault(false);
                spawned.setBillboard(Display.Billboard.CENTER);
            });
            privateDisplays.put(loc, secret);
        }
        long minimum = manager.minimum(clan);
        secret.text(ShopUi.text("§aSaldo: " + EconomyManager.format(base.vaultBalance)
                + (base.vaultBalance < minimum ? "\n§cAbaixo do mínimo (" + EconomyManager.format(minimum) + ")" : "")));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (clan.isMember(player.getUniqueId())) {
                player.showEntity(plugin, secret);
            } else {
                player.hideEntity(plugin, secret);
            }
        }
    }

    private void updateDisplay(BaseLoc loc, String text) {
        if (loc == null || !loc.isLoaded()) {
            return;
        }
        TextDisplay display = displays.get(loc);
        if (display == null || !display.isValid()) {
            Location at = loc.center().add(0, 1.0, 0);
            display = at.getWorld().spawn(at, TextDisplay.class, spawned -> {
                spawned.setPersistent(false);
                spawned.setBillboard(Display.Billboard.CENTER);
            });
            displays.put(loc, display);
        }
        display.text(ShopUi.text(text));
    }

    private void removeDisplay(BaseLoc loc) {
        TextDisplay display = displays.remove(loc);
        if (display != null) {
            display.remove();
        }
    }

    private void removePrivate(BaseLoc loc) {
        TextDisplay display = privateDisplays.remove(loc);
        if (display != null) {
            display.remove();
        }
    }

    // ------------------------------------------------------------ escudo (efeito azul)

    private void shieldTick() {
        long now = System.currentTimeMillis();
        for (ClanBase base : manager.all()) {
            shieldEffect(base.core, base.shieldUntil, now);
            shieldEffect(base.vault, base.vaultShieldUntil, now);
            for (ClanChest chest : base.chests.values()) {
                shieldEffect(chest.loc, chest.shieldUntil, now);
            }
        }
        cleanup(shields);
    }

    private void shieldEffect(BaseLoc loc, long until, long now) {
        if (loc == null) {
            return;
        }
        if (until <= now || !loc.isLoaded()) {
            removeShield(loc);
            return;
        }
        Location center = loc.center();
        BlockDisplay glass = shields.get(loc);
        if (glass == null || !glass.isValid()) {
            Location corner = center.clone().subtract(0.5, 0.5, 0.5);
            glass = corner.getWorld().spawn(corner, BlockDisplay.class, display -> {
                display.setPersistent(false);
                display.setBlock(Material.LIGHT_BLUE_STAINED_GLASS.createBlockData());
                // Um pouco maior que o bloco, centrado nele.
                display.setTransformation(new Transformation(new Vector3f(-0.2f, -0.2f, -0.2f),
                        new AxisAngle4f(), new Vector3f(1.4f, 1.4f, 1.4f), new AxisAngle4f()));
                display.setBrightness(new Display.Brightness(15, 15));
            });
            shields.put(loc, glass);
        }
        Particle.DustOptions blue = new Particle.DustOptions(Color.fromRGB(80, 170, 255), 1.2f);
        double turn = (now % 2000) / 2000.0 * Math.PI * 2;
        for (int i = 0; i < 12; i++) {
            double angle = turn + i * Math.PI / 6;
            center.getWorld().spawnParticle(Particle.DUST, center.getX() + Math.cos(angle) * 1.1,
                    center.getY(), center.getZ() + Math.sin(angle) * 1.1, 1, 0, 0, 0, 0, blue);
        }
    }

    private void removeShield(BaseLoc loc) {
        BlockDisplay glass = loc == null ? null : shields.remove(loc);
        if (glass != null) {
            glass.remove();
        }
    }

    /** Linhas extras do /clan info: Poder, Núcleo e (só para membros) Cofre, mínimo e manutenção. */
    public List<String> infoLines(Player viewer, Clan clan) {
        ClanBase base = manager.of(clan);
        String core;
        long now = System.currentTimeMillis();
        if (base.core != null && base.isCoreDisabled()) {
            core = "§cDesativado (" + mmss(base.coreDisabledUntil - now) + ") - VULNERÁVEL";
        } else if (base.core != null) {
            core = "§a" + base.coreHp + "/" + settings.coreHp() + (base.isVulnerable()
                    ? " §c(VULNERÁVEL por " + ClanBaseManager.formatDuration(base.vulnerableUntil - now) + ")" : "");
        } else if (base.coreReplaceDeadline > now) {
            core = "§eRecolhido — recoloque em " + (base.coreReplaceDeadline - now + 999) / 1000 + "s";
        } else if (base.coreMissingPenalty) {
            core = "§cnão posicionado - VULNERÁVEL até ser colocado";
        } else if (base.isVulnerable()) {
            core = "§7nenhum §c(VULNERÁVEL por " + ClanBaseManager.formatDuration(base.vulnerableUntil - now) + ")";
        } else {
            core = "§7nenhum";
        }
        List<String> lines = new ArrayList<>(List.of("§7Poder: §f" + base.power, "§7Núcleo: " + core));
        if (clan.isMember(viewer.getUniqueId())) {
            lines.add("§7Cofre: §a" + EconomyManager.format(base.vaultBalance) + " §8(mínimo "
                    + EconomyManager.format(manager.minimum(clan)) + ")" + (base.vault == null ? " §8(sem Cofre)" : ""));
            lines.add("§7Manutenção: §f" + EconomyManager.format(manager.upkeepCost(clan)) + "/dia"
                    + (base.debt > 0 ? " §c(EM DÍVIDA: " + EconomyManager.format(base.debt) + ")" : ""));
            if (manager.weakened(clan)) {
                lines.add("§c§lENFRAQUECIDO");
            }
        }
        return lines;
    }

    /**
     * Reset de conta (líder sozinho): tira Núcleo, Cofre e Baús do mundo e dos arquivos SEM soltar nada; o dinheiro
     * do Cofre é perdido. Chamar antes de desfazer o clan (o backup dos itens é feito antes).
     */
    public void purge(Clan clan) {
        ClanBase base = manager.of(clan);
        base.vaultBalance = 0; // o dinheiro do Cofre é perdido (não vai para o líder)
        for (ClanChest chest : List.copyOf(base.chests.values())) {
            chestStore.takeAll(chest.id); // fecha para quem estiver mexendo e apaga o arquivo
            removeBlock(chest.loc, Sound.BLOCK_BARREL_CLOSE);
            removeShield(chest.loc);
        }
        base.chests.clear();
        if (base.core != null) {
            removeBlock(base.core, Sound.BLOCK_BEACON_DEACTIVATE);
            removeShield(base.core);
            base.core = null;
        }
        if (base.vault != null) {
            removeBlock(base.vault, Sound.BLOCK_VAULT_BREAK);
            removeShield(base.vault);
            removePrivate(base.vault);
            base.vault = null;
        }
        manager.forget(clan);
    }
}
