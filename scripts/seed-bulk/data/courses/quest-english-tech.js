'use strict';

// Technical English course for developers, from absolute zero to C2.
// The seed runner accepts executable JS data modules, so this file generates
// a large Firestore-ready hierarchy while keeping the source reviewable.

const OPTION_IDS = ['a', 'b', 'c', 'd', 'e', 'f'];

const LEVEL_GRAMMAR = {
  A0: {
    correct: (term) => `I can say "${term}" in English.`,
    wrong: (term) => [
      `I can says "${term}" in English.`,
      `I am can say "${term}" in English.`,
      `I say can "${term}" in English.`,
    ],
    fill: {
      text: 'I ___ learning English for developers.',
      answer: 'am',
      candidates: ['am', 'is', 'are', 'be', 'do'],
      info: 'With "I" in the present continuous, use "am": I am learning.',
    },
  },
  A1: {
    correct: (term) => `Our team uses "${term}" every day.`,
    wrong: (term) => [
      `Our team use "${term}" every day.`,
      `Our team using "${term}" every day.`,
      `Our team is use "${term}" every day.`,
    ],
    fill: {
      text: 'She ___ the ticket before lunch.',
      answer: 'checks',
      candidates: ['checks', 'check', 'checking', 'checked', 'to check'],
      info: 'Present Simple with he/she/it takes -s: she checks.',
    },
  },
  A2: {
    correct: (term) => `Yesterday, I used "${term}" to finish the task.`,
    wrong: (term) => [
      `Yesterday, I use "${term}" to finish the task.`,
      `Yesterday, I have use "${term}" to finish the task.`,
      `Yesterday, I using "${term}" to finish the task.`,
    ],
    fill: {
      text: 'Tomorrow we ___ test the new build.',
      answer: 'will',
      candidates: ['will', 'did', 'was', 'has', 'being'],
      info: 'Use "will" for a simple future plan or decision.',
    },
  },
  B1: {
    correct: (term) => `We have already documented how "${term}" works.`,
    wrong: (term) => [
      `We already documented how "${term}" works yesterday and have now.`,
      `We have already document how "${term}" works.`,
      `We have already documenting how "${term}" works.`,
    ],
    fill: {
      text: 'If the test fails, we ___ the change.',
      answer: 'revert',
      candidates: ['revert', 'reverted', 'reverting', 'to revert', 'reverts'],
      info: 'In the first conditional, use present simple after "if" and base verb after "will/should/can"; here the sentence means "we revert".',
    },
  },
  B2: {
    correct: (term) => `The change that affects "${term}" should be reviewed carefully.`,
    wrong: (term) => [
      `The change which it affects "${term}" should be reviewed carefully.`,
      `The change affects "${term}" should reviewed carefully.`,
      `The change that affects "${term}" should reviewing carefully.`,
    ],
    fill: {
      text: 'The issue was fixed ___ the root cause was confirmed.',
      answer: 'after',
      candidates: ['after', 'although', 'unless', 'despite', 'during'],
      info: 'Use "after" to show that confirmation happened first, then the fix.',
    },
  },
  C1: {
    correct: (term) => `The document explains the trade-offs associated with "${term}".`,
    wrong: (term) => [
      `The document explains about the trade-offs associated with "${term}".`,
      `The document explain the trade-offs associated with "${term}".`,
      `The document explains the trade-offs what associated with "${term}".`,
    ],
    fill: {
      text: 'The proposal is persuasive, ___ it understates the migration risk.',
      answer: 'although',
      candidates: ['although', 'because', 'therefore', 'unless', 'beside'],
      info: 'Use "although" to introduce a contrast or reservation.',
    },
  },
  C2: {
    correct: (term) => `The argument distinguishes "${term}" from adjacent but materially different concerns.`,
    wrong: (term) => [
      `The argument distinguishes "${term}" and adjacent but materially different concerns.`,
      `The argument is distinguish "${term}" from adjacent concerns.`,
      `The argument distinguishes between "${term}" from adjacent concerns.`,
    ],
    fill: {
      text: 'The memo is concise, but it leaves one assumption ___ stated.',
      answer: 'insufficiently',
      candidates: ['insufficiently', 'insufficient', 'insufficiency', 'insufficientness', 'insufficiented'],
      info: 'An adverb is needed before "stated": insufficiently stated.',
    },
  },
};

function options(texts) {
  return texts.map((text, index) => ({ id: OPTION_IDS[index], text }));
}

function candidates(texts) {
  return texts.map((text, index) => ({ id: `c${index + 1}`, text }));
}

function rotate(items, amount) {
  const shift = amount % items.length;
  return items.slice(shift).concat(items.slice(0, shift));
}

function sc(difficulty, text, optionTexts, correctText, info, seed) {
  const rotated = rotate(optionTexts, seed);
  const opts = options(rotated);
  const correct = opts.find((option) => option.text === correctText);
  if (!correct) throw new Error(`SingleChoice correct option not found: ${correctText}`);
  return {
    type: 'SingleChoice',
    difficulty,
    text,
    imageUrl: null,
    options: opts,
    correctOptionId: correct.id,
    info,
  };
}

