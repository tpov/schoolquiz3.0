# QuestionDialogFragment Usage

## Описание

`QuestionDialogFragment` - это полноэкранный диалоговый фрагмент, который имеет точно тот же внешний вид и функциональность, что и оригинальная `QuestionActivity`. Это позволяет показывать викторину как диалог вместо запуска новой Activity.

## Преимущества использования DialogFragment вместо Activity

1. **Лучшая производительность** - нет необходимости создавать новую Activity
2. **Плавные переходы** - диалог может появляться с анимацией
3. **Сохранение контекста** - родительская Activity остается активной
4. **Простое управление** - легко закрыть диалог программно
5. **Меньше памяти** - DialogFragment использует меньше ресурсов

## Использование

### Вариант 1: Через Helper класс

```kotlin
import com.tpov.common.presentation.question.QuestionDialogHelper
import com.tpov.common.presentation.model.PathStructure

// В Activity
QuestionDialogHelper.showQuestionDialog(
    fragmentManager = supportFragmentManager,
    pathStructure = PathStructure(
        nameQuiz = "Quiz Name",
        nameCategory = "Category",
        nameSubCategory = "Sub Category",
        nameSubsubCategory = "Sub Sub Category",
        nameEvent = "Event"
    ),
    hardQuestion = false,
    life = 3
)
```

### Вариант 2: Через Extension функцию (в Fragment)

```kotlin
import com.tpov.common.presentation.question.showQuestionDialog
import com.tpov.common.presentation.model.PathStructure

// В Fragment
showQuestionDialog(
    pathStructure = PathStructure(
        nameQuiz = "Quiz Name",
        nameCategory = "Category",
        nameSubCategory = "Sub Category", 
        nameSubsubCategory = "Sub Sub Category",
        nameEvent = "Event"
    ),
    hardQuestion = false,
    life = 3
)
```

### Вариант 3: Прямое создание

```kotlin
import com.tpov.common.presentation.question.QuestionDialogFragment

val dialog = QuestionDialogFragment.newInstance(
    pathStructure = pathStructure,
    hardQuestion = false,
    life = 3
)
dialog.show(supportFragmentManager, "QuestionDialog")
```

## Замена существующих вызовов Activity

### Было (с Activity):
```kotlin
val intent = QuestionActivity.newIntent(
    context = this,
    hardQuestion = false,
    pathStructure = pathStructure,
    life = 3
)
startActivity(intent)
```

### Стало (с DialogFragment):
```kotlin
QuestionDialogHelper.showQuestionDialog(
    fragmentManager = supportFragmentManager,
    pathStructure = pathStructure,
    hardQuestion = false,
    life = 3
)
```

## Технические детали

- Использует тот же лэйаут `activity_question.xml`
- Полноэкранный режим с скрытием системных UI элементов
- Автоматическое закрытие при завершении викторины
- Поддержка всех анимаций и переходов
- Инъекция зависимостей через Dagger

## Примечания

- Диалог автоматически закрывается когда `viewModel.closeActivity` становится `true`
- Все функции викторины работают идентично оригинальной Activity
- Поддерживается drag-and-drop для ответов
- Работают все анимации и таймеры 