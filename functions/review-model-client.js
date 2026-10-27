"use strict";

/**
 * Транспорт модели проверки (AD-32, история 4.12).
 *
 * Конфиг — только окружение VPS: `REVIEW_MODEL_API_URL`, `REVIEW_MODEL_API_KEY`,
 * `REVIEW_MODEL_NAME`. Ключ не читается из файлов репозитория, не пишется в журналы и не едет
 * в записи вердиктов — наружу называется только имя модели (OQ-10: конкретная модель — после
 * пробных прогонов, до тех пор имя задаёт окружение).
 *
 * Текст урока едет данными внутри разграничителей: модель судит содержимое, а не исполняет его.
 * Промпт этапа собирает скилл этапа (4.13/4.14); здесь — только конверт данных и честный транспорт.
 */

const REQUEST_TIMEOUT_MS = 300_000;

/** Разграничители данных урока: всё между ними — данные, никогда инструкции. */
const LESSON_DATA_OPEN = "<<<LESSON_DATA>>>";
const LESSON_DATA_CLOSE = "<<<END_LESSON_DATA>>>";

/**
 * Конфиг из окружения — или громкий отказ.
 *
 * Воркер без ключа не должен «просто подольше поспать»: молчаливый простой очереди выглядел бы
 * как зависшая проверка, а не как неразвёрнутый сервис.
 */
function readModelConfig(env = process.env) {
  const apiUrl = env.REVIEW_MODEL_API_URL || "";
  const apiKey = env.REVIEW_MODEL_API_KEY || "";
  const modelName = env.REVIEW_MODEL_NAME || "";
  if (!apiUrl || !apiKey || !modelName) {
    return {ok: false, reason: "REVIEW_MODEL_ENV_MISSING: REVIEW_MODEL_API_URL, REVIEW_MODEL_API_KEY and REVIEW_MODEL_NAME are required"};
  }
  return {ok: true, config: {apiUrl, apiKey, modelName}};
}

/** Тело урока как данные: JSON между разграничителями, без всяких исключений из правила. */
function wrapLessonData(lesson) {
  return `${LESSON_DATA_OPEN}${JSON.stringify(lesson)}${LESSON_DATA_CLOSE}`;
}

/**
 * Один судящий вызов: system+user промпты скилла этапа, ответ — строка содержимого.
 *
 * Транспорт инъецируется; отказ именованный: недоступность модели не превращается ни в отказ по
 * качеству, ни в молчаливый null.
 */
function createReviewModelClient({config, transport = defaultTransport, timeoutMs = REQUEST_TIMEOUT_MS}) {
  if (!config || !config.apiUrl || !config.apiKey || !config.modelName) {
    throw new TypeError("Review model client requires apiUrl, apiKey and modelName");
  }
  return {
    modelName: config.modelName,
    async judge({system, user, tools}) {
      let raw;
      try {
        const body = {
          model: config.modelName,
          messages: [
            {role: "system", content: system},
            {role: "user", content: user},
          ],
        };
        // Этап просит инструменты сам («Факты» — поиск); транспорт только доставляет запрос.
        if (Array.isArray(tools) && tools.length > 0) body.tools = tools;
        raw = await transport({
          url: config.apiUrl,
          apiKey: config.apiKey,
          timeoutMs,
          body,
        });
      } catch (error) {
        return {ok: false, code: "REVIEW_MODEL_UNAVAILABLE", message: String(error && error.message || error)};
      }
      const content = extractContent(raw);
      if (content === null) {
        return {ok: false, code: "REVIEW_MODEL_BAD_REPLY", message: "reply carries no message content"};
      }
      return {ok: true, content};
    },
  };
}

function extractContent(raw) {
  const choices = raw && Array.isArray(raw.choices) ? raw.choices : null;
  const message = choices && choices[0] && choices[0].message;
  const content = message && typeof message.content === "string" ? message.content : null;
  return content && content.trim() ? content : null;
}

async function defaultTransport({url, apiKey, timeoutMs, body}) {
  const response = await fetch(url, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      authorization: `Bearer ${apiKey}`,
    },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(timeoutMs),
  });
  if (!response.ok) {
    throw new Error(`REVIEW_MODEL_HTTP_${response.status}`);
  }
  return response.json();
}

module.exports = {
  LESSON_DATA_CLOSE,
  LESSON_DATA_OPEN,
  REQUEST_TIMEOUT_MS,
  createReviewModelClient,
  extractContent,
  readModelConfig,
  wrapLessonData,
};