function mc(difficulty, text, correctTexts, distractors, info) {
  const opts = options([...correctTexts, ...distractors]);
  return {
    type: 'MultipleChoice',
    difficulty,
    text,
    imageUrl: null,
    options: opts,
    correctOptionIds: opts.slice(0, correctTexts.length).map((option) => option.id),
    info,
  };
}

function ord(difficulty, text, itemTexts, info) {
  return {
    type: 'Ordering',
    difficulty,
    text,
    imageUrl: null,
    items: itemTexts.map((item, index) => ({ id: `i${index + 1}`, text: item })),
    info,
  };
}

function fb(difficulty, text, answer, candidateTexts, info) {
  const allCandidates = [answer, ...candidateTexts.filter((candidate) => candidate !== answer)].slice(0, 5);
  if (allCandidates.length !== 5) {
    throw new Error(`FillBlank must have 5 candidates for "${text}"`);
  }
  return {
    type: 'FillBlank',
    difficulty,
    text,
    imageUrl: null,
    blanks: [{ id: 'b1', correctCandidateId: 'c1' }],
    candidates: candidates(allCandidates),
    info,
  };
}

function L(title, focus, term, meaning, related, distractors) {
  if (related.length !== 3) throw new Error(`Lesson "${title}" must have 3 related terms`);
  if (distractors.length !== 3) throw new Error(`Lesson "${title}" must have 3 distractors`);
  return { title, focus, term, meaning, related, distractors };
}

function theme(title, lessons) {
  return { title, lessons };
}

function level(code, title, themes) {
  return { code, title, themes, grammar: LEVEL_GRAMMAR[code] };
}

