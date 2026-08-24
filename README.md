# Recados

Notas adesivas na área de trabalho, em **Java 21 + Swing**. Cada nota é uma janelinha colorida sem
decoração do sistema, que se lembra de onde estava, do tamanho, da cor e se fica no topo.
Sem dependências externas.

## Executar

```bash
mvn package
```

Depois, no Windows, dê um duplo clique em `recados.bat` (ou rode direto):

```bash
java -jar target/recados.jar
```

No Linux/macOS, use `./recados.sh`.

## Como usar

| Ação | Como |
| --- | --- |
| Mover a nota | arraste pela barra colorida de cima |
| Redimensionar | arraste a alça no canto inferior direito |
| Nova nota | botão `+`, `Ctrl+N`, ou o menu da bandeja |
| Trocar a cor | botão do meio-círculo ou `Ctrl+E` (6 cores; a padrão é azul) |
| Formatar texto | a **barra de ícones no rodapé** (aparece com a nota em foco) |
| Desfazer / refazer | `Ctrl+Z` / `Ctrl+Y` (ou `Ctrl+Shift+Z`), e no menu de contexto |
| Aumentar / diminuir | `Ctrl +` / `Ctrl −` — cresce **o texto e a janela juntos**; `Ctrl 0` volta ao normal |
| Fonte monoespaçada | Formatar → **Fonte monoespaçada** — na seleção; sem seleção, na nota toda |
| Fixar/soltar no topo | botão do ponto (cheio = fixada) ou `Ctrl+T` |
| Minimizar a nota | botão da barra ou `Ctrl+W` — **não apaga**, volta pela bandeja |
| Apagar a nota | `Ctrl+D` ou o menu de contexto — pede confirmação e vai para a lixeira; nota **em branco** apaga direto |
| Mostrar todas as notas | `Ctrl+Shift+A` ou clique no ícone da bandeja |
| Configurações | `Ctrl+,`, menu da bandeja, ou menu de contexto |
| Sair | `Ctrl+Q` ou o menu da bandeja |

Clique com o botão direito em qualquer parte da nota para o menu de contexto.

O texto salva sozinho meio segundo depois da última tecla, e também ao mover, redimensionar
ou perder o foco — não existe botão de salvar.

## Ícone próprio na barra de tarefas

```bash
powershell -ExecutionPolicy Bypass -File package-app.ps1
```

Gera `target\dist\Recados\Recados.exe` com `jpackage` — um lançador nativo com a nossa nota
como ícone, e um JRE embutido (não precisa de Java instalado para rodar).

**Por que isso é necessário:** a barra de tarefas do Windows tira o ícone do **executável que
lançou o processo**, não da janela. Rodando por `javaw.exe`, o botão mostra o cafezinho do Java
por mais tamanhos de ícone que a janela declare — `setIconImages` resolve o Alt+Tab e a janela,
não a barra. O que mudaria isso é o `AppUserModelID` do processo, e Java puro não tem como
definir (precisaria de chamada nativa; a FFM API do Java 21 ainda é preview). Um executável
próprio resolve sem dependência nativa.

O `.ico` multi-tamanho é gerado pelo próprio app, sem ferramenta externa:

```bash
java -cp target/recados.jar com.recados.Icons target/recados.ico
```

Rodar pelo jar continua funcionando — só o ícone da barra de tarefas fica sendo o do Java.

## Pacote instalável (GitHub Actions)

O [workflow](.github/workflows/pacote.yml) roda em `windows-latest` a cada push em `main`,
compila, **roda as checagens** e monta dois arquivos — os dois com um JRE recortado embutido,
então a máquina que instala não precisa de Java nenhum:

| Arquivo | O quê |
| --- | --- |
| `Recados-<versão>-windows.msi` | instalador: atalho no menu Iniciar e na área de trabalho, escolha de pasta, instalação **por usuário** (não pede administrador) |
| `Recados-<versão>-windows-portavel.zip` | descompactar e rodar, sem instalar |

Os dois ficam nos artefatos da execução. Numa tag `v1.2.3` o pacote sai como `1.2.3` e vira uma
**release** com os dois anexados; fora de tag sai como `0.0.<número da execução>`, que mantém os
pacotes de teste distinguíveis e nunca colide com uma versão publicada.

Para publicar uma versão:

