package com.tpov.schoolquiz.shared.core.stats

/** Signals that the authenticated user's UID changed; consumers should re-subscribe. */
class AuthUidChanged : Exception("Authenticated user UID changed — re-subscribe required")
