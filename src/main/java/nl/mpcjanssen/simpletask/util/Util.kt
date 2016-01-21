/**
 * This file is part of Todo.txt Touch, an Android app for managing your todo.txt file (http://todotxt.com).

 * Copyright (c) 2009-2012 Todo.txt contributors (http://todotxt.com)

 * LICENSE:

 * Todo.txt Touch is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 2 of the License, or (at your option) any
 * later version.

 * Todo.txt Touch is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.

 * You should have received a copy of the GNU General Public License along with Todo.txt Touch.  If not, see
 * //www.gnu.org/licenses/>.

 * @author Todo.txt contributors @yahoogroups.com>
 * *
 * @license http://www.gnu.org/licenses/gpl.html
 * *
 * @copyright 2009-2012 Todo.txt contributors (http://todotxt.com)
 */
@file:JvmName("Util")
package nl.mpcjanssen.simpletask.util


import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.support.v7.app.AlertDialog
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Window
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Toast
import hirondelle.date4j.DateTime
import nl.mpcjanssen.simpletask.*
import nl.mpcjanssen.simpletask.sort.AlphabeticalStringComparator
import nl.mpcjanssen.simpletask.task.Task
import org.luaj.vm2.*
import java.io.*
import java.nio.channels.FileChannel
import java.util.*
import java.util.regex.Pattern

