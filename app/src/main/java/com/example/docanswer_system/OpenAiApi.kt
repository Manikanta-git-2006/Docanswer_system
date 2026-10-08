package com.example.docanswer_system

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenAiApi {
    @POST("v1/embeddings")
    suspend fun getEmbeddings(
        @Header("Authorization") auth: String,
        @Body request: EmbeddingRequest
    ): EmbeddingResponse

    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") auth: String,
        @Body request: ChatRequest
    ): ChatResponse
}

data class EmbeddingRequest(
    val input: List<String>,
    val model: String = "text-embedding-3-small"
)

data class EmbeddingResponse(
    val data: List<EmbeddingData>
)

data class EmbeddingData(
    val embedding: List<Float>,
    val index: Int
)

data class ChatRequest(
    val model: String = "gpt-4o-mini",
    val messages: List<ChatMessage>,
    val temperature: Double = 0.0
)

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatResponse(
    val choices: List<ChatChoice>
)

data class ChatChoice(
    val message: ChatMessage
)