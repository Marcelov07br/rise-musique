# Rise Musique — notas do projeto (leia isto primeiro)

## Estado atual: 0.19.0 (10/10/2026) — linha principal `main`
- Repositório do código: `Marcelov07br/rise-musique` (privado). Releases públicas: `Marcelov07br/rise-musique-releases`.
- Nome visível "Rise Musique". **Application ID `com.rise.musique.gpt` — NÃO mudar** (outro ID = outro app, sem atualizar por cima).
- A branch `gpt` é histórico antigo (0.7–0.9.1), não usar.
- Teste real é no celular do Marce (Samsung SM-S948B, Android 17). Emulador não roda aqui. Paparazzi = única verificação visual.
- Marce lê português. Rodadas: Marce aprova uma lista → implementar tudo → testes, snapshots (olhar!), build, bump, notas em PT, commit, push, publicar.

## Ambiente (computador do bot / box)
- `export JAVA_HOME=~/jdk ANDROID_HOME=~/android` (JDK 17, SDK em ~/android). Projeto em `/workspace/rise-musique`.
- `./gradlew :app:compileDebugKotlin` (rápido), `./gradlew :app:recordPaparazziDebug` (todos os testes + grava snapshots em `app/src/test/snapshots/images/`).
- Commit sem identidade configurada: `git -c user.name="$(git log -1 --format=%an)" -c user.email="$(git log -1 --format=%ae)" commit …`.
- `gh` autenticado para publicar no repo de releases.

## Receita de release (aprovada)
1. `app/build.gradle.kts`: versionCode +1, versionName novo.
2. `RELEASE_NOTES.md` em português, sem "GPT" (vira o texto da release e a seção "Novidades" de Ajustes → Sobre; é copiado para os assets pela task `notesAsset`).
3. `./build-local.sh` → assembleRelease + lintRelease + testes + `scripts/prepare-release.py` → `delivery/v<versão>/` (APK, update.json, notas). Lint com erro aborta (ex.: APIs `@UnstableApi` do Media3 precisam de `@OptIn`).
4. (opcional) `apksigner verify --print-certs delivery/v<v>/RiseMusique-<v>.apk` → SHA-256 `788c94b2df3707ba4f4ebdc3d8912e5dba1cc20ce6cca51be1fa89741ffa6609`.
5. Commit + `git push origin main`.
6. `gh release create v<v> --repo Marcelov07br/rise-musique-releases --title "Rise Musique <v>" -F delivery/v<v>/RELEASE_NOTES.md --latest delivery/v<v>/RiseMusique-<v>.apk delivery/v<v>/update.json`
- Atualização no app (`Updates.kt`/`UpdateRelease.kt`): lê `releases/latest`, exige tag `v<versionName>`, asset `update.json` {versionCode, versionName, asset, sha256}; confere tamanho, SHA-256, pacote, versionCode maior e mesmo certificado.

## Segredos e assinatura (nunca commitar nem imprimir)
- `signing/rise-musique-gpt.jks` (alias `rise-musique-gpt`) + `signing/credentials.json` (senhas) — só neste box e no PC do Marce; ignorados pelo Git, chmod 600.
- `secrets.properties` (raiz, ignorado; modelo `secrets.properties.example`): `LASTFM_API_KEY`, `LASTFM_API_SECRET` → BuildConfig. Sem eles, "Last.fm não configurado".
- Windows: `build-local.ps1` lê o mesmo `credentials.json`; ou exportar `RISE_KEYSTORE`, `RISE_STORE_PASSWORD`, `RISE_KEY_ALIAS`, `RISE_KEY_PASSWORD`.

