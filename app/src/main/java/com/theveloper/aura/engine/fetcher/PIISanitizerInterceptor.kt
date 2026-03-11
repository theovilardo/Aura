package com.theveloper.aura.engine.fetcher

import okhttp3.Interceptor
import okhttp3.Response
import android.util.Log

class PIISanitizerInterceptor : Interceptor {
    private val blocklist = listOf("name", "email", "phone", "address", "id", "user", "token")

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url

        val allowedQueryParameterNames = originalUrl.queryParameterNames.filter { paramName ->
            blocklist.none { paramName.contains(it, ignoreCase = true) }
        }

        val cleanUrlBuilder = originalUrl.newBuilder()
        cleanUrlBuilder.query(null) // Clear existing query params

        for (name in allowedQueryParameterNames) {
            val values = originalUrl.queryParameterValues(name)
            for (value in values) {
                cleanUrlBuilder.addQueryParameter(name, value)
            }
        }

        val cleanUrl = cleanUrlBuilder.build()
        Log.d("PIISanitizer", "Request parameters sanitized: ${cleanUrl.queryParameterNames}")

        val cleanRequest = originalRequest.newBuilder()
            .url(cleanUrl)
            .build()

        return chain.proceed(cleanRequest)
    }
}
