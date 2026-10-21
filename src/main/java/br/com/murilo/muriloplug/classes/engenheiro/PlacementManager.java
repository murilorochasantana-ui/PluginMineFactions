package br.com.murilo.muriloplug.classes.engenheiro;

import br.com.murilo.muriloplug.claim.ClaimManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.PlayerClassChangeEvent;
import br.com.murilo.muriloplug.combat.CombatManager;
import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.structure.Palette;
import org.bukkit.structure.Structure;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.BlockVector;
import org.bukkit.util.RayTraceResult;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Colocar a estrutura de um Livro de Estrutura cheio: prévia só visual (sendBlockChange, nada muda no mundo),
 * validação, e a colocação em si. O livro só gera a estrutura UMA vez: a regra é o arquivo .nbt, que é apagado
 * na hora de colocar (sem arquivo, sem estrutura).
 */
public final class PlacementManager implements Listener {

    private static final int RANGE = 15;
    private static final long PERIOD = 5L;
    private static final String HINT = "§eClique direito para confirmar o local | Shift + clique direito para girar";

    private record Pos(int x, int y, int z) {
    }

    /** Um bloco da estrutura (sem ar), na posição relativa original. */
    private record Piece(int x, int y, int z, BlockData data) {
    }

    private static final class Preview {
        final UUID owner;
        final UUID bookId;
        final String structureId;
        final String world;
        final Structure structure;
        final List<Piece> pieces;
        final int sizeX;
        final int sizeY;
        final int sizeZ;
        StructureRotation rotation = StructureRotation.NONE;
        Pos anchor;
        boolean dirty = true;
        /** Com o menu de confirmação aberto, a prévia fica parada no lugar escolhido. */
        boolean frozen;
        /** O que o jogador está vendo de falso agora, por posição. */
        Map<Pos, BlockData> shown = new HashMap<>();

        Preview(UUID owner, UUID bookId, String structureId, String world, Structure structure, List<Piece> pieces) {
            this.owner = owner;
            this.bookId = bookId;
            this.structureId = structureId;
            this.world = world;
            this.structure = structure;
            this.pieces = pieces;
            BlockVector size = structure.getSize();
            this.sizeX = size.getBlockX();
            this.sizeY = size.getBlockY();
            this.sizeZ = size.getBlockZ();
        }

        boolean turned() {
            return rotation == StructureRotation.CLOCKWISE_90 || rotation == StructureRotation.COUNTERCLOCKWISE_90;
        }

        int rotatedX() {
            return turned() ? sizeZ : sizeX;
        }

        int rotatedZ() {
            return turned() ? sizeX : sizeZ;
        }
    }

    private final JavaPlugin plugin;
    private final BuildManager builds;
    private final ClaimManager claims;
    private final CombatManager combat;
    private final EngenheiroSettings settings;
    private final StructureBooks books;
    private final StructureStore store;
    private final Map<UUID, Preview> previews = new HashMap<>();

    public PlacementManager(JavaPlugin plugin, BuildManager builds, ClaimManager claims, CombatManager combat,
                            EngenheiroSettings settings, StructureBooks books, StructureStore store) {
        this.plugin = plugin;
        this.builds = builds;
        this.claims = claims;
        this.combat = combat;
        this.settings = settings;
        this.books = books;
        this.store = store;
    }

