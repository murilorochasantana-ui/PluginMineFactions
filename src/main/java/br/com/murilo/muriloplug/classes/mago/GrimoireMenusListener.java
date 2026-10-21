package br.com.murilo.muriloplug.classes.mago;

import br.com.murilo.muriloplug.combat.CombatManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Cliques dos menus do Grimório. Todo clique e arrasto é cancelado: nada pode ser pego, movido ou colocado. Clicar
 * numa staff do próprio inventário abre a confirmação de aprender; confirmar reconfere o slot e consome a staff.
 */
public final class GrimoireMenusListener implements Listener {

    private final JavaPlugin plugin;
    private final GrimoireManager grimoire;
    private final StaffItems staffs;
    private final ManaManager mana;
    private final CombatManager combat;
    private final MagoSettings settings;

    public GrimoireMenusListener(JavaPlugin plugin, GrimoireManager grimoire, StaffItems staffs, ManaManager mana,
                                 CombatManager combat, MagoSettings settings) {
        this.plugin = plugin;
        this.grimoire = grimoire;
        this.staffs = staffs;
        this.mana = mana;
        this.combat = combat;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof GrimoireHolder)) {
            return;
        }
        // Cancela tudo (shift-click, teclas 1-9, tecla F, clique duplo, soltar com Q).
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() == null) {
            return;
        }
        boolean top = event.getClickedInventory() == event.getView().getTopInventory();

        if (holder instanceof GrimoireMenu menu) {
            if (top) {
                onMenuClick(player, menu, event.getSlot(), event.getClick());
            } else if (event.getClickedInventory() == player.getInventory()) {
                onInventoryClick(player, event.getSlot(), event.getCurrentItem());
            }
        } else if (holder instanceof GrimoireConfirmMenu confirm && top) {
            switch (event.getSlot()) {
                case GrimoireConfirmMenu.CANCEL_SLOT -> {
                    player.sendMessage("§eNada mudou.");
                    openMenuLater(player);
                }
                case GrimoireConfirmMenu.CONFIRM_SLOT -> onConfirm(player, confirm);
                default -> { }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GrimoireHolder) {
            event.setCancelled(true);
        }
    }

    /** Equipar/desequipar (clique numa aprendida ou numa equipada) ou selecionar (clique direito numa equipada). */
    private void onMenuClick(Player player, GrimoireMenu menu, int slot, ClickType click) {
        UUID id = player.getUniqueId();
        StaffType learned = menu.learnedAt(slot);
        StaffType equipped = menu.equippedAt(slot);
        if (learned == null && equipped == null) {
            return;
        }
        if (equipped != null && click.isRightClick()) {
            grimoire.select(id, equipped);
            player.sendMessage("§5Magia selecionada: " + equipped.coloredSpellName());
        } else {
            if (combat.isInCombat(player)) {
                player.sendMessage("§cVocê não pode trocar as magias equipadas em combate.");
                return;
            }
            StaffType type = learned != null ? learned : equipped;
            switch (grimoire.toggleEquip(id, type)) {
                case EQUIPPED -> player.sendMessage("§aEquipada: " + type.coloredSpellName());
                case UNEQUIPPED -> player.sendMessage("§eDesequipada: " + type.coloredSpellName());
                case FULL -> player.sendMessage("§cVocê só pode equipar " + grimoire.maxEquipped() + " magias.");
                case NOT_LEARNED -> player.sendMessage("§cVocê ainda não aprendeu esta magia.");
            }
        }
        menu.render();
        grimoire.refresh(player);
    }

    /** Clique numa staff do próprio inventário: abre a confirmação de aprender. */
    private void onInventoryClick(Player player, int slot, ItemStack clicked) {
        StaffType type = staffs.read(clicked);
        if (type == null) {
            return;
        }
        if (grimoire.knows(player.getUniqueId(), type)) {
            player.sendMessage("§cVocê já aprendeu a magia de " + type.coloredSpellName() + "§c.");
            return;
        }
        // Abre a confirmação no tick seguinte, fora do evento de clique.
        plugin.getServer().getScheduler().runTask(plugin, () -> GrimoireConfirmMenu.open(player, type, slot));
    }

    private void onConfirm(Player player, GrimoireConfirmMenu confirm) {
        if (confirm.used()) {
            return;
        }
        confirm.markUsed();
        UUID id = player.getUniqueId();
        StaffType spell = confirm.spell();

        // Revalida tudo: o estado pode ter mudado com o menu aberto.
        if (!mana.isMage(id)) {
            player.sendMessage("§cSomente Magos podem aprender magias.");
        } else if (grimoire.knows(id, spell)) {
            player.sendMessage("§cVocê já aprendeu a magia de " + spell.coloredSpellName() + "§c.");
        } else {
            ItemStack stack = player.getInventory().getItem(confirm.staffSlot());
            if (staffs.read(stack) != spell) {
                player.sendMessage("§cA staff não está mais nesse slot. Nada foi consumido.");
            } else {
                // Consome 1 staff e aprende no mesmo instante: não há como o item continuar com o jogador.
                if (stack.getAmount() > 1) {
                    stack.setAmount(stack.getAmount() - 1);
                    player.getInventory().setItem(confirm.staffSlot(), stack);
                } else {
                    player.getInventory().setItem(confirm.staffSlot(), null);
                }
                grimoire.learn(id, spell);
                player.sendMessage("§5Você aprendeu a magia de " + spell.coloredSpellName() + "§5!");
            }
        }
        openMenuLater(player);
    }

    private void openMenuLater(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> GrimoireMenu.open(player, grimoire, settings));
    }
}
