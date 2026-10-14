# Rise Musique — notas do projeto (leia isto primeiro)

## Estado atual: 0.28.0 (14/10/2026) — linha principal `main`
- Repositório do código: `Marcelov07br/rise-musique` (privado). Releases públicas: `Marcelov07br/rise-musique-releases`.
- Nome visível "Rise Musique". **Application ID `com.rise.musique.gpt` — NÃO mudar** (outro ID = outro app, sem atualizar por cima).
- A branch `gpt` é histórico antigo (0.7–0.9.1), não usar.
- Teste real é no celular do Marce (Samsung SM-S948B, Android 17). Emulador não roda aqui. Paparazzi = única verificação visual.
- Marce lê português. Rodadas: Marce aprova uma lista → implementar tudo → testes, snapshots (olhar!), build, bump, notas em PT, commit, push, publicar.

## 0.28.0 (vidro do Square + GPL-3.0) — o que mudou
- **Licença**: Marce aprovou relicenciar o app inteiro para **GPL-3.0** (`LICENSE` na raiz, `NOTICE.md` com créditos) para reaproveitar código do Square (github.com/Lelonio/Square, GPL-3.0). A regra "clean-room" de 0.27 deixou de valer. A GPL exige o código para quem recebe o APK: o repo de código continua **privado** (Marce decide se abre), então **toda release leva `rise-musique-<v>-source.tar.gz`** (`git archive` da tag `v<v>` do repo de código). Opções: abrir `Marcelov07br/rise-musique` ou continuar anexando o tarball.
- Dependências conferidas: tudo Apache-2.0/MIT/Unlicense/GPL-3.0 (youtubedl-android)/LGPL-GPL (FFmpeg) → compatível. Só de teste (não distribuídas): JUnit (EPL-1.0) e org.json (JSON License).
- **Vidro** (`ui/glass/`): `backdrop/**` = cópia da biblioteca Kyant0/backdrop (Apache-2.0) como o Square a embute; `SquareGlass.kt` = porte do `GlassEffect.kt` do Square (`squareLiquidGlass`: saturação 1,6, desfoque 2 dp, lente 19,2/28,8 dp limitada a 45 % do lado menor, aro .8 dp α .3 a 45°, `Shadow.Default`, filme `#4A4A4E→#23232A` (escuro) / `#FAFAFA` (claro) α .5, resolução reduzida conforme o desfoque). `LiquidGlass.kt` virou um adaptador (`liquidGlass(shape, dark)`); **Haze removido**. Fonte: `rememberLayerBackdrop()` no `RiseApp` + `glassSource` (= `layerBackdrop`) no conteúdo. Sem captura (pré-visualização) ou API < 31 → filme translúcido + aro; API 31–32 → sem lente (RuntimeShader é 33+).
- Correção nossa no vendorizado: `DrawBackdropNode.updateEffects()` ignora tamanho `Unspecified` (no `onAttach` a lente lia `size` e quebrava o nó: "LayoutNode should be attached"). `InverseLayerScope`: `blendMode/colorFilter` sem `override` (Compose 1.7; o Square usa BOM 2026.06).
- **Dock** (`NavigationDock`, agora `internal`): cápsula de vidro + filme branco .12/.45; pastilha da aba ativa = `drawBackdrop` com lente 10/14 dp + aberração cromática, aro .35, sombra .35, lavagem preta .5/.11, arrastável (`FoldingTabs`/`puckLayout`); ícones 26 dp, rótulo 10 sp; acento só no ícone/rótulo. Puck e abas são IRMÃOS do vidro da barra. Não portado: dobra por rolagem e círculo de busca separado.
- **Mini** (`PlayerSheet`, `MiniLook`): 56 dp (capa 40/9 dp, folga 12×8, controles 40 dp), pílula, título bodyMedium + artista 70 %, próxima + play/pausa sem fundo (`PlayButton(icon=.6)`), fio de progresso 2 dp, aperto 1.04 (só graphicsLayer, sem recompor), deslizar = próxima/anterior (28 % ou arremesso; usa o `throwX` da capa). Mantido do Rise: botão fechar. Fica 8 dp acima da linha da dock (margem da dock) + inset real da barra do sistema; `bottomSpace` usa `MiniLook.Height`.
- Créditos (Sobre): Square, Convx, Backdrop (Kyant0), Phosphor. Licenças em `assets/licenses/` (GPL-3.0, backdrop, phosphor).
- Testes: `Round28Test` (fallback, padrões do Square, altura do mini) + snapshots `Round28` (`chromeDark/Light` = dock + mini isolados — no `RiseApp` a dock só entra no 2º quadro e o Paparazzi grava o 1º, por isso Round27 nunca mostrou a dock). Paparazzi desenha o **fallback** (não roda RenderEffect/RuntimeShader). Cópias em `/workspace/rise-musique-shots/0.28.0/`.
- Não testado no aparelho: aparência real do desfoque/lente/aberração, custo por quadro da captura (o Square congela durante rolagem/transições; aqui não), gestos vs 3 botões, deslizar no mini vs arrastar o player.

