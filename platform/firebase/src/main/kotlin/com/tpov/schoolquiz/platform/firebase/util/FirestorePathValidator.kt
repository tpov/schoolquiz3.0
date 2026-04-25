package com.tpov.schoolquiz.platform.firebase.util

internal fun isValidRelativePath(path: String): Boolean =
    !path.contains("..") &&
        !path.startsWith("/") &&
        !path.startsWith("https://") &&
        !path.startsWith("http://") &&
        !path.startsWith("gs://")
