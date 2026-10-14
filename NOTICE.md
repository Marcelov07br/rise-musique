# Rise Musique — licença e créditos

Desde a versão 0.28.0, o Rise Musique inteiro é distribuído sob a **GNU General Public License v3.0** (arquivo [`LICENSE`](LICENSE)).
Copyright (C) Marce (Marcelov07br) e contribuidores.

## Código de terceiros portado

- **Square** — https://github.com/Lelonio/Square — GPL-3.0.
  O vidro líquido, a dock/barra de abas e o mini player do Rise 0.28.0 foram portados do Square:
  - `app/src/main/java/com/rise/musique/ui/glass/SquareGlass.kt` ← `ui/glass/GlassEffect.kt` (`Modifier.liquidGlass`, `GlassEffectConfig`, saturação, resolução, lente, fallback translúcido) e `ui/player/GlassSurface.kt` (aro da barra).
  - `ui/glass/backdrop/**` ← `ui/glass/backdrop/**` do Square (cópia embutida da biblioteca **Kyant0/backdrop** v2.0.0, Apache-2.0, https://github.com/Kyant0/backdrop; licença em `ui/glass/LICENSE-backdrop.txt`). Dois arquivos (`BackdropFreeze.kt`, `BackdropStaticRegion.kt`) são do Square/Convx (GPL-3.0).
  - Dock (`NavigationDock` em `ui/RiseApp.kt`) ← `ui/glass/SquareBottomBar.kt` (`FoldingTabs`, `puckLayout`) e o `puckWash`/`GlassFilm` de `SquareApp.kt`/`ui/player/PlayerScreen.kt`.
  - Mini player (`PlayerSheet.kt`, `MiniLook`) ← `ui/player/FloatingMiniPlayer.kt` do Square, que por sua vez é adaptado do **Convx** (https://github.com/cosmictaserdev-creator/Convx, GPL-3.0).
  Cada arquivo copiado mantém o cabeçalho original e traz no topo uma nota do que foi modificado.
- **Phosphor Icons** (MIT) — `res/drawable/ph_*.xml`; licença em `app/src/main/assets/licenses/phosphor-LICENSE.txt`.
- Extensão FFmpeg do AndroidX Media3 (Apache-2.0) em `app/src/main/java/androidx/media3/decoder/ffmpeg/`.

## Código-fonte

A GPL-3.0 exige que quem recebe o APK tenha acesso ao código-fonte correspondente. Cada release em
https://github.com/Marcelov07br/rise-musique-releases traz o tarball do código da tag exata (`rise-musique-<versão>-source.tar.gz`).
