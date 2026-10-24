package br.com.murilo.muriloplug.classes.engenheiro;

import br.com.murilo.muriloplug.classes.SilenceManager;
import br.com.murilo.muriloplug.auth.AuthManager;
import br.com.murilo.muriloplug.shop.ShopUi;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Candle;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.SeaPickle;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Snow;
import org.bukkit.block.data.type.TurtleEgg;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.structure.Structure;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Planta de Engenharia: guarda projetos (só a ideia da construção) e os transforma em Livros de Estrutura cheios
 * quando o Engenheiro tem um Livro de Estrutura vazio e todos os materiais, que são consumidos de uma vez.
 * <p>
 * Materiais: cada bloco vira o item que o coloca ({@code BlockData#getPlacementMaterial}); a segunda metade de
 * portas, camas e plantas altas e a cabeça do pistão não contam; laje dupla conta 2; velas, picles, ovos de tartaruga
 * e camadas de neve contam pela quantidade; água e lava (só as fontes) pedem balde cheio e o balde vazio volta;
 * os itens dentro de baús, ejetores etc. também entram na lista. Blocos sem item (fogo, vasos com planta) não pedem nada.
 */
public final class PlantaManager implements Listener {

    private static final long PROMPT_MILLIS = 60_000L;
    private static final Set<Material> TALL_PLANTS = EnumSet.of(Material.TALL_GRASS, Material.LARGE_FERN,
            Material.TALL_SEAGRASS, Material.SUNFLOWER, Material.LILAC, Material.ROSE_BUSH, Material.PEONY,
            Material.PITCHER_PLANT);

    /** Um material do projeto: o item (quantidade 1) e quantos são necessários. */
    record Need(ItemStack key, int amount) {
    }

    private enum PromptKind { SAVE, RENAME }

    private record Prompt(PromptKind kind, UUID planta, String projectId, long expires) {
    }

    private final JavaPlugin plugin;
    private final BuildManager builds;
    private final EngenheiroSettings settings;
    private final StructureBooks books;
    private final StructureStore structures;
    private final PlantaItems items;
    private final PlantaStore store;
    private final EngenheiroAcoes actions;
    private final AuthManager auth;
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

    public PlantaManager(JavaPlugin plugin, BuildManager builds, EngenheiroSettings settings, StructureBooks books,
                         StructureStore structures, PlantaItems items, PlantaStore store, EngenheiroAcoes actions,
                         AuthManager auth) {
        this.plugin = plugin;
        this.builds = builds;
        this.settings = settings;
        this.books = books;
        this.structures = structures;
        this.items = items;
        this.store = store;
        this.actions = actions;
        this.auth = auth;
    }

    // ------------------------------------------------------------ a Planta no inventário

    /** Slot (de getContents) da Planta com este UUID, ou -1. */
    private int slotOf(Player player, UUID planta) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (planta.equals(items.id(contents[i]))) {
                return i;
            }
        }
        return -1;
    }

    /** Se o jogador tem alguma Planta com espaço (sem mexer em nada). */
    boolean hasPlantaWithSpace(Player player) {
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            UUID id = items.id(stack);
            if (id != null && (items.isFresh(stack) || store.count(id) < settings.plantaMaxProjects())) {
                return true;
            }
        }
        return false;
    }

    /** Slot da primeira Planta com espaço (uma Planta nova ganha o UUID dela agora), ou -1. */
    private int slotWithSpace(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            UUID id = items.id(contents[i]);
            if (id == null) {
                continue;
            }
            if (items.isFresh(contents[i])) {
                inventory.setItem(i, items.claim(contents[i]));
                return i;
            }
            if (store.count(id) < settings.plantaMaxProjects()) {
                return i;
            }
        }
        return -1;
    }

    /** Guarda o projeto na Planta do slot e atualiza a linha "Projetos" do item. Retorna false se falhar. */
    boolean saveProject(Player player, String name, String size, int blocks, Structure structure) {
        int slot = slotWithSpace(player);
        if (slot < 0) {
            player.sendMessage("§cVocê precisa de uma Planta com espaço no inventário.");
            return false;
        }
        ItemStack planta = player.getInventory().getItem(slot);
        UUID id = items.id(planta);
        PlantaStore.Project project = new PlantaStore.Project(UUID.randomUUID().toString(), name, size,
                player.getName(), System.currentTimeMillis(), blocks);
        try {
            store.add(id, project, structure);
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao guardar o projeto na Planta de " + player.getName(), e);
            player.sendMessage("§cNão foi possível guardar o projeto. Nada foi alterado.");
            return false;
        }
        refreshLore(player, id);
        return true;
    }

    private void refreshLore(Player player, UUID planta) {
        int slot = slotOf(player, planta);
        if (slot >= 0) {
            ItemStack item = player.getInventory().getItem(slot);
            items.setProjectCount(item, store.count(planta));
            player.getInventory().setItem(slot, item);
        }
    }

    // ------------------------------------------------------------ abrir a Planta

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!items.isPlanta(item)) {
            return;
        }
        Player player = event.getPlayer();
        if (event.getClickedBlock() != null && event.getClickedBlock().getType().isInteractable()
                && !player.isSneaking()) {
            return; // deixa abrir baú, porta, alavanca etc. normalmente
        }
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND || !auth.isLoggedIn(player.getUniqueId())) {
            return;
        }
        if (!builds.isEngineer(player)) {
            player.sendMessage("§cSomente Engenheiros podem usar a Planta.");
            return;
        }
        if (SilenceManager.blocks(player)) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (items.isFresh(hand)) {
            hand = items.claim(hand);
            player.getInventory().setItemInMainHand(hand);
        }
        openList(player, items.id(hand));
    }

    private void openList(Player player, UUID planta) {
        List<PlantaStore.Project> projects = store.list(planta);
        player.openInventory(new PlantaMenu(planta, projects, settings.plantaMaxProjects()).getInventory());
    }

    private void openProject(Player player, UUID planta, String projectId) {
        PlantaStore.Project project = store.get(planta, projectId);
        if (project == null) {
            player.sendMessage("§cEste projeto não existe mais.");
            openList(player, planta);
            return;
        }
        Structure structure = load(player, planta, projectId);
        if (structure == null) {
            return;
        }
        List<Need> needs = needs(structure);
        PlantaProjectMenu menu = new PlantaProjectMenu(planta, projectId, project.name());
        var inventory = menu.getInventory();
        ItemStack[] contents = player.getInventory().getStorageContents();
        int bookSlot = findBook(contents);
        boolean complete = bookSlot >= 0;

        int shown = needs.size() > PlantaProjectMenu.MATERIAL_SLOTS ? PlantaProjectMenu.MATERIAL_SLOTS - 1 : needs.size();
        for (int i = 0; i < needs.size(); i++) {
            Need need = needs.get(i);
            int have = have(contents, need.key(), Set.of(bookSlot), planta);
            boolean enough = have >= need.amount();
            complete &= enough;
            if (i < shown) {
                inventory.setItem(i, materialIcon(need, have, enough));
            }
        }
        if (shown < needs.size()) {
            inventory.setItem(shown, ShopUi.button(Material.PAPER,
                    "§7... e mais " + (needs.size() - shown) + " tipo(s) de material"));
        }
        inventory.setItem(PlantaProjectMenu.BACK_SLOT, ShopUi.button(Material.ARROW, "§cVoltar"));
        inventory.setItem(PlantaProjectMenu.BOOK_SLOT, ShopUi.button(Material.BOOK,
                (bookSlot >= 0 ? "§a" : "§c") + "Livro de Estrutura vazio",
                bookSlot >= 0 ? "§7Você tem um: será gasto." : "§cVocê precisa de 1 no inventário.",
                "§7(Não vale o livro de uma construção aberta.)"));
        inventory.setItem(PlantaProjectMenu.CREATE_SLOT, ShopUi.button(Material.LIME_CONCRETE,
                "§aCriar livro",
                complete ? "§7Você tem tudo. Clique para criar." : "§cFaltam materiais (em vermelho).",
                "§7Tamanho: " + project.size()));
        inventory.setItem(PlantaProjectMenu.RENAME_SLOT, ShopUi.button(Material.NAME_TAG,
                "§eRenomear"));
        inventory.setItem(PlantaProjectMenu.DELETE_SLOT, ShopUi.button(Material.LAVA_BUCKET,
                "§4Apagar projeto"));
        player.openInventory(inventory);
    }

    private static ItemStack materialIcon(Need need, int have, boolean enough) {
        NamedTextColor color = enough ? NamedTextColor.GREEN : NamedTextColor.RED;
        ItemStack icon = need.key().clone();
        icon.setAmount(Math.max(1, Math.min(99, need.amount())));
        ItemMeta meta = icon.getItemMeta();
        if (!meta.hasDisplayName()) {
            meta.displayName(Component.translatable(icon.getType().translationKey(), color)
                    .decoration(TextDecoration.ITALIC, false));
        }
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.text("Tem: " + have + " / Precisa: " + need.amount(), color)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    // ------------------------------------------------------------ materiais

    private Structure load(Player player, UUID planta, String projectId) {
        try {
            Structure structure = store.load(planta, projectId);
            if (structure != null && structure.getPaletteCount() > 0) {
                return structure;
            }
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível carregar o projeto " + projectId, e);
        }
        player.sendMessage("§cO arquivo deste projeto está corrompido ou sumiu.");
        return null;
    }

    /** Os materiais do projeto, na ordem em que aparecem. */
    List<Need> needs(Structure structure) {
        Map<ItemStack, Integer> total = new LinkedHashMap<>();
        for (BlockState state : structure.getPalettes().get(0).getBlocks()) {
            BlockData data = state.getBlockData();
            Material type = data.getMaterial();
            if (type.isAir() || isSecondPart(data)) {
                continue;
            }
            Material item;
            int amount = 1;
            if (type == Material.WATER || type == Material.LAVA) {
                if (!(data instanceof Levelled levelled) || levelled.getLevel() != 0) {
                    continue; // água corrente: nasce da fonte
                }
                item = type == Material.WATER ? Material.WATER_BUCKET : Material.LAVA_BUCKET;
            } else {
                item = data.getPlacementMaterial();
                if (item == null || item.isAir() || !item.isItem()) {
                    continue;
                }
                amount = multiplicity(data);
            }
            total.merge(new ItemStack(item), amount, Integer::sum);
            if (state instanceof Container container) {
                for (ItemStack content : container.getSnapshotInventory().getContents()) {
                    if (content != null && !content.getType().isAir()) {
                        ItemStack key = content.clone();
                        key.setAmount(1);
                        total.merge(key, content.getAmount(), Integer::sum);
                    }
                }
            }
        }
        List<Need> result = new ArrayList<>();
        total.forEach((key, amount) -> result.add(new Need(key, amount)));
        return result;
    }

    /** Segunda metade de bloco de 2 partes (e a cabeça do pistão): o item da primeira já cobre. */
    private static boolean isSecondPart(BlockData data) {
        Material type = data.getMaterial();
        if (type == Material.PISTON_HEAD || type == Material.MOVING_PISTON) {
            return true;
        }
        if (data instanceof Door door) {
            return door.getHalf() == Bisected.Half.TOP;
        }
        if (data instanceof Bed bed) {
            return bed.getPart() == Bed.Part.HEAD;
        }
        return data instanceof Bisected bisected && TALL_PLANTS.contains(type) && bisected.getHalf() == Bisected.Half.TOP;
    }

    private static int multiplicity(BlockData data) {
        if (data instanceof Slab slab && slab.getType() == Slab.Type.DOUBLE) {
            return 2;
        }
        if (data instanceof Candle candle) {
            return candle.getCandles();
        }
        if (data instanceof SeaPickle pickle) {
            return pickle.getPickles();
        }
        if (data instanceof TurtleEgg eggs) {
            return eggs.getEggs();
        }
        if (data instanceof Snow snow) {
            return snow.getLayers();
        }
        return 1;
    }

    /** Primeiro Livro de Estrutura vazio que não pertence a uma construção aberta, ou -1. */
    private int findBook(ItemStack[] contents) {
        for (int i = 0; i < contents.length; i++) {
            if (books.isEmpty(contents[i]) && builds.ofBook(books.bookId(contents[i])) == null) {
                return i;
            }
        }
        return -1;
    }

    /** Quantos itens iguais ao modelo há nos slots que podem ser gastos (a própria Planta e os excluídos não). */
    private int have(ItemStack[] contents, ItemStack key, Set<Integer> excluded, UUID planta) {
        int total = 0;
        for (int i = 0; i < contents.length; i++) {
            if (spendable(contents[i], key, planta) && !excluded.contains(i)) {
                total += contents[i].getAmount();
            }
        }
        return total;
    }

    private boolean spendable(ItemStack stack, ItemStack key, UUID planta) {
        return stack != null && stack.isSimilar(key) && !planta.equals(items.id(stack));
    }

    /** Quanto tirar de cada slot para pagar tudo, ou null se faltar algo. */
    private Map<Integer, Integer> plan(ItemStack[] contents, List<Need> needs, int bookSlot, UUID planta) {
        Map<Integer, Integer> plan = new HashMap<>();
        for (Need need : needs) {
            int remaining = need.amount();
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                if (i != bookSlot && spendable(contents[i], need.key(), planta)) {
                    int take = Math.min(remaining, contents[i].getAmount());
                    plan.merge(i, take, Integer::sum);
                    remaining -= take;
                }
            }
            if (remaining > 0) {
                return null;
            }
        }
        return plan;
    }

    // ------------------------------------------------------------ cliques dos menus

    private boolean ready(Player player, UUID planta) {
        if (!builds.isEngineer(player)) {
            player.sendMessage("§cVocê não é mais Engenheiro.");
        } else if (!auth.isLoggedIn(player.getUniqueId())) {
            player.sendMessage("§cFaça login primeiro.");
        } else if (slotOf(player, planta) < 0) {
            player.sendMessage("§cVocê precisa estar com esta Planta no inventário.");
        } else {
            return true;
        }
        later(player::closeInventory);
        return false;
    }

    private void later(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    void onListClick(Player player, PlantaMenu menu, int slot) {
        PlantaStore.Project project = menu.projectAt(slot);
        if (project != null && ready(player, menu.planta())) {
            later(() -> openProject(player, menu.planta(), project.id()));
        }
    }

    void onProjectClick(Player player, PlantaProjectMenu menu, int slot) {
        UUID planta = menu.planta();
        String projectId = menu.projectId();
        if (slot != PlantaProjectMenu.BACK_SLOT && slot != PlantaProjectMenu.CREATE_SLOT
                && slot != PlantaProjectMenu.RENAME_SLOT && slot != PlantaProjectMenu.DELETE_SLOT) {
            return;
        }
        if (!ready(player, planta)) {
            return;
        }
        PlantaStore.Project project = store.get(planta, projectId);
        if (project == null) {
            player.sendMessage("§cEste projeto não existe mais.");
            later(() -> openList(player, planta));
            return;
        }
        switch (slot) {
            case PlantaProjectMenu.BACK_SLOT -> later(() -> openList(player, planta));
            case PlantaProjectMenu.CREATE_SLOT -> {
                String lacking = missing(player, planta, projectId);
                if (lacking != null) {
                    player.sendMessage("§cVocê não tem tudo: " + lacking);
                    return;
                }
                later(() -> player.openInventory(new PlantaConfirmMenu(PlantaConfirmMenu.Kind.CREATE, planta,
                        projectId, project.name()).getInventory()));
            }
            case PlantaProjectMenu.RENAME_SLOT -> later(() -> {
                player.closeInventory();
                prompts.put(player.getUniqueId(), new Prompt(PromptKind.RENAME, planta, projectId,
                        System.currentTimeMillis() + PROMPT_MILLIS));
                player.sendMessage("§eDigite o novo nome do projeto no chat (até " + settings.plantaNameMax()
                        + " caracteres) ou \"cancelar\".");
            });
            default -> later(() -> player.openInventory(new PlantaConfirmMenu(PlantaConfirmMenu.Kind.DELETE, planta,
                    projectId, project.name()).getInventory()));
        }
    }

    void onConfirmClick(Player player, PlantaConfirmMenu menu, int slot) {
        UUID planta = menu.planta();
        String projectId = menu.projectId();
        if (slot == PlantaConfirmMenu.BACK_SLOT) {
            later(() -> openProject(player, planta, projectId));
        } else if (slot == PlantaConfirmMenu.CONFIRM_SLOT && !menu.used()) {
            menu.markUsed();
            if (!ready(player, planta)) {
                return;
            }
            if (menu.kind() == PlantaConfirmMenu.Kind.CREATE) {
                createBook(player, planta, projectId);
                later(player::closeInventory);
            } else {
                store.remove(planta, projectId);
                refreshLore(player, planta);
                player.sendMessage("§eProjeto apagado.");
                later(() -> openList(player, planta));
            }
        }
    }

    /** Texto do que falta (para avisar), ou null se está tudo. */
    private String missing(Player player, UUID planta, String projectId) {
        Structure structure = load(player, planta, projectId);
        if (structure == null) {
            return "o arquivo do projeto não pôde ser lido.";
        }
        ItemStack[] contents = player.getInventory().getStorageContents();
        int bookSlot = findBook(contents);
        if (bookSlot < 0) {
            return "falta um Livro de Estrutura vazio.";
        }
        return plan(contents, needs(structure), bookSlot, planta) == null ? "faltam materiais (em vermelho)." : null;
    }

    // ------------------------------------------------------------ criar o livro

    /** Confere tudo de novo e, no mesmo tick, gasta materiais e livro vazio e entrega o livro cheio. */
    private void createBook(Player player, UUID planta, String projectId) {
        PlantaStore.Project project = store.get(planta, projectId);
        Structure structure = project == null ? null : load(player, planta, projectId);
        if (structure == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        int bookSlot = findBook(contents);
        if (bookSlot < 0) {
            player.sendMessage("§cVocê precisa de um Livro de Estrutura vazio.");
            return;
        }
        List<Need> needs = needs(structure);
        Map<Integer, Integer> plan = plan(contents, needs, bookSlot, planta);
        if (plan == null) {
            player.sendMessage("§cFaltam materiais. Nada foi gasto.");
            return;
        }
        // O arquivo da estrutura nova é gravado ANTES de gastar qualquer coisa.
        String structureId = UUID.randomUUID().toString();
        File source = store.file(planta, projectId);
        try {
            structures.copyFrom(structureId, source);
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao copiar o projeto " + projectId + " para uma estrutura", e);
            player.sendMessage("§cNão foi possível criar o livro. Nada foi gasto.");
            return;
        }
        UUID bookId = books.bookId(contents[bookSlot]);

        int buckets = 0;
        for (Need need : needs) {
            if (need.key().getType() == Material.WATER_BUCKET || need.key().getType() == Material.LAVA_BUCKET) {
                buckets += need.amount();
            }
        }
        plan.forEach((slot, take) -> {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getAmount() <= take) {
                inventory.setItem(slot, null);
            } else {
                stack.setAmount(stack.getAmount() - take);
                inventory.setItem(slot, stack);
            }
        });
        String[] size = project.size().split("x");
        inventory.setItem(bookSlot, books.createFull(bookId, structureId, intOf(size, 0), intOf(size, 1),
                intOf(size, 2), project.blocks(), project.author()));
        if (buckets > 0) {
            inventory.addItem(new ItemStack(Material.BUCKET, buckets)).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        player.sendMessage("§aLivro de Estrutura criado: " + project.name() + "§a! O projeto continua na Planta.");
        actions.registrarAcao(player);
    }

    private static int intOf(String[] parts, int index) {
        try {
            return index < parts.length ? Integer.parseInt(parts[index].trim()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------ nome no chat

    /** Pede o nome do projeto que vai ser salvo (depois da confirmação do menu Construção). */
    void askProjectName(Player player) {
        prompts.put(player.getUniqueId(), new Prompt(PromptKind.SAVE, null, null,
                System.currentTimeMillis() + PROMPT_MILLIS));
        player.sendMessage("§eDigite o NOME do projeto no chat (até " + settings.plantaNameMax()
                + " caracteres) ou \"cancelar\".");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Prompt prompt = prompts.get(player.getUniqueId());
        if (prompt == null) {
            return;
        }
        if (prompt.expires() < System.currentTimeMillis()) {
            prompts.remove(player.getUniqueId());
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        later(() -> answer(player, text));
    }

    private void answer(Player player, String text) {
        Prompt prompt = prompts.remove(player.getUniqueId());
        if (prompt == null || !player.isOnline()) {
            return;
        }
        if (prompt.expires() < System.currentTimeMillis()) {
            player.sendMessage("§cO tempo para digitar o nome acabou.");
            return;
        }
        if (text.equalsIgnoreCase("cancelar")) {
            player.sendMessage("§eCancelado.");
            return;
        }
        if (text.isEmpty() || text.length() > settings.plantaNameMax() || text.indexOf('§') >= 0) {
            prompts.put(player.getUniqueId(), new Prompt(prompt.kind(), prompt.planta(), prompt.projectId(),
                    System.currentTimeMillis() + PROMPT_MILLIS));
            player.sendMessage("§cNome inválido: use de 1 a " + settings.plantaNameMax()
                    + " caracteres. Tente de novo ou digite \"cancelar\".");
            return;
        }
        if (prompt.kind() == PromptKind.SAVE) {
            builds.saveProject(player, text);
        } else if (ready(player, prompt.planta()) && store.get(prompt.planta(), prompt.projectId()) != null) {
            store.rename(prompt.planta(), prompt.projectId(), text);
            player.sendMessage("§aProjeto renomeado para " + text + "§a.");
            openProject(player, prompt.planta(), prompt.projectId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        prompts.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        prompts.remove(event.getEntity().getUniqueId());
    }
}
