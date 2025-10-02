# Guia de GitHub Actions - Caderneta

Este projeto possui um conjunto completo de workflows de GitHub Actions configurados para garantir qualidade de código, segurança e automação de CI/CD.

## 📊 Badges de Status

Adicione os seguintes badges ao seu README.md (substitua `USUARIO` e `REPO` pelos valores corretos):

```markdown
![CI Status](https://github.com/USUARIO/REPO/actions/workflows/ci.yml/badge.svg)
![Code Quality](https://github.com/USUARIO/REPO/actions/workflows/code-quality.yml/badge.svg)
![Security](https://github.com/USUARIO/REPO/actions/workflows/security.yml/badge.svg)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
```

## 🚀 Workflows Configurados

### 1. CI - Build and Test (`.github/workflows/ci.yml`)

**Quando executa:**
- Push para branch `master`
- Pull requests para `master`
- Manualmente via GitHub UI

**O que faz:**
- ✅ Build do APK debug
- ✅ Executa testes unitários
- ✅ Executa testes instrumentados (Android Tests)
- ✅ Gera relatórios de cobertura de código
- ✅ Build de release (somente em push para master)
- ✅ Publica resultados de testes e artifacts

**Jobs:**
- `build` - Build do APK Debug
- `unit-tests` - Testes unitários com relatórios
- `instrumented-tests` - Testes instrumentados no emulador Android
- `build-release` - Build de Release APK (após sucesso dos testes)
- `status-check` - Resumo do status do CI

### 2. Code Quality - Lint & Analysis (`.github/workflows/code-quality.yml`)

**Quando executa:**
- Push para branch `master`
- Pull requests para `master`
- Manualmente via GitHub UI

**O que faz:**
- 🔍 Análise estática com Detekt
- 📏 Verificação de formatação com ktlint
- 🤖 Android Lint nativo
- 📝 Comentários automáticos em PRs com issues encontrados
- 📊 Upload de relatórios SARIF para GitHub Security

**Jobs:**
- `detekt` - Análise estática de qualidade de código Kotlin
- `ktlint` - Verificação de formatação e estilo
- `android-lint` - Análise nativa do Android
- `quality-summary` - Resumo dos resultados

### 3. PR Validation (`.github/workflows/pr-validation.yml`)

**Quando executa:**
- Pull requests (opened, synchronize, reopened, edited)

**O que faz:**
- ✉️ Valida mensagens de commit (Conventional Commits)
- 🏷️ Valida título do PR
- 📏 Verifica tamanho do PR e adiciona labels
- 📝 Valida descrição do PR
- 🔀 Verifica conflitos de merge
- 💬 Adiciona comentários automáticos em PRs

**Jobs:**
- `commit-lint` - Validação de mensagens de commit
- `pr-title` - Validação de título do PR
- `pr-size` - Verificação de tamanho e labels
- `pr-description` - Validação de descrição
- `merge-conflict` - Verificação de conflitos
- `validation-summary` - Resumo das validações

**Padrão de Commits:**
```
<tipo>(<escopo>): <assunto>

<corpo>

<rodapé>
```

**Tipos válidos:** feat, fix, docs, style, refactor, perf, test, build, ci, chore, revert

### 4. Security Analysis (`.github/workflows/security.yml`)

**Quando executa:**
- Push para branch `master`
- Pull requests para `master`
- Semanalmente (segundas-feiras às 9h UTC)
- Manualmente via GitHub UI

**O que faz:**
- 🔒 Revisão de dependências (Dependency Review)
- 🛡️ Verificação de vulnerabilidades (OWASP Dependency Check)
- 🔎 Análise semântica de segurança (CodeQL)
- 🔑 Detecção de secrets no código (Gitleaks)
- 📜 Verificação de licenças
- 🤖 Verificação de segurança Android (manifest, proguard)

**Jobs:**
- `dependency-review` - Análise de mudanças em dependências (PRs)
- `dependency-check` - OWASP Dependency Check
- `codeql-analysis` - CodeQL Security Scan
- `secret-scan` - Detecção de secrets com Gitleaks
- `license-check` - Verificação de conformidade de licenças
- `android-security` - Checagem de configurações de segurança Android
- `security-summary` - Resumo dos resultados

## 🔧 Configurações Adicionais

### Dependabot (`.github/dependabot.yml`)

Atualiza automaticamente:
- Dependências Gradle (semanalmente)
- GitHub Actions (semanalmente)

Cria PRs automaticamente com:
- Labels apropriados
- Assignees configurados
- Agrupamento de dependências relacionadas

### Pull Request Template (`.github/PULL_REQUEST_TEMPLATE.md`)

Template completo para PRs incluindo:
- Descrição de mudanças
- Tipo de mudança
- Motivação e contexto
- Como foi testado
- Checklist de qualidade
- Impacto em performance
- Breaking changes