const LEVELS = [
  level('A0', 'A0 - Полный ноль: первые слова разработчика', [
    theme('Буквы, символы и первые фразы', [
      L('Keyboard symbols and file names', 'name symbols in paths, URLs, and file names', 'slash', 'the / symbol used in paths and URLs', ['dot', 'dash', 'underscore'], ['sandwich', 'airport', 'umbrella']),
      L('IDE buttons and tiny commands', 'recognize basic IDE buttons and commands', 'Run', 'the command that starts a program', ['Save', 'Open', 'Close'], ['kitchen', 'ticket price', 'weather']),
      L('Simple greetings in team chat', 'start and end a short work chat politely', 'Hi team', 'a friendly greeting for a work chat', ['please', 'thanks', 'bye'], ['compile error', 'database', 'invoice']),
      L('Numbers, versions, and dates', 'read numbers, versions, and simple dates', 'version', 'a named state of software, such as 1.0.0', ['number', 'date', 'release'], ['chair', 'window', 'coffee']),
    ]),
    theme('Objects in an app', [
      L('Files and folders', 'talk about project files and folders', 'folder', 'a place that contains files', ['file', 'path', 'project'], ['spoon', 'hotel', 'river']),
      L('Apps and screens', 'name visible parts of an application', 'screen', 'one view or page in an app', ['app', 'page', 'window'], ['banana', 'train', 'salary']),
      L('Buttons, fields, and labels', 'recognize simple UI words', 'button', 'a UI element that performs an action', ['field', 'label', 'icon'], ['keyboard noise', 'printer ink', 'airport gate']),
      L('Basic code words', 'identify the first words used around code', 'code', 'instructions written for a computer', ['line', 'word', 'text'], ['breakfast', 'mountain', 'wallet']),
    ]),
    theme('Tiny actions', [
      L('Click, tap, and type', 'describe simple UI actions', 'click', 'press a button or link with a mouse', ['tap', 'type', 'select'], ['sleep', 'cook', 'swim']),
      L('Read, copy, and paste', 'follow tiny text-editing actions', 'copy', 'put selected text into the clipboard', ['read', 'paste', 'delete'], ['garden', 'ticket office', 'cloudy']),
      L('Open, close, and save', 'use the smallest workflow verbs', 'save', 'store the current change', ['open', 'close', 'load'], ['borrow', 'paint', 'dance']),
      L('Send a short message', 'write one-line work messages', 'send', 'deliver a message to another person or system', ['reply', 'ask', 'tell'], ['freeze', 'bake', 'measure rain']),
    ]),
  ]),
  level('A1', 'A1 - База: простые задачи и рабочие инструменты', [
    theme('Грамматика для ежедневной работы', [
      L('Pronouns and the verb be', 'describe yourself and teammates', 'I am', 'a simple way to introduce your role or state', ['you are', 'he is', 'we are'], ['we was', 'they is', 'I be']),
      L('Present Simple for routines', 'describe regular developer actions', 'I use', 'a Present Simple phrase for a regular action', ['we write', 'they test', 'she checks'], ['I using', 'we writes', 'she check']),
      L('There is and there are', 'describe what exists in a project', 'there is', 'a phrase for one existing thing', ['there are', 'one file', 'two folders'], ['there have', 'there be', 'there were one']),
      L('Can and cannot', 'talk about ability and permissions', 'can', 'a modal verb for ability or permission', ['cannot', 'could', 'may'], ['cans', 'to can', 'can to']),
    ]),
    theme('Developer tools', [
      L('Browser and tabs', 'talk about web pages and browser tabs', 'browser', 'an app used to open web pages', ['tab', 'address bar', 'link'], ['kettle', 'receipt', 'pillow']),
      L('Terminal basics', 'recognize basic command-line words', 'terminal', 'a text interface for running commands', ['command', 'prompt', 'output'], ['supermarket', 'bottle', 'passport']),
      L('Repositories and commits', 'name the smallest Git concepts', 'repository', 'a project history and its files', ['commit', 'branch', 'remote'], ['sand', 'elevator', 'calendar']),
      L('Branches and pull requests', 'describe simple collaboration in Git', 'pull request', 'a request to review and merge changes', ['branch', 'merge', 'review'], ['dessert', 'garage', 'umbrella']),
    ]),
    theme('Team basics', [
      L('Tasks and tickets', 'understand simple work items', 'ticket', 'a written task or issue in a tracker', ['task', 'status', 'assignee'], ['fork', 'bedroom', 'museum']),
      L('Meetings and standups', 'say what happens in short team meetings', 'standup', 'a short meeting for team updates', ['meeting', 'update', 'agenda'], ['wardrobe', 'recipe', 'island']),
      L('Deadlines and priorities', 'talk about time and importance', 'deadline', 'the time by which work should be finished', ['priority', 'today', 'tomorrow'], ['cup', 'forest', 'camera']),
      L('Simple documentation', 'read short instructions and notes', 'README', 'a document that explains a project', ['note', 'guide', 'example'], ['curtain', 'sandwich', 'taxi']),
    ]),
  ]),
  level('A2', 'A2 - Уверенный старт: Git, API, тесты и обновления', [
    theme('Время, сравнение и количество', [
      L('Past Simple for completed work', 'report finished tasks', 'fixed', 'completed a bug repair in the past', ['changed', 'created', 'updated'], ['fixing now', 'will fix', 'fixes tomorrow']),
      L('Future plans with will and going to', 'describe planned technical work', 'will deploy', 'a future action to publish software', ['going to test', 'will review', 'will update'], ['deployed yesterday', 'deploys now', 'deploying always']),
      L('Comparatives in technical choices', 'compare tools and solutions', 'faster', 'having more speed than another option', ['slower', 'safer', 'simpler'], ['fastest of two', 'more fast', 'fastly']),
      L('Quantifiers for logs and data', 'talk about amount in technical contexts', 'many logs', 'a large count of log entries', ['some data', 'a few errors', 'much traffic'], ['many traffic', 'few information', 'a data']),
    ]),
    theme('API, data, and testing basics', [
      L('Endpoints and requests', 'describe a simple API call', 'endpoint', 'a URL where an API receives a request', ['request', 'response', 'method'], ['keyboard', 'invoice', 'blanket']),
      L('JSON and fields', 'name simple data structures', 'field', 'a named piece of data in an object', ['value', 'object', 'array'], ['balcony', 'dessert', 'train']),
      L('Database rows and records', 'talk about stored data', 'record', 'one stored item in a database', ['row', 'table', 'column'], ['helmet', 'holiday', 'spoon']),
      L('Manual and automated tests', 'describe basic testing work', 'test case', 'a check that verifies behavior', ['expected result', 'actual result', 'bug'], ['hotel', 'bicycle', 'recipe']),
    ]),
    theme('Updates and collaboration', [
      L('Status updates', 'write short progress messages', 'status update', 'a short message about current progress', ['done', 'in progress', 'next step'], ['plate', 'cloud', 'forest']),
      L('Blockers and questions', 'explain why work is paused', 'blocker', 'a problem that prevents progress', ['question', 'dependency', 'waiting'], ['breakfast', 'painting', 'river']),
      L('Estimates and scope', 'talk about size and time', 'estimate', 'an approximate amount of time or effort', ['scope', 'small task', 'large task'], ['passport', 'cheese', 'window']),
      L('Giving and receiving feedback', 'respond politely to review comments', 'feedback', 'comments that help improve work', ['suggestion', 'comment', 'change request'], ['airport', 'shirt', 'moon']),
    ]),
  ]),
  level('B1', 'B1 - Средний уровень: архитектура, релизы и отладка', [
    theme('Грамматика для объяснения причин', [
      L('Present Perfect for recent changes', 'connect past work to the current state', 'has changed', 'describes a change relevant now', ['has fixed', 'have tested', 'has failed'], ['changed tomorrow', 'has change', 'is changed yesterday']),
      L('First conditional for risk', 'describe likely technical outcomes', 'if it fails', 'a condition for a possible failure', ['if it passes', 'if we deploy', 'if users report'], ['if it failed yesterday', 'if it will fails', 'if it failing']),
      L('Modals for advice and obligation', 'discuss what should or must happen', 'should review', 'advises a review without making it absolute', ['must fix', 'can postpone', 'might break'], ['should to review', 'must to fix', 'can reviews']),
      L('Passive voice in bug reports', 'focus on actions rather than people', 'was deployed', 'says that something was published', ['was tested', 'was merged', 'was reported'], ['deployed was', 'was deploy', 'is deployed yesterday']),
    ]),
    theme('Architecture and platforms', [
      L('Frontend basics', 'explain user-facing code', 'frontend', 'the part of an app users see and interact with', ['component', 'layout', 'state'], ['kitchen', 'passport', 'garden']),
      L('Backend basics', 'explain server-side code', 'backend', 'the part of a system that runs on servers', ['service', 'database', 'API'], ['balcony', 'sandwich', 'theater']),
      L('Mobile app basics', 'talk about Android and iOS features', 'mobile client', 'an app running on a phone or tablet', ['screen', 'permission', 'offline mode'], ['fork', 'island', 'umbrella']),
      L('Architecture boundaries', 'describe separation of responsibilities', 'module boundary', 'a line that separates responsibilities between modules', ['dependency', 'interface', 'layer'], ['dessert', 'airport', 'pillow']),
    ]),
    theme('Debugging and releases', [
      L('Reading error messages', 'extract meaning from errors', 'stack trace', 'a list of calls that led to an error', ['exception', 'line number', 'message'], ['coffee', 'receipt', 'elevator']),
      L('Reproducing bugs', 'explain steps that show a problem', 'steps to reproduce', 'instructions that make a bug happen again', ['actual result', 'expected result', 'environment'], ['train', 'wallet', 'chair']),
      L('Release notes', 'summarize user-visible changes', 'release notes', 'a summary of changes in a release', ['feature', 'fix', 'known issue'], ['recipe', 'holiday', 'curtain']),
      L('Sprint planning', 'discuss work in iterations', 'sprint goal', 'the main objective of a short work cycle', ['story', 'capacity', 'priority'], ['river', 'bottle', 'camera']),
    ]),
  ]),
  level('B2', 'B2 - Выше среднего: ревью, безопасность, производительность', [
    theme('Точность и осторожная формулировка', [
      L('Hedging in technical claims', 'avoid overclaiming when evidence is incomplete', 'appears to', 'signals a likely but not certain conclusion', ['seems to', 'is likely to', 'may indicate'], ['always proves', 'never means', 'guarantees']),
      L('Relative clauses in specs', 'attach precise detail to nouns', 'which handles retries', 'adds information about a component or service', ['that stores tokens', 'which validates input', 'that renders the page'], ['which it handles', 'what handles', 'who handles service']),
      L('Reported speech in reviews', 'summarize another person accurately', 'the reviewer said that', 'introduces someone else\'s comment', ['the tester noted that', 'the user reported that', 'the lead suggested that'], ['the reviewer said me', 'the tester told that', 'the lead suggested me']),
      L('Second conditional for alternatives', 'discuss hypothetical technical choices', 'if we cached it', 'introduces an imagined alternative', ['if we split the module', 'if we delayed the release', 'if we changed the API'], ['if we will cache it', 'if we cached it will', 'if we cache yesterday']),
    ]),
    theme('Quality attributes', [
      L('Performance bottlenecks', 'describe slow parts of a system', 'bottleneck', 'the part that limits overall speed', ['latency', 'throughput', 'profiling'], ['sandwich', 'balcony', 'receipt']),
      L('Security reviews', 'talk about protecting systems and users', 'threat model', 'a structured view of possible attacks', ['authentication', 'authorization', 'input validation'], ['dessert', 'train', 'umbrella']),
      L('Concurrency and race conditions', 'explain work happening at the same time', 'race condition', 'a bug caused by unexpected timing between operations', ['thread', 'lock', 'atomic'], ['museum', 'pillow', 'holiday']),
      L('CI and deployment pipelines', 'describe automated checks and releases', 'pipeline', 'a sequence of automated build and release steps', ['job', 'artifact', 'rollback'], ['kettle', 'window', 'shirt']),
    ]),
    theme('Professional communication', [
      L('Pull request reviews', 'write useful review comments', 'nit', 'a small non-blocking review comment', ['blocking', 'suggestion', 'approval'], ['airport', 'camera', 'forest']),
      L('Design documents', 'structure technical proposals', 'design doc', 'a document that explains a proposed solution', ['context', 'trade-off', 'decision'], ['invoice', 'chair', 'river']),
      L('Incident updates', 'communicate during production issues', 'incident', 'an event that harms service quality', ['impact', 'mitigation', 'root cause'], ['recipe', 'passport', 'bicycle']),
      L('Stakeholder summaries', 'translate technical detail for non-specialists', 'stakeholder', 'a person affected by a project or decision', ['summary', 'risk', 'timeline'], ['fork', 'balcony', 'sand']),
    ]),
  ]),
  level('C1', 'C1 - Продвинутый: RFC, распределённые системы и влияние', [
    theme('Стиль, связность и позиция автора', [
      L('Nominalization in architecture prose', 'turn actions into precise abstract nouns', 'migration', 'the process of moving from one system to another', ['validation', 'deprecation', 'adoption'], ['migratingly', 'migratedness', 'migrate to']),
      L('Cohesion across paragraphs', 'connect arguments smoothly', 'therefore', 'marks a conclusion from previous evidence', ['however', 'moreover', 'nevertheless'], ['because of', 'despite of', 'in spite']),
      L('Register in technical disagreement', 'disagree without sounding hostile', 'I would challenge', 'a polite way to question an assumption', ['I am not convinced', 'one concern is', 'could we revisit'], ['you are wrong', 'this is nonsense', 'obviously bad']),
      L('Precision with assumptions', 'state what must be true for a plan to work', 'assumption', 'something treated as true for planning', ['constraint', 'dependency', 'unknown'], ['certainty', 'randomness', 'decoration']),
    ]),
    theme('Advanced systems vocabulary', [
      L('Distributed systems', 'discuss systems split across machines', 'eventual consistency', 'a model where replicas become consistent over time', ['replica', 'partition', 'consensus'], ['umbrella', 'invoice', 'kitchen']),
      L('Observability', 'explain how systems are monitored', 'trace', 'a record of a request across services', ['metric', 'log', 'span'], ['museum', 'dessert', 'passport']),
      L('Privacy and data protection', 'describe sensitive data controls', 'data minimization', 'collecting only the data that is needed', ['consent', 'retention', 'anonymization'], ['sandwich', 'train', 'shirt']),
      L('API contracts', 'define stable interfaces between systems', 'backward compatibility', 'support for older clients after a change', ['schema', 'versioning', 'breaking change'], ['pillow', 'airport', 'receipt']),
    ]),
    theme('Leadership communication', [
      L('Writing RFCs', 'make proposals that invite critique', 'RFC', 'a request for comments on a technical proposal', ['motivation', 'alternatives', 'open questions'], ['recipe', 'holiday', 'window']),
      L('Mentoring through code review', 'teach without taking over', 'learning moment', 'a comment that explains a principle, not only a fix', ['example', 'rationale', 'next step'], ['fork', 'garden', 'camera']),
      L('Negotiating scope', 'protect delivery while preserving trust', 'scope trade-off', 'a choice between included work and delivery constraints', ['must-have', 'nice-to-have', 'deadline'], ['river', 'coffee', 'helmet']),
      L('Cross-team alignment', 'coordinate decisions across teams', 'alignment', 'shared understanding about direction and constraints', ['owner', 'dependency', 'decision log'], ['bottle', 'curtain', 'island']),
    ]),
  ]),
  level('C2', 'C2 - Мастерство: стратегия, критика и высокие ставки', [
    theme('Риторика и смысловая точность', [
      L('Nuanced claims in executive writing', 'make strong claims without hiding uncertainty', 'qualified claim', 'a claim limited by evidence, scope, or assumptions', ['caveat', 'evidence', 'scope'], ['always true', 'empty praise', 'random guess']),
      L('Ambiguity and interpretation', 'identify meanings that could be misunderstood', 'ambiguity', 'a phrase or requirement with more than one plausible meaning', ['interpretation', 'edge case', 'disambiguation'], ['certainty', 'button color', 'keyboard']),
      L('Legal and compliance language', 'read obligations and limitations carefully', 'shall', 'a formal word used for obligation in contracts or rules', ['must', 'may', 'prohibited'], ['maybe always', 'can to', 'shoulded']),
      L('Strategic technical framing', 'connect engineering choices to business outcomes', 'strategic leverage', 'a technical choice that improves future options', ['optionality', 'cost of delay', 'operational risk'], ['sandwich', 'holiday', 'receipt']),
    ]),
    theme('Expert technical domains', [
      L('Compiler and runtime discussions', 'talk about low-level execution trade-offs', 'runtime overhead', 'extra cost while a program is running', ['allocation', 'optimization', 'inlining'], ['airport', 'pillow', 'forest']),
      L('Reliability engineering', 'discuss failure modes and resilience', 'error budget', 'the allowed amount of unreliability for a service', ['SLO', 'availability', 'degradation'], ['kitchen', 'wallet', 'umbrella']),
      L('AI-assisted development', 'evaluate AI outputs responsibly', 'hallucination', 'an AI output that sounds plausible but is false', ['prompt', 'verification', 'grounding'], ['bicycle', 'dessert', 'curtain']),
      L('Platform governance', 'set rules for shared engineering systems', 'governance model', 'the way decisions and ownership are managed', ['policy', 'exception', 'review board'], ['spoon', 'museum', 'train']),
    ]),
    theme('High-stakes communication', [
      L('Executive technical briefs', 'compress complex engineering issues for leaders', 'executive summary', 'a concise decision-oriented overview', ['recommendation', 'risk', 'next action'], ['recipe', 'window', 'garden']),
      L('Critiquing technical papers', 'evaluate claims, methods, and evidence', 'methodology', 'the approach used to produce evidence', ['validity', 'sample', 'limitation'], ['fork', 'hotel', 'camera']),
      L('Cross-org conflict resolution', 'resolve disagreement across groups', 'decision rationale', 'the reason behind a chosen option', ['trade-off', 'escalation path', 'shared constraint'], ['sand', 'shirt', 'elevator']),
      L('Postmortems without blame', 'analyze incidents rigorously and fairly', 'blameless postmortem', 'an incident review focused on systems and learning', ['timeline', 'contributing factor', 'follow-up action'], ['passport', 'coffee', 'island']),
    ]),
  ]),
];