## 0.27.0 (visual: vidro + ícones) — o que mudou
- **Referência de design em sala limpa (clean-room)**: o visual foi inspirado nas capturas do app Square (github.com/Lelonio/Square, GPL-3.0). Só foram olhados o README, as capturas do fastlane e a lista de dependências (para identificar o conjunto de ícones). **Nenhum código, shader, recurso ou asset do Square foi copiado, portado ou parafraseado**; tudo foi escrito do zero no estilo do Rise.
- Ícones: o Square usa **Phosphor Icons (MIT)** (`com.adamglin:phosphor-icon`). Aqui os SVGs vieram do upstream oficial `phosphor-icons/core` (assets/fill e assets/bold) e viraram `res/drawable/ph_*.xml`; licença em `assets/licenses/phosphor-LICENSE.txt`. `ui/PhIcons.kt` expõe Home/Library/Search (abas) e Play/Pause/Prev/Next/Shuffle/Repeat/RepeatOne/Close (player e mini). Fill no transporte e nas abas, Bold nos de traço. Obs.: antes desta versão o Rise NÃO tinha Phosphor (só material-icons-extended).
- Vidro (`LiquidGlass.kt`, `GlassLook`): continua sendo Haze (não AndroidLiquidGlass/backdrop; não havia essa dependência). Desfoque 26→34 dp, véu escuro `#3C3C42` α .48 (antes `#1C1C20` α .55), claro branco α .58, ruído .06, faixa de refração na borda (traço duplo 7 dp por dentro), aro contínuo 1,2 dp, sombra 18→10 dp α .16. O conteúdo agora é desenhado por cima dos efeitos. `GlassBox` com aro também embaixo. Pílula da aba ativa neutra (branca), acento só no ícone/rótulo.
- `PlayPauseMorph`: cantos arredondados (traço com junção redonda), mesma animação.
- Insets: a caixa de conteúdo usa `safeDrawing.only(Top + Horizontal)` + `imePadding()`; a dock tem `navigationBarsPadding()` e altura fixa (`GlassDock.Height` = 64 + 2×8 dp), usada também pelo PlayerSheet para pôr o mini acima dela. `bottomSpace()` = inset da barra de navegação + dock + mini + 16 dp, então as listas rolam por trás do vidro até o fim.
- Testes: snapshots `Round27` (dock+mini escuro/claro, só dock, controles do player); cópias em `/workspace/rise-musique-shots/0.27.0/`.
- Não testado no aparelho: aparência real do desfoque (Paparazzi não renderiza RenderEffect), gestos vs 3 botões (Paparazzi tem inset 0), teclado aberto na busca com a nova margem.

