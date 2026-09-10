# Contrato — `SettingsRepository`

A porta de preferências. Existe desde o esqueleto inicial; esta feature implementa-a.

```
interface SettingsRepository {
    val settings: Flow<SkySettings>
    suspend fun update(transform: (SkySettings) -> SkySettings)
}
```

## Pós-condições

- `settings` emite **sempre** valores válidos: `coerced()` é aplicado a **toda** leitura, não numa
  migração pontual (AD-022);
- sem nada guardado, emite os valores de origem — não um estado vazio nem um erro;
- armazenamento ilegível ou corrompido emite os valores de origem, **sem lançar** (FR-008);
- `update` é atómico: uma escrita não deixa metade dos campos gravados;
- `update` aplica `coerced()` **antes** de gravar, para que um chamador distraído não consiga
  persistir um valor fora dos limites.

## Invariantes verificadas por teste

| # | Invariante | Requisito |
|---|---|---|
| 1 | Sem nada guardado, emite os valores de origem | FR-008 |
| 2 | Um valor guardado fora dos limites é lido como o válido mais próximo | FR-008 |
| 3 | Um valor **dentro** dos limites é lido tal e qual, sem alteração | FR-007 |
| 4 | Armazenamento ilegível emite os valores de origem em vez de lançar | FR-008 |
| 5 | Uma escrita sobrevive a fechar e reabrir | FR-007 |
| 6 | `update` grava valores já dentro dos limites, mesmo com um `transform` que os viole | FR-009 |
| 7 | Repor devolve **todos** os campos desta feature aos valores de origem, e não toca nos outros | FR-005 |

A invariante 2 é a que dispensa versionar o esquema: se um limite mudar numa versão futura, o valor
antigo é corrigido a cada leitura, para sempre. A 3 é o par dela — sem ela, um `coerced()` demasiado
zeloso poderia "corrigir" escolhas legítimas e ninguém dava por isso.

A invariante 7 é o que impede a reposição de apagar a tabela de rotas por engano: `SettingsRepository`
e `RouteTableRepository` são portas distintas de propósito (AD-017).
