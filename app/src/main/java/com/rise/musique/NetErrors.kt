package com.rise.musique

/** Prefixo do status de busca com erro (a tela mostra estado de erro com "Tentar de novo"). */
const val SearchErrorPrefix = "Erro na busca: "

/** Mensagens curtas e humanas para falhas de rede (0.24). Puro, testado na JVM. */
object NetErrors {
  fun isOffline(t: Throwable): Boolean = generateSequence(t) { it.cause }.any {
    it is java.net.UnknownHostException || it is java.net.ConnectException || it is java.net.NoRouteToHostException }
  fun friendly(t: Throwable): String = when {
    isOffline(t) -> "Sem conexão com a internet. Confira o Wi-Fi ou os dados móveis."
    generateSequence(t) { it.cause }.any { it is java.net.SocketTimeoutException || it is java.io.InterruptedIOException } -> "A conexão demorou demais. Tente de novo."
    else -> t.message?.lineSequence()?.lastOrNull { it.isNotBlank() }?.take(100)?.ifBlank { null } ?: "Algo deu errado. Tente de novo."
  }
}