const TARGET_THEME_COUNTS = {
  A0: 6,
  A1: 10,
  A2: 12,
  B1: 13,
  B2: 15,
  C1: 17,
  C2: 17,
};

const LESSONS_PER_THEME = 12;

const MODE_TEMPLATES = [
  { title: 'recognition', verb: 'recognize' },
  { title: 'controlled practice', verb: 'use' },
  { title: 'workplace reading', verb: 'understand' },
  { title: 'chat response', verb: 'answer with' },
  { title: 'ticket update', verb: 'write about' },
  { title: 'review comment', verb: 'review' },
  { title: 'debugging note', verb: 'explain' },
  { title: 'standup summary', verb: 'summarize' },
  { title: 'error report', verb: 'report' },
  { title: 'planning discussion', verb: 'discuss' },
  { title: 'decision note', verb: 'justify' },
  { title: 'spaced review', verb: 'recall and reuse' },
];

const LEVEL_SCENARIOS = {
  A0: [
    'a first day in an IDE',
    'a file path on screen',
    'a very short team chat',
    'a button in a mobile app',
    'a browser tab',
    'a copied command',
  ],
  A1: [
    'a daily routine',
    'a simple ticket',
    'a repository page',
    'a pull request checklist',
    'a standup update',
    'a short README',
  ],
  A2: [
    'an API request',
    'a JSON response',
    'a database table',
    'a test case',
    'a blocker update',
    'a small estimate',
  ],
  B1: [
    'a release note',
    'a bug reproduction flow',
    'a stack trace investigation',
    'a module boundary discussion',
    'a backend change',
    'a mobile client issue',
  ],
  B2: [
    'a security review',
    'a performance investigation',
    'a CI pipeline failure',
    'a concurrency bug',
    'an incident update',
    'a stakeholder summary',
  ],
  C1: [
    'an RFC',
    'a design review',
    'a distributed systems trade-off',
    'an observability proposal',
    'a privacy decision',
    'a cross-team alignment meeting',
  ],
  C2: [
    'an executive technical brief',
    'a reliability strategy',
    'a compliance constraint',
    'an AI-assisted development review',
    'a platform governance decision',
    'a blameless postmortem',
  ],
};

