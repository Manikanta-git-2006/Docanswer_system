package com.example.docanswer_system

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.docanswer_system.databinding.ActivityMainBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var ragManager: RagManager
    
    // Note: In a real app, the API key should be stored securely (e.g., encrypted in SharedPreferences or fetched from a backend).
    private val apiKey = "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"

    private val selectPdfLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { processSelectedPdf(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ragManager = RagManager(this, apiKey)

        setupListeners()
        updateDocumentLibrary()
    }

    private fun setupListeners() {
        binding.btnUploadZone.setOnClickListener {
            selectPdfLauncher.launch("application/pdf")
        }

        binding.btnSearch.setOnClickListener {
            val question = binding.etQuestion.text.toString().trim()
            if (question.isNotEmpty()) {
                performSearch(question)
            } else {
                Toast.makeText(this, getString(R.string.type_your_question), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCopy.setOnClickListener {
            val text = binding.tvAnswerText.text.toString()
            if (text.isNotEmpty()) {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText(getString(R.string.clipboard_label), text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRegenerate.setOnClickListener {
            val question = binding.etQuestion.text.toString().trim()
            if (question.isNotEmpty()) {
                performSearch(question)
            }
        }

        binding.btnViewDocument.setOnClickListener {
            Toast.makeText(this, getString(R.string.feature_coming_soon), Toast.LENGTH_SHORT).show()
        }
    }

    private fun processSelectedPdf(uri: Uri) {
        val fileName = getFileName(uri) ?: "Unknown Document"
        
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.isIndeterminate = true
        
        lifecycleScope.launch {
            try {
                ragManager.processPdf(uri, fileName)
                updateDocumentLibrary()
                Toast.makeText(this@MainActivity, getString(R.string.file_item_format, fileName), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, getString(R.string.error_format, e.localizedMessage ?: "Unknown Error"), Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun performSearch(question: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.isIndeterminate = true
        
        lifecycleScope.launch {
            try {
                val (answer, sources) = ragManager.askQuestion(question)
                displayAnswer(answer, sources)
            } catch (e: Exception) {
                binding.tvAnswerText.text = getString(R.string.error_format, e.localizedMessage ?: "Unknown Error")
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun displayAnswer(answer: String, sources: List<DocumentChunk>) {
        binding.tvAnswerText.text = answer
        binding.llSourcesTags.removeAllViews()
        
        if (sources.isEmpty()) {
            binding.tvSourcesLabel.visibility = View.GONE
            binding.hsvSources.visibility = View.GONE
        } else {
            binding.tvSourcesLabel.visibility = View.VISIBLE
            binding.hsvSources.visibility = View.VISIBLE
            
            sources.forEach { chunk ->
                val chip = Chip(this).apply {
                    text = getString(R.string.source_format, chunk.sourceName, chunk.pageNumber)
                    chipBackgroundColor = ContextCompat.getColorStateList(this@MainActivity, android.R.color.white)
                    setChipStrokeColorResource(android.R.color.darker_gray)
                    chipStrokeWidth = 2f
                    textSize = 12f
                    isClickable = false
                }
                binding.llSourcesTags.addView(chip)
            }
        }
    }

    private fun updateDocumentLibrary() {
        binding.llDocumentList.removeAllViews()
        val files = ragManager.getLoadedFileNames()
        if (files.isEmpty()) {
            val tv = TextView(this).apply {
                text = getString(R.string.no_documents)
                setTextColor(0xFF9CA3AF.toInt())
                textSize = 12f
            }
            binding.llDocumentList.addView(tv)
        } else {
            files.forEach { fileName ->
                val tv = TextView(this).apply {
                    text = getString(R.string.file_item_format, fileName)
                    setPadding(0, 8, 0, 8)
                    setTextColor(0xFF4B5563.toInt())
                    textSize = 13f
                }
                binding.llDocumentList.addView(tv)
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor.use {
                if (it != null && it.moveToFirst()) {
                    result = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }
}
