package nl.mpcjanssen.simpletask.remote

import android.app.Activity
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.util.Log
import nl.mpcjanssen.simpletask.TodoApplication
import nl.mpcjanssen.simpletask.util.join
import nl.mpcjanssen.simpletask.util.writeToFile
import java.io.BufferedReader
import java.io.File
import java.io.FilenameFilter
import java.io.InputStreamReader
import java.util.*
import kotlin.reflect.KClass
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.room.util.CursorUtil.getColumnIndexOrThrow
import androidx.documentfile.provider.DocumentFile
import nl.mpcjanssen.simpletask.Simpletask


object FileStore : IFileStore {
    override fun getRemoteVersion(uri: Uri): String? =   uri.metaData(TodoApplication.app).lastModified
    private val TAG = "FileStore"

    override fun loadTasksFromFile(uri: Uri): RemoteContents {
        val lines = ArrayList<String>()
        TodoApplication.app.contentResolver.openInputStream(uri)?.use { inputStream ->
             inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                var line: String? = reader.readLine()
                while (line != null) {
                    lines.add(line)
                    line = reader.readLine()
                }
            }
        }
        return RemoteContents(uri.metaData(TodoApplication.app).lastModified, lines)
    }

    override fun saveTasksToFile(uri: Uri, lines: List<String>, eol: String) {
        TodoApplication.app.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.bufferedWriter(Charsets.UTF_8).write(lines.joinToString(eol))
        }

    }

    override fun appendTaskToFile(uri: Uri, lines: List<String>, eol: String) {
        TodoApplication.app.contentResolver.openOutputStream(uri, "wa")?.use { stream ->
            stream.bufferedWriter(Charsets.UTF_8).write(lines.joinToString(eol))
        }
    }
}


data class MetaData(val lastModified: String?, val displayName: String?)

fun Uri.metaData(ctxt: Context) : MetaData {
    val cursor: Cursor? = ctxt.contentResolver.query( this, null, null, null, null, null)

    cursor?.use {
        // moveToFirst() returns false if the cursor has 0 rows.  Very handy for
        // "if there's anything to look at, look at it" conditionals.
        if (it.moveToFirst()) {

            // Note it's called "Display Name".  This is
            // provider-specific, and might not necessarily be the file name.
            val displayName: String = it.getString(it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
            val lastModified = it.getLong(it.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED))
            return MetaData(lastModified.toString(), displayName)
        }
    }
    return MetaData(null,null)
}