const SUPPORT_TERMS = [
  'context',
  'evidence',
  'example',
  'risk',
  'impact',
  'next step',
  'constraint',
  'assumption',
  'trade-off',
  'owner',
  'timeline',
  'follow-up',
];

function unique(items) {
  return [...new Set(items.filter(Boolean))];
}

function pickScenario(levelCode, themeIndex, lessonIndex) {
  const scenarios = LEVEL_SCENARIOS[levelCode];
  return scenarios[(themeIndex + lessonIndex) % scenarios.length];
}

function expandLesson(levelSpec, baseLesson, themeIndex, lessonIndex) {
  const mode = MODE_TEMPLATES[(themeIndex + lessonIndex) % MODE_TEMPLATES.length];
  const scenario = pickScenario(levelSpec.code, themeIndex, lessonIndex);
  const termPool = unique([baseLesson.term, ...baseLesson.related]);
  const term = termPool[(themeIndex + lessonIndex) % termPool.length];
  const related = unique([
    ...termPool.filter((item) => item !== term),
    ...SUPPORT_TERMS,
  ]).slice(0, 3);
  const primaryMeaning =
    term === baseLesson.term
      ? baseLesson.meaning
      : `a related technical term used with "${baseLesson.term}"`;
  return L(
    `${baseLesson.title} — ${mode.title}`,
    `${mode.verb} ${baseLesson.focus} in ${scenario}`,
    term,
    `${primaryMeaning}; practice it in ${scenario}`,
    related,
    baseLesson.distractors,
  );
}

