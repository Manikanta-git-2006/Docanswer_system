package com.example.docanswer_system

data class DocumentChunk(
    val content: String,
    val sourceName: String,
    val pageNumber: Int,
    var embedding: List<Float>? = null
)

data class SearchResult(
    val chunk: DocumentChunk,
    val score: Float
)
