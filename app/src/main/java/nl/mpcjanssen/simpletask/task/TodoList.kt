package nl.mpcjanssen.simpletask.task

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.CountDownTimer
import android.os.SystemClock
import android.util.Log
import nl.mpcjanssen.simpletask.*
import nl.mpcjanssen.simpletask.remote.FileStore
import nl.mpcjanssen.simpletask.remote.IFileStore
import nl.mpcjanssen.simpletask.util.*
import java.util.*
import kotlin.collections.ArrayList

/**
 * Implementation of the in memory representation of the Todo list
 * uses an ActionQueue to ensure modifications and access of the underlying todo list are
 * sequential. If this is not done properly the result is a likely ConcurrentModificationException.

 * @author Mark Janssen
 */
class TodoList(val config: Config) {
    private var timer: CountDownTimer? = null
    private var mLists: MutableList<String>? = null
    private var mTags: MutableList<String>? = null
    private val todoItems = ArrayList<Task>()
    val pendingEdits = HashSet<Task>()
    internal val TAG = TodoList::class.java.simpleName

    init {
        config.todoList?.let { todoItems.addAll(it) }
    }

    @Synchronized
    fun add(items: List<Task>, atEnd: Boolean) {
        Log.d(TAG, "Add task ${items.size} atEnd: $atEnd")
        val updatedItems = items.map { item ->
            Interpreter.onAddCallback(item) ?: item
        }
        if (atEnd) {
            todoItems.addAll(updatedItems)
        } else {
            todoItems.addAll(0, updatedItems)
        }

    }

    fun add(t: Task, atEnd: Boolean) {
        add(listOf(t), atEnd)
    }

    @Synchronized
    fun removeAll(tasks: List<Task>) {
        Log.d(TAG, "Remove")
        pendingEdits.removeAll(tasks)
        todoItems.removeAll(tasks)

    }

    @Synchronized
    fun size(): Int {
        return todoItems.size
    }

    val priorities: ArrayList<Priority>
        @Synchronized
        get() {
            val res = HashSet<Priority>()
            todoItems.forEach {
                res.add(it.priority)
            }
            val ret = ArrayList(res)
            ret.sort()
            return ret
        }

    val contexts: List<String>
        @Synchronized
        get() {
            val lists = mLists
            if (lists != null) {
                return lists
            }
            val res = HashSet<String>()
            todoItems.forEach { t ->
                t.lists?.let {res.addAll(it)}

            }
            val newLists = res.toMutableList()
            mLists = newLists
            return newLists
        }

    val projects: List<String>
        @Synchronized
        get() {
            val tags = mTags
            if (tags != null) {
                return tags
            }
            val res = HashSet<String>()
            todoItems.forEach { t ->
                t.tags?.let {res.addAll(it)}

            }
            val newTags = res.toMutableList()
            mTags = newTags
            return newTags
        }

    @Synchronized
    fun uncomplete(items: List<Task>) {
        Log.d(TAG, "Uncomplete")
        items.forEach {
            it.markIncomplete()
        }
    }

    @Synchronized
    fun complete(tasks: List<Task>, keepPrio: Boolean, extraAtEnd: Boolean) {
        Log.d(TAG, "Complete")
        for (task in tasks) {
            val extra = task.markComplete(todayAsString)
            if (extra != null) {
                if (extraAtEnd) {
                    todoItems.add(extra)
                } else {
                    todoItems.add(0, extra)
                }
            }
            if (!keepPrio) {
                task.priority = Priority.NONE
            }
        }

    }

    @Synchronized
    fun prioritize(tasks: List<Task>, prio: Priority) {
        Log.d(TAG, "Complete")
        tasks.map { it.priority = prio }
    }

    @Synchronized
    fun defer(deferString: String, tasks: List<Task>, dateType: DateType) {
        Log.d(TAG, "Defer")
        tasks.forEach {
            when (dateType) {
                DateType.DUE -> it.deferDueDate(deferString, todayAsString)
                DateType.THRESHOLD -> it.deferThresholdDate(deferString, todayAsString)
            }
        }
    }

    @Synchronized
    fun update(org: Collection<Task>, updated: List<Task>, addAtEnd: Boolean) {
        val smallestSize = org.zip(updated) { orgTask, updatedTask ->
            val idx = todoItems.indexOf(orgTask)
            if (idx != -1) {
                todoItems[idx] = updatedTask
            } else {
                todoItems.add(updatedTask)
            }
            1
        }.size
        removeAll(org.toMutableList().drop(smallestSize))
        add(updated.toMutableList().drop(smallestSize), addAtEnd)
    }

