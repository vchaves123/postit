| Trocar a cor | botão do meio-círculo ou `Ctrl+E` (6 cores; a padrão é azul) |# Recados

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
| Trocar a cor | botão do meio-círculo ou `Ctrl+E` (6 cores) |
| Formatar texto | `Ctrl+B` / `Ctrl+I` / `Ctrl+U`, listas e links no menu **Formatar** |
| Fixar/soltar no topo | botão do ponto (cheio = fixada) ou `Ctrl+T` |
| Minimizar a nota | botão da barra ou `Ctrl+W` — **não apaga**, volta pela bandeja |
| Apagar a nota | `Ctrl+D` ou o menu de contexto — sempre pede confirmação, e vai para a lixeira |
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

O documento é **HTML**, no menu **Formatar** do clique direito:

| O quê | Como |
| --- | --- |
| Negrito, itálico, sublinhado | `Ctrl+B` / `Ctrl+I` / `Ctrl+U`, ou o menu |
| Lista com marcadores | Formatar → Inserir lista |
| Link | Formatar → Inserir link — a seleção vira o rótulo; sem seleção, o endereço |
| Abrir um link | **Ctrl+clique** |
| Copiar com formatação | Formatar → Copiar tudo |
| Limpar formatação | Formatar → Limpar formatação (na seleção, ou na nota toda) |

**Colar em e-mail e navegador** funciona porque a cópia oferece `text/html` *e* texto puro —
quem aceita HTML recebe a formatação, quem não aceita recebe o texto. É o motivo de o formato
ser HTML e não RTF.

Ctrl+clique, e não clique simples, porque o painel é editável: ali o clique é do cursor de
texto. Pelo mesmo motivo não dá para usar `HyperlinkListener`, que só dispara em painel
somente-leitura.

A cor do texto vem da paleta da nota, não do documento: trocar a cor recolore tudo. Os links
ficam de fora — azul de link não é decoração, é sinal de que dá para clicar.

Cada nota grava **os dois formatos**: `html=` com a formatação e `text=` com o texto puro. O
texto puro mantém o arquivo pesquisável com `grep` e alimenta a lista da bandeja.

### Notas de versões anteriores

Abrem sem nada a fazer. Nota com `rtf=` (da versão anterior a esta) é convertida para HTML ao
ser aberta e passa a gravar `html=`; nota só com `text=` também. Se o HTML estiver corrompido,
a nota abre pelo texto puro em vez de falhar.

### Duas armadilhas do Swing que valem registro

`InsertHTMLTextAction` **não insere nada quando o cursor está no offset 0** — e não reclama.
Como a nota abre com o cursor em 0, lista e link desapareciam em silêncio se você não clicasse
antes. Por isso o cursor é levado para o fim quando está em zero.

Marcar o texto com o atributo `HTML.Tag.A` para criar link **não funciona**: o escritor emite
`<a href><u><p-implied></u></a>`, sem o rótulo. O link precisa entrar como HTML, pelo parser.

## Checagens

```bash
powershell -ExecutionPolicy Bypass -File run-checks.ps1
```

No Linux/macOS, `./run-checks.sh`. Cada grupo usa um diretório temporário próprio, então as
checagens nunca tocam as suas notas.

Cobrem lixeira e restauração, a migração de `~/.postit`, minimizar sem apagar, `WM_CLOSE` sem
minimizar, apagar sem ressuscitar, o ida-e-volta do HTML, listas, links, a colagem com
`text/html` e a conversão de notas antigas em RTF ou texto puro.

**Por que não JUnit e `mvn test`:** nesta máquina o Kaspersky encerra o booter do
maven-surefire como `PDM:Trojan.Win32.Generic`, então `mvn test` nunca chega a rodar. As
checagens são classes Java comuns, compiladas contra o jar e executadas direto — sem
dependência de teste no `pom.xml` e sem depender de um processo que o antivírus mata.

## Dois monitores com escalas diferentes

Arrastar a nota entre um monitor a 100% e outro a 150% dava dois problemas, com causas
diferentes. Os dois estão corrigidos, e o app usa a escala do sistema em cada monitor.

**A nota crescia.** O Java reinterpreta o tamanho na escala do monitor novo: `280x260` chega
como `350x325` num monitor a 125%. Isso era gravado, então a nota inflava a cada travessia,
para sempre. Agora **só a alça** muda o tamanho gravado; qualquer mudança que venha de fora é
desfeita.

