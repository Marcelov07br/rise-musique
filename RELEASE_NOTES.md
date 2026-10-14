# Rise Musique 0.26.0

Capas animadas: quando o álbum tem a capa em vídeo do Apple Music, ela aparece em loop no lugar da capa.

- **Capas animadas** no player (cheio e mini) e no topo das páginas de álbum. O vídeo entra suavemente por cima da capa normal depois do primeiro quadro; se não houver, ou se algo falhar, fica a capa de sempre (com o movimento lento de antes).
- **Sem som e sem interferir na música**: é um vídeo mudo, separado do player de música, que pausa com o app em segundo plano e quando a música pausa. Um só vídeo decodificando por vez.
- **Leve na rede**: o resultado de cada álbum fica guardado (inclusive "não tem"), e os vídeos ficam num cache de até 120 MB.
- **Ajustes → Aparência → Capas animadas**: Sempre, Só Wi-Fi (padrão) ou Não. "Reduzir movimento" desliga.
- A capa que voa até o player continua igual: durante o voo aparece a capa estática.
- Fonte externa e não oficial: se ela sair do ar, o app segue funcionando normalmente, só sem as capas animadas.
