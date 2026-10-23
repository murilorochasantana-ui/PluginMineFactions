package br.com.murilo.muriloplug.classes.engenheiro;

import br.com.murilo.muriloplug.claim.ClaimManager;
import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.combat.CombatManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Construções abertas dos Engenheiros: marcar a área (chão vira concreto branco), salvar na estrutura, cancelar e
 * encerrar sozinhas. O chão original é sempre devolvido ao terminar; enquanto a construção está aberta ela fica em
 * construcoes-abertas.yml, e o que sobrar de um desligamento brusco é devolvido no onEnable.
 */
public final class BuildManager {

    /** Resultado de olhar o volume da construção: quantos blocos, a menor caixa que os contém e os proibidos. */
    record Scan(int count, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Set<Material> forbidden) {
        int sizeX() {
            return maxX - minX + 1;
        }

        int sizeY() {
            return maxY - minY + 1;
        }

        int sizeZ() {
            return maxZ - minZ + 1;
        }

        String sizeText() {
            return count == 0 ? "0x0x0" : sizeX() + "x" + sizeY() + "x" + sizeZ();
        }
    }

    private static final Set<Material> ERASABLE = EnumSet.of(Material.SHORT_GRASS, Material.TALL_GRASS, Material.FERN,
            Material.LARGE_FERN, Material.DEAD_BUSH, Material.SNOW, Material.BROWN_MUSHROOM, Material.RED_MUSHROOM);
    private static final Set<Material> TALL_PLANTS = EnumSet.of(Material.TALL_GRASS, Material.LARGE_FERN,
            Material.TALL_SEAGRASS, Material.SUNFLOWER, Material.LILAC, Material.ROSE_BUSH, Material.PEONY,
            Material.PITCHER_PLANT);

    private static final int BORDER_PULSES = 10;

    private final JavaPlugin plugin;
    private final ClassManager classes;
    private final ClaimManager claims;
    private final CombatManager combat;
    private final EngenheiroSettings settings;
    private final StructureBooks books;
    private final StructureStore store;
    private final File file;
    private final Map<UUID, OpenBuild> builds = new LinkedHashMap<>();
    private final Map<UUID, BukkitTask> borderTasks = new HashMap<>();
    private PlantaManager plantas;

    public BuildManager(JavaPlugin plugin, ClassManager classes, ClaimManager claims, CombatManager combat,
                        EngenheiroSettings settings, StructureBooks books, StructureStore store) {
        this.plugin = plugin;
        this.classes = classes;
        this.claims = claims;
        this.combat = combat;
        this.settings = settings;
        this.books = books;
        this.store = store;
        this.file = new File(plugin.getDataFolder(), "construcoes-abertas.yml");
    }

    /** Liga a Planta (ela precisa deste gerenciador para saber quais livros estão presos a uma construção). */
    public void setPlantas(PlantaManager plantas) {
        this.plantas = plantas;
    }

    // ------------------------------------------------------------ consultas

    public boolean isEngineer(Player player) {
        return classes.hasClass(player.getUniqueId(), PlayerClass.ENGENHEIRO);
    }

    /** Se a caixa encosta (ou sobrepõe) em alguma construção aberta. */
    boolean overlapsOpenBuild(World world, int minX, int maxX, int minZ, int maxZ, int minY, int maxY) {
        for (OpenBuild build : builds.values()) {
            if (build.touches(world.getName(), minX, maxX, minZ, maxZ, minY, maxY, 0)) {
                return true;
            }
        }
        return false;
    }

    boolean hasAny() {
        return !builds.isEmpty();
    }

    OpenBuild ofOwner(UUID owner) {
        return builds.get(owner);
    }

    OpenBuild ofBook(UUID bookId) {
        for (OpenBuild build : builds.values()) {
            if (build.bookId.equals(bookId)) {
                return build;
            }
        }
        return null;
    }

