package com.imayx.host.ui.vm

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.imayx.host.data.ProcessState
import com.imayx.host.data.ProjectConfig
import com.imayx.host.data.ProjectInfo
import com.imayx.host.data.RuntimeType
import com.imayx.host.graph
import com.imayx.host.projects.ZipInfo
import com.imayx.host.service.HostService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ProjectsUi(
    val loading: Boolean = true,
    val items: List<ProjectInfo> = emptyList(),
    val error: String? = null,
    /** Non-null while a long operation (import/export/duplicate) runs. */
    val busy: String? = null,
    val progress: Float? = null
)

data class ImportStage(
    val file: File,
    val info: ZipInfo,
    val sourceName: String,
    val suggestedName: String
)

class ProjectsViewModel(app: Application) : AndroidViewModel(app) {
    private val g = app.graph

    private val _ui = MutableStateFlow(ProjectsUi())
    val ui: StateFlow<ProjectsUi> = _ui

    private val _stage = MutableStateFlow<ImportStage?>(null)
    val stage: StateFlow<ImportStage?> = _stage

    /** Name of a project that was just imported, so the UI can offer "Start Hosting". */
    private val _imported = MutableStateFlow<String?>(null)
    val imported: StateFlow<String?> = _imported

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    val activeProject: String get() = g.settings.activeProject
    val hostState get() = g.host.state

    fun nodeEnv(project: String): Map<String, String> = g.secrets.getEnv(project)

    fun saveNodeEnv(project: String, env: Map<String, String>) {
        g.secrets.setEnv(project, env)
        _messages.tryEmit("Environment saved securely")
    }

    fun nodeInstalled(): Boolean = g.nodeInstaller.isInstalled()

    private fun isHosted(name: String): Boolean {
        val h = g.host.state.value
        return h.project == name && h.state != ProcessState.STOPPED
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val items = withContext(Dispatchers.IO) { g.projects.listInfo() }
                _ui.value = _ui.value.copy(loading = false, items = items, error = null)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = e.message ?: "Could not load projects")
            }
        }
    }

    private fun runOp(label: String, successMessage: String?, block: () -> Unit) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = label, progress = null)
            try {
                withContext(Dispatchers.IO) { block() }
                if (successMessage != null) _messages.emit(successMessage)
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Operation failed")
            } finally {
                _ui.value = _ui.value.copy(busy = null, progress = null)
                refresh()
            }
        }
    }

    fun create(name: String, runtime: RuntimeType) =
        runOp("Creating project…", "Project \"$name\" created") { g.projects.create(name, runtime) }

    fun rename(old: String, new: String) {
        if (isHosted(old)) {
            _messages.tryEmit("Stop hosting before renaming this project")
            return
        }
        runOp("Renaming…", "Renamed to \"$new\"") { g.projects.rename(old, new) }
    }

    fun delete(name: String) {
        if (isHosted(name)) {
            _messages.tryEmit("Stop hosting before deleting this project")
            return
        }
        runOp("Deleting…", "Project \"$name\" deleted") { g.projects.delete(name) }
    }

    fun duplicate(name: String, newName: String) =
        runOp("Duplicating…", "Duplicated as \"$newName\"") { g.projects.duplicate(name, newName) }

    fun setActive(name: String) {
        g.projects.setActive(name)
        refresh()
        _messages.tryEmit("\"$name\" is now the active project")
    }

    fun saveConfig(cfg: ProjectConfig) {
        if (isHosted(cfg.name)) {
            _messages.tryEmit("Saved. Restart the host to apply changes")
        } else {
            _messages.tryEmit("Project settings saved")
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { g.projects.save(cfg) }
            refresh()
        }
    }

    fun suggestedPort(): Int = g.settings.defaultPort

    // ---- Import ---------------------------------------------------------------------------------

    fun stageImport(uri: Uri) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = "Reading ZIP…", progress = null)
            try {
                val stage = withContext(Dispatchers.IO) {
                    val (file, info) = g.projects.stageZip(uri)
                    ImportStage(file, info, g.projects.displayName(uri), g.projects.suggestName(uri))
                }
                _stage.value = stage
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Could not read the selected file")
            } finally {
                _ui.value = _ui.value.copy(busy = null)
            }
        }
    }

    fun cancelImport() {
        _stage.value?.file?.delete()
        _stage.value = null
    }

    fun confirmImport(name: String) {
        val st = _stage.value ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = "Importing…", progress = 0f)
            try {
                withContext(Dispatchers.IO) {
                    g.projects.importStaged(st.file, st.info, name) { done, total ->
                        if (total > 0) _ui.value = _ui.value.copy(progress = done.toFloat() / total)
                    }
                }
                _stage.value = null
                _imported.value = name
                _messages.emit("Project ready")
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Import failed")
            } finally {
                _ui.value = _ui.value.copy(busy = null, progress = null)
                refresh()
            }
        }
    }

    fun clearImported() {
        _imported.value = null
    }

    fun startHosting(name: String) {
        g.projects.setActive(name)
        HostService.start(getApplication<Application>(), name)
        _imported.value = null
    }

    // ---- Export ---------------------------------------------------------------------------------

    fun exportTo(project: String, uri: Uri) {
        runOp("Exporting ZIP…", "Project exported") {
            val out = getApplication<Application>().contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Cannot write to the selected location")
            out.use { g.projects.exportZip(project, it) }
        }
    }
}
