# Napkin Runbook

## Curation Rules
- Reordenar por importância a cada leitura.
- Manter só o que se repete e tem valor.
- Máximo 10 itens por categoria.
- Cada item tem data + "Do instead".

## Pendências Abertas (Prioridade Máxima)

1. **[2026-08-13] Foco de áudio pausa só às vezes — NÃO resolvido**
   Sintoma do usuário: primeiro teste pausou certo ao abrir YouTube; no segundo
   teste a música continuou tocando por cima. Intermitente.
   Já implementado e verde em teste: `AudioFocusPolicy` (6 testes JVM),
   `AudioFocusManager`, `request()` em `play()` e em `resume()`,
   `abandon()` só em `stop()` público.
   Do instead: investigar por que o listener não dispara na 2ª vez. Hipóteses não
   testadas — (a) `hasFocus` fica `true` após LOSS permanente, então `request()`
   retorna cedo sem repedir; (b) `AUDIOFOCUS_LOSS` deveria zerar `hasFocus` e não
   zera; (c) `wasPlayingBeforeLoss` calculado depois do estado já ter mudado.
   Começar por logar `hasFocus` em `request()` e resetar `hasFocus=false` no
   `AudioFocusAction.PAUSE`.

2. **[2026-08-13] Trabalho não commitado na árvore**
   `AudioFocusPolicy.kt`, `AudioFocusManager.kt`, alterações em
   `BassPlaybackEngine.kt`, `PlaybackController.kt`, `MusicPlayerManager.kt` e
   `MusicPlayerManagerTest.kt` + `AudioFocusPolicyTest.kt`.
   Do instead: confirmar com o usuário antes de commitar — o item 1 acima ainda
   está aberto e pode exigir mudar o `AudioFocusManager`.

## Domain Behavior Guardrails

1. **[2026-08-13] A UI toca por `PlaybackViewModel` → `PlaybackController`, não pelo `MusicPlayerManager`**
   `PlayerViewModel` é código morto (zero usos). Persistência que ficar só no
   `MusicPlayerManager.playPlaylist()` nunca executa.
   Do instead: para persistir algo de reprodução, observar
   `PlaybackController.playlistContext` (StateFlow) no `MusicPlayerManager`.

2. **[2026-08-13] Media3 não gerencia foco de áudio aqui**
   O player é BASS por trás de um `SimpleBasePlayer` (`BassMediaSessionAdapter`),
   não ExoPlayer. `setAudioAttributes(handleAudioFocus = true)` não existe nesse
   caminho.
   Do instead: foco é responsabilidade nossa, via `AudioFocusManager`.

3. **[2026-08-13] Ao testar restauração, cuidado com faixa perto do fim**
   Uma sessão inteira foi diagnosticada errado porque a faixa restaurada estava em
   2:13 de 2:22 e acabou 9s depois do play — o `stop()` de fim de faixa devolveu o
   foco, parecendo bug.
   Do instead: sempre reproduzir com música tocando desde o começo.

4. **[2026-08-13] `stop()` do engine tem dois significados**
   `stop()` público devolve o foco; `stopInternal(abandonFocus = false)` é troca de
   faixa e mantém o foco. Soltar e repedir entre faixas abre janela para outro app
   retomar.
   Do instead: nunca chamar `stop()` público em troca de faixa.

5. **[2026-08-13] `_failure_payload` retorna dict, não string JSON**
   Documentado em `helpers.py:14` — serializar ali causaria JSON dentro de string.
   Do instead: quem chama faz `json.dumps()`. Coberto por `tests/test_contract.py`.

6. **[2026-08-13] `fetch_video_info` aninha tudo em `data`**
   O Kotlin lê por `data.title`, não pela raiz.
   Do instead: ao assertar sobre esse retorno, entrar em `data` primeiro.

## Execution & Validation

1. **[2026-08-13] Testes Python não têm discovery**
   Não existe `tests/__init__.py`; `unittest discover` falha.
   Do instead: `cd android/app/src/main/python && PYTHONPATH=. python3 -B tests/test_x.py`
   (use `-B`: bytecode obsoleto já causou falha fantasma que custou meia hora).

2. **[2026-08-13] Build debug instala como `com.example.ytdown.native`**
   `applicationIdSuffix ".native"`. A Activity continua em `com.example.ytdown.MainActivity`.
   Do instead: `adb shell am start -n com.example.ytdown.native/com.example.ytdown.MainActivity`.

