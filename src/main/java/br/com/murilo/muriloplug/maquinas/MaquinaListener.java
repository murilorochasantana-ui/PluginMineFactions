package br.com.murilo.muriloplug.maquinas;

import br.com.murilo.muriloplug.clan.Clan;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * As máquinas no mundo: colocar (só no claim do próprio clan), proteção do bloco (não quebra na mão, não queima, não
 * pega água/lava, o spawner não gera mobs; pistões, funis, mobs e explosões vanilla ficam com a proteção dos blocos da
 * base, ligada pelo {@link MaquinaManager#start()}) e o clique direito, que abre o menu da máquina no lugar do
 * inventário da fornalha/defumador/spawner.
 */
public final class MaquinaListener implements Listener {

    private static final long AVISO_MILLIS = 2_000L;

    private final MaquinaManager manager;
    private final MaquinasMenus menus;
    private final Map<UUID, Long> ultimoAviso = new HashMap<>();

    public MaquinaListener(MaquinaManager manager, MaquinasMenus menus) {
        this.manager = manager;
        this.menus = menus;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        MaquinaTipo tipo = manager.itens().tipoMaquina(event.getItemInHand());
        if (tipo == null) {
            return;
        }
        String problema = manager.colocar(event.getPlayer(), event.getBlockPlaced(), tipo);
        if (problema != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(problema);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        if (!manager.protegido(event.getBlock())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        long agora = System.currentTimeMillis();
        Long ultimo = ultimoAviso.get(player.getUniqueId());
        if (ultimo == null || agora - ultimo > AVISO_MILLIS) {
            ultimoAviso.put(player.getUniqueId(), agora);
            player.sendMessage("§cMáquinas não quebram na mão. §7Use \"Recolher máquina\" no menu dela.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBurn(BlockBurnEvent event) {
        if (manager.protegido(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onIgnite(BlockIgniteEvent event) {
        if (manager.protegido(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /** Água e lava não entram no bloco da máquina. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFlow(BlockFromToEvent event) {
        if (manager.protegido(event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    /** O spawner da Máquina de Drops nunca gera mobs. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawner(SpawnerSpawnEvent event) {
        if (event.getSpawner() != null && manager.protegido(event.getSpawner().getBlock())) {
            event.setCancelled(true);
        }
    }

    /** Itens de máquina e de upgrade não servem de ingrediente (não somem numa receita). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (manager.itens().tipoMaquina(item) != null || manager.itens().lerUpgrade(item) != null) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    /** Clique direito: menu da máquina (nunca o inventário original do bloco, nem ovo de spawn no spawner). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null) {
            return;
        }
        Maquina maquina = manager.em(block);
        if (maquina == null) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }
        Player player = event.getPlayer();
        Clan clan = manager.clans().getByTag(maquina.clan);
        if (clan == null || !clan.isMember(player.getUniqueId())) {
            player.sendMessage("§cEsta máquina pertence ao clan " + maquina.clan);
            return;
        }
        if (player.isSneaking() && manager.reparar(player, maquina)) {
            return;
        }
        menus.abrirMaquina(player, maquina, false);
    }
}
