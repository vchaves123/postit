# postit

Post-it na área de trabalho, em **Java 21 + Swing**. Cada nota é uma janelinha colorida sem
decoração do sistema, que se lembra de onde estava, do tamanho, da cor e se fica no topo.
Sem dependências externas.

## Executar

```bash
mvn package
```

Depois, no Windows, dê um duplo clique em `postit.bat` (ou rode direto):

```bash
java -jar target/postit.jar
```

No Linux/macOS, use `./postit.sh`.

## Como usar

| Ação | Como |
| --- | --- |
| Mover a nota | arraste pela barra colorida de cima |
| Redimensionar | arraste a alça no canto inferior direito |
| Nova nota | botão `+`, `Ctrl+N`, ou o menu da bandeja |
| Trocar a cor | botão `◑` ou `Ctrl+E` (6 cores) |
| Fixar/soltar no topo | botão `●`/`○` ou `Ctrl+T` |
| Apagar a nota | botão `×` ou `Ctrl+D` (pede confirmação se tiver texto) |
| Mostrar todas as notas | `Ctrl+Shift+A` ou clique no ícone da bandeja |
| Sair | `Ctrl+Q` ou o menu da bandeja |

Clique com o botão direito em qualquer parte da nota para o menu de contexto.

O texto salva sozinho meio segundo depois da última tecla, e também ao mover, redimensionar
ou perder o foco — não existe botão de salvar.

## Onde as notas ficam

`~/.postit/notes/<uuid>.properties` — um arquivo por nota, texto e geometria juntos. Escrever
uma nota nunca mexe nas outras, e a gravação é atômica (arquivo temporário + `move`), então
uma falha no meio não deixa nota truncada. Um `.lock` no diretório impede duas instâncias
brigando pelos mesmos arquivos.

## Estrutura

| Arquivo | Papel |
| --- | --- |
| [PostItApp.java](src/main/java/com/postit/PostItApp.java) | ponto de entrada, ciclo de vida das janelas, bandeja do sistema |
| [NoteFrame.java](src/main/java/com/postit/NoteFrame.java) | a janela da nota: arrastar, redimensionar, atalhos, autosave |
| [Note.java](src/main/java/com/postit/Note.java) | o modelo: texto, geometria, cor, fixação |
| [NoteStore.java](src/main/java/com/postit/NoteStore.java) | leitura e gravação em `~/.postit` |
| [Palette.java](src/main/java/com/postit/Palette.java) | as 6 cores |
| [Icons.java](src/main/java/com/postit/Icons.java) | ícone da bandeja, desenhado em runtime |
