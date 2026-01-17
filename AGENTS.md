# AGENTS.md

This file provides guidance for AI agents working on the ReadYou Android RSS reader project.

## Build Commands

### Standard Build
```bash
./gradlew build
```

### Build Specific Variants
```bash
./gradlew assembleDebug      # Debug build
./gradlew assembleRelease    # Release build
./gradlew assembleGithub     # GitHub flavor
./gradlew assembleFdroid     # F-Droid flavor
./gradlew assembleGooglePlay # Google Play flavor
```

### Testing
```bash
./gradlew test                           # All unit tests
./gradlew connectedAndroidTest           # Instrumented tests
./gradlew test --tests ClassName.testName  # Single test
./gradlew test --tests me.ash.reader.infrastructure.rss.RssHelperTest
```

### Clean Build
```bash
./gradlew clean
```

### Note on Linting
The project disables some lint rules (MissingTranslation, ExtraTranslation) in build.gradle.kts. No dedicated ktlint or detekt configuration is present.

## Code Style Guidelines

### Project Architecture
- **Clean Architecture**: Domain (models, services, repositories), Data (DAOs), UI (Compose components, ViewModels), Infrastructure (DI, database)
- **MVVM**: ViewModels with StateFlow for UI state
- **Single Activity**: Jetpack Compose navigation

### Package Structure
```
me.ash.reader.domain.model/        # Data models (data classes)
me.ash.reader.domain.repository/   # Room DAOs
me.ash.reader.domain.service/      # Business logic services
me.ash.reader.domain.data/         # Use cases
me.ash.reader.ui.component/       # Reusable Compose components
me.ash.reader.ui.page/            # Screen ViewModels and composables
me.ash.reader.ui.ext/             # Extension functions
me.ash.reader.infrastructure.di/  # Hilt modules
me.ash.reader.infrastructure.db/  # Database setup
```

### Naming Conventions
- **Classes**: PascalCase (e.g., `ArticlePagingListUseCase`, `FeedOptionViewModel`)
- **Composable Functions**: PascalCase (e.g., `Banner`, `ArticleItem`)
- **Functions**: camelCase (e.g., `markAsRead`, `showRenameDialog`)
- **State Variables**: `_variableName` (mutable), `variableName` (exposed)
- **Constants**: UPPER_SNAKE_CASE (e.g., `enclosureUrlString1`)
- **Database Entities**: PascalCase with `@Entity` annotation

### Imports
- Group by: standard library, Android, third-party, project
- Alphabetical within groups
- Avoid wildcard imports
- Import statements match package structure (domain, ui, infrastructure)

### Kotlin Conventions
- Use `data class` for models with `equals()`/`hashCode()` needs
- Use `val` by default, `var` only when necessary
- Non-null by default, explicit nullable types with `?`
- Use `suspend` for coroutine functions
- Prefer `Flow` over callback-style APIs

### Compose UI
- Use `@Composable` functions for UI components
- Pass `Modifier` as first parameter with defaults
- Use `MaterialTheme.colorScheme` for colors
- Use `dp` units for dimensions
- Prefer composable parameters over callbacks where possible
- Use `Modifier.padding()`, `Modifier.clickable()`, etc.

### Dependency Injection (Hilt)
- Use `@Inject constructor(...)` for classes
- Use `@HiltViewModel` for ViewModels
- Use `@Module @InstallIn(SingletonComponent::class)` for providers
- Use qualifiers like `@ApplicationContext`, `@IODispatcher`, `@ApplicationScope`

### Database (Room)
- Entities: `@Entity` with `@PrimaryKey`
- DAOs: Interface with `@Dao` annotation
- Queries: Use SQL string literals, `@Query`, `@Insert`, `@Update`, `@Delete`
- Use `suspend` for database operations
- Use `Flow<T>` or `PagingSource` for reactive queries
- Foreign keys defined in `@Entity` annotation

### State Management
- ViewModels: `private val _state = MutableStateFlow(...)`, `val state: StateFlow = _state.asStateFlow()`
- Use `viewModelScope` for ViewModel coroutines
- Use `applicationScope` for application-wide operations
- Switch dispatchers: `withContext(ioDispatcher) { ... }`

### Coroutines
- Use `@IODispatcher` for I/O operations
- Use `@MainDispatcher` for UI updates
- Use `@ApplicationScope` for application-level coroutines
- Prefer `flow.collectLatest()` over `flow.collect()`

### Error Handling
- Use try-catch with proper logging
- Use `suspend` functions with proper exception handling
- Use `Result` type where appropriate
- Avoid silent failures - log errors with Timber

### Testing
- Unit tests in `app/src/test/`
- Instrumented tests in `app/src/androidTest/`
- Use Mockito with `@Mock` annotations
- Use JUnit assertions (`assertEquals`, `assertNull`, etc.)
- Test file names end with `Test.kt`

### Formatting
- Use Kotlin standard formatting (4-space indentation)
- Max line length: ~120 characters (not enforced)
- Trailing commas in multi-line function calls
- Space before `{` in function declarations
- No semicolons

### File Structure Example
```kotlin
package me.ash.reader.domain.model

import android...
import androidx...
import kotlinx...
import me.ash.reader...

@Entity(tableName = "table_name")
data class Model(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val field: String
)
```
