# Rise Musique — notas do projeto (leia isto primeiro)

## Estado atual: 0.20.1 (11/10/2026) — linha principal `main`
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
- `HiRes.kt` (0.20) — `HiRes.parseDsf/parseDff/parseAiff` (cabeçalhos puros, testados) + `ChunkExtractor` (um extrator para os três: pacotes fixos + SeekMap linear) + `RiseExtractorsFactory` (nossos primeiro, depois os padrões do Media3). MIMEs próprios `audio/x-rise-dsd-*` e `audio/x-rise-pcm-*be`; `dsdLabel` ("DSD64 · 2.8 MHz → PCM 16/176").
- `Lossless.kt` (0.20) — tags/capa ID3 de DSF/DFF/AIFF na importação; APE/WavPack → FLAC com o binário FFmpeg do youtubedl-android (`nativeLibraryDir/libffmpeg.so`, `LD_LIBRARY_PATH` = pasta com `libavcodec.so` em `noBackupFilesDir/youtubedl-android`). TAK recusado (o FFmpeg do yt-dlp não tem demuxer TAK).
- `androidx/media3/decoder/ffmpeg/*.java` (0.20) — cópia da extensão FFmpeg do Media3 1.5.1 (Apache 2.0) com `getCodecName` estendido para os MIMEs acima. `jniLibs/arm64-v8a/libffmpegJNI.so` = build nosso (ver "Decoder FFmpeg"). `PlaybackService`/`Crossfade` usam `EXTENSION_RENDERER_MODE_PREFER` + `RiseExtractorsFactory`.
- `ArtistBrowse.kt` (0.20) — innertube `browse` (WEB_REMIX) do canal do artista e de álbuns `MPREb_…`: `ArtistPage` (foto, bio, ouvintes, Top músicas, Álbuns, Singles, Vídeos, Parecidos) e `AlbumPage` (faixas). Cache bruto em `cacheDir/ytm/*.json` por 24 h + memória. Nome→canal via busca de artistas (ou id já conhecido dos "parecidos"). Fixtures reais em `app/src/test/resources/ytm/*.json.gz`.
### UI (`ui/`)
- `LiquidGlass.kt` (0.20.1) — `LocalHaze` (HazeState criado no `RiseApp`), `glassSource` no conteúdo (a barra/mini são irmãos, nunca filhos da fonte), `liquidGlass(shape, dark)`: Haze 1.3.1 (blur 26dp, grão, tint; fallback tingido < API 31) + borda especular/sombra interna. Pílula das abas: duas molas (frente firme, trás mole) desenhadas em `drawBehind`; troca de aba `Motion.tabSwitch`.
- `RiseApp.kt` — raiz: `NavState` (pilha por aba), `SeekableTransitionState` (transição interrompível + volta preditiva), barra flutuante (`NavigationDock`), topo com título colapsável, `MainPane` (Buscar), `ResultCard`.
- `Nav.kt` — rotas (Album, Playlist, Artist, Smart, Downloads), `sharedCover` (elemento compartilhado, dono único), `morphCorner`, `enterRise`.
- `Motion.kt` — tokens: `OpenMs 720`/`CloseMs 560` (relógio linear da transição), `coverOpen` (mola ζ0.8, k210) / `coverClose` (ζ0.8, k300), `pageEnter` com atraso de 80 ms. Overshoot só em escala/translação.
- `Titles.kt` — `LargeTitle` 34sp, `rememberTitleCollapse`, `collapsingHeight`, `bottomSpace` (espaço do dock + mini), `accentColor` (rosa discreto), `bleed`.
- `LibraryHome.kt` (Início/Biblioteca), `Home.kt` (hero cards, estações), `Tint.kt` (tons suaves por capa), `CollectionScreen.kt`, `ArtistScreen.kt`, `Smart.kt`.
- `PlayerSheet.kt` — mini → cheio, capa centrada (`PlayerLayout.art`), Ken Burns (`Motion2.kt`), letras, fila, compartilhar trecho (`ShareSheet.kt`).
- `DownloadHub.kt` — página Downloads (`DownloadPage`, `queueItems`); `DownloadOptions.kt`, `CutSheet.kt`.
- `ArtistScreen.kt` (0.20) — perfil estilo Apple Music: foto de ponta a ponta sob a status bar (`bleedTop`), Ken Burns (`kenBurns`, respeita economia/sem animações), parallax 0.5x e esticão no overscroll (NestedScrollConnection `pull`), colapso próprio para a barra; seções Top músicas/Álbuns/Singles & EPs/Vídeos/Na sua biblioteca/Artistas parecidos/Sobre; sem página do YTM cai na lista antiga (`artistPopular`). Fotos ficam em `UiState.artistPhotos` (prefs `artistPhotos`), usadas também na Biblioteca (`artistArt`).
- `RemoteAlbum.kt` (0.20) — álbum do YouTube Music (`Route.RemoteAlbum`): faixas em streaming (`playStreams`), "Baixar tudo" (`downloadAll` → página Downloads), download por faixa.
- `PlayerSheet.kt` (reescrito em 0.20) — layout fixo de tela cheia; abrir/fechar só em graphicsLayer (translationY + `SheetShape` como recorte, capa com translação/escala a partir do canto). `Animatable` com limites 0..1; só limiares viram `derivedStateOf`. `PlayerProbe` conta recomposições (teste `PlayerRecompositionTest`). O toque que abre fica na faixa do mini (a camada cobre a tela inteira, mas o recorte deixa os toques passarem).
- Chaves de capa compartilhada: linhas `cover-al-<álbum>`, destaque do Início `cover-al-hero-<álbum>` (`Route.Album(name, src="hero")`), álbuns remotos `cover-ra-<id>`, artista `cover-ar-<nome>` / parecidos `cover-ar-similar-<nome>`.
- `SearchParts.kt` — filtros (chip "Filtros" → folha), voz, esqueleto, resultados de coleção.
- `SettingsSheet.kt` (+ Sobre em `About.kt`), `EqSheet.kt`, `StatsSheet.kt`, `RecapSheet.kt`, `VideoPlayer.kt`, `TrackActions.kt`, `UpdateDialog.kt`, `Visuals.kt` (GlassBox, paleta, arte viva).
### Recursos
- Ícone: `res/drawable/ic_launcher_foreground.xml` (wordmark "rise" em Roboto Bold, tracking −1sp, 50dp de largura na zona segura), `ic_launcher_monochrome.xml` (ícone temático), fundo `#111113`. Splash (`Theme.Rise.Splash`) usa o mesmo foreground.
- Regerar o wordmark: fontTools com `/usr/share/fonts/truetype/sand-box/google/Roboto/Roboto-VariableFont_wdth,wght.ttf` instanciada em wght 700 (script na conversa de 0.19; ~20 linhas com SVGPathPen).

