package com.tpov.schoolquiz.shared.feature.lesson.data.di

import com.tpov.schoolquiz.shared.feature.lesson.data.LessonLocalDataSource
import com.tpov.schoolquiz.shared.feature.lesson.data.LessonLocalDataSourceImpl
import com.tpov.schoolquiz.shared.feature.lesson.data.LessonRepositoryImpl
import com.tpov.schoolquiz.shared.feature.lesson.data.outbox.LessonUnlockQuarantineRollback
import com.tpov.schoolquiz.shared.feature.lesson.data.outbox.OutboxLessonUnlockQueue
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonUnlockQueue
import kotlinx.datetime.Clock
import org.koin.dsl.module

val lessonDataModule =
    module {
        single<LessonLocalDataSource> { LessonLocalDataSourceImpl(get()) }
        single<LessonRepository> { LessonRepositoryImpl(local = get(), remote = get()) }
        // Разблокировка урока — операция фичи lesson, и её постановка в очередь объявлена здесь же,
        // в модуле владеющей фичи (NFR4). Таблица и движок остаются в composition root (NFR5).
        single<LessonUnlockQueue> { OutboxLessonUnlockQueue(userStats = get(), clock = Clock.System) }
        // Реакция на карантин принадлежит той же фиче: локальные таблицы правит она, а не ядро (AD-7,
        // AD-28). Связь с именем операции стоит в composition root, рядом с движком.
        single { LessonUnlockQuarantineRollback(userStats = get()) }
    }
