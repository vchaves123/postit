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
| Fixar/soltar no topo | botão do ponto (cheio = fixada) ou `Ctrl+T` |
| Minimizar a nota | botão da barra ou `Ctrl+W` — **não apaga**, volta pela bandeja |
| Apagar a nota | `Ctrl+D` ou o menu de contexto — pede confirmação e vai para a lixeira; nota **em branco** apaga direto |
| Mostrar todas as notas | `Ctrl+Shift+A` ou clique no ícone da bandeja |
| Configurações | `Ctrl+,`, menu da bandeja, ou menu de contexto |
| Sair | `Ctrl+Q` ou o menu da bandeja |

Clique com o botão direito em qualquer parte da nota para o menu de contexto.

O texto salva sozinho meio segundo depois da última tecla, e também ao mover, redimensionar
ou perder o foco — não existe botão de salvar.

## Por que azul, e não amarelo

A cor padrão da nota e o ícone do aplicativo são **azuis** de propósito. A 3M tem registro da
cor "canary yellow" aplicada sobre a superfície inteira de notas adesivas, e há relato de
acionamento contra versão *digital* de nota adesiva na mesma cor. O amarelo continua na paleta
como uma das seis opções — o que ele não é mais é a identidade visual do app.

Não é orientação jurídica; é distância barata de tomar.

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
| três linhas com marcadores | lista — **com texto selecionado, cada linha vira um item**; sem seleção, um item vazio | menu Formatar |
| dois elos de corrente | inserir link — a seleção vira o rótulo; sem seleção, o endereço | menu Formatar |
| borracha | limpar formatação (na seleção, ou na nota toda) | menu Formatar |

O **recuo da lista é curto de propósito**: o marcador cai na primeira coluna do texto, e o texto
do item fica 7 px à direita. O padrão do Swing para `<ul>` é 50 px — numa nota de 280 px isso
come um sexto da linha e joga o ponto para o meio do nada. O valor foi medido no pixel pintado,
não escolhido no olho, e uma checagem trava a medida.

Abrir um link é **Ctrl+clique**. Copiar a nota com formatação continua no menu **Formatar** do
clique direito, junto de tudo o que está na barra — é ação da nota inteira, não da seleção, e
tirá-la da barra é o que faz os seis ícones caberem na nota mais estreita (160 px).

A barra fica **embaixo**, e não na barra de título, para não misturar ações da *janela* (nova
nota, cor, fixar, minimizar) com ações do *texto*. E aparece só com a nota em foco: nota que
você está apenas lendo não precisa dela. A altura do rodapé não muda quando ela aparece — se
mudasse, o texto pularia e a rolagem escorregaria a cada troca de foco. Por causa da barra, a
altura mínima da nota subiu de 120 para 150 px.

O menu **Formatar** do clique direito continua existindo: é onde os atalhos aparecem escritos.

**Colar em e-mail e navegador** funciona porque a cópia oferece `text/html` *e* texto puro —
quem aceita HTML recebe a formatação, quem não aceita recebe o texto. É o motivo de o formato
ser HTML e não RTF.

Ctrl+clique, e não clique simples, porque o painel é editável: ali o clique é do cursor de
texto. Pelo mesmo motivo não dá para usar `HyperlinkListener`, que só dispara em painel
somente-leitura.

A cor do texto vem da paleta da nota, não do documento: trocar a cor recolore tudo. Os links
ficam de fora — azul de link não é decoração, é sinal de que dá para clicar.

### Notas de versões anteriores

Abrem sem nada a fazer. Nota com `rtf=` (de duas versões atrás) é convertida para HTML, e nota
só com `text=` também — ver "Onde as notas ficam". Se o HTML estiver corrompido, a nota abre
pelo texto puro em vez de falhar.

### Quatro armadilhas do Swing que valem registro

`InsertHTMLTextAction` **não insere nada quando o cursor está no offset 0** — e não reclama.
Como a nota abre com o cursor em 0, lista e link desapareciam em silêncio se você não clicasse
antes. Por isso o cursor é levado para o fim quando está em zero.

Marcar o texto com o atributo `HTML.Tag.A` para criar link **não funciona**: o escritor emite
`<a href><u><p-implied></u></a>`, sem o rótulo. O link precisa entrar como HTML, pelo parser.

**Linha não é sempre a mesma coisa.** Texto digitado na nota vira `<br>` dentro de *um*
parágrafo (`p-implied`); texto **colado** de fora vira um `<p>` por linha. As duas formas
convivem na mesma nota, então quebrar por linha é considerar as duas — e, no caso do parágrafo,
não deixar para trás o `<p>` que ficou vazio, senão sobra uma linha em branco. E o
`HTMLDocument` guarda uma quebra própria no offset 0, então seleção que começa em zero (Ctrl+A)
precisa pular essa primeira posição, senão a lista sai vazia.

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
de `~/.postit`, minimizar sem apagar, `WM_CLOSE` sem minimizar, apagar sem ressuscitar, a barra
de formatação (aparece com o foco, não rouba o foco), o ida-e-volta do HTML, a lista feita a
partir da seleção, links e a colagem com `text/html`.

**Por que não JUnit e `mvn test`:** nesta máquina o Kaspersky encerra o booter do
maven-surefire como `PDM:Trojan.Win32.Generic`, então `mvn test` nunca chega a rodar. As
checagens são classes Java comuns, compiladas contra o jar e executadas direto — sem
dependência de teste no `pom.xml` e sem depender de um processo que o antivírus mata.

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