## Testes
- Unidade + Robolectric em `app/src/test/java/com/rise/musique/` (`Round15..20Test`, `TransitionCrashTest` — navegação, troca de aba, volta, Downloads, vídeo, destaque→álbum, artista→álbum remoto→volta, artista→parecido; sempre manter; `PlayerRecompositionTest` — expansão não recompõe por quadro).
- `Round20Test` gera DSF/DFF de verdade em memória e usa AIFF feitos com ffmpeg (`src/test/resources/hires/`); a decodificação nativa (arm64) NÃO roda na JVM.
- Snapshots com imagem: `SyncImages()` (Coil com dispatcher Unconfined e sem bitmap de hardware) + `src/test/resources/hero.jpg`/`cover.jpg`.
- Snapshots em `Snapshots.kt` (`RoundNN` por rodada). Dois elementos com a mesma chave de `sharedCover` visíveis ao mesmo tempo travam a UI (laço infinito) — chaves únicas!
- Animação infinita + `onSizeChanged` que muda layout = laço; medir com `wrapContentHeight(unbounded = true)`.

## Decisões e preferências do Marce
- Visual sóbrio, pílulas, vidro, molas com quique leve. Nada de cor dinâmica do sistema (cores só da capa). Acento rosa discreto.
- Todo valor animado que mexe em tamanho/margem é travado; overshoot só via graphicsLayer.
- Mandar resumo + caminhos, não código inteiro.

## Decoder FFmpeg (como refazer)
- NDK 27.2.12479018 e CMake 3.22.1 instalados em `~/android` (sdkmanager). FFmpeg n6.0 em `/tmp/ffmpeg` (some ao reiniciar: `git clone --depth 1 --branch n6.0 https://git.ffmpeg.org/ffmpeg.git`).
- configure arm64 (api 26) com `--disable-everything --disable-avformat --enable-swresample` + decoders: dsd_lsbf, dsd_msbf, dsd_lsbf_planar, dsd_msbf_planar, ape, wavpack, tak, alac, flac, pcm_s16be/s24be/s32be/s16le/s24le/s32le/f32be/f32le.
- JNI: `native/ffmpeg/ffmpeg_jni.cc` (do Media3 1.5.1, com patch: taxa/canais do contêiner quando não há extradata; saída reamostrada para ≤192 kHz — DSD64 352,8 k → 176,4 k; contagem real de amostras do swr) + `CMakeLists.txt`; build com o toolchain do NDK, `llvm-strip`, copiar para `app/src/main/jniLibs/arm64-v8a/`.
- `app/lint.xml` ignora `UnsafeOptInUsageError` só no código copiado do Media3.

## Lacunas conhecidas / não testado no aparelho
- 0.20.1: desfoque real só aparece no aparelho (Paparazzi não renderiza RenderEffect direito); custo de GPU do Haze em rolagem; sem lente AGSL (ficou só borda/realce).
- 0.20: tocar DSF/DFF/AIFF de verdade no aparelho (decoder nativo nunca rodou aqui); som do DSD (nível/ruído), busca (seek) em DSD, AIFF 24-bit; conversão APE/WavPack→FLAC pelo binário do yt-dlp (caminho das libs); selo diz "PCM 16" porque o sink atual é 16-bit (float desligado pelo nosso processador de efeitos).
- 0.20: perfil do artista com dados reais (artistas sem "Singles"/"Vídeos", nomes ambíguos → canal errado), Ken Burns + parallax + esticão no aparelho, foto do artista na Biblioteca, álbum remoto tocando/baixando tudo, transição destaque→álbum.
- 0.20: player novo — sensação do arraste, volta preditiva, toques passando pela área recortada (acima/abaixo do mini).
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
- Saída float/24-bit de verdade (RiseAudioProcessor em float) para o selo virar "PCM 24/176"; DSD nativo (DoP) em DACs USB.
- TAK e extratores APE/WavPack nativos (hoje convertem para FLAC na importação).
- Cantarolar para buscar (humming) — precisa de serviço externo; fora até achar fonte legítima.
- Discord: "ouvindo agora" (Rich Presence via app/OAuth) — opções a estudar.
- Teste de EQ por dispositivo (perfis por fone Bluetooth) com o Marce.
- Perfil do artista: "Ver tudo" das seções (browse com params), playlists do artista, shows ao vivo.
- Capas animadas reais se aparecer fonte pública legítima.
- Letras: tradução, karaokê por palavra; editar sincronização.
- Fila: arrastar para reordenar com prévia; "tocar a seguir".
- Downloads: playlists inteiras com seleção na página Downloads; pasta por artista/álbum.