function expandThemes(levelSpec) {
  const targetThemeCount = TARGET_THEME_COUNTS[levelSpec.code];
  if (!targetThemeCount) throw new Error(`No target theme count for ${levelSpec.code}`);

  return Array.from({ length: targetThemeCount }, (_, themeIndexZero) => {
    const baseTheme = levelSpec.themes[themeIndexZero % levelSpec.themes.length];
    const cycle = Math.floor(themeIndexZero / levelSpec.themes.length) + 1;
    const title = `${baseTheme.title} — модуль ${String(cycle).padStart(2, '0')}`;
    const lessons = Array.from({ length: LESSONS_PER_THEME }, (_, lessonIndexZero) => {
      const baseLesson = baseTheme.lessons[lessonIndexZero % baseTheme.lessons.length];
      return expandLesson(levelSpec, baseLesson, themeIndexZero, lessonIndexZero);
    });
    return theme(title, lessons);
  });
}

const REGISTER_DISTRACTORS = [
  'Write: "Fix it now!!!"',
  'Write: "This is broken because someone was careless."',
  'Write: "No idea, not my problem."',
];

const ACTIONS = {
  correct: ['review the change', 'document the decision', 'ask a clarifying question'],
  distractors: ['hide the error', 'guess the password', 'ignore the failing test'],
};

