package br.com.murilo.muriloplug.eventos;

import br.com.murilo.muriloplug.claim.Claim;
import br.com.murilo.muriloplug.claim.ClaimManager;
import br.com.murilo.muriloplug.clan.base.BaseLoc;
import br.com.murilo.muriloplug.economy.notas.Notas;
import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Airdrop: de tempos em tempos (com jogadores suficientes online) um baú cai do céu num lugar aleatório do Overworld,
 * longe de claims e fora da água. Aviso com as coordenadas aos 60, 30 e 10 segundos; depois o baú desce (animação),
 * fica com um feixe de partículas e some depois de {@code duracao} minutos, mesmo com itens. Não pode ser quebrado,
 * esvaziado por funil nem levado; só aberto. Os itens vêm da tabela editável ({@link AirdropTable}).
 */
public final class AirdropManager implements Listener, CommandExecutor, TabCompleter {

    public static final String ADMIN_PERMISSION = "mineplug.airdrop.admin";
    public static final String MIN_INTERVAL = "eventos.airdrop.intervalo-min";
    public static final String MAX_INTERVAL = "eventos.airdrop.intervalo-max";
    public static final String MIN_PLAYERS = "eventos.airdrop.minimo-jogadores";
    public static final String DURATION = "eventos.airdrop.duracao";
    public static final String CLAIM_DISTANCE = "eventos.airdrop.distancia-claims";
    public static final String MAX_RADIUS = "eventos.airdrop.raio-maximo";
    public static final String MIN_ITEMS = "eventos.airdrop.itens-min";
    public static final String MAX_ITEMS = "eventos.airdrop.itens-max";
    public static final String COUNTDOWN = "eventos.airdrop.contagem-segundos";

    private static final int SEARCH_ATTEMPTS = 40;
    private static final int FALL_HEIGHT = 40;
    private static final int FALL_TICKS = 40;
    private static final long RETRY_MILLIS = 10 * 60_000L;

    private enum Phase { IDLE, SEARCHING, COUNTDOWN, FALLING, LANDED }

    private final JavaPlugin plugin;
    private final ClaimManager claims;
    private final AirdropTable table;
    private final ContractItems contracts;
    private final Notas notas;
    private final NamespacedKey chestKey;
    private final Set<UUID> fireworks = new HashSet<>();
    private Phase phase = Phase.IDLE;
    private long nextAt;
    private Location target;
    private int countdown;
    private BaseLoc chest;
    private long landedAt;
    private boolean warnedMinute;
    private boolean opened;
    private BlockDisplay falling;
    private BukkitTask fallTask;

    public AirdropManager(JavaPlugin plugin, ClaimManager claims, AirdropTable table, ContractItems contracts,
                          Notas notas) {
        this.plugin = plugin;
        this.claims = claims;
        this.table = table;
        this.contracts = contracts;
        this.notas = notas;
        this.chestKey = new NamespacedKey(plugin, "airdrop");
    }

    public static boolean addMissing(FileConfiguration config) {
        boolean changed = false;
        Object[][] defaults = {{MIN_INTERVAL, 90}, {MAX_INTERVAL, 150}, {MIN_PLAYERS, 4}, {DURATION, 10},
                {CLAIM_DISTANCE, 50}, {MAX_RADIUS, 3000}, {MIN_ITEMS, 4}, {MAX_ITEMS, 8}, {COUNTDOWN, 60}};
        for (Object[] entry : defaults) {
            if (!config.contains((String) entry[0])) {
                config.set((String) entry[0], entry[1]);
                changed = true;
            }
        }
        return changed;
    }

    private int config(String path, int fallback) {
        return plugin.getConfig().getInt(path, fallback);
    }