### Configurações de Código

**Detekt (`detekt-config.yml`):**
- Análise de complexidade
- Verificação de coroutines
- Detecção de bugs potenciais
- Formatação e estilo
- Performance

**EditorConfig (`.editorconfig`):**
- Charset UTF-8
- LF line endings
- 4 espaços de indentação (Kotlin, XML, Gradle)
- 2 espaços (YAML, JSON)
- Trim trailing whitespace

## 📦 Secrets Necessários

Configure os seguintes secrets no GitHub (Settings > Secrets and variables > Actions):

| Secret | Descrição | Obrigatório |
|--------|-----------|-------------|
| `GITHUB_TOKEN` | Token automático do GitHub | ✅ Sim (auto-gerado) |
| `GITLEAKS_LICENSE` | Licença Gitleaks (enterprise) | ❌ Não (opcional) |

Para adicionar outros secrets (ex: chaves de assinatura, tokens de deploy):
1. Vá em Settings > Secrets and variables > Actions
2. Clique em "New repository secret"
3. Adicione nome e valor do secret

## 🎯 Uso Recomendado

### Para Desenvolvedores

1. **Antes de criar um commit:**
   ```bash
   # Execute lint localmente
   ./gradlew ktlintCheck
   ./gradlew detekt

   # Execute testes
   ./gradlew test
   ```

2. **Mensagens de commit:**
   ```bash
   # Boa prática
   git commit -m "feat: adiciona filtro de data nas transações"
   git commit -m "fix: corrige crash ao salvar sem internet"

   # Evite
   git commit -m "Update code"
   git commit -m "WIP"
   ```

3. **Criando Pull Requests:**
   - Use títulos descritivos seguindo Conventional Commits
   - Preencha o template completo
   - Mantenha PRs pequenos (< 500 linhas quando possível)
   - Adicione screenshots para mudanças visuais

### Para Revisores

- ✅ Verifique se todos os checks passaram
- ✅ Revise os comentários automáticos de lint/detekt
- ✅ Verifique relatórios de cobertura de testes
- ✅ Confirme que não há vulnerabilidades de segurança

### Para Mantenedores

1. **Configurar Branch Protection:**
   - Settings > Branches > Add rule
   - Branch name pattern: `master`
   - Marque:
     - Require pull request reviews before merging
     - Require status checks to pass before merging
     - Require branches to be up to date before merging
   - Selecione os checks obrigatórios:
     - CI Status Check
     - Code Quality Summary
     - PR Validation Summary
     - Security Summary

2. **Revisar Dependabot PRs:**
   - Analise mudanças em dependências
   - Verifique breaking changes
   - Execute testes antes de mergear

## 🐛 Troubleshooting

### Build falhando no CI mas passa localmente

```bash
# Limpe o cache local
./gradlew clean

# Execute com mesmas opções do CI
./gradlew assembleDebug --stacktrace --no-daemon
```

### Detekt/ktlint falhando

```bash
# Auto-corrigir problemas de formatação
./gradlew ktlintFormat

# Ver relatório detalhado do Detekt
./gradlew detekt
# Abra: build/reports/detekt/detekt.html
```

### Testes instrumentados falhando

- Verifique se o código é compatível com API 29 (Android 10)
- Desabilite animações no emulador
- Use `@SdkSuppress` para testes específicos de versão

### CodeQL timeout

- Divida arquivos muito grandes
- Adicione exclusões em `.github/workflows/security.yml`:
  ```yaml
  paths-ignore:
    - '**/generated/**'
  ```

## 📚 Referências

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Detekt Documentation](https://detekt.dev/)
- [ktlint Documentation](https://pinterest.github.io/ktlint/)
- [Conventional Commits](https://www.conventionalcommits.org/)
- [CodeQL Documentation](https://codeql.github.com/docs/)
- [OWASP Dependency Check](https://owasp.org/www-project-dependency-check/)

## 🎨 Customização

### Ajustar thresholds de qualidade

Edite `detekt-config.yml`:
```yaml
complexity:
  LongMethod:
    threshold: 60  # Ajuste conforme necessário
```

### Modificar validação de PRs

Edite `.github/workflows/pr-validation.yml`:
```yaml
pr-size:
  xl_label: 'size/xl'
  l_max_size: 1000  # Ajuste o tamanho máximo
```

### Adicionar novos checks

Crie novos jobs nos workflows existentes ou crie novos arquivos YAML em `.github/workflows/`.

## 🤝 Contribuindo

Ao contribuir para este projeto:
1. Siga o guia de estilo de código (Detekt + ktlint)
2. Adicione testes para novas funcionalidades
3. Mantenha cobertura de testes > 60% para arquivos modificados
4. Preencha o template de PR completamente
5. Certifique-se que todos os checks passam

---

**Dúvidas?** Abra uma issue ou consulte a documentação dos workflows individuais.
