# Rise Musique — notas do projeto

## Versão GPT (0.7.0)

A branch `gpt` agora usa `com.rise.musique.gpt`, assinatura local própria e releases
em `Marcelov07br/rise-musique-releases`. Consultar `UPDATES.md` e `RELEASE_NOTES.md`
para o fluxo atual. As informações abaixo documentam a versão original do Grok.

App Android (Kotlin + Jetpack Compose + Material 3) que busca e baixa música/vídeo do YouTube com yt-dlp + ffmpeg embutidos (youtubedl-android 0.18.1). Dono: Marce. Versão atual: 0.6 código / build "v8" (debug).

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

