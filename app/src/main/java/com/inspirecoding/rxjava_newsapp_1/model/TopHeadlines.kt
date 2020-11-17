package com.inspirecoding.rxjava_newsapp_1.model

data class TopHeadlines(
    val articles: List<Article>? = listOf(),
    val status: String? = "",
    val totalResults: Int? = 0
)