function makeQuestions(levelSpec, lesson, seed) {
  const grammar = levelSpec.grammar;
  const termOptions = [
    lesson.meaning,
    `a place where people buy food`,
    `a physical object unrelated to software`,
    `a calendar event with no technical meaning`,
  ];
  const grammarCorrect = grammar.correct(lesson.term);
  const grammarOptions = [grammarCorrect, ...grammar.wrong(lesson.term)];
  const registerCorrect = `Write: "Could you clarify ${lesson.term} in this context?"`;
  const registerOptions = [
    registerCorrect,
    ...REGISTER_DISTRACTORS,
  ];
  const grammarFill = grammar.fill;

  return [
    sc(
      'EASY',
      `In developer English, what does "${lesson.term}" mean in ${lesson.focus}?`,
      termOptions,
      lesson.meaning,
      `"${lesson.term}" is used here to talk about ${lesson.focus}.`,
      seed,
    ),
    sc(
      'EASY',
      `Choose the grammatically correct sentence with "${lesson.term}".`,
      grammarOptions,
      grammarCorrect,
      'The correct option uses the grammar pattern for this level.',
      seed + 1,
    ),
    sc(
      'EASY',
      `Which message is the most professional when "${lesson.term}" is unclear?`,
      registerOptions,
      registerCorrect,
      'Professional technical English asks for clarification without blame or drama.',
      seed + 2,
    ),
    sc(
      'EASY',
      `Which learning outcome best matches "${lesson.title}"?`,
      [
        `Use "${lesson.term}" when you need to ${lesson.focus}.`,
        `Memorize a random travel phrase unrelated to work.`,
        `Avoid asking questions in technical discussions.`,
        `Use emotional language instead of evidence.`,
      ],
      `Use "${lesson.term}" when you need to ${lesson.focus}.`,
      'The lesson outcome connects the target term with a real developer task.',
      seed + 3,
    ),
    sc(
      'EASY',
      'Which sentence is calm and precise?',
      [
        `I can reproduce the issue and will check ${lesson.related[0]}.`,
        'Everything is terrible and nobody knows anything.',
        'It is broken because it is broken.',
        'Maybe maybe maybe, no details.',
      ],
      `I can reproduce the issue and will check ${lesson.related[0]}.`,
      'A useful technical sentence gives an observation and a next action.',
      seed + 4,
    ),
    sc(
      'EASY',
      `Which sentence uses evidence with "${lesson.term}"?`,
      [
        `The logs show that "${lesson.term}" changed after the last release.`,
        `"${lesson.term}" is bad because I feel so.`,
        `Nobody likes "${lesson.term}", so ignore it.`,
        `Maybe "${lesson.term}" happened somewhere.`,
      ],
      `The logs show that "${lesson.term}" changed after the last release.`,
      'Evidence-based language names the source of the claim.',
      seed + 5,
    ),
    mc(
      'EASY',
      `Select words or phrases that belong to this lesson: ${lesson.focus}.`,
      lesson.related,
      lesson.distractors,
      `The correct answers are technical terms connected to "${lesson.term}".`,
    ),
    mc(
      'EASY',
      'Select natural technical English sentences.',
      [
        `Could you explain "${lesson.term}" in the ticket?`,
        `The team discussed "${lesson.term}" during review.`,
        `This note describes "${lesson.term}" clearly.`,
      ],
      [
        `Explain you "${lesson.term}" me?`,
        `The team discussed about "${lesson.term}" yesterday.`,
        `This note is more clearer about "${lesson.term}".`,
      ],
      'Natural technical English is clear, direct, and grammatically stable.',
    ),
    mc(
      'EASY',
      `Which actions are sensible when learning or using "${lesson.term}"?`,
      ACTIONS.correct,
      ACTIONS.distractors,
      'Good engineering communication means reviewing, documenting, and asking precise questions.',
    ),
    mc(
      'EASY',
      'Select evidence that can support a technical update.',
      ['logs', 'screenshots', 'test results'],
      ['random guesses', 'blame', 'silence'],
      'Logs, screenshots, and test results make a technical update verifiable.',
    ),
    mc(
      'EASY',
      'Select polite review phrases.',
      ['Could we clarify this?', 'One concern is...', 'Could you add context?'],
      ['This is nonsense.', 'Fix it now!!!', 'You did it wrong.'],
      'Polite review language focuses on the work and the next improvement.',
    ),
    mc(
      'EASY',
      'Select items that belong in a short status update.',
      ['current state', 'blocker', 'next step'],
      ['private complaint', 'unverified accusation', 'random joke'],
      'A status update should make progress, blockers, and next steps visible.',
    ),
    ord(
      'HARD',
      `Build a natural sentence about "${lesson.term}".`,
      ['Please', 'check', `"${lesson.term}"`, 'before merging.'],
      'A polite request usually starts with "Please", then the verb and object.',
    ),
    ord(
      'HARD',
      `Order the workflow for understanding "${lesson.term}".`,
      ['Read the ticket', 'check the context', 'ask one question', 'send the update'],
      'A good workflow moves from reading to context, then clarification, then update.',
    ),
    ord(
      'HARD',
      'Order a concise issue report.',
      ['Observed behavior', 'Expected behavior', 'Steps to reproduce', 'Relevant evidence'],
      'A useful issue report separates what happened, what should happen, reproduction steps, and evidence.',
    ),
    ord(
      'HARD',
      'Order a polite review comment.',
      ['Could you', 'add context', `about "${lesson.term}"`, 'before we merge?'],
      'A polite review request starts with a modal phrase and asks for a concrete improvement.',
    ),
    ord(
      'HARD',
      'Order a learning loop for technical English.',
      ['Read the example', 'say it aloud', 'write your own version', 'check the result'],
      'Input, speaking, writing, and feedback form a useful learning loop.',
    ),
    ord(
      'HARD',
      'Order a concise status update.',
      ['I checked the logs.', 'The failure happens on login.', 'The likely cause is the token refresh.', 'Next I will add a regression test.'],
      'A strong update gives evidence, observation, hypothesis, and next action.',
    ),
    fb(
      'HARD',
      `The word ___ helps us ${lesson.focus}.`,
      lesson.term,
      [...lesson.related, lesson.distractors[0], lesson.distractors[1]],
      `The target term for this lesson is "${lesson.term}".`,
    ),
    fb(
      'HARD',
      grammarFill.text,
      grammarFill.answer,
      grammarFill.candidates,
      grammarFill.info,
    ),
    fb(
      'HARD',
      'Before merging, we should ___ the change.',
      'review',
      ['ignore', 'hide', 'guess', 'decorate', 'forget'],
      'The natural collocation is "review the change".',
    ),
    fb(
      'HARD',
      'Could you ___ what this term means in this ticket?',
      'clarify',
      ['delete', 'hide', 'guess', 'decorate', 'panic'],
      'The natural polite verb is "clarify".',
    ),
    fb(
      'HARD',
      'A good technical update includes context, evidence, and the next ___.',
      'step',
      ['emotion', 'silence', 'rumor', 'decoration', 'blame'],
      'The phrase "next step" makes the update actionable.',
    ),
    fb(
      'HARD',
      'In professional English, claims should be supported by ___.',
      'evidence',
      ['mood', 'noise', 'speedrun', 'guessing', 'anger'],
      'Technical claims are stronger when supported by evidence.',
    ),
  ];
}

