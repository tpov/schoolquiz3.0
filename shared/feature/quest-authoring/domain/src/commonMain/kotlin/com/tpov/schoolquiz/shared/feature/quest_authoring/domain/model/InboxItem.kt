package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

/**
 * Одна строка общего inbox (AD-20, CAP-6).
 *
 * Два вида записей с разными политиками доступа приезжают одним списком, и каждая строка несёт
 * свой исходный круг читателей ([readers]) и ссылку на урок ([lessonId]) — inbox ничего не
 * приватизирует и ничего не расширяет. Приватный отчёт — текст про весь урок; ни вопроса, ни
 * ответа в нём нет по построению (сервер такие поля не принимает), а того, кто подал, проекция
 * не называет: ответ автору не обещает двустороннего чата.
 */
sealed interface InboxItem {
    val id: String
    val lessonId: String
    val text: String
    val createdAtMs: Long
    val readers: InboxReaders

    /** Приватный отчёт об ошибке урока: читает только разрешённый автор урока. */
    data class PrivateErrorReport(
        override val id: String,
        override val lessonId: String,
        override val text: String,
        override val createdAtMs: Long,
    ) : InboxItem {
        override val readers: InboxReaders get() = InboxReaders.AUTHOR_ONLY
    }

    /** Публичный комментарий к уроку автора: читают все, inbox лишь сводит его в один список. */
    data class LessonComment(
        override val id: String,
        override val lessonId: String,
        override val text: String,
        override val createdAtMs: Long,
        val authorNickname: String,
    ) : InboxItem {
        override val readers: InboxReaders get() = InboxReaders.PUBLIC
    }
}

/** Исходный круг читателей строки inbox — едет по проводу, чтобы контракт был проверяемым. */
enum class InboxReaders(val wire: String) {
    AUTHOR_ONLY("author_only"),
    PUBLIC("public"),
}

/**
 * Страница inbox и якорь продолжения.
 *
 * Якорь прозрачен: пара (момент, id) последней выданной строки. Сервер сортирует по свежести
 * по убыванию, при равенстве момента — id по убыванию, и продолжает строго после якоря.
 */
data class InboxAnchor(
    val atMs: Long,
    val id: String,
)

data class InboxPage(
    val entries: List<InboxItem>,
    val nextAnchor: InboxAnchor?,
)