    public void start() {
        removeLeftover();
        scheduleNext();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickSecond, 20L, 20L);
    }

    private void scheduleNext() {
        int min = Math.max(1, config(MIN_INTERVAL, 90));
        int max = Math.max(min, config(MAX_INTERVAL, 150));
        nextAt = System.currentTimeMillis() + (min + ThreadLocalRandom.current().nextInt(max - min + 1)) * 60_000L;
    }

    // ------------------------------------------------------------ ciclo

    private void tickSecond() {
        long now = System.currentTimeMillis();
        switch (phase) {
            case IDLE -> {
                if (now >= nextAt) {
                    if (Bukkit.getOnlinePlayers().size() >= config(MIN_PLAYERS, 4)) {
                        begin(null);
                    } else {
                        nextAt = now + RETRY_MILLIS;
                    }
                }
            }
            case COUNTDOWN -> {
                countdown--;
                if (countdown == 30 || countdown == 10) {
                    announceCountdown();
                }
                if (countdown <= 0) {
                    fall();
                }
            }
            case LANDED -> {
                beam();
                long remaining = landedAt + Math.max(1, config(DURATION, 10)) * 60_000L - now;
                if (remaining <= 60_000L && !warnedMinute) {
                    warnedMinute = true;
                    Bukkit.broadcast(ShopUi.text("§6§lAIRDROP! §eO suprimento em " + coords() + " some em 1 minuto!"));
                }
                if (remaining <= 0) {
                    removeChest(true);
                    phase = Phase.IDLE;
                    scheduleNext();
                }
            }
            default -> { }
        }
    }

    /** Começa um airdrop (automático ou /airdrop iniciar). Retorna false se já houver um em andamento. */
    public boolean begin(CommandSender requester) {
        if (phase != Phase.IDLE) {
            return false;
        }
        World world = Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == World.Environment.NORMAL)
                .findFirst().orElse(null);
        if (world == null) {
            return false;
        }
        phase = Phase.SEARCHING;
        search(world, 0, requester);
        return true;
    }

    /** Procura um lugar válido, carregando um chunk por tentativa sem travar o servidor. */
    private void search(World world, int attempt, CommandSender requester) {
        if (attempt >= SEARCH_ATTEMPTS) {
            phase = Phase.IDLE;
            nextAt = System.currentTimeMillis() + RETRY_MILLIS;
            if (requester != null) {
                requester.sendMessage("§cNão achei um lugar válido para o Airdrop (longe de claims e fora da água). "
                        + "Tente de novo.");
            }
            return;
        }
        WorldBorder border = world.getWorldBorder();
        double radius = Math.max(16, Math.min(border.getSize() / 2 - 16, config(MAX_RADIUS, 3000)));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int x = (int) Math.floor(border.getCenter().getX() + random.nextDouble(-radius, radius));
        int z = (int) Math.floor(border.getCenter().getZ() + random.nextDouble(-radius, radius));
        if (nearClaim(world, x, z)) {
            search(world, attempt + 1, requester);
            return;
        }
        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Block top = world.getBlockAt(x, y, z);
            Location spot = new Location(world, x + 0.5, y + 1, z + 0.5);
            if (top.isLiquid() || top.getType() == Material.ICE || !border.isInside(spot)
                    || y + 1 >= world.getMaxHeight() - FALL_HEIGHT) {
                search(world, attempt + 1, requester);
                return;
            }
            target = spot;
            countdown = Math.max(10, config(COUNTDOWN, 60));
            phase = Phase.COUNTDOWN;
            announceCountdown();
        }).exceptionally(error -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> search(world, attempt + 1, requester));
            return null;
        });
    }

    private boolean nearClaim(World world, int x, int z) {
        int distance = Math.max(0, config(CLAIM_DISTANCE, 50));
        for (UUID owner : claims.owners()) {
            for (Claim claim : claims.getByOwner(owner)) {
                if (!claim.world().equals(world.getName())) {
                    continue;
                }
                int dx = Math.max(0, Math.max(claim.minX() - x, x - claim.maxX()));
                int dz = Math.max(0, Math.max(claim.minZ() - z, z - claim.maxZ()));
                if ((long) dx * dx + (long) dz * dz < (long) distance * distance) {
                    return true;
                }
            }
        }
        return false;
    }

    private String coords() {
        Location at = chest != null ? chest.center() : target;
        return at == null ? "?" : at.getBlockX() + ", " + at.getBlockZ();
    }

    private void announceCountdown() {
        Bukkit.broadcast(ShopUi.text("§6§lAIRDROP! §eUm suprimento vai cair em " + coords() + " em " + countdown
                + " segundos!"));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f);
        }
    }

    // ------------------------------------------------------------ queda e baú

    private void fall() {
        phase = Phase.FALLING;
        World world = target.getWorld();
        Location top = new Location(world, target.getBlockX(), target.getBlockY() + FALL_HEIGHT, target.getBlockZ());
        falling = world.spawn(top, BlockDisplay.class, display -> {
            display.setBlock(Material.CHEST.createBlockData());
            display.setPersistent(false);
            display.setTeleportDuration(FALL_TICKS);
        });
        int[] tick = {0};
        fallTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            tick[0]++;
            if (tick[0] == 2 && falling != null && falling.isValid()) {
                falling.teleport(new Location(world, target.getBlockX(), target.getBlockY(), target.getBlockZ()));
            }
            double y = target.getY() + FALL_HEIGHT * Math.max(0, 1 - (tick[0] - 2) / (double) FALL_TICKS);
            world.spawnParticle(Particle.CLOUD, target.getX(), y + 0.5, target.getZ(), 6, 0.3, 0.3, 0.3, 0.02, null, true);
            world.spawnParticle(Particle.FLAME, target.getX(), y + 0.5, target.getZ(), 3, 0.2, 0.2, 0.2, 0.01, null, true);
            if (tick[0] >= FALL_TICKS + 3) {
                fallTask.cancel();
                fallTask = null;
                land();
            }
        }, 1L, 1L);
    }

    private void land() {
        if (falling != null) {
            falling.remove();
            falling = null;
        }
        World world = target.getWorld();
        Block block = world.getBlockAt(target.getBlockX(), target.getBlockY(), target.getBlockZ());
        while (!block.getType().isAir() && block.getY() < world.getMaxHeight() - 1) {
            block = block.getRelative(BlockFace.UP); // alguém colocou algo no lugar: cai em cima
        }
        block.setType(Material.CHEST, false);
        if (block.getState(false) instanceof Chest state) {
            state.getPersistentDataContainer().set(chestKey, PersistentDataType.BYTE, (byte) 1);
            state.update(false, false);
        }
        if (block.getState(false) instanceof Chest state) {
            fill(state.getBlockInventory());
        }
        chest = BaseLoc.of(block);
        table.setActiveChest(chest.serialize());
        landedAt = System.currentTimeMillis();
        warnedMinute = false;
        opened = false;
        phase = Phase.LANDED;
        Location center = chest.center();
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2f, 1.2f);
        world.spawnParticle(Particle.EXPLOSION, center, 2, 0.3, 0.3, 0.3, 0, null, true);
        launchFirework(center.clone().add(0, 1, 0));
        Bukkit.broadcast(ShopUi.text("§6§lAIRDROP! §eO suprimento caiu em " + coords() + "! Corra para pegar."));
    }

    private void fill(Inventory inventory) {
        List<ItemStack> items = table.draw(Math.max(1, config(MIN_ITEMS, 4)), Math.max(1, config(MAX_ITEMS, 8)));
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            slots.add(slot);
        }
        Collections.shuffle(slots);
        for (int i = 0; i < items.size() && i < slots.size(); i++) {
            inventory.setItem(slots.get(i), items.get(i));
        }
    }

    private void launchFirework(Location at) {
        Firework firework = at.getWorld().spawn(at, Firework.class, spawned -> {
            FireworkMeta meta = spawned.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.ORANGE, Color.YELLOW).withFade(Color.RED).withFlicker().build());
            meta.setPower(1);
            spawned.setFireworkMeta(meta);
        });
        fireworks.add(firework.getUniqueId());
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (firework.isValid()) {
                firework.detonate();
            }
            fireworks.remove(firework.getUniqueId());
        }, 20L);
    }

    /** Feixe de partículas visível de longe acima do baú. */
    private void beam() {
        Location center = chest == null ? null : chest.center();
        if (center == null || !chest.isLoaded()) {
            return;
        }
        World world = center.getWorld();
        for (int dy = 1; dy <= 60; dy += 2) {
            world.spawnParticle(Particle.END_ROD, center.getX(), center.getY() + dy, center.getZ(), 1, 0.05, 0.4, 0.05, 0,
                    null, true);
        }
    }

    private void removeChest(boolean announce) {
        if (chest == null) {
            return;
        }
        Block block = chest.block();
        if (block != null && block.getState(false) instanceof Chest state) {
            state.getBlockInventory().clear(); // os itens somem junto com o baú
            block.setType(Material.AIR, false);
        }
        if (announce) {
            Bukkit.broadcast(ShopUi.text("§7O Airdrop em " + coords() + " sumiu."));
        }
        chest = null;
        table.setActiveChest(null);
    }

    /** Baú de um airdrop que ficou no mundo (servidor caiu com ele lá). */
    private void removeLeftover() {
        BaseLoc leftover = BaseLoc.parse(table.activeChest());
        if (leftover == null) {
            return;
        }
        Block block = leftover.block();
        if (block != null && block.getState(false) instanceof Chest state
                && state.getPersistentDataContainer().has(chestKey, PersistentDataType.BYTE)) {
            state.getBlockInventory().clear();
            block.setType(Material.AIR, false);
        }
        table.setActiveChest(null);
    }

    /** onDisable: tira o baú e a animação do mundo. */
    public void shutdown() {
        if (fallTask != null) {
            fallTask.cancel();
        }
        if (falling != null) {
            falling.remove();
        }
        removeChest(false);
    }

    // ------------------------------------------------------------ proteção do baú

    private boolean isAirdrop(Block block) {
        return chest != null && block != null && chest.equals(BaseLoc.of(block));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        if (isAirdrop(event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cO baú do Airdrop não pode ser quebrado, só aberto.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isAirdrop);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isAirdrop);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isAirdrop)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isAirdrop)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (isAirdrop(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /** Funil não esvazia o baú. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHopper(InventoryMoveItemEvent event) {
        Location source = event.getSource().getLocation();
        if (chest != null && source != null && isAirdrop(source.getBlock())) {
            event.setCancelled(true);
        }
    }

    /** Outro baú do lado viraria um baú duplo com o do Airdrop. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlaceNear(BlockPlaceEvent event) {
        Material type = event.getBlockPlaced().getType();
        if (chest == null || (type != Material.CHEST && type != Material.TRAPPED_CHEST)) {
            return;
        }
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            if (isAirdrop(event.getBlockPlaced().getRelative(face))) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cNão dá para colocar um baú colado no Airdrop.");
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!opened && event.getInventory().getHolder() instanceof Chest holder && isAirdrop(holder.getBlock())) {
            opened = true;
            Bukkit.broadcast(ShopUi.text("§e" + event.getPlayer().getName() + " abriu o Airdrop!"));
        }
    }

    /** Os fogos do Airdrop não machucam ninguém. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFireworkDamage(EntityDamageByEntityEvent event) {
        if (fireworks.contains(event.getDamager().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ editor da tabela

    @EventHandler
    public void onEditorDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof AirdropEditorMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEditorClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof AirdropEditorMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() == null) {
            return;
        }
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }
        ClickType click = event.getClick();
        if (!click.isLeftClick() && !click.isRightClick()) {
            return;
        }
        if (event.getClickedInventory() != top) {
            ItemStack picked = event.getCurrentItem();
            if (picked != null && !picked.getType().isAir()) {
                int index = table.add(picked, 10);
                if (index < 0) {
                    player.sendMessage("§cA tabela está cheia (" + AirdropTable.MAX_ENTRIES + " itens).");
                } else {
                    menu.select(index);
                }
            }
            menu.render(notas.values());
            return;
        }
        int slot = event.getSlot();
        if (slot < AirdropEditorMenu.PER_PAGE) {
            int index = menu.indexAt(slot);
            if (index >= 0) {
                menu.select(index);
            }
        } else if (slot == AirdropEditorMenu.PREVIOUS_SLOT) {
            menu.changePage(-1);
        } else if (slot == AirdropEditorMenu.NEXT_SLOT) {
            menu.changePage(1);
        } else if (slot == AirdropEditorMenu.MINUS_TEN_SLOT) {
            table.addChance(menu.selected(), -10);
        } else if (slot == AirdropEditorMenu.MINUS_ONE_SLOT) {
            table.addChance(menu.selected(), -1);
        } else if (slot == AirdropEditorMenu.PLUS_ONE_SLOT) {
            table.addChance(menu.selected(), 1);
        } else if (slot == AirdropEditorMenu.PLUS_TEN_SLOT) {
            table.addChance(menu.selected(), 10);
        } else if (slot == AirdropEditorMenu.REMOVE_SLOT && menu.selected() >= 0) {
            table.remove(menu.selected());
            menu.select(-1);
        } else if (slot == AirdropEditorMenu.NOTE_SLOT) {
            List<Long> values = notas.values();
            if (click.isRightClick()) {
                menu.nextNote(values.size());
            } else {
                // values vem do maior para o menor; o índice do menu conta do menor para o maior.
                long value = values.get(values.size() - 1 - Math.min(menu.noteIndex(), values.size() - 1));
                int index = table.add(notas.create(value, click.isShiftClick() ? 10 : 1), 50);
                if (index >= 0) {
                    menu.select(index);
                }
            }
        }
        menu.render(notas.values());
    }

    // ------------------------------------------------------------ comando

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(switch (phase) {
                case IDLE, SEARCHING -> "§eNenhum Airdrop agora. Eles caem de tempos em tempos (fique de olho no chat).";
                case COUNTDOWN -> "§6Um Airdrop vai cair em " + coords() + " em " + countdown + " segundos!";
                case FALLING -> "§6O Airdrop está caindo em " + coords() + "!";
                case LANDED -> "§6Há um Airdrop em " + coords() + (opened ? " §7(já foi aberto)" : "") + ".";
            });
            return true;
        }
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cVocê não tem permissão.");
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("iniciar")) {
            sender.sendMessage(begin(sender) ? "§aProcurando um lugar para o Airdrop..."
                    : "§cJá há um Airdrop em andamento.");
            return true;
        }
        if (action.equals("admin") && args.length == 2 && args[1].equalsIgnoreCase("editar")
                && sender instanceof Player player) {
            AirdropEditorMenu menu = new AirdropEditorMenu(table);
            menu.render(notas.values());
            player.openInventory(menu.getInventory());
            return true;
        }
        if (action.equals("admin") && args.length == 4 && args[1].equalsIgnoreCase("contrato")
                && sender instanceof Player player) {
            int level;
            int amount;
            try {
                level = Integer.parseInt(args[2]);
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                level = 0;
                amount = 0;
            }
            if (level < 1 || level > ContractItems.LEVELS || amount < 1 || amount > 640) {
                player.sendMessage("§cUso: /" + label + " admin contrato <1-5> <quantidade (1-640)>");
                return true;
            }
            int left = amount;
            while (left > 0) {
                ItemStack stack = contracts.create(level, left);
                left -= stack.getAmount();
                player.getInventory().addItem(stack).values()
                        .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
            }
            player.sendMessage("§aVocê recebeu " + amount + "x Contrato de Recompensa Nível "
                    + ContractItems.roman(level) + ".");
            return true;
        }
        sender.sendMessage("§cUso: /" + label + " [iniciar | admin editar | admin contrato <nível> <quantidade>]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("iniciar", "admin");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return List.of("editar", "contrato");
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("contrato")) {
            return List.of("1", "2", "3", "4", "5");
        }
        return List.of();
    }
}
