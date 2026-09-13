# Contrato: `SkyWidgetRepository`

```kotlin
interface SkyWidgetRepository {
    val snapshot: Flow<SkyWidgetSnapshot?>
    suspend fun save(snapshot: SkyWidgetSnapshot)
}
```

Porto de domínio (AD-023). Substitui o estado do Glance como sítio do último resultado, porque esse é
por instância de widget e este valor é um só para todas — e para as notificações, que não são widget.

## Invariantes

1. **`null` significa "nunca correu"**, e é distinto de qualquer snapshot gravado. É a fronteira entre
   a FR-007 (nunca uma caixa vazia) e a mentira de mostrar um céu vazio que nunca foi observado.
2. **`save` substitui sempre**; não há histórico. Uma linha, como diz a AD-023.
3. **Ler nunca lança.** Armazenamento ilegível emite `null`, pelo mesmo padrão do
   `SettingsRepositoryImpl`: `retryWhen` limitado e depois valores de recurso.
4. **Escrever nunca lança.** Uma falha de I/O perde o snapshot novo e mantém o antigo — nunca derruba o
   worker. Foi um defeito real encontrado na revisão da 004 e não se repete.
5. **Quem falha não escreve.** O worker só chama `save` quando tem um facto novo. É isto, e não uma
   regra a lembrar, que garante a FR-014 — os dados anteriores sobrevivem a um ciclo falhado porque
   ninguém lhes toca.
6. **Sobrevive à morte do processo** (FR-010), por ser DataStore em disco.

## Porque não é o mesmo DataStore das definições

Um guarda escolhas do utilizador, que ele espera que durem para sempre; o outro guarda um cache
descartável, reescrito a cada 30 minutos. Misturá-los faria o worker escrever, de 30 em 30 minutos, no
ficheiro onde vivem as preferências — e uma corrupção desse ficheiro passaria a poder custar as escolhas
do utilizador, em vez de custar um snapshot que se recalcula sozinho.
