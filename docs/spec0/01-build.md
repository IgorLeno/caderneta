# Etapa 1 — Consolidação do sistema de build

## Estado encontrado (antes)

Três fontes de verdade conflitantes:

| Fonte | AGP | Kotlin | Observações |
| --- | --- | --- | --- |
| `build.gradle.kts` (root) | 8.10.0 | 1.9.23 | SafeArgs 2.7.7, KSP 1.9.23-1.0.19 |
| `gradle/libs.versions.toml` | 8.5.2 | 1.9.0 | Catálogo praticamente não referenciado; entradas fantasma (filament, firebase-vertexai, media3) |
| `app/build.gradle.kts` | — | — | Versões hardcoded com comentários provisórios ("verifique a mais recente", "Exemplo", "Temporariamente") |

Dependências/plugins sem uso real no código:

- `org.jetbrains.kotlin.plugin.serialization` — nenhum `kotlinx.serialization` no código.
- `androidx.paging:paging-runtime-ktx` — nenhum import de Paging.
- Compose (BOM, material3, ui-*, activity-compose, buildFeatures.compose) — nenhum `setContent`/`@Composable` fora dos 3 arquivos de tema gerados (`ui/theme/`); `MainActivity` usa ViewBinding.
- Catálogo: `filament-android`, `firebase-vertexai`, `media3-common-ktx`, `material3-android` — nunca referenciados.
- `androidx.drawerlayout` explícito — já transitivo, mas usado diretamente em layout (mantido).

Em uso real confirmado: Room, Navigation (fragment/ui + SafeArgs), Lifecycle, Coroutines, Material Components, MPAndroidChart (`HistoricoVendasFragment`), core-ktx, appcompat, fragment-ktx, desugaring.

`Produto`/`ItemVenda`/`ProdutoRepository`/`ItemVendaRepository`: código morto (só instanciados no DI e passados ao `VendasViewModel`, que não os usa). Remoção das entidades ocorre na Etapa 2 (exige mudança de schema).

## Matriz de versões escolhida (fonte única: `gradle/libs.versions.toml`)

Verificada contra os metadados Maven (Google Maven / Maven Central / Plugin Portal) em 2026-07-07:

| Componente | De | Para | Justificativa |
| --- | --- | --- | --- |
| Gradle wrapper | 8.11.1 | 8.13 | Mínimo exigido pelo AGP 8.13 |
| AGP | 8.10.0 / 8.5.2 | 8.13.2 | Última da linha 8.x (evita o salto para AGP 9, que muda defaults e DSL) |
| Kotlin | 1.9.23 / 1.9.0 | 2.1.21 | K2 estável e maduro; decisão "toolchain moderado" aprovada |
| KSP | 1.9.23-1.0.19 | 2.1.21-2.0.2 | KSP2, par exato do Kotlin |
| Room | 2.6.1 | 2.8.4 | Estável atual; exige minSdk 23 (decisão aprovada) |
| Navigation + SafeArgs | 2.7.7 | 2.9.8 | Estável atual da linha 2.9 |
| minSdk | 21 | 23 | Decisão aprovada (Room 2.8) |
| JDK | 17 | 17 | Compatível com AGP 8.13; disponível no ambiente |

Passos executados em commits separados (build verde entre cada um) — resultados reais registrados abaixo conforme execução.

## Execução (comandos e resultados reais)

Baseline release (config original, antes de qualquer mudança): `./gradlew assembleRelease` → **BUILD SUCCESSFUL em 2m56s**, `app-release-unsigned.apk` com R8.

| Commit | Mudança | Verificação |
| --- | --- | --- |
| 1 | Catálogo como fonte única (mesmas versões efetivas) | `assembleDebug testDebugUnitTest` ✅ |
| 2 | Remoção de mortos (Compose, serialization, Paging, tema) + recyclerview/constraintlayout explícitos | `assembleDebug testDebugUnitTest` ✅ |
| 3 | minSdk 21→23 | `assembleDebug` ✅ |
| 4 | Toolchain: Gradle 8.13, AGP 8.13.2, Kotlin 2.1.21, KSP2, Room 2.8.4, Nav 2.9.8, compileSdk 36 | `assembleDebug testDebugUnitTest` ✅ (5m12s) e `assembleRelease` ✅ (2m58s) |
| 5 | Detekt 1.23.8 + ktlint 14.2.0 declarados no build com baselines | `:app:detekt :app:ktlintCheck` ✅ |

Notas do commit 2: a remoção do Compose expôs dependências implícitas — `bindingAdapterPosition` exige RecyclerView ≥ 1.2, que vinha transitivo do Compose. RecyclerView 1.3.2 e ConstraintLayout 2.1.4 passaram a ser declarados diretamente (a spec pedia exatamente isso).

Notas do commit 4: compileSdk elevado a 36 porque as AndroidX atuais o exigem; **targetSdk permanece 35** (mudar targetSdk altera comportamento em runtime e fica para a Etapa 9). Únicos warnings de compilação restantes são deprecações pré-existentes no código (`fallbackToDestructiveMigration`, `Locale(String,String)`, `onOptionsItemSelected`) — tratados nas Etapas 2 e 6.

Notas do commit 5: `detekt-config.yml` tinha a seção `formatting` (exige plugin extra; formatação já é papel do ktlint) e regras renomeadas/removidas no Detekt 1.23 (`ComplexMethod`→`CyclomaticComplexMethod`, `DuplicateCaseInWhenExpression`, `ForbiddenPublicDataClass`, `MandatoryBracesIfStatements`) — corrigido. Baselines geradas: `app/detekt-baseline.xml` (142 issues do legado) e `app/ktlint-baseline.xml` (1460). As ferramentas rodam de verdade; código novo é cobrado integralmente.
