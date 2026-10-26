package br.com.murilo.muriloplug.maquinas;

import br.com.murilo.muriloplug.clan.Clan;
import br.com.murilo.muriloplug.clan.ClanManager;
import br.com.murilo.muriloplug.clan.base.ClanBase;
import br.com.murilo.muriloplug.clan.base.ClanBaseManager;
import br.com.murilo.muriloplug.economy.EconomyManager;
import br.com.murilo.muriloplug.shop.InventorySpace;
import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Telas dos jogadores: o /maquinas (comprar com o dinheiro do Cofre, "Minhas máquinas", "Juntar upgrades") e o menu de
 * cada máquina (armazém, combustível, upgrades, recolher). Os dados ficam na {@link Maquina}: o menu só desenha e
 * chama o {@link MaquinaManager}, então dois membros com o mesmo menu aberto veem a mesma coisa.
 */
public final class MaquinasMenus {

    static final int[] SLOTS_TIPO = {10, 12, 14, 16};
    private static final int PRIMEIRO_PRODUTO = 9;
    private static final int PRODUTOS = 27;
    private static final int PRIMEIRO_UPGRADE = 37;
    private static final int MAX_SLOTS_UPGRADE = 7;

    private final JavaPlugin plugin;
    private final MaquinaManager manager;
    private final MaquinasConfig config;
    private final MaquinaItens itens;
    private final ClanManager clans;
    private final ClanBaseManager bases;

    public MaquinasMenus(JavaPlugin plugin, MaquinaManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.config = manager.config();
        this.itens = manager.itens();
        this.clans = manager.clans();
        this.bases = manager.bases();
    }

    private void fechar(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
    }

    // ------------------------------------------------------------ /maquinas

    public void abrirLoja(Player player) {
        new Menu(4, "Máquinas").desenho(menu -> desenharLoja(menu, player)).abrir(player);
    }

    private void desenharLoja(Menu menu, Player player) {
        Clan clan = clans.getClan(player.getUniqueId());
        List<MaquinaTipo> ativos = new ArrayList<>();
        for (MaquinaTipo tipo : MaquinaTipo.values()) {
            if (config.ativa(tipo)) {
                ativos.add(tipo);
            }
        }
        for (int i = 0; i < ativos.size() && i < SLOTS_TIPO.length; i++) {
            MaquinaTipo tipo = ativos.get(i);
            menu.set(SLOTS_TIPO[i], iconeTipo(tipo, clan, player), (p, e) -> comprar(p, tipo));
        }
        if (ativos.isEmpty()) {
            menu.set(13, Icone.de(Material.BARRIER, "§7Nenhuma máquina à venda agora"));
        }
        if (clan != null) {
            ClanBase base = bases.of(clan);
            menu.set(4, Icone.de(Material.GOLD_INGOT, "§eCofre de [" + clan.tag() + "]",
                    "§7Saldo: §a" + EconomyManager.format(base.vaultBalance()),
                    "§7Mínimo obrigatório: §f" + EconomyManager.format(bases.minimum(clan)),
                    "§7As máquinas são pagas com o Cofre."));
        } else {
            menu.set(4, Icone.de(Material.BARRIER, "§cVocê não está em um clan",
                    "§7Só clans compram e usam máquinas."));
        }
        menu.set(30, Icone.de(Material.CHEST, "§eMinhas máquinas", "§7Lista das máquinas do seu clan."),
                (p, e) -> abrirMinhas(p));
        menu.set(32, Icone.de(Material.ANVIL, "§bJuntar upgrades",
                "§72 upgrades do mesmo tipo e nível", "§7viram 1 do nível seguinte."), (p, e) -> abrirJuntar(p));
        menu.preencher(27, 35);
    }

