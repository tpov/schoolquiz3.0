# Domain Models — KMP

## Principles

- Domain models are pure Kotlin common code — no Room, Retrofit, Android annotations, platform SDK types, or serialization annotations.
- Immutable `data class` with `val` fields.
- Closed state sets via `enum class` or `sealed interface`.
- Add field to domain only if concept is needed by multiple layers.
- Value objects encapsulate validation (e.g., `Email`, `UserId`).

## Mapper chain

```
Entity (Room) <-> mapper (extension function) <-> Domain Model <-> DTO (Network)
```

- Mappers live in data layer.
- Convention: `Entity.toDomain()` and `DomainModel.toEntity()`.
- DTO mapping: `Dto.toDomain()` and `DomainModel.toDto()`.

## Rules

- Domain models define the vocabulary of the business logic. Every layer speaks in domain terms.
- No parallel shadow-models if existing aggregate can be extended.
- Consult PROJECT-CONTEXT.md for project-specific domain model locations and conventions.

## Avoid

- No DTO or Room entity leaks to UI — always map to domain first.
- **No Android framework types anywhere in domain** (включая: поля класса, параметры функций, generic параметры, return types, extension receivers, default values, typealias):
  - `android.content.Context`, `android.content.Intent`, `android.net.Uri`, `android.os.Bundle`
  - `android.view.View`, `android.widget.*`, любой UI тип
  - `androidx.*` (Lifecycle, ViewModel, LiveData, WorkManager)
  - Исключений для Parcelable в KMP domain нет; navigation/platform mapping живёт вне domain.
- **No third-party SDK types в domain signatures** (включая параметры, поля, return types):
  - `io.livekit.*`, `com.google.firebase.*`, `retrofit2.*`, `okhttp3.*`
  - `androidx.room.*`, `com.squareup.moshi.*`, `kotlinx.serialization.*`
  - Любой SDK тип wrap'ится в data layer и маппится в domain-специфичный тип
- No serialization annotations (`@SerialName`, `@ColumnInfo`, `@Json`, `@Entity`) on domain models.
- **No DI annotations** (`@Inject`, `@Provides`, `@Module`, `@Singleton`) — domain ничего не знает о DI framework.
- **No `suspend` в domain pure functions** без явной async cause — domain синхронный. Async boundary — в data/presentation layer.

## Review check (grep-паттерны для architect-reviewer)

```bash
# Нарушение: Android imports в domain (кроме Parcelable)
rg -n "^import (android|androidx)\\." <domain_path>/

# Нарушение: SDK types в domain
rg -n "^import (io\\.livekit|com\\.google\\.firebase|retrofit2|okhttp3|androidx\\.room|com\\.squareup\\.moshi|kotlinx\\.serialization)" <domain_path>/

# Нарушение: Context/Uri/Bundle/View как параметр функции или поле class
rg -n "\\b(Context|Uri|Bundle|Intent|View|Activity|Fragment)\\s*[:,)]" <domain_path>/ -g "*.kt"

# Нарушение: DI аннотации в domain
rg -n "@(Inject|Provides|Binds|Module|Singleton|HiltAndroidApp|AndroidEntryPoint|HiltViewModel)" <domain_path>/ -g "*.kt"
```

Любой non-empty output = blocker для architect-reviewer.