```bash
git tag v1.0.0 && git push origin v1.0.0
```

Localmente, o mesmo empacotamento (é o script que o workflow chama, não uma segunda receita):

```bash
powershell -ExecutionPolicy Bypass -File package-installer.ps1 -Version 1.0.0
```

**Roda em Windows porque o produto é de Windows:** o `jpackage` só gera `.msi` na própria
plataforma, e o ícone da barra de tarefas depende do executável nativo.

O `.msi` precisa do **WiX Toolset 3** — o `jpackage` chama `candle.exe` e `light.exe`, que o WiX
4 não tem mais. A imagem do runner já traz uma versão do 3 (hoje a 3.14), então o workflow
procura o `candle.exe` e só instala se faltar: pedir uma versão fixa fazia o `choco` falhar por
*downgrade*, e foi assim que a primeira execução caiu antes de empacotar. Sem WiX na máquina, o
script local gera só o portável, avisa o que faltou e sai bem — o portável é útil por si.

## Iniciar com o Windows

Abra **Configurações** (menu da bandeja, botão direito na nota, ou `Ctrl+,`) e marque
**"Iniciar o Recados com o Windows"**. Não precisa rodar nada por fora — a caixa reflete o
estado real do sistema, e desmarcar desfaz.

Por baixo é um atalho `recados.lnk` na pasta Inicializar do usuário. Ele aponta para o
`Recados.exe` quando você empacotou com `package-app.ps1`, e para `javaw.exe` + jar quando não
(sem janela de console nos dois casos). Se você mover a pasta do Recados, desmarque e marque de novo
para reapontar.

Para instalação automatizada (sem abrir a interface) os mesmos arquivos podem ser criados por
script — é o mesmo `recados.lnk`, então os dois caminhos nunca conflitam:

```bash
powershell -ExecutionPolicy Bypass -File install-startup.ps1
```

```bash
powershell -ExecutionPolicy Bypass -File uninstall-startup.ps1
```

### Por que atalho e não a chave `Run` do registro

A chave `HKCU\...\CurrentVersion\Run` seria o caminho óbvio, mas gravar ali um caminho de
executável entre aspas **a partir de um processo Java** é barrado pela proteção do Windows: o
`CreateProcess` do `reg.exe` volta com `Access is denied`, e depois disso todo `reg.exe` daquele
processo é negado. O atalho na pasta Inicializar passa limpo, aparece em "Aplicativos de
inicialização" nas configurações do Windows e é I/O puro para consultar e remover.

## Texto rico

O documento é **HTML**, e a formatação fica numa **barra de ícones no rodapé** da nota:

| Ícone | O quê | Também por |
| --- | --- | --- |
| **B** | negrito | `Ctrl+B` |
| *I* | itálico | `Ctrl+I` |
| U̲ | sublinhado | `Ctrl+U` |
| três linhas com marcadores | lista com marcadores — **com texto selecionado, cada linha vira um item**; sem seleção, um item vazio | menu Formatar |
| três linhas numeradas | a mesma coisa, numerada (`<ol>`) | menu Formatar |
| dois elos de corrente | inserir link — a seleção vira o rótulo; sem seleção, o endereço | menu Formatar |
| `</>` | fonte monoespaçada **na seleção** (sem seleção, na nota toda) | menu Formatar |
| borracha | limpar formatação: tira negrito/itálico/sublinhado **e desmonta listas** (na seleção, ou na nota toda) | menu Formatar |

**`ENTER`** quebra a linha: dentro de uma lista cria o item seguinte, e num item vazio **sai da
lista** — que é o único jeito de terminar uma lista sem usar o mouse. Fora de lista, divide o
parágrafo onde o cursor está.

Limpar formatação **não tira os links**. Link não é decoração, é conteúdo: perder o endereço ali
seria perder informação que não está em nenhum outro lugar da nota.

Com seleção, a borracha desmonta **a lista inteira** que a seleção tocar, não só as linhas
marcadas — e não mexe em parágrafo nenhum além de tirar o estilo da seleção. Trocar a lista
inteira não é preguiça: apagar só o texto dos itens deixa o `<ul>` e os `<li>` vazios de pé, e as
linhas novas vão para *dentro* do item — o marcador do primeiro continua na tela e o resto sai
indentado. Foi o que aconteceu na primeira versão disto.

