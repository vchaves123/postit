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
| Trocar a cor | botão do meio-círculo ou `Ctrl+E` (6 cores) |
| Fixar/soltar no topo | botão do ponto (cheio = fixada) ou `Ctrl+T` |
| Minimizar a nota | botão da barra ou `Ctrl+W` — **não apaga**, volta pela bandeja |
| Apagar a nota | `Ctrl+D` ou o menu de contexto — sempre pede confirmação, e vai para a lixeira |
| Mostrar todas as notas | `Ctrl+Shift+A` ou clique no ícone da bandeja |
| Configurações | `Ctrl+,`, menu da bandeja, ou menu de contexto |
| Sair | `Ctrl+Q` ou o menu da bandeja |

Clique com o botão direito em qualquer parte da nota para o menu de contexto.

O texto salva sozinho meio segundo depois da última tecla, e também ao mover, redimensionar
ou perder o foco — não existe botão de salvar.

## Iniciar com o Windows

Abra **Configurações** (menu da bandeja, botão direito na nota, ou `Ctrl+,`) e marque
**"Iniciar o Recados com o Windows"**. Não precisa rodar nada por fora — a caixa reflete o
estado real do sistema, e desmarcar desfaz.

Por baixo é um atalho `recados.lnk` na pasta Inicializar do usuário, apontando para `javaw.exe`
com o jar (sem janela de console). Se você mover a pasta do Recados, desmarque e marque de novo
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

## Onde as notas ficam

`~/.recados/notes/<uuid>.properties` — um arquivo por nota, texto e geometria juntos. Escrever
uma nota nunca mexe nas outras, e a gravação é atômica (arquivo temporário + `move`), então
uma falha no meio não deixa nota truncada. Um `.lock` no diretório impede duas instâncias
brigando pelos mesmos arquivos.

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
