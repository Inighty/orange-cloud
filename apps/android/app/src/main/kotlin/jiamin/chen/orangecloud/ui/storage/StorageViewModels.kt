package jiamin.chen.orangecloud.ui.storage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jiamin.chen.orangecloud.core.auth.AuthRepository
import jiamin.chen.orangecloud.core.auth.Scopes
import jiamin.chen.orangecloud.data.model.D1Database
import jiamin.chen.orangecloud.data.model.D1QueryResult
import jiamin.chen.orangecloud.data.model.KVKey
import jiamin.chen.orangecloud.data.model.KVNamespace
import jiamin.chen.orangecloud.data.model.R2Bucket
import jiamin.chen.orangecloud.data.model.R2Object
import jiamin.chen.orangecloud.data.repository.AccountStore
import jiamin.chen.orangecloud.data.repository.StorageRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// MARK: - 简单列表（存储桶 / 数据库 / 命名空间）

@HiltViewModel
class R2BucketListViewModel @Inject constructor(
    accountStore: AccountStore,
    private val storageRepository: StorageRepository,
    authRepository: AuthRepository,
) : StorageListViewModel<R2Bucket>(accountStore, authRepository.hasScope(Scopes.R2_READ)) {
    override suspend fun fetch(accountId: String) = storageRepository.listBuckets(accountId)
    init { load() }
}

@HiltViewModel
class D1DatabaseListViewModel @Inject constructor(
    accountStore: AccountStore,
    private val storageRepository: StorageRepository,
    authRepository: AuthRepository,
) : StorageListViewModel<D1Database>(accountStore, authRepository.hasScope(Scopes.D1_READ)) {
    override suspend fun fetch(accountId: String) = storageRepository.listDatabases(accountId)
    init { load() }
}

@HiltViewModel
class KVNamespaceListViewModel @Inject constructor(
    accountStore: AccountStore,
    private val storageRepository: StorageRepository,
    authRepository: AuthRepository,
) : StorageListViewModel<KVNamespace>(accountStore, authRepository.hasScope(Scopes.KV_READ)) {
    override suspend fun fetch(accountId: String) = storageRepository.listNamespaces(accountId)
    init { load() }
}

// MARK: - R2 对象（游标分页）

data class R2ObjectUiState(
    val objects: List<R2Object> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasError: Boolean = false,
    val missingScope: Boolean = false,
    val hasMore: Boolean = false,
)

@HiltViewModel
class R2ObjectListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountStore: AccountStore,
    private val storageRepository: StorageRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    val bucket: String = checkNotNull(savedStateHandle["bucket"])
    private val hasScope = authRepository.hasScope(Scopes.R2_READ)
    private var cursor: String? = null

    private val _uiState = MutableStateFlow(R2ObjectUiState(isLoading = hasScope, missingScope = !hasScope))
    val uiState: StateFlow<R2ObjectUiState> = _uiState.asStateFlow()

    init {
        if (hasScope) loadFirst()
    }

    fun loadFirst() {
        if (!hasScope) return
        cursor = null
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, hasError = false, objects = emptyList()) }
            fetchPage(reset = true)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun loadMore() {
        if (!hasScope || cursor == null || _uiState.value.isLoadingMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            fetchPage(reset = false)
            _uiState.update { it.copy(isLoadingMore = false) }
        }
    }

    private suspend fun fetchPage(reset: Boolean) {
        try {
            accountStore.ensureLoaded()
            val accountId = accountStore.selectedAccountId.value ?: run {
                _uiState.update { it.copy(hasError = true) }
                return
            }
            val (objects, next) = storageRepository.listObjects(accountId, bucket, cursor)
            cursor = next
            _uiState.update {
                it.copy(objects = if (reset) objects else it.objects + objects, hasMore = next != null)
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(hasError = true) }
        }
    }
}

// MARK: - D1 查询控制台

data class D1QueryUiState(
    val results: List<D1QueryResult> = emptyList(),
    val columns: List<String> = emptyList(),
    val isRunning: Boolean = false,
    val error: String? = null,
    val missingScope: Boolean = false,
)