O **recuo da lista é 10 px**, e não os 50 px que o Swing usa por padrão para `<ul>` — numa nota
de 280 px, 50 px comem um sexto da linha e jogam o marcador para o meio do nada. Com 10 px o
ponto sai a 5 px da coluna do texto normal e o texto do item a 13 px. Os números vêm de medição
no pixel pintado, e uma checagem trava a medida.

Abrir um link é **Ctrl+clique**. Copiar a nota com formatação continua no menu **Formatar** do
clique direito, junto de tudo o que está na barra — é ação da nota inteira, não da seleção, e
tirá-la da barra é parte do que faz os ícones caberem na nota mais estreita. A outra parte é o
tamanho: os botões do rodapé têm 18 px, e não 22 px como os da barra de título — o `</>` tem 22,
porque três elementos em 14 px viram borrão. A soma (164 px com a alça) é a razão de a **largura
mínima da nota ser 180 px**. Uma checagem confere essa conta: foi ela que avisou quando o oitavo
botão passou de 160, e é ela que vai avisar do nono.

A barra fica **embaixo**, e não na barra de título, para não misturar ações da *janela* (nova
nota, cor, fixar, minimizar) com ações do *texto*. E aparece só com a nota em foco: nota que
você está apenas lendo não precisa dela. A altura do rodapé não muda quando ela aparece — se
mudasse, o texto pularia e a rolagem escorregaria a cada troca de foco. Por causa da barra, a
altura mínima da nota subiu de 120 para 150 px.

O menu **Formatar** do clique direito continua existindo: é onde os atalhos aparecem escritos.

**Colar em e-mail e navegador** funciona porque a cópia oferece `text/html` *e* texto puro —
quem aceita HTML recebe a formatação, quem não aceita recebe o texto. É o motivo de o formato
ser HTML e não RTF.

**O offset 0 pertence ao `<head>`.** Num `HTMLDocument`, o offset 0 fica dentro do cabeçalho, que
tem um parágrafo implícito próprio — o corpo só começa no 1. O cursor era posto em 0 ao abrir a
nota, então **tudo o que se digitava numa nota nova entrava no `<head>`**: o botão de lista não
achava parágrafo e não criava item, o `ENTER` não dividia nada, e o documento chegou a uma forma
em que o layout do Swing entrou em laço infinito (2,5 milhões de views e 4,5 GB antes de o
processo ser encerrado). O cursor agora vai para o início do `<body>`, e nota vazia nasce com um
parágrafo vazio para receber o texto.

Ctrl+clique, e não clique simples, porque o painel é editável: ali o clique é do cursor de
texto. Pelo mesmo motivo não dá para usar `HyperlinkListener`, que só dispara em painel
somente-leitura.

A cor do texto vem da paleta da nota, não do documento: trocar a cor recolore tudo. Os links
ficam de fora — azul de link não é decoração, é sinal de que dá para clicar.

### Fonte, tamanho e a barra de rolagem

`Ctrl +` e `Ctrl −` mudam **a fonte e a janela na mesma proporção**, então o texto continua
ocupando o mesmo espaço relativo dentro da nota; `Ctrl 0` volta ao padrão (11 pt). O tamanho é da
nota, vai gravado em `recados:font-size` e entra no `<style>` do arquivo — a nota aberta no
navegador tem o mesmo tamanho da nota na tela.

**Fonte monoespaçada é do trecho selecionado** (sem seleção, da nota toda), marcada com `<tt>`.
Três descobertas decidiram essa implementação, e cada uma tem checagem:

- **Marcar por atributo de fonte não persiste.** Na tela funciona — o `MMMM` monoespaçado mede
  32 px contra 40 px do proporcional —, mas o escritor de HTML do Swing grava `face=""`: a fonte
  se perde no disco. Com `<tt>` a marca sobrevive ao ida-e-volta, e o navegador já a entende.
- **Inserir `<tt>` como HTML parte o parágrafo.** A inserção do Swing trata a tag como bloco:
  "antes MEIO depois" virava três parágrafos. O parágrafo é remontado inteiro (`setInnerHTML`),
  então continua um.
- **O Swing guarda `<tt>` como atributo do caractere, não como elemento** — igual a `<b>` e
  `<a>`. Procurar por elemento nunca acha nada, e foi assim que *desligar* a fonte não funcionou
  na primeira versão.