    private ItemStack iconeTipo(MaquinaTipo tipo, Clan clan, Player player) {
        int limite = config.limite(tipo);
        List<Object> lore = new ArrayList<>(MaquinaCalculos.linhasProducao(config, tipo, 0));
        lore.add("");
        lore.add("§7Ciclo: §f" + MaquinasConfig.decimal(config.ciclo(tipo)) + "s §8(" + MaquinasConfig.decimal(
                config.sorteios(tipo)) + " sorteio(s))");
        lore.add("§7Combustível: §f" + MaquinasConfig.decimal(config.combustivelPorCiclo(tipo)) + " por ciclo");
        if (config.custoHora(tipo) > 0) {
            lore.add("§7Custo do Cofre: §f" + EconomyManager.format(Math.round(config.custoHora(tipo))) + "/hora");
        }
        lore.add("§7Armazém: §f" + config.armazemBase(tipo) + " itens");
        lore.add("§7Preço: §a" + EconomyManager.format(config.preco(tipo)) + " §8(do Cofre)");
        lore.add("§7Limite por clan: §f" + (limite <= 0 ? "sem limite" : limite));
        if (clan != null) {
            lore.add("§7Seu clan tem: §f" + manager.contar(clan.tag(), tipo));
        }
        lore.add("");
        boolean podeComprar = clan != null && (!config.apenasLiderCompra() || clan.leader().equals(player.getUniqueId()));
        lore.add(podeComprar ? "§eClique para comprar" : "§c" + (clan == null ? "Precisa de um clan" : "Só o líder compra"));
        return Icone.de(config.bloco(tipo), config.nome(tipo), lore.toArray());
    }

    /** Confere quem pode comprar; null = pode. */
    private String problemaCompra(Player player, Clan clan, MaquinaTipo tipo) {
        if (clan == null) {
            return "§cVocê precisa estar em um clan para comprar máquinas.";
        }
        if (config.apenasLiderCompra() && !clan.leader().equals(player.getUniqueId())) {
            return "§cSó o líder do clan pode comprar máquinas.";
        }
        if (!config.ativa(tipo)) {
            return "§cEsta máquina não está à venda agora.";
        }
        int limite = config.limite(tipo);
        if (limite > 0 && manager.contar(clan.tag(), tipo) >= limite) {
            return "§cSeu clan já tem o máximo de " + limite + " desta máquina.";
        }
        return null;
    }

    private void comprar(Player player, MaquinaTipo tipo) {
        String problema = problemaCompra(player, clans.getClan(player.getUniqueId()), tipo);
        if (problema != null) {
            player.sendMessage(problema);
            return;
        }
        new Menu(3, "Comprar máquina?").desenho(menu -> {
            menu.set(13, itens.criarMaquina(tipo));
            menu.set(11, Icone.de(Material.LIME_CONCRETE, "§aConfirmar compra",
                    "§7Preço: §a" + EconomyManager.format(config.preco(tipo)), "§7Pago com o dinheiro do Cofre."),
                    (p, e) -> confirmarCompra(p, tipo));
            menu.set(15, Icone.de(Material.RED_CONCRETE, "§cCancelar"), (p, e) -> abrirLoja(p));
            menu.preencher(0, 26);
        }).abrir(player);
    }

    private void confirmarCompra(Player player, MaquinaTipo tipo) {
        Clan clan = clans.getClan(player.getUniqueId());
        String problema = problemaCompra(player, clan, tipo); // confere de novo na hora
        if (problema != null) {
            player.sendMessage(problema);
            fechar(player);
            return;
        }
        ItemStack item = itens.criarMaquina(tipo);
        if (!InventorySpace.fits(player.getInventory(), item)) {
            player.sendMessage("§cSeu inventário está cheio. Nada foi cobrado.");
            return;
        }
        long preco = config.preco(tipo);
        if (preco > 0 && !bases.withdrawFromVault(clan, preco)) { // cobra do Cofre antes de entregar
            player.sendMessage("§cO Cofre não tem saldo para esta compra: ele tem "
                    + EconomyManager.format(bases.of(clan).vaultBalance()) + " e precisa manter no mínimo "
                    + EconomyManager.format(bases.minimum(clan)) + ".");
            return;
        }
        MaquinaManager.dar(player, List.of(item));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        clans.broadcast(clan, "§a" + player.getName() + " comprou uma " + config.nome(tipo) + " §apor "
                + EconomyManager.format(preco) + " do Cofre.");
        abrirLoja(player);
    }

