package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

/**
 * Сервер ответил «право не выдано» (4.17, UX-22): requireVerifier отказал, пользователь не админ.
 *
 * Это ответ, а не сбой канала: вкладка «Решения» обязана показать авторскую сторону — секцию
 * «Предложения». Живёт в домене, потому что решает им экран, а транспорт только различает
 * отказ роли и прочие сбои.
 */
class ReviewDecisionsAccessRefusedException : Exception("Decisions access refused by role")
