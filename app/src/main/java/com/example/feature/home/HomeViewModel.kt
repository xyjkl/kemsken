package com.example.feature.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.database.DocumentEntity
import com.example.core.database.FolderEntity
import com.example.data.repository.DocumentRepository
import com.example.domain.model.SortOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val documents: List<DocumentEntity> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val selectedFolderId: String? = null,
    val isFavoritesOnly: Boolean = false,
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.NEWEST,
    val isGridView: Boolean = false,
    val selectedDocumentIds: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val isLoading: Boolean = false
)

class HomeViewModel(private val repository: DocumentRepository) : ViewModel() {

    private val _selectedFolderId = MutableStateFlow<String?>(null)
    private val _isFavoritesOnly = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")
    private val _sortOption = MutableStateFlow(SortOption.NEWEST)
    private val _isGridView = MutableStateFlow(false)
    private val _selectedDocumentIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isSelectionMode = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(
        repository.getAllActiveDocuments(),
        repository.getAllFolders(),
        _selectedFolderId,
        _isFavoritesOnly,
        _searchQuery,
        _sortOption,
        _isGridView,
        _selectedDocumentIds,
        _isSelectionMode
    ) { args: Array<Any?> ->
        val rawDocs = args[0] as List<DocumentEntity>
        val folders = args[1] as List<FolderEntity>
        val folderId = args[2] as String?
        val favOnly = args[3] as Boolean
        val query = args[4] as String
        val sort = args[5] as SortOption
        val isGrid = args[6] as Boolean
        val selectedIds = args[7] as Set<String>
        val isSelection = args[8] as Boolean

        // Filter documents
        var filtered = rawDocs.filter { doc ->
            (folderId == null || doc.folderId == folderId) &&
            (!favOnly || doc.isFavorite) &&
            (query.isBlank() || doc.title.contains(query, ignoreCase = true) || doc.tags.contains(query, ignoreCase = true))
        }

        // Sort documents
        filtered = when (sort) {
            SortOption.NEWEST -> filtered.sortedByDescending { it.createdAt }
            SortOption.OLDEST -> filtered.sortedBy { it.createdAt }
            SortOption.NAME_ASC -> filtered.sortedBy { it.title.lowercase() }
            SortOption.NAME_DESC -> filtered.sortedByDescending { it.title.lowercase() }
            SortOption.MODIFIED -> filtered.sortedByDescending { it.updatedAt }
            SortOption.PAGE_COUNT -> filtered.sortedByDescending { it.pageCount }
        }

        HomeUiState(
            documents = filtered,
            folders = folders,
            selectedFolderId = folderId,
            isFavoritesOnly = favOnly,
            searchQuery = query,
            sortOption = sort,
            isGridView = isGrid,
            selectedDocumentIds = selectedIds,
            isSelectionMode = isSelection,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true)
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onSelectFolder(folderId: String?) {
        _isFavoritesOnly.value = false
        _selectedFolderId.value = folderId
    }

    fun onToggleFavoritesOnly() {
        _selectedFolderId.value = null
        _isFavoritesOnly.value = !_isFavoritesOnly.value
    }

    fun onSortChange(sortOption: SortOption) {
        _sortOption.value = sortOption
    }

    fun onToggleGridList() {
        _isGridView.value = !_isGridView.value
    }

    fun onToggleFavorite(docId: String, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(docId, isFavorite)
        }
    }

    fun onRenameDocument(docId: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameDocument(docId, newTitle)
        }
    }

    fun onMoveToRecycleBin(docId: String) {
        viewModelScope.launch {
            repository.moveToRecycleBin(docId)
        }
    }

    fun onDuplicateDocument(docId: String) {
        viewModelScope.launch {
            repository.duplicateDocument(docId)
        }
    }

    fun onMoveToFolder(docId: String, folderId: String?) {
        viewModelScope.launch {
            repository.moveToFolder(docId, folderId)
        }
    }

    fun onCreateFolder(name: String, colorHex: String) {
        viewModelScope.launch {
            repository.createFolder(name, colorHex)
        }
    }

    fun toggleSelectionMode(enabled: Boolean) {
        _isSelectionMode.value = enabled
        if (!enabled) _selectedDocumentIds.value = emptySet()
    }

    fun toggleDocumentSelected(docId: String) {
        val current = _selectedDocumentIds.value.toMutableSet()
        if (current.contains(docId)) {
            current.remove(docId)
        } else {
            current.add(docId)
        }
        _selectedDocumentIds.value = current
        if (current.isEmpty()) {
            _isSelectionMode.value = false
        }
    }

    fun deleteSelectedDocuments() {
        viewModelScope.launch {
            for (id in _selectedDocumentIds.value) {
                repository.moveToRecycleBin(id)
            }
            toggleSelectionMode(false)
        }
    }

    class Factory(private val repository: DocumentRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository) as T
        }
    }
}
