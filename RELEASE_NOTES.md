# Rise Musique 0.14.0

## Novidades (0.14.0)
- Três abas: Início (recentes, álbuns, playlists, artistas e retrospectiva), Buscar (busca unificada e downloads) e Biblioteca (tudo o que você tem). Cada aba guarda o próprio caminho e a posição da lista; tocar de novo na aba volta ao começo.
- Tela de artista: foto, suas faixas, álbuns e as mais tocadas do YouTube com prévia e download. Abre com a mesma transição dos álbuns, fecha arrastando para baixo ou com o gesto de voltar.
- Busca unificada: um só campo procura na sua biblioteca na hora (faixas, álbuns, artistas e playlists) e no YouTube logo depois que você para de digitar. Histórico em chips com botão de remover, links colados são reconhecidos e o teclado some ao rolar.
- Menu da faixa (toque e segure em qualquer lista): Tocar a seguir, Adicionar à fila, Adicionar à playlist, Favoritar, Rádio, Ir para artista, Ir para álbum, Compartilhar e Apagar.
- Desfazer: apagar, tirar da playlist e limpar a fila mostram “Desfazer” por alguns segundos; o arquivo só é apagado depois.
- Telas vazias explicam o que fazer (biblioteca, favoritos, playlists, fila, busca, estatísticas e artista).
- Animações um pouco mais calmas, sem quique.

## Correção (0.13.1)
- Corrigido o fechamento do app ao abrir na versão 0.13.0 (erro "SeekableTransitionState has been used in different Transitions"). A transição de abrir e fechar álbuns continua igual.

## Movimento (0.13.0)
- Abrir um álbum ou playlist agora é uma transição contínua: a capa sai do cartão e cresce até o topo da tela, os cantos se ajustam, o fundo vivo aparece e o título sobe suavemente. Fechar faz o caminho inverso até o lugar de origem.
- Tudo pode ser interrompido: voltar no meio da animação inverte a partir de onde estava. O gesto de voltar do Android mostra a prévia seguindo o dedo.
- Arraste para baixo para fechar o player e as telas de álbum: a tela acompanha o dedo e, ao soltar, termina ou volta de acordo com a velocidade.
- Toques com peso: cartões e faixas afundam de leve e escurecem ao pressionar.
- Fileiras horizontais de álbuns e da nova seção "Suas playlists" param alinhadas à margem.
- Movimento unificado, sem quiques exagerados, e desempenho melhor em telas de 120 Hz (menos trabalho por quadro e perfil de inicialização pré-compilado).

## Acabamento (0.12.1)
- Telas de álbum e playlist: o fundo vivo agora ocupa a tela inteira, por trás das barras do sistema, e se dissolve suavemente na cor de fundo, sem cortes nem blocos desalinhados.
- Cabeçalho do álbum que recolhe ao rolar: o título aparece na barra de cima, com botões de voltar e tocar sempre à mão.
- Corte de trecho redesenhado: o botão Cortar fica na mesma linha de Música/Vídeo e abre uma folha com forma de onda (música) ou quadros do vídeo, duas alças para escolher o trecho, campos de início/fim, botão para ouvir o trecho e confirmar ou limpar. O trecho escolhido aparece no próprio botão.
- Botões com área de toque maior (48 dp), espaçamentos e margens padronizados, e a lista nunca fica escondida atrás do mini player.
- Estados vazios mais claros e informações repetidas removidas das faixas.

## Novidades (0.12.0)
- Automix: transições no ritmo entre músicas locais. O app analisa o andamento (BPM) e os tempos fortes de cada faixa (fica guardado), entra com a próxima no tempo forte, dura compassos inteiros e ajusta o andamento por alguns segundos. Se a análise não for confiável, usa o crossfade normal. Ative em Ajustes › Transições.
- Last.fm: scrobbles e "ouvindo agora" (faixas com mais de 30 s, após metade da duração ou 4 minutos). Entre com sua conta em Ajustes; a senha não fica salva. Scrobbles feitos sem internet são enviados depois.
- Rádio da música: a partir de uma busca ou de uma faixa da biblioteca, encontra músicas parecidas, com prévia e download de todas ou das escolhidas.
- Retrospectiva: cartão animado do mês ou do ano (minutos, artista e faixas mais tocadas), com imagem 9:16 para compartilhar nos stories.
- Player em paisagem e tablet: capa de um lado, letra ou fila do outro.
- Discord: não há integração direta (o Discord não oferece presença oficial para apps de celular). Para mostrar o que você ouve no perfil do Discord, conecte o Last.fm em Discord › Configurações › Conexões.

## Novo visual (0.11.0)
- Fundo vivo: degradê em malha com as cores da capa, se movendo devagar atrás do player e das telas de álbum/playlist.
- Vidro líquido: mini player, barra inferior e botões redondos translúcidos, com borda de luz; desfoque real no Android 12+ e versão mais leve nos anteriores.
- Player em tela cheia no estilo Apple Music: capa grande, título e artista em destaque, barra de progresso fina com tempo decorrido e restante, e a qualidade do áudio (codec, kbps, kHz).
- Letras animadas: a linha atual cresce e fica nítida; as outras ficam menores e desfocadas, com rolagem suave.
- Tela de álbum e playlist: capa em destaque, título, resumo (faixas e duração), botões Tocar, Aleatório e Adicionar à fila, lista numerada.
- Vibração leve ao tocar/pausar, pular faixa, arrastar e alternar opções.
- O mini player volta a ficar acima da barra de navegação do app.

## Estatísticas, Android Auto e transições (0.10.0)
- Estatísticas: faixas e artistas mais ouvidos, tempo total e resumo mensal. Uma reprodução conta após 30 s ou metade da faixa. Entram no backup.
- Android Auto e Bluetooth: navegue por Favoritos, Playlists, Biblioteca e Recentes no carro (só arquivos locais), com capas e busca por voz.
- Crossfade configurável (desligado ou 2 a 12 s) entre faixas locais; faixas seguidas do mesmo álbum tocam sem pausa. Prévias não entram.

## Correções (0.9.1)
- Prévias de busca voltam a tocar e o mini player ficou compacto.

APK ARM64, Android 8 ou superior. Atualiza por cima da versão instalada, preservando a biblioteca.