Cor e tamanho, por outro lado, são aplicados como **atributo de caractere**, não como regra de
CSS: regra adicionada à folha de estilo de um documento que *já existe* não redesenha nada —
medido. Mas o arquivo **não** guarda `face` e `size` em cada trecho: repetidos ali eles
atropelariam o `<style>` no navegador. Ficam nos metadados; a janela os reaplica ao abrir.

A **barra de rolagem** é pintada com a cor da nota (trilha da cor do corpo, polegar cinza
translúcido, 9 px, sem setas nas pontas). A do sistema chega cinza, e uma faixa cinza no meio de
uma nota colorida se anuncia como "componente" em vez de parte do papel.

### Desfazer

`Ctrl+Z` desfaz, `Ctrl+Y` (ou `Ctrl+Shift+Z`) refaz, e o menu de contexto tem os dois, cinzentos
quando não há o que fazer. O Swing não traz isso de graça: o documento avisa cada edição e um
`UndoManager` guarda a pilha (200 passos).

Duas coisas que decidem se o undo ajuda ou atrapalha:

- **A abertura da nota não entra na pilha.** Para o documento, carregar a nota é inserir texto —
  se contasse, o primeiro `Ctrl+Z` de uma nota recém-aberta apagaria tudo. Recolorir pela paleta
  também fica fora: desfazer devolveria a cor antiga só no documento, e a nota continuaria
  gravada com a nova.
- **Uma ação vale um passo.** Virar linhas em lista, ou trocar a seleção por um link, mexe no
  documento duas ou três vezes; as edições vão dentro de um `CompoundEdit`, então um `Ctrl+Z`
  volta a ação inteira em vez de parar num meio-caminho que ninguém viu.

Digitação corrida também vira um passo só: edições que chegam com menos de 700 ms de intervalo
entram no mesmo grupo, e uma pausa marca onde ele termina. Sem isso, voltar uma frase custaria
uma tecla `Ctrl+Z` por letra.

### Notas de versões anteriores

Abrem sem nada a fazer. Nota com `rtf=` (de duas versões atrás) é convertida para HTML, e nota
só com `text=` também — ver "Onde as notas ficam". Se o HTML estiver corrompido, a nota abre
pelo texto puro em vez de falhar.

### Armadilhas do Swing que valem registro

`InsertHTMLTextAction` **não insere nada quando o cursor está no offset 0** — e não reclama.
Como a nota abre com o cursor em 0, lista e link desapareciam em silêncio se você não clicasse
antes. Por isso o cursor é levado para o fim quando está em zero.

Marcar o texto com o atributo `HTML.Tag.A` para criar link **não funciona**: o escritor emite
`<a href><u><p-implied></u></a>`, sem o rótulo. O link precisa entrar como HTML, pelo parser.

**Uma linha é um `<p>`, e isso é o que faz o `ENTER` funcionar.** O `insert-break` do Swing
divide o parágrafo onde o cursor está — e quando o texto não está dentro de um `<p>` de verdade
(o Swing chama isso de `p-implied`), não há o que dividir: a tecla insere um `"\n"` cru, que em
HTML é espaço em branco e **não aparece na tela**. Era o caso de toda nota digitada, de nota
antiga e de nota convertida de RTF. Por isso o conteúdo é normalizado em um parágrafo por linha
ao abrir. Linha em branco continua linha em branco: `<p></p>` ocupa a mesma altura que o `<br>`
que ele substituiu (medido: 30 px entre duas linhas nos dois casos).

Ainda assim, as duas formas convivem numa nota (texto colado traz `<p>`, HTML de fora pode trazer
`<br>`), então quebrar por linha considera as duas — e, no caso do parágrafo, não deixa para trás
o `<p>` que ficou vazio, senão sobra uma linha em branco. E o `HTMLDocument` guarda uma quebra
própria no offset 0, então seleção que começa em zero (Ctrl+A) precisa pular essa primeira
posição, senão a lista sai vazia.

**Regex de tag pede fronteira.** `</?(…|b|…)[^>]*>` parece certo e **come o `<br>`**: "b" é
prefixo de "br". Foi assim que "limpar formatação" juntou a nota toda numa linha só. O padrão
tem de exigir `>` ou espaço depois do nome da tag: `(\s[^>]*)?>`.

