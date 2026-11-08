# CodeScan — Agent Development Guide

CodeScan is a fork of SonarSource's SonarQube (v24.12), maintained by AutoRABIT LLC. It is a static code analysis platform with a Java backend and a React/TypeScript frontend.

## Repository Orientation

```
sonarqube/
├── server/
│   ├── sonar-web/                        # React 18 + TypeScript frontend (Vite)
│   ├── sonar-webserver-webapi/           # REST API v1 (/api/*)
│   ├── sonar-webserver-webapi-v2/        # REST API v2 (/api/v2/*), OpenAPI-annotated
│   ├── sonar-webserver-auth/             # Authentication & authorization
│   ├── sonar-ce-task-projectanalysis/    # Analysis computation pipeline
│   ├── sonar-db-dao/                     # DAOs and MyBatis mappers
│   ├── sonar-db-migration/               # Database schema migrations
│   └── sonar-main/                       # Bootstrap and process management
├── sonar-scanner-engine/                 # Local analysis engine (client-side)
├── codescan-application/                 # CodeScan-specific customizations
└── plugins/sonar-xoo-plugin/             # Example plugin for integration tests
```

**Data flow:**
```
Scanner → Compute Engine → Database + Elasticsearch → Web API → Frontend
```

**Branch:** `codescan-24.12` — all PRs target this branch.

---

## Build & Run Commands

### Backend (from repo root)

```bash
./gradlew build          # Full build with unit tests
./gradlew ide            # Fast IDE build (skips slower checks)
./gradlew test           # Run all Java tests
./gradlew test --tests ClassName  # Run specific test class
```

### Frontend (from `server/sonar-web/`)

```bash
yarn install             # Install dependencies
yarn build               # Production build
yarn start               # Dev server (Vite)
yarn test                # Run all Jest tests
yarn test --watch        # Watch mode
yarn test --coverage     # Coverage report
yarn ts-check            # TypeScript type checking
yarn lint                # ESLint
```

---

## Architecture

### Backend Layers (Controller → Service → DAO)

**v2 REST API** (preferred for new endpoints):
- Controllers: `server/sonar-webserver-webapi-v2/src/main/java/org/sonar/server/v2/api/{domain}/controller/`
- Naming: `Default{Domain}Controller` implements `{Domain}Controller` interface
- Inject `UserSession` for auth, delegate logic to service layer

**Service Layer:**
- Location: `server/sonar-webserver-webapi/src/main/java/org/sonar/server/{domain}/`
- Naming: `{Domain}Service`
- Holds business logic; uses `DbClient` to get DAOs, wraps in `try (DbSession session = dbClient.openSession(false))`

**DAO Layer:**
- Location: `server/sonar-db-dao/src/main/java/org/sonar/db/{domain}/`
- Naming: `{Entity}Dao`, DTOs named `{Entity}Dto`
- SQL queries in `src/main/resources/org/sonar/db/{domain}/{Entity}Mapper.xml`

**Compute Engine Steps:**
- Location: `server/sonar-ce-task-projectanalysis/src/main/java/org/sonar/ce/task/projectanalysis/step/`
- Implement `ComputationStep`; register in `ProjectAnalysisTaskContainer.java`

### Frontend (React 18 + TypeScript)

- Source root: `server/sonar-web/src/main/js/`
- Feature apps: `apps/{feature-name}/` — self-contained feature modules
- Shared components: `components/` — reusable UI components
- API client: `api/` — one file per backend domain, functions returning `Promise<T>`
- React Query hooks: `queries/` — wrap API calls for caching and state
- Helpers: `helpers/` — pure utility functions
- Types: `types/` — shared TypeScript types
- i18n keys: `server/sonar-web/l10n/` — manually maintained

**Design system:** `@sonarsource/echoes-react` — use its components before rolling custom ones.

**Path aliases:**
- `~sonar-aligned/*` → `src/main/js/sonar-aligned/*`
- `~design-system` → `src/main/js/design-system/index.ts`

---

## Where to Add New Code

### New v2 API endpoint

1. `server/sonar-webserver-webapi-v2/src/main/java/org/sonar/server/v2/api/{domain}/controller/Default{Domain}Controller.java`
2. Service: `server/sonar-webserver-webapi/src/main/java/org/sonar/server/{domain}/{Domain}Service.java`
3. DAO/DTO: `server/sonar-db-dao/src/main/java/org/sonar/db/{domain}/`
4. Tests: mirror source path under `src/test/java/`

### New frontend feature