    // ------------------------------------------------------------ minhas máquinas

    private void abrirMinhas(Player player) {
        new Menu(6, "Minhas máquinas").desenho(menu -> {
            Clan clan = clans.getClan(player.getUniqueId());
            List<Maquina> lista = clan == null ? List.of() : manager.doClan(clan.tag());
            for (int i = 0; i < lista.size() && i < 45; i++) {
                menu.set(i, iconeResumo(lista.get(i)));
            }
            if (lista.isEmpty()) {
                menu.set(22, Icone.de(Material.BARRIER, "§7Seu clan não tem máquinas colocadas"));
            }
            menu.set(49, Icone.de(Material.ARROW, "§7Voltar"), (p, e) -> abrirLoja(p));
            menu.preencher(45, 53);
        }).abrir(player);
    }

    /** Ícone com o resumo de uma máquina (tipo, lugar, status, armazém, combustível, vida, upgrades). */
    ItemStack iconeResumo(Maquina maquina) {
        List<Object> lore = new ArrayList<>();
        lore.add("§7Clan: §f[" + maquina.clan + "]");
        lore.add("§7Local: §f" + maquina.local.world() + " " + maquina.local.coords());
        lore.add("§7Status: " + maquina.status.texto());
        lore.add("§7Armazém: §f" + maquina.totalArmazem() + "/" + MaquinaCalculos.armazemMaximo(config, maquina));
        lore.add("§7Combustível: §f" + MaquinasConfig.decimal(maquina.combustivel) + "/"
                + MaquinasConfig.decimal(MaquinaCalculos.combustivelMaximo(config, maquina)));
        lore.add("§7Vida: §c" + maquina.vida + "/" + MaquinaCalculos.vidaMaxima(config, maquina));
        if (!maquina.upgrades.isEmpty()) {
            lore.add("§7Upgrades: " + String.join("§7, ", maquina.upgrades.stream().map(Upgrade::nomeColorido).toList()));
        }
        return Icone.de(maquina.bloco, config.nome(maquina.tipo), lore.toArray());
    }

    // ------------------------------------------------------------ menu da máquina

    /** Abre o menu da máquina ({@code admin}: aberto pelo painel, sem precisar ser do clan). */
    public void abrirMaquina(Player player, Maquina maquina, boolean admin) {
        Menu menu = new Menu(6, config.nome(maquina.tipo) + (admin ? " §4[admin]" : ""));
        menu.maquinaId = maquina.id;
        menu.painel = admin;
        menu.inventarioLivre = true;
        menu.desenho(m -> desenharMaquina(m, maquina, admin));
        menu.inferior = (p, e) -> {
            if (pode(p, maquina, admin) && e.isShiftClick()) {
                abastecerDoInventario(p, maquina, e);
            }
        };
        menu.abrir(player);
    }

    /** Ainda pode mexer: a máquina existe e o jogador é do clan (ou é o admin com a permissão). */
    private boolean pode(Player player, Maquina maquina, boolean admin) {
        if (manager.porId(maquina.id) == null) {
            fechar(player);
            return false;
        }
        if (admin) {
            return player.hasPermission(MaquinasMenusListener.ADMIN_PERMISSION);
        }
        Clan clan = clans.getByTag(maquina.clan);
        if (clan == null || !clan.isMember(player.getUniqueId())) {
            player.sendMessage("§cEsta máquina pertence ao clan " + maquina.clan);
            fechar(player);
            return false;
        }
        return true;
    }