## Mapa da arquitetura (`app/src/main/java/com/rise/musique`)
### Núcleo
- `RiseApplication.kt` — init yt-dlp/FFmpeg; handler de crash grava `filesDir/crash.txt` (formato em `ui/About.kt` → `CrashReport`).
- `MainActivity.kt` — Compose host, permissões (mic), tela cheia/PiP, intents (SEND → página Downloads; atalhos `Shortcuts.*`), diálogo de crash (`CrashDialog`), liga `Actions` ao ViewModel.
- `MainViewModel.kt` — `UiState` único; busca, streaming (`togglePreview`), vídeo, rádio, estações (`playStation`), downloads (`dlItems`, `dlLink`, `enqueue`), trecho (`shareSnippet`), aleatório (`shuffleAll`), EQ, stats, biblioteca, backup.
- `PlaybackService.kt` — MediaLibraryService + ExoPlayer embrulhado em `LyricPlayer` (letra na tela de bloqueio); `RiseAudioProcessor` (normalização + EQ + limitador); crossfade/automix com player auxiliar; atualiza widget e `TileState`.
- `Engine.kt` — yt-dlp: busca, info de vídeo, rádio (`RDAMVM`), download (formatos, corte, tags, capa, `--continue`), cópia para pasta SAF; `HistoryStore`.
- `MusicSearch.kt` — busca do YouTube Music (innertube WEB_REMIX), tipos, filtros, paginação, ranking, cache.
- `Streaming.kt` — URIs `rise-stream://<id>`, resolução/re-resolução em 403/410, prefetch, `SimpleCache` (guardar enquanto toca).
- `Video.kt` — `VideoController` (ExoPlayer separado; MergingMediaSource vídeo+áudio+legenda; qualidade).
- `DownloadWorker.kt` — WorkManager, um por vez, retry com backoff, Só no Wi-Fi.
- `Follow.kt` — seguir artista (worker periódico + notificação "Baixar").
- `Dsp.kt` / `AudioFx.kt` — EQ 10 bandas biquad, presets, perfis por saída (`EqStore`).
- `TrackInfo.kt` — loudness EBU R128, gênero/ano/BPM, `LibraryScanWorker`, `Moods`.
- `Beats.kt`, `Crossfade.kt` — grade de batidas e regras de transição.
- `Lyrics.kt` (LRCLIB), `LyricPlayer.kt`, `LastFm.kt`, `Stats.kt` (PlayStats, retrospectiva), `RecapImage.kt`.
- `Library.kt`, `BackupMerge.kt`, `PlaylistEdits.kt`, `AutoLibrary.kt` (Android Auto), `LocalMedia.kt` (importar arquivos/pasta).
- `Stations.kt` — estações automáticas (artistas mais ouvidos, climas, Descobertas), capa gerada em Canvas (cache em `cacheDir/stations`), `StationQueue` (intercala rádios + faixas locais).
- `Snippet.kt` — cartão 9:16 e MP4 de 15 s (Media3 Transformer, overlay por linha de letra). Streams: só imagem (decisão: não recortar áudio do YouTube).
- `Shortcuts.kt` — atalhos dinâmicos do ícone + `PlayTile` (bloco de Configurações rápidas) + `TileState`.
- `RiseWidget.kt` — widget Glance responsivo (2x2, 4x2, 4x4). Paparazzi não renderiza Glance.
- `QuickDownloadActivity.kt` — "Baixar com Rise" no menu Compartilhar (baixa direto com as últimas escolhas).
- `Voice.kt` — busca por voz (SpeechRecognizer pt-BR).
### UI (`ui/`)
- `RiseApp.kt` — raiz: `NavState` (pilha por aba), `SeekableTransitionState` (transição interrompível + volta preditiva), barra flutuante (`NavigationDock`), topo com título colapsável, `MainPane` (Buscar), `ResultCard`.
- `Nav.kt` — rotas (Album, Playlist, Artist, Smart, Downloads), `sharedCover` (elemento compartilhado, dono único), `morphCorner`, `enterRise`.
- `Motion.kt` — tokens: `OpenMs 720`/`CloseMs 560` (relógio linear da transição), `coverOpen` (mola ζ0.8, k210) / `coverClose` (ζ0.8, k300), `pageEnter` com atraso de 80 ms. Overshoot só em escala/translação.
- `Titles.kt` — `LargeTitle` 34sp, `rememberTitleCollapse`, `collapsingHeight`, `bottomSpace` (espaço do dock + mini), `accentColor` (rosa discreto), `bleed`.
- `LibraryHome.kt` (Início/Biblioteca), `Home.kt` (hero cards, estações), `Tint.kt` (tons suaves por capa), `CollectionScreen.kt`, `ArtistScreen.kt`, `Smart.kt`.
- `PlayerSheet.kt` — mini → cheio, capa centrada (`PlayerLayout.art`), Ken Burns (`Motion2.kt`), letras, fila, compartilhar trecho (`ShareSheet.kt`).
- `DownloadHub.kt` — página Downloads (`DownloadPage`, `queueItems`); `DownloadOptions.kt`, `CutSheet.kt`.
- `SearchParts.kt` — filtros (chip "Filtros" → folha), voz, esqueleto, resultados de coleção.
- `SettingsSheet.kt` (+ Sobre em `About.kt`), `EqSheet.kt`, `StatsSheet.kt`, `RecapSheet.kt`, `VideoPlayer.kt`, `TrackActions.kt`, `UpdateDialog.kt`, `Visuals.kt` (GlassBox, paleta, arte viva).
### Recursos
- Ícone: `res/drawable/ic_launcher_foreground.xml` (wordmark "rise" em Roboto Bold, tracking −1sp, 50dp de largura na zona segura), `ic_launcher_monochrome.xml` (ícone temático), fundo `#111113`. Splash (`Theme.Rise.Splash`) usa o mesmo foreground.
- Regerar o wordmark: fontTools com `/usr/share/fonts/truetype/sand-box/google/Roboto/Roboto-VariableFont_wdth,wght.ttf` instanciada em wght 700 (script na conversa de 0.19; ~20 linhas com SVGPathPen).

