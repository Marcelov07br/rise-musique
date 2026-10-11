# Rise Musique 0.20.0

Artistas de verdade, player mais leve e áudio de alta resolução.

- **Novo perfil de artista**: foto do artista de ponta a ponta no topo (do canal oficial no YouTube Music), nome grande, Tocar, Aleatório, Rádio e Seguir. Logo abaixo: Top músicas, Álbuns, Singles & EPs, Vídeos, o que está na sua biblioteca, Artistas parecidos e Sobre (biografia). A foto se mexe devagar (desliga em economia de bateria ou sem animações), faz parallax ao rolar e estica quando você puxa para baixo.
- **Álbuns do YouTube Music**: tocar um álbum de um artista abre a lista de faixas completa; dá para ouvir em streaming ou "Baixar tudo" pela página Downloads. Álbuns da busca também abrem assim.
- **Fotos de artista**: os artistas na Biblioteca passam a usar a foto real, guardada no aparelho; sem internet, fica a capa como antes.
- **Player mais fluido**: abrir e fechar o player agora só move e recorta camadas, sem recalcular a tela a cada quadro. Arrastar, gesto de voltar e vibrações continuam iguais.
- **Capa que cresce do destaque**: tocar num cartão grande do Início abre o álbum com a capa crescendo até o topo.
- **DSD e alta resolução**: arquivos DSF e DFF (DSD64/128…) tocam convertidos para PCM até 176,4 kHz, com o selo de qualidade (ex.: "DSD64 · 2.8 MHz → PCM 16/176"). AIFF/AIFC passam a tocar com o decodificador FFmpeg embutido, e ALAC/FLAC também usam ele. APE e WavPack são convertidos para FLAC (sem perda) na importação. Nome, artista, álbum e capa vêm das tags ID3 dos arquivos DSD/AIFF. TAK ainda não é suportado.