**O escritor de HTML às vezes devolve documento sem `<body>`** — `<html><head>…</head>conteúdo
</html>`. Tratar isso como "o corpo" grava um `<html>` dentro do `<body>` do arquivo. A extração
do corpo tira cabeçalho e `<html>` quando não acha `<body>`, e uma checagem confere o arquivo.

Botão que **recebe o foco apaga a seleção na tela**: você marca a palavra, clica no B, e não vê
mais o que marcou. Os botões da barra de formatação são `setFocusable(false)` por isso, e uma
checagem garante que continuem assim.

## Checagens

```bash
powershell -ExecutionPolicy Bypass -File run-checks.ps1
```

No Linux/macOS, `./run-checks.sh`. Cada grupo usa um diretório temporário próprio, então as
checagens nunca tocam as suas notas.

Cobrem o formato do arquivo HTML e seus metadados, a pasta de minimizadas, a lixeira e a
restauração, nota em branco apagando direto, a conversão do formato `.properties`, a migração
minimizar sem apagar, `WM_CLOSE` sem minimizar, apagar sem ressuscitar, a barra
de formatação (aparece com o foco, não rouba o foco, e cabe na nota mais estreita), desfazer e
refazer, a tecla ENTER (parágrafo, item de lista, e sair da lista), limpar formatação
desmontando listas, zoom de texto e janela, a fonte monoespacada no trecho, a barra de rolagem com
a cor da
nota, o ida-e-volta do HTML, a lista com marcadores e a numerada feitas a
partir da seleção, links e a colagem com `text/html`.

**Por que não JUnit e `mvn test`:** nesta máquina o Kaspersky encerra o booter do
maven-surefire como `PDM:Trojan.Win32.Generic`, então `mvn test` nunca chega a rodar. As
checagens são classes Java comuns, compiladas contra o jar e executadas direto — sem
dependência de teste no `pom.xml` e sem depender de um processo que o antivírus mata.

## Diário de bordo (para reproduzir um problema)

Quando algo trava ou se comporta mal e é difícil descrever o que foi feito, rode o Recados com
o diário ligado:

```bash
powershell -ExecutionPolicy Bypass -File rodar-com-trace.ps1
```

Use o programa normalmente até o problema aparecer, feche-o (bandeja → **Sair**, ou `Ctrl+Q`) e
mande o arquivo cujo caminho o script imprime — por padrão `~\.recados\trace\trace-<data>.log`.
Com `-Acompanhar`, uma segunda janela mostra o log crescendo em tempo real.

O que entra no arquivo:

- **cada tecla e cada clique**, com o componente em que caiu — o nome do botão da barra vem da
  própria dica dele, então dá para ver qual ícone foi apertado;
- **cada comando**, em par: `> tecla control Z  nota=… cursor=17 sel=[3,20) tamanho=42` e, na
  volta, quanto demorou e o **HTML resultante**. Junto com a linha `ABRIU` (o conteúdo da nota
  ao abrir), isso é o bastante para repetir a sequência inteira aqui;
- **a pilha das threads quando a tela congela**. Um vigia em thread própria pergunta à interface
  "você ainda responde?" quatro vezes por segundo; depois de 2 segundos sem resposta, fotografa
  a pilha de todas as threads e o consumo de memória, e refotografa a cada 3 segundos enquanto
  continuar travada. Uma foto só não distingue "está lento" de "está em círculo" — várias sim.

O vigia mora fora da EDT de propósito: o travamento que motivou tudo isto come justamente a
EDT, e qualquer diagnóstico que dependesse dela congelaria junto.

**Desligado por padrão.** Sem `-Drecados.trace`, `Trace` é um `if` que volta: o pacote que vai
para o usuário não grava nada, não abre arquivo nenhum e não fica com uma thread extra. Uma
checagem garante isso a cada build — trace esquecido ligado gravaria cada tecla do usuário.

Para ligar no aplicativo **já instalado**, o lançador do jpackage não aceita `-D` na linha de
comando: as opções da JVM vêm do arquivo `app\Recados.cfg`, ao lado do `Recados.exe` (a
instalação é por usuário, em `%LOCALAPPDATA%\Recados`). Acrescente uma linha
`java-options=-Drecados.trace` na seção `[JavaOptions]` e reabra o programa.

## Dois monitores com escalas diferentes

