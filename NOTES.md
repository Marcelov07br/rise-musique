# Rise Musique — notas do projeto

## Estado atual (0.9.1, publicada em 10/10/2026) — linha principal: `main`

- `main` é a linha de desenvolvimento. A branch `gpt` (trabalho de 0.7–0.9.1) foi incorporada em `main`
  por fast-forward em 10/10/2026 e fica só como histórico (não apagar, não usar mais).
- Nome visível: "Rise Musique". Application ID interno `com.rise.musique.gpt` — NÃO mudar (mudar = outro app,
  sem atualização por cima da 0.9.0 instalada no celular do Marce).
- Atualização no app: consulta `https://api.github.com/repos/Marcelov07br/rise-musique-releases/releases/latest`
  (repo público), exige tag `v<versionName>`, release estável, asset `update.json` {versionCode, versionName, asset, sha256}
  e o APK nomeado em `asset` (agora `RiseMusique-<versão>.apk`; antigos eram `RiseMusique-GPT-<versão>.apk`).
  Valida tamanho, SHA-256, pacote, versionCode maior e **mesmo certificado** do app instalado.
- Assinatura: releases 0.7–0.9.0 assinadas com `signing/rise-musique-gpt.jks` (alias `rise-musique-gpt`,
  DN `CN=Rise Musique GPT`, SHA-256 do certificado `788c94b2…41ffa6609`), criada por `setup-signing.py`
  no PC Windows do Marce. A chave NÃO está neste computador nem no Git. APK debug daqui usa a debug.keystore
  e não instala por cima da versão de release.

### Como lançar uma versão (Linux, neste computador)
A chave fica em `signing/` (ignorado pelo Git, chmod 600; nunca commitar nem imprimir senhas).
1. Aumentar versionCode/versionName em `app/build.gradle.kts`; atualizar `RELEASE_NOTES.md` (português, sem "GPT").
2. `./build-local.sh` — lê `signing/credentials.json`, usa a cópia local do .jks, roda assembleRelease + lintRelease + testes e `scripts/prepare-release.py`.
3. Conferir: `apksigner verify --print-certs delivery/v<versão>/RiseMusique-<versão>.apk` → SHA-256 `788c94b2df3707ba4f4ebdc3d8912e5dba1cc20ce6cca51be1fa89741ffa6609`.
4. `gh release create v<versão> --repo Marcelov07br/rise-musique-releases --title "Rise Musique <versão>" -F delivery/v<versão>/RELEASE_NOTES.md --latest delivery/v<versão>/RiseMusique-<versão>.apk delivery/v<versão>/update.json`
5. Commit + push em `main`.

### Como lançar uma versão (genérico / Windows)
1. Aumentar versionCode/versionName em `app/build.gradle.kts`; atualizar `RELEASE_NOTES.md`.
2. Ter `signing/` (jks + credentials.json) disponível localmente e exportar `RISE_KEYSTORE`, `RISE_STORE_PASSWORD`,
   `RISE_KEY_ALIAS`, `RISE_KEY_PASSWORD` (ou `build-local.ps1` no Windows, que lê `signing/credentials.json`).
3. `./gradlew :app:assembleRelease :app:lintRelease :app:testDebugUnitTest`
4. `python3 scripts/prepare-release.py` → `delivery/v<versão>/` (APK + update.json + notas).
5. `gh release create v<versão> --repo Marcelov07br/rise-musique-releases --title "Rise Musique <versão>" -F delivery/v<versão>/RELEASE_NOTES.md delivery/v<versão>/RiseMusique-<versão>.apk delivery/v<versão>/update.json`
6. Commit + push em `main`.

## Histórico (versão original do Grok)

## Como compilar (no computador do bot)
- JDK 17 em `~/jdk`, Android SDK em `~/android`, Gradle via `./gradlew`.
- `export JAVA_HOME=~/jdk ANDROID_HOME=~/android`
- APK: `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk` (só arm64-v8a, ~75 MB)
- Prints: `./gradlew :app:recordPaparazziDebug` → `app/src/test/snapshots/images/` (dados de exemplo; imagens da internet não carregam)
- APKs entregues no Google Drive do Marce (RiseMusique-debug-vN.apk). Chat não aceita >25 MB.
- Emulador não roda bem aqui (falta memória); teste real é no celular do Marce (Samsung SM-S948B, Android 17).

## Arquitetura (app/src/main/java/com/rise/musique)
- `Engine.kt` — busca (`-J --flat-playlist`, ytsearch5 / playlist), stream de prévia (`-f ba -g`), download (formatos MP3/M4A/Opus/FLAC, vídeo MP4 por altura, corte `--download-sections`, tags e capa quadrada), cópia pra pasta SAF; `HistoryStore` (history.json).
- `DownloadWorker.kt` — WorkManager, um trabalho por item, Mutex = um por vez, notificação de progresso, cancelar/tentar de novo; `Jobs` guarda os dados de cada item.
- `PlaybackService.kt` — MediaSessionService com ExoPlayer (notificação, tela de bloqueio); TeeAudioProcessor → `AudioLevels.kt` (8 faixas pras ondas); atualiza o widget.
- `MainViewModel.kt` — estado da UI (`UiState`), MediaController, fila de reprodução, letras, temporizador, biblioteca, backup.
- `Lyrics.kt` — LRCLIB (letras sincronizadas), limpa título do YouTube.
- `Library.kt` — favoritos, playlists, export/import de backup (JSON).
- `QuickDownloadActivity.kt` — "Baixar com Rise" no Compartilhar.
- `RiseWidget.kt` — widget Glance.
- `RiseApplication.kt` — init do yt-dlp/ffmpeg + relatório de crash (filesDir/crash.txt, mostrado na próxima abertura).
- `ui/` — `Theme.kt` (preto puro / claro neutro), `Components.kt` (PillButton, PillSelector que estica, RoundIconButton, springPress, cascade), `RiseApp.kt` (tela principal, painel de histórico/biblioteca, voo do download), `PlayerSheet.kt` (mini player → player cheio, fundo desfocado, capa que respira/arremessável, anel de ondas, letras, toque duplo, temporizador).

## Decisões e preferências do Marce
- Visual sóbrio, pílulas, ícones arredondados, molas. Bounce LIBERADO (ele pediu), mas todo valor animado que mexe em tamanho/margem deve ser travado em 0..1 (crash da v6 foi margem negativa por overshoot).
- Mandar resumo + caminhos, não código inteiro no chat.
- Rodadas: Marce dá uma ideia, o bot sugere outras, Marce aprova, aí implementa.

## Próximos passos combinados
1. Subir o código pro GitHub privado `Marcelov07br/rise-musique`.
2. Atualização automática: repositório público `rise-musique-releases` com os APKs; GitHub Actions compila/assina/publica ao marcar tag `vX.Y`; app confere a release ao abrir. Precisa: chave de assinatura definitiva (guardar cópia fora do GitHub) + token do Marce com acesso aos dois repos (fazer no chat particular). Trocar a chave exige reinstalar uma vez — usar Exportar/Importar backup.

