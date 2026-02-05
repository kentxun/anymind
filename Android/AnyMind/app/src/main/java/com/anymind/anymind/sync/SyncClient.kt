package com.anymind.anymind.sync

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody

class SyncClient(
    private val client: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson()
) {
    fun push(baseUrl: String, request: SyncPushRequest): SyncPushResponse {
        return post(baseUrl, "/sync/push", request, SyncPushResponse::class.java)
    }

    fun pull(baseUrl: String, request: SyncPullRequest): SyncPullResponse {
        return post(baseUrl, "/sync/pull", request, SyncPullResponse::class.java)
    }

    fun createSpace(baseUrl: String, name: String?): SpaceCreateResponse {
        return post(baseUrl, "/spaces", SpaceCreateRequest(name), SpaceCreateResponse::class.java)
    }

    private fun <T : Any, R : Any> post(
        baseUrl: String,
        path: String,
        body: T,
        responseType: Class<R>
    ): R {
        val url = baseUrl.trimEnd('/') + path
        val json = gson.toJson(body)
        val requestBody = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()
        val response = client.newCall(request).execute()
        response.use {
            if (!response.isSuccessful) {
                throw SyncException("HTTP ${response.code}")
            }
            val responseBody = response.body ?: throw SyncException("Empty response")
            return parse(responseBody, responseType)
        }
    }

    private fun <R : Any> parse(body: ResponseBody, responseType: Class<R>): R {
        val text = body.string()
        return gson.fromJson(text, responseType)
    }
}

class SyncException(message: String) : RuntimeException(message)