**A nota saltava e voltava.** Com escalas mistas o espaço de coordenadas tem um **vão**: um
monitor de 1920 px a 150% ocupa 1280 unidades, então a faixa entre o fim dele e o início do
outro não pertence a monitor nenhum:

```
Display0 (150%):  x de -1920 a -640      Display1 (100%):  x de 0 a 1920
                            └── vão de -640 a 0 ──┘
```

Pedir uma posição nesse vão dá resultado imprevisível — medindo aqui, `x=-200` virou `x=660`.
E não há transformação para inverter: compensar subtraindo o erro observado errava por 1010 px.
O que existe é uma regra, verificada na medição: **posição dentro da faixa válida de um monitor
é obedecida ao pixel**.

Então, durante o arraste, a nota fica ancorada no monitor onde está o ponteiro. Ela acompanha o
cursor, para na borda enquanto o ponteiro ainda está do outro lado, salta para a borda oposta
quando o ponteiro atravessa, e volta a acompanhar livremente.

O custo: em configuração de escalas mistas, a nota não fica meio em cada monitor durante o
arraste. Em monitor único, ou com todos na mesma escala, nada muda — inclusive continua dando
para deixar a nota meio fora da tela.

## Onde as notas ficam

`~/.recados/notes/<uuid>.properties` — um arquivo por nota, texto e geometria juntos. Escrever
uma nota nunca mexe nas outras, e a gravação é atômica (arquivo temporário + `move`), então
uma falha no meio não deixa nota truncada. Um `.lock` no diretório impede duas instâncias
brigando pelos mesmos arquivos.

A cor vai gravada pelo **nome** (`color=Azul`), nunca pela posição na paleta: reordenar as
cores não pode repintar nota já gravada. Arquivo da versão que gravava `colorIndex=` continua
sendo lido pela ordem antiga daquela época, que está fixa em `NoteStore`.

### Minimizar não é apagar

O botão da barra de título **minimiza** a nota — barra horizontal, como o minimizar do Windows: ela continua
no disco, aparece marcada como `(minimizada)` na lista da bandeja, e voltar é um clique ali. O
estado fica gravado em `visible=`, então nota minimizada continua minimizada no próximo início.

Apagar é ação separada e explícita — `Ctrl+D` ou o menu de contexto — e **sempre** pede
confirmação, inclusive em nota em branco. Antes um `×` apagava e a nota vazia ia embora sem
perguntar, o que transformava um clique no lugar errado em perda silenciosa.

### Lixeira

Apagar **não** remove o arquivo: ele vai para `~/.recados/trash/<uuid>-<timestamp>.properties`.
A pasta nasce no primeiro apagar, e o carimbo de tempo garante que restaurar uma nota e apagar
de novo não sobrescreve a cópia anterior.

Para restaurar, mova o arquivo de volta para `notes/` — sem renomear, porque o id da nota vem
do nome do arquivo. O botão **"Abrir a lixeira"** em Configurações leva direto lá.

Nada esvazia a lixeira automaticamente; apagar de vez é decisão sua, no explorador de arquivos.

## Estrutura

| Arquivo | Papel |
| --- | --- |
| [RecadosApp.java](src/main/java/com/recados/RecadosApp.java) | ponto de entrada, ciclo de vida das janelas, bandeja do sistema |
| [NoteFrame.java](src/main/java/com/recados/NoteFrame.java) | a janela da nota: arrastar, redimensionar, atalhos, autosave |
| [Note.java](src/main/java/com/recados/Note.java) | o modelo: texto, geometria, cor, fixação |
| [NoteStore.java](src/main/java/com/recados/NoteStore.java) | leitura e gravação em `~/.recados` |
| [SettingsDialog.java](src/main/java/com/recados/SettingsDialog.java) | a janela de Configurações |
| [Autostart.java](src/main/java/com/recados/Autostart.java) | liga e desliga o início automático com o Windows |
| [Palette.java](src/main/java/com/recados/Palette.java) | as 6 cores |
| [Icons.java](src/main/java/com/recados/Icons.java) | ícone da bandeja, desenhado em runtime |
| [install-startup.ps1](install-startup.ps1) · [uninstall-startup.ps1](uninstall-startup.ps1) | o mesmo atalho, para instalação por script |
| [checks/](checks) · [run-checks.ps1](run-checks.ps1) | as checagens e o script que compila e roda |
| [package-app.ps1](package-app.ps1) | empacota o `Recados.exe` com jpackage, para o ícone da barra de tarefas |
