package br.com.murilo.muriloplug.maquinas;

import br.com.murilo.muriloplug.economy.EconomyManager;
import br.com.murilo.muriloplug.maquinas.MaquinasConfig.CampoGeral;
import br.com.murilo.muriloplug.maquinas.MaquinasConfig.CampoTipo;
import br.com.murilo.muriloplug.maquinas.MaquinasConfig.Formato;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * Painel de controle do admin (/maquinas admin): edita TUDO do maquinas.yml dentro do jogo, pelos métodos da
 * {@link MaquinasConfig} (que salvam na hora e registram no console). Todos os menus são {@link Menu}s com
 * {@code painel = true}: só funcionam com a permissão, e itens da mão/cursor só são lidos, nunca consumidos.
 *
 * <p>Telas: principal → tipo (campos, upgrades permitidos, tabela de produção → produto), combustíveis, upgrades
 * (→ editor de cada upgrade), regras gerais e máquinas no mundo (teleportar, remover, abrir como admin). Todo número
 * abre o mesmo editor ({@link #numero}), com passos que dependem do formato do campo.
 */
public final class PainelMaquinas implements Listener {

    /** Um campo numérico editável: nome, explicação, formato, limites, leitura e escrita. */
    record Campo(String nome, String explicacao, Formato formato, double min, double max, DoubleSupplier ler,
                 DoubleConsumer gravar) {
    }

    private static final int[] SLOTS_NUMERO_MENOS = {12, 11, 10, 9};
    private static final int[] SLOTS_NUMERO_MAIS = {14, 15, 16, 17};

    private final JavaPlugin plugin;
    private final MaquinaManager manager;
    private final MaquinasMenus menus;
    private final MaquinasConfig config;
    private final MaquinaItens itens;
    /** Admins digitando um texto no chat (nome de máquina). */
    private final Map<UUID, Consumer<String>> esperandoChat = new ConcurrentHashMap<>();

    public PainelMaquinas(JavaPlugin plugin, MaquinaManager manager, MaquinasMenus menus) {
        this.plugin = plugin;
        this.manager = manager;
        this.menus = menus;
        this.config = manager.config();
        this.itens = manager.itens();
    }

    private static Menu menu(int linhas, String titulo) {
        Menu menu = new Menu(linhas, titulo);
        menu.painel = true;
        return menu;
    }

    private static String quem(Player player) {
        return player.getName();
    }

    /** Item segurado: o do cursor, ou o da mão principal (só lido, nunca consumido). */
    private static ItemStack segurado(Player player) {
        ItemStack cursor = player.getItemOnCursor();
        return !cursor.getType().isAir() ? cursor : player.getInventory().getItemInMainHand();
    }

    // ------------------------------------------------------------ principal

    public void abrirPrincipal(Player player) {
        if (!player.hasPermission(MaquinasMenusListener.ADMIN_PERMISSION)) {
            player.sendMessage("§cVocê não tem permissão.");
            return;
        }
        menu(5, "§4Painel de Máquinas").desenho(m -> {
            for (int i = 0; i < MaquinaTipo.values().length && i < MaquinasMenus.SLOTS_TIPO.length; i++) {
                MaquinaTipo tipo = MaquinaTipo.values()[i];
                m.set(MaquinasMenus.SLOTS_TIPO[i], Icone.de(config.bloco(tipo), config.nome(tipo),
                        "§7Chave: §f" + tipo.name(),
                        config.ativa(tipo) ? "§aAtiva" : "§cDesativada",
                        "§7Preço: §a" + EconomyManager.format(config.preco(tipo)),
                        "§7No mundo: §f" + manager.todas().stream().filter(x -> x.tipo == tipo).count(),
                        "", "§eClique para editar"), (p, e) -> abrirTipo(p, tipo));
            }
            m.set(28, Icone.de(Material.COAL, "§6Combustíveis", "§7Itens aceitos e quanto valem."),
                    (p, e) -> abrirCombustiveis(p));
            m.set(30, Icone.de(Material.ANVIL, "§bUpgrades", "§7Nível máximo, valor por nível,",
                    "§7limites, taxa e custo de juntar."), (p, e) -> abrirUpgrades(p));
            m.set(32, Icone.de(Material.COMPARATOR, "§eRegras gerais", "§7Condição de funcionamento,",
                    "§7Engenheiro, regeneração..."), (p, e) -> abrirRegras(p));
            m.set(34, Icone.de(Material.FILLED_MAP, "§aMáquinas no mundo", "§7Total: §f" + manager.todas().size()),
                    (p, e) -> abrirMundo(p, 0, null, null));
            boolean teste = config.modoTeste();
            m.set(40, Icone.de(teste ? Material.LIME_DYE : Material.GRAY_DYE,
                    "§dModo teste: " + (teste ? "§aON" : "§cOFF"),
                    "§7Ligado: as máquinas ficam §fx" + MaquinasConfig.decimal(config.geral(CampoGeral.TESTE_MULTIPLICADOR))
                            + " §7mais rápidas", "§7e o holograma mostra [MODO TESTE].", "",
                    "§eClique para " + (teste ? "desligar" : "ligar")), (p, e) -> {
                boolean novo = !config.modoTeste();
                config.setModoTeste(novo, quem(p));
                avisarOps("§d[Máquinas] " + p.getName() + (novo ? " LIGOU" : " DESLIGOU") + " o modo teste"
                        + (novo ? " (x" + MaquinasConfig.decimal(config.geral(CampoGeral.TESTE_MULTIPLICADOR)) + ")." : "."));
                m.redesenhar();
            });
            m.set(44, Icone.de(Material.WRITABLE_BOOK, "§fRecarregar maquinas.yml",
                    "§7Relê o arquivo (para quem editou à mão)."), (p, e) -> {
                config.reload(quem(p));
                p.sendMessage("§amaquinas.yml recarregado.");
                m.redesenhar();
            });
            m.preencher(0, 44);
        }).abrir(player);
    }

    private static void avisarOps(String mensagem) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.isOp() || online.hasPermission(MaquinasMenusListener.ADMIN_PERMISSION)) {
                online.sendMessage(mensagem);
            }
        }
    }

    // ------------------------------------------------------------ editor de número

    /** Ícone de um campo numérico (nome, valor atual, explicação). */
    private static ItemStack iconeCampo(Material material, Campo campo) {
        return Icone.de(material, "§e" + campo.nome(),
                "§7Atual: §f" + MaquinasConfig.formatar(campo.formato(), campo.ler().getAsDouble()),
                "§8" + campo.explicacao(), "", "§eClique para ajustar");
    }

    /** Submenu -passos | valor | +passos, no estilo dos botões de preço da /loja. */
    void numero(Player player, Campo campo, Consumer<Player> voltar) {
        menu(3, "§4" + campo.nome()).desenho(m -> {
            double atual = campo.ler().getAsDouble();
            double[] passos = campo.formato().passos();
            for (int i = 0; i < passos.length && i < 4; i++) {
                double passo = passos[i];
                String texto = MaquinasConfig.formatar(campo.formato() == Formato.PORCENTAGEM ? Formato.PORCENTAGEM
                        : campo.formato() == Formato.DINHEIRO ? Formato.DINHEIRO : Formato.DECIMAL, passo);
                m.set(SLOTS_NUMERO_MENOS[i], Icone.de(Material.RED_STAINED_GLASS_PANE, "§c-" + texto),
                        (p, e) -> ajustar(p, campo, -passo, m));
                m.set(SLOTS_NUMERO_MAIS[i], Icone.de(Material.LIME_STAINED_GLASS_PANE, "§a+" + texto),
                        (p, e) -> ajustar(p, campo, passo, m));
            }
            m.set(13, Icone.de(Material.NAME_TAG, "§e" + campo.nome() + ": §f"
                    + MaquinasConfig.formatar(campo.formato(), atual), "§8" + campo.explicacao(),
                    "§7Mínimo: §f" + MaquinasConfig.formatar(campo.formato(), campo.min())
                            + " §7| máximo: §f" + MaquinasConfig.formatar(campo.formato(), campo.max())));
            m.set(22, Icone.de(Material.ARROW, "§7Voltar"), (p, e) -> voltar.accept(p));
            m.preencher(0, 26);
        }).abrir(player);
    }

    private static void ajustar(Player player, Campo campo, double delta, Menu menu) {
        double novo = Math.max(campo.min(), Math.min(campo.max(), campo.ler().getAsDouble() + delta));
        campo.gravar().accept(Math.round(novo * 10_000) / 10_000.0);
        menu.redesenhar();
    }

    private Campo campoTipo(Player player, MaquinaTipo tipo, CampoTipo c) {
        return new Campo(c.nome(), c.explicacao(), c.formato(), c.min(), c.max(), () -> config.numero(tipo, c),
                valor -> config.setNumero(tipo, c, valor, quem(player)));
    }

    private Campo campoGeral(Player player, CampoGeral c) {
        return new Campo(c.nome(), c.explicacao(), c.formato(), c.min(), c.max(), () -> config.geral(c),
                valor -> config.setGeral(c, valor, quem(player)));
    }

    // ------------------------------------------------------------ tipo de máquina

    private static final int[] SLOTS_CAMPOS_TIPO = {18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30};

    void abrirTipo(Player player, MaquinaTipo tipo) {
        menu(6, "§4Editar " + tipo.name()).desenho(m -> {
            double ciclo = config.ciclo(tipo);
            double ciclosHora = ciclo > 0 ? 3600 / ciclo : 0;
            m.set(4, Icone.de(config.bloco(tipo), config.nome(tipo),
                    "§7Resumo (sem upgrades, velocidade normal):",
                    "§7Itens por hora (média): §f" + MaquinasConfig.decimal(ciclosHora * MaquinaCalculos.mediaPorCiclo(config, tipo)),
                    "§7Combustível por hora: §f" + MaquinasConfig.decimal(ciclosHora * config.combustivelPorCiclo(tipo)),
                    "§7Ciclos por hora: §f" + MaquinasConfig.decimal(ciclosHora),
                    config.custoHora(tipo) > 0 ? "§7Custo do Cofre: §f"
                            + EconomyManager.format(Math.round(config.custoHora(tipo))) + "/hora" : null));
            boolean ativa = config.ativa(tipo);
            m.set(9, Icone.de(ativa ? Material.LIME_DYE : Material.RED_DYE, ativa ? "§aAtiva" : "§cDesativada",
                    "§7Desativada: não compra, não coloca", "§7e as do mundo param.", "", "§eClique para alternar"),
                    (p, e) -> {
                        String problema = config.setAtiva(tipo, !config.ativa(tipo), quem(p));
                        if (problema != null) {
                            p.sendMessage("§c" + problema);
                        }
                        m.redesenhar();
                    });
            m.set(10, Icone.de(Material.NAME_TAG, "§eNome: " + config.nome(tipo), "§eClique e digite o nome no chat",
                    "§7(use & para cores; \"cancelar\" para desistir)"), (p, e) -> pedirNome(p, tipo));
            m.set(11, Icone.de(config.bloco(tipo), "§eBloco: §f" + config.bloco(tipo).name(),
                    "§7Clique segurando um bloco (no cursor", "§7ou na mão) para trocar.",
                    "§8Máquinas já colocadas continuam iguais."), (p, e) -> {
                ItemStack item = segurado(p);
                String problema = config.setBloco(tipo, item.getType(), quem(p));
                if (problema != null) {
                    p.sendMessage("§c" + problema);
                }
                m.redesenhar();
            });
            m.set(13, Icone.de(Material.CHEST, "§eTabela de produção", MaquinaCalculos.linhasProducao(config, tipo, 0),
                    "", "§eClique para editar"), (p, e) -> abrirTabela(p, tipo));
            CampoTipo[] campos = CampoTipo.values();
            for (int i = 0; i < campos.length && i < SLOTS_CAMPOS_TIPO.length; i++) {
                CampoTipo campo = campos[i];
                m.set(SLOTS_CAMPOS_TIPO[i], iconeCampo(Material.PAPER, campoTipo(player, tipo, campo)),
                        (p, e) -> numero(p, campoTipo(p, tipo, campo), pp -> abrirTipo(pp, tipo)));
            }
            UpgradeTipo[] upgrades = UpgradeTipo.values();
            m.set(35, Icone.de(Material.ENCHANTED_BOOK, "§bUpgrades permitidos", "§7Clique em cada um para alternar."));
            for (int i = 0; i < upgrades.length; i++) {
                UpgradeTipo upgrade = upgrades[i];
                boolean permitido = config.upgradePermitido(tipo, upgrade);
                m.set(36 + i, Icone.de(upgrade.material(), (permitido ? "§a" : "§c") + upgrade.nome(),
                        permitido ? "§aPermitido" : "§cNão permitido", "§eClique para alternar"), (p, e) -> {
                    config.setUpgradePermitido(tipo, upgrade, !config.upgradePermitido(tipo, upgrade), quem(p));
                    m.redesenhar();
                });
            }
            m.set(49, Icone.de(Material.ARROW, "§7Voltar"), (p, e) -> abrirPrincipal(p));
            m.preencher(0, 53);
        }).abrir(player);
    }

    private void pedirNome(Player player, MaquinaTipo tipo) {
        player.closeInventory();
        player.sendMessage("§eDigite no chat o novo nome da " + config.nome(tipo)
                + " §e(use & para cores, ex: &7Máquina de Minérios). Digite \"cancelar\" para desistir.");
        esperandoChat.put(player.getUniqueId(), texto -> {
            if (!texto.equalsIgnoreCase("cancelar") && !texto.isBlank()) {
                config.setNome(tipo, texto.replace('&', '§'), quem(player));
                player.sendMessage("§aNome trocado para " + config.nome(tipo));
            }
            abrirTipo(player, tipo);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Consumer<String> acao = esperandoChat.remove(event.getPlayer().getUniqueId());
        if (acao == null) {
            return;
        }
        event.setCancelled(true);
        String texto = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline() && event.getPlayer().hasPermission(MaquinasMenusListener.ADMIN_PERMISSION)) {
                acao.accept(texto);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        esperandoChat.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------ tabela de produção

    void abrirTabela(Player player, MaquinaTipo tipo) {
        Menu menu = menu(6, "§4Produção " + tipo.name());
        menu.inventarioLivre = true; // o admin pode pegar um item do inventário no cursor para adicionar
        menu.desenho(m -> {
            List<Produto> tabela = config.producao(tipo);
            Map<Produto, Double> chances = MaquinaCalculos.chances(tabela, 0);
            for (int i = 0; i < tabela.size() && i < 45; i++) {
                Produto produto = tabela.get(i);
                m.set(i, Icone.com(new ItemStack(produto.item()), null,
                        "§7Peso: §f" + MaquinasConfig.decimal(produto.peso()),
                        "§7Chance: §e" + MaquinaCalculos.porcento(chances.get(produto)),
                        "§7Quantidade: §f" + produto.min() + "-" + produto.max(),
                        produto.raro() ? "§d★ Raro" : "§7Comum", "", "§eClique para editar"),
                        (p, e) -> abrirProduto(p, tipo, produto.item()));
            }
            m.set(45, Icone.de(Material.HOPPER, "§aAdicionar item da mão",
                    "§7Adiciona o item do cursor (ou da mão)", "§7com peso 1 e quantidade 1-1.",
                    "§8O item não é gasto."), (p, e) -> {
                ItemStack item = segurado(p);
                if (item.getType().isAir()) {
                    p.sendMessage("§cSegure um item (no cursor ou na mão).");
                    return;
                }
                List<Produto> nova = new ArrayList<>(config.producao(tipo));
                if (nova.stream().anyMatch(x -> x.item() == item.getType())) {
                    p.sendMessage("§cEste item já está na tabela.");
                    return;
                }
                nova.add(new Produto(item.getType(), 1, 1, 1, false));
                avisar(p, config.setProducao(tipo, nova, quem(p)));
                m.redesenhar();
            });
            if (tabela.isEmpty()) {
                m.set(22, Icone.de(Material.BARRIER, "§cTabela vazia", "§7Máquina com tabela vazia não produz."));
            }
            m.set(49, Icone.de(Material.ARROW, "§7Voltar"), (p, e) -> abrirTipo(p, tipo));
            m.preencher(45, 53);
        });
        menu.abrir(player);
    }

    private static void avisar(Player player, String problema) {
        if (problema != null) {
            player.sendMessage("§c" + problema);
        }
    }

    private Produto produto(MaquinaTipo tipo, Material material) {
        return config.producao(tipo).stream().filter(x -> x.item() == material).findFirst().orElse(null);
    }

    /** Troca o produto (achado pelo material) na tabela. */
    private void trocarProduto(Player player, MaquinaTipo tipo, Material material,
                               java.util.function.UnaryOperator<Produto> mudanca) {
        List<Produto> nova = new ArrayList<>(config.producao(tipo));
        nova.replaceAll(x -> x.item() == material ? mudanca.apply(x) : x);
        avisar(player, config.setProducao(tipo, nova, quem(player)));
    }

    void abrirProduto(Player player, MaquinaTipo tipo, Material material) {
        menu(3, "§4Produto").desenho(m -> {
            Produto produto = produto(tipo, material);
            if (produto == null) {
                m.set(13, Icone.de(Material.BARRIER, "§cEste item não está mais na tabela"));
                m.set(22, Icone.de(Material.ARROW, "§7Voltar"), (p, e) -> abrirTabela(p, tipo));
                return;
            }
            double chance = MaquinaCalculos.chances(config.producao(tipo), 0).getOrDefault(produto, 0.0);
            m.set(4, Icone.com(new ItemStack(material), null, "§7Chance atual: §e" + MaquinaCalculos.porcento(chance)));
            Campo peso = new Campo("Peso", "Chance = peso / soma dos pesos.", Formato.DECIMAL, 0.01, 1_000_000,
                    () -> produto(tipo, material) == null ? 0 : produto(tipo, material).peso(),
                    valor -> trocarProduto(player, tipo, material, x -> x.comPeso(valor)));
            Campo min = new Campo("Quantidade mínima", "Mínimo por sorteio.", Formato.INTEIRO, 1, 10_000,
                    () -> produto(tipo, material) == null ? 1 : produto(tipo, material).min(),
                    valor -> trocarProduto(player, tipo, material, x -> x.comMin((int) Math.round(valor))));
            Campo max = new Campo("Quantidade máxima", "Máximo por sorteio.", Formato.INTEIRO, 1, 10_000,
                    () -> produto(tipo, material) == null ? 1 : produto(tipo, material).max(),
                    valor -> trocarProduto(player, tipo, material, x -> x.comMax((int) Math.round(valor))));
            Consumer<Player> voltar = p -> abrirProduto(p, tipo, material);
            m.set(10, iconeCampo(Material.PAPER, peso), (p, e) -> numero(p, peso, voltar));
            m.set(11, iconeCampo(Material.PAPER, min), (p, e) -> numero(p, min, voltar));
            m.set(12, iconeCampo(Material.PAPER, max), (p, e) -> numero(p, max, voltar));
            m.set(14, Icone.de(produto.raro() ? Material.AMETHYST_SHARD : Material.FLINT,
                    produto.raro() ? "§d★ Raro" : "§7Comum", "§7Raros ganham peso com a Sorte.", "§eClique para alternar"),
                    (p, e) -> {
                        trocarProduto(p, tipo, material, x -> x.comRaro(!x.raro()));
                        m.redesenhar();
                    });
            m.set(16, Icone.de(Material.LAVA_BUCKET, "§4Remover da tabela"), (p, e) -> confirmar(p,
                    "§4Remover " + material.name() + "?", pp -> {
                        List<Produto> nova = new ArrayList<>(config.producao(tipo));
                        nova.removeIf(x -> x.item() == material);
                        String problema = config.setProducao(tipo, nova, quem(pp));
                        avisar(pp, problema);
                        if (problema == null) {
                            abrirTabela(pp, tipo);
                        } else {
                            abrirProduto(pp, tipo, material);
                        }
                    }, pp -> abrirProduto(pp, tipo, material)));
            m.set(22, Icone.de(Material.ARROW, "§7Voltar"), (p, e) -> abrirTabela(p, tipo));
            m.preencher(0, 26);
        }).abrir(player);
    }

    /** Menu verde/vermelho de confirmação. */
    private void confirmar(Player player, String titulo, Consumer<Player> sim, Consumer<Player> nao) {
        menu(3, titulo).desenho(m -> {
            m.set(11, Icone.de(Material.LIME_CONCRETE, "§aConfirmar"), (p, e) -> sim.accept(p));
            m.set(15, Icone.de(Material.RED_CONCRETE, "§cCancelar"), (p, e) -> nao.accept(p));
            m.preencher(0, 26);
        }).abrir(player);
    }

    // ------------------------------------------------------------ combustíveis

    void abrirCombustiveis(Player player) {
        Menu menu = menu(6, "§4Combustíveis");
        menu.inventarioLivre = true;
        menu.desenho(m -> {
            int slot = 0;
            for (Map.Entry<Material, Double> entry : config.combustiveis().entrySet()) {
                if (slot >= 45) {
                    break;
                }
                Material material = entry.getKey();
                Campo valor = new Campo("Valor de " + material.name(), "Unidades de combustível por item.",
                        Formato.DECIMAL, 0.1, 100_000, () -> config.valorCombustivel(material),
                        v -> config.setCombustivel(material, v, quem(player)));
                m.set(slot++, Icone.com(new ItemStack(material), null,
                        "§7Vale: §f" + MaquinasConfig.decimal(entry.getValue()) + " §7unidades", "",
                        "§eEsquerdo: alterar valor", "§cShift + direito: remover"), (p, e) -> {
                    if (e.isShiftClick() && e.isRightClick()) {
                        config.setCombustivel(material, 0, quem(p));
                        m.redesenhar();
                    } else {
                        numero(p, valor, this::abrirCombustiveis);
                    }
                });
            }
            m.set(45, Icone.de(Material.HOPPER, "§aAdicionar item da mão", "§7Adiciona o item do cursor (ou da mão)",
                    "§7valendo 1 unidade.", "§8O item não é gasto."), (p, e) -> {
                ItemStack item = segurado(p);
                if (item.getType().isAir()) {
                    p.sendMessage("§cSegure um item (no cursor ou na mão).");
                    return;
                }
                if (config.valorCombustivel(item.getType()) > 0) {
                    p.sendMessage("§eEste item já é combustível.");
                    return;
                }
                config.setCombustivel(item.getType(), 1, quem(p));
                m.redesenhar();
            });
            m.set(49, Icone.de(Material.ARROW, "§7Voltar"), (p, e) -> abrirPrincipal(p));
            m.preencher(45, 53);
        });
        menu.abrir(player);
    }

    // ------------------------------------------------------------ upgrades

    void abrirUpgrades(Player player) {
        menu(5, "§4Upgrades").desenho(m -> {
            UpgradeTipo[] upgrades = UpgradeTipo.values();
            for (int i = 0; i < upgrades.length; i++) {
                UpgradeTipo upgrade = upgrades[i];
                m.set(9 + i + (i >= 4 ? 1 : 0), Icone.de(upgrade.material(), "§b" + upgrade.nome(),
                        "§7Nível máximo: §f" + config.nivelMaximo(upgrade),
                        "§7Por nível: §f" + MaquinasConfig.formatar(MaquinasConfig.formatoUpgrade(upgrade),
                                config.valorPorNivel(upgrade)),
                        "§7Nível I: §f" + itens.efeito(new Upgrade(upgrade, 1)), "", "§eClique para editar"),
                        (p, e) -> abrirUpgrade(p, upgrade));
            }
            CampoGeral[] gerais = {CampoGeral.CICLO_MINIMO, CampoGeral.COMBUSTIVEL_MINIMO, CampoGeral.TAXA_VENDA,
                    CampoGeral.CUSTO_JUNTAR};
            for (int i = 0; i < gerais.length; i++) {
                CampoGeral geral = gerais[i];
                m.set(28 + i * 2, iconeCampo(Material.PAPER, campoGeral(player, geral)),
                        (p, e) -> numero(p, campoGeral(p, geral), this::abrirUpgrades));
            }
            m.set(40, Icone.de(Material.ARROW, "§7Voltar"), (p, e) -> abrirPrincipal(p));
            m.preencher(0, 44);
        }).abrir(player);
    }

    void abrirUpgrade(Player player, UpgradeTipo upgrade) {
        menu(4, "§4Upgrade " + upgrade.nome()).desenho(m -> {
            Consumer<Player> voltar = p -> abrirUpgrade(p, upgrade);
            Campo maximo = new Campo("Nível máximo", "Até que nível o upgrade vai (juntar para no máximo).",
                    Formato.INTEIRO, 1, 100, () -> config.nivelMaximo(upgrade),
                    v -> config.setNivelMaximo(upgrade, (int) Math.round(v), quem(player)));
            Campo valor = new Campo("Valor por nível", "Efeito de cada nível.", MaquinasConfig.formatoUpgrade(upgrade),
                    0, upgrade.porcentagem() ? 1 : 1_000_000, () -> config.valorPorNivel(upgrade),
                    v -> config.setValorPorNivel(upgrade, v, quem(player)));
            m.set(10, iconeCampo(Material.PAPER, maximo), (p, e) -> numero(p, maximo, voltar));
            m.set(11, iconeCampo(Material.PAPER, valor), (p, e) -> numero(p, valor, voltar));
            if (upgrade == UpgradeTipo.ESTOQUE) {
                Campo combustivel = new Campo("Combustível por nível", "Combustível máximo a mais por nível.",
                        Formato.INTEIRO, 0, 1_000_000, config::estoqueCombustivelPorNivel,
                        v -> config.setEstoqueCombustivelPorNivel(v, quem(player)));
                m.set(12, iconeCampo(Material.PAPER, combustivel), (p, e) -> numero(p, combustivel, voltar));
            }
            m.set(4, Icone.de(upgrade.material(), "§b" + upgrade.nome(),
                    "§7Nível I: §f" + itens.efeito(new Upgrade(upgrade, 1)),
                    "§7Nível máx: §f" + itens.efeito(new Upgrade(upgrade, config.nivelMaximo(upgrade)))));
            int niveis = Math.min(9, config.nivelMaximo(upgrade));
            for (int nivel = 1; nivel <= niveis; nivel++) {
                Upgrade item = new Upgrade(upgrade, nivel);
                m.set(18 + nivel - 1, Icone.mais(itens.criarUpgrade(item, 1), "", "§ePegar 1 §7(Shift: 64)"),
                        (p, e) -> MaquinaManager.dar(p, List.of(itens.criarUpgrade(item, e.isShiftClick() ? 64 : 1))));
            }
            m.set(31, Icone.de(Material.ARROW, "§7Voltar"), (p, e) -> abrirUpgrades(p));
            m.preencher(0, 35);
        }).abrir(player);
    }

    // ------------------------------------------------------------ regras gerais

    void abrirRegras(Player player) {
        menu(4, "§4Regras gerais").desenho(m -> {
            MaquinasConfig.Condicao condicao = config.condicao();
            m.set(10, Icone.de(Material.REPEATER, "§eCondição: §f" + condicao.name(), "§7" + condicao.descricao(),
                    "", "§eClique para alternar", "§8SEMPRE → CLAN_ONLINE → MEMBRO_PERTO"), (p, e) -> {
                config.setCondicao(config.condicao().proxima(), quem(p));
                m.redesenhar();
            });
            boolean lider = config.apenasLiderCompra();
            m.set(11, Icone.de(lider ? Material.GOLDEN_HELMET : Material.LEATHER_HELMET,
                    "§eApenas o líder compra: " + (lider ? "§aSim" : "§cNão"), "§eClique para alternar"), (p, e) -> {
                config.setApenasLiderCompra(!config.apenasLiderCompra(), quem(p));
                m.redesenhar();
            });
            CampoGeral[] campos = {CampoGeral.RAIO_MEMBRO, CampoGeral.REGENERACAO, CampoGeral.VELOCIDADE_GLOBAL,
                    CampoGeral.TESTE_MULTIPLICADOR, CampoGeral.ENG_BONUS, CampoGeral.ENG_RAIO,
                    CampoGeral.ENG_REPARO_VIDA, CampoGeral.ENG_REPARO_ENERGIA};
            int[] slots = {12, 13, 14, 15, 19, 20, 21, 22};
            for (int i = 0; i < campos.length; i++) {
                CampoGeral campo = campos[i];
                m.set(slots[i], iconeCampo(campo.name().startsWith("ENG") ? Material.IRON_PICKAXE : Material.PAPER,
                        campoGeral(player, campo)), (p, e) -> numero(p, campoGeral(p, campo), this::abrirRegras));
            }
            m.set(31, Icone.de(Material.ARROW, "§7Voltar"), (p, e) -> abrirPrincipal(p));
            m.preencher(0, 35);
        }).abrir(player);
    }

    // ------------------------------------------------------------ máquinas no mundo

    void abrirMundo(Player player, int pagina, MaquinaTipo filtroTipo, String filtroClan) {
        menu(6, "§4Máquinas no mundo").desenho(m -> {
            List<Maquina> lista = new ArrayList<>(manager.todas().stream()
                    .filter(x -> filtroTipo == null || x.tipo == filtroTipo)
                    .filter(x -> filtroClan == null || x.clan.equalsIgnoreCase(filtroClan))
                    .toList());
            lista.sort(Comparator.comparing((Maquina x) -> x.clan).thenComparing(x -> x.tipo));
            int paginas = Math.max(1, (lista.size() + 44) / 45);
            int atual = Math.max(0, Math.min(paginas - 1, pagina));
            for (int i = 0; i < 45 && atual * 45 + i < lista.size(); i++) {
                Maquina maquina = lista.get(atual * 45 + i);
                m.set(i, Icone.mais(menus.iconeResumo(maquina), "", "§eEsquerdo: teleportar",
                        "§cDireito: remover", "§bShift + clique: abrir como admin"), (p, e) -> {
                    if (manager.porId(maquina.id) == null) {
                        p.sendMessage("§cEsta máquina não existe mais.");
                        m.redesenhar();
                    } else if (e.isShiftClick()) {
                        menus.abrirMaquina(p, maquina, true);
                    } else if (e.isRightClick()) {
                        abrirRemover(p, maquina, pp -> abrirMundo(pp, atual, filtroTipo, filtroClan));
                    } else {
                        teleportar(p, maquina);
                    }
                });
            }
            if (lista.isEmpty()) {
                m.set(22, Icone.de(Material.BARRIER, "§7Nenhuma máquina"));
            }
            m.set(45, Icone.de(Material.HOPPER, "§eFiltro de tipo: §f" + (filtroTipo == null ? "Todos" : filtroTipo.name()),
                    "§eClique para trocar"), (p, e) -> {
                MaquinaTipo[] tipos = MaquinaTipo.values();
                MaquinaTipo proximo = filtroTipo == null ? tipos[0]
                        : filtroTipo.ordinal() + 1 < tipos.length ? tipos[filtroTipo.ordinal() + 1] : null;
                abrirMundo(p, 0, proximo, filtroClan);
            });
            List<String> tags = manager.todas().stream().map(x -> x.clan).distinct().sorted().toList();
            m.set(46, Icone.de(Material.WHITE_BANNER, "§eFiltro de clan: §f" + (filtroClan == null ? "Todos" : "[" + filtroClan + "]"),
                    "§eClique para trocar"), (p, e) -> {
                int indice = filtroClan == null ? -1 : tags.indexOf(filtroClan);
                String proximo = indice + 1 < tags.size() ? tags.get(indice + 1) : null;
                abrirMundo(p, 0, filtroTipo, proximo);
            });
            if (atual > 0) {
                m.set(48, Icone.de(Material.ARROW, "§7Página anterior"),
                        (p, e) -> abrirMundo(p, atual - 1, filtroTipo, filtroClan));
            }
            m.set(49, Icone.de(Material.OAK_DOOR, "§7Voltar §8(página " + (atual + 1) + "/" + paginas + ")"),
                    (p, e) -> abrirPrincipal(p));
            if (atual + 1 < paginas) {
                m.set(50, Icone.de(Material.ARROW, "§7Próxima página"),
                        (p, e) -> abrirMundo(p, atual + 1, filtroTipo, filtroClan));
            }
            m.preencher(45, 53);
        }).abrir(player);
    }

    private static void teleportar(Player player, Maquina maquina) {
        Location centro = maquina.local.center();
        if (centro == null) {
            player.sendMessage("§cO mundo " + maquina.local.world() + " não está carregado.");
            return;
        }
        player.closeInventory();
        player.teleport(centro.add(0, 1, 0));
        player.sendMessage("§aTeleportado até a máquina de [" + maquina.clan + "].");
    }

    private void abrirRemover(Player player, Maquina maquina, Consumer<Player> voltar) {
        menu(3, "§4Remover máquina?").desenho(m -> {
            m.set(13, menus.iconeResumo(maquina));
            m.set(11, Icone.de(Material.CHEST, "§aDevolver item ao líder do clan",
                    "§7A máquina sai do mundo e o líder recebe", "§7o item, os produtos, o combustível e os",
                    "§7upgrades (agora ou quando entrar)."), (p, e) -> {
                manager.removerAdmin(maquina, true, quem(p));
                p.sendMessage("§aMáquina removida e itens devolvidos ao líder.");
                voltar.accept(p);
            });
            m.set(15, Icone.de(Material.LAVA_BUCKET, "§4Apagar", "§7A máquina e tudo dentro dela somem."), (p, e) -> {
                manager.removerAdmin(maquina, false, quem(p));
                p.sendMessage("§cMáquina apagada.");
                voltar.accept(p);
            });
            m.set(22, Icone.de(Material.ARROW, "§7Cancelar"), (p, e) -> voltar.accept(p));
            m.preencher(0, 26);
        }).abrir(player);
    }
}
