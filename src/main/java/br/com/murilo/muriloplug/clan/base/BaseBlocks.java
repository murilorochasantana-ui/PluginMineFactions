package br.com.murilo.muriloplug.clan.base;

import br.com.murilo.muriloplug.claim.Claim;
import br.com.murilo.muriloplug.claim.ClaimManager;
import br.com.murilo.muriloplug.clan.Clan;
import br.com.murilo.muriloplug.clan.ClanManager;
import br.com.murilo.muriloplug.combat.CombatManager;
import br.com.murilo.muriloplug.economy.EconomyManager;
import br.com.murilo.muriloplug.economy.notas.Notas;
import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractWindCharge;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Os blocos da base: Núcleo do Clan (BEACON) e Cofre do Clan (VAULT). Colocar (com as regras de cada um), proteção
 * (golpes, picareta, pistões e mobs não quebram; explosões nunca quebram o bloco direto), dano por explosão, texto em
 * cima de cada bloco, regeneração do Núcleo e o menu do Cofre.
 */
public final class BaseBlocks implements Listener {

    private static final long TICK = 40L;
    private static final long WARN_COOLDOWN_MILLIS = 10_000L;

    private final JavaPlugin plugin;
    private final ClanBaseManager manager;
    private final ClanManager clans;
    private final ClaimManager claims;
    private final CombatManager combat;
    private final BaseItems items;
    private final ExplosionOwners owners;
    private final BaseSettings settings;
    private final Map<BaseLoc, TextDisplay> displays = new HashMap<>();
    private final Map<String, Long> lastWarn = new HashMap<>();
    private final Map<UUID, Long> lastBreakMessage = new HashMap<>();
    /** Clans que estavam vulneráveis no último tick (para avisar quando acaba). */
    private final Set<String> vulnerable = new HashSet<>();

    public BaseBlocks(JavaPlugin plugin, ClanBaseManager manager, ClaimManager claims, CombatManager combat,
                      BaseItems items, ExplosionOwners owners) {
        this.plugin = plugin;
        this.manager = manager;
        this.clans = manager.clans();
        this.claims = claims;
        this.combat = combat;
        this.items = items;
        this.owners = owners;
        this.settings = manager.settings();
    }

