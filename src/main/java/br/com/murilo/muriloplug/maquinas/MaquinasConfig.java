package br.com.murilo.muriloplug.maquinas;

import br.com.murilo.muriloplug.economy.EconomyManager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Todos os números, itens e chances das máquinas, em plugins/MinePlug/maquinas.yml. Os valores padrão vêm do
 * maquinas.yml de dentro do jar (copiado na primeira vez e usado como reserva para chaves que faltarem): não há valor
 * de equilíbrio no código.
 *
 * <p>Cada valor tem um método para ler e outro para alterar. Alterar grava o arquivo na hora, registra no console
 * ("[Maquinas] quem mudou campo de X para Y") e avisa os ouvintes (hologramas, menus), então vale sem reiniciar.
 */
public final class MaquinasConfig {

    /** Quando as máquinas funcionam. */
    public enum Condicao {
        SEMPRE("Sempre"),
        CLAN_ONLINE("Com alguém do clan online"),
        MEMBRO_PERTO("Com um membro do clan perto");

        private final String descricao;

        Condicao(String descricao) {
            this.descricao = descricao;
        }

        public String descricao() {
            return descricao;
        }

        public Condicao proxima() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** Como um número aparece e com que passos é ajustado no painel. */
    public enum Formato {
        INTEIRO(1, 10, 100, 1000),
        DECIMAL(0.1, 1, 10, 100),
        PORCENTAGEM(0.01, 0.05, 0.1),
        SEGUNDOS(1, 10, 60),
        MINUTOS(1, 5, 10, 60),
        DINHEIRO(1, 100, 1000, 10000),
        BLOCOS(1, 5, 10);

        private final double[] passos;

        Formato(double... passos) {
            this.passos = passos;
        }

        /** Passos do menor para o maior. */
        public double[] passos() {
            return passos.clone();
        }

        public boolean inteiro() {
            return this == INTEIRO || this == SEGUNDOS || this == MINUTOS || this == DINHEIRO || this == BLOCOS;
        }
    }

    /** Números de cada tipo de máquina (tipos.&lt;TIPO&gt;.&lt;chave&gt;). */
    public enum CampoTipo {
        PRECO("preco", "Preço", "Dinheiro tirado do Cofre do clan na compra.", Formato.DINHEIRO, 0, 1.0E12),
        LIMITE("limite-por-clan", "Limite por clan", "Máximo desta máquina colocada por clan (0 = sem limite).",
                Formato.INTEIRO, 0, 10_000),
        CICLO("ciclo-segundos", "Tempo do ciclo", "Segundos entre uma produção e outra.", Formato.SEGUNDOS, 1, 86_400),
        SORTEIOS("sorteios-por-ciclo", "Sorteios por ciclo", "Itens sorteados por ciclo (o decimal acumula).",
                Formato.DECIMAL, 0, 1000),
        COMBUSTIVEL("combustivel-por-ciclo", "Combustível por ciclo", "Unidades gastas por ciclo (o decimal acumula).",
                Formato.DECIMAL, 0, 10_000),
        CUSTO_HORA("custo-cofre-por-hora", "Custo do Cofre por hora",
                "Dinheiro tirado do Cofre por hora funcionando (0 = desligado).", Formato.DINHEIRO, 0, 1.0E9),
        ARMAZEM("armazem-base", "Armazém base", "Máximo de itens guardados (somando tudo).", Formato.INTEIRO, 1, 1_000_000),
        COMBUSTIVEL_MAX("combustivel-maximo-base", "Combustível máximo base", "Máximo de unidades de combustível.",
                Formato.INTEIRO, 1, 1_000_000),
        SLOTS("slots-upgrade", "Slots de upgrade", "Quantos upgrades cabem na máquina (até 7).", Formato.INTEIRO, 0, 7),
        VIDA("vida", "Vida", "Explosões que a máquina aguenta (sem a Blindagem).", Formato.INTEIRO, 1, 10_000),
        ESCUDO("escudo-segundos", "Escudo", "Segundos sem tomar dano depois de cada explosão.", Formato.SEGUNDOS, 0, 3600),
        RAID_DROP("raid-drop-armazem", "Armazém que cai no raid",
                "Fração de cada produto e do combustível que cai ao ser destruída.", Formato.PORCENTAGEM, 0, 1),
        RAID_CHANCE("raid-chance-dropar-maquina", "Chance de cair a máquina",
                "Chance de o item da máquina cair ao ser destruída.", Formato.PORCENTAGEM, 0, 1);

