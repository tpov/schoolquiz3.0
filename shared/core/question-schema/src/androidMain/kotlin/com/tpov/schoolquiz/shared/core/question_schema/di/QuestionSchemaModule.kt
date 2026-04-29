package com.tpov.schoolquiz.shared.core.question_schema.di

import com.tpov.schoolquiz.shared.core.question_schema.KotlinxSerializationQuestionContentParser
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContentParser
import org.koin.dsl.module

val questionSchemaModule = module {
    single<QuestionContentParser> { KotlinxSerializationQuestionContentParser() }
}