    private void desenharMaquina(Menu menu, Maquina maquina, boolean admin) {
        int armazemMax = MaquinaCalculos.armazemMaximo(config, maquina);
        double combustivelMax = MaquinaCalculos.combustivelMaximo(config, maquina);
        int total = maquina.totalArmazem();

        menu.set(0, Icone.de(maquina.status.icone(), "§fStatus: " + maquina.status.texto(), motivo(maquina)));
        menu.set(1, Icone.de(Material.CHEST, "§fArmazém: §e" + total + "/" + armazemMax,
                Icone.barra(total, armazemMax), "§7O que não couber é perdido.",
                total > armazemMax ? "§cAcima do máximo: a máquina para até baixar." : null));
        List<Object> combustiveis = new ArrayList<>();
        combustiveis.add(Icone.barra(maquina.combustivel, combustivelMax));
        combustiveis.add("§7Gasta §f" + MaquinasConfig.decimal(MaquinaCalculos.combustivelPorCiclo(config, maquina))
                + " §7por ciclo.");
        combustiveis.add("");
        combustiveis.add("§eClique com combustível no cursor para colocar.");
        combustiveis.add("§eShift + clique no seu inventário também coloca.");
        combustiveis.add("§7Aceitos:");
        for (Map.Entry<Material, Double> entry : config.combustiveis().entrySet()) {
            combustiveis.add(ShopUi.text("§8• ").append(MaquinaCalculos.nome(entry.getKey()))
                    .append(ShopUi.text(" §7= §f" + MaquinasConfig.decimal(entry.getValue()))));
        }
        menu.set(2, Icone.de(Material.CAMPFIRE, "§6Combustível: §f" + MaquinasConfig.decimal(maquina.combustivel) + "/"
                + MaquinasConfig.decimal(combustivelMax), combustiveis.toArray()), (p, e) -> {
            if (pode(p, maquina, admin)) {
                abastecerDoCursor(p, maquina);
            }
        });
        double ciclo = MaquinaCalculos.ciclo(config, maquina);
        menu.set(3, Icone.de(Material.CLOCK, "§fPróximo ciclo: §e"
                        + (maquina.status == MaquinaStatus.PRODUZINDO
                        ? MaquinasConfig.decimal(Math.ceil(manager.segundosParaCiclo(maquina))) + "s" : "§cpausado"),
                "§7Ciclo: §f" + MaquinasConfig.decimal(ciclo) + "s §8(base " + MaquinasConfig.decimal(config.ciclo(maquina.tipo)) + "s)",
                "§7Velocidade: §fx" + MaquinasConfig.decimal(manager.velocidade(maquina)),
                maquina.status == MaquinaStatus.PRODUZINDO ? null : "§7O tempo do ciclo pausa enquanto ela está parada."));
        menu.set(4, Icone.de(maquina.bloco, config.nome(maquina.tipo), resumoBonus(maquina)));
        int vidaMax = MaquinaCalculos.vidaMaxima(config, maquina);
        menu.set(5, Icone.de(Material.RED_DYE, "§c❤ " + maquina.vida + "/" + vidaMax + (maquina.comEscudo() ? " §b🛡" : ""),
                "§7Só explosões tiram vida (1 por explosão).",
                "§7Fora de raid regenera 1 a cada §f" + MaquinasConfig.decimal(config.regeneracaoMinutos()) + " min§7.",
                "§7Engenheiro do clan: Shift + clique direito", "§7no bloco repara."));
        menu.set(6, Icone.de(Material.BOOK, "§eTabela de produção",
                MaquinaCalculos.linhasProducao(config, maquina.tipo, MaquinaCalculos.bonusRaro(config, maquina))));
        menu.set(7, Icone.de(Material.HOPPER, "§aColetar tudo", "§7Pega tudo o que couber no inventário."),
                (p, e) -> {
                    if (pode(p, maquina, admin)) {
                        int pegou = manager.coletarTudo(p, maquina);
                        p.sendMessage(pegou > 0 ? "§aVocê coletou " + pegou + " itens."
                                : maquina.totalArmazem() > 0 ? "§cSeu inventário está cheio." : "§eO armazém está vazio.");
                    }
                });
        menu.set(8, Icone.de(Material.BARRIER, "§cRecolher máquina", "§7Devolve o item da máquina, o combustível",
                "§7(em carvão) e os upgrades.", "§7Precisa do armazém vazio, fora de combate", "§7e sem raid na base."),
                (p, e) -> {
                    if (pode(p, maquina, admin)) {
                        abrirRecolher(p, maquina, admin);
                    }
                });

        // Produtos guardados: um ícone por item, com a quantidade na lore.
        List<Map.Entry<Material, Integer>> produtos = new ArrayList<>(maquina.armazem.entrySet());
        for (int i = 0; i < produtos.size() && i < PRODUTOS; i++) {
            Material material = produtos.get(i).getKey();
            int quantidade = produtos.get(i).getValue();
            ItemStack icone = new ItemStack(material, Math.max(1, Math.min(material.getMaxStackSize(), quantidade)));
            Icone.com(icone, null, "§7Guardado: §f" + quantidade, "§eClique: pegar até 1 pack");
            menu.set(PRIMEIRO_PRODUTO + i, icone, (p, e) -> {
                if (pode(p, maquina, admin)) {
                    int pegou = manager.coletar(p, maquina, material, material.getMaxStackSize());
                    if (pegou <= 0) {
                        p.sendMessage("§cSeu inventário está cheio.");
                    }
                }
            });
        }
        if (produtos.size() > PRODUTOS) {
            menu.set(PRIMEIRO_PRODUTO + PRODUTOS - 1, Icone.de(Material.PAPER, "§7E mais " + (produtos.size() - PRODUTOS + 1)
                    + " tipos...", "§7Use \"Coletar tudo\"."));
        }

        // Upgrades.
        menu.set(36, Icone.de(Material.ENCHANTED_BOOK, "§bUpgrades", resumoBonus(maquina)));
        int slots = Math.min(MAX_SLOTS_UPGRADE, Math.max(config.slots(maquina.tipo), maquina.upgrades.size()));
        for (int i = 0; i < slots; i++) {
            int indice = i;
            ItemStack icone;
            if (i < maquina.upgrades.size()) {
                Upgrade upgrade = maquina.upgrades.get(i);
                icone = Icone.mais(itens.criarUpgrade(upgrade, 1), "",
                        config.upgradePermitido(maquina.tipo, upgrade.tipo()) ? "§eClique para retirar"
                                : "§cNão é mais permitido aqui (sem efeito). §eClique para retirar");
            } else if (i < config.slots(maquina.tipo)) {
                icone = Icone.de(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "§7Slot de upgrade vazio",
                        "§eClique com um upgrade no cursor.");
            } else {
                continue;
            }
            menu.set(PRIMEIRO_UPGRADE + i, icone, (p, e) -> {
                if (pode(p, maquina, admin)) {
                    cliqueUpgrade(p, maquina, indice);
                }
            });
        }
        menu.preencher(36, 53);
        if (admin) {
            menu.set(49, Icone.de(Material.REDSTONE_BLOCK, "§4Aberto como admin", "§7Clan: [" + maquina.clan + "]"));
        }
    }

