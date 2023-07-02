package com.tpov.schoolquiz.presentation.custom

import com.tpov.schoolquiz.data.model.LanguageEntity

object LanguageUtils {
    val languagesShortCodes = arrayOf(
        "af", "sq", "am", "ar", "hy", "as", "ay", "az", "bm", "eu", "be", "bn", "bho", "bs", "bg", "ca", "ceb", "zh-CN",
        "zh-TW", "co", "hr", "cs", "da", "dv", "doi", "nl", "en", "eo", "et", "ee", "fil", "fi", "fr", "fy", "gl", "ka",
        "de", "el", "gn", "gu", "ht", "ha", "haw", "he", "hi", "hmn", "hu", "is", "ig", "ilo", "id", "ga", "it", "ja",
        "jv", "kn", "kk", "km", "rw", "gom", "ko", "kri", "ku", "ckb", "ky", "lo", "la", "lv", "ln", "lt", "lg", "lb",
        "mk", "mai", "mg", "ms", "ml", "mt", "mi", "mr", "mni-Mtei", "lus", "mn", "my", "ne", "no", "ny", "or", "om",
        "ps", "fa", "pl", "pt", "pa", "qu", "ro", "ru", "sm", "sa", "gd", "nso", "sr", "st", "sn", "sd", "si", "sk",
        "sl", "so", "es", "su", "sw", "sv", "tl", "tg", "ta", "tt", "te", "th", "ti", "ts", "tr", "tk", "ak", "uk", "ur",
        "ug", "uz", "vi", "cy", "xh", "yi", "yo", "zu"
    )

    val languagesFullNames = arrayOf(
        "Afrikaans", "Albanian", "Amharic", "Arabic", "Armenian", "Assamese", "Aymara", "Azerbaijani", "Bambara", "Basque",
        "Belarusian", "Bengali", "Bhojpuri", "Bosnian", "Bulgarian", "Catalan", "Cebuano", "Chinese (Simplified)", "Chinese (Traditional)",
        "Corsican", "Croatian", "Czech", "Danish", "Dhivehi", "Dogri", "Dutch", "English", "Esperanto", "Estonian", "Ewe", "Filipino (Tagalog)",
        "Finnish", "French", "Frisian", "Galician", "Georgian", "German", "Greek", "Guarani", "Gujarati", "Haitian Creole", "Hausa",
        "Hawaiian", "Hebrew", "Hindi", "Hmong", "Hungarian", "Icelandic", "Igbo", "Ilocano", "Indonesian", "Irish", "Italian", "Japanese",
        "Javanese", "Kannada", "Kazakh", "Khmer", "Kinyarwanda", "Konkani", "Korean", "Krio", "Kurdish", "Kurdish (Sorani)", "Kyrgyz",
        "Lao", "Latin", "Latvian", "Lingala", "Lithuanian", "Luganda", "Luxembourgish", "Macedonian", "Maithili", "Malagasy", "Malay",
        "Malayalam", "Maltese", "Maori", "Marathi", "Meiteilon (Manipuri)", "Mizo", "Mongolian", "Myanmar (Burmese)", "Nepali", "Norwegian",
        "Nyanja (Chichewa)", "Odia (Oriya)", "Oromo", "Pashto", "Persian", "Polish", "Portuguese (Portugal, Brazil)", "Punjabi", "Quechua",
        "Romanian", "Russian", "Samoan", "Sanskrit", "Scots Gaelic", "Sepedi", "Serbian", "Sesotho", "Shona", "Sindhi", "Sinhala (Sinhalese)",
        "Slovak", "Slovenian", "Somali", "Spanish", "Sundanese", "Swahili", "Swedish", "Tagalog (Filipino)", "Tajik", "Tamil", "Tatar",
        "Telugu", "Thai", "Tigrinya", "Tsonga", "Turkish", "Turkmen", "Twi (Akan)", "Ukrainian", "Urdu", "Uyghur", "Uzbek", "Vietnamese",
        "Welsh", "Xhosa", "Yiddish", "Yoruba", "Zulu"
    )

    fun getLanguageFullName(languageCode: String): String {
        val index = languagesShortCodes.indexOf(languageCode)
        return if (index != -1) languagesFullNames[index] else "und"
    }

    fun getLanguageShortCode(languageFullName: String): String {
        val index = languagesFullNames.indexOf(languageFullName)
        return if (index != -1) languagesShortCodes[index] else "und"
    }

    val ratingNum = arrayOf(0, 1, 2, 3)

