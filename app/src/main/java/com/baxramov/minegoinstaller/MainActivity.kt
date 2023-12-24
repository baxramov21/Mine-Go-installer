package com.baxramov.minegoinstaller

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class MainActivity : AppCompatActivity() {

    private val PICK_FILE_REQUEST_CODE = 1
    private val PICK_DIRECTORY_REQUEST_CODE = 2

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
        startActivityForResult(intent, PICK_FILE_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            selectedFileUri = data?.data
            if (selectedFileUri != null) {
                // Open SAF to let the user pick a directory
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                startActivityForResult(intent, PICK_DIRECTORY_REQUEST_CODE)
            }
        } else if (requestCode == PICK_DIRECTORY_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val treeUri: Uri? = data?.data
            if (treeUri != null) {
                // Copy the selected file to the chosen directory, preserving the original file name
                copyFileToDirectory(selectedFileUri, treeUri)
            }
        }
    }

    private fun copyFileToDirectory(fileUri: Uri?, directoryUri: Uri) {
        try {
            if (fileUri != null) {
                // Open input stream to read data from the selected file
                val inputStream: InputStream? = contentResolver.openInputStream(fileUri)

                // Get the original file name
                val originalFileName = getFileName(fileUri)

                // Create the new file in the chosen directory with the original file name
                val documentFile = DocumentFile.fromTreeUri(this, directoryUri)
                val existingFile = documentFile?.findFile(originalFileName)

                // Delete the existing file if it exists
                existingFile?.delete()

                // Create the new file in the chosen directory
                val newFile = documentFile?.createFile("*/*", originalFileName)

                // Open output stream to write data to the new file
                val outputStream: OutputStream? = contentResolver.openOutputStream(newFile!!.uri)

                // Copy the data
                inputStream?.use { input ->
                    outputStream?.use { output ->
                        val buffer = ByteArray(1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }

                // Notify the user about the success
                Toast.makeText(this, "File successfully copied", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            // Handle input-output errors
            e.printStackTrace()

            // Notify the user about the failure
            Toast.makeText(this, "Error copying file", Toast.LENGTH_SHORT).show()
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
        // If the display name is not found, generate a unique name
        return "copied_file_${UUID.randomUUID()}"
    }
}
