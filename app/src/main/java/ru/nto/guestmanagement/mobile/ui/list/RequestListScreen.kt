package ru.nto.guestmanagement.mobile.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.nto.guestmanagement.mobile.data.ApiResult
import ru.nto.guestmanagement.mobile.data.RequestStatus
import ru.nto.guestmanagement.mobile.data.RequestSummary
import ru.nto.guestmanagement.mobile.data.RequestsApi
import ru.nto.guestmanagement.mobile.data.formatDate
import ru.nto.guestmanagement.mobile.data.formatInterval
import ru.nto.guestmanagement.mobile.di.AppContainer
import ru.nto.guestmanagement.mobile.ui.TestIds
import ru.nto.guestmanagement.mobile.ui.common.StatusLabel
import ru.nto.guestmanagement.mobile.ui.common.containerViewModel
import ru.nto.guestmanagement.mobile.ui.refresh.RefreshGate
import ru.nto.guestmanagement.mobile.ui.refresh.ResumeEffect

enum class FilterOption(
    val title: String,
    val status: RequestStatus?,
    val tag: String,
) {
    ALL("Все", null, TestIds.REQUESTS_FILTER_ALL),
    PENDING("На рассмотрении", RequestStatus.PENDING, TestIds.REQUESTS_FILTER_PENDING),
    APPROVED("Одобрены", RequestStatus.APPROVED, TestIds.REQUESTS_FILTER_APPROVED),
    REJECTED("Отклонены", RequestStatus.REJECTED, TestIds.REQUESTS_FILTER_REJECTED),
    CANCELLED("Отменены", RequestStatus.CANCELLED, TestIds.REQUESTS_FILTER_CANCELLED),
}

class RequestListViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val api = RequestsApi(container.httpClient)
    private val refreshGate = RefreshGate()

    var selectedFilter by mutableStateOf(FilterOption.ALL)
        private set

    var items by mutableStateOf<List<RequestSummary>?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var initialError by mutableStateOf<String?>(null)
        private set

    var isOffline by mutableStateOf(false)
        private set

    var sessionExpiredEvent by mutableStateOf(false)
        private set

    var logoutEvent by mutableStateOf(false)
        private set

    fun refresh() {
        refreshGate.launch(viewModelScope) {
            isLoading = true
            val code = container.session.current()
            if (code.isNullOrEmpty()) {
                isLoading = false
                container.session.clear()
                sessionExpiredEvent = true
                return@launch
            }

            when (val result = api.list(code, selectedFilter.status)) {
                is ApiResult.Success -> {
                    items = result.value
                    initialError = null
                    isOffline = false
                }
                is ApiResult.Unauthorized -> {
                    container.session.clear()
                    sessionExpiredEvent = true
                }
                is ApiResult.Failure, ApiResult.Network, ApiResult.NotFound, is ApiResult.Conflict, is ApiResult.FieldErrors -> {
                    if (items == null) {
                        initialError = "Не удалось загрузить заявки"
                        isOffline = false
                    } else {
                        initialError = null
                        isOffline = true
                    }
                }
            }
            isLoading = false
        }
    }

    fun selectFilter(option: FilterOption) {
        if (selectedFilter == option) return
        selectedFilter = option
        refreshGate.cancel()
        items = null
        initialError = null
        isOffline = false
        refresh()
    }

    fun logout() {
        refreshGate.cancel()
        viewModelScope.launch {
            container.session.clear()
            logoutEvent = true
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RequestListScreen(
    container: AppContainer,
    onOpenDetails: (Long) -> Unit,
    onAddRequest: () -> Unit,
    onLogout: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val vm = containerViewModel { RequestListViewModel(container) }

    LaunchedEffect(vm.sessionExpiredEvent) {
        if (vm.sessionExpiredEvent) {
            onSessionExpired()
        }
    }

    LaunchedEffect(vm.logoutEvent) {
        if (vm.logoutEvent) {
            onLogout()
        }
    }

    ResumeEffect {
        vm.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мои заявки") },
                actions = {
                    OutlinedButton(
                        onClick = vm::refresh,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag(TestIds.REQUESTS_REFRESH),
                    ) {
                        Text("Обновить", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = vm::logout,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag(TestIds.REQUESTS_LOGOUT),
                    ) {
                        Text("Выйти", style = MaterialTheme.typography.labelMedium)
                    }
                },
            )
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Button(
                    onClick = onAddRequest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag(TestIds.REQUESTS_ADD),
                ) {
                    Text("Новая заявка")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                FilterOption.entries.forEach { option ->
                    FilterChip(
                        selected = vm.selectedFilter == option,
                        onClick = { vm.selectFilter(option) },
                        label = {
                            Text(
                                text = option.title,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        },
                        modifier = Modifier.testTag(option.tag),
                    )
                }
            }

            if (vm.isOffline && vm.items != null) {
                Text(
                    text = "Нет связи",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag(TestIds.REQUESTS_OFFLINE),
                )
            }

            val currentItems = vm.items
            val error = vm.initialError

            when {
                currentItems != null -> {
                    if (currentItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Заявок нет",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.testTag(TestIds.REQUESTS_EMPTY),
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            itemsIndexed(
                                items = currentItems,
                                key = { _, item -> item.id },
                            ) { index, item ->
                                RequestItemCard(
                                    item = item,
                                    index = index,
                                    onClick = { onOpenDetails(item.id) },
                                )
                            }
                        }
                    }
                }
                error != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.testTag(TestIds.REQUESTS_ERROR),
                        )
                    }
                }
                vm.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestItemCard(
    item: RequestSummary,
    index: Int,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestIds.requestsPos(index)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.fullName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                        .testTag(TestIds.REQUEST_NAME),
                )
                StatusLabel(
                    status = item.status,
                    tag = TestIds.REQUEST_STATUS,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = formatDate(item.validFrom),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag(TestIds.REQUEST_DATE),
                )
                Text(
                    text = formatInterval(item.validFrom, item.validTo),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag(TestIds.REQUEST_TIME),
                )
            }
        }
    }
}