        private final String chave;
        private final String nome;
        private final String explicacao;
        private final Formato formato;
        private final double min;
        private final double max;

        CampoTipo(String chave, String nome, String explicacao, Formato formato, double min, double max) {
            this.chave = chave;
            this.nome = nome;
            this.explicacao = explicacao;
            this.formato = formato;
            this.min = min;
            this.max = max;
        }

        public String chave() {
            return chave;
        }

        public String nome() {
            return nome;
        }

        public String explicacao() {
            return explicacao;
        }

        public Formato formato() {
            return formato;
        }

        public double min() {
            return min;
        }

        public double max() {
            return max;
        }
    }

    /** Números gerais (caminho completo no arquivo). */
    public enum CampoGeral {
        RAIO_MEMBRO("geral.raio-membro-perto", "Raio do membro perto", "Blocos para a condição MEMBRO_PERTO.",
                Formato.BLOCOS, 1, 1000),
        VELOCIDADE_GLOBAL("geral.velocidade-global", "Velocidade global",
                "Multiplicador de velocidade de todas as máquinas (1.0 = normal).", Formato.DECIMAL, 0.1, 1000),
        REGENERACAO("geral.regeneracao-minutos", "Regeneração de vida",
                "Minutos para recuperar 1 de vida fora de raid (0 = desligado).", Formato.MINUTOS, 0, 100_000),
        ENG_BONUS("engenheiro.bonus-velocidade", "Bônus do Engenheiro",
                "Velocidade a mais com um Engenheiro do clan perto.", Formato.PORCENTAGEM, 0, 10),
        ENG_RAIO("engenheiro.raio", "Raio do Engenheiro", "Blocos para o bônus do Engenheiro.", Formato.BLOCOS, 1, 200),
        ENG_REPARO_VIDA("engenheiro.reparo-vida", "Reparo do Engenheiro (vida)",
                "Vida recuperada por reparo (Shift + clique direito).", Formato.INTEIRO, 0, 10_000),
        ENG_REPARO_ENERGIA("engenheiro.reparo-energia", "Reparo do Engenheiro (energia)",
                "Energia gasta por reparo.", Formato.DECIMAL, 0, 10_000),
        CICLO_MINIMO("upgrades.ciclo-minimo", "Ciclo mínimo", "O ciclo nunca fica abaixo desta fração do base.",
                Formato.PORCENTAGEM, 0.01, 1),
        COMBUSTIVEL_MINIMO("upgrades.combustivel-minimo", "Combustível mínimo",
                "O combustível por ciclo nunca fica abaixo desta fração do base.", Formato.PORCENTAGEM, 0, 1),
        TAXA_VENDA("upgrades.venda-automatica-taxa", "Taxa da venda automática",
                "Parte do valor que fica com o servidor.", Formato.PORCENTAGEM, 0, 1),
        CUSTO_JUNTAR("upgrades.custo-juntar", "Custo de juntar",
                "Dinheiro do Cofre por nível do resultado ao juntar upgrades.", Formato.DINHEIRO, 0, 1.0E9),
        TESTE_MULTIPLICADOR("modo-teste.multiplicador", "Multiplicador do modo teste",
                "Quantas vezes mais rápido com o modo teste ligado.", Formato.DECIMAL, 1, 100_000);

        private final String caminho;
        private final String nome;
        private final String explicacao;
        private final Formato formato;
        private final double min;
        private final double max;

        CampoGeral(String caminho, String nome, String explicacao, Formato formato, double min, double max) {
            this.caminho = caminho;
            this.nome = nome;
            this.explicacao = explicacao;
            this.formato = formato;
            this.min = min;
            this.max = max;
        }

        public String caminho() {
            return caminho;
        }

        public String nome() {
            return nome;
        }

        public String explicacao() {
            return explicacao;
        }

        public Formato formato() {
            return formato;
        }

        public double min() {
            return min;
        }

        public double max() {
            return max;
        }
    }

    private static final String ARQUIVO = "maquinas.yml";

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration yaml = new YamlConfiguration();
    private final Map<MaquinaTipo, List<Produto>> producaoCache = new EnumMap<>(MaquinaTipo.class);
    private Map<Material, Double> combustiveisCache;
    private final List<Runnable> ouvintes = new CopyOnWriteArrayList<>();

