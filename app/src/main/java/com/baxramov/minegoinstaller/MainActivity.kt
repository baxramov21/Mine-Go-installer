package com.baxramov.minegoinstaller

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class MainActivity : AppCompatActivity() {

    private val chooseFileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                val fileUri: Uri? = data.data
                onFileSelected(fileUri)
            }
        }

    private val chooseDirectoryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                val directoryUri: Uri? = data.data
                onDirectorySelected(directoryUri)
            }
        }

    private var selectedFileUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val chooseFileButton: Button = findViewById(R.id.chooseFileButton)

        chooseFileButton.setOnClickListener {
            openFileChooser()
        }
    }

    private fun openFileChooser() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.type = "*/*"
        chooseFileLauncher.launch(intent)
    }

    private fun onFileSelected(fileUri: Uri?) {
        selectedFileUri = fileUri
        if (selectedFileUri != null) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            chooseDirectoryLauncher.launch(intent)
        }
    }

    private fun onDirectorySelected(directoryUri: Uri?) {
        if (directoryUri != null) {
            MainScope().launch {
                copyFileToDirectory(selectedFileUri, directoryUri)
            }
        }
    }

    private suspend fun copyFileToDirectory(fileUri: Uri?, directoryUri: Uri) {
        try {
            if (fileUri != null) {
                val inputStream: InputStream? = contentResolver.openInputStream(fileUri)

                val originalFileName = getFileName(fileUri)

                val documentFile = DocumentFile.fromTreeUri(this, directoryUri)
                val existingFile = documentFile?.findFile(originalFileName)

                existingFile?.delete()

                val newFile = documentFile?.createFile("*/*", originalFileName)

                if (inputStream != null && newFile != null) {
                    copyDataWithCoroutines(inputStream, newFile.uri)
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Error copying file", Toast.LENGTH_SHORT)
                            .show()
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "File successfully copied",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()

            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Error copying file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun copyDataWithCoroutines(inputStream: InputStream, outputUri: Uri) {
        withContext(Dispatchers.IO) {
            val outputStream: OutputStream? = contentResolver.openOutputStream(outputUri)

            inputStream.use { input ->
                outputStream?.use { output ->
                    val buffer = ByteArray(1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val displayNameIndex = it.getColumnIndex("_display_name")
                if (displayNameIndex != -1) {
                    val displayName = it.getString(displayNameIndex)
                    if (!displayName.isNullOrBlank()) {
                        return displayName
                    }
                }
            }
        }
        return "copied_file_${UUID.randomUUID()}"
    }
}