    val selectedTasks: List<Task>
        @Synchronized
        get() {
            return todoItems.toList().filter { it.selected }
        }

    val fileFormat : String =  todoItems.toList().joinToString(separator = "\n", transform = {
        it.inFileFormat(config.useUUIDs)
    })



    @Synchronized
    fun notifyTasklistChanged(uri: Uri?, save: Boolean, refreshMainUI: Boolean = true) {
        Log.d(TAG, "Notified changed")
        if (uri == null) {
            return
        }
        if (save) {
            save(FileStore, uri, true, config.eol)
        }
        if (!config.hasKeepSelection) {
            clearSelection()
        }
        mLists = null
        mTags = null
        if (refreshMainUI) {
            broadcastTasklistChanged(TodoApplication.app.localBroadCastManager)
        } else {
            broadcastRefreshWidgets(TodoApplication.app.localBroadCastManager)
        }
    }

    @Synchronized
    private fun startAddTaskActivity(act: Activity, prefill: String) {
        Log.d(TAG, "Start add/edit task activity")
        val intent = Intent(act, AddTask::class.java)
        intent.putExtra(Constants.EXTRA_PREFILL_TEXT, prefill)
        act.startActivity(intent)
    }

    @Synchronized
    fun getSortedTasks(filter: Query, caseSensitive: Boolean): Pair<List<Task>, Int> {
        val sorts = filter.getSort(config.defaultSorts)
        Log.d(TAG, "Getting sorted and filtered tasks")
        val start = SystemClock.elapsedRealtime()
        val comp = MultiComparator(sorts, TodoApplication.app.today, caseSensitive, filter.createIsThreshold, filter.luaModule)
        val listCopy = todoItems.toList()
        val taskCount = listCopy.size
        val itemsToSort = if (comp.fileOrder) {
            listCopy
        } else {
            listCopy.reversed()
        }
        val sortedItems = comp.comparator?.let { itemsToSort.sortedWith(it) } ?: itemsToSort
        val result = filter.applyFilter(sortedItems, showSelected = true)
        val end = SystemClock.elapsedRealtime()
        Log.d(TAG, "Sorting and filtering tasks took ${end - start} ms")
        return Pair(result, taskCount)

    }

    @Synchronized
    fun reload(reason: String = "") {
        Log.d(TAG, "Reload: $reason")
        broadcastFileSyncStart(TodoApplication.app.localBroadCastManager)
        val uri = config.todoUri
        if (uri == null) {
            broadcastFileSyncDone(TodoApplication.app.localBroadCastManager)
            return
        }
        if (config.changesPending ) {
            Log.i(TAG, "Not loading, changes pending")
            Log.i(TAG, "Saving instead of loading")
            save(FileStore, uri, true, config.eol)
        } else {

            FileStoreActionQueue.add("Reload") {

                val needSync = try {
                    val newerVersion = FileStore.getRemoteVersion(uri)
                    newerVersion != config.lastSeenRemoteId
                } catch (e: Throwable) {
                    Log.e(TAG, "Can't determine remote file version", e)
                    false
                }
                if (needSync) {
                    Log.i(TAG, "Remote version is different, sync")
                } else {
                    Log.i(TAG, "Remote version is same, load from cache")
                }
                val cachedList = config.todoList
                if (cachedList == null || needSync) {
                    try {
                        val remoteContents = FileStore.loadTasksFromFile(uri)
                        val items = ArrayList<Task>(
                                remoteContents.contents.map { text ->
                                    Task(text)
                                })

                        Log.d(TAG, "Fill todolist")
                        todoItems.clear()
                        todoItems.addAll(items)
                        // Update cache
                        config.cachedContents = remoteContents.contents.joinToString("\n")
                        config.lastSeenRemoteId = remoteContents.lastModified
                        // Backup
                        Backupper.backup(uri.toString(), items.map { it.inFileFormat(config.useUUIDs) })
                        notifyTasklistChanged(uri, false, true)
                    } catch (e: Exception) {
                        Log.e(TAG, "TodoList load failed: {}" + uri, e)
                        showToastShort(TodoApplication.app, "Loading of todo file failed")
                    }

                    Log.i(TAG, "TodoList loaded from dropbox")
                }
            }
            broadcastFileSyncDone(TodoApplication.app.localBroadCastManager)
        }
    }

