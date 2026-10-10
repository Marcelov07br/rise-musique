# Atualizações do Rise Musique GPT

O código fica na branch `gpt` de `Marcelov07br/rise-musique` (privado).
Somente APKs, `update.json` e notas de versão ficam nas releases públicas de
`Marcelov07br/rise-musique-releases`. Não colocar tokens no aplicativo.

## Identidade e assinatura

Application ID: `com.rise.musique.gpt`. A versão do Grok usa outro ID e pode continuar instalada.
Todas as versões GPT devem ser assinadas pela mesma chave estável.
A pasta `signing/` contém a chave e as credenciais locais; ambos estão ignorados pelo Git.
Guardar uma cópia privada dos dois arquivos, fora deste computador. Não regenerar a chave.

## Entregar uma versão

1. Aumentar **versionCode** e **versionName** em `app/build.gradle.kts`.
2. Atualizar `RELEASE_NOTES.md`.
3. Compilar e verificar: `powershell -File build-local.ps1 -Test` (ambiente Windows deste projeto).
   Em outros ambientes: definir JDK 17, SDK 35 e variáveis `RISE_KEYSTORE`,
   `RISE_STORE_PASSWORD`, `RISE_KEY_ALIAS`, `RISE_KEY_PASSWORD`; executar
   `./gradlew :app:assembleRelease :app:lintRelease :app:testDebugUnitTest --tests com.rise.musique.UpdateReleaseTest`.
4. Executar `python scripts/prepare-release.py`.
5. Publicar uma release estável `v<versionName>` no repositório público, anexando o APK e
   `update.json` de `delivery/v<versionName>/`. Usar as notas de versão geradas.
   Não substituir APKs de versões já publicadas; publicar uma versão com código maior.

O app consulta `/releases/latest` ao abrir, com intervalo mínimo de cinco minutos entre
consultas automáticas enquanto o processo está vivo. O botão manual ignora esse intervalo.
A consulta só informa uma atualização depois que a release e seus dois arquivos foram publicados.
Publicar um commit sozinho não cria uma atualização instalável.

O download valida tamanho, SHA-256, ID do pacote, código de versão e certificado do APK.
O usuário confirma a instalação no Android e, na primeira vez, autoriza instalar a partir do Rise.
Não é necessário desinstalar entre versões GPT.

## Validação no celular

Para testes locais em emulador x86_64, compilar `:app:assembleDebug` e
`:app:assembleDebugAndroidTest` com `-Prise.emulator=true`. Essa propriedade é
somente para a variante de teste; a release pública usa o build normal ARM64.
Os testes instrumentados `LocalPlaybackTest` verificam leitura de metadados,
importação repetida, preservação do original e restauração pausada do serviço.

- Importar uma pasta, verificar duração/capas e remover um importado preservando o original.
- Encerrar e reabrir: verificar fila, faixa e posição restauradas sem começar a tocar.
- Renomear e reorganizar playlist; conferir a ordem de reprodução.
- Alternar tema claro/escuro/sistema e verificar a escolha após reabrir.

- Instalar o APK inicial e manter a versão do Grok ao lado.
- Baixar e tocar uma música; fechar/reabrir a tela e conferir controles.
- Testar favoritos e playlist após novos downloads, inclusive acima de 50 itens.
- Conferir o temporizador com tela fechada; cancelar e verificar volume.
- Publicar uma versão posterior, verificar download e confirmar atualização sem perder biblioteca.
- Negar permissão de instalação e verificar que é possível tentar novamente.
- Testar arquivo inválido/incompleto e interrupção da conexão.

O backup é de metadados e configurações. Não contém músicas nem concede permissões de pasta
em outra instalação. A importação preserva a pasta autorizada nesta instalação.