Os lançadores passam `-Dsun.java2d.uiScale=1`, que mantém **uma escala só** em todos os
monitores. Sem isso, arrastar a nota entre um monitor a 100% e outro a 125% dava dois
problemas: o Java reinterpretava o tamanho na escala nova — `280x260` chegava como `350x325` —
e as coordenadas eram remapeadas na faixa da borda, então a nota saltava de volta e parecia não
atravessar.

O tamanho está resolvido no código: **só a alça** muda o tamanho gravado, e qualquer mudança
vinda de fora é desfeita. O salto de coordenadas é do Java, e a escala única é o que o elimina.

O custo: num monitor a 125% ou mais, a nota fica no tamanho de 100%, ou seja, um pouco menor.
Se você preferir a escala do sistema à travessia suave, tire o `-Dsun.java2d.uiScale=1` de
[recados.bat](recados.bat), [recados.sh](recados.sh) e [package-app.ps1](package-app.ps1).

## Onde as notas ficam

```
~/.recados/notes/<uuid>.html              nota na tela
~/.recados/notes/minimizados/<uuid>.html  nota minimizada
~/.recados/trash/<uuid>-<timestamp>.html  nota apagada
~/.recados/legado/<uuid>.properties       o arquivo do formato anterior, arquivado
```

Um arquivo por nota, e um **HTML de verdade**: duplo clique abre no navegador, dá para arrastar
para um e-mail, dá para ler a olho nu. Escrever uma nota nunca mexe nas outras. Um `.lock` no
diretório impede duas instâncias brigando pelos mesmos arquivos.

```html
<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<title>Comprar pão, leite…</title>
<meta name="recados:created-at" content="2026-08-23T13:14:22.187Z">
<meta name="recados:x" content="120">
<meta name="recados:y" content="340">
<meta name="recados:width" content="280">
<meta name="recados:height" content="260">
<meta name="recados:color" content="Azul">
<meta name="recados:always-on-top" content="true">
<style>
body { background: #C2E4FF; color: #1E3547; font-family: 'Segoe UI', sans-serif; ... }
</style>
<!-- Nao renomeie este arquivo: o id do recado vem do nome dele. -->
</head>
<body>
Comprar <b>pão</b>, leite…
</body>
</html>
```

**Por que `<meta>` e não comentário:** comentário é a primeira coisa que editor de HTML,
minificador ou sanitizador tira sem avisar, e `<meta name>` é o lugar que o próprio padrão
reserva para metadado — de quebra já vem estruturado se um dia a leitura passar por um parser.
Comentário ficou onde comentário é bom: o aviso para o humano não renomear o arquivo. O
`<title>` dá nome à aba do navegador, e o `<style>` leva a cor da paleta, então o arquivo
aberto fora do Recados tem a cara da nota.

A cor vai gravada pelo **nome** (`content="Azul"`), nunca pela posição na paleta: reordenar as
cores não pode repintar nota já gravada.

**O texto puro não é gravado.** Antes o arquivo tinha `html=` e `text=`, as duas versões do
mesmo conteúdo, podendo divergir; agora o texto puro (que dá o título e a lista da bandeja) é
derivado do HTML na leitura. O custo é que `grep "comprar pão"` pode não achar se houver um
`<b>` no meio da frase — `grep pão` acha.

### Gravação atômica

A gravação passa por `<uuid>.html.tmp` e depois um `move` — falha no meio não deixa nota
truncada. O `move` usa `ATOMIC_MOVE`, e não só `REPLACE_EXISTING`: no Windows, o segundo
**apaga o destino e depois renomeia**, e quem estiver lendo a pasta nesse instante vê a nota
desaparecida. Não é teórico — foi assim que uma checagem começou a falhar de vez em quando, e
foi essa checagem que revelou o problema.

Só que a troca atômica **falha** enquanto outro processo estiver com o arquivo aberto, mesmo por
um instante: antivírus, indexador, o explorador. Isso também apareceu de verdade, como uma
gravação perdida numa rodada de checagens. Então:

- o `move` **insiste** algumas vezes com pausa curta (o bloqueio dura milissegundos) e, só
  depois, cai na troca não atômica — perder a atomicidade é ruim, perder a gravação é pior;