@HiltViewModel
class D1QueryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountStore: AccountStore,
    private val storageRepository: StorageRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    val databaseId: String = checkNotNull(savedStateHandle["dbId"])
    val databaseName: String = savedStateHandle.get<String>("dbName").orEmpty()
    private val hasScope = authRepository.hasScope(Scopes.D1_READ)

    private val _uiState = MutableStateFlow(D1QueryUiState(missingScope = !hasScope))
    val uiState: StateFlow<D1QueryUiState> = _uiState.asStateFlow()

    fun run(sql: String) {
        if (!hasScope || sql.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRunning = true, error = null) }
            try {
                accountStore.ensureLoaded()
                val accountId = accountStore.selectedAccountId.value ?: error("no account")
                val results = storageRepository.query(accountId, databaseId, sql.trim())
                val columns = results.firstOrNull()?.results?.firstOrNull()?.keys?.toList().orEmpty()
                _uiState.update { it.copy(results = results, columns = columns) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "error", results = emptyList(), columns = emptyList()) }
            } finally {
                _uiState.update { it.copy(isRunning = false) }
            }
        }
    }
}

// MARK: - KV 键列表（游标分页）+ 值读写删

sealed interface KVEvent {
    data object Saved : KVEvent
    data object Deleted : KVEvent
    data class Error(val cfMessage: String?) : KVEvent
}

data class KVKeyUiState(
    val keys: List<KVKey> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasError: Boolean = false,
    val missingScope: Boolean = false,
    val hasMore: Boolean = false,
    val canWrite: Boolean = false,
)

@HiltViewModel
class KVKeyListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountStore: AccountStore,
    private val storageRepository: StorageRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    val namespaceId: String = checkNotNull(savedStateHandle["nsId"])
    val namespaceTitle: String = savedStateHandle.get<String>("nsTitle").orEmpty()
    private val hasRead = authRepository.hasScope(Scopes.KV_READ)
    private val canWrite = authRepository.hasScope(Scopes.KV_WRITE)
    private var cursor: String? = null

    private val _uiState = MutableStateFlow(
        KVKeyUiState(isLoading = hasRead, missingScope = !hasRead, canWrite = canWrite),
    )
    val uiState: StateFlow<KVKeyUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<KVEvent>(Channel.BUFFERED)
    val events: Flow<KVEvent> = eventChannel.receiveAsFlow()

    init {
        if (hasRead) loadFirst()
    }

    fun loadFirst() {
        if (!hasRead) return
        cursor = null
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, hasError = false, keys = emptyList()) }
            fetchPage(reset = true)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun loadMore() {
        if (!hasRead || cursor == null || _uiState.value.isLoadingMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            fetchPage(reset = false)
            _uiState.update { it.copy(isLoadingMore = false) }
        }
    }

    private suspend fun fetchPage(reset: Boolean) {
        try {
            accountStore.ensureLoaded()
            val accountId = accountStore.selectedAccountId.value ?: run {
                _uiState.update { it.copy(hasError = true) }
                return
            }
            val (keys, next) = storageRepository.listKeys(accountId, namespaceId, cursor)
            cursor = next
            _uiState.update {
                it.copy(keys = if (reset) keys else it.keys + keys, hasMore = next != null)
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(hasError = true) }
        }
    }

    /** 读取键值（UTF-8 文本）。 */
    suspend fun loadValue(key: String): String? {
        val accountId = accountStore.selectedAccountId.value ?: return null
        return runCatching {
            storageRepository.getValue(accountId, namespaceId, key).decodeToString()
        }.getOrNull()
    }

    fun saveValue(key: String, value: String) {
        if (!canWrite) return
        viewModelScope.launch {
            try {
                val accountId = accountStore.selectedAccountId.value ?: error("no account")
                storageRepository.putValue(accountId, namespaceId, key, value)
                eventChannel.send(KVEvent.Saved)
                loadFirst()
            } catch (e: Exception) {
                eventChannel.send(KVEvent.Error(e.message))
            }
        }
    }

    fun deleteKey(key: String) {
        if (!canWrite) return
        viewModelScope.launch {
            try {
                val accountId = accountStore.selectedAccountId.value ?: error("no account")
                storageRepository.deleteKey(accountId, namespaceId, key)
                eventChannel.send(KVEvent.Deleted)
                loadFirst()
            } catch (e: Exception) {
                eventChannel.send(KVEvent.Error(e.message))
            }
        }
    }
}