    public void start() {
        verifyAll();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK, TICK);
    }

    /** onDisable: tira os textos (não são salvos no mundo, mas some na hora). */
    public void shutdown() {
        displays.values().forEach(TextDisplay::remove);
        displays.clear();
    }

    // ------------------------------------------------------------ consulta

    private record Ref(ClanBase base, BaseDamageEvent.Kind kind) {
    }

    private Ref find(Block block) {
        BaseLoc loc = BaseLoc.of(block);
        for (ClanBase base : manager.all()) {
            if (loc.equals(base.core)) {
                return new Ref(base, BaseDamageEvent.Kind.CORE);
            }
            if (loc.equals(base.vault)) {
                return new Ref(base, BaseDamageEvent.Kind.VAULT);
            }
        }
        return null;
    }

    public boolean isBaseBlock(Block block) {
        return find(block) != null;
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
        String problem = kind == BaseItems.Kind.CORE ? placeCore(player, block) : placeVault(player, block);
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
        long wait = base.replaceAllowedAt - System.currentTimeMillis();
        if (wait > 0) {
            return "§cO Núcleo do clan foi destruído há pouco. Espere " + ClanBaseManager.formatDuration(wait)
                    + " para colocar outro.";
        }
        Claim claim = claims.getAt(block.getLocation());
        if (claim == null || !clan.isMember(claim.owner())) {
            return "§cO Núcleo só pode ser colocado dentro do claim de um membro do clan.";
        }
        base.core = BaseLoc.of(block);
        base.coreHp = settings.coreHp();
        base.lastCoreAttack = 0;
        base.lastCoreRegen = 0;
        manager.save();
        clans.broadcast(clan, "§6" + player.getName() + " colocou o Núcleo do clan em " + base.core.coords()
                + ". §7Ele só é destruído por explosões.");
        plugin.getServer().getScheduler().runTask(plugin, () -> updateDisplay(base.core, coreText(base)));
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
        manager.save();
        clans.broadcast(clan, "§e" + player.getName() + " colocou o Cofre do clan. §7Saldo guardado: §a"
                + EconomyManager.format(base.vaultBalance));
        plugin.getServer().getScheduler().runTask(plugin, () -> updateDisplay(base.vault, vaultText(base)));
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
            event.getPlayer().sendMessage(ref.kind() == BaseDamageEvent.Kind.CORE
                    ? "§cO Núcleo do Clan só pode ser destruído por explosões. §7(o líder pode usar /clan nucleo recolher)"
                    : "§cO Cofre do Clan só pode ser destruído por explosões.");
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        explosion(event.getBlock().getLocation().add(0.5, 0.5, 0.5), null);
    }

    /** Cada explosão a até {@code raio} blocos tira pontos do Núcleo e do Cofre por perto. */
    private void explosion(Location location, UUID attacker) {
        double radius = settings.coreRadius() + 0.5;
        for (ClanBase base : manager.all()) {
            if (base.core != null && base.core.distance(location) <= radius) {
                damageCore(base, attacker, location);
            }
            if (base.vault != null && base.vault.distance(location) <= radius) {
                damageVault(base, attacker, location);
            }
        }
    }

    private void damageCore(ClanBase base, UUID attacker, Location explosion) {
        Clan clan = clans.getByTag(base.tag);
        if (clan == null) {
            return;
        }
        int damage = manager.inDebt(clan) ? 2 : 1; // em dívida: o dobro de dano
        BaseLoc loc = base.core;
        base.coreHp -= damage;
        base.lastCoreAttack = System.currentTimeMillis();
        if (base.coreHp > 0) {
            manager.save();
            updateDisplay(loc, coreText(base));
            warn(clan, "§c⚠ O Núcleo do clan levou dano de explosão! §7(" + base.coreHp + "/" + settings.coreHp() + ")");
            Bukkit.getPluginManager().callEvent(new BaseDamageEvent(base.tag, BaseDamageEvent.Kind.CORE, loc.center(),
                    attacker, damage, false, 0));
            return;
        }
        destroyCore(base, clan, attacker);
        Bukkit.getPluginManager().callEvent(new BaseDamageEvent(base.tag, BaseDamageEvent.Kind.CORE,
                loc.center() != null ? loc.center() : explosion, attacker, damage, true, 0));
    }

    private void destroyCore(ClanBase base, Clan clan, UUID attacker) {
        BaseLoc loc = base.core;
        removeBlock(loc, Sound.BLOCK_BEACON_DEACTIVATE);
        long now = System.currentTimeMillis();
        base.core = null;
        base.coreHp = settings.coreHp();
        base.vulnerableUntil = now + settings.vulnerableMillis();
        base.replaceAllowedAt = now + settings.replaceWaitMillis();
        base.power = Math.max(0, base.power - settings.powerCoreLost());

        Clan attackerClan = attacker == null ? null : clans.getClan(attacker);
        boolean enemy = attacker != null && !clan.isMember(attacker);
        String by;
        if (attackerClan != null) {
            by = "[" + attackerClan.tag() + "]";
        } else if (attacker != null) {
            by = ClanManager.nameOf(attacker);
        } else {
            by = "desconhecidos";
        }
        if (enemy) {
            long reward = settings.coreReward();
            if (reward > 0) {
                boolean notes = manager.economy().pagarGanho(attacker, reward);
                Player online = Bukkit.getPlayer(attacker);
                if (online != null) {
                    online.sendMessage("§aVocê destruiu o Núcleo de [" + clan.tag() + "] e ganhou "
                            + EconomyManager.format(reward) + (notes ? " em notas!" : "!"));
                }
            }
            if (attackerClan != null && settings.powerCoreDestroyed() != 0) {
                manager.addPower(attackerClan, settings.powerCoreDestroyed());
            }
        }
        manager.save();
        Bukkit.broadcast(ShopUi.text("§c§lO Núcleo de [" + clan.tag() + "] foi destruído por " + by + "!"));
        clans.broadcast(clan, "§c§lSeu clan está VULNERÁVEL por "
                + ClanBaseManager.formatDuration(settings.vulnerableMillis())
                + "! §cInimigos podem quebrar, colocar e abrir baús nos claims dos membros.");
        vulnerable.add(base.tag);
    }

    private void damageVault(ClanBase base, UUID attacker, Location explosion) {
        Clan clan = clans.getByTag(base.tag);
        if (clan == null) {
            return;
        }
        BaseLoc loc = base.vault;
        base.vaultHp -= 1;
        if (base.vaultHp > 0) {
            manager.save();
            updateDisplay(loc, vaultText(base));
            warn(clan, "§c⚠ O Cofre do clan levou dano de explosão! §7(" + base.vaultHp + "/" + settings.vaultHp() + ")");
            Bukkit.getPluginManager().callEvent(new BaseDamageEvent(base.tag, BaseDamageEvent.Kind.VAULT, loc.center(),
                    attacker, 1, false, 0));
            return;
        }
        Location center = loc.center() != null ? loc.center() : explosion;
        long loss = (long) Math.floor(base.vaultBalance * settings.vaultLoss(base.isVulnerable()));
        base.vaultBalance -= loss;
        removeBlock(loc, Sound.BLOCK_VAULT_BREAK);
        base.vault = null;
        base.vaultHp = settings.vaultHp();
        manager.save();
        Notas notas = manager.economy().notas();
        if (notas != null) {
            notas.drop(center, loss);
        }
        clans.broadcast(clan, "§c§lO Cofre do clan foi destruído! §c" + EconomyManager.format(loss)
                + " caíram no chão em notas; " + EconomyManager.format(base.vaultBalance)
                + " continuam guardados (voltam com um Cofre novo).");
        Bukkit.getPluginManager().callEvent(new BaseDamageEvent(base.tag, BaseDamageEvent.Kind.VAULT, center,
                attacker, 1, true, loss));
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
        if (combat.isInCombat(player)) {
            return "§cVocê não pode recolher o Núcleo em combate.";
        }
        long wait = base.lastCoreAttack + settings.pickupWaitMillis() - System.currentTimeMillis();
        if (wait > 0) {
            return "§cO Núcleo foi atacado há pouco. Espere " + ClanBaseManager.formatDuration(wait) + ".";
        }
        removeBlock(base.core, Sound.BLOCK_BEACON_DEACTIVATE);
        base.core = null;
        base.coreHp = settings.coreHp();
        manager.save();
        ItemStack item = items.create(BaseItems.Kind.CORE);
        player.getInventory().addItem(item).values()
                .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
        clans.broadcast(clan, "§e" + player.getName() + " recolheu o Núcleo do clan.");
        return "§aNúcleo recolhido. Coloque-o de novo dentro de um claim do clan.";
    }

    /** Clan desfeito: tira o Núcleo e o Cofre do mundo (o saldo do Cofre vai para o líder). */
    public void onDisband(Clan clan) {
        ClanBase base = manager.forget(clan);
        if (base == null) {
            return;
        }
        if (base.core != null) {
            removeBlock(base.core, Sound.BLOCK_BEACON_DEACTIVATE);
        }
        if (base.vault != null) {
            removeBlock(base.vault, Sound.BLOCK_VAULT_BREAK);
        }
    }

    // ------------------------------------------------------------ clique nos blocos e menu do Cofre

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Ref ref = find(event.getClickedBlock());
        if (ref == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isSneaking() && !event.getPlayer().getInventory().getItemInMainHand().getType().isAir()) {
            return; // agachado com item: coloca bloco ao lado normalmente
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ClanBase base = ref.base();
        Clan clan = clans.getByTag(base.tag);
        if (clan == null) {
            return;
        }
        if (ref.kind() == BaseDamageEvent.Kind.CORE) {
            player.sendMessage("§6Núcleo de [" + clan.tag() + "] §c" + base.coreHp + "/" + settings.coreHp()
                    + " §7- só é destruído por explosões.");
            return;
        }
        if (!clan.isMember(player.getUniqueId())) {
            player.sendMessage("§cEste Cofre pertence ao clan [" + clan.tag() + "].");
            return;
        }
        CofreMenu menu = new CofreMenu(clan.tag(), clan.leader().equals(player.getUniqueId()));
        menu.render(base, settings);
        player.openInventory(menu.getInventory());
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
        boolean leader = clan.leader().equals(player.getUniqueId());
        int slot = event.getSlot();
        if (slot == CofreMenu.DEPOSIT_SLOT) {
            deposit(player, clan);
        } else if (leader && menu.leader() && slot >= CofreMenu.FIRST_STEP_SLOT
                && slot < CofreMenu.FIRST_STEP_SLOT + CofreMenu.STEPS.length) {
            long step = CofreMenu.STEPS[slot - CofreMenu.FIRST_STEP_SLOT];
            menu.addAmount(click.isLeftClick() ? step : -step, base.vaultBalance);
        } else if (leader && menu.leader() && slot == CofreMenu.RESET_SLOT) {
            menu.resetAmount();
        } else if (leader && menu.leader() && slot == CofreMenu.WITHDRAW_SLOT) {
            withdraw(player, clan, menu.amount());
            menu.resetAmount();
        }
        menu.render(base, settings);
    }

    private void deposit(Player player, Clan clan) {
        Notas notas = manager.economy().notas();
        long total = notas == null ? 0 : notas.takeAll(player); // tira as notas antes de somar no Cofre
        if (total <= 0) {
            player.sendMessage("§cVocê não tem notas de dinheiro no inventário.");
            return;
        }
        manager.depositToVault(clan, total);
        clans.broadcast(clan, "§e" + player.getName() + " depositou §a" + EconomyManager.format(total) + " §eno Cofre.");
    }

    private void withdraw(Player player, Clan clan, long amount) {
        Notas notas = manager.economy().notas();
        if (amount <= 0 || notas == null) {
            player.sendMessage("§cEscolha o valor do saque nos botões +100/+1k/+10k.");
            return;
        }
        long smallest = notas.values().getLast();
        if (amount % smallest != 0) {
            player.sendMessage("§cO valor precisa ser múltiplo de " + EconomyManager.format(smallest) + ".");
            return;
        }
        if (!manager.withdrawFromVault(clan, amount)) { // tira do Cofre antes de criar as notas
            player.sendMessage("§cO Cofre não tem esse saldo.");
            return;
        }
        notas.give(player, amount);
        clans.broadcast(clan, "§e" + player.getName() + " sacou §a" + EconomyManager.format(amount) + " §edo Cofre.");
    }

    // ------------------------------------------------------------ tick: textos, conferência e regeneração

    private void tick() {
        long now = System.currentTimeMillis();
        for (ClanBase base : manager.all()) {
            Clan clan = clans.getByTag(base.tag);
            if (clan == null) {
                continue;
            }
            if (base.core != null && base.core.isLoaded()) {
                if (base.core.block().getType() != Material.BEACON) {
                    lost(base, clan, true);
                } else {
                    if (base.coreHp < settings.coreHp() && now - base.lastCoreAttack >= settings.regenMillis()
                            && now - base.lastCoreRegen >= settings.regenMillis()) {
                        base.coreHp++;
                        base.lastCoreRegen = now;
                        manager.save();
                    }
                    updateDisplay(base.core, coreText(base));
                }
            }
            if (base.vault != null && base.vault.isLoaded()) {
                if (base.vault.block().getType() != Material.VAULT) {
                    lost(base, clan, false);
                } else {
                    updateDisplay(base.vault, vaultText(base));
                }
            }
            if (base.isVulnerable()) {
                vulnerable.add(base.tag);
            } else if (vulnerable.remove(base.tag)) {
                clans.broadcast(clan, "§aSeu clan não está mais vulnerável.");
            }
        }
        displays.entrySet().removeIf(entry -> {
            boolean stale = !entry.getValue().isValid() || find(entry.getKey()) == null;
            if (stale) {
                entry.getValue().remove();
            }
            return stale;
        });
    }

    private Ref find(BaseLoc loc) {
        for (ClanBase base : manager.all()) {
            if (loc.equals(base.core) || loc.equals(base.vault)) {
                return new Ref(base, loc.equals(base.core) ? BaseDamageEvent.Kind.CORE : BaseDamageEvent.Kind.VAULT);
            }
        }
        return null;
    }

    /** O bloco sumiu por outro jeito (comando, edição do mundo): o registro sai sem efeitos de destruição. */
    private void lost(ClanBase base, Clan clan, boolean core) {
        BaseLoc loc = core ? base.core : base.vault;
        TextDisplay display = displays.remove(loc);
        if (display != null) {
            display.remove();
        }
        if (core) {
            base.core = null;
            base.coreHp = settings.coreHp();
        } else {
            base.vault = null;
            base.vaultHp = settings.vaultHp();
        }
        manager.save();
        plugin.getLogger().warning("O " + (core ? "Núcleo" : "Cofre") + " do clan [" + clan.tag() + "] não existe mais em "
                + loc.serialize() + "; registro removido.");
        clans.broadcast(clan, "§cO " + (core ? "Núcleo" : "Cofre") + " do clan não está mais no lugar.");
    }

    /** No onEnable: confere se os blocos registrados ainda existem no mundo. */
    private void verifyAll() {
        for (ClanBase base : manager.all()) {
            Clan clan = clans.getByTag(base.tag);
            if (clan == null) {
                continue;
            }
            if (base.core != null && base.core.block() != null && base.core.block().getType() != Material.BEACON) {
                lost(base, clan, true);
            }
            if (base.vault != null && base.vault.block() != null && base.vault.block().getType() != Material.VAULT) {
                lost(base, clan, false);
            }
        }
    }

    private String coreText(ClanBase base) {
        return "§6Núcleo de [" + base.tag + "] §c" + base.coreHp + "/" + settings.coreHp();
    }

    private String vaultText(ClanBase base) {
        return "§eCofre de [" + base.tag + "] §c" + base.vaultHp + "/" + settings.vaultHp();
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

    /** Linhas extras do /clan info: Poder, Núcleo e (só para membros) Cofre e dívida. */
    public List<String> infoLines(Player viewer, Clan clan) {
        ClanBase base = manager.of(clan);
        String core;
        if (base.core != null) {
            core = "§a" + base.coreHp + "/" + settings.coreHp();
        } else if (base.isVulnerable()) {
            core = "§cdestruído - VULNERÁVEL (" + ClanBaseManager.formatDuration(base.vulnerableUntil
                    - System.currentTimeMillis()) + ")";
        } else {
            core = "§7nenhum";
        }
        List<String> lines = new java.util.ArrayList<>(List.of("§7Poder: §f" + base.power, "§7Núcleo: " + core));
        if (clan.isMember(viewer.getUniqueId())) {
            lines.add("§7Cofre: §a" + EconomyManager.format(base.vaultBalance) + (base.vault == null ? " §8(sem Cofre)" : ""));
            lines.add("§7Manutenção: §f" + EconomyManager.format(manager.upkeepCost(clan)) + "/dia"
                    + (base.debt > 0 ? " §c(EM DÍVIDA: " + EconomyManager.format(base.debt) + ")" : ""));
        }
        return lines;
    }
}