- `save` **devolve se conseguiu**, e a janela remarca o autosave quando não conseguiu, até cinco
  vezes. Antes o resultado era ignorado, e o que você tinha escrito ia embora em silêncio;
- o temporário de uma gravação que não completou é apagado, e não fica de lixo na pasta.

### Minimizar não é apagar, e minimizada é a pasta

O botão da barra de título **minimiza** a nota — barra horizontal, como o minimizar do Windows:
ela continua no disco, aparece marcada como `(minimizada)` na lista da bandeja, e voltar é um
clique ali.

Onde ela está minimizada não é um campo no arquivo: é a **pasta**. Minimizar é mover
`notes/<uuid>.html` para `notes/minimizados/<uuid>.html`, e voltar é mover de volta. Uma
verdade só, visível no explorador, e você pode restaurar uma nota à mão arrastando o arquivo.

O nome do arquivo **nunca** muda — e é de propósito. Marcar o estado no nome
(`<uuid>.html.minimizado`) foi considerado e descartado: a extensão passaria a ser
`.minimizado`, o Windows perderia a associação, e justamente o duplo clique que o formato HTML
conquistou deixaria de funcionar nas notas minimizadas. Além disso o id vem do nome do arquivo,
e renomear a cada minimizar abriria a chance de, com um desligamento no meio, sobrarem dois
arquivos com o mesmo id. Movendo, a nota existe em exatamente um lugar em qualquer instante.

### Lixeira

Apagar **não** remove o arquivo: ele vai para `~/.recados/trash/<uuid>-<timestamp>.html`.
A pasta nasce no primeiro apagar, e o carimbo de tempo garante que restaurar uma nota e apagar
de novo não sobrescreve a cópia anterior.

**Nota em branco é a exceção:** apaga de vez, sem confirmação e sem lixeira. Não há o que
confirmar nem o que recuperar, e arquivo vazio na lixeira só dá trabalho de limpar depois.
Nota com conteúdo continua pedindo confirmação — e é aqui que vale lembrar por que: antes um
`×` na barra de título apagava sem perguntar, e um clique no lugar errado virava perda
silenciosa.

Para restaurar, mova o arquivo de volta para `notes/` — sem renomear, porque o id da nota vem
do nome do arquivo. O botão **"Abrir a lixeira"** em Configurações leva direto lá.

Nada esvazia a lixeira automaticamente; apagar de vez é decisão sua, no explorador de arquivos.

### O formato anterior

Nota gravada em `.properties` (formato até esta versão) é convertida para `.html` na primeira
leitura, com posição, tamanho, cor, formatação e o estado de minimizada preservados — inclusive
o `rtf=` de duas versões atrás, que vira HTML na conversão. O arquivo antigo vai para
`legado/`, não para o lixo: mudança de formato não deve ser caminho sem volta.

## Estrutura

| Arquivo | Papel |
| --- | --- |
| [RecadosApp.java](src/main/java/com/recados/RecadosApp.java) | ponto de entrada, ciclo de vida das janelas, bandeja do sistema |
| [NoteFrame.java](src/main/java/com/recados/NoteFrame.java) | a janela da nota: arrastar, redimensionar, atalhos, autosave |
| [Note.java](src/main/java/com/recados/Note.java) | o modelo: texto, geometria, cor, fixação |
| [NoteStore.java](src/main/java/com/recados/NoteStore.java) | leitura e gravação dos `.html` em `~/.recados` |
| [HtmlText.java](src/main/java/com/recados/HtmlText.java) | escapar texto e converter os formatos anteriores para HTML |
| [SettingsDialog.java](src/main/java/com/recados/SettingsDialog.java) | a janela de Configurações |
| [Autostart.java](src/main/java/com/recados/Autostart.java) | liga e desliga o início automático com o Windows |
| [Palette.java](src/main/java/com/recados/Palette.java) | as 6 cores |
| [Icons.java](src/main/java/com/recados/Icons.java) | ícone da bandeja, desenhado em runtime |
| [install-startup.ps1](install-startup.ps1) · [uninstall-startup.ps1](uninstall-startup.ps1) | o mesmo atalho, para instalação por script |
| [checks/](checks) · [run-checks.ps1](run-checks.ps1) | as checagens e o script que compila e roda |
| [package-app.ps1](package-app.ps1) | empacota o `Recados.exe` com jpackage, para o ícone da barra de tarefas |