function buildLesson(sectionIndex, themeIndex, lessonIndex, levelSpec, lessonSpec) {
  const TYPE_PREFIX = {
    SingleChoice: 'sc',
    MultipleChoice: 'mc',
    Ordering: 'ord',
    FillBlank: 'fb',
  };
  const DIFF_PREFIX = { EASY: 'e', HARD: 'h' };
  const lessonId = `lb-courses-english-tech-${sectionIndex}-${themeIndex}-${lessonIndex}`;
  const counters = {};
  const questions = makeQuestions(levelSpec, lessonSpec, sectionIndex + themeIndex + lessonIndex)
    .map((question, order) => {
      const typePrefix = TYPE_PREFIX[question.type];
      const diffPrefix = DIFF_PREFIX[question.difficulty];
      const key = `${typePrefix}-${diffPrefix}`;
      counters[key] = (counters[key] || 0) + 1;
      return {
        id: `qsb-courses-english-tech-${sectionIndex}-${themeIndex}-${lessonIndex}-${typePrefix}-${diffPrefix}-${counters[key]}`,
        order,
        text: question.text,
        payload: question,
      };
    });

  return {
    id: lessonId,
    title: lessonSpec.title,
    questions,
  };
}

function buildQuest() {
  return {
    id: 'qb-courses-english-tech-c2',
    title: 'Technical English A0-C2: английский для разработчиков',
    sections: LEVELS.map((levelSpec, sectionIndexZero) => {
      const sectionIndex = sectionIndexZero + 1;
      const expandedThemes = expandThemes(levelSpec);
      return {
        id: `sb-courses-english-tech-${sectionIndex}`,
        title: levelSpec.title,
        themes: expandedThemes.map((themeSpec, themeIndexZero) => {
          const themeIndex = themeIndexZero + 1;
          return {
            id: `tb-courses-english-tech-${sectionIndex}-${themeIndex}`,
            title: themeSpec.title,
            lessons: themeSpec.lessons.map((lessonSpec, lessonIndexZero) => (
              buildLesson(sectionIndex, themeIndex, lessonIndexZero + 1, levelSpec, lessonSpec)
            )),
          };
        }),
      };
    }),
  };
}

module.exports = buildQuest();