## Testes
- Unidade + Robolectric em `app/src/test/java/com/rise/musique/` (`Round15..19Test`, `TransitionCrashTest` — navegação, troca de aba, volta, Downloads, vídeo; sempre manter).
- Snapshots em `Snapshots.kt` (`RoundNN` por rodada). Dois elementos com a mesma chave de `sharedCover` visíveis ao mesmo tempo travam a UI (laço infinito) — chaves únicas!
- Animação infinita + `onSizeChanged` que muda layout = laço; medir com `wrapContentHeight(unbounded = true)`.

## Decisões e preferências do Marce
- Visual sóbrio, pílulas, vidro, molas com quique leve. Nada de cor dinâmica do sistema (cores só da capa). Acento rosa discreto.
- Todo valor animado que mexe em tamanho/margem é travado; overshoot só via graphicsLayer.
- Mandar resumo + caminhos, não código inteiro.

## Lacunas conhecidas / não testado no aparelho
- MP4 do trecho (Transformer image+áudio+overlay), compartilhamento.
- Atalhos e bloco de Configurações rápidas; ícone temático e splash no aparelho real.
- Estações (rádio real, mistura local, Descobertas), cores das capas com imagens reais.
- Ken Burns: fluidez/bateria; desliga com economia de bateria e "remover animações".
- Nova transição de álbum (0.19): sensação real, volta preditiva com o novo relógio.
- Busca: tocar ao toque, link de playlist na página Downloads, "Colar" da área de transferência.
- Widget Glance (sem snapshot), PiP, EQ por dispositivo Bluetooth.
- "Guardar" no player ainda salva direto (não passa pela página Downloads). "Baixar com Rise" no Compartilhar continua baixando direto.
- Botão Baixar fica desabilitado até escolher tipo/formato/qualidade (regra antiga de `DownloadChoices`).

## Backlog de ideias
- DSD/lossless de verdade via decoder FFmpeg do Media3 (extensão ffmpeg) — ALAC, APE, DSF/DFF.
- Cantarolar para buscar (humming) — precisa de serviço externo; fora até achar fonte legítima.
- Discord: "ouvindo agora" (Rich Presence via app/OAuth) — opções a estudar.
- Teste de EQ por dispositivo (perfis por fone Bluetooth) com o Marce.
- Imagem do canal do artista (YouTube Music) no topo do artista.
- Capas animadas reais se aparecer fonte pública legítima.
- Letras: tradução, karaokê por palavra; editar sincronização.
- Fila: arrastar para reordenar com prévia; "tocar a seguir".
- Downloads: playlists inteiras com seleção na página Downloads; pasta por artista/álbum.
