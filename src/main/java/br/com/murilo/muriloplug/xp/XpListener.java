package br.com.murilo.muriloplug.xp;

import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExpEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ExpBottleEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * O servidor não tem XP: nenhuma fonte dá XP real (mortes, blocos, fornalha, pesca, reprodução, garrafa) nem orbes, o
 * XP de quem já tinha é zerado ao entrar e a mesa de encantamento e a bigorna ficam bloqueadas (opções em
 * {@code xp.*}). A barra de XP que o jogador vê é só a das classes ({@code ClassBarManager}), enviada sem dar XP.
 */
public final class XpListener implements Listener {

    private final XpSettings settings;

    public XpListener(XpSettings settings) {
        this.settings = settings;
    }

    // ------------------------------------------------------------ fontes de XP

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        if (settings.disabled()) {
            event.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (settings.disabled()) {
            event.setDroppedExp(0);
            event.setNewExp(0);
            event.setNewLevel(0);
            event.setNewTotalExp(0);
            event.setKeepLevel(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (settings.disabled()) {
            event.setExpToDrop(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockExp(BlockExpEvent event) {
        if (settings.disabled()) {
            event.setExpToDrop(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        if (settings.disabled()) {
            event.setExpToDrop(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFish(PlayerFishEvent event) {
        if (settings.disabled()) {
            event.setExpToDrop(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreed(EntityBreedEvent event) {
        if (settings.disabled()) {
            event.setExperience(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExpBottle(ExpBottleEvent event) {
        if (settings.disabled()) {
            event.setExperience(0);
        }
    }

    /** Garantia final (rebolo, troca com aldeão etc.): orbe nenhum surge no mundo. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onOrbSpawn(EntitySpawnEvent event) {
        if (settings.disabled() && event.getEntity() instanceof ExperienceOrb) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExpChange(PlayerExpChangeEvent event) {
        if (settings.disabled()) {
            event.setAmount(0);
        }
    }

    // ------------------------------------------------------------ XP que já existia

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        clear(event.getPlayer());
    }

    /** Zera o XP real do jogador (para quem já tinha antes de o XP ser removido). */
    public void clear(Player player) {
        if (settings.disabled() && (player.getLevel() != 0 || player.getExp() != 0 || player.getTotalExperience() != 0)) {
            player.setLevel(0);
            player.setExp(0);
            player.setTotalExperience(0);
        }
    }

    // ------------------------------------------------------------ Locator Bar

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        disableLocatorBar(event.getWorld());
    }

    /**
     * Desliga a Locator Bar do mundo.
     */
    public void disableLocatorBar(World world) {
        if (!settings.disableLocatorBar()) {
            return;
        }
        world.setGameRule(GameRules.LOCATOR_BAR, false);
    }

    // ------------------------------------------------------------ encantamento

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        InventoryType type = event.getInventory().getType();
        boolean blocked = (type == InventoryType.ENCHANTING && settings.blockEnchantingTable())
                || (type == InventoryType.ANVIL && settings.blockAnvil());
        if (blocked) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cO encantamento deste servidor será feito de outra forma.");
        }
    }
}