1. App module: `server/sonar-web/src/main/js/apps/{feature-name}/`
2. API client: `server/sonar-web/src/main/js/api/{domain}.ts`
3. React Query hooks: `server/sonar-web/src/main/js/queries/{domain}.ts`
4. Tests: co-located `__tests__/{Component}-test.tsx`
5. i18n strings: add keys to `server/sonar-web/l10n/default.properties`

### New database migration

1. `server/sonar-db-migration/src/main/java/org/sonar/server/platform/db/migration/version/v{VERSION}/{MigrationName}.java`
2. Implement `DatabaseMigration` interface
3. Register in the version's step list

### New Compute Engine step

1. `server/sonar-ce-task-projectanalysis/.../step/{Name}Step.java` implementing `ComputationStep`
2. Add to pipeline order in `ProjectAnalysisTaskContainer.java`

---

## Code Conventions

### Java

- Spring `@Service` beans injected via constructor
- Nullability: annotate with `@CheckForNull` / `@Nullable` (JSR 305)
- Builder pattern for complex objects: `Entity.builder().setX().setY().build()`
- Permissions: always call `userSession.checkPermission(...)` or `userSession.checkEntityPermission(...)` before mutating
- `DbSession` always opened in try-with-resources
- Class naming: `Default{X}Controller`, `{X}Service`, `{X}Dao`, `{X}Dto`, `{X}Mapper`

### TypeScript / React

- Strict mode — no `any`, no `@ts-ignore` without justification
- Function components only — no class components
- Props typed via inline `interface Props {}`; interface keys sorted alphabetically (ESLint enforces this)
- Every file must have LGPL 3.0 copyright header (ESLint `header` plugin)
- Use `<Image>` component instead of `<img>` tags
- Use `SafeHTMLInjection` instead of `dangerouslySetInnerHTML`
- Named exports preferred; default exports only for React components
- API functions return `Promise<T>` and call `throwGlobalError()` on unexpected errors
- Avoid `Math.random()` in tests

---

## Testing

### Frontend

- Framework: Jest 29 + React Testing Library
- Test files: `__tests__/{Component}-test.tsx` co-located with source
- Render helper: `renderComponent()` from `helpers/testReactTestingUtils.ts`
- Query helpers: `byRole()`, `byTestId()` from `sonar-aligned/helpers/testSelector.ts`
- Mock API modules with `jest.mock('../api/domain')` + `jest.mocked(fn).mockResolvedValueOnce(...)`
- Async interactions: use `userEvent.setup()` from `@testing-library/user-event`
- Tests fail on `console.error` / `console.warn` — suppress expected warnings explicitly
- Custom matchers: `toHaveATooltipWithContent()`, `toHaveAPopoverWithContent()`

### Backend

- Framework: JUnit 5 + Mockito + AssertJ
- Test files: `src/test/java/` mirroring `src/main/java/` structure
- Naming: `{ClassName}Test.java`
- Use `underTest` as the variable name for the class under test
- Prefer real H2 database over mocks for DAO-level tests

---

## Key Technologies

| Layer | Tech |
|---|---|
| Backend language | Java 17+ |
| Build | Gradle 8+ |
| Backend framework | Spring 5.3 |
| ORM | MyBatis 3.5 |
| Database | PostgreSQL / MSSQL / H2 (test) |
| Search | Elasticsearch 8.14 |
| Serialization | Protocol Buffers 4.28, Gson 2.11 |
| Frontend framework | React 18 + TypeScript 5.6 (strict) |
| Frontend build | Vite 5.4 |
| Package manager | Yarn 4.2 |
| Styling | Tailwind CSS 3 + Emotion |
| Data fetching | TanStack React Query 5 |
| i18n | react-intl 6 |
| HTTP client (backend) | OkHttp 4.12 |
| HTTP client (frontend) | Axios 1.7 |
| Auth | JWT (jjwt 0.12), SAML (java-saml 2.9), BCrypt |

---

## Important Patterns to Follow

- **Never skip permission checks** in controllers — always assert user permissions before any mutation.
- **Use `DbSession` try-with-resources** — never leave sessions open.
- **Add migration for every schema change** — never alter tables without a versioned migration class.
- **i18n all user-visible strings** — no hardcoded English strings in frontend; use `translate('key')` or `<FormattedMessage id="key" />`.
- **React Query for all API calls** — do not use raw `useEffect` + `useState` for data fetching.
- **Test the behavior, not the implementation** — use semantic queries (`byRole`, `byLabelText`) not test IDs unless no semantic alternative exists.
- **No `console.log` left in committed code** — tests will fail on unexpected console output.
