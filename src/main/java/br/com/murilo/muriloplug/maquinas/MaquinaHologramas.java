package br.com.murilo.muriloplug.maquinas;

import br.com.murilo.muriloplug.clan.Clan;
import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Texto em cima de cada máquina (TextDisplay), visível só para os membros do clan dono, como o saldo do Cofre. O texto
 * só é trocado quando muda. Os textos são marcados no PDC ({@code mineplug:maquina_holograma}) para que sobras (de um
 * desligamento sem onDisable) sejam apagadas ao ligar e ao carregar chunks.
 */
public final class MaquinaHologramas implements Listener {

    private static final double ALTURA = 1.0;

    private final JavaPlugin plugin;
    private final NamespacedKey marca;
    private final Map<String, TextDisplay> displays = new HashMap<>();
    private final Map<String, String> textos = new HashMap<>();
    private final Map<String, Set<UUID>> vendo = new HashMap<>();

    public MaquinaHologramas(JavaPlugin plugin) {
        this.plugin = plugin;
        this.marca = new NamespacedKey(plugin, "maquina_holograma");
    }

    /** Põe/atualiza o texto (só com o chunk carregado) e mostra só para os membros online do clan. */
    void atualizar(Maquina maquina, String texto, Clan clan) {
        if (!maquina.local.isLoaded()) {
            remover(maquina.id);
            return;
        }
        TextDisplay display = displays.get(maquina.id);
        if (display == null || !display.isValid()) {
            Location at = maquina.local.center().add(0, ALTURA, 0);
            display = at.getWorld().spawn(at, TextDisplay.class, spawned -> {
                spawned.setPersistent(false);
                spawned.setVisibleByDefault(false);
                spawned.setBillboard(Display.Billboard.CENTER);
                spawned.getPersistentDataContainer().set(marca, PersistentDataType.STRING, maquina.id);
            });
            displays.put(maquina.id, display);
            textos.remove(maquina.id);
            vendo.remove(maquina.id);
        }
        if (!texto.equals(textos.get(maquina.id))) {
            display.text(ShopUi.text(texto));
            textos.put(maquina.id, texto);
        }
        Set<UUID> atual = vendo.computeIfAbsent(maquina.id, id -> new HashSet<>());
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean membro = clan != null && clan.isMember(player.getUniqueId());
            if (membro && atual.add(player.getUniqueId())) {
                player.showEntity(plugin, display);
            } else if (!membro && atual.remove(player.getUniqueId())) {
                player.hideEntity(plugin, display);
            }
        }
        atual.removeIf(id -> Bukkit.getPlayer(id) == null); // quem saiu volta a receber o show ao entrar
    }

    void remover(String id) {
        TextDisplay display = displays.remove(id);
        textos.remove(id);
        vendo.remove(id);
        if (display != null) {
            display.remove();
        }
    }

    /** onDisable. */
    void removerTodos() {
        displays.values().forEach(TextDisplay::remove);
        displays.clear();
        textos.clear();
        vendo.clear();
    }

    /** onEnable: apaga textos de máquinas que sobraram nos mundos já carregados. */
    void limparSobras() {
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                limpar(display);
            }
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof TextDisplay display) {
                limpar(display);
            }
        }
    }

    private void limpar(TextDisplay display) {
        String id = display.getPersistentDataContainer().get(marca, PersistentDataType.STRING);
        if (id != null && displays.get(id) != display) {
            display.remove();
        }
    }
}