    /** Inicia o relógio da prévia (segue a mira a cada 5 ticks). */
    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, PERIOD, PERIOD);
    }

    /** Apaga todas as prévias (onDisable). */
    public void clearAll() {
        for (Preview preview : List.copyOf(previews.values())) {
            clear(preview);
        }
    }

    // ------------------------------------------------------------ uso do livro

    /** Clique direito com um livro cheio: inicia a prévia, gira (Shift) ou pede a confirmação. */
    public void onUse(Player player, ItemStack item) {
        UUID bookId = books.bookId(item);
        Preview preview = previews.get(player.getUniqueId());
        if (preview != null && !preview.bookId.equals(bookId)) {
            clear(preview);
            preview = null;
        }
        if (preview == null) {
            startPreview(player, bookId, books.structureId(item));
        } else if (preview.frozen) {
            return;
        } else if (player.isSneaking()) {
            preview.rotation = next(preview.rotation);
            preview.dirty = true;
            refresh(player, preview);
        } else if (preview.anchor == null) {
            player.sendMessage("§cMire em um bloco (até " + RANGE + " blocos) para escolher o local.");
        } else {
            preview.frozen = true;
            PlaceConfirmMenu.open(player, preview.rotatedX() + "x" + preview.sizeY + "x" + preview.rotatedZ(),
                    preview.pieces.size());
        }
    }

    private void startPreview(Player player, UUID bookId, String structureId) {
        int wait = combat.secondsUntilCalm(player, settings.combatWaitSeconds());
        if (wait > 0) {
            player.sendMessage("§cVocê esteve em combate. Aguarde " + wait + "s para construir.");
            return;
        }
        File file = structureId == null ? null : store.file(structureId);
        if (file == null || !file.isFile()) {
            player.sendMessage("§cEste livro está corrompido ou já foi usado.");
            return;
        }
        Structure structure;
        try {
            structure = store.load(structureId);
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível carregar a estrutura " + structureId, e);
            player.sendMessage("§cEste livro está corrompido ou já foi usado.");
            return;
        }
        if (structure == null || structure.getPaletteCount() == 0) {
            player.sendMessage("§cEste livro está corrompido ou já foi usado.");
            return;
        }
        List<Piece> pieces = new ArrayList<>();
        Palette palette = structure.getPalettes().get(0);
        for (BlockState state : palette.getBlocks()) {
            BlockData data = state.getBlockData();
            if (!data.getMaterial().isAir()) {
                pieces.add(new Piece(state.getX(), state.getY(), state.getZ(), data));
            }
        }
        Preview preview = new Preview(player.getUniqueId(), bookId, structureId, player.getWorld().getName(),
                structure, pieces);
        previews.put(player.getUniqueId(), preview);
        refresh(player, preview);
    }

    private static StructureRotation next(StructureRotation rotation) {
        return switch (rotation) {
            case NONE -> StructureRotation.CLOCKWISE_90;
            case CLOCKWISE_90 -> StructureRotation.CLOCKWISE_180;
            case CLOCKWISE_180 -> StructureRotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> StructureRotation.NONE;
        };
    }

    // ------------------------------------------------------------ geometria

    private static int turnX(StructureRotation rotation, int x, int z) {
        return switch (rotation) {
            case NONE -> x;
            case CLOCKWISE_90 -> -z;
            case CLOCKWISE_180 -> -x;
            case COUNTERCLOCKWISE_90 -> z;
        };
    }

    private static int turnZ(StructureRotation rotation, int x, int z) {
        return switch (rotation) {
            case NONE -> z;
            case CLOCKWISE_90 -> x;
            case CLOCKWISE_180 -> -z;
            case COUNTERCLOCKWISE_90 -> -x;
        };
    }

    /** Menor X e Z da estrutura depois de girar (a estrutura gira em volta da sua origem). */
    private static int[] minTurned(Preview preview) {
        int maxX = preview.sizeX - 1;
        int maxZ = preview.sizeZ - 1;
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (int[] corner : new int[][]{{0, 0}, {maxX, 0}, {0, maxZ}, {maxX, maxZ}}) {
            minX = Math.min(minX, turnX(preview.rotation, corner[0], corner[1]));
            minZ = Math.min(minZ, turnZ(preview.rotation, corner[0], corner[1]));
        }
        return new int[]{minX, minZ};
    }

    /** Onde cada bloco da estrutura cai no mundo (canto mínimo da estrutura girada = âncora), já com a rotação. */
    private Map<Pos, BlockData> layout(Preview preview, Pos anchor) {
        int[] min = minTurned(preview);
        Map<Pos, BlockData> result = new HashMap<>();
        for (Piece piece : preview.pieces) {
            BlockData data = piece.data().clone();
            data.rotate(preview.rotation);
            result.put(new Pos(
                    anchor.x() + turnX(preview.rotation, piece.x(), piece.z()) - min[0],
                    anchor.y() + piece.y(),
                    anchor.z() + turnZ(preview.rotation, piece.x(), piece.z()) - min[1]), data);
        }
        return result;
    }

    // ------------------------------------------------------------ prévia (só visual)

    private void tick() {
        for (Preview preview : List.copyOf(previews.values())) {
            Player player = Bukkit.getPlayer(preview.owner);
            if (player == null || !player.isOnline() || player.isDead()
                    || !player.getWorld().getName().equals(preview.world) || !holdsBook(player, preview)) {
                clear(preview);
                continue;
            }
            if (!preview.frozen) {
                refresh(player, preview);
            }
            player.sendActionBar(ShopUi.text(HINT));
        }
    }

    private boolean holdsBook(Player player, Preview preview) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        return books.isFull(hand) && preview.bookId.equals(books.bookId(hand));
    }

    /** Segue a mira e redesenha só o que mudou. */
    private void refresh(Player player, Preview preview) {
        Pos anchor = null;
        RayTraceResult hit = player.rayTraceBlocks(RANGE, FluidCollisionMode.NEVER);
        if (hit != null && hit.getHitBlock() != null) {
            Block target = hit.getHitBlock();
            anchor = new Pos(target.getX(), target.getY() + 1, target.getZ());
        }
        if (!preview.dirty && java.util.Objects.equals(anchor, preview.anchor)) {
            return;
        }
        preview.anchor = anchor;
        preview.dirty = false;
        draw(player, preview, anchor == null ? Map.of() : layout(preview, anchor));
    }

    /** Troca o que o jogador vê: manda os blocos reais de volta onde saiu e os falsos onde entrou. */
    private void draw(Player player, Preview preview, Map<Pos, BlockData> desired) {
        World world = player.getWorld();
        for (Pos pos : preview.shown.keySet()) {
            if (!desired.containsKey(pos)) {
                player.sendBlockChange(new Location(world, pos.x(), pos.y(), pos.z()),
                        world.getBlockAt(pos.x(), pos.y(), pos.z()).getBlockData());
            }
        }
        for (Map.Entry<Pos, BlockData> entry : desired.entrySet()) {
            if (!entry.getValue().equals(preview.shown.get(entry.getKey()))) {
                Pos pos = entry.getKey();
                player.sendBlockChange(new Location(world, pos.x(), pos.y(), pos.z()), entry.getValue());
            }
        }
        preview.shown = new HashMap<>(desired);
    }

    /** Apaga a prévia: cada posição mostrada volta a ser o bloco real. */
    private void clear(Preview preview) {
        previews.remove(preview.owner);
        Player player = Bukkit.getPlayer(preview.owner);
        if (player != null && player.isOnline() && player.getWorld().getName().equals(preview.world)) {
            draw(player, preview, Map.of());
        }
    }

    /** Botão vermelho: cancela a prévia (o livro continua cheio). */
    void cancelPreview(Player player) {
        Preview preview = previews.get(player.getUniqueId());
        if (preview != null) {
            clear(preview);
        }
        player.sendMessage("§ePrévia cancelada. O livro continua cheio.");
    }

    /** Fechou o menu sem escolher (ESC): a prévia volta a seguir a mira. */
    void unfreeze(Player player) {
        Preview preview = previews.get(player.getUniqueId());
        if (preview != null) {
            preview.frozen = false;
        }
    }

    // ------------------------------------------------------------ colocar

    private static boolean isFree(Material type) {
        return BuildManager.isErasable(type) || type == Material.WATER || type == Material.SEAGRASS
                || type == Material.TALL_SEAGRASS || type == Material.KELP || type == Material.KELP_PLANT;
    }

    /** Botão verde: valida tudo de novo e coloca. Retorna sem fazer nada (e explicando) se algo não servir. */
    void confirm(Player player) {
        Preview preview = previews.get(player.getUniqueId());
        if (preview == null) {
            return;
        }
        preview.frozen = false;
        String error = validate(player, preview);
        if (error != null) {
            player.sendMessage(error);
            return;
        }
        World world = player.getWorld();
        Pos anchor = preview.anchor;
        int[] min = minTurned(preview);
        Location where = new Location(world, anchor.x() - min[0], anchor.y(), anchor.z() - min[1]);

        // a) o livro sai do inventário (é o mesmo livro: já conferido na validação)
        player.getInventory().setItemInMainHand(null);
        // b) o arquivo é apagado: sem arquivo, sem estrutura (livro de uso único)
        File file = store.file(preview.structureId);
        if (file == null || !file.delete()) {
            if (file != null && file.exists()) {
                player.getInventory().setItemInMainHand(books.createFull(preview.bookId, preview.structureId,
                        preview.sizeX, preview.sizeY, preview.sizeZ, preview.pieces.size(), player.getName()));
                player.sendMessage("§cNão foi possível gastar o livro. Nada foi colocado.");
                plugin.getLogger().severe("Não foi possível apagar " + file + "; livro devolvido uma vez.");
                return;
            }
            // O arquivo sumiu entre a validação e agora (não deveria): nada a fazer, o livro já saiu.
        }
        // c) coloca (itens de baús e ejetores vão junto, como foram guardados)
        try {
            preview.structure.place(where, false, preview.rotation, Mirror.NONE, 0, 1.0f, new Random());
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao colocar a estrutura " + preview.structureId + " de "
                    + player.getName() + " em " + where + " (o livro e o arquivo já foram gastos)", e);
            player.sendMessage("§cOcorreu um erro ao colocar a estrutura. Avise um administrador.");
            clear(preview);
            return;
        }
        // d) apaga a prévia (agora os blocos são reais) e avisa
        clear(preview);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
        player.sendMessage("§aEstrutura colocada!");
    }

    /** Mensagem de erro, ou null se pode colocar. */
    private String validate(Player player, Preview preview) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!builds.isEngineer(player)) {
            return "§cVocê não é mais Engenheiro.";
        }
        if (!books.isFull(hand) || !preview.bookId.equals(books.bookId(hand))) {
            return "§cVocê precisa estar com o livro desta estrutura na mão principal.";
        }
        int wait = combat.secondsUntilCalm(player, settings.combatWaitSeconds());
        if (wait > 0) {
            return "§cVocê esteve em combate. Aguarde " + wait + "s para construir.";
        }
        File file = store.file(preview.structureId);
        if (file == null || !file.isFile()) {
            return "§cEste livro está corrompido ou já foi usado.";
        }
        Pos anchor = preview.anchor;
        World world = player.getWorld();
        if (anchor == null || !world.getName().equals(preview.world)) {
            return "§cMire em um bloco (até " + RANGE + " blocos) para escolher o local.";
        }
        int sizeX = preview.rotatedX();
        int sizeZ = preview.rotatedZ();
        if (anchor.y() < world.getMinHeight() || anchor.y() + preview.sizeY > world.getMaxHeight()) {
            return "§cA estrutura não cabe nesta altura do mundo.";
        }
        int maxX = anchor.x() + sizeX - 1;
        int maxZ = anchor.z() + sizeZ - 1;
        int maxY = anchor.y() + preview.sizeY - 1;

        // Toda a caixa precisa estar livre (a estrutura também escreve o ar dela por cima do que existir).
        for (int x = anchor.x(); x <= maxX; x++) {
            for (int z = anchor.z(); z <= maxZ; z++) {
                for (int y = anchor.y(); y <= maxY; y++) {
                    if (!isFree(world.getBlockAt(x, y, z).getType())) {
                        return "§cO local não está livre: há blocos no caminho. Escolha um lugar vazio.";
                    }
                }
                if (!claims.canBuild(player, new Location(world, x, anchor.y(), z))) {
                    return "§cVocê não pode construir aqui: parte da área pertence a outro jogador.";
                }
            }
        }
        if (builds.overlapsOpenBuild(world, anchor.x(), maxX, anchor.z(), maxZ, anchor.y(), maxY)) {
            return "§cO local está dentro de uma área de construção aberta.";
        }
        // Ninguém pode ficar preso dentro de um bloco sólido da estrutura.
        Set<Pos> solid = new HashSet<>();
        for (Map.Entry<Pos, BlockData> entry : layout(preview, anchor).entrySet()) {
            if (entry.getValue().getMaterial().isSolid()) {
                solid.add(entry.getKey());
            }
        }
        for (Player other : world.getPlayers()) {
            if (other.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            BoundingBox box = other.getBoundingBox();
            for (int x = (int) Math.floor(box.getMinX()); x <= (int) Math.floor(box.getMaxX() - 1e-7); x++) {
                for (int y = (int) Math.floor(box.getMinY()); y <= (int) Math.floor(box.getMaxY() - 1e-7); y++) {
                    for (int z = (int) Math.floor(box.getMinZ()); z <= (int) Math.floor(box.getMaxZ() - 1e-7); z++) {
                        if (solid.contains(new Pos(x, y, z))) {
                            return "§cHá um jogador dentro do espaço da estrutura. Peça para ele sair.";
                        }
                    }
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------ fim da prévia

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        Preview preview = previews.get(event.getPlayer().getUniqueId());
        if (preview != null) {
            clear(preview);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // A prévia era só visual: não há nada a desfazer no mundo.
        previews.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Preview preview = previews.get(event.getEntity().getUniqueId());
        if (preview != null) {
            clear(preview);
        }
    }

    @EventHandler
    public void onClassChange(PlayerClassChangeEvent event) {
        if (event.getOldClass() == PlayerClass.ENGENHEIRO) {
            Preview preview = previews.get(event.getPlayerId());
            if (preview != null) {
                clear(preview);
            }
        }
    }
}
