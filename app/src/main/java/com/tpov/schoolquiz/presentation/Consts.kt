package com.tpov.schoolquiz.presentation

//quiz
const val RATING_QUIZ_EVENT_BED = 1
const val RATING_QUIZ_ALL_POINTS_IN_FB = 100
const val COUNT_STARS_QUIZ_RATING = 3

const val EVENT_QUIZ_HOME = 8
const val EVENT_QUIZ_TOURNIRE_LEADER = 7
const val EVENT_QUIZ_TOURNIRE = 6
const val EVENT_QUIZ_ARENA = 5
const val EVENT_QUIZ_FOR_ADMIN = 4
const val EVENT_QUIZ_FOR_MODERATOR = 3
const val EVENT_QUIZ_FOR_TESTER = 2
const val EVENT_QUIZ_FOR_USER = 1

const val DEFAULT_TPOVID = 0
const val DEFAULT_TPOVID_COOL_START = -1
const val QUIZ_VERSION_DEFAULT = -1
const val RATING_QUIZ_ARENA_IN_TOP = 250 //max 300

//skill

const val MIN_VALUE_SKILL_POINTS = 100_000f

//Qualification
//Player
const val COUNT_SKILL_EDUCATION = 0
const val COUNT_SKILL_BEGINNER = 2000
const val COUNT_SKILL_PLAYER = 10_0000
const val COUNT_SKILL_AMATEUR = 50_0000
const val COUNT_SKILL_VETERAN = 120_0000
const val COUNT_SKILL_GRANDMASTER = 250_0000
const val COUNT_SKILL_EXPERT = 500_0000
const val COUNT_SKILL_LEGEND = 1000_0000

const val COUNT_SKILL_USERGUIDE_1 = 200

//Translation
const val LVL_GOOGLE_TRANSLATOR = 100
const val LVL_TRANSLATOR_1_LVL = 100
const val LVL_TRANSLATOR_2_LVL = 200
const val LVL_TRANSLATOR_3_LVL = 300
const val LVL_TESTER_1_LVL = 100
const val LVL_MODERATOR_1_LVL = 100
const val LVL_ADMIN_1_LVL = 100
const val LVL_DEVELOPER_1_LVL = 100
const val SPLIT_BETWEEN_LVL_TRANSLATE_AND_LANG = "-"
const val SPLIT_BETWEEN_LANGUAGES = "|"
const val DEFAULT_INFO_TRANSLATOR_BY_GOOGLE_TRANSL = "0|0"

const val DEFAULT_LVL_QUALIFAICATION = 0

//Throphy
const val THROPHY1 = "\uD83E\uDD47" //🥇
const val THROPHY2 = "\uD83E\uDD48" //🥈️
const val THROPHY3 = "\uD83E\uDD49" //🥉

//Quiz (full -> 0 - 120, short -> 0 - 100)
const val MAX_PERCENT_LIGHT_QUIZ_FULL = 100
const val MAX_PERCENT_HARD_QUIZ_FULL = 120
const val PERCENT_1STAR_QUIZ_SHORT = 33F
const val BARRIER_QUIZ_ID_LOCAL_AND_REMOVE = 100 //Remove - Имеется ввиду если квест уже был синхронизирован с сервером (после чего у него меняется id)
const val CORRECTLY_ANSWERED_IN_CODE_ANSWER = '2'
const val INCORRECTLY_ANSWERED_IN_CODE_ANSWER = '1'
const val UNANSWERED_IN_CODE_ANSWER = '0'
const val DEFAULT_RATING_QUIZ = 0

const val DURATION_ANIMATION_COUNTS = 3000L

const val Y_ROTATE_ANIMATION_DURATION = 1000
const val REPEAT_DELAY = 60_000L // Задержка между повторениями (1 минута)
const val INITIAL_DELAY = 1000L // Начальная задержка перед запуском анимации
const val ADD_INITIAL_DELAY = 250L

const val INTERVAL_SYNTH_VIEW_PROFILE = 100_000L
const val MAX_COUNT_DAY_BOX = 10 //Сколько дней подряд нужно зайти что-бы получить коробку (DAY_BOX - имеется в ввиду дней подряд)

//chat
const val DEFAULT_RATING_IN_CHAT = 0
const val DEFAULT_REACTION_IN_CHAT = 0
const val DEFAULT_PERSONAL_SMS_IN_CHAT = 0
const val DEFAULT_DATA_IN_GET_CHAT = "HH:mm:ss - dd/MM/yy"
const val DEFAULT_DATA_IN_SEND_CHAT = "yyyy-MM-dd"
const val DEFAULT_DATA_IN_SHOW_AD = "yyyy-MM-dd"
const val DEFAULT_LOCAL_IN_GET_CHAT = "Europe/Kiev"

//delay show text by char
const val DELAY_SHOW_TEXT_IN_CHAT = 30L
const val DELAY_SHOW_TEXT_IN_MAINACTIVITY_PB = 10L
const val DELAY_SHOW_TEXT_IN_MAINACTIVITY_NICK = 50L
const val DELAY_SHOW_TEXT_IN_QUESTIONACTIVITY = 15L

const val COUNT_LIFE_POINTS_IN_LIFE = 100
const val COUNT_MAX_LIFE = 5
const val COUNT_MAX_LIFE_GOLD = 1
const val COUNT_MAX_SHOW_AD = 5
const val DELAY_TIMER_SHOW_AD = 180
//Profile
const val DEFAULT_INVINSTATION = 0
const val DELAY_TIMEOUT_SYNTH_FB = 60000L
const val ID_USERGUIDE_NOTIFICATION_LIFE = 201