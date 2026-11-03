'use strict';

// ===========================================================================
// Full syllabus for the Technical English A0-C2 rewrite (quest-english-tech.v2).
// 7 levels x 4 themes x 5 lessons = 140 lessons. Section index: A0=1 .. C2=7.
// Each lesson: { title, focus } — focus is the teaching brief handed to the
// author agent. Theme 1 of every level is the GRAMMAR theme for that level;
// themes 2-4 are vocabulary / communication skills for that level's domain.
//
// Progress is tracked in quest-english-tech.v2.js, not here.
// (Authored so far: A1 / Theme 1 — all 5 lessons.)
// ===========================================================================

const L = (title, focus) => ({ title, focus });
const T = (title, lessons) => ({ title, lessons });
const LV = (code, section, title, themes) => ({ code, section, title, themes });

module.exports = {
  levels: [
    LV('A0', 1, 'A0 — Полный ноль: первые слова разработчика', [
      T('Буквы, символы и первые слова', [
        L('Symbols in paths and URLs', 'name / . - _ : in file paths and links'),
        L('The keyboard and its keys', 'Enter, Tab, Esc, Space, Shift and what they do'),
        L('First nouns: file, folder, code', 'the most basic objects a developer talks about'),
        L('Numbers, digits and versions', 'read digits and versions like 1.0.0, 2.3'),
        L('Saying hello in team chat', 'hi, hello, thanks, bye in a work chat'),
      ]),
      T('Объекты в приложении', [
        L('App, screen, page, window', 'name the largest visible parts of software'),
        L('Button, field, label, icon', 'name small UI elements on a screen'),
        L('File, folder, path', 'talk about where things are stored'),
        L('Menu, list, tab', 'name navigation elements'),
        L('Text, word, line of code', 'talk about the smallest pieces of content'),
      ]),
      T('Крошечные действия', [
        L('Click, tap, press', 'simplest pointer/touch actions'),
        L('Open, close, save', 'core file actions'),
        L('Copy, paste, cut', 'clipboard actions'),
        L('Read, write, delete', 'basic content actions'),
        L('Send, reply, ask', 'basic communication actions'),
      ]),
      T('Первые фразы на работе', [
        L('Yes, no, ok — short answers', 'minimal answers and confirmations'),
        L('Please and thank you', 'basic politeness at work'),
        L('I do not understand / can you help', 'asking for help with fixed phrases'),
        L('Time words: at, on, in', 'at 10, on Monday, in the morning'),
        L('It works / it does not work', 'reporting the simplest status'),
      ]),
    ]),

    LV('A1', 2, 'A1 — База: простые задачи и рабочие инструменты', [
      T('Грамматика для ежедневной работы', [
        L('Местоимения и глагол "be"', 'DONE — pronouns + am/is/are'),
        L('Present Simple для рабочих рутин', 'DONE — third-person -s, do/does'),
        L('There is / There are', 'DONE — existence, singular/plural'),
        L('Can / cannot (способность и разрешение)', 'DONE — modal can'),
        L('Артикли a / an / the с тех. существительными', 'DONE — articles by sound and reference'),
      ]),
      T('Инструменты разработчика', [
        L('Browser and tabs', 'browser, tab, address bar, link'),
        L('Terminal basics', 'terminal, command, prompt, output'),
        L('Repository and commits', 'repository, commit, message, history'),
        L('Branches and pull requests', 'branch, pull request, merge, review'),
        L('Files, folders and paths', 'create, move, rename files; relative paths'),
      ]),
      T('Командная работа', [
        L('Tasks and tickets', 'ticket, task, status, assignee'),
        L('Standups and meetings', 'standup, meeting, agenda, update'),
        L('Deadlines and priorities', 'deadline, priority, today, tomorrow'),
        L('Reading a short README', 'note, guide, example, instruction'),
        L('Asking for help in chat', 'polite questions and clear context in chat'),
      ]),
      T('Числа, время и статусы', [
        L('Versions and dates', 'read versions and dates; ordinal numbers'),
        L('Days, times and schedules', 'days of week, times, every day/week'),
        L('Status words', 'done, in progress, blocked, ready'),
        L('Short answers and agreement', 'yes/no answers, I agree, me too'),
        L('Polite requests', 'Could you, Please, Can you + verb'),
      ]),
    ]),

    LV('A2', 3, 'A2 — Уверенный старт: Git, API, тесты и обновления', [
      T('Грамматика: время, сравнение, количество', [
        L('Past Simple для завершённой работы', 'fixed, changed, created; regular/irregular'),
        L('Future: will и going to', 'will deploy, going to test; plans vs decisions'),
        L('Comparatives in tech choices', 'faster, safer, simpler; than'),
        L('Quantifiers for logs and data', 'many, much, a few, a little, some/any'),
        L('Present Continuous vs Simple', 'I am fixing now vs I fix every day'),
      ]),
      T('API, данные и тесты', [
        L('Endpoints and requests', 'endpoint, request, response, method'),
        L('JSON and fields', 'field, value, object, array'),
        L('Database rows and records', 'row, record, table, column'),
        L('Manual and automated tests', 'test case, expected vs actual result'),
        L('Status codes and responses', '200, 404, 500; success vs error'),
      ]),
      T('Обновления и совместная работа', [
        L('Status updates', 'done, in progress, next step; concise updates'),
        L('Blockers and questions', 'blocker, dependency, waiting on'),
        L('Estimates and scope', 'estimate, small/large task, scope'),
        L('Giving and receiving feedback', 'suggestion, comment, change request'),
        L('Writing a clear commit message', 'imperative mood, short summary, why'),
      ]),
      T('Прошлое и будущее задач', [
        L('Reporting what you did', 'yesterday I fixed / tested / reviewed'),
        L('Planning what you will do', 'today I will / I am going to'),
        L('Describing changes over time', 'before/after, used to, now'),
        L('Talking about deadlines', 'by Friday, until, on time, late'),
        L('Following up', 'any update on, just checking, gentle reminder'),
      ]),
    ]),

    LV('B1', 4, 'B1 — Средний уровень: архитектура, релизы и отладка', [
      T('Грамматика для объяснения причин', [
        L('Present Perfect для недавних изменений', 'has changed, have tested; just/already/yet'),
        L('First conditional для рисков', 'if it fails, we revert'),
        L('Modals: advice and obligation', 'should, must, have to, need to'),
        L('Passive voice in bug reports', 'was deployed, was fixed, is being reviewed'),
        L('Linking words for cause/result', 'because, so, although, however'),
      ]),
      T('Архитектура и платформы', [
        L('Frontend basics', 'frontend, component, layout, state'),
        L('Backend basics', 'backend, service, database, API'),
        L('Mobile client basics', 'mobile client, screen, permission, offline'),
        L('Architecture boundaries', 'module, layer, interface, dependency'),
        L('Databases and storage', 'query, index, cache, migration'),
      ]),
      T('Отладка и релизы', [
        L('Reading error messages', 'stack trace, exception, line number'),
        L('Reproducing bugs', 'steps to reproduce, environment, expected/actual'),
        L('Release notes', 'feature, fix, known issue, changelog'),
        L('Sprint planning', 'sprint goal, story, capacity, estimate'),
        L('Rollbacks and hotfixes', 'rollback, hotfix, revert, patch'),
      ]),
      T('Объяснение и обсуждение', [
        L('Explaining a cause', 'root cause, leads to, results in'),
        L('Describing a process step by step', 'first, then, after that, finally'),
        L('Comparing two solutions', 'on one hand, whereas, the difference is'),
        L('Agreeing and disagreeing politely', 'I see your point, but; I am not sure'),
        L('Summarizing a discussion', 'to sum up, the main point, we decided'),
      ]),
    ]),

    LV('B2', 5, 'B2 — Выше среднего: ревью, безопасность, производительность', [
      T('Точность и осторожная формулировка', [
        L('Hedging in technical claims', 'appears to, seems to, may indicate, likely'),
        L('Relative clauses in specs', 'which handles retries, that stores tokens'),
        L('Reported speech in reviews', 'the reviewer said that, noted that'),
        L('Second conditional for alternatives', 'if we cached it, we would'),
        L('Cause-effect connectors', 'therefore, as a result, consequently'),
      ]),
      T('Качественные атрибуты', [
        L('Performance bottlenecks', 'bottleneck, latency, throughput, profiling'),
        L('Security reviews', 'threat model, authentication, authorization'),
        L('Concurrency and race conditions', 'race condition, thread, lock, atomic'),
        L('CI/CD pipelines', 'pipeline, job, artifact, rollback'),
        L('Reliability and monitoring', 'uptime, alert, metric, dashboard'),
      ]),
      T('Профессиональная коммуникация', [
        L('Pull request reviews', 'nit, blocking, suggestion, approval'),
        L('Design documents', 'context, trade-off, decision, alternatives'),
        L('Incident updates', 'impact, mitigation, root cause, timeline'),
        L('Stakeholder summaries', 'summary, risk, timeline for non-specialists'),
        L('Writing constructive feedback', 'specific, actionable, kind but honest'),
      ]),
      T('Аргументация', [
        L('Making a recommendation', 'I recommend, the best option is, because'),
        L('Weighing trade-offs', 'pros and cons, on balance, the cost is'),
        L('Justifying a decision', 'we chose X because, the rationale is'),
        L('Raising a concern diplomatically', 'one concern is, have we considered'),
        L('Responding to criticism', 'fair point, let me clarify, you are right that'),
      ]),
    ]),

    LV('C1', 6, 'C1 — Продвинутый: RFC, распределённые системы и влияние', [
      T('Стиль, связность и позиция автора', [
        L('Nominalization in architecture prose', 'migration, validation, deprecation, adoption'),
        L('Cohesion across paragraphs', 'however, moreover, nevertheless, in contrast'),
        L('Register in technical disagreement', 'I would challenge, I am not convinced'),
        L('Precision with assumptions', 'assumption, constraint, dependency, unknown'),
        L('Emphasis and focus structures', 'it is X that, what matters is, cleft sentences'),
      ]),
      T('Продвинутая системная лексика', [
        L('Distributed systems', 'eventual consistency, replica, partition, consensus'),
        L('Observability', 'trace, metric, log, span'),
        L('Privacy and data protection', 'data minimization, consent, retention'),
        L('API contracts and versioning', 'backward compatibility, schema, breaking change'),
        L('Scalability and capacity', 'horizontal scaling, load, capacity, headroom'),
      ]),
      T('Лидерская коммуникация', [
        L('Writing RFCs', 'motivation, alternatives, open questions'),
        L('Mentoring through code review', 'rationale, example, next step, learning moment'),
        L('Negotiating scope', 'must-have, nice-to-have, trade-off, deadline'),
        L('Cross-team alignment', 'owner, dependency, decision log, alignment'),
        L('Influencing without authority', 'framing, shared goals, evidence, buy-in'),
      ]),
      T('Сложная аргументация', [
        L('Structuring a long argument', 'thesis, support, structure, signposting'),
        L('Anticipating objections', 'some might argue, a common concern, while'),
        L('Conceding and rebutting', 'admittedly, even so, nonetheless'),
        L('Framing for different audiences', 'for engineers vs for leadership'),
        L('Driving consensus', 'common ground, proposal, next action, agreement'),
      ]),
    ]),

    LV('C2', 7, 'C2 — Мастерство: стратегия, критика и высокие ставки', [
      T('Риторика и смысловая точность', [
        L('Nuanced, qualified claims', 'qualified claim, caveat, evidence, scope'),
        L('Ambiguity and interpretation', 'ambiguity, interpretation, edge case'),
        L('Legal and compliance language', 'shall, must, may, prohibited, liability'),
        L('Strategic technical framing', 'optionality, cost of delay, leverage'),
        L('Tone and implication', 'understatement, implication, diplomatic phrasing'),
      ]),
      T('Экспертные технические домены', [
        L('Compiler and runtime trade-offs', 'runtime overhead, allocation, inlining'),
        L('Reliability engineering', 'error budget, SLO, availability, degradation'),
        L('AI-assisted development', 'hallucination, prompt, grounding, verification'),
        L('Platform governance', 'governance model, policy, exception, review board'),
        L('Cost and efficiency at scale', 'unit economics, efficiency, waste, budget'),
      ]),
      T('Коммуникация высоких ставок', [
        L('Executive technical briefs', 'executive summary, recommendation, risk'),
        L('Critiquing technical papers', 'methodology, validity, sample, limitation'),
        L('Cross-org conflict resolution', 'decision rationale, escalation path'),
        L('Blameless postmortems', 'timeline, contributing factor, follow-up action'),
        L('Crisis communication', 'clear, calm, accurate updates under pressure'),
      ]),
      T('Мастерство аргументации', [
        L('Persuasive executive writing', 'lead with the point, evidence, ask'),
        L('Defending a position under scrutiny', 'hold ground, concede gracefully, evidence'),
        L('Synthesizing opposing views', 'reconcile, integrate, the underlying tension'),
        L('Precision under ambiguity', 'name the unknown, state assumptions, scope'),
        L('Writing for posterity', 'decision records that read well in a year'),
      ]),
    ]),
  ],
};
