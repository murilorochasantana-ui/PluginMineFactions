# MinePlug: lista de comandos

Plugin do servidor de factions (Paper 26.3).

**Índice:** [Login](#login) · [Claim](#claim) · [Home](#home) · [TPA](#tpa) · [Combate](#combate) · [KDA](#kda) · [Dinheiro](#dinheiro) · [Loja](#loja) · [Mercado](#mercado) · [Clan](#clan) · [Classes](#classes) · [PvP](#pvp) · [Regras](#regras) · [Admin](#comandos-de-admin-op) · [Permissões](#permissões)

---

## Login

| Comando | O que faz |
|---|---|
| `/register <senha> <senha>` ou `/registrar` | Cria a sua senha (primeira vez no servidor) |
| `/login <senha>` ou `/logar` | Entra na sua conta |
| `/trocarsenha <senha antiga> <senha nova>` | Troca a sua senha |

- Antes de logar, não dá para andar, falar, usar comandos nem interagir
- 60 segundos para logar e 5 tentativas de senha
- Senha de 6 a 32 caracteres, diferente do nick
- Não dá para entrar com o nick de quem já está online
- Use o nick exatamente como registrou (maiúsculas e minúsculas contam)

---

## Claim

| Comando | O que faz |
|---|---|
| `/claim <tamanho>` | Mostra uma pré-visualização da área (1 a 50 blocos) com blocos amarelos por 60 segundos |
| `/claim <nome>` | Confirma a área mostrada e cria o claim com esse nome |
| `/claim cancelar` | Cancela a área demarcada (remove os blocos amarelos) |
| `/claim remover <nome>` | Pede a remoção do claim com esse nome |
| `/claim remover confirmar` | Confirma a remoção (em até 30 segundos) |
| `/claim trust <jogador> <nome>` | Dá permissão para um amigo construir no claim |
| `/claim untrust <jogador> <nome>` | Tira a permissão do amigo no claim |
| `/claim info` | Mostra os seus claims (nome, coordenadas e amigos com permissão) |
| `/meuclaim` | Abre um baú com todos os seus claims (cabeças numeradas de 1 a 9) |

---

## Home

| Comando | O que faz |
|---|---|
| `/sethome <nome>` | Salva o local onde você está (usar o mesmo nome atualiza o local) |
| `/home <nome>` | Teleporta para a home em 5 segundos (cancela se você se mexer ou levar dano) |
| `/home` | Abre um baú com as suas homes; clique em uma para teleportar |
| `/delhome` | Abre um baú com as suas homes; clique na que quer apagar e confirme (🟩 apaga, 🟥 cancela) |
| `/delhome <nome>` | Abre direto a confirmação para apagar essa home |

---

## TPA

| Comando | O que faz |
|---|---|
| `/tpa <jogador>` | Pede para ir até o jogador |
| `/tpaqui <jogador>` | Pede para o jogador vir até você |
| `/tpa aceitar [jogador]` | Aceita o último pedido (ou o de um jogador específico) |
| `/tpa negar [jogador]` | Recusa o último pedido (ou o de um jogador específico) |
| `/tpa cancelar` | Cancela o pedido que você enviou |
| `/tpa bloquear` | Liga/desliga receber pedidos de TPA |

- O pedido expira em 60 segundos
- Depois de aceito, quem vai ser teleportado espera 5 segundos (cancela se mexer, levar dano ou entrar em combate)
- 10 segundos de espera entre um pedido e outro
- No chat aparecem os botões **[ACEITAR]** e **[NEGAR]** para clicar

---

## Combate

- Todo jogador tem a tag **[Combate]** no nome: 🟢 verde = fora de combate, 🔴 vermelho = em combate
- Bater ou apanhar de outro jogador coloca os dois em combate por 5 segundos (cada golpe reinicia o tempo)
- A barra lateral mostra "Combate: Não" ou "Combate: Sim" com os segundos restantes
- **Bloqueados em combate:** `/home`, `/sethome`, `/spawn`, `/tpa`, `/tpaqui`, `/warp`, `/back`, `/loja`, `/mercado` e `/classe` (lista no `config.yml`)
- **Combat log:** sair do servidor em combate mata o jogador, ele dropa os itens e conta como morte; quem estava lutando com ele ganha a kill/assistência
- **Ender pearl:** 15 segundos de espera entre uma e outra (sempre, não só em combate)

---

## KDA

| Comando | O que faz |
|---|---|
| `/kda` | Mostra o seu K/D/A |
| `/kda <jogador>` | Mostra o K/D/A de outro jogador |

- **Kill:** quem deu o golpe final (ou quem bateu por último nos 30 segundos antes, se a vítima morrer de queda, lava, void etc.)
- **Assistência:** quem causou dano na vítima nos 30 segundos antes da morte, sem ser quem pegou a kill
- **Morte:** só conta quando foi morto por jogador (ou combat log)
- **KDA** = (kills + assistências) / mortes, igual ao LoL (com 0 mortes, divide por 1)
- A barra lateral mostra o K/D/A e o KDA

---

## Dinheiro

| Comando | O que faz |
|---|---|
| `/money` | Mostra o seu saldo |
| `/money <jogador>` | Mostra o saldo de outro jogador |
| `/money pay <jogador> <valor>` | Envia dinheiro para outro jogador (funciona mesmo se ele estiver offline) |

- **Kill:** +100 · **Assistência:** +30 (valores no `config.yml`)
- Todo jogador começa com 0
- A barra lateral mostra o seu dinheiro

---

## Loja

| Comando | O que faz |
|---|---|
| `/loja` | Abre a loja do servidor (abre mesmo se estiver vazia). Clique no item e confirme (🟩 compra, 🟥 cancela) |

---

## Mercado

| Comando | O que faz |
|---|---|
| `/mercado` | Abre o mercado com os itens anunciados pelos jogadores, mostrando o vendedor, o preço e há quanto tempo foi anunciado |

Dentro do menu:
- **Comprar:** clique no item de outro jogador. A confirmação mostra a cabeça do vendedor (🟩 compra, 🟥 cancela)
- **Vender item:** clique em um item do seu inventário, escolha o preço com os botões `+1`, `+10`, `+100`, `+1k`, `+10k`, `+100k` e `+1M` (clique esquerdo soma, direito subtrai) e clique em **Anunciar**
- **Meus anúncios:** clique em um anúncio seu para mudar o preço ou remover (o item volta para você)

Regras:
- Sem limite de anúncios por jogador
- O dinheiro da venda vai para o vendedor mesmo se ele estiver offline
- Não dá para comprar o próprio anúncio

---

## Clan

| Comando | O que faz |
|---|---|
| `/clan` | Abre o menu do seu clan (membros, cargo, K/D/A, online/offline). Sem clan, mostra os comandos |
| `/clan criar <TAG> <nome>` | Cria um clan (TAG de 2 a 4 letras/números, ex: `ABC`) |
| `/clan convidar <jogador>` | Convida um jogador (só o líder). O convite expira em 60 segundos |
| `/clan aceitar [TAG]` | Aceita o último convite (ou clique em **[ACEITAR]** no chat) |
| `/clan negar [TAG]` | Recusa o último convite |
| `/clan sair` | Sai do clan (o líder precisa passar a liderança ou desfazer antes) |
| `/clan expulsar <jogador>` | Expulsa um membro (só o líder, com confirmação) |
| `/clan lider <jogador>` | Passa a liderança para outro membro (com confirmação) |
| `/clan desfazer` | Apaga o clan (só o líder, com confirmação) |
| `/clan info <TAG>` | Mostra o menu de outro clan |
| `/c <mensagem>` ou `/clan chat <mensagem>` | Fala só com o seu clan |

- Máximo de **2 membros** por clan (dá para mudar no `config.yml`: `clan.max-membros`)
- A TAG do clan aparece no nome, no TAB, no chat e na barra lateral
- Membros do mesmo clan não causam dano um no outro (e isso não coloca em combate)
- Membros do mesmo clan podem construir nos claims uns dos outros sem precisar de trust

---

## Classes

| Comando | O que faz |
|---|---|
| `/classe` | Abre o menu de classes |
| `/classe trocar` | Deixa a classe atual (com confirmação) para escolher outra |

- Sem classe até escolher uma
- A classe aparece na barra lateral e no menu do `/clan`
- Disponíveis: **Mago**, **Arqueiro**, **Combatente** e **Engenheiro** (Suporte em breve)

### 🔮 Mago

Usa staffs (compradas na `/loja`), lançadas com **clique direito**:

| Staff | Efeito | Mana | Recarga |
|---|---|---|---|
| Fogo | Bola de fogo em área: 4 de dano, queima e cegueira por 2s | 2 | 5s |
| Gelo | 2 de dano, lentidão II e fadiga por 5s | 2 | 10s |
| Raio | 6 de dano em cadeia (até 3 alvos) | 3 | 5s |
| Teleporte | Teleporta até 30 blocos (não atravessa parede). Segurando 3s leva até 2 jogadores a até 3 blocos | 5 | 30s |
| Vento | Empurra quem está perto, 3 de dano | 2 | 20s |

- **Grimório** (`/grimorio`): um livro que reúne as magias e libera a hotbar. Quem vira Mago pela primeira vez ganha um Grimório com a magia de Fogo. As outras magias são aprendidas **consumindo a staff** (no menu `/grimorio`, clique numa staff do seu inventário e confirme); a magia fica aprendida para sempre, salva em `mago.yml`. Dá para equipar até 4 magias (`mago.grimorio.max-equipadas`); trocar as equipadas fica bloqueado em combate
  - Clique direito: lança a magia selecionada (mesmos custo, dano, recarga e combos da staff). Clique esquerdo: Míssil Arcano. Shift + clique esquerdo: próxima magia equipada. Teleporte em grupo: Shift + clique direito
  - A recarga é por magia e compartilhada entre o Grimório e a staff. O Grimório não vai em bigorna, mesa de encantamento nem rebolo
- **Míssil Arcano (clique esquerdo com a staff):** projétil rápido de 2.5 de dano, sem mana e sem recarga (0.6s entre mísseis); cada acerto devolve +1 de mana. O clique esquerdo não dá golpe corpo a corpo
- Mana máxima 10 (aparece na barra lateral): recupera 1 a cada 2s fora de combate e 1 a cada 6s em combate; kill dá +3 e assistência +1
- **Poder Arcano:** cada magia dá 1 acúmulo por 5s (máx. 3); cada acúmulo dá +10% de dano nas magias e no míssil. Bater com arma zera
- **Escudo de Mana:** 20% do dano que leva é absorvido pela mana (1 de mana a cada 2 de dano)
- **Combos** (cada um liga/desliga em `mago.combos.*`):
  - **Choque Térmico** (Gelo → Fogo): alvo congelado leva o dobro do Fogo e perde a lentidão e a fadiga
  - **Condutor** (Gelo → Raio): se o primeiro alvo está congelado, o raio pula até 4 vezes e a 7 blocos
  - **Tempestade de Fogo** (Vento → Fogo): alvo empurrado pelo Vento queima por 6s e a explosão tem raio 4
  - **Passo Arcano** (Teleporte → qualquer magia): a próxima magia em 4s não gasta mana
- As magias não acertam membros do seu clan e não quebram blocos
- **Fraqueza:** leva +15% de dano de golpes e projéteis, e dá 60% menos dano com espada, machado, maça, tridente, lança e flechas de arco/besta (mão vazia não tem penalidade)
- Tudo ajustável em `mago.*` no `config.yml`

### 🏹 Arqueiro

- **Tecla F** com o arco na mão: alterna entre 1 flecha e rajada de 3 flechas seguidas (recarga da rajada de 3s, gasta 3 flechas)
- +20% de velocidade com o arco na mão
- **Flecha Explosiva** (comprada na `/loja`): coloque na mão secundária para usar. Dá 3x o dano ao acertar um jogador e explode como um creeper, sem quebrar blocos
- Só Arqueiros podem atirar a Flecha Explosiva
- **Fraqueza:** 40% menos dano com espadas e machados

### ⚔️ Combatente

- 13 corações de vida máxima (em vez de 10)
- Recebe 30% menos dano de todas as origens
- **Grito de Fúria (Shift + F):** por 8 segundos acerta golpes 1.5x mais rápido, mas fica com lentidão (recarga de 30s)
- **Fraqueza:** não pode usar arco

### 🔧 Engenheiro

- Usa **Livros de Estrutura** (comprados na `/loja`)
- **Livro vazio + clique direito:** marca uma área de 20x20 (altura 20) a partir de onde você está, para frente e para a direita. O chão vira concreto branco. O lugar precisa estar plano e vazio
- Construa em cima do concreto. Só você pode construir e quebrar dentro da área
- **Clique direito de novo:** menu para **Salvar** ou **Cancelar** (com confirmação)
  - **Salvar:** os blocos (e os itens dentro de ejetores/baús) vão para o livro e somem do mundo, e o chão volta ao normal
  - **Cancelar:** os blocos ficam no lugar e o livro continua vazio
- **Livro cheio + clique direito:** mostra uma prévia da estrutura que só você vê. **Shift + clique direito** gira. **Clique direito** confirma o local
- O livro cheio só pode ser usado 1 vez. Qualquer Engenheiro pode usar (dá para vender no `/mercado`)
- Não dá para colocar em cima de blocos que já existem nem no claim de outro jogador
- Em combate e por 60 segundos depois, não dá para marcar, salvar nem colocar estruturas

---

## PvP

- PvP estilo 1.8: sem recarga de ataque, todo golpe dá dano cheio
- Espada não acerta em área (sem sweep)
- Machado com dano da 1.8 (menor que o da espada)

---

## Regras

- Cada jogador pode ter até **9 claims** e **5 homes**
- Um claim não pode encostar em outro claim
- Amigos com trust podem construir, quebrar e usar baldes no claim, mas não podem remover o claim nem dar trust para outros
- O amigo precisa ter entrado no servidor pelo menos uma vez para receber trust
- Nome do claim e da home: 3 a 16 caracteres, só letras, números, `_` e `-`, e não pode ser só números

---

## Comandos de admin (OP)

| Comando | O que faz |
|---|---|
| `/auth admin resetar <jogador>` | Apaga a senha do jogador para ele se registrar de novo (também funciona no console) |
| `/money admin dar <jogador> <valor>` | Dá dinheiro |
| `/money admin tirar <jogador> <valor>` | Tira dinheiro |
| `/money admin definir <jogador> <valor>` | Define o saldo |
| `/loja admin` | Abre a loja em modo admin (ver abaixo) |
| `/classe admin staff <fogo\|gelo\|raio\|teleporte\|vento>` | Pega uma staff do Mago (para colocar na loja) |
| `/classe admin mana definir <jogador> <valor>` | Define a mana atual do Mago |
| `/classe admin mana dar <jogador> <valor>` | Soma mana no Mago |
| `/classe admin manamaxima definir <jogador> <valor>` | Muda a mana máxima só desse jogador |
| `/classe admin manamaxima resetar <jogador>` | Volta a mana máxima para o padrão do `config.yml` |
| `/classe admin flecha <quantidade>` | Pega Flechas Explosivas (para colocar na loja) |
| `/classe admin livro <quantidade>` | Pega Livros de Estrutura vazios (para colocar na loja) |

**`/loja admin`:**
- **Botão "Adicionar item":** clique em um item do seu inventário, escolha o preço com os botões `+1` a `+1M` (esquerdo soma, direito subtrai) e clique em **Salvar**
- **Clique em um item da loja** para editar o preço ou removê-lo (com confirmação 🟩/🟥)

---

## Permissões

| Permissão | Para que serve | Padrão |
|---|---|---|
| `mineplug.claim.bypass` | Construir no claim de outros jogadores | OP |
| `mineplug.auth.admin` | Comando `/auth admin` | OP |
| `mineplug.money.admin` | Comandos `/money admin` | OP |
| `mineplug.loja.admin` | Comando `/loja admin` | OP |
| `mineplug.classe.admin` | Comandos `/classe admin` | OP |
