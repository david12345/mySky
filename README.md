# mySky

App Android nativa que mostra, em tempo quase real, os aviões que estão a passar sobre a tua
localização — com widget de ecrã inicial e notificações opcionais quando uma aeronave passa mesmo
por cima.

> **Estado:** esqueleto inicial. A estrutura, o build e a lógica de deteção estão feitos; os ecrãs,
> o widget e as notificações estão por implementar.

## O que faz

- **Céu agora** — lista dos aviões atualmente visíveis a partir da tua posição, ordenados por
  altura no céu: indicativo, altitude, velocidade, distância e direção.
- **Detalhe da aeronave** — informação adicional e trajeto recente.
- **Widget** — o avião mais relevante no teu céu, no ecrã inicial, com toque para atualizar.
- **Notificações** (opcionais) — aviso quando um avião entra na zona "por cima" de ti.
- **Definições** — unidades, frequência de atualização, raio de deteção, notificações e widget.

### Como é decidido que um avião está "no teu céu"

1. Obtém-se a tua posição (lat/lon).
2. Pedem-se à API as aeronaves numa caixa envolvente à volta dessa posição.
3. Para cada aeronave calcula-se a distância horizontal (Haversine), o azimute e o ângulo de
   elevação acima do horizonte.
4. Conta como "por cima" quando a distância horizontal está dentro do raio configurado **e** a
   elevação ultrapassa o limiar configurado (por omissão 25°).

O passo 4 é o que distingue "está perto no mapa" de "está mesmo visível por cima de ti": um avião
a 30 km e a 3 000 m de altitude aparece a menos de 6° acima do horizonte — na prática, escondido
pelos prédios.

## Stack

Kotlin · Jetpack Compose (Material 3) · Jetpack Glance · Hilt · Retrofit + Kotlinx Serialization ·
Room · DataStore · WorkManager · Fused Location Provider · JUnit + MockK + Turbine

Arquitetura Clean + MVVM, em três camadas (`data` / `domain` / `presentation`).

## Fonte de dados

[OpenSky Network REST API](https://openskynetwork.github.io/opensky-api/rest.html), endpoint
`GET /states/all`. O uso anónimo é gratuito e não precisa de chave, mas tem limites baixos; uma
conta gratuita aumenta-os.

A app não está acoplada à OpenSky: qualquer fonte alternativa (ADS-B Exchange, airplanes.live, um
dataset local) entra implementando a interface `FlightDataSource` e trocando um binding em
`di/DataSourceModule`.

## Requisitos

- JDK 17
- Android SDK com a plataforma **API 36** e build-tools 35+
- Android Studio (opcional — o build por linha de comandos é suficiente)
- Dispositivo ou emulador com **Android 8.0 (API 26)** ou superior

## Setup

```bash
git clone https://github.com/david12345/mySky.git
cd mySky
```

Indica onde está o SDK, criando `local.properties` na raiz (o ficheiro não vai para o repositório):

```properties
sdk.dir=/caminho/para/o/Android/Sdk
```

> Se abrires o projeto no Android Studio, este ficheiro é criado automaticamente.

Compilar e correr os testes:

```bash
./gradlew :app:testDebugUnitTest   # testes unitários
./gradlew :app:assembleDebug       # APK de debug
./gradlew :app:installDebug        # instalar no dispositivo ligado
```

O APK fica em `app/build/outputs/apk/debug/`.

## Estrutura

```
app/src/main/java/com/mysky/app/
├── data/           fontes de dados, repositórios, Room, DataStore
├── domain/         modelos, geometria, interfaces e casos de uso (Kotlin puro, testável na JVM)
├── presentation/   ecrãs Compose e ViewModels
├── di/             módulos Hilt
├── widget/         widget Glance
├── worker/         atualização periódica via WorkManager
└── notification/   notificações de "avião por cima"
```

## Privacidade

A localização é usada exclusivamente no dispositivo, para calcular distâncias e ângulos. Não é
enviada para a OpenSky (só a caixa envolvente da zona consultada) nem para qualquer servidor da
app — a app não tem servidor.

A permissão de localização em segundo plano é opcional e só é pedida se ativares as notificações;
sem ela a app funciona na mesma, apenas não te avisa com a app fechada.

## Contribuir

O desenvolvimento é orientado a especificação com o [Spec-Kit](https://github.com/github/spec-kit):
cada feature grande passa por `specify → clarify → plan → tasks → analyze → implement`. As
convenções de código e as decisões de arquitetura estão em [CLAUDE.md](CLAUDE.md); os princípios
não negociáveis em [`.specify/memory/constitution.md`](.specify/memory/constitution.md).

## Licença

Ainda por definir.