    @Synchronized
    private fun save(fileStore: IFileStore, uri: Uri, backup: Boolean, eol: String) {
        config.changesPending = true
        broadcastUpdateStateIndicator(TodoApplication.app.localBroadCastManager)
        val lines = todoItems.map {
            it.inFileFormat(config.useUUIDs)
        }
        // Update cache
        config.cachedContents = lines.joinToString("\n")
        FileStoreActionQueue.add("Backup") {
            if (backup) {
                Backupper.backup(uri.toString(), lines)
            }
        }
        runOnMainThread(Runnable {
        timer?.apply { cancel() }

        timer = object: CountDownTimer(3000, 1000) {
            override fun onFinish() {
                Log.d(TAG, "Executing pending Save")
                FileStoreActionQueue.add("Save") {
                    broadcastFileSyncStart(TodoApplication.app.localBroadCastManager)
                    try {
                        Log.i(TAG, "Saving todo list, size ${lines.size}")
                        fileStore.saveTasksToFile(uri, lines, eol = eol)
                        val changesWerePending = config.changesPending
                        config.changesPending = false
                        if (changesWerePending) {
                            // Remove the red bar
                            broadcastUpdateStateIndicator(TodoApplication.app.localBroadCastManager)
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "TodoList save to $uri failed", e)
                        config.changesPending = true
                    }
                    broadcastFileSyncDone(TodoApplication.app.localBroadCastManager)
                }
            }

            override fun onTick(p0: Long) {
               Log.d(TAG, "Scheduled save in ${p0}")
            }
        }.start()})
    }

    @Synchronized
    fun archive(todoFilename: String, doneFileName: String, tasks: List<Task>, eol: String) {
//        Log.d(TAG, "Archive ${tasks.size} tasks")
//
//        FileStoreActionQueue.add("Append to file") {
//            broadcastFileSyncStart(TodoApplication.app.localBroadCastManager)
//            try {
//                FileStore.appendTaskToFile(doneFileName, tasks.map { it.text }, eol)
//                removeAll(tasks)
//                notifyTasklistChanged(todoFilename, true, true)
//            } catch (e: Exception) {
//                Log.e(TAG, "Task archiving failed", e)
//                showToastShort(TodoApplication.app, "Task archiving failed")
//            }
//            broadcastFileSyncDone(TodoApplication.app.localBroadCastManager)
//        }
    }

    fun isSelected(item: Task): Boolean = item.selected

    @Synchronized
    fun numSelected(): Int {
        return todoItems.toList().count { it.selected }
    }

    @Synchronized
    fun selectTasks(items: List<Task>) {
        Log.d(TAG, "Select")
        items.forEach { selectTask(it) }
        broadcastRefreshSelection(TodoApplication.app.localBroadCastManager)
    }

    @Synchronized
    private fun selectTask(item: Task?) {
        item?.selected = true
    }

    @Synchronized
    private fun unSelectTask(item: Task) {
        item.selected = false
    }

    @Synchronized
    fun unSelectTasks(items: List<Task>) {
        Log.d(TAG, "Unselect")
        items.forEach { unSelectTask(it) }
        broadcastRefreshSelection(TodoApplication.app.localBroadCastManager)

    }

    @Synchronized
    fun clearSelection() {
        Log.d(TAG, "Clear selection")
        todoItems.iterator().forEach { it.selected = false }
        broadcastRefreshSelection(TodoApplication.app.localBroadCastManager)

    }

    @Synchronized
    fun getTaskIndex(t: Task): Int {
        return todoItems.indexOf(t)
    }

    @Synchronized
    fun getTaskAt(idx: Int): Task? {
        return todoItems.getOrNull(idx)
    }

    @Synchronized
    fun each (callback : (Task) -> Unit) {
        todoItems.forEach { callback.invoke(it) }
    }

    @Synchronized
    fun editTasks(from: Activity, tasks: List<Task>, prefill: String) {
        Log.d(TAG, "Edit tasks")
        pendingEdits.addAll(tasks)
        startAddTaskActivity(from, prefill)
    }

    @Synchronized
    fun clearPendingEdits() {
        Log.d(TAG, "Clear selection")
        pendingEdits.clear()
    }
}

