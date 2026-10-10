# Rise Musique GPT

- Trabalhar na branch `gpt`; preservar a versão do Grok nas outras branches.
- Application ID `com.rise.musique.gpt`; manter assinatura estável para atualizar por cima.
- Para cada versão solicitada, compilar APK release assinado, executar os testes relevantes e Android Lint.
- Publicar somente APK e `update.json` em releases de `Marcelov07br/rise-musique-releases`.
- Incrementar versionCode/versionName em cada nova entrega. Nunca substituir o APK de uma release publicada.
- `signing/` é estritamente local e ignorado pelo Git. Nunca enviar chave, senhas ou tokens ao Git ou à release.
- Fluxo de compilação/publicação: `UPDATES.md`. Registrar mudanças em `RELEASE_NOTES.md`.
- Relatar validações reais e limitações; compilação não substitui teste no celular.
- Preservar o visual sóbrio, pílulas e molas; limitar valores de layout animados para evitar dimensões negativas.
