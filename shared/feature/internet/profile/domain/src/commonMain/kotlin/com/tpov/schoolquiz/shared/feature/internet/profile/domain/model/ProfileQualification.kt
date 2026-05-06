package com.tpov.schoolquiz.shared.feature.internet.profile.domain.model

data class ProfileQualification(
    val sponsorLevel: Int = 0,
    val testerLevel: Int = 0,
    val translatorLevel: Int = 0,
    val moderatorLevel: Int = 0,
    val adminLevel: Int = 0,
    val developerLevel: Int = 0,
) {
    init {
        require(sponsorLevel >= 0) { "sponsorLevel must be non-negative" }
        require(testerLevel >= 0) { "testerLevel must be non-negative" }
        require(translatorLevel >= 0) { "translatorLevel must be non-negative" }
        require(moderatorLevel >= 0) { "moderatorLevel must be non-negative" }
        require(adminLevel >= 0) { "adminLevel must be non-negative" }
        require(developerLevel >= 0) { "developerLevel must be non-negative" }
    }

    val isValidated: Boolean
        get() =
            sponsorLevel > 0 ||
                testerLevel > 0 ||
                translatorLevel > 0 ||
                moderatorLevel > 0 ||
                adminLevel > 0 ||
                developerLevel > 0
}
