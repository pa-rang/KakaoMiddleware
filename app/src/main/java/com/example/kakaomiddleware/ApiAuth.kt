package com.example.kakaomiddleware

import okhttp3.Request

/**
 * Attach the server API key to a request when this build carries one.
 *
 * The key authenticates the device against kakao-agent-server; it is not a model
 * key and grants no access to any AI provider. It is injected at build time from
 * `local.properties` (see `app/build.gradle.kts`).
 *
 * When no key is compiled in, the header is omitted entirely rather than sent
 * empty: the server accepts an unauthenticated request while `AUTH_ENFORCE` is
 * off, but rejects a malformed one outright.
 *
 * A key baked into an APK can be recovered by decompiling it. This raises the
 * bar against scanners that stumble onto the endpoint; it is not protection
 * against someone who has the APK and wants in.
 */
fun Request.Builder.addApiKeyHeader(): Request.Builder {
    val apiKey = BuildConfig.API_KEY
    return if (apiKey.isNotBlank()) {
        addHeader("Authorization", "Bearer $apiKey")
    } else {
        this
    }
}
