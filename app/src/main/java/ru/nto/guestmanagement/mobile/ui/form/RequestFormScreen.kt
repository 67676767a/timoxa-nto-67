package ru.nto.guestmanagement.mobile.ui.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.nto.guestmanagement.mobile.data.ApiResult
import ru.nto.guestmanagement.mobile.data.CreateRequestBody
import ru.nto.guestmanagement.mobile.data.RequestsApi
import ru.nto.guestmanagement.mobile.data.toApi
import ru.nto.guestmanagement.mobile.di.AppContainer
import ru.nto.guestmanagement.mobile.ui.TestIds
import ru.nto.guestmanagement.mobile.ui.common.containerViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

private val STRICT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.uuuu")
    .withResolverStyle(ResolverStyle.STRICT)
private val STRICT_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
    .withResolverStyle(ResolverStyle.STRICT)
private val PHONE_REGEX = Regex("^\\+7[0-9]{10}$")

data class FormErrors(
    val fullName: String? = null,
    val phone: String? = null,
    val organization: String? = null,
    val purpose: String? = null,
    val date: String? = null,
    val validFrom: String? = null,
    val validTo: String? = null,
) {
    val hasErrors: Boolean
        get() = listOf(fullName, phone, organization, purpose, date, validFrom, validTo).any { it != null }
}

class RequestFormViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val api = RequestsApi(container.httpClient)

    var fullName by mutableStateOf("")
        private set
    var phone by mutableStateOf("")
        private set
    var organization by mutableStateOf("")
        private set
    var purpose by mutableStateOf("")
        private set
    var date by mutableStateOf("")
        private set
    var timeFrom by mutableStateOf("")
        private set
    var timeTo by mutableStateOf("")
        private set

    var hasAttemptedSubmit by mutableStateOf(false)
        private set

    var clientErrors by mutableStateOf(FormErrors())
        private set

    var serverFieldErrors by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var commonError by mutableStateOf<String?>(null)
        private set

    var isSubmitting by mutableStateOf(false)
        private set

    var createdEvent by mutableStateOf(false)
        private set

    var sessionExpiredEvent by mutableStateOf(false)
        private set

    val effectiveFullNameError: String? get() = clientErrors.fullName ?: serverFieldErrors["fullName"]
    val effectivePhoneError: String? get() = clientErrors.phone ?: serverFieldErrors["phone"]
    val effectiveOrganizationError: String? get() = clientErrors.organization ?: serverFieldErrors["organization"]
    val effectivePurposeError: String? get() = clientErrors.purpose ?: serverFieldErrors["purpose"]
    val effectiveDateError: String? get() = clientErrors.date
    val effectiveValidFromError: String? get() = clientErrors.validFrom ?: serverFieldErrors["validFrom"]
    val effectiveValidToError: String? get() = clientErrors.validTo ?: serverFieldErrors["validTo"]
    fun onFullNameChange(v: String) {
        fullName = v
        serverFieldErrors = serverFieldErrors - "fullName"
        commonError = null
        if (hasAttemptedSubmit) revalidate()
    }

    fun onPhoneChange(v: String) {
        phone = v
        serverFieldErrors = serverFieldErrors - "phone"
        commonError = null
        if (hasAttemptedSubmit) revalidate()
    }

    fun onOrganizationChange(v: String) {
        organization = v
        serverFieldErrors = serverFieldErrors - "organization"
        commonError = null
        if (hasAttemptedSubmit) revalidate()
    }

    fun onPurposeChange(v: String) {
        purpose = v
        serverFieldErrors = serverFieldErrors - "purpose"
        commonError = null
        if (hasAttemptedSubmit) revalidate()
    }

    fun onDateChange(v: String) {
        date = v
        commonError = null
        if (hasAttemptedSubmit) revalidate()
    }

    fun onTimeFromChange(v: String) {
        timeFrom = v
        serverFieldErrors = serverFieldErrors - "validFrom"
        commonError = null
        if (hasAttemptedSubmit) revalidate()
    }

    fun onTimeToChange(v: String) {
        timeTo = v
        serverFieldErrors = serverFieldErrors - "validTo"
        commonError = null
        if (hasAttemptedSubmit) revalidate()
    }

    private fun validateAll(): FormErrors {
        val trimmedFullName = fullName.trim()
        val fullNameErr = when {
            trimmedFullName.isEmpty() -> "Введите ФИО гостя"
            trimmedFullName.length !in 5..150 -> "ФИО должно содержать от 5 до 150 символов"
            else -> null
        }

        val trimmedPhone = phone.trim()
        val phoneErr = when {
            trimmedPhone.isEmpty() -> "Введите телефон"
            !PHONE_REGEX.matches(trimmedPhone) -> "Формат телефона: +7 и 10 цифр"
            else -> null
        }

        val trimmedOrg = organization.trim()
        val orgErr = if (trimmedOrg.length > 150) {
            "Название организации не длиннее 150 символов"
        } else {
            null
        }

        val trimmedPurpose = purpose.trim()
        val purposeErr = when {
            trimmedPurpose.isEmpty() -> "Введите цель визита"
            trimmedPurpose.length !in 5..200 -> "Цель должна содержать от 5 до 200 символов"
            else -> null
        }

        val trimmedDate = date.trim()
        val parsedDate: LocalDate? = if (trimmedDate.length != 10) {
            null
        } else {
            try {
                LocalDate.parse(trimmedDate, STRICT_DATE_FORMATTER)
            } catch (_: Exception) {
                null
            }
        }
        val dateErr = if (parsedDate == null) "Укажите корректную дату ДД.ММ.ГГГГ" else null

        val trimmedFrom = timeFrom.trim()
        val parsedFrom: LocalTime? = if (trimmedFrom.length != 5) {
            null
        } else {
            try {
                LocalTime.parse(trimmedFrom, STRICT_TIME_FORMATTER)
            } catch (_: Exception) {
                null
            }
        }

        val now = container.timeProvider.now()
        val validFromErr = when {
            parsedFrom == null -> "Укажите время в формате ЧЧ:ММ"
            parsedDate != null && LocalDateTime.of(parsedDate, parsedFrom).isBefore(now) ->
                "Время начала не может быть раньше текущего"
            else -> null
        }

        val trimmedTo = timeTo.trim()
        val parsedTo: LocalTime? = if (trimmedTo.length != 5) {
            null
        } else {
            try {
                LocalTime.parse(trimmedTo, STRICT_TIME_FORMATTER)
            } catch (_: Exception) {
                null
            }
        }

        val validToErr = when {
            parsedTo == null -> "Укажите время в формате ЧЧ:ММ"
            parsedFrom != null && !parsedTo.isAfter(parsedFrom) ->
                "Время окончания должно быть позже начала"
            else -> null
        }

        return FormErrors(
            fullName = fullNameErr,
            phone = phoneErr,
            organization = orgErr,
            purpose = purposeErr,
            date = dateErr,
            validFrom = validFromErr,
            validTo = validToErr,
        )
    }

    private fun revalidate() {
        clientErrors = validateAll()
    }

    fun submit() {
        if (isSubmitting) return
        hasAttemptedSubmit = true
        commonError = null
        val errors = validateAll()
        clientErrors = errors
        if (errors.hasErrors) return

        val parsedDate = LocalDate.parse(date.trim(), STRICT_DATE_FORMATTER)
        val parsedFrom = LocalTime.parse(timeFrom.trim(), STRICT_TIME_FORMATTER)
        val parsedTo = LocalTime.parse(timeTo.trim(), STRICT_TIME_FORMATTER)

        val body = CreateRequestBody(
            fullName = fullName.trim(),
            phone = phone.trim(),
            organization = organization.trim().ifEmpty { null },
            purpose = purpose.trim(),
            validFrom = LocalDateTime.of(parsedDate, parsedFrom).toApi(),
            validTo = LocalDateTime.of(parsedDate, parsedTo).toApi(),
        )

        isSubmitting = true
        serverFieldErrors = emptyMap()

        viewModelScope.launch {
            val code = container.session.current()
            if (code.isNullOrEmpty()) {
                isSubmitting = false
                container.session.clear()
                sessionExpiredEvent = true
                return@launch
            }

            when (val result = api.create(code, body)) {
                is ApiResult.Success -> {
                    isSubmitting = false
                    createdEvent = true
                }
                is ApiResult.Unauthorized -> {
                    isSubmitting = false
                    container.session.clear()
                    sessionExpiredEvent = true
                }
                is ApiResult.FieldErrors -> {
                    isSubmitting = false
                    serverFieldErrors = result.errors
                }
                is ApiResult.Conflict -> {
                    isSubmitting = false
                    commonError = "На это время уже есть заявка для указанного телефона"
                }
                is ApiResult.Failure -> {
                    isSubmitting = false
                    commonError = result.message?.takeIf { it.isNotBlank() } ?: "Ошибка при создании заявки"
                }
                is ApiResult.Network -> {
                    isSubmitting = false
                    commonError = "Нет связи с сервером"
                }
                is ApiResult.NotFound -> {
                    isSubmitting = false
                    commonError = "Ошибка при создании заявки"
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestFormScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onCreated: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val vm = containerViewModel { RequestFormViewModel(container) }
    val scrollState = rememberScrollState()

    LaunchedEffect(vm.createdEvent) {
        if (vm.createdEvent) {
            onCreated()
        }
    }

    LaunchedEffect(vm.sessionExpiredEvent) {
        if (vm.sessionExpiredEvent) {
            onSessionExpired()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новая заявка") },
                navigationIcon = {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .testTag(TestIds.FORM_BACK),
                    ) {
                        Text("Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FormInputField(
                value = vm.fullName,
                onValueChange = vm::onFullNameChange,
                label = "ФИО гостя",
                fieldTag = TestIds.FORM_FULL_NAME,
                errorText = vm.effectiveFullNameError,
                errorTag = TestIds.FORM_ERROR_FULL_NAME,
                enabled = !vm.isSubmitting,
            )

            FormInputField(
                value = vm.phone,
                onValueChange = vm::onPhoneChange,
                label = "Телефон (+7XXXXXXXXXX)",
                fieldTag = TestIds.FORM_PHONE,
                errorText = vm.effectivePhoneError,
                errorTag = TestIds.FORM_ERROR_PHONE,
                enabled = !vm.isSubmitting,
            )

            FormInputField(
                value = vm.organization,
                onValueChange = vm::onOrganizationChange,
                label = "Организация (необязательно)",
                fieldTag = TestIds.FORM_ORGANIZATION,
                errorText = vm.effectiveOrganizationError,
                errorTag = TestIds.FORM_ERROR_ORGANIZATION,
                enabled = !vm.isSubmitting,
            )

            FormInputField(
                value = vm.purpose,
                onValueChange = vm::onPurposeChange,
                label = "Цель визита",
                fieldTag = TestIds.FORM_PURPOSE,
                errorText = vm.effectivePurposeError,
                errorTag = TestIds.FORM_ERROR_PURPOSE,
                enabled = !vm.isSubmitting,
            )

            FormInputField(
                value = vm.date,
                onValueChange = vm::onDateChange,
                label = "Дата (ДД.ММ.ГГГГ)",
                fieldTag = TestIds.FORM_DATE,
                errorText = vm.effectiveDateError,
                errorTag = TestIds.FORM_ERROR_DATE,
                enabled = !vm.isSubmitting,
            )

            FormInputField(
                value = vm.timeFrom,
                onValueChange = vm::onTimeFromChange,
                label = "Время начала (ЧЧ:ММ)",
                fieldTag = TestIds.FORM_TIME_FROM,
                errorText = vm.effectiveValidFromError,
                errorTag = TestIds.FORM_ERROR_VALID_FROM,
                enabled = !vm.isSubmitting,
            )

            FormInputField(
                value = vm.timeTo,
                onValueChange = vm::onTimeToChange,
                label = "Время окончания (ЧЧ:ММ)",
                fieldTag = TestIds.FORM_TIME_TO,
                errorText = vm.effectiveValidToError,
                errorTag = TestIds.FORM_ERROR_VALID_TO,
                enabled = !vm.isSubmitting,
            )

            vm.commonError?.let { common ->
                Text(
                    text = common,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestIds.FORM_ERROR_COMMON),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = vm::submit,
                enabled = !vm.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestIds.FORM_SUBMIT),
            ) {
                Text("Отправить")
            }

            if (vm.isSubmitting) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(32.dp)
                            .testTag(TestIds.FORM_LOADING),
                    )
                }
            }
        }
    }
}

@Composable
private fun FormInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    fieldTag: String,
    errorText: String?,
    errorTag: String,
    enabled: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            isError = errorText != null,
            enabled = enabled,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(fieldTag),
        )
        if (errorText != null) {
            Text(
                text = errorText,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .testTag(errorTag),
            )
        }
    }
}
