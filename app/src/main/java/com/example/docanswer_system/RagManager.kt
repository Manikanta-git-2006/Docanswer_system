package com.example.docanswer_system

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.math.sqrt

class RagManager(private val context: Context, private val apiKey: String) {

    private val api: OpenAiApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openai.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenAiApi::class.java)
    }

    private val documentStore = mutableListOf<DocumentChunk>()

    init {
        PDFBoxResourceLoader.init(context)
    }

    suspend fun processPdf(uri: Uri, fileName: String) = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
        val document = PDDocument.load(inputStream)
        val stripper = PDFTextStripper()
        
        val newChunks = mutableListOf<DocumentChunk>()
        
        for (page in 1..document.numberOfPages) {
            stripper.startPage = page
            stripper.endPage = page
            val text = stripper.getText(document)
            
            // Simple chunking logic: Split by paragraphs or fixed length
            val chunks = text.split("\n\n").filter { it.isNotBlank() }
            chunks.forEach { content ->
                newChunks.add(DocumentChunk(content, fileName, page))
            }
        }
        document.close()

        // Generate embeddings for new chunks
        val batchSize = 20
        for (i in 0 until newChunks.size step batchSize) {
            val end = minOf(i + batchSize, newChunks.size)
            val batch = newChunks.subList(i, end)
            val response = api.getEmbeddings("Bearer $apiKey", EmbeddingRequest(batch.map { it.content }))
            response.data.forEachIndexed { index, embeddingData ->
                batch[index].embedding = embeddingData.embedding
            }
        }
        
        documentStore.addAll(newChunks)
    }

    suspend fun askQuestion(question: String): Pair<String, List<DocumentChunk>> = withContext(Dispatchers.IO) {
        if (documentStore.isEmpty()) return@withContext "Please upload a document first." to emptyList()

        // 1. Embed the question
        val questionEmbeddingResponse = api.getEmbeddings("Bearer $apiKey", EmbeddingRequest(listOf(question)))
        val questionEmbedding = questionEmbeddingResponse.data.first().embedding

        // 2. Retrieve relevant chunks
        val results = documentStore.map { chunk ->
            SearchResult(chunk, cosineSimilarity(questionEmbedding, chunk.embedding!!))
        }.sortedByDescending { it.score }.take(5)

        val contextText = results.joinToString("\n\n") { it.chunk.content }
        val sources = results.map { it.chunk }.distinctBy { it.sourceName + it.pageNumber }

        // 3. Generate Answer
        val prompt = """
            You are a helpful assistant. Answer the question based ONLY on the provided context.
            If the answer is not in the context, say "I'm sorry, but I couldn't find information about that in the uploaded documents."
            
            Context:
            $contextText
            
            Question: $question
        """.trimIndent()

        val chatResponse = api.chatCompletion("Bearer $apiKey", ChatRequest(
            messages = listOf(ChatMessage("user", prompt))
        ))

        val answer = chatResponse.choices.first().message.content
        return@withContext answer to sources
    }

    private fun cosineSimilarity(v1: List<Float>, v2: List<Float>): Float {
        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        return dotProduct / (sqrt(normA) * sqrt(normB))
    }
    
    fun getDocumentCount(): Int = documentStore.distinctBy { it.sourceName }.size
    
    fun getLoadedFileNames(): List<String> = documentStore.map { it.sourceName }.distinct()
}