## 0.26.0 (capas animadas) — o que mudou
- Fonte: API de terceiros `https://artwork.m8tec.top` (raspa o Apple Music; sem MusicKit; pode cair). `GET /api/v1/artwork/search?artist=&album=`. Resposta real: 200 `{url, url_tall, artist, album, isCached}` com **HLS m3u8** mestre (quadrado 360–1080, H.264/HEVC, sem áudio); 404 `{"message":"No animated artwork found."}`. Sem MP4/WebP (o parser prefere MP4 se um dia vier).
- `MotionArtwork.kt`: `MotionArtParser`, `MotionArtKey` (normaliza artista/álbum: acentos, "(Deluxe…)", " - Single", feat.), `MotionHttp` (HttpURLConnection, 5 s/8 s), `MotionArtworkRepository` (Dispatchers.IO, Mutex, cache em memória + `filesDir/motion_art.json`, até 600 entradas; TTL achou 14 d, 404 3 d, erro 30 min; tudo vira null).
- `ui/MotionCover.kt`: `MotionArtPrefs` (pref `rise/motionArt`: 2 Sempre, 1 Só Wi-Fi padrão, 0 Não; Reduzir movimento desliga), `MotionArtworkViewModel` (StateFlow por álbum), `MotionArtPlayer` (UM ExoPlayer mudo, loop, sem foco de áudio/MediaSession, áudio desligado na seleção, ≤800 px; SimpleCache LRU 120 MB em `cacheDir/motion-art`; reivindicação por prioridade: cheio 3 > álbum 2 > mini 1; liberado 3 s sem dono; pausa em ON_STOP), `MotionCover` (TextureView center-crop por cima da capa; fade 450 ms após o 1º quadro; erro → estática; o Ken Burns de 0.18 (`kenBurns`, Motion2.kt) continua como fallback e desliga quando o vídeo está visível).
- Uso: capa do PlayerSheet (mesmo elemento do mini e do cheio; estática durante o `CoverFlight`, na letra, em prévias e com a música pausada); topo de `CollectionScreen` (álbum local) e `RemoteAlbumScreen` (ativo 700 ms depois de entrar, para não atrapalhar a transição de capa compartilhada; para ao recolher).
- `NowPlaying.album` (de `albumTitle`); sem álbum = sem capa animada.
- Testes: `Round26Test` (parsing com a resposta real, MP4 preferido, normalização, cache/persistência, TTL negativo, erros/5xx/JSON quebrado, regra do ajuste). Snapshot Aparência atualizado.
- Não testado no aparelho: vídeo HLS real (decodificação, loop sem emenda, fade), consumo de bateria/dados, troca de dono mini↔álbum, comportamento com a API fora.

## 0.25.0 (movimento imersivo) — o que mudou
- `ui/Immersive.kt`: `ReduceMotion` (pref `rise/reduceMotion` + escala de animação do sistema = 0; `rememberReduceMotion()`), `LikeButton` (partículas pré-calculadas em `LikeBurst`, sem alocação no desenho; háptico `tick`), `PlayPauseMorph` (Path reutilizado, `PlayMorph` pontos 2 quadriláteros), `RollingText` (odômetro por dígito), `Tilt`/`rememberTilt` (TYPE_GRAVITY a ~16 Hz, só com o app RESUMED, sem economia de bateria e sem Reduzir movimento; suavizado por mola) + `Modifier.glassTilt` (≤2,2°, só graphicsLayer) + `LocalTilt`; `CoverFlight` (origem da capa tocada em coordenadas da janela; `BoundsHolder`/`trackBounds`; `LocalCoverAnchor` = capa do topo do álbum para linhas numeradas).
- Player: coração ao lado do título (`Actions.likeNow` → comando `rise.like` do serviço, mesmo caminho da notificação); capa em voo desenhada fora do recorte do cartão; arrastar esquece a origem (vai para o mini); troca de faixa esquece a origem. Fechar por botão/voltar volta a capa para a lista. Arremesso da capa: vizinha (fila ±1) visível atrás, capa nova sobe com `depth`. Molas do player migradas para `Motion.spring` (limites 0..1 seguram a passada).
- Letra: linha atual escala 0.84→1.04 + sombra branca (brilho); rolagem com `animateScrollBy` + mola, centralizando a 38% da altura.
- `liquidGlass` agora em `drawWithCache` (pincéis criados só quando muda o tamanho); brilho especular transladado pela inclinação.
- Ken Burns e a cascata da fila (já existente) desligam com Reduzir movimento.
- Testes: `Round25Test` + snapshots `Round25` (player curtido/pausado, Aparência, micro detalhes).
- Não testado no aparelho: sensação do voo da capa (coordenadas da janela supõem tela edge-to-edge), parallax/consumo do sensor, partículas, odômetro a 120 Hz, brilho da letra. Baseline Profile só o arquivo à mão (curinga cobre o app); sem Macrobenchmark.