val TAG = "Util"
val log = Logger;
    val todayAsString: String
        get() = DateTime.today(TimeZone.getDefault()).format(Constants.DATE_FORMAT)

    fun runOnMainThread(r: Runnable) {
        val handler = Handler(Looper.getMainLooper())
        handler.post(r)
    }


    fun showToastShort(cxt: Context, resid: Int) {
        runOnMainThread(Runnable { Toast.makeText(cxt, resid, Toast.LENGTH_SHORT).show() })
    }

    fun showToastLong(cxt: Context, resid: Int) {
        runOnMainThread(Runnable { Toast.makeText(cxt, resid, Toast.LENGTH_LONG).show() })
    }


    fun showToastShort(cxt: Context, msg: String) {
        runOnMainThread(Runnable { Toast.makeText(cxt, msg, Toast.LENGTH_SHORT).show() })
    }

    fun showToastLong(cxt: Context, msg: String) {
        runOnMainThread(Runnable { Toast.makeText(cxt, msg, Toast.LENGTH_LONG).show() })
    }

    fun tasksToString(tasks: List<Task>): List<String>? {
        val result = ArrayList<String>()
        for (t in tasks) {
            result.add(t.inFileFormat())
        }
        return result
    }

    interface InputDialogListener {
        fun onClick(input: String)
    }

    @Throws(TodoException::class)
    fun createParentDirectory(dest: File?) {
        val log = Logger;
        if (dest == null) {
            throw TodoException("createParentDirectory: dest is null")
        }
        val dir = dest.parentFile
        if (dir != null && !dir.exists()) {
            createParentDirectory(dir)
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    log.error(TAG, "Could not create dirs: " + dir.absolutePath)
                    throw TodoException("Could not create dirs: " + dir.absolutePath)
                }
            }
        }
    }

    fun addHeaderLines(visibleTasks: List<Task>, firstSort: String, no_header: String, showHidden: Boolean, showEmptyLists: Boolean): List<VisibleLine> {
        var header = ""
        var newHeader: String
        val result = ArrayList<VisibleLine>()
        for (t in visibleTasks) {
            newHeader = t.getHeader(firstSort, no_header)
            if (header != newHeader) {
                val headerLine = HeaderLine(newHeader)
                val last = result.size - 1
                if (last != -1 && result[last].header && !showEmptyLists) {
                    // replace empty preceding header
                    result[last] = headerLine
                } else {
                    result.add(headerLine)
                }
                header = newHeader
            }

            if (t.isVisible || showHidden) {
                // enduring tasks should not be displayed
                val taskLine = TaskLine(t)
                result.add(taskLine)
            }
        }

        // Clean up possible last empty list header that should be hidden
        val i = result.size
        if (i > 0 && result[i - 1].header && !showEmptyLists) {
            result.removeAt(i - 1)
        }
        return result
    }

    fun joinTasks(s: Collection<Task>?, delimiter: String): String {
        val builder = StringBuilder()
        if (s == null) {
            return ""
        }
        val iter = s.iterator()
        while (iter.hasNext()) {
            builder.append(iter.next().inFileFormat())
            if (!iter.hasNext()) {
                break
            }
            builder.append(delimiter)
        }
        return builder.toString()
    }

    fun join(s: Collection<String>?, delimiter: String): String {
        if (s == null) {
            return ""
        }
        return s.joinToString(delimiter)
    }

    fun setColor(ss: SpannableString, color: Int, s: String) {
        val strList = ArrayList<String>()
        strList.add(s)
        setColor(ss, color, strList)
    }

    fun setColor(ss: SpannableString, color: Int, items: List<String>) {
        val data = ss.toString()
        for (item in items) {
            val i = data.indexOf(item)
            if (i != -1) {
                ss.setSpan(ForegroundColorSpan(color), i,
                        i + item.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    fun setColor(ss: SpannableString, color: Int) {

        ss.setSpan(ForegroundColorSpan(color), 0,
                ss.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    fun addInterval(date: DateTime?, interval: String): DateTime? {
        var date = date
        val p = Pattern.compile("(\\d+)([dwmy])")
        val m = p.matcher(interval.toLowerCase(Locale.getDefault()))
        val amount: Int
        val type: String
        if (date == null) {
            date = DateTime.today(TimeZone.getDefault())
        }
        if (!m.find()) {
            //If the interval is invalid, just return the original date
            return date
        }
        if (m.groupCount() == 2) {
            amount = Integer.parseInt(m.group(1))
            type = m.group(2).toLowerCase(Locale.getDefault())
        } else {
            return date
        }
        when (type) {
            "d" -> date = date!!.plusDays(amount)
            "w" -> date = date!!.plusDays(7 * amount)
            "m" -> date = date!!.plus(0, amount, 0, 0, 0, 0, 0, DateTime.DayOverflow.LastDay)
            "y" -> date = date!!.plus(amount, 0, 0, 0, 0, 0, 0, DateTime.DayOverflow.LastDay)
            else -> {
            }
        }// Dont add anything
        return date
    }

    fun prefixItems(prefix: String, items: ArrayList<String>): ArrayList<String> {
        val result = ArrayList<String>()
        for (item in items) {
            result.add(prefix + item)
        }
        return result
    }

    fun getCheckedItems(listView: ListView, checked: Boolean): ArrayList<String> {
        val checks = listView.checkedItemPositions
        val items = ArrayList<String>()
        for (i in 0..checks.size() - 1) {
            val item = listView.adapter.getItem(checks.keyAt(i)) as String
            if (checks.valueAt(i) && checked) {
                items.add(item)
            } else if (!checks.valueAt(i) && !checked) {
                items.add(item)
            }
        }
        return items
    }

    fun createDeferDialog(act: Activity, dateType: Int, showNone: Boolean, listener: InputDialogListener): AlertDialog {
        var keys = act.resources.getStringArray(R.array.deferOptions)
        val today = "0d"
        val tomorrow = "1d"
        val oneWeek = "1w"
        val twoWeeks = "2w"
        val oneMonth = "1m"
        val values = arrayOf("", today, tomorrow, oneWeek, twoWeeks, oneMonth, "pick")
        if (!showNone) {
            keys = Arrays.copyOfRange(keys, 1, keys.size)
        }
        val titleId: Int
        if (dateType == Task.DUE_DATE) {
            titleId = R.string.defer_due
        } else {
            titleId = R.string.defer_threshold
        }

        val builder = AlertDialog.Builder(act)
        builder.setTitle(titleId)
        builder.setItems(keys) { dialog, whichButton ->
            var whichButton = whichButton
            if (!showNone) {
                whichButton++
            }
            val selected = values[whichButton]
            listener.onClick(selected)
        }
        return builder.create()
    }


    fun initGlobals(globals: Globals, t: Task) {
        globals.set("task", t.inFileFormat())

        if (t.dueDate != null) {
            globals.set("due", (t.dueDate!!.getMilliseconds(TimeZone.getDefault()) / 1000).toDouble())
        } else {
            globals.set("due", LuaValue.NIL)
        }


        if (t.thresholdDate != null) {
            globals.set("threshold", (t.thresholdDate!!.getMilliseconds(TimeZone.getDefault()) / 1000).toDouble())
        } else {
            globals.set("threshold", LuaValue.NIL)
        }


        if (t.createDate != null) {
            globals.set("createdate", (DateTime(t.createDate).getMilliseconds(TimeZone.getDefault()) / 1000).toDouble())
        } else {
            globals.set("createdate", LuaValue.NIL)
        }


        if (t.completionDate != null) {
            globals.set("completiondate", (DateTime(t.completionDate).getMilliseconds(TimeZone.getDefault()) / 1000).toDouble())
        } else {
            globals.set("completiondate", LuaValue.NIL)
        }

        globals.set("completed", LuaBoolean.valueOf(t.isCompleted))
        globals.set("priority", t.priority.code)

        globals.set("tags", javaListToLuaTable(t.tags))
        globals.set("lists", javaListToLuaTable(t.lists))
    }

    private fun javaListToLuaTable(javaList: List<String>): LuaValue {
        val size = javaList.size
        if (size == 0) return LuaValue.NIL
        val luaArray = arrayOfNulls<LuaString>(javaList.size)
        var i = 0
        for (item in javaList) {
            luaArray[i] = LuaString.valueOf(item)
            i++
        }
        return LuaTable.listOf(luaArray)

    }

    @Throws(IOException::class)
    fun createCachedFile(context: Context, fileName: String,
                         content: String) {

        val cacheFile = File(context.cacheDir , fileName)
        if (cacheFile.createNewFile()) {
            val fos = FileOutputStream(cacheFile, false)
            val osw = OutputStreamWriter(fos, "UTF8")
            val pw = PrintWriter(osw)
            pw.println(content)
            pw.flush()
            pw.close()
        }
    }

    @Throws(IOException::class)
    fun copyFile(sourceFile: File, destFile: File) {

        if (destFile.createNewFile()) {
            log.debug(TAG, "Destination file created {}"+  destFile.absolutePath)
        }

        var source: FileChannel? = null
        var destination: FileChannel? = null

        try {
            source = FileInputStream(sourceFile).channel
            destination = FileOutputStream(destFile).channel
            destination!!.transferFrom(source, 0, source!!.size())
        } finally {
            if (source != null) {
                source.close()
            }
            if (destination != null) {
                destination.close()
            }
        }
    }

    @Throws(IOException::class)
    fun createCachedDatabase(context: Context, dbFile: File) {
        val cacheFile = File(context.cacheDir, dbFile.name)
        copyFile(dbFile, cacheFile)
    }

    fun sortWithPrefix(items: List<String>, caseSensitive: Boolean, prefix: String?): ArrayList<String> {
        val result = ArrayList<String>()
        result.addAll(items)
        Collections.sort(result, AlphabeticalStringComparator(caseSensitive))
        if (prefix != null) {
            result.add(0, prefix)
        }
        return result
    }

    fun sortWithPrefix(items: Set<String>, caseSensitive: Boolean, prefix: String?): ArrayList<String> {
        val temp = ArrayList<String>()
        temp.addAll(items)
        return sortWithPrefix(temp, caseSensitive, prefix)
    }

    fun shareText(act: Activity, text: String) {

        val shareIntent = Intent(android.content.Intent.ACTION_SEND)
        shareIntent.setType("text/plain")
        shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT,
                "Simpletask list")

        // If text is small enough SEND it directly
        if (text.length < 50000) {
            shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, text)
        } else {

            // Create a cache file to pass in EXTRA_STREAM
            try {
                createCachedFile(act,
                        Constants.SHARE_FILE_NAME, text)
                val fileUri = Uri.parse("content://" + CachedFileProvider.AUTHORITY + "/" + Constants.SHARE_FILE_NAME)
                shareIntent.putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
            } catch (e: Exception) {
                log.warn(TAG, "Failed to create file for sharing")
            }

        }
        act.startActivity(Intent.createChooser(shareIntent, "Share"))
    }

    fun showLoadingOverlay(act: Activity, visibleDialog: Dialog?, show: Boolean): Dialog? {
        if (show) {
            val newDialog = Dialog(act)
            newDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            newDialog.setContentView(R.layout.loading)
            val pr = newDialog.findViewById(R.id.progress) as ProgressBar
            pr.indeterminateDrawable.setColorFilter(-16737844, android.graphics.PorterDuff.Mode.MULTIPLY)
            newDialog.window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            newDialog.setCancelable(false)
            newDialog.show()
            return newDialog
        } else if (visibleDialog != null && visibleDialog.isShowing) {
            visibleDialog.dismiss()
        }
        return null
    }
