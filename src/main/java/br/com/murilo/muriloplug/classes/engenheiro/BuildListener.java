package br.com.murilo.muriloplug.classes.engenheiro;

import br.com.murilo.muriloplug.auth.AuthManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.PlayerClassChangeEvent;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Uso do Livro de Estrutura (clique direito) e proteção das áreas de construção abertas: só o dono mexe no volume,
 * ninguém mexe no chão de concreto, e nada de fora (explosões, pistões, líquidos, fogo, enderman) entra ou sai.
 */
public final class BuildListener implements Listener {

    private static final long WARNING_COOLDOWN_MILLIS = 2_000L;

    private final BuildManager builds;
    private final StructureBooks books;
    private final EngenheiroSettings settings;
    private final AuthManager auth;
    private final PlacementManager placement;
    private final Map<UUID, Long> lastWarning = new HashMap<>();

    public BuildListener(BuildManager builds, StructureBooks books, EngenheiroSettings settings, AuthManager auth,
                         PlacementManager placement) {
        this.placement = placement;
        this.builds = builds;
        this.books = books;
        this.settings = settings;
        this.auth = auth;
    }

    /** Avisa no máximo uma vez a cada 2s (cliques e quebras repetidos não enchem o chat). */
    private void warn(Player player, String message) {
        long now = System.currentTimeMillis();
        Long last = lastWarning.get(player.getUniqueId());
        if (last == null || now - last >= WARNING_COOLDOWN_MILLIS) {
            lastWarning.put(player.getUniqueId(), now);
            player.sendMessage(message);
        }
    }

    // ------------------------------------------------------------ o livro

    // Não usa ignoreCancelled: um claim alheio cancela o clique em blocos, mas o livro não mexe em blocos.
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!books.isBook(item)) {
            return;
        }
        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        if (clicked != null && clicked.getType().isInteractable() && !player.isSneaking()) {
            return; // deixa abrir baú, porta, alavanca etc. normalmente
        }
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND || !auth.isLoggedIn(player.getUniqueId())) {
            return;
        }
        if (!builds.isEngineer(player)) {
            warn(player, "§cSomente Engenheiros podem usar este livro.");
            return;
        }
        if (books.isFull(item)) {
            placement.onUse(player, item);
            return;
        }
        OpenBuild bound = builds.ofBook(books.bookId(item));
        if (bound == null) {
            builds.mark(player, item);
        } else if (bound.owner.equals(player.getUniqueId())) {
            BuildMenu.open(player, builds, bound);
        } else {
            warn(player, "§cEsta construção pertence a outro Engenheiro.");
        }
    }

    // ------------------------------------------------------------ blocos do jogador

    /** Cancela se o jogador não pode mexer neste bloco: de outro dono, ou o chão (nem o dono mexe). */
    private boolean denyEdit(Player player, Block block, Cancellable event) {
        OpenBuild area = builds.at(block);
        if (area == null) {
            return false;
        }
        if (!area.owner.equals(player.getUniqueId())) {
            event.setCancelled(true);
            warn(player, "§cEsta é a área de construção de outro Engenheiro.");
            return true;
        }
        if (area.isFloor(block.getY())) {
            event.setCancelled(true);
            warn(player, "§cO chão da construção (concreto branco) não pode ser alterado.");
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!builds.hasAny() || denyEdit(event.getPlayer(), block, event) || builds.at(block) == null) {
            return;
        }
        Material type = block.getType();
        if (settings.forbidden().test(type)) {
            event.setCancelled(true);
            warn(event.getPlayer(), "§cEste bloco não pode ser usado na construção: " + type.name());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (builds.hasAny()) {
            denyEdit(event.getPlayer(), event.getBlock(), event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (builds.hasAny()) {
            denyEdit(event.getPlayer(), event.getBlock(), event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (builds.hasAny()) {
            denyEdit(event.getPlayer(), event.getBlock(), event);
        }
    }

    // ------------------------------------------------------------ o que vem de fora

    /** Líquido não passa de dentro para fora nem de fora para dentro. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent event) {
        if (builds.hasAny() && builds.at(event.getBlock()) != builds.at(event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (crosses(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (crosses(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    /** Se o pistão mexeria em blocos de fora para dentro da área (ou de dentro para fora). Conservador nas bordas. */
    private boolean crosses(Block piston, List<Block> moved, BlockFace direction) {
        if (!builds.hasAny()) {
            return false;
        }
        OpenBuild home = builds.at(piston);
        if (builds.at(piston.getRelative(direction)) != home) {
            return true;
        }
        for (Block block : moved) {
            if (builds.at(block) != home || builds.at(block.getRelative(direction)) != home
                    || builds.at(block.getRelative(direction.getOppositeFace())) != home) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (builds.hasAny()) {
            event.blockList().removeIf(block -> builds.at(block) != null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (builds.hasAny()) {
            event.blockList().removeIf(block -> builds.at(block) != null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (builds.hasAny() && builds.at(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (builds.hasAny() && builds.at(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    /** O fogo também se espalha por BlockSpreadEvent. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        Material type = event.getNewState().getType();
        if (builds.hasAny() && (type == Material.FIRE || type == Material.SOUL_FIRE)
                && builds.at(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    /** Enderman, wither e afins não mexem em blocos da área (areia caindo e jogadores, sim). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (builds.hasAny() && !(event.getEntity() instanceof FallingBlock) && !(event.getEntity() instanceof Player)
                && builds.at(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    /** Ejetor/dispensador de fora não joga água, lava, fogo ou blocos para dentro da área (nem o contrário). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (!builds.hasAny()) {
            return;
        }
        Material item = event.getItem().getType();
        boolean placesThings = item.isBlock() || item.name().endsWith("_BUCKET") || item == Material.FIRE_CHARGE
                || item == Material.FLINT_AND_STEEL || item == Material.BONE_MEAL;
        if (!placesThings || !(event.getBlock().getBlockData() instanceof org.bukkit.block.data.Directional facing)) {
            return;
        }
        Block target = event.getBlock().getRelative(facing.getFacing());
        if (builds.at(event.getBlock()) != builds.at(target)) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ fim da construção

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        builds.cancel(event.getPlayer().getUniqueId(), null);
        lastWarning.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        builds.cancel(event.getEntity().getUniqueId(),
                "§eConstrução cancelada porque você morreu. Os blocos ficaram no lugar.");
    }

    @EventHandler
    public void onClassChange(PlayerClassChangeEvent event) {
        if (event.getOldClass() == PlayerClass.ENGENHEIRO) {
            builds.cancel(event.getPlayerId(),
                    "§eConstrução cancelada: você deixou de ser Engenheiro. Os blocos ficaram no lugar.");
        }
    }
}