    /** Construção aberta que ocupa este bloco (chão ou volume), ou null. */
    OpenBuild at(Block block) {
        return at(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    OpenBuild at(World world, int x, int y, int z) {
        for (OpenBuild build : builds.values()) {
            if (build.contains(world, x, y, z)) {
                return build;
            }
        }
        return null;
    }

    static boolean isErasable(Material type) {
        return type.isAir() || ERASABLE.contains(type) || Tag.FLOWERS.isTagged(type) || Tag.SAPLINGS.isTagged(type);
    }

    /** Chão que não dá para trocar e devolver igual (baús, camas, portas, plantas altas). */
    private static boolean isSpecialFloor(Block block) {
        Material type = block.getType();
        return block.getState(false) instanceof TileState || Tag.BEDS.isTagged(type) || Tag.DOORS.isTagged(type)
                || TALL_PLANTS.contains(type);
    }

    // ------------------------------------------------------------ marcar a área

    /** Clique direito com um livro vazio sem construção ligada: valida e marca a área. */
    public void mark(Player player, ItemStack hand) {
        UUID id = player.getUniqueId();
        if (builds.containsKey(id)) {
            player.sendMessage("§cVocê já tem uma construção aberta. Termine ou cancele a atual primeiro.");
            return;
        }
        int wait = combat.secondsUntilCalm(player, settings.combatWaitSeconds());
        if (wait > 0) {
            player.sendMessage("§cVocê esteve em combate. Aguarde " + wait + "s para construir.");
            return;
        }
        if (!player.isOnGround()) {
            player.sendMessage("§cFique parado no chão para marcar a área.");
            return;
        }

        World world = player.getWorld();
        Location loc = player.getLocation();
        float yaw = (loc.getYaw() % 360 + 360) % 360;
        int fx;
        int fz;
        if (yaw >= 45 && yaw < 135) {
            fx = -1;
            fz = 0;
        } else if (yaw >= 135 && yaw < 225) {
            fx = 0;
            fz = -1;
        } else if (yaw >= 225 && yaw < 315) {
            fx = 1;
            fz = 0;
        } else {
            fx = 0;
            fz = 1;
        }
        int rx = -fz;
        int rz = fx;
        int size = settings.areaSize();
        int height = settings.areaHeight();
        int x0 = loc.getBlockX();
        int z0 = loc.getBlockZ();
        int floorY = loc.getBlockY() - 1;
        int x1 = x0 + (fx + rx) * (size - 1);
        int z1 = z0 + (fz + rz) * (size - 1);
        int minX = Math.min(x0, x1);
        int maxX = Math.max(x0, x1);
        int minZ = Math.min(z0, z1);
        int maxZ = Math.max(z0, z1);

        if (floorY < world.getMinHeight() || floorY + height >= world.getMaxHeight()) {
            player.sendMessage("§cEsta altura não comporta a área de construção.");
            return;
        }
        for (OpenBuild other : builds.values()) {
            if (other.touches(world.getName(), minX, maxX, minZ, maxZ, floorY, floorY + height, 1)) {
                player.sendMessage("§cEsta área encosta em uma construção aberta de outro Engenheiro.");
                return;
            }
        }

        // Valida tudo antes de mexer em qualquer bloco.
        boolean claimBlocked = false;
        boolean specialFloor = false;
        boolean notEmpty = false;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Block floorBlock = world.getBlockAt(x, floorY, z);
                if (!claims.canBuild(player, floorBlock.getLocation())) {
                    claimBlocked = true;
                }
                if (isSpecialFloor(floorBlock)) {
                    specialFloor = true;
                }
                for (int y = floorY + 1; y <= floorY + height && !notEmpty; y++) {
                    if (!isErasable(world.getBlockAt(x, y, z).getType())) {
                        notEmpty = true;
                    }
                }
            }
        }
        if (claimBlocked) {
            player.sendMessage("§cVocê não pode construir aqui: parte da área pertence a outro jogador.");
            return;
        }
        if (specialFloor) {
            player.sendMessage("§cO chão tem baús, portas, camas ou plantas altas. Escolha outro lugar.");
            return;
        }
        if (notEmpty) {
            player.sendMessage("§cEscolha um lugar plano e vazio.");
            return;
        }

        // Tudo certo: guarda o chão original, troca por concreto branco e apaga plantas/neve (sem drop).
        List<String> floor = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Block floorBlock = world.getBlockAt(x, floorY, z);
                floor.add(x + " " + z + " " + floorBlock.getBlockData().getAsString());
                floorBlock.setType(Material.WHITE_CONCRETE, false);
                for (int y = floorY + 1; y <= floorY + height; y++) {
                    Block above = world.getBlockAt(x, y, z);
                    if (!above.getType().isAir()) {
                        above.setType(Material.AIR, false);
                    }
                }
            }
        }

        // O livro usado ganha um UUID novo (a loja entrega cópias iguais) e fica ligado a esta construção.
        ItemStack unique = books.createEmpty();
        if (hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
            player.getInventory().addItem(unique).values()
                    .forEach(left -> world.dropItem(player.getLocation(), left));
        } else {
            player.getInventory().setItemInMainHand(unique);
        }
        OpenBuild build = new OpenBuild(id, player.getName(), books.bookId(unique), world.getName(), floorY,
                minX, maxX, minZ, maxZ, height, System.currentTimeMillis(), floor);
        builds.put(id, build);
        saveFile();
        showBorder(player, build);
        player.sendMessage("§aÁrea de construção marcada! Construa em cima do concreto branco. "
                + "Clique de novo com o livro para salvar.");
    }

    private void showBorder(Player player, OpenBuild build) {
        stopBorder(build.owner);
        double x0 = build.minX;
        double x1 = build.maxX + 1.0;
        double z0 = build.minZ;
        double z1 = build.maxZ + 1.0;
        double y0 = build.floorY + 1.0;
        double y1 = build.topY() + 1.0;
        BukkitRunnable task = new BukkitRunnable() {
            private int pulses;

            @Override
            public void run() {
                if (!player.isOnline() || pulses++ >= BORDER_PULSES) {
                    cancel();
                    return;
                }
                for (double y : new double[]{y0, y1}) {
                    line(player, x0, y, z0, x1, y, z0);
                    line(player, x0, y, z1, x1, y, z1);
                    line(player, x0, y, z0, x0, y, z1);
                    line(player, x1, y, z0, x1, y, z1);
                }
                line(player, x0, y0, z0, x0, y1, z0);
                line(player, x1, y0, z0, x1, y1, z0);
                line(player, x0, y0, z1, x0, y1, z1);
                line(player, x1, y0, z1, x1, y1, z1);
            }
        };
        borderTasks.put(build.owner, task.runTaskTimer(plugin, 0L, 10L));
    }

    private static void line(Player player, double ax, double ay, double az, double bx, double by, double bz) {
        double length = Math.max(Math.abs(bx - ax), Math.max(Math.abs(by - ay), Math.abs(bz - az)));
        int steps = (int) Math.ceil(length);
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : (double) i / steps;
            player.spawnParticle(Particle.END_ROD, ax + (bx - ax) * t, ay + (by - ay) * t, az + (bz - az) * t,
                    1, 0, 0, 0, 0);
        }
    }

    private void stopBorder(UUID owner) {
        BukkitTask task = borderTasks.remove(owner);
        if (task != null) {
            task.cancel();
        }
    }

    // ------------------------------------------------------------ olhar o volume

    Scan scan(OpenBuild build) {
        World world = Bukkit.getWorld(build.world);
        Set<Material> forbiddenFound = EnumSet.noneOf(Material.class);
        if (world == null) {
            return new Scan(0, 0, 0, 0, 0, 0, 0, forbiddenFound);
        }
        Predicate<Material> forbidden = settings.forbidden();
        int count = 0;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int x = build.minX; x <= build.maxX; x++) {
            for (int z = build.minZ; z <= build.maxZ; z++) {
                for (int y = build.floorY + 1; y <= build.topY(); y++) {
                    Material type = world.getBlockAt(x, y, z).getType();
                    if (type.isAir()) {
                        continue;
                    }
                    count++;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    minZ = Math.min(minZ, z);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    maxZ = Math.max(maxZ, z);
                    if (forbidden.test(type)) {
                        forbiddenFound.add(type);
                    }
                }
            }
        }
        return count == 0 ? new Scan(0, 0, 0, 0, 0, 0, 0, forbiddenFound)
                : new Scan(count, minX, minY, minZ, maxX, maxY, maxZ, forbiddenFound);
    }

    // ------------------------------------------------------------ salvar

    /**
     * Valida o salvar (livro vazio desta construção na mão, combate, blocos proibidos) e olha o volume. Retorna o
     * resultado, ou null depois de avisar o jogador do que está errado.
     */
    private Scan checkSave(Player player, OpenBuild build) {
        if (!isEngineer(player)) {
            player.sendMessage("§cVocê não é mais Engenheiro.");
            return null;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!books.isEmpty(hand) || !build.bookId.equals(books.bookId(hand))) {
            player.sendMessage("§cVocê precisa estar com o livro desta construção na mão principal.");
            return null;
        }
        int wait = combat.secondsUntilCalm(player, settings.combatWaitSeconds());
        if (wait > 0) {
            player.sendMessage("§cVocê esteve em combate. Aguarde " + wait + "s para construir.");
            return null;
        }
        if (Bukkit.getWorld(build.world) == null) {
            player.sendMessage("§cO mundo da construção não está carregado.");
            return null;
        }
        Scan scan = scan(build);
        if (scan.count() == 0) {
            player.sendMessage("§cConstrua pelo menos 1 bloco acima do concreto branco.");
            return null;
        }
        if (!scan.forbidden().isEmpty()) {
            player.sendMessage("§cHá blocos proibidos na construção: " + scan.forbidden().stream()
                    .map(Material::name).sorted().toList());
            return null;
        }
        return scan;
    }

    /** Guarda a menor caixa que contém os blocos (sem entidades, com o conteúdo de baús e ejetores), ou null. */
    private Structure capture(Player player, World world, Scan scan) {
        Location corner1 = new Location(world, scan.minX(), scan.minY(), scan.minZ());
        Location corner2 = new Location(world, scan.maxX(), scan.maxY(), scan.maxZ());
        try {
            Structure structure = Bukkit.getStructureManager().createStructure();
            structure.fill(corner1, corner2, false);
            if (!hasSize(structure, scan)) {
                structure = Bukkit.getStructureManager().createStructure();
                structure.fill(corner1, new BlockVector(scan.sizeX(), scan.sizeY(), scan.sizeZ()), false);
            }
            if (!hasSize(structure, scan)) {
                player.sendMessage("§cNão foi possível guardar a estrutura (tamanho inesperado). Nada foi alterado.");
                plugin.getLogger().warning("Estrutura com tamanho inesperado: " + structure.getSize()
                        + " (esperado " + scan.sizeText() + ")");
                return null;
            }
            return structure;
        } catch (RuntimeException e) {
            player.sendMessage("§cNão foi possível guardar a estrutura. Nada foi alterado.");
            plugin.getLogger().severe("Falha ao guardar estrutura de " + player.getName() + ": " + e);
            return null;
        }
    }

    /** Salva a construção do jogador no livro (depois da confirmação). Tudo no mesmo tick. */
    public void save(Player player) {
        OpenBuild build = builds.get(player.getUniqueId());
        if (build == null) {
            player.sendMessage("§cVocê não tem uma construção aberta.");
            return;
        }
        Scan scan = checkSave(player, build);
        if (scan == null) {
            return;
        }
        World world = Bukkit.getWorld(build.world);

        // Grava o arquivo ANTES de apagar qualquer coisa.
        String structureId = UUID.randomUUID().toString();
        Structure structure = capture(player, world, scan);
        if (structure == null) {
            return;
        }
        try {
            store.save(structureId, structure);
        } catch (IOException | RuntimeException e) {
            player.sendMessage("§cNão foi possível salvar a estrutura. Nada foi alterado.");
            plugin.getLogger().severe("Falha ao salvar estrutura de " + player.getName() + ": " + e);
            return;
        }

        clearBlocks(world, build, scan);
        restoreFloor(build);
        builds.remove(build.owner);
        stopBorder(build.owner);
        saveFile();
        player.getInventory().setItemInMainHand(books.createFull(build.bookId, structureId,
                scan.sizeX(), scan.sizeY(), scan.sizeZ(), scan.count(), player.getName()));
        player.sendMessage("§aEstrutura salva no livro!");
    }

    /** Se há uma Planta com espaço para guardar um projeto (para o botão do menu Construção). */
    boolean plantaAvailable(Player player) {
        return plantas != null && plantas.hasPlantaWithSpace(player);
    }

    /**
     * Guarda a construção como projeto de uma Planta, com o nome dado. Os blocos FICAM no mundo; o chão volta e a
     * área é fechada, como no cancelar. O livro vazio continua com o jogador.
     */
    public void saveProject(Player player, String name) {
        OpenBuild build = builds.get(player.getUniqueId());
        if (build == null) {
            player.sendMessage("§cVocê não tem uma construção aberta.");
            return;
        }
        Scan scan = checkSave(player, build);
        if (scan == null) {
            return;
        }
        Structure structure = capture(player, Bukkit.getWorld(build.world), scan);
        if (structure == null || plantas == null
                || !plantas.saveProject(player, name, scan.sizeText(), scan.count(), structure)) {
            return;
        }
        restoreFloor(build);
        builds.remove(build.owner);
        stopBorder(build.owner);
        saveFile();
        player.sendMessage("§aProjeto \"" + name + "\" guardado na Planta! Os blocos ficaram no mundo.");
    }

    private static boolean hasSize(Structure structure, Scan scan) {
        BlockVector size = structure.getSize();
        return size.getBlockX() == scan.sizeX() && size.getBlockY() == scan.sizeY() && size.getBlockZ() == scan.sizeZ();
    }

    /** Apaga todos os blocos construídos sem drop e sem física; o conteúdo dos baús vai junto (já está na estrutura). */
    private void clearBlocks(World world, OpenBuild build, Scan scan) {
        List<Block> dependent = new ArrayList<>();
        List<Block> solid = new ArrayList<>();
        for (int x = scan.minX(); x <= scan.maxX(); x++) {
            for (int z = scan.minZ(); z <= scan.maxZ(); z++) {
                for (int y = scan.minY(); y <= scan.maxY(); y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType().isAir()) {
                        continue;
                    }
                    if (block.getState(false) instanceof Container container) {
                        container.getInventory().clear();
                    }
                    // Tochas, redstone, botões, trilhos, água etc. primeiro; depois os blocos que os seguram.
                    (block.getType().isOccluding() ? solid : dependent).add(block);
                }
            }
        }
        for (Block block : dependent) {
            block.setType(Material.AIR, false);
        }
        for (Block block : solid) {
            block.setType(Material.AIR, false);
        }
    }

    // ------------------------------------------------------------ cancelar / encerrar

    /** Cancela a construção: os blocos ficam, o chão volta ao original e a proteção sai. */
    public void cancel(UUID owner, String message) {
        OpenBuild build = builds.remove(owner);
        if (build == null) {
            return;
        }
        restoreFloor(build);
        stopBorder(owner);
        saveFile();
        Player player = Bukkit.getPlayer(owner);
        if (player != null && message != null) {
            player.sendMessage(message);
        }
    }

    /** Devolve o chão original (sempre tem que voltar). */
    private void restoreFloor(OpenBuild build) {
        World world = Bukkit.getWorld(build.world);
        if (world == null) {
            plugin.getLogger().warning("Mundo '" + build.world + "' não carregado: não foi possível devolver o chão.");
            return;
        }
        for (String entry : build.floor) {
            String[] parts = entry.split(" ", 3);
            Block block = world.getBlockAt(Integer.parseInt(parts[0]), build.floorY, Integer.parseInt(parts[1]));
            try {
                BlockData data = Bukkit.createBlockData(parts[2]);
                block.setBlockData(data, false);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Chão inválido ignorado (" + entry + "): " + e.getMessage());
                block.setType(Material.DIRT, false);
            }
        }
    }

    /** Vigia o tempo máximo das construções abertas (a cada 20 segundos). */
    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long limit = settings.maxMinutes() * 60_000L;
            long now = System.currentTimeMillis();
            for (OpenBuild build : List.copyOf(builds.values())) {
                if (now - build.openedAt >= limit) {
                    cancel(build.owner, "§eA construção ficou aberta por tempo demais e foi cancelada. "
                            + "Os blocos ficaram no lugar.");
                }
            }
        }, 400L, 400L);
    }

    /** Cancela todas as construções abertas (onDisable). */
    public void cancelAll() {
        for (UUID owner : List.copyOf(builds.keySet())) {
            cancel(owner, null);
        }
    }

    // ------------------------------------------------------------ arquivo

    private void saveFile() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (OpenBuild build : builds.values()) {
            String path = "construcoes." + build.owner + ".";
            yaml.set(path + "nome", build.ownerName);
            yaml.set(path + "livro", build.bookId.toString());
            yaml.set(path + "mundo", build.world);
            yaml.set(path + "chao-y", build.floorY);
            yaml.set(path + "min-x", build.minX);
            yaml.set(path + "max-x", build.maxX);
            yaml.set(path + "min-z", build.minZ);
            yaml.set(path + "max-z", build.maxZ);
            yaml.set(path + "altura", build.height);
            yaml.set(path + "aberta-em", build.openedAt);
            yaml.set(path + "chao", build.floor);
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar construcoes-abertas.yml: " + e.getMessage());
        }
    }

    /** No onEnable: devolve o chão de qualquer construção que ficou aberta (ex: o servidor caiu). */
    public void restoreLeftovers() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("construcoes");
        if (section == null) {
            return;
        }
        int restored = 0;
        boolean pending = false;
        YamlConfiguration remaining = new YamlConfiguration();
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            try {
                OpenBuild build = new OpenBuild(UUID.fromString(key), s.getString("nome", "?"),
                        UUID.fromString(s.getString("livro", "")), s.getString("mundo", ""), s.getInt("chao-y"),
                        s.getInt("min-x"), s.getInt("max-x"), s.getInt("min-z"), s.getInt("max-z"),
                        s.getInt("altura"), s.getLong("aberta-em"), s.getStringList("chao"));
                if (Bukkit.getWorld(build.world) == null) {
                    // Mundo ainda não carregado: mantém no arquivo para tentar no próximo início.
                    remaining.set("construcoes." + key, s.getValues(false));
                    pending = true;
                    continue;
                }
                restoreFloor(build);
                restored++;
            } catch (RuntimeException e) {
                plugin.getLogger().severe("construcoes-abertas.yml: entrada inválida (" + key + "): " + e);
            }
        }
        try {
            if (pending) {
                remaining.save(file);
            } else {
                deleteFile();
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível atualizar construcoes-abertas.yml: " + e.getMessage());
        }
        if (restored > 0) {
            plugin.getLogger().info("Chão devolvido em " + restored + " construção(ões) que ficou(aram) aberta(s).");
        }
    }

    private void deleteFile() {
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Não foi possível apagar construcoes-abertas.yml.");
        }
    }
}
