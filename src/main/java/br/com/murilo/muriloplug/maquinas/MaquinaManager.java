package br.com.murilo.muriloplug.maquinas;

import br.com.murilo.muriloplug.claim.Claim;
import br.com.murilo.muriloplug.claim.ClaimManager;
import br.com.murilo.muriloplug.classes.ClassManager;
import br.com.murilo.muriloplug.classes.PlayerClass;
import br.com.murilo.muriloplug.classes.engenheiro.EngenheiroAcoes;
import br.com.murilo.muriloplug.classes.engenheiro.EngenheiroEnergia;
import br.com.murilo.muriloplug.clan.Clan;
import br.com.murilo.muriloplug.clan.ClanManager;
import br.com.murilo.muriloplug.clan.base.BaseBlocks;
import br.com.murilo.muriloplug.clan.base.BaseDamageEvent;
import br.com.murilo.muriloplug.clan.base.BaseLoc;
import br.com.murilo.muriloplug.clan.base.ClanBase;
import br.com.murilo.muriloplug.clan.base.ClanBaseManager;
import br.com.murilo.muriloplug.clan.base.ClanChest;
import br.com.murilo.muriloplug.clan.base.ClanChestStore;
import br.com.murilo.muriloplug.combat.CombatManager;
import br.com.murilo.muriloplug.economy.EconomyManager;
import br.com.murilo.muriloplug.economy.vender.SellPrices;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * As máquinas colocadas no mundo: colocar, recolher, abastecer, coletar, upgrades, produção, holograma, dano de raid
 * e o arquivo maquinas-dados.yml (gravado a cada alteração importante, a cada 10s se a produção mudou algo, e no
 * onDisable).
 *
 * <p>Produção: uma tarefa por segundo para TODAS as máquinas, só com os dados (não depende do chunk carregado nem de
 * tick de bloco). Enquanto o status for {@link MaquinaStatus#PRODUZINDO}, o ciclo anda {@code velocidade} segundos por
 * segundo (velocidade global × modo teste × bônus do Engenheiro); a cada ciclo completo gasta combustível (e dinheiro
 * do Cofre, se configurado), sorteia os produtos e guarda no armazém. Parada, o ciclo pausa onde estava.
 */
public final class MaquinaManager implements Listener {

    private static final long TICK = 20L;
    private static final long SAVE_TICKS = 20L * 10;
    private static final long AUTO_INTERVAL_MILLIS = 5_000L;
    private static final int MAX_CICLOS_POR_SEGUNDO = 100;

    private final JavaPlugin plugin;
    private final MaquinasConfig config;
    private final MaquinaItens itens;
    private final MaquinaHologramas hologramas;
    private final ClanManager clans;
    private final ClanBaseManager bases;
    private final ClaimManager claims;
    private final BaseBlocks baseBlocks;
    private final ClanChestStore chests;
    private final SellPrices sellPrices;
    private final CombatManager combat;
    private final ClassManager classes;
    private final EngenheiroEnergia energia;
    private final EngenheiroAcoes acoes;
    private final br.com.murilo.muriloplug.raid.RaidManager raids;
    private final File file;
    private final Map<String, Maquina> maquinas = new LinkedHashMap<>();
    private final Map<BaseLoc, Maquina> porLocal = new HashMap<>();
    /** Itens a entregar quando o jogador entrar (máquina removida pelo admin com o líder offline etc.). */
    private final Map<UUID, List<ItemStack>> entregas = new HashMap<>();
    private boolean sujo;

    public MaquinaManager(JavaPlugin plugin, MaquinasConfig config, MaquinaItens itens, MaquinaHologramas hologramas,
                          ClanManager clans, ClanBaseManager bases, ClaimManager claims, BaseBlocks baseBlocks,
                          ClanChestStore chests, SellPrices sellPrices, CombatManager combat, ClassManager classes,
                          EngenheiroEnergia energia, EngenheiroAcoes acoes,
                          br.com.murilo.muriloplug.raid.RaidManager raids) {
        this.plugin = plugin;
        this.config = config;
        this.itens = itens;
        this.hologramas = hologramas;
        this.clans = clans;
        this.bases = bases;
        this.claims = claims;
        this.baseBlocks = baseBlocks;
        this.chests = chests;
        this.sellPrices = sellPrices;
        this.combat = combat;
        this.classes = classes;
        this.energia = energia;
        this.acoes = acoes;
        this.raids = raids;
        this.file = new File(plugin.getDataFolder(), "maquinas-dados.yml");
    }

    public MaquinasConfig config() {
        return config;
    }

    public MaquinaItens itens() {
        return itens;
    }

    public ClanManager clans() {
        return clans;
    }

    public ClanBaseManager bases() {
        return bases;
    }

    public void start() {
        hologramas.limparSobras();
        baseBlocks.setExtraProtected(this::protegido);
        baseBlocks.addExplosionListener(this::explosao);
        config.addListener(() -> maquinas.values().forEach(this::mudou)); // painel: holograma e menus na hora
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK, TICK);
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (sujo) {
                salvar();
            }
        }, SAVE_TICKS, SAVE_TICKS);
    }

    /** onDisable: grava tudo e tira os hologramas. */
    public void shutdown() {
        // Menus com itens dentro (juntar upgrades) devolvem antes de fechar: os eventos não rodam mais no onDisable.
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu) {
                var devolver = menu.aoFechar;
                menu.aoFechar = null;
                if (devolver != null) {
                    devolver.accept(player);
                }
                player.closeInventory();
            }
        }
        salvar();
        hologramas.removerTodos();
    }

    // ------------------------------------------------------------ consulta

    public Collection<Maquina> todas() {
        return List.copyOf(maquinas.values());
    }

    public Maquina porId(String id) {
        return id == null ? null : maquinas.get(id);
    }

    public Maquina em(Block block) {
        return block == null ? null : porLocal.get(BaseLoc.of(block));
    }

    /** Bloco de uma máquina (protegido como os blocos da base). */
    public boolean protegido(Block block) {
        return em(block) != null;
    }

    public List<Maquina> doClan(String tag) {
        return maquinas.values().stream().filter(maquina -> maquina.clan.equalsIgnoreCase(tag)).toList();
    }

    public int contar(String tag, MaquinaTipo tipo) {
        return (int) maquinas.values().stream()
                .filter(maquina -> maquina.tipo == tipo && maquina.clan.equalsIgnoreCase(tag)).count();
    }

    /** Velocidade do relógio da máquina agora (global × modo teste × Engenheiro). */
    public double velocidade(Maquina maquina) {
        double engenheiro = maquina.engenheiroPerto ? 1 + config.engenheiroBonus() : 1;
        return Math.max(0.0001, config.velocidadeAtual() * engenheiro);
    }

    /** Segundos reais até o próximo ciclo (com a velocidade de agora). */
    public double segundosParaCiclo(Maquina maquina) {
        double falta = Math.max(0, MaquinaCalculos.ciclo(config, maquina) - maquina.progresso);
        return falta / velocidade(maquina);
    }

    public boolean emRaid(Maquina maquina) {
        return raids.underRaid(maquina.clan);
    }

    // ------------------------------------------------------------ colocar

    /** Coloca a máquina no bloco (já posto pelo jogador). Devolve o motivo de não poder, ou null se colocou. */
    public String colocar(Player player, Block block, MaquinaTipo tipo) {
        if (!config.ativa(tipo)) {
            return "§cEste tipo de máquina está desativado no servidor.";
        }
        Clan clan = clans.getClan(player.getUniqueId());
        if (clan == null) {
            return "§cVocê precisa estar em um clan para colocar máquinas.";
        }
        Claim claim = claims.getAt(block.getLocation());
        if (claim == null || !clan.isMember(claim.owner())) {
            return "§cMáquinas só podem ser colocadas dentro de um claim do seu clan.";
        }
        int limite = config.limite(tipo);
        if (limite > 0 && contar(clan.tag(), tipo) >= limite) {
            return "§cSeu clan já tem o máximo de " + limite + " " + config.nome(tipo) + "§c.";
        }
        Maquina maquina = new Maquina(UUID.randomUUID().toString(), tipo, clan.tag(), BaseLoc.of(block), block.getType());
        maquina.vida = config.vida(tipo);
        maquina.ultimaRegeneracao = System.currentTimeMillis();
        registrar(maquina);
        salvar();
        clans.broadcast(clan, "§a" + player.getName() + " colocou uma " + config.nome(tipo) + " §aem "
                + maquina.local.coords() + ".");
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!maquinas.containsKey(maquina.id)) {
                return;
            }
            if (block.getType() != maquina.bloco) {
                // A colocação foi desfeita depois (o item voltou ao jogador): o registro sai sem entregar nada.
                desregistrar(maquina);
                salvar();
                return;
            }
            esvaziarSpawner(block);
            mudou(maquina);
        });
        return null;
    }

    /** O spawner da Máquina de Drops fica vazio (sem tipo de mob). */
    private static void esvaziarSpawner(Block block) {
        if (block.getState(false) instanceof CreatureSpawner spawner) {
            spawner.setSpawnedType(null);
            spawner.update(true, false);
        }
    }

    private void registrar(Maquina maquina) {
        maquinas.put(maquina.id, maquina);
        porLocal.put(maquina.local, maquina);
    }

    /** Tira dos dados, do holograma e fecha os menus dela. NÃO mexe no bloco nem nos itens. */
    private void desregistrar(Maquina maquina) {
        maquinas.remove(maquina.id);
        porLocal.remove(maquina.local);
        hologramas.remover(maquina.id);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu
                    && maquina.id.equals(menu.maquinaId)) {
                player.closeInventory();
            }
        }
    }

    private static void tirarBloco(Maquina maquina) {
        Block block = maquina.local.block();
        if (block != null && block.getType() == maquina.bloco) {
            block.setType(Material.AIR, false);
        }
    }

    // ------------------------------------------------------------ menu: combustível, coleta, upgrades, recolher

    /**
     * Põe combustível do item (até o máximo). Devolve quantos itens foram usados (o chamador tira do cursor/slot), ou
     * -1 se o item não é combustível.
     */
    public int abastecer(Maquina maquina, ItemStack item) {
        double valor = itens.combustivel(item);
        if (valor <= 0) {
            return -1;
        }
        double espaco = MaquinaCalculos.combustivelMaximo(config, maquina) - maquina.combustivel;
        int usar = (int) Math.min(item.getAmount(), Math.floor(espaco / valor + 1.0E-9));
        if (usar <= 0) {
            return 0;
        }
        maquina.combustivel += usar * valor;
        salvar();
        mudou(maquina);
        return usar;
    }

    /**
     * Pega até {@code limite} do produto para o inventário (só o que couber). O armazém é baixado ANTES de dar os
     * itens; se sobrar algo (não deveria), volta para o armazém. Devolve quantos foram entregues.
     */
    public int coletar(Player player, Maquina maquina, Material material, int limite) {
        int guardado = maquina.armazem.getOrDefault(material, 0);
        int cabe = espaco(player.getInventory(), material, Math.min(guardado, limite));
        if (cabe <= 0) {
            return 0;
        }
        guardar(maquina, material, -cabe);
        int sobrou = 0;
        for (ItemStack resto : player.getInventory().addItem(pilhas(material, cabe).toArray(ItemStack[]::new)).values()) {
            sobrou += resto.getAmount();
        }
        if (sobrou > 0) {
            guardar(maquina, material, sobrou);
        }
        salvar();
        mudou(maquina);
        return cabe - sobrou;
    }

    /** Pega tudo o que couber. Devolve quantos itens foram entregues. */
    public int coletarTudo(Player player, Maquina maquina) {
        int total = 0;
        for (Material material : List.copyOf(maquina.armazem.keySet())) {
            total += coletar(player, maquina, material, Integer.MAX_VALUE);
        }
        return total;
    }

    /** Instala o upgrade. Devolve o problema, ou null se instalou (o chamador tira 1 item do cursor). */
    public String instalar(Maquina maquina, Upgrade upgrade) {
        if (!config.upgradePermitido(maquina.tipo, upgrade.tipo())) {
            return "§cO upgrade " + upgrade.tipo().nome() + " não é permitido nesta máquina.";
        }
        if (maquina.upgrade(upgrade.tipo()) != null) {
            return "§cEsta máquina já tem um upgrade de " + upgrade.tipo().nome() + ".";
        }
        if (maquina.upgrades.size() >= config.slots(maquina.tipo)) {
            return "§cNão há slot de upgrade livre.";
        }
        maquina.upgrades.add(upgrade);
        salvar();
        mudou(maquina);
        return null;
    }

    /** Tira o upgrade do slot (o chamador já conferiu o espaço no inventário). */
    public Upgrade retirar(Maquina maquina, int indice) {
        if (indice < 0 || indice >= maquina.upgrades.size()) {
            return null;
        }
        Upgrade upgrade = maquina.upgrades.remove(indice);
        maquina.vida = Math.min(maquina.vida, MaquinaCalculos.vidaMaxima(config, maquina));
        salvar();
        mudou(maquina);
        return upgrade;
    }

    /** Recolhe a máquina (só com o armazém vazio, fora de combate e de raid). Devolve a mensagem. */
    public String recolher(Player player, Maquina maquina) {
        if (!maquinas.containsKey(maquina.id)) {
            return "§cEsta máquina não existe mais.";
        }
        Clan clan = clans.getByTag(maquina.clan);
        if (clan == null || !clan.isMember(player.getUniqueId())) {
            return "§cSó o clan dono pode recolher a máquina.";
        }
        if (maquina.totalArmazem() > 0) {
            return "§cEsvazie o armazém antes de recolher a máquina.";
        }
        if (combat.isInCombat(player)) {
            return "§cVocê não pode recolher a máquina em combate.";
        }
        if (emRaid(maquina)) {
            return "§cA base do clan está sendo atacada: não dá para recolher agora.";
        }
        List<ItemStack> devolver = conteudo(maquina, 1.0, true, true);
        desregistrar(maquina); // sai dos dados antes de entregar os itens
        tirarBloco(maquina);
        salvar();
        dar(player, devolver);
        clans.broadcast(clan, "§e" + player.getName() + " recolheu a " + config.nome(maquina.tipo) + " §ede "
                + maquina.local.coords() + ".");
        return "§aMáquina recolhida (com o combustível e os upgrades).";
    }

    /**
     * Itens de dentro da máquina: {@code fracao} de cada produto e do combustível (em carvão, arredondando para
     * baixo), os upgrades e, se pedido, o item da máquina.
     */
    private List<ItemStack> conteudo(Maquina maquina, double fracao, boolean upgrades, boolean itemMaquina) {
        List<ItemStack> lista = new ArrayList<>();
        maquina.armazem.forEach((material, quantidade) ->
                lista.addAll(pilhas(material, (int) Math.floor(quantidade * fracao + 1.0E-9))));
        lista.addAll(pilhas(Material.COAL, (int) Math.floor(maquina.combustivel * fracao + 1.0E-9)));
        if (upgrades) {
            maquina.upgrades.forEach(upgrade -> lista.add(itens.criarUpgrade(upgrade, 1)));
        }
        if (itemMaquina) {
            lista.add(itens.criarMaquina(maquina.tipo));
        }
        return lista;
    }

    // ------------------------------------------------------------ Engenheiro

    /**
     * Shift + clique direito do Engenheiro do clan: repara a máquina (não durante o raid, não acima do máximo).
     * Devolve false se não era caso de reparo (aí o menu abre normalmente).
     */
    public boolean reparar(Player player, Maquina maquina) {
        if (!classes.hasClass(player.getUniqueId(), PlayerClass.ENGENHEIRO)) {
            return false;
        }
        int max = MaquinaCalculos.vidaMaxima(config, maquina);
        if (maquina.vida >= max) {
            return false;
        }
        if (emRaid(maquina)) {
            player.sendMessage("§cA base está sendo atacada: não dá para reparar a máquina agora.");
            return true;
        }
        int cura = config.engenheiroReparoVida();
        double custo = config.engenheiroReparoEnergia();
        if (cura <= 0) {
            return false;
        }
        if (!energia.gastarEnergia(player.getUniqueId(), custo)) {
            player.sendMessage("§cEnergia insuficiente para reparar (precisa de " + MaquinasConfig.decimal(custo) + ").");
            return true;
        }
        maquina.vida = Math.min(max, maquina.vida + cura);
        Location centro = maquina.local.center();
        if (centro != null) {
            centro.getWorld().spawnParticle(Particle.WAX_OFF, centro, 20, 0.5, 0.5, 0.5, 0);
            centro.getWorld().playSound(centro, Sound.BLOCK_ANVIL_USE, 0.6f, 1.6f);
        }
        player.sendMessage("§a⚙ Máquina reparada: §c❤ " + maquina.vida + "/" + max);
        acoes.registrarAcao(player);
        salvar();
        mudou(maquina);
        return true;
    }

    // ------------------------------------------------------------ raid

    /** Explosão que conta para a base (mesmo raio e atacante do Cofre, pelo {@link BaseBlocks}). */
    private void explosao(Location location, UUID atacante) {
        double raio = baseBlocks.explosionRadius();
        for (Maquina maquina : List.copyOf(maquinas.values())) {
            if (maquinas.containsKey(maquina.id) && maquina.local.distance(location) <= raio) {
                danificar(maquina, atacante, location);
            }
        }
    }

    private void danificar(Maquina maquina, UUID atacante, Location explosao) {
        long agora = System.currentTimeMillis();
        Clan clan = clans.getByTag(maquina.clan);
        if (clan == null || agora < maquina.escudoAte) {
            return; // escudo: a explosão não conta
        }
        int max = MaquinaCalculos.vidaMaxima(config, maquina);
        maquina.vida = Math.min(max, maquina.vida) - 1;
        maquina.ultimoDano = agora;
        maquina.escudoAte = agora + Math.round(config.escudoSegundos(maquina.tipo) * 1000);
        Location centro = maquina.local.center() != null ? maquina.local.center() : explosao;
        if (maquina.vida > 0) {
            salvar();
            centro.getWorld().spawnParticle(Particle.CRIT, centro, 30, 0.5, 0.5, 0.5, 0.2);
            centro.getWorld().playSound(centro, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.7f);
            clans.broadcast(clan, "§cSua " + config.nome(maquina.tipo) + " §cem " + maquina.local.coords()
                    + " tomou dano! (" + maquina.vida + "/" + max + ")");
            mudou(maquina);
            Bukkit.getPluginManager().callEvent(new BaseDamageEvent(maquina.clan, BaseDamageEvent.Kind.MACHINE,
                    centro, atacante, 1, false, 0));
            return;
        }
        destruir(maquina, clan, atacante, centro);
    }

    /** Vida 0: sai dos dados ANTES de qualquer item cair (uma segunda explosão não acha mais a máquina). */
    private void destruir(Maquina maquina, Clan clan, UUID atacante, Location centro) {
        desregistrar(maquina);
        salvar();
        double fracao = config.raidDropArmazem(maquina.tipo);
        int roubados = 0;
        for (int quantidade : maquina.armazem.values()) {
            roubados += (int) Math.floor(quantidade * fracao + 1.0E-9);
        }
        boolean maquinaCaiu = ThreadLocalRandom.current().nextDouble() < config.raidChanceDroparMaquina(maquina.tipo);
        List<ItemStack> drops = conteudo(maquina, fracao, true, maquinaCaiu);
        tirarBloco(maquina);
        centro.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, centro, 1);
        centro.getWorld().playSound(centro, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f);
        drops.forEach(item -> centro.getWorld().dropItemNaturally(centro, item));

        String nome = config.nome(maquina.tipo);
        String limpo = nome.replaceAll("§.", "");
        clans.broadcast(clan, "§c§lSua " + limpo + " em " + maquina.local.coords() + " foi destruída! §c("
                + roubados + " produtos caíram" + (maquinaCaiu ? ", e o item da máquina também" : "") + ")");
        Clan atacanteClan = atacante == null ? null : clans.getClan(atacante);
        if (atacanteClan != null && atacanteClan != clan) {
            clans.broadcast(atacanteClan, "§a" + ClanManager.nameOf(atacante) + " destruiu uma " + limpo + " de ["
                    + clan.tag() + "]! §7(" + roubados + " produtos" + (maquinaCaiu ? " + o item da máquina" : "")
                    + " no chão)");
        }
        String detalhe = "Máquina destruída: " + limpo + ", produtos roubados: " + roubados + ", máquina roubada: "
                + (maquinaCaiu ? "sim" : "não");
        Bukkit.getPluginManager().callEvent(new BaseDamageEvent(maquina.clan, BaseDamageEvent.Kind.MACHINE, centro,
                atacante, 1, true, 0, detalhe));
    }

    // ------------------------------------------------------------ admin

    /** Remove pelo painel: devolve tudo ao líder do clan (agora ou quando ele entrar) ou apaga. */
    public void removerAdmin(Maquina maquina, boolean devolver, String admin) {
        if (!maquinas.containsKey(maquina.id)) {
            return;
        }
        List<ItemStack> itensDentro = conteudo(maquina, 1.0, true, true);
        desregistrar(maquina);
        tirarBloco(maquina);
        salvar();
        Clan clan = clans.getByTag(maquina.clan);
        if (devolver && clan != null) {
            entregar(clan.leader(), itensDentro);
        }
        plugin.getLogger().info("[Maquinas] " + admin + " removeu a máquina " + maquina.tipo + " de [" + maquina.clan
                + "] em " + maquina.local.serialize() + (devolver ? " (itens devolvidos ao líder)" : " (apagada)"));
        if (clan != null) {
            clans.broadcast(clan, "§eA " + config.nome(maquina.tipo) + " §eem " + maquina.local.coords()
                    + " foi removida por um administrador" + (devolver ? " (os itens foram para o líder)." : "."));
        }
    }

    /** Entrega os itens ao jogador (online: no inventário, o que sobrar cai aos pés; offline: quando entrar). */
    public void entregar(UUID jogador, List<ItemStack> lista) {
        if (lista.isEmpty()) {
            return;
        }
        Player online = Bukkit.getPlayer(jogador);
        if (online != null) {
            dar(online, lista);
            online.sendMessage("§eVocê recebeu itens de uma máquina do clan.");
            return;
        }
        entregas.computeIfAbsent(jogador, id -> new ArrayList<>()).addAll(lista);
        salvar();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        List<ItemStack> lista = entregas.remove(player.getUniqueId());
        if (lista == null) {
            return;
        }
        salvar();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                dar(player, lista);
                player.sendMessage("§eVocê recebeu itens de máquinas do clan enquanto estava fora.");
            } else {
                entregar(player.getUniqueId(), lista);
            }
        }, 40L);
    }

    /** Clan desfeito: as máquinas saem do mundo e tudo o que tinham dentro cai no lugar. */
    public void onDisband(Clan clan) {
        for (Maquina maquina : doClan(clan.tag())) {
            semDono(maquina, clan.leader());
        }
    }

    private void semDono(Maquina maquina, UUID lider) {
        List<ItemStack> itensDentro = conteudo(maquina, 1.0, true, true);
        desregistrar(maquina);
        tirarBloco(maquina);
        salvar();
        Location centro = maquina.local.center();
        if (centro != null) {
            itensDentro.forEach(item -> centro.getWorld().dropItemNaturally(centro, item));
        } else if (lider != null) {
            entregar(lider, itensDentro);
        }
    }

    // ------------------------------------------------------------ produção

    private void tick() {
        long agora = System.currentTimeMillis();
        for (Maquina maquina : List.copyOf(maquinas.values())) {
            if (!maquinas.containsKey(maquina.id)) {
                continue;
            }
            Clan clan = clans.getByTag(maquina.clan);
            if (clan == null) {
                if (maquina.local.isLoaded()) {
                    semDono(maquina, null);
                }
                continue;
            }
            if (maquina.local.isLoaded() && maquina.local.block().getType() != maquina.bloco) {
                perdida(maquina, clan);
                continue;
            }
            maquina.engenheiroPerto = engenheiroPerto(maquina, clan);
            regenerar(maquina, agora);
            produzir(maquina, clan, agora);
            hologramas.atualizar(maquina, texto(maquina), clan);
        }
        // Menus de máquina abertos: o tempo do ciclo e o status andam a cada segundo.
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu && menu.maquinaId != null) {
                menu.redesenhar();
            }
        }
    }

    /** O bloco sumiu por outro jeito (comando, edição do mundo): o registro sai e o líder recebe os itens. */
    private void perdida(Maquina maquina, Clan clan) {
        List<ItemStack> itensDentro = conteudo(maquina, 1.0, true, true);
        desregistrar(maquina);
        salvar();
        entregar(clan.leader(), itensDentro);
        plugin.getLogger().warning("A máquina " + maquina.tipo + " de [" + clan.tag() + "] não existe mais em "
                + maquina.local.serialize() + "; registro removido e itens entregues ao líder.");
        clans.broadcast(clan, "§cA " + config.nome(maquina.tipo) + " §cem " + maquina.local.coords()
                + " não está mais no lugar; o líder recebeu o item e o que tinha dentro.");
    }

    private void produzir(Maquina maquina, Clan clan, long agora) {
        MaquinaStatus status = status(maquina, clan);
        if (status == MaquinaStatus.ARMAZEM_CHEIO && temAutomatico(maquina)
                && agora - maquina.ultimaTentativaAuto >= AUTO_INTERVAL_MILLIS) {
            maquina.ultimaTentativaAuto = agora;
            esvaziarAutomatico(maquina, clan);
            status = status(maquina, clan);
        }
        if (status == MaquinaStatus.PRODUZINDO) {
            double ciclo = MaquinaCalculos.ciclo(config, maquina);
            // Ciclo encurtado no painel: o progresso que sobrou não vira uma rajada de ciclos de uma vez.
            maquina.progresso = Math.min(maquina.progresso, ciclo) + velocidade(maquina);
            int ciclos = 0;
            while (maquina.progresso >= ciclo && ciclos++ < MAX_CICLOS_POR_SEGUNDO) {
                status = status(maquina, clan);
                if (status != MaquinaStatus.PRODUZINDO) {
                    break;
                }
                if (!cobrarCiclo(maquina, clan, ciclo)) {
                    status = MaquinaStatus.COFRE_SEM_DINHEIRO;
                    break;
                }
                maquina.combustivel = Math.max(0, maquina.combustivel - MaquinaCalculos.combustivelPorCiclo(config, maquina));
                ciclo(maquina);
                maquina.progresso -= ciclo;
                sujo = true;
                if (cheio(maquina) && temAutomatico(maquina)) {
                    maquina.ultimaTentativaAuto = agora;
                    esvaziarAutomatico(maquina, clan);
                }
            }
            if (ciclos > 0) {
                status = status(maquina, clan);
            }
        }
        maquina.status = status;
    }

    /** Por que a máquina está parada (ou PRODUZINDO). */
    public MaquinaStatus status(Maquina maquina, Clan clan) {
        if (!config.ativa(maquina.tipo)) {
            return MaquinaStatus.DESATIVADA;
        }
        switch (config.condicao()) {
            case CLAN_ONLINE -> {
                if (clan.members().stream().noneMatch(id -> Bukkit.getPlayer(id) != null)) {
                    return MaquinaStatus.CLAN_OFFLINE;
                }
            }
            case MEMBRO_PERTO -> {
                if (!membroPerto(maquina, clan, config.raioMembroPerto(), false)) {
                    return MaquinaStatus.NINGUEM_PERTO;
                }
            }
            case SEMPRE -> {
            }
        }
        if (cheio(maquina)) {
            return MaquinaStatus.ARMAZEM_CHEIO;
        }
        if (maquina.combustivel + 1.0E-9 < MaquinaCalculos.combustivelPorCiclo(config, maquina)) {
            return MaquinaStatus.SEM_COMBUSTIVEL;
        }
        double custoHora = MaquinaCalculos.custoHora(config, maquina);
        if (custoHora > 0) {
            double devido = maquina.custoResto + custoHora * MaquinaCalculos.ciclo(config, maquina) / 3600.0;
            if (disponivelNoCofre(clan) < Math.max(1, Math.floor(devido))) {
                return MaquinaStatus.COFRE_SEM_DINHEIRO;
            }
        }
        return MaquinaStatus.PRODUZINDO;
    }

    private long disponivelNoCofre(Clan clan) {
        return bases.of(clan).vaultBalance() - bases.minimum(clan);
    }

    /** Cobra do Cofre o custo deste ciclo (a parte decimal acumula). False se o Cofre não pôde pagar. */
    private boolean cobrarCiclo(Maquina maquina, Clan clan, double ciclo) {
        double custoHora = MaquinaCalculos.custoHora(config, maquina);
        if (custoHora <= 0) {
            return true;
        }
        double devido = maquina.custoResto + custoHora * ciclo / 3600.0;
        long inteiro = (long) Math.floor(devido);
        if (inteiro > 0 && !bases.withdrawFromVault(clan, inteiro)) {
            return false;
        }
        maquina.custoResto = devido - inteiro;
        return true;
    }

    private boolean cheio(Maquina maquina) {
        return maquina.totalArmazem() >= MaquinaCalculos.armazemMaximo(config, maquina);
    }

    /** Um ciclo: sorteios (o decimal acumula), FORTUNA (dobro) e SORTE (peso dos raros). O que não couber se perde. */
    private void ciclo(Maquina maquina) {
        List<Produto> tabela = config.producao(maquina.tipo);
        if (tabela.isEmpty()) {
            return;
        }
        maquina.sorteioResto += config.sorteios(maquina.tipo);
        int sorteios = (int) Math.floor(maquina.sorteioResto + 1.0E-9);
        maquina.sorteioResto = Math.max(0, maquina.sorteioResto - sorteios);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean dobro = random.nextDouble() < MaquinaCalculos.chanceDobro(config, maquina);
        double bonusRaro = MaquinaCalculos.bonusRaro(config, maquina);
        double soma = 0;
        for (Produto produto : tabela) {
            soma += MaquinaCalculos.peso(produto, bonusRaro);
        }
        if (soma <= 0) {
            return;
        }
        for (int i = 0; i < sorteios; i++) {
            double sorteio = random.nextDouble() * soma;
            Produto escolhido = tabela.getLast();
            for (Produto produto : tabela) {
                sorteio -= MaquinaCalculos.peso(produto, bonusRaro);
                if (sorteio < 0) {
                    escolhido = produto;
                    break;
                }
            }
            int quantidade = random.nextInt(escolhido.min(), escolhido.max() + 1) * (dobro ? 2 : 1);
            int espaco = MaquinaCalculos.armazemMaximo(config, maquina) - maquina.totalArmazem();
            if (espaco > 0) {
                guardar(maquina, escolhido.item(), Math.min(espaco, quantidade));
            }
        }
    }

    private static void guardar(Maquina maquina, Material material, int delta) {
        int novo = Math.max(0, maquina.armazem.getOrDefault(material, 0) + delta);
        if (novo == 0) {
            maquina.armazem.remove(material);
        } else {
            maquina.armazem.put(material, novo);
        }
    }

    private boolean temAutomatico(Maquina maquina) {
        return MaquinaCalculos.nivel(config, maquina, UpgradeTipo.COLETA_AUTOMATICA) > 0
                || MaquinaCalculos.nivel(config, maquina, UpgradeTipo.VENDA_AUTOMATICA) > 0;
    }

    /** Armazém cheio: COLETA_AUTOMATICA manda para os Baús do Clan; o que sobrar, VENDA_AUTOMATICA vende. */
    private void esvaziarAutomatico(Maquina maquina, Clan clan) {
        if (MaquinaCalculos.nivel(config, maquina, UpgradeTipo.COLETA_AUTOMATICA) > 0) {
            ClanBase base = bases.of(clan);
            for (ClanChest chest : base.chests()) {
                if (maquina.armazem.isEmpty()) {
                    break;
                }
                for (Material material : List.copyOf(maquina.armazem.keySet())) {
                    int quantidade = maquina.armazem.getOrDefault(material, 0);
                    guardar(maquina, material, -quantidade); // sai do armazém antes de entrar no baú
                    int sobrou = 0;
                    for (ItemStack resto : chests.addItems(chest.id(), clan.tag(), pilhas(material, quantidade))) {
                        sobrou += resto.getAmount();
                    }
                    guardar(maquina, material, sobrou);
                }
            }
            sujo = true;
        }
        if (cheio(maquina) && MaquinaCalculos.nivel(config, maquina, UpgradeTipo.VENDA_AUTOMATICA) > 0) {
            double total = 0;
            int vendidos = 0;
            for (Material material : List.copyOf(maquina.armazem.keySet())) {
                if (!sellPrices.hasPrice(material)) {
                    continue; // sem preço no /vender: fica no armazém
                }
                int quantidade = maquina.armazem.getOrDefault(material, 0);
                guardar(maquina, material, -quantidade);
                total += sellPrices.sell(material, quantidade); // preço atual, conta para a desvalorização
                vendidos += quantidade;
            }
            long pago = (long) Math.floor(total * (1 - config.taxaVendaAutomatica()) + 1.0E-9);
            if (pago > 0) {
                bases.depositToVault(clan, pago);
            }
            if (vendidos > 0) {
                clans.broadcast(clan, "§7Venda automática da " + config.nome(maquina.tipo) + "§7: " + vendidos
                        + " itens, §a+" + EconomyManager.format(pago) + " §7no Cofre.");
            }
            sujo = true;
        }
    }

    private boolean engenheiroPerto(Maquina maquina, Clan clan) {
        return config.engenheiroBonus() > 0 && membroPerto(maquina, clan, config.engenheiroRaio(), true);
    }

    private boolean membroPerto(Maquina maquina, Clan clan, double raio, boolean engenheiro) {
        for (UUID id : clan.members()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && !player.isDead() && maquina.local.distance(player.getLocation()) <= raio
                    && (!engenheiro || classes.hasClass(id, PlayerClass.ENGENHEIRO))) {
                return true;
            }
        }
        return false;
    }

    /** Fora de raid, 1 de vida a cada {@code regeneracao-minutos} desde o último dano/regeneração. */
    private void regenerar(Maquina maquina, long agora) {
        int max = MaquinaCalculos.vidaMaxima(config, maquina);
        if (maquina.vida > max) {
            maquina.vida = max;
            sujo = true;
        }
        double minutos = config.regeneracaoMinutos();
        if (minutos <= 0 || maquina.vida >= max || emRaid(maquina)) {
            return;
        }
        long espera = Math.round(minutos * 60_000);
        if (agora - Math.max(maquina.ultimoDano, maquina.ultimaRegeneracao) >= espera) {
            maquina.vida++;
            maquina.ultimaRegeneracao = agora;
            sujo = true;
        }
    }

    // ------------------------------------------------------------ holograma e menus

    /** Atualiza holograma e menus abertos da máquina agora (depois de uma mudança). */
    void mudou(Maquina maquina) {
        Clan clan = clans.getByTag(maquina.clan);
        if (clan == null || !maquinas.containsKey(maquina.id)) {
            return;
        }
        maquina.status = status(maquina, clan);
        hologramas.atualizar(maquina, texto(maquina), clan);
        redesenharMenus(maquina);
    }

    private void redesenharMenus(Maquina maquina) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu
                    && maquina.id.equals(menu.maquinaId)) {
                menu.redesenhar();
            }
        }
    }

    String texto(Maquina maquina) {
        StringBuilder texto = new StringBuilder(config.nome(maquina.tipo))
                .append("\n§7Combustível §f").append(MaquinasConfig.decimal(maquina.combustivel)).append("/")
                .append(MaquinasConfig.decimal(MaquinaCalculos.combustivelMaximo(config, maquina)))
                .append("\n§7Armazém §f").append(maquina.totalArmazem()).append("/")
                .append(MaquinaCalculos.armazemMaximo(config, maquina))
                .append("\n").append(maquina.status.texto())
                .append("\n§c❤ ").append(maquina.vida).append("/").append(MaquinaCalculos.vidaMaxima(config, maquina));
        if (maquina.comEscudo()) {
            texto.append(" §b🛡");
        }
        if (maquina.engenheiroPerto) {
            texto.append("\n§b⚙ Engenheiro +").append(MaquinasConfig.decimal(config.engenheiroBonus() * 100)).append("%");
        }
        if (config.modoTeste()) {
            texto.append("\n§d[MODO TESTE]");
        }
        return texto.toString();
    }

    // ------------------------------------------------------------ itens

    /** Pilhas de até o tamanho máximo do material. */
    static List<ItemStack> pilhas(Material material, int quantidade) {
        List<ItemStack> lista = new ArrayList<>();
        int max = Math.max(1, material.getMaxStackSize());
        int resto = quantidade;
        while (resto > 0) {
            int agora = Math.min(max, resto);
            lista.add(new ItemStack(material, agora));
            resto -= agora;
        }
        return lista;
    }

    /** Quantos itens comuns do material cabem no inventário (até {@code limite}). */
    static int espaco(PlayerInventory inventory, Material material, int limite) {
        ItemStack modelo = new ItemStack(material);
        int max = Math.max(1, material.getMaxStackSize());
        long cabe = 0;
        for (ItemStack slot : inventory.getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                cabe += max;
            } else if (slot.isSimilar(modelo)) {
                cabe += Math.max(0, max - slot.getAmount());
            }
            if (cabe >= limite) {
                return limite;
            }
        }
        return (int) Math.min(limite, cabe);
    }

    /** Dá os itens; o que não couber cai aos pés do jogador (nada some). */
    static void dar(Player player, List<ItemStack> lista) {
        if (lista.isEmpty()) {
            return;
        }
        player.getInventory().addItem(lista.toArray(ItemStack[]::new)).values()
                .forEach(resto -> player.getWorld().dropItem(player.getLocation(), resto));
    }

    // ------------------------------------------------------------ arquivo

    public void load() {
        maquinas.clear();
        porLocal.clear();
        entregas.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("maquinas");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection data = section.getConfigurationSection(id);
                if (data == null) {
                    continue;
                }
                MaquinaTipo tipo = MaquinaTipo.parse(data.getString("tipo"));
                BaseLoc local = BaseLoc.parse(data.getString("local"));
                Material bloco = Material.matchMaterial(data.getString("bloco", ""));
                String clan = data.getString("clan");
                if (tipo == null || local == null || bloco == null || clan == null) {
                    plugin.getLogger().warning("Máquina inválida em maquinas-dados.yml, ignorada: " + id);
                    continue;
                }
                Maquina maquina = new Maquina(id, tipo, clan, local, bloco);
                ConfigurationSection armazem = data.getConfigurationSection("armazem");
                if (armazem != null) {
                    for (String key : armazem.getKeys(false)) {
                        Material material = Material.matchMaterial(key);
                        int quantidade = armazem.getInt(key);
                        if (material != null && quantidade > 0) {
                            maquina.armazem.put(material, quantidade);
                        }
                    }
                }
                for (String texto : data.getStringList("upgrades")) {
                    Upgrade upgrade = Upgrade.parse(texto);
                    if (upgrade != null && maquina.upgrade(upgrade.tipo()) == null) {
                        maquina.upgrades.add(upgrade);
                    }
                }
                maquina.combustivel = Math.max(0, data.getDouble("combustivel"));
                maquina.progresso = Math.max(0, data.getDouble("progresso"));
                maquina.sorteioResto = Math.max(0, data.getDouble("sorteio-resto"));
                maquina.custoResto = Math.max(0, data.getDouble("custo-resto"));
                maquina.vida = data.getInt("vida", config.vida(tipo));
                maquina.escudoAte = data.getLong("escudo-ate");
                maquina.ultimoDano = data.getLong("ultimo-dano");
                maquina.ultimaRegeneracao = data.getLong("ultima-regeneracao");
                registrar(maquina);
            }
        }
        ConfigurationSection pendentes = yaml.getConfigurationSection("entregas");
        if (pendentes != null) {
            for (String key : pendentes.getKeys(false)) {
                try {
                    UUID jogador = UUID.fromString(key);
                    List<ItemStack> lista = new ArrayList<>();
                    for (String data : pendentes.getStringList(key)) {
                        lista.add(ItemStack.deserializeBytes(Base64.getDecoder().decode(data)));
                    }
                    if (!lista.isEmpty()) {
                        entregas.put(jogador, lista);
                    }
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Entrega inválida em maquinas-dados.yml, ignorada: " + key);
                }
            }
        }
    }

    public void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Maquina maquina : maquinas.values()) {
            String path = "maquinas." + maquina.id + ".";
            yaml.set(path + "tipo", maquina.tipo.name());
            yaml.set(path + "clan", maquina.clan);
            yaml.set(path + "local", maquina.local.serialize());
            yaml.set(path + "bloco", maquina.bloco.name());
            maquina.armazem.forEach((material, quantidade) -> yaml.set(path + "armazem." + material.name(), quantidade));
            yaml.set(path + "combustivel", maquina.combustivel);
            yaml.set(path + "progresso", maquina.progresso);
            yaml.set(path + "sorteio-resto", maquina.sorteioResto);
            yaml.set(path + "custo-resto", maquina.custoResto);
            yaml.set(path + "upgrades", maquina.upgrades.stream().map(Upgrade::serialize).toList());
            yaml.set(path + "vida", maquina.vida);
            yaml.set(path + "escudo-ate", maquina.escudoAte);
            yaml.set(path + "ultimo-dano", maquina.ultimoDano);
            yaml.set(path + "ultima-regeneracao", maquina.ultimaRegeneracao);
        }
        entregas.forEach((jogador, lista) -> yaml.set("entregas." + jogador, lista.stream()
                .map(item -> Base64.getEncoder().encodeToString(item.serializeAsBytes())).toList()));
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
            sujo = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar maquinas-dados.yml: " + e.getMessage());
        }
    }
}
