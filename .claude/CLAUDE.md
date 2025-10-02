# CLAUDE.md - Caderneta Project

> **Project-specific configuration for Claude Code**
> This file provides context and guidelines for working with the Caderneta Android application.

## Project Overview

Caderneta is an Android sales management application built in Kotlin for small food vendors (specifically those selling snacks and juices). It serves as a mobile ledger system that manages:

- Customer organization with location hierarchies
- Product sales (cash and credit)
- Special promotions and packages
- Accounts receivable tracking
- Payment processing
- Financial reporting

## Technology Stack

- **Language**: Kotlin 1.9.23
- **Build System**: Gradle with Kotlin DSL
- **Architecture**: MVVM (Model-View-ViewModel)
- **Database**: Room 2.6.1 (SQLite ORM)
- **UI Framework**: XML layouts + Jetpack Compose (hybrid)
- **Navigation**: Jetpack Navigation Component with Bottom Navigation
- **Dependency Injection**: Custom DI via Application class (not Hilt/Koin)
- **Concurrency**: Kotlin Coroutines with Flow
- **Charts**: MPAndroidChart v3.1.0
- **Min SDK**: 21 (Android 5.0)
- **Target SDK**: 35 (Android 15)
- **Compile SDK**: 35

## Architecture & Code Organization

```
app/src/main/java/com/example/caderneta/
├── CadernetaApplication.kt        # App entry point, DI setup
├── MainActivity.kt                # Main activity with navigation
├── data/
│   ├── AppDatabase.kt            # Room database configuration
│   ├── dao/                      # Data Access Objects
│   │   ├── ClienteDao.kt
│   │   ├── ConfiguracoesDao.kt
│   │   ├── ContaDao.kt
│   │   ├── ItemVendaDao.kt
│   │   ├── LocalDao.kt
│   │   ├── OperacaoDao.kt
│   │   ├── ProdutoDao.kt
│   │   └── VendaDao.kt
│   └── entity/                   # Database entities
│       ├── Cliente.kt
│       ├── Configuracoes.kt
│       ├── Conta.kt
│       ├── ItemVenda.kt
│       ├── Local.kt
│       ├── ModoOperacao.kt       # Enum: VENDA, PROMOCAO, PAGAMENTO
│       ├── Operacao.kt
│       ├── Produto.kt
│       ├── TipoTransacao.kt      # Enum: A_VISTA, A_PRAZO
│       └── Venda.kt
├── repository/                    # Repository pattern implementation
│   ├── ClienteRepository.kt
│   ├── ConfiguracoesRepository.kt
│   ├── ContaRepository.kt
│   ├── ItemVendaRepository.kt
│   ├── LocalRepository.kt
│   ├── OperacaoRepository.kt
│   ├── ProdutoRepository.kt
│   └── VendaRepository.kt
├── ui/                           # User interface layer
│   ├── balanco/                  # Cash balance screens
│   │   └── BalancoCaixaFragment.kt
│   ├── components/               # Reusable UI components
│   │   └── CardBalancoView.kt
│   ├── configuracoes/            # Settings screens
│   │   └── ConfiguracoesFragment.kt
│   ├── consultas/                # Query/search screens
│   │   ├── ConsultasFragment.kt
│   │   ├── EditarOperacaoDialog.kt
│   │   ├── ExtratoAdapter.kt
│   │   ├── LocalConsultaAdapter.kt
│   │   ├── OpcoesClienteConsultaDialog.kt
│   │   ├── OpcoesExtratoDialog.kt
│   │   ├── ResultadoConsulta.kt
│   │   └── ResultadosConsultaAdapter.kt
│   ├── historico/                # Sales history screens
│   │   ├── DetalhesVendasAdapter.kt
│   │   └── HistoricoVendasFragment.kt
│   ├── theme/                    # Compose theme
│   │   ├── Color.kt
│   │   ├── Theme.kt
│   │   └── Type.kt
│   └── vendas/                   # Sales management screens
│       ├── ClientesAdapter.kt
│       ├── LocalAdapter.kt
│       ├── NovoClienteDialog.kt
│       ├── OpcoesClienteDialog.kt
│       ├── RegistrarPagamentoDialog.kt
│       ├── VendaPersonalizadaDialog.kt
│       └── VendasFragment.kt
├── util/                         # Utility classes
│   ├── ContadorHelper.kt
│   ├── DateConverter.kt
│   └── Extensions.kt
└── viewmodel/                    # ViewModels
    ├── BalancoCaixaViewModel.kt
    ├── ConfiguracoesViewModel.kt
    ├── ConsultasViewModel.kt
    ├── HistoricoVendasViewModel.kt
    └── VendasViewModel.kt
```

## Key Domain Concepts

### Location Hierarchy
- Up to 4 levels of nested locations (e.g., Region → Zone → Building → Floor)
- Clients are associated with locations
- Navigation drawer allows filtering by location

### Transaction Types
- **A_VISTA** (Cash): Immediate payment
- **A_PRAZO** (Credit): Payment deferred, tracked in customer accounts

### Operation Modes
- **VENDA** (Sale): Regular product sales
- **PROMOCAO** (Promotion): Special promotional packages
- **PAGAMENTO** (Payment): Payment against outstanding balance

### Pricing System
- Products have different prices for cash vs. credit sales
- Promotions have custom pricing
- Configurable promotional packages

### Customer Accounts
- Tracks outstanding balances for credit sales
- Records payment history
- Automatic balance updates

## Development Guidelines

### Code Style
- Follow Kotlin conventions
- Use Kotlin idioms (scope functions, null safety, etc.)
- Prefer immutable data structures
- Use sealed classes for state management
- Follow existing naming conventions

