package com.ganjianping.ai.features.websites.api

import com.ganjianping.ai.OpenApiClient
import com.ganjianping.ai.Website

class WebsitesApi(private val client: OpenApiClient) {
    suspend fun allWebsites(updatedAfter: String? = null): List<Website> {
        return client.fetchAll("websites/all", updatedAfter) { Website.fromJson(it) }
    }
}