## 0.24.0 (polimento) — o que mudou
- Login do Google adiado (estudo em `docs/ESTUDO-LOGIN.md`, não versionado).
- `ErrorLog.kt`: log em anel `filesDir/log/app.log` (256 KB, corta a metade antiga em quebra de linha), `ErrorLog.i/w/e`; handler de crash também grava nele; `report()` = crash.txt + ApplicationExitInfo (API 30+) + log + `logcat -d --pid`; `export()` → `cacheDir/share/` + FileProvider + chooser. Botão "Exportar registro de erro" em Sobre (`AboutContent(exportLog=)`).
- `NetErrors.kt`: mensagens humanas de rede (sem conexão / demorou / última linha). Busca com erro → status `SearchErrorPrefix…` → `EmptyState` "Tentar de novo" (banner de status escondido nesse caso). `DownloadWorker` usa `NetErrors.friendly` e loga.
- Desfazer: `libUndo` (snapshot de `Library.data` → `Library.restore`) em removeFromPlaylist, deletePlaylist, toggleAlbum (remoção), deleteSpotifyPlaylist; buscas recentes (remover/limpar) com Desfazer.
- `Motion.spring(stiffness, damping)`: mola única, ζ padrão 0.8 (`Bounce`), travada em ≥ 0.6 (`PopBounce`, só toques em graphicsLayer). `Motion.place` para animateItem. Todas as molas soltas da UI passaram a usar isso.
- `Haptics` agora usa `View.performHapticFeedback` (CLOCK_TICK / CONTEXT_CLICK / CONFIRM no API 30+) — mais sutil que LongPress; `confirm()` ao soltar o player.
- Fila: rolagem automática na borda (`QueueMotion.edgeSpeed`, borda 56 dp, até 22 px/quadro; `travel` absorve a rolagem). A playlist (`Modifier.reorderable`) ainda NÃO tem.
- `ArtPrefetch.kt`: pré-carrega no Coil as próximas capas da fila e da busca. `poll()` mantém a mesma instância de `playbackQueue` quando nada mudou.
- `SkeletonRows` (SearchParts) no lugar de spinners em Artista, Álbum remoto, Estação e Spotify. `Tokens.kt`: `Space`/`TypeScale` (aplicados no que foi mexido; resto migra aos poucos).
- Vidro: tint 0.42→0.55 (escuro) e 0.5→0.62 (claro). Downloads: progresso animado, erro em cor de erro, "Tentar todos".
- Baseline Profile: já existia `src/main/baseline-prof.txt` (escrito à mão); acrescentado Haze. Sem Macrobenchmark (sem aparelho/emulador no box).
- Testes: `Round24Test` (borda, erros de rede, anel do log, prefetch, mola travada) + snapshots `Round24`.
- Não testado no aparelho: sensação da rolagem na borda, hápticos no Samsung, logcat/ExitInfo reais no arquivo exportado, 120 Hz/partida a frio (sem medição).

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
6. (GPL, desde 0.28) `git tag v<v> && git push origin v<v>` e `git archive --prefix=rise-musique-<v>/ -o delivery/v<v>/rise-musique-<v>-source.tar.gz v<v>`.
7. `gh release create v<v> --repo Marcelov07br/rise-musique-releases --title "Rise Musique <v>" -F delivery/v<v>/RELEASE_NOTES.md --latest delivery/v<v>/RiseMusique-<v>.apk delivery/v<v>/update.json delivery/v<v>/rise-musique-<v>-source.tar.gz`
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
- **Biblioteca unificada (0.23)** — fonte única para curtidas, playlists, artistas seguidos e álbuns salvos:
  - `LibraryModel.kt` (puro, testado na JVM): `LibraryData` { tracks (chave → `LibTrack`), likes, playlists (`LibPlaylist`: id, nome, ordem, `cover` vazio = mosaico automático), artists, albums }. Toda faixa tem `videoId` + `source` (local/youtube/spotify) + `externalIds` (youtube, spotify, ytmAlbum…) + `addedAt/updatedAt` (para import do Google/Spotify em 0.24 e backup no Drive em 0.25).
  - Chaves de faixa: caminho/`content://` para arquivos, `rise-stream://<videoId>` para streams, `rise-spotify://track/<id>` para Spotify não resolvido. Ids antigos `stream:<url>` são normalizados (`LibraryMigrations.normalizeKey`, `Library.keyFor`).
  - Armazenado em `filesDir/library.json` com `schema` (1 = antigo `{fav, pl}` sem campo; 2 = atual). `LibraryMigrations.from1` junta `library.json` + `history.json` (metadados) + prefs `follow/artists` + `spotify_playlists.json`; o original fica em `library.v1.json`. Arquivos antigos ficam no lugar mas não são mais lidos. Schema maior que o do app é recusado.
  - Histórico de reprodução continua em `stats.json` (`PlayStats`); não foi duplicado na biblioteca. O arquivo exportado inclui o histórico.
  - `Library.kt` = repositório (cache em memória, um processo só; `listener` → `MainViewModel.withLibrary` espelha no `UiState.library` + `favorites/playlists/followed/savedSpotifyPlaylists`). `FollowStore.all/toggle` e `SpotifyStore.load/save/remove` delegam para ela. Downloads gravam o `videoId` (`DownloadWorker` → `Library.rememberFile`).
  - Mesclagem (`LibraryMerge`): faixas deduplicadas por videoId (depois chave); a de fora é remapeada para a chave existente (arquivo + stream do mesmo vídeo = uma faixa); playlists de mesmo nome (ou mesmo id do Spotify) unem faixas; metadados: brancos preenchidos, `updatedAt` maior ganha.
  - Arquivo exportado (`LibraryFile`): `{format: "rise-musique-library", version: 1, exportedAt, library: <schema 2>, history: [plays]}`; SAF (`CreateDocument`/`OpenDocument`). O backup antigo (`Library.export/import`, v3) segue existindo e agora leva a biblioteca no schema 2; também aceita v1/v2.
  - UI: `ui/LibraryScreens.kt` — abas (`LibraryTabs`, `PillSelector(compact)`), `libraryItems` (chave → linha: arquivo se houver, senão a faixa online/Spotify), `PlaylistArt` (mosaico 2×2 / capa própria), `AddTracksSheet`, `NewPlaylistDialog`, `PlaylistReorder` + `Modifier.reorderable` (mesmo gesto da fila: segurar 1 s, mola ζ0.6 só em graphicsLayer, um `moveTrack` ao soltar). `CollectionScreen` de playlist: +, Editar (renomear/capa/apagar), arrastar, deslizar ← remove com Desfazer. Salvar álbum em `RemoteAlbum` e no álbum local (`local:<nome>`). `StreamActionSheet(r, a, s)` tem Curtir e Adicionar à playlist.
  - `playList(lista, faixa)` toca listas mistas (arquivo/stream/Spotify; `uriOf` aceita `rise-stream://` e `rise-spotify://`).
  - A classe de snapshot `Library` virou `LibrarySnapshots` (mesmo FQN do objeto `Library` escondia o objeto nos testes).