    private List<String> motivo(Maquina maquina) {
        return switch (maquina.status) {
            case PRODUZINDO -> List.of("§7Tudo certo.");
            case SEM_COMBUSTIVEL -> List.of("§7Coloque combustível no slot ao lado.");
            case ARMAZEM_CHEIO -> List.of("§7Colete os produtos para voltar a produzir.");
            case COFRE_SEM_DINHEIRO -> List.of("§7O Cofre do clan não tem dinheiro", "§7(acima do mínimo) para o custo por hora.");
            case CLAN_OFFLINE -> List.of("§7Só funciona com alguém do clan online.");
            case NINGUEM_PERTO -> List.of("§7Só funciona com um membro do clan a até §f"
                    + MaquinasConfig.decimal(config.raioMembroPerto()) + " §7blocos.");
            case DESATIVADA -> List.of("§7Este tipo de máquina foi desligado pelo servidor.");
        };
    }

    /** Bônus ativos: ciclo, combustível e armazém finais, chance de dobro, bônus de raro e Engenheiro. */
    private List<String> resumoBonus(Maquina maquina) {
        List<String> linhas = new ArrayList<>();
        linhas.add("§7Ciclo final: §f" + MaquinasConfig.decimal(MaquinaCalculos.ciclo(config, maquina)) + "s");
        linhas.add("§7Combustível final: §f" + MaquinasConfig.decimal(MaquinaCalculos.combustivelPorCiclo(config, maquina))
                + " §7por ciclo");
        if (config.custoHora(maquina.tipo) > 0) {
            linhas.add("§7Custo do Cofre: §f" + MaquinasConfig.decimal(MaquinaCalculos.custoHora(config, maquina)) + "/hora");
        }
        linhas.add("§7Armazém final: §f" + MaquinaCalculos.armazemMaximo(config, maquina) + " §7| combustível máx: §f"
                + MaquinasConfig.decimal(MaquinaCalculos.combustivelMaximo(config, maquina)));
        linhas.add("§7Chance de dobro: §f" + MaquinaCalculos.porcento(MaquinaCalculos.chanceDobro(config, maquina)));
        linhas.add("§7Bônus de raro: §f+" + MaquinaCalculos.porcento(MaquinaCalculos.bonusRaro(config, maquina)));
        linhas.add("§7Vida máxima: §f" + MaquinaCalculos.vidaMaxima(config, maquina));
        if (MaquinaCalculos.nivel(config, maquina, UpgradeTipo.COLETA_AUTOMATICA) > 0) {
            linhas.add("§aColeta automática para o Baú do Clan");
        }
        if (MaquinaCalculos.nivel(config, maquina, UpgradeTipo.VENDA_AUTOMATICA) > 0) {
            linhas.add("§aVenda automática (taxa " + MaquinaCalculos.porcento(config.taxaVendaAutomatica()) + ")");
        }
        if (maquina.engenheiroPerto) {
            linhas.add("§b⚙ Engenheiro por perto: +" + MaquinaCalculos.porcento(config.engenheiroBonus()) + " de velocidade");
        }
        return linhas;
    }