    val languagesWithCheckBox = listOf(
        LanguageEntity("Afrikaans", false),
        LanguageEntity("Albanian", false),
        LanguageEntity("Amharic", false),
        LanguageEntity("Arabic", false),
        LanguageEntity("Armenian", false),
        LanguageEntity("Assamese", false),
        LanguageEntity("Aymara", false),
        LanguageEntity("Azerbaijani", false),
        LanguageEntity("Bambara", false),
        LanguageEntity("Basque", false),
        LanguageEntity("Belarusian", false),
        LanguageEntity("Bengali", false),
        LanguageEntity("Bhojpuri", false),
        LanguageEntity("Bosnian", false),
        LanguageEntity("Bulgarian", false),
        LanguageEntity("Catalan", false),
        LanguageEntity("Cebuano", false),
        LanguageEntity("Chinese (Simplified)", false),
        LanguageEntity("Chinese (Traditional)", false),
        LanguageEntity("Corsican", false),
        LanguageEntity("Croatian", false),
        LanguageEntity("Czech", false),
        LanguageEntity("Danish", false),
        LanguageEntity("Dhivehi", false),
        LanguageEntity("Dogri", false),
        LanguageEntity("Dutch", false),
        LanguageEntity("English", false),
        LanguageEntity("Esperanto", false),
        LanguageEntity("Estonian", false),
        LanguageEntity("Ewe", false),
        LanguageEntity("Filipino (Tagalog)", false),
        LanguageEntity("Finnish", false),
        LanguageEntity("French", false),
        LanguageEntity("Frisian", false),
        LanguageEntity("Galician", false),
        LanguageEntity("Georgian", false),
        LanguageEntity("German", false),
        LanguageEntity("Greek", false),
        LanguageEntity("Guarani", false),
        LanguageEntity("Gujarati", false),
        LanguageEntity("Haitian Creole", false),
        LanguageEntity("Hausa", false),
        LanguageEntity("Hawaiian", false),
        LanguageEntity("Hebrew", false),
        LanguageEntity("Hindi", false),
        LanguageEntity("Hmong", false),
        LanguageEntity("Hungarian", false),
        LanguageEntity("Icelandic", false),
        LanguageEntity("Igbo", false),
        LanguageEntity("Ilocano", false),
        LanguageEntity("Indonesian", false),
        LanguageEntity("Irish", false),
        LanguageEntity("Italian", false),
        LanguageEntity("Japanese", false),
        LanguageEntity("Javanese", false),
        LanguageEntity("Kannada", false),
        LanguageEntity("Kazakh", false),
        LanguageEntity("Khmer", false),
        LanguageEntity("Kinyarwanda", false),
        LanguageEntity("Konkani", false),
        LanguageEntity("Korean", false),
        LanguageEntity("Krio", false),
        LanguageEntity("Kurdish", false),
        LanguageEntity("Kurdish (Sorani)", false),
        LanguageEntity("Kyrgyz", false),
        LanguageEntity("Lao", false),
        LanguageEntity("Latin", false),
        LanguageEntity("Latvian", false),
        LanguageEntity("Lingala", false),
        LanguageEntity("Lithuanian", false),
        LanguageEntity("Luganda", false),
        LanguageEntity("Luxembourgish", false),
        LanguageEntity("Macedonian", false),
        LanguageEntity("Maithili", false),
        LanguageEntity("Malagasy", false),
        LanguageEntity("Malay", false),
        LanguageEntity("Malayalam", false),
        LanguageEntity("Maltese", false),
        LanguageEntity("Maori", false),
        LanguageEntity("Marathi", false),
        LanguageEntity("Meiteilon (Manipuri)", false),
        LanguageEntity("Mizo", false),
        LanguageEntity("Mongolian", false),
        LanguageEntity("Myanmar (Burmese)", false),
        LanguageEntity("Nepali", false),
        LanguageEntity("Norwegian", false),
        LanguageEntity("Nyanja (Chichewa)", false),
        LanguageEntity("Odia (Oriya)", false),
        LanguageEntity("Oromo", false),
        LanguageEntity("Pashto", false),
        LanguageEntity("Persian", false),
        LanguageEntity("Polish", false),
        LanguageEntity("Portuguese (Portugal, Brazil)", false),
        LanguageEntity("Punjabi", false),
        LanguageEntity("Quechua", false),
        LanguageEntity("Romanian", false),
        LanguageEntity("Russian", false),
        LanguageEntity("Samoan", false),
        LanguageEntity("Sanskrit", false),
        LanguageEntity("Scots Gaelic", false),
        LanguageEntity("Sepedi", false),
        LanguageEntity("Serbian", false),
        LanguageEntity("Sesotho", false),
        LanguageEntity("Shona", false),
        LanguageEntity("Sindhi", false),
        LanguageEntity("Sinhala (Sinhalese)", false),
        LanguageEntity("Slovak", false),
        LanguageEntity("Slovenian", false),
        LanguageEntity("Somali", false),
        LanguageEntity("Spanish", false),
        LanguageEntity("Sundanese", false),
        LanguageEntity("Swahili", false),
        LanguageEntity("Swedish", false),
        LanguageEntity("Tagalog (Filipino)", false),
        LanguageEntity("Tajik", false),
        LanguageEntity("Tamil", false),
        LanguageEntity("Tatar", false),
        LanguageEntity("Telugu", false),
        LanguageEntity("Thai", false),
        LanguageEntity("Tigrinya", false),
        LanguageEntity("Tsonga", false),
        LanguageEntity("Turkish", false),
        LanguageEntity("Turkmen", false),
        LanguageEntity("Twi (Akan)", false),
        LanguageEntity("Ukrainian", false),
        LanguageEntity("Urdu", false),
        LanguageEntity("Uyghur", false),
        LanguageEntity("Uzbek", false),
        LanguageEntity("Vietnamese", false),
        LanguageEntity("Welsh", false),
        LanguageEntity("Xhosa", false),
        LanguageEntity("Yiddish", false),
        LanguageEntity("Yoruba", false),
        LanguageEntity("Zulu", false)
    )


    fun getPositionLang(shortCode: String): Int {
        return when (shortCode.lowercase()) {
            "en" -> 0
            "ru" -> 1
            "fr" -> 2
            "de" -> 3
            "es" -> 4
            else -> 0
        }
    }
}
