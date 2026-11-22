package ru.nto.guestmanagement.mobile.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import ru.nto.guestmanagement.mobile.data.RequestsApi
import ru.nto.guestmanagement.mobile.di.AppContainer
import ru.nto.guestmanagement.mobile.ui.TestIds
import ru.nto.guestmanagement.mobile.ui.common.containerViewModel

private val CODE_REGEX = Regex("^[A-Za-z0-9]{4}$")

class LoginViewModel(
    private val container: AppContainer,
    initialSessionExpired: Boolean,
) : ViewModel() {
    private val api = RequestsApi(container.httpClient)

    var isCheckingSession by mutableStateOf(true)
        private set

    var code by mutableStateOf("")
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var showSessionExpired by mutableStateOf(initialSessionExpired)
        private set

    var navigateToList by mutableStateOf(false)
        private set

    val isCodeValid: Boolean
        get() = CODE_REGEX.matches(code)

    init {
        viewModelScope.launch {
            val savedCode = container.session.current()
            if (!savedCode.isNullOrEmpty() && CODE_REGEX.matches(savedCode)) {
                navigateToList = true
            } else {
                isCheckingSession = false
            }
        }
    }

    fun onCodeChange(newValue: String) {
        code = newValue
        errorMessage = null
    }

    fun submit() {
        if (!isCodeValid || isLoading) return
        val currentCode = code
        isLoading = true
        errorMessage = null
        showSessionExpired = false

        viewModelScope.launch {
            when (val result = api.auth(currentCode)) {
                is ApiResult.Success -> {
                    container.session.save(currentCode)
                    isLoading = false
                    navigateToList = true
                }
                is ApiResult.Unauthorized -> {
                    isLoading = false
                    errorMessage = "Неверный код сотрудника"
                }
                is ApiResult.Failure -> {
                    isLoading = false
                    errorMessage = result.message?.takeIf { it.isNotBlank() } ?: "Ошибка авторизации"
                }
                is ApiResult.Network -> {
                    isLoading = false
                    errorMessage = "Нет связи с сервером"
                }
                else -> {
                    isLoading = false
                    errorMessage = "Ошибка входа"
                }
            }
        }
    }
}

@Composable
fun LoginScreen(container: AppContainer, sessionExpired: Boolean, onLoggedIn: () -> Unit) {
    val vm = containerViewModel { LoginViewModel(container, sessionExpired) }

    LaunchedEffect(vm.navigateToList) {
        if (vm.navigateToList) {
            onLoggedIn()
        }
    }

    if (vm.isCheckingSession) {
        return
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Вход в систему",
                style = MaterialTheme.typography.headlineSmall,
            )

            if (vm.showSessionExpired) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Сессия завершена",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag(TestIds.AUTH_SESSION_EXPIRED),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = vm.code,
                onValueChange = vm::onCodeChange,
                label = { Text("Код сотрудника") },
                singleLine = true,
                enabled = !vm.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestIds.AUTH_CODE),
            )

            vm.errorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestIds.AUTH_ERROR),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = vm::submit,
                enabled = vm.isCodeValid && !vm.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestIds.AUTH_SUBMIT),
            ) {
                Text("Войти")
            }

            if (vm.isLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(32.dp)
                        .testTag(TestIds.AUTH_LOADING),
                )
            }
        }
    }
}