    private void abastecerDoCursor(Player player, Maquina maquina) {
        ItemStack cursor = player.getItemOnCursor();
        if (cursor.getType().isAir()) {
            player.sendMessage("§7Pegue o combustível com o mouse e clique neste slot.");
            return;
        }
        int usados = manager.abastecer(maquina, cursor);
        if (usados < 0) {
            player.sendMessage("§cEste item não é combustível.");
        } else if (usados == 0) {
            player.sendMessage("§eO combustível está no máximo.");
        } else {
            ItemStack resto = cursor.clone();
            resto.setAmount(cursor.getAmount() - usados);
            player.setItemOnCursor(resto.getAmount() <= 0 ? null : resto);
        }
    }

    private void abastecerDoInventario(Player player, Maquina maquina, InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir()) {
            return;
        }
        int usados = manager.abastecer(maquina, item);
        if (usados < 0) {
            player.sendMessage("§cEste item não é combustível.");
        } else if (usados == 0) {
            player.sendMessage("§eO combustível está no máximo.");
        } else {
            ItemStack resto = item.clone();
            resto.setAmount(item.getAmount() - usados);
            event.getClickedInventory().setItem(event.getSlot(), resto.getAmount() <= 0 ? null : resto);
        }
    }

    private void cliqueUpgrade(Player player, Maquina maquina, int indice) {
        ItemStack cursor = player.getItemOnCursor();
        boolean ocupado = indice < maquina.upgrades.size();
        if (!cursor.getType().isAir()) {
            Upgrade upgrade = itens.lerUpgrade(cursor);
            if (upgrade == null) {
                player.sendMessage("§cIsso não é um item de upgrade.");
                return;
            }
            if (ocupado) {
                player.sendMessage("§cEste slot já tem um upgrade. Retire-o antes (com o cursor vazio).");
                return;
            }
            String problema = manager.instalar(maquina, upgrade);
            if (problema != null) {
                player.sendMessage(problema);
                return;
            }
            ItemStack resto = cursor.clone();
            resto.setAmount(cursor.getAmount() - 1);
            player.setItemOnCursor(resto.getAmount() <= 0 ? null : resto);
            player.sendMessage("§aUpgrade instalado: " + upgrade.nomeColorido());
            return;
        }
        if (!ocupado) {
            player.sendMessage("§7Pegue um upgrade com o mouse e clique no slot.");
            return;
        }
        ItemStack item = itens.criarUpgrade(maquina.upgrades.get(indice), 1);
        if (!InventorySpace.fits(player.getInventory(), item)) {
            player.sendMessage("§cSeu inventário está cheio.");
            return;
        }
        Upgrade retirado = manager.retirar(maquina, indice); // sai da máquina antes de ir para o inventário
        if (retirado != null) {
            MaquinaManager.dar(player, List.of(itens.criarUpgrade(retirado, 1)));
            player.sendMessage("§eUpgrade retirado: " + retirado.nomeColorido());
        }
    }

    private void abrirRecolher(Player player, Maquina maquina, boolean admin) {
        Menu menu = new Menu(3, "Recolher máquina?");
        menu.painel = admin;
        menu.desenho(m -> {
            m.set(13, iconeResumo(maquina));
            m.set(11, Icone.de(Material.LIME_CONCRETE, "§aRecolher", "§7Volta para o seu inventário."), (p, e) -> {
                String mensagem = manager.recolher(p, maquina);
                p.sendMessage(mensagem);
                if (manager.porId(maquina.id) != null) {
                    abrirMaquina(p, maquina, admin);
                } else {
                    fechar(p);
                }
            });
            m.set(15, Icone.de(Material.RED_CONCRETE, "§cCancelar"), (p, e) -> abrirMaquina(p, maquina, admin));
            m.preencher(0, 26);
        });
        menu.abrir(player);
    }

    // ------------------------------------------------------------ juntar upgrades

    private static final int ENTRADA_A = 11;
    private static final int ENTRADA_B = 13;
    private static final int BOTAO_JUNTAR = 15;

    private void abrirJuntar(Player player) {
        ItemStack[] entradas = new ItemStack[2];
        Menu menu = new Menu(3, "Juntar upgrades");
        menu.inventarioLivre = true;
        menu.desenho(m -> {
            for (int i = 0; i < 2; i++) {
                int indice = i;
                ItemStack icone = entradas[i] != null ? entradas[i].clone()
                        : Icone.de(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "§7Coloque um upgrade",
                        "§eClique com o upgrade no cursor", "§eou Shift + clique nele no inventário.");
                m.set(i == 0 ? ENTRADA_A : ENTRADA_B, icone, (p, e) -> cliqueEntrada(p, entradas, indice, m));
            }
            m.set(BOTAO_JUNTAR, botaoJuntar(entradas), (p, e) -> juntar(p, entradas, m));
            m.set(22, Icone.de(Material.ARROW, "§7Voltar"), (p, e) -> abrirLoja(p));
            m.preencher(0, 26);
        });
        menu.inferior = (p, e) -> {
            if (!e.isShiftClick()) {
                return;
            }
            ItemStack item = e.getCurrentItem();
            if (item == null || item.getType().isAir()) {
                return;
            }
            if (itens.lerUpgrade(item) == null) {
                p.sendMessage("§cIsso não é um item de upgrade.");
                return;
            }
            int livre = entradas[0] == null ? 0 : entradas[1] == null ? 1 : -1;
            if (livre < 0) {
                p.sendMessage("§eOs dois espaços já estão ocupados.");
                return;
            }
            entradas[livre] = item.asOne(); // 1 sai do inventário e entra no menu
            ItemStack resto = item.clone();
            resto.setAmount(item.getAmount() - 1);
            e.getClickedInventory().setItem(e.getSlot(), resto.getAmount() <= 0 ? null : resto);
            menu.redesenhar();
        };
        menu.aoFechar = p -> devolverEntradas(p, entradas);
        menu.abrir(player);
    }

    private ItemStack botaoJuntar(ItemStack[] entradas) {
        Upgrade a = itens.lerUpgrade(entradas[0]);
        Upgrade b = itens.lerUpgrade(entradas[1]);
        if (a == null || b == null) {
            return Icone.de(Material.GRAY_DYE, "§7Juntar", "§7Coloque 2 upgrades iguais.");
        }
        if (!a.equals(b)) {
            return Icone.de(Material.RED_DYE, "§cNão dá para juntar", "§7Os dois precisam ser do mesmo tipo e nível.");
        }
        if (a.nivel() >= config.nivelMaximo(a.tipo())) {
            return Icone.de(Material.RED_DYE, "§cNível máximo", "§7" + a.tipo().nome() + " vai até o nível "
                    + config.nivelMaximo(a.tipo()) + ".");
        }
        Upgrade resultado = new Upgrade(a.tipo(), a.nivel() + 1);
        long custo = config.custoJuntar() * resultado.nivel();
        return Icone.mais(itens.criarUpgrade(resultado, 1), "",
                custo > 0 ? "§7Custo: §a" + EconomyManager.format(custo) + " §7(do Cofre)" : "§7Grátis",
                "§aClique para juntar");
    }

    private void cliqueEntrada(Player player, ItemStack[] entradas, int indice, Menu menu) {
        ItemStack cursor = player.getItemOnCursor();
        if (entradas[indice] != null) {
            if (!cursor.getType().isAir()) {
                player.sendMessage("§eEste espaço já tem um upgrade. Clique com o cursor vazio para tirar.");
                return;
            }
            ItemStack item = entradas[indice];
            if (!InventorySpace.fits(player.getInventory(), item)) {
                player.sendMessage("§cSeu inventário está cheio.");
                return;
            }
            entradas[indice] = null; // sai do menu antes de voltar ao inventário
            MaquinaManager.dar(player, List.of(item));
        } else {
            if (cursor.getType().isAir()) {
                return;
            }
            if (itens.lerUpgrade(cursor) == null) {
                player.sendMessage("§cIsso não é um item de upgrade.");
                return;
            }
            entradas[indice] = cursor.asOne();
            ItemStack resto = cursor.clone();
            resto.setAmount(cursor.getAmount() - 1);
            player.setItemOnCursor(resto.getAmount() <= 0 ? null : resto);
        }
        menu.redesenhar();
    }

    private void juntar(Player player, ItemStack[] entradas, Menu menu) {
        Upgrade a = itens.lerUpgrade(entradas[0]);
        Upgrade b = itens.lerUpgrade(entradas[1]);
        if (a == null || b == null || !a.equals(b) || a.nivel() >= config.nivelMaximo(a.tipo())) {
            player.sendMessage("§cColoque 2 upgrades do mesmo tipo e nível (abaixo do máximo).");
            return;
        }
        Upgrade resultado = new Upgrade(a.tipo(), a.nivel() + 1);
        ItemStack item = itens.criarUpgrade(resultado, 1);
        if (!InventorySpace.fits(player.getInventory(), item)) {
            player.sendMessage("§cSeu inventário está cheio.");
            return;
        }
        long custo = config.custoJuntar() * resultado.nivel();
        if (custo > 0) {
            Clan clan = clans.getClan(player.getUniqueId());
            if (clan == null) {
                player.sendMessage("§cJuntar custa " + EconomyManager.format(custo) + " do Cofre: você precisa de um clan.");
                return;
            }
            if (!bases.withdrawFromVault(clan, custo)) {
                player.sendMessage("§cO Cofre não tem " + EconomyManager.format(custo) + " acima do mínimo ("
                        + EconomyManager.format(bases.minimum(clan)) + ").");
                return;
            }
        }
        entradas[0] = null; // os dois saem antes de o resultado ser entregue
        entradas[1] = null;
        MaquinaManager.dar(player, List.of(item));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.3f);
        player.sendMessage("§aVocê juntou e recebeu " + resultado.nomeColorido() + "§a.");
        menu.redesenhar();
    }

    /** Fechou o menu de juntar: os itens dos espaços voltam (o que não couber cai no chão). */
    private static void devolverEntradas(Player player, ItemStack[] entradas) {
        List<ItemStack> lista = new ArrayList<>();
        for (int i = 0; i < entradas.length; i++) {
            if (entradas[i] != null) {
                lista.add(entradas[i]);
                entradas[i] = null;
            }
        }
        MaquinaManager.dar(player, lista);
    }
}