3. **[2026-08-13] Ver o estado real do player no aparelho**
   Do instead: `adb shell run-as com.example.ytdown.native cat shared_prefs/player_state.xml`
   — foi o que provou que nada era persistido.

4. **[2026-08-13] `./gradlew` pode estourar o timeout da ferramenta**
   Builds limpos com Chaquopy passam de 10 min.
   Do instead: rodar em background redirecionando para log, e esperar com
   `until grep -q "^EXIT=" log; do sleep 15; done`.

5. **[2026-08-13] `monkey -p com.google.android.youtube` só abre o app, não toca vídeo**
   Sem áudio tocando, o YouTube não pede foco, e o teste de foco não vale nada.
   Do instead: pedir ao usuário para dar play num vídeo, ou achar outra forma de
   fazer outro app pedir foco de verdade.

6. **[2026-08-13] `BUILD SUCCESSFUL` não prova que testes rodaram**
   Do instead: conferir
   `grep -hoE 'tests="[0-9]+".*failures="[0-9]+"' app/build/test-results/testDebugUnitTest/*.xml`
   ou usar `--rerun-tasks`.

## Shell & Command Reliability

1. **[2026-08-13] Comando em background reseta o cwd da sessão**
   Depois de um `run_in_background`, o `cd` anterior se perde e comandos relativos
   rodam do lugar errado — foi assim que um `find __pycache__` limpou o diretório
   errado e um `.pyc` mutado sobreviveu.
   Do instead: usar caminho absoluto ou refazer o `cd` no mesmo comando.

2. **[2026-08-13] `str.replace` em Python casa substring e duplica linha**
   Padrão com 8 espaços casou dentro de linha com 16 de indentação.
   Do instead: incluir contexto único no padrão e conferir com `grep -n` depois.

## User Directives

1. **[2026-08-13] Commits sem atribuição**
   `~/.claude/CLAUDE.md` diz que atribuição está desativada globalmente.
   Do instead: não adicionar `Co-Authored-By` nos commits.

2. **[2026-08-13] Commits separados por assunto, mensagens explicativas**
   O usuário pede `commit e sobe` com frequência e valoriza mensagem que explica a
   causa raiz, não "update".
   Do instead: agrupar por história (não por tipo de arquivo) e escrever o porquê
   no corpo.

3. **[2026-08-13] `scripts/` está no `.gitignore` (linha 75)**
   Correções na automação ADB não sobem sem `-f`.
   Do instead: avisar o usuário; não forçar contra o ignore dele.

4. **[2026-08-13] Trabalho em andamento do usuário na árvore**
   Ele costuma ter WIP não commitado ao iniciar a sessão.
   Do instead: separar o que é meu do que é dele antes de commitar, e nunca
   commitar o WIP dele sem pedir.

## Contexto do Projeto (estável, consultar antes de investigar)

1. **[2026-08-13] Stack atual, verificada em aparelho Android 16**
   AGP 9.3.1 · Gradle 9.5.0 · Kotlin embutido do AGP (sem plugin `kotlin.android`)
   · KSP 2.3.11 · Hilt 2.60.1 · Room 2.8.4 · Media3 1.11.0 · Compose BOM 2026.08 ·
   compileSdk 37 · Chaquopy 17.0.0 com Python 3.14.0. Build sem warnings.
   Do instead: não reintroduzir `android.builtInKotlin=false` nem
   `android.newDsl=false` — foram removidos porque o KSP passou a funcionar com o
   Kotlin embutido.

2. **[2026-08-13] Chaquopy nunca travou as bibliotecas Kotlin**
   O bloqueio era `force "com.google.dagger:hilt-android:2.56.2"` no
   `app/build.gradle`, herdado de `ksp.useKSP2=false`.
   Do instead: se surgir "não dá para atualizar por causa do Chaquopy", conferir
   pins e a tabela de compatibilidade antes de aceitar a premissa.

3. **[2026-08-13] Plano de remover o Chaquopy existe e é opcional**
   `docs/superpowers/plans/2026-08-13-remover-chaquopy-cpython-3-14-7.md`.
   Python 3.14.7 oficial para Android existe (python.org, aarch64 e x86_64) e a
   remoção é viável, mas o ganho de APK é quase nulo — o valor é controle de versão.
   Do instead: tratar como escolha, não necessidade.

4. **[2026-08-13] Maior peso do APK é o ffmpeg, não o Python**
   `libffmpeg_exe.so` = 46,7 MB. Chaquopy inteiro = ~41,6 MB.
   Do instead: se o objetivo for tamanho, atacar o ffmpeg (build mínimo) e ABI splits.