### Database Operations
- All database operations must be async (use suspend functions or Flow)
- Use transactions for multi-table operations
- Foreign keys are enforced - check relationships before deletions
- Use cascade/set-null strategies appropriately
- Room entities use proper type converters (Date, Enums)

### State Management
- ViewModels use StateFlow and SharedFlow
- UI state is encapsulated in data classes
- Use reactive patterns with Flow collection
- Implement debouncing for search queries
- Single source of truth for data

### UI Development
- Hybrid approach: XML layouts + Jetpack Compose
- Migration to Compose is ongoing but incomplete
- Use Material Design 3 components
- Follow existing dialog patterns (BottomSheetDialogFragment)
- Preserve existing UI patterns when modifying

### Navigation
- Uses Navigation Component with nav graph
- Bottom navigation for main sections
- Navigation drawer for location filtering
- Safe Args plugin for type-safe argument passing

### Testing
- Unit tests in `test/` directory
- Instrumented tests in `androidTest/` directory
- Use Room in-memory database for testing
- Mock repositories in ViewModel tests

## Common Tasks

### Adding a New Entity
1. Create entity class in `data/entity/`
2. Create DAO interface in `data/dao/`
3. Add DAO to AppDatabase
4. Increment database version
5. Implement migration
6. Create repository in `repository/`
7. Add repository to DI setup in CadernetaApplication

### Adding a New Screen
1. Create Fragment in appropriate `ui/` subdirectory
2. Add layout XML or Compose UI
3. Create ViewModel if needed
4. Add to navigation graph
5. Update bottom navigation if main section

### Modifying Database Schema
1. Update entity class
2. Increment database version in AppDatabase
3. Write migration in AppDatabase
4. Test migration thoroughly
5. Update affected DAOs and repositories

### Adding a New Query
1. Add suspend function or Flow to DAO
2. Use proper SQL with Room annotations
3. Add repository method
4. Expose via ViewModel
5. Collect Flow in Fragment/Activity

## Build & Run

### Gradle Tasks
- `./gradlew build` - Build the project
- `./gradlew assembleDebug` - Build debug APK
- `./gradlew assembleRelease` - Build release APK (minified)
- `./gradlew test` - Run unit tests
- `./gradlew connectedAndroidTest` - Run instrumented tests
- `./gradlew clean` - Clean build artifacts

### Build Configuration
- Uses KSP for Room annotation processing (not KAPT)
- ProGuard enabled for release builds
- Core library desugaring enabled for Java 8+ APIs
- Compose compiler version: 1.5.12

### Dependencies Management
- Compose BOM: 2024.05.00 (check for latest stable)
- Room version: 2.6.1
- Navigation version: 2.7.7
- Keep versions consistent across modules

## Known Issues & Considerations

### Potential Improvements
1. **Dependency Injection**: Consider migrating to Hilt or Koin
2. **Testing**: Increase test coverage for business logic
3. **Error Handling**: Implement comprehensive error handling
4. **UI Migration**: Complete migration to Jetpack Compose
5. **Data Backup**: Add cloud sync for backup/recovery
6. **Multi-user**: Support multiple vendor accounts
7. **Receipt Generation**: Generate and share sales receipts
8. **Notifications**: Implement reminders for overdue accounts
9. **Analytics Dashboard**: Enhanced reporting with detailed analytics

### Technical Debt
- Mixed UI paradigm (XML + Compose) - complete migration
- Custom DI instead of framework
- Limited test coverage
- No offline-first sync strategy

## Code Quality Tools

### Configured Tools
- **Detekt**: Kotlin static analysis (`detekt-config.yml`)
- **EditorConfig**: Code style consistency (`.editorconfig`)
- **Qodana**: Code quality inspection (`qodana.yaml`)

### CI/CD
- GitHub Actions workflows in `.github/workflows/`
- Automated checks on pull requests
- Build and test automation

## Important Files

- `build.gradle.kts` (root) - Project-level Gradle configuration
- `app/build.gradle.kts` - App module Gradle configuration
- `settings.gradle.kts` - Gradle settings
- `gradle.properties` - Gradle properties
- `.editorconfig` - Editor configuration
- `detekt-config.yml` - Detekt configuration
- `qodana.yaml` - Qodana configuration
- `proguard-rules.pro` - ProGuard rules for release builds

## Git Workflow

### Branching Strategy
- Current branch: `master` (main branch)
- Create feature branches for new features
- Use descriptive branch names

### Commit Guidelines
- Follow existing commit message style
- Be descriptive but concise
- Reference issue numbers if applicable
- Include co-author attribution when pair programming

## Working with Claude Code

### Best Practices
1. **Understand before modifying**: Read related code before making changes
2. **Follow existing patterns**: Match the architectural style
3. **Test changes**: Run builds and tests after modifications
4. **Maintain consistency**: Use same coding style and patterns
5. **Update documentation**: Keep README and comments current

### When Making Changes
- Always read existing files before editing
- Preserve existing structure and patterns
- Test database migrations thoroughly
- Run `./gradlew build` after significant changes
- Check for compilation errors
- Verify UI changes on multiple screen sizes

### Tool Usage Preferences
- Use **Read** tool to understand existing code
- Use **Edit** tool to modify existing files
- Use **Grep** tool to search for patterns
- Use **Glob** tool to find files
- Use **Bash** tool for Gradle commands and git operations

## Contact & Support

For questions about the codebase:
1. Check the comprehensive README.md
2. Review existing code patterns
3. Check git history for context on changes
4. Ask clarifying questions before major refactoring

---

**Remember**: This is a production application for real business use. Changes should be:
- Thoroughly tested
- Backwards compatible with existing data
- Well-documented
- Following established patterns