- `BackupMerge.kt`, `PlaylistEdits.kt`, `AutoLibrary.kt` (Android Auto), `LocalMedia.kt` (importar arquivos/pasta).
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
- `SpotifyImport.kt` / `SpotifyStore.kt` (0.21, contribuição do colega) — lê playlist/álbum público pela página embed `open.spotify.com/embed/<tipo>/<id>` (`__NEXT_DATA__`, sem credenciais/API do Spotify); `spotify.link` segue redirect. Faixas viram URIs `rise-spotify://<id>` resolvidas no `StreamDataSource` via busca no YTM (`resolveToSearchResult`). Salvas em `filesDir/spotify_playlists.json`. Intent VIEW para `open.spotify.com/playlist|album`. Frágil: depende do HTML do embed.
- Qualidade de streaming (0.21): `Streams.streamQuality` (pref `stream_quality`: Econômica/Normal/Alta) → formato do yt-dlp em `Engine.audioStream`. `PlaybackService` com `DefaultLoadControl` (buffer 30–60 s, início 600 ms).
### UI (`ui/`)
- `LiquidGlass.kt` (0.20.1) — `LocalHaze` (HazeState criado no `RiseApp`), `glassSource` no conteúdo; a barra e o mini são desenhados no nível do `RiseApp` (slot `LocalDock`) — dentro da árvore do `RiseContent` o Haze não desenhava nada (barra transparente, 0.20.2), `liquidGlass(shape, dark)`: Haze 1.3.1 (blur 26dp, grão, tint; fallback tingido < API 31) + borda especular/sombra interna. Pílula das abas: duas molas (frente firme, trás mole) desenhadas em `drawBehind`; troca de aba `Motion.tabSwitch`.
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
- `StationScreen.kt` (0.21) — tocar numa estação abre a lista (`Route.Station`, `loadStation`/`StationPage`). `SpotifyScreen.kt` (`Route.Spotify`) + `SpotifyImportDialog.kt` (AlertDialog padrão e verde do Spotify — destoa do vidro; candidato a refazer no estilo da casa).
- `SearchParts.kt` — filtros (chip "Filtros" → folha), voz, esqueleto, resultados de coleção.
- `SettingsSheet.kt` (+ Sobre em `About.kt`), `EqSheet.kt`, `StatsSheet.kt`, `RecapSheet.kt`, `VideoPlayer.kt`, `TrackActions.kt`, `UpdateDialog.kt`, `Visuals.kt` (GlassBox, paleta, arte viva).
- `ui/PlaybackQueueSheet.kt` (reescrito em 0.22) — "Tocando agora" + "A seguir" com capas; sem setas. Segurar ~1 s (`QueueMotion.LiftMs`, detector próprio com `withTimeoutOrNull`) levanta a linha (mola ζ0.6 em escala/sombra só no graphicsLayer, háptico) e arrasta; a ordem fica local (`dragList`) e o player recebe um único `moveQueue` ao soltar; as outras linhas abrem espaço com `animateItem` (placement spring ζ0.82). `SwipeToDismissBox`: → `playNextFromQueue`, ← `removeQueueUndo` (Desfazer via `withUndo`; a folha é outra janela, por isso tem o próprio `UndoBar(bottom=)`). Chaves `uri#n` (`queueEntries`). "Salvar como playlist" (`saveQueueAsPlaylist`: só faixas da biblioteca).
- `ForYou` (em `Stations.kt`, 0.22) — "Feito pra você" no Início: Parecidos com <artista curtido>, Na linha de <álbum curtido> (rádio, reaproveita `loadStation`/`StationScreen`), Curtidas recentes e Redescobrir (`localOnly`: só faixas locais, sem rede; Redescobrir = sem tocar há 30+ dias via `PlayStats`). `refreshForYou(true)` no pull-to-refresh (`PullToRefreshBox` neutro em `LibraryHome.RefreshWrap`) troca a rodada → ids novos `fy:*:<rodada>` e limpa `stationPages` desses ids.
- `SmartQueue` (em `PlaybackService.kt`, 0.22) — quando não há próxima (`!hasNextMediaItem`), busca o rádio da última faixa (stream: o próprio; local: busca no YTM) e acrescenta até 15 streams não repetidos. Pref `rise/smartQueue` (padrão ligado), Ajustes → Reprodução.
- Temporizador (0.22): `sleepAt = -1` (`END_OF_TRACK`) usa `pauseAtEndOfMediaItems` + fade de 12 s; temporizado faz fade de 30 s (curva quadrática `fadeVolume`). Crossfade desliga perto do fim do temporizador.
- Favoritar na notificação/tela de bloqueio (0.22): `CommandButton` custom layout (`rise.like`, ícones `ic_w_heart*`); app ↔ serviço sincronizam por `rise.likeSync` e `favStamp` nas session extras.
- Retomar (0.22): `restorePlayback` agora restaura streams (`stream:` → `Streams.uriFor`); itens do Spotify ficam de fora (precisam do registro em memória da importação).
- `ui/HistoryScreen.kt` (`Route.History`, 0.22) — reproduções do `PlayStats` por dia (Hoje/Ontem/data), repetições seguidas viram uma linha, "Tocar de novo" (`playAgain`: local ou stream; capa de stream pela miniatura do YouTube). Entrada: cartão "Histórico" no Início.
- Ajustes (0.22): `SettingsContent` com busca + seções (Reprodução, Áudio, Downloads, Aparência, Integrações, Sobre) em subpáginas (AnimatedContent com mola). Cada opção é um `SettingEntry` (seção, título, palavras-chave). Biblioteca/Backup ficaram em Integrações; Estatísticas/Atualizações/Sobre em Sobre.
- Menus (0.22): "Tocar em seguida"/"Adicionar à fila" em todas as listas — locais pelo `TrackActionSheet`, online pelo `StreamActionSheet` (busca, Top músicas do artista, álbum remoto, estação, Spotify via `queueSpotify`); cartões de Reproduções recentes com toque longo.
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
- 0.23: migração com dados reais do aparelho (testada só com arquivos sintéticos em Robolectric), arrastar/deslizar na playlist (sem rolagem automática na borda), capa própria via SAF (permissão persistente), tocar playlists mistas com faixas do Spotify não resolvidas, import/export pelo seletor de arquivos real. Linhas pequenas aparecem sem capa no Paparazzi (limitação já existente). Reordenar playlists na aba é por menu (Mover para cima/baixo), não por arraste.
- 0.22: sensação do arraste/levantar e dos deslizes na fila (Paparazzi só mostra estático; sem rolagem automática ao arrastar até a borda), pull-to-refresh, fila inteligente com rede real, botão de coração na notificação e na tela de bloqueio do Samsung (Android 17 pode mostrar ou não custom actions), temporizador "fim da faixa" com crossfade/automix ligados, retomar com streams (URL re-resolvida ao tocar).
- 0.21 (código do colega, sem testes novos nem snapshots): importação do Spotify com links reais, página de estação, qualidade Econômica/Normal, seletor de qualidade de download em Ajustes (opções 128k/192k/320k podem não bater com as qualidades por modo). Revisão: removido o fallback dele que assinava release com chave debug se o keystore faltasse.
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
- Capas animadas: fonte própria/estável (hoje é API de terceiros que raspa o Apple Music).
- Letras: tradução, karaokê por palavra; editar sincronização.
- Downloads: playlists inteiras com seleção na página Downloads; pasta por artista/álbum.
