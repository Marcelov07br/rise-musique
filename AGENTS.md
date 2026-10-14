# Rise Musique

- Trabalhar na branch `main` (a branch `gpt` foi incorporada e fica só como histórico).
- Application ID `com.rise.musique.gpt` (identificador interno, não visível; não mudar, senão o app instalado não atualiza por cima). Manter assinatura estável.
- Para cada versão solicitada, compilar APK release assinado, executar os testes relevantes e Android Lint.
- Publicar APK, `update.json` e (GPL-3.0, desde 0.28.0) o tarball do código da tag em releases de `Marcelov07br/rise-musique-releases`. Nunca segredos.
- Incrementar versionCode/versionName em cada nova entrega. Nunca substituir o APK de uma release publicada.
- `signing/` é estritamente local e ignorado pelo Git. Nunca enviar chave, senhas ou tokens ao Git ou à release.
- Fluxo de compilação/publicação: `UPDATES.md`. Registrar mudanças em `RELEASE_NOTES.md`.
- Relatar validações reais e limitações; compilação não substitui teste no celular.
- Preservar o visual sóbrio, pílulas e molas; limitar valores de layout animados para evitar dimensões negativas.