    public MaquinasConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), ARQUIVO);
    }

    /** Chamado depois de cada alteração (e de cada recarga). */
    public void addListener(Runnable ouvinte) {
        ouvintes.add(ouvinte);
    }

    // ------------------------------------------------------------ arquivo

    /** Cria o arquivo com os padrões na primeira vez, lê e acrescenta as chaves que faltarem. */
    public void load() {
        if (!file.exists()) {
            plugin.saveResource(ARQUIVO, false);
        }
        YamlConfiguration lido = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration padroes = padroes();
        lido.setDefaults(padroes);
        boolean mudou = false;
        for (String path : padroes.getKeys(true)) {
            if (padroes.isConfigurationSection(path)) {
                continue;
            }
            // Listas de itens (combustíveis) que já existem não ganham de volta o que o admin tirou.
            if (path.startsWith("combustiveis.") && lido.isSet("combustiveis")) {
                continue;
            }
            if (!lido.isSet(path)) {
                lido.set(path, padroes.get(path));
                mudou = true;
            }
        }
        yaml = lido;
        limparCache();
        if (mudou) {
            save();
        }
    }

    /** Relê o arquivo (para quem editou à mão) e avisa os ouvintes. */
    public void reload(String quem) {
        load();
        plugin.getLogger().info("[Maquinas] " + quem + " recarregou o maquinas.yml");
        avisar();
    }

    private YamlConfiguration padroes() {
        try (InputStream in = plugin.getResource(ARQUIVO)) {
            if (in == null) {
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("Não foi possível ler o maquinas.yml padrão: " + e.getMessage());
            return new YamlConfiguration();
        }
    }

    public void save() {
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar maquinas.yml: " + e.getMessage());
        }
    }

    private void limparCache() {
        producaoCache.clear();
        combustiveisCache = null;
    }

    private void avisar() {
        ouvintes.forEach(Runnable::run);
    }

    /** Grava um valor, salva, registra no console e avisa. Não faz nada se o valor não mudou. */
    private void alterar(String path, Object novo, Object antigoMostrado, Object novoMostrado, String quem) {
        Object antigo = yaml.get(path);
        if (Objects.equals(antigo, novo)) {
            return;
        }
        yaml.set(path, novo);
        limparCache();
        save();
        plugin.getLogger().info("[Maquinas] " + quem + " mudou " + path + " de " + antigoMostrado + " para "
                + novoMostrado);
        avisar();
    }

    private void alterar(String path, Object novo, String quem) {
        alterar(path, novo, yaml.get(path), novo, quem);
    }

    // ------------------------------------------------------------ tipos de máquina

    private static String tp(MaquinaTipo tipo, String chave) {
        return "tipos." + tipo.name() + "." + chave;
    }

    public boolean ativa(MaquinaTipo tipo) {
        return yaml.getBoolean(tp(tipo, "ativa"));
    }

    /** Liga/desliga o tipo no servidor inteiro. Retorna o problema, ou null se mudou. */
    public String setAtiva(MaquinaTipo tipo, boolean ativa, String quem) {
        if (ativa && producao(tipo).isEmpty()) {
            return "A tabela de produção está vazia: adicione um item antes de ativar.";
        }
        alterar(tp(tipo, "ativa"), ativa, quem);
        return null;
    }

    public String nome(MaquinaTipo tipo) {
        return yaml.getString(tp(tipo, "nome"), tipo.name());
    }

    public void setNome(MaquinaTipo tipo, String nome, String quem) {
        alterar(tp(tipo, "nome"), nome, quem);
    }

    public Material bloco(MaquinaTipo tipo) {
        Material material = Material.matchMaterial(yaml.getString(tp(tipo, "bloco"), ""));
        if (material == null || !material.isBlock() || !material.isItem()) {
            material = Material.matchMaterial(String.valueOf(yaml.getDefaults() == null ? "STONE"
                    : yaml.getDefaults().getString(tp(tipo, "bloco"), "STONE")));
        }
        return material == null ? Material.STONE : material;
    }

    /** Troca o bloco (só blocos sólidos que viram item). Retorna o problema, ou null se mudou. */
    public String setBloco(MaquinaTipo tipo, Material bloco, String quem) {
        if (bloco == null || !bloco.isBlock() || !bloco.isItem() || !bloco.isSolid()) {
            return "Segure um bloco sólido na mão.";
        }
        alterar(tp(tipo, "bloco"), bloco.name(), quem);
        return null;
    }

    public double numero(MaquinaTipo tipo, CampoTipo campo) {
        return limitar(yaml.getDouble(tp(tipo, campo.chave())), campo.min(), campo.max(), campo.formato());
    }

    public void setNumero(MaquinaTipo tipo, CampoTipo campo, double valor, String quem) {
        double novo = limitar(valor, campo.min(), campo.max(), campo.formato());
        if (novo == numero(tipo, campo)) {
            return;
        }
        String path = tp(tipo, campo.chave());
        alterar(path, guardado(novo, campo.formato()), formatar(campo.formato(), numero(tipo, campo)),
                formatar(campo.formato(), novo), quem);
    }

    public long preco(MaquinaTipo tipo) {
        return Math.round(numero(tipo, CampoTipo.PRECO));
    }

    public int limite(MaquinaTipo tipo) {
        return (int) numero(tipo, CampoTipo.LIMITE);
    }

    public double ciclo(MaquinaTipo tipo) {
        return numero(tipo, CampoTipo.CICLO);
    }

    public double sorteios(MaquinaTipo tipo) {
        return numero(tipo, CampoTipo.SORTEIOS);
    }

    public double combustivelPorCiclo(MaquinaTipo tipo) {
        return numero(tipo, CampoTipo.COMBUSTIVEL);
    }

    public double custoHora(MaquinaTipo tipo) {
        return numero(tipo, CampoTipo.CUSTO_HORA);
    }

    public int armazemBase(MaquinaTipo tipo) {
        return (int) numero(tipo, CampoTipo.ARMAZEM);
    }

    public int combustivelMaximoBase(MaquinaTipo tipo) {
        return (int) numero(tipo, CampoTipo.COMBUSTIVEL_MAX);
    }

    public int slots(MaquinaTipo tipo) {
        return (int) numero(tipo, CampoTipo.SLOTS);
    }

    public int vida(MaquinaTipo tipo) {
        return (int) numero(tipo, CampoTipo.VIDA);
    }

    public double escudoSegundos(MaquinaTipo tipo) {
        return numero(tipo, CampoTipo.ESCUDO);
    }

    public double raidDropArmazem(MaquinaTipo tipo) {
        return numero(tipo, CampoTipo.RAID_DROP);
    }

    public double raidChanceDroparMaquina(MaquinaTipo tipo) {
        return numero(tipo, CampoTipo.RAID_CHANCE);
    }

    /** Tabela de produção (cópia; mudar com {@link #setProducao}). */
    public List<Produto> producao(MaquinaTipo tipo) {
        return producaoCache.computeIfAbsent(tipo, t -> {
            List<Produto> lista = new ArrayList<>();
            for (Map<?, ?> map : yaml.getMapList(tp(t, "producao"))) {
                Produto produto = Produto.deserialize(map);
                if (produto != null) {
                    lista.add(produto);
                }
            }
            return Collections.unmodifiableList(lista);
        });
    }

    /** Troca a tabela inteira. Retorna o problema, ou null se mudou. */
    public String setProducao(MaquinaTipo tipo, List<Produto> tabela, String quem) {
        if (tabela.isEmpty() && ativa(tipo)) {
            return "Uma máquina ativa não pode ficar com a tabela vazia (desative o tipo antes).";
        }
        List<Map<String, Object>> lista = tabela.stream().map(Produto::serialize).toList();
        alterar(tp(tipo, "producao"), lista, producao(tipo), tabela, quem);
        return null;
    }

    public boolean upgradePermitido(MaquinaTipo tipo, UpgradeTipo upgrade) {
        return yaml.getStringList(tp(tipo, "upgrades-bloqueados")).stream()
                .noneMatch(nome -> nome.equalsIgnoreCase(upgrade.name()));
    }

    public void setUpgradePermitido(MaquinaTipo tipo, UpgradeTipo upgrade, boolean permitido, String quem) {
        List<String> bloqueados = new ArrayList<>(yaml.getStringList(tp(tipo, "upgrades-bloqueados")));
        bloqueados.removeIf(nome -> nome.equalsIgnoreCase(upgrade.name()));
        if (!permitido) {
            bloqueados.add(upgrade.name());
        }
        String path = tp(tipo, "upgrades-bloqueados");
        alterar(path, bloqueados, quem);
    }

    // ------------------------------------------------------------ combustíveis

    /** Itens aceitos como combustível e quantas unidades cada um vale (cópia). */
    public Map<Material, Double> combustiveis() {
        if (combustiveisCache == null) {
            Map<Material, Double> mapa = new LinkedHashMap<>();
            ConfigurationSection section = yaml.isSet("combustiveis") ? yaml.getConfigurationSection("combustiveis") : null;
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    Material material = Material.matchMaterial(key);
                    double valor = section.getDouble(key);
                    if (material != null && material.isItem() && valor > 0) {
                        mapa.put(material, valor);
                    }
                }
            }
            combustiveisCache = Collections.unmodifiableMap(mapa);
        }
        return combustiveisCache;
    }

    /** Unidades de combustível de 1 item, ou 0 se não for combustível. */
    public double valorCombustivel(Material material) {
        return combustiveis().getOrDefault(material, 0.0);
    }

    /** Define quanto o item vale como combustível (0 ou menos = tira da lista). */
    public void setCombustivel(Material material, double valor, String quem) {
        if (!yaml.isSet("combustiveis")) {
            yaml.createSection("combustiveis");
        }
        double novo = valor <= 0 ? 0 : Math.round(valor * 100) / 100.0;
        String path = "combustiveis." + material.name();
        Object antigo = yaml.isSet(path) ? yaml.get(path) : "(nada)";
        if (novo <= 0) {
            if (!yaml.isSet(path)) {
                return;
            }
            yaml.set(path, null);
            limparCache();
            save();
            plugin.getLogger().info("[Maquinas] " + quem + " mudou " + path + " de " + antigo + " para (removido)");
            avisar();
            return;
        }
        alterar(path, novo, antigo, novo, quem);
    }

    // ------------------------------------------------------------ regras gerais

    public Condicao condicao() {
        try {
            return Condicao.valueOf(yaml.getString("geral.condicao", "CLAN_ONLINE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Condicao.CLAN_ONLINE;
        }
    }

    public void setCondicao(Condicao condicao, String quem) {
        alterar("geral.condicao", condicao.name(), quem);
    }

    public boolean apenasLiderCompra() {
        return yaml.getBoolean("geral.apenas-lider-compra");
    }

    public void setApenasLiderCompra(boolean valor, String quem) {
        alterar("geral.apenas-lider-compra", valor, quem);
    }

    public boolean modoTeste() {
        return yaml.getBoolean("modo-teste.ativo");
    }

    public void setModoTeste(boolean ativo, String quem) {
        alterar("modo-teste.ativo", ativo, quem);
    }

    public double geral(CampoGeral campo) {
        return limitar(yaml.getDouble(campo.caminho()), campo.min(), campo.max(), campo.formato());
    }

    public void setGeral(CampoGeral campo, double valor, String quem) {
        double novo = limitar(valor, campo.min(), campo.max(), campo.formato());
        if (novo == geral(campo)) {
            return;
        }
        alterar(campo.caminho(), guardado(novo, campo.formato()), formatar(campo.formato(), geral(campo)),
                formatar(campo.formato(), novo), quem);
    }

    public double raioMembroPerto() {
        return geral(CampoGeral.RAIO_MEMBRO);
    }

    public double regeneracaoMinutos() {
        return geral(CampoGeral.REGENERACAO);
    }

    public double engenheiroBonus() {
        return geral(CampoGeral.ENG_BONUS);
    }

    public double engenheiroRaio() {
        return geral(CampoGeral.ENG_RAIO);
    }

    public int engenheiroReparoVida() {
        return (int) geral(CampoGeral.ENG_REPARO_VIDA);
    }

    public double engenheiroReparoEnergia() {
        return geral(CampoGeral.ENG_REPARO_ENERGIA);
    }

    public double cicloMinimo() {
        return geral(CampoGeral.CICLO_MINIMO);
    }

    public double combustivelMinimo() {
        return geral(CampoGeral.COMBUSTIVEL_MINIMO);
    }

    public double taxaVendaAutomatica() {
        return geral(CampoGeral.TAXA_VENDA);
    }

    public long custoJuntar() {
        return Math.round(geral(CampoGeral.CUSTO_JUNTAR));
    }

    /** Multiplicador de velocidade valendo agora: o global, vezes o do modo teste se estiver ligado. */
    public double velocidadeAtual() {
        double global = geral(CampoGeral.VELOCIDADE_GLOBAL);
        return modoTeste() ? global * geral(CampoGeral.TESTE_MULTIPLICADOR) : global;
    }

    // ------------------------------------------------------------ upgrades

    private static String up(UpgradeTipo upgrade, String chave) {
        return "upgrades.tipos." + upgrade.name() + "." + chave;
    }

    public int nivelMaximo(UpgradeTipo upgrade) {
        return Math.max(1, Math.min(100, yaml.getInt(up(upgrade, "nivel-maximo"), 1)));
    }

    public void setNivelMaximo(UpgradeTipo upgrade, int nivel, String quem) {
        alterar(up(upgrade, "nivel-maximo"), Math.max(1, Math.min(100, nivel)), quem);
    }

    /** Efeito de cada nível (fração para os de porcentagem). */
    public double valorPorNivel(UpgradeTipo upgrade) {
        return Math.max(0, yaml.getDouble(up(upgrade, "valor-por-nivel")));
    }

    public void setValorPorNivel(UpgradeTipo upgrade, double valor, String quem) {
        Formato formato = formatoUpgrade(upgrade);
        double novo = limitar(valor, 0, upgrade.porcentagem() ? 1 : 1_000_000, formato);
        if (novo == valorPorNivel(upgrade)) {
            return;
        }
        alterar(up(upgrade, "valor-por-nivel"), guardado(novo, formato), formatar(formato, valorPorNivel(upgrade)),
                formatar(formato, novo), quem);
    }

    /** ESTOQUE: combustível máximo a mais por nível. */
    public double estoqueCombustivelPorNivel() {
        return Math.max(0, yaml.getDouble(up(UpgradeTipo.ESTOQUE, "combustivel-por-nivel")));
    }

    public void setEstoqueCombustivelPorNivel(double valor, String quem) {
        double novo = limitar(valor, 0, 1_000_000, Formato.INTEIRO);
        alterar(up(UpgradeTipo.ESTOQUE, "combustivel-por-nivel"), guardado(novo, Formato.INTEIRO), quem);
    }

    public static Formato formatoUpgrade(UpgradeTipo upgrade) {
        return upgrade.porcentagem() ? Formato.PORCENTAGEM : Formato.INTEIRO;
    }

    // ------------------------------------------------------------ números

    private static double limitar(double valor, double min, double max, Formato formato) {
        double limitado = Math.max(min, Math.min(max, valor));
        if (formato.inteiro()) {
            return Math.max(Math.ceil(min), Math.round(limitado));
        }
        return Math.round(limitado * 10_000) / 10_000.0; // tira o lixo do ponto flutuante (0.30000000004)
    }

    private static Object guardado(double valor, Formato formato) {
        return formato.inteiro() ? (Object) (long) Math.round(valor) : (Object) valor;
    }

    /** Valor como aparece para o admin (0.25 = 25%, segundos com "s"...). */
    public static String formatar(Formato formato, double valor) {
        return switch (formato) {
            case PORCENTAGEM -> decimal(valor * 100) + "%";
            case SEGUNDOS -> (long) Math.round(valor) + "s";
            case MINUTOS -> (long) Math.round(valor) + " min";
            case DINHEIRO -> EconomyManager.format(Math.round(valor));
            case BLOCOS -> (long) Math.round(valor) + " blocos";
            case INTEIRO -> String.valueOf((long) Math.round(valor));
            case DECIMAL -> decimal(valor);
        };
    }

    /** Até 2 casas, sem zeros sobrando: 1.5, 0.25, 3. */
    public static String decimal(double valor) {
        if (Math.abs(valor - Math.rint(valor)) < 1.0E-9) {
            return String.valueOf((long) Math.rint(valor));
        }
        String texto = String.format(Locale.ROOT, "%.2f", valor);
        while (texto.endsWith("0")) {
            texto = texto.substring(0, texto.length() - 1);
        }
        return texto.endsWith(".") ? texto.substring(0, texto.length() - 1) : texto;
    }
}
