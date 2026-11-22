package ru.nto.guestmanagement.mobile.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.nto.guestmanagement.mobile.data.ApiResult
import ru.nto.guestmanagement.mobile.data.RequestDetails
import ru.nto.guestmanagement.mobile.data.RequestStatus
import ru.nto.guestmanagement.mobile.data.RequestsApi
import ru.nto.guestmanagement.mobile.data.formatDateTime
import ru.nto.guestmanagement.mobile.di.AppContainer
import ru.nto.guestmanagement.mobile.ui.TestIds
import ru.nto.guestmanagement.mobile.ui.common.StatusLabel
import ru.nto.guestmanagement.mobile.ui.common.containerViewModel
import ru.nto.guestmanagement.mobile.ui.refresh.RefreshGate
import ru.nto.guestmanagement.mobile.ui.refresh.ResumeEffect

class RequestDetailsViewModel(
    private val container: AppContainer,
    private val requestId: Long,
) : ViewModel() {
    private val api = RequestsApi(container.httpClient)
    private val refreshGate = RefreshGate()

    var details by mutableStateOf<RequestDetails?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var initialError by mutableStateOf<String?>(null)
        private set

    var isOffline by mutableStateOf(false)
        private set

    var showCancelConfirmDialog by mutableStateOf(false)
        private set

    var isCancelling by mutableStateOf(false)
        private set

    var cancelError by mutableStateOf<String?>(null)
        private set

    var sessionExpiredEvent by mutableStateOf(false)
        private set

    fun refresh(clearCancelError: Boolean = true) {
        refreshGate.launch(viewModelScope) {
            isLoading = true
            if (clearCancelError) {
                cancelError = null
            }
            val code = container.session.current()
            if (code.isNullOrEmpty()) {
                isLoading = false
                container.session.clear()
                sessionExpiredEvent = true
                return@launch
            }

            when (val result = api.get(code, requestId)) {
                is ApiResult.Success -> {
                    details = result.value
                    initialError = null
                    isOffline = false
                }
                is ApiResult.Unauthorized -> {
                    container.session.clear()
                    sessionExpiredEvent = true
                }
                is ApiResult.NotFound -> {
                    if (details == null) {
                        initialError = "Заявка не найдена"
                        isOffline = false
                    } else {
                        isOffline = true
                    }
                }
                is ApiResult.Failure, ApiResult.Network, is ApiResult.Conflict, is ApiResult.FieldErrors -> {
                    if (details == null) {
                        initialError = "Не удалось загрузить детали заявки"
                        isOffline = false
                    } else {
                        isOffline = true
                    }
                }
            }
            isLoading = false
        }
    }

    fun requestCancel() {
        showCancelConfirmDialog = true
    }

    fun dismissCancelDialog() {
        showCancelConfirmDialog = false
    }

    fun confirmCancel() {
        if (isCancelling) return
        showCancelConfirmDialog = false
        isCancelling = true
        cancelError = null

        viewModelScope.launch {
            val code = container.session.current()
            if (code.isNullOrEmpty()) {
                isCancelling = false
                container.session.clear()
                sessionExpiredEvent = true
                return@launch
            }

            when (val result = api.cancel(code, requestId)) {
                is ApiResult.Success -> {
                    isCancelling = false
                    details = result.value
                    cancelError = null
                    isOffline = false
                }
                is ApiResult.Unauthorized -> {
                    isCancelling = false
                    container.session.clear()
                    sessionExpiredEvent = true
                }
                is ApiResult.Conflict -> {
                    isCancelling = false
                    cancelError = "Заявка уже рассмотрена"
                    refreshGate.cancel()
                    refresh(clearCancelError = false)
                }
                is ApiResult.NotFound -> {
                    isCancelling = false
                    cancelError = "Заявка не найдена"
                }
                is ApiResult.Failure -> {
                    isCancelling = false
                    cancelError = result.message?.takeIf { it.isNotBlank() } ?: "Не удалось отменить заявку"
                }
                is ApiResult.Network, is ApiResult.FieldErrors -> {
                    isCancelling = false
                    cancelError = "Нет связи с сервером"
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestDetailsScreen(
    container: AppContainer,
    requestId: Long,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val vm = containerViewModel { RequestDetailsViewModel(container, requestId) }

    LaunchedEffect(vm.sessionExpiredEvent) {
        if (vm.sessionExpiredEvent) {
            onSessionExpired()
        }
    }

    ResumeEffect {
        vm.refresh()
    }

    if (vm.showCancelConfirmDialog) {
        AlertDialog(
            onDismissRequest = vm::dismissCancelDialog,
            title = { Text("Отмена заявки") },
            text = { Text("Вы действительно хотите отменить эту заявку?") },
            confirmButton = {
                Button(
                    onClick = vm::confirmCancel,
                    modifier = Modifier.testTag(TestIds.DETAILS_CANCEL_CONFIRM),
                ) {
                    Text("Отменить заявку")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = vm::dismissCancelDialog,
                    modifier = Modifier.testTag(TestIds.DETAILS_CANCEL_DISMISS),
                ) {
                    Text("Назад")
                }
            },
        )
    }

    val currentDetails = vm.details

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Детали заявки") },
                navigationIcon = {
                    OutlinedButton(
                        onClick = onBack,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .testTag(TestIds.DETAILS_BACK),
                    ) {
                        Text("Назад")
                    }
                },
                actions = {
                    OutlinedButton(
                        onClick = { vm.refresh() },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag(TestIds.DETAILS_REFRESH),
                    ) {
                        Text("Обновить")
                    }
                },
            )
        },
        bottomBar = {
            if (currentDetails != null && currentDetails.status == RequestStatus.PENDING) {
                Surface(shadowElevation = 4.dp) {
                    Button(
                        onClick = vm::requestCancel,
                        enabled = !vm.isCancelling,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag(TestIds.DETAILS_CANCEL),
                    ) {
                        Text("Отменить заявку")
                    }
                }
            }
        },
    ) { padding ->
        val error = vm.initialError

        when {
            currentDetails != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (vm.isOffline) {
                        Text(
                            text = "Нет связи",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(TestIds.DETAILS_OFFLINE),
                        )
                    }

                    vm.cancelError?.let { cancelErr ->
                        Text(
                            text = cancelErr,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(TestIds.DETAILS_CANCEL_ERROR),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Заявка №",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = currentDetails.id.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag(TestIds.DETAILS_ID),
                            )
                        }
                        StatusLabel(
                            status = currentDetails.status,
                            tag = TestIds.DETAILS_STATUS,
                        )
                    }

                    HorizontalDivider()

                    DetailField(
                        label = "ФИО гостя",
                        value = currentDetails.fullName,
                        valueTag = TestIds.DETAILS_NAME,
                    )

                    DetailField(
                        label = "Телефон",
                        value = currentDetails.phone,
                        valueTag = TestIds.DETAILS_PHONE,
                    )

                    val org = currentDetails.organization
                    if (!org.isNullOrBlank()) {
                        DetailField(
                            label = "Организация",
                            value = org,
                            valueTag = TestIds.DETAILS_ORGANIZATION,
                        )
                    }

                    DetailField(
                        label = "Цель визита",
                        value = currentDetails.purpose,
                        valueTag = TestIds.DETAILS_PURPOSE,
                    )

                    DetailField(
                        label = "Начало действия",
                        value = formatDateTime(currentDetails.validFrom),
                        valueTag = TestIds.DETAILS_VALID_FROM,
                    )

                    DetailField(
                        label = "Окончание действия",
                        value = formatDateTime(currentDetails.validTo),
                        valueTag = TestIds.DETAILS_VALID_TO,
                    )

                    DetailField(
                        label = "Создана",
                        value = formatDateTime(currentDetails.createdAt),
                        valueTag = TestIds.DETAILS_CREATED_AT,
                    )

                    DetailField(
                        label = "Обновлена",
                        value = formatDateTime(currentDetails.updatedAt),
                        valueTag = TestIds.DETAILS_UPDATED_AT,
                    )

                    if (currentDetails.status == RequestStatus.REJECTED) {
                        DetailField(
                            label = "Комментарий охраны",
                            value = currentDetails.comment.orEmpty(),
                            valueTag = TestIds.DETAILS_COMMENT,
                        )
                    }
                }
            }
            error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag(TestIds.DETAILS_ERROR),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { vm.refresh() },
                        modifier = Modifier.testTag(TestIds.DETAILS_RETRY),
                    ) {
                        Text("Повторить")
                    }
                }
            }
            vm.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun DetailField(
    label: String,
    value: String,
    valueTag: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .padding(top = 2.dp)
                .testTag(valueTag),
        )
    }
}
