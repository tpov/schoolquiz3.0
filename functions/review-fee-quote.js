"use strict";

/**
 * Цитата платы за проверку для диалога подачи (история 4.11, D13).
 *
 * Чистая половина: валидация тела и пересчёт той же формулой, которой заявка платит на приёмке
 * (`reviewFeeNolics`). Показ — не власть: финальная плата всегда считается сервером из тела
 * реальной заявки в `planReviewFeeCharge`, а цитата лишь заранее называет автору цену. Если между
 * цитатой и подачей тариф или счётчик вопросов изменится, платой будет расчёт приёмки, а не цитата.
 */

const {reviewFeeNolics} = require("./review-fee");

/**
 * Тело запроса цитаты: адрес квеста и число вопросов заявки.
 *
 * `questionCount` считает клиент по своему черновику; цитата требует минимум один вопрос — заявка
 * без вопросов существовать не может, и честная цитата для пустого черновика не существует тоже.
 */
function parseReviewFeeQuoteRequest(payload) {
  const source = payload && typeof payload === "object" ? payload : {};
  const questId = source.questId;
  if (typeof questId !== "string" || questId.trim() === "" || questId.includes("/") ||
      questId === "." || questId === "..") {
    return quoteFailure("questId must be a non-empty document id");
  }
  const questionCount = source.questionCount;
  if (!Number.isInteger(questionCount) || questionCount < 1) {
    return quoteFailure("questionCount must be a positive integer");
  }
  return {ok: true, questId, questionCount};
}

function quoteFailure(reason) {
  return {ok: false, reason};
}

/** Версия конверта цитаты: строгий клиентский парсер читает только её. */
const QUOTE_VIEW_VERSION = "review-fee-quote-v1";

/**
 * Цитата: тариф, Revision и плата — ровно то, что диалог показывает автору.
 *
 * `tariff` повторяет решение `planReviewFeeCharge` («initial» | «recheck»), чтобы слово на экране
 * и тариф в расчёте не могли разойтись.
 */
function quoteReviewFee({tariffs, priorCycle, questionCount}) {
  return {
    view: QUOTE_VIEW_VERSION,
    priorCycle: Boolean(priorCycle),
    tariff: priorCycle ? "recheck" : "initial",
    feeNolics: reviewFeeNolics({tariffs, priorCycle, questionCount}),
    tariffRevision: tariffs.revision,
  };
}

module.exports = {parseReviewFeeQuoteRequest, quoteReviewFee, QUOTE_VIEW_VERSION};
