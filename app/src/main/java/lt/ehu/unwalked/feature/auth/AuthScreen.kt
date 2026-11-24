package lt.ehu.unwalked.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import lt.ehu.unwalked.core.ui.components.PrimaryButton
import lt.ehu.unwalked.core.ui.motion.Motion
import lt.ehu.unwalked.core.ui.theme.Palette

@Composable
fun AuthScreen(
    state: AuthUiState,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onConfirmation: (String) -> Unit,
    onSubmit: () -> Unit,
    onSwitchMode: () -> Unit,
) {
    val focus = LocalFocusManager.current
    val register = state.mode == AuthMode.Register
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Icon(Icons.AutoMirrored.Outlined.DirectionsWalk, contentDescription = null, tint = Palette.Accent, modifier = Modifier.size(40.dp))
        Text(if (register) "Create an account" else "Welcome to Unwalked", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (register) {
                "Your walks stay on this phone. The account only keeps your place in the study."
            } else {
                "Sign in to see where you have not walked yet."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Muted,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmail,
            label = { Text("Email") },
            singleLine = true,
            isError = state.errors.email != null,
            supportingText = state.errors.email?.let { message -> { FieldError(message) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            modifier = Modifier.fillMaxWidth().testTag("email"),
        )
        PasswordField(
            value = state.password,
            onValueChange = onPassword,
            label = "Password",
            error = state.errors.password,
            hint = if (register) "At least 10 characters" else null,
            imeAction = if (register) ImeAction.Next else ImeAction.Done,
            onIme = { if (register) focus.moveFocus(FocusDirection.Down) else onSubmit() },
            tag = "password",
        )
        if (register) {
            PasswordField(
                value = state.confirmation,
                onValueChange = onConfirmation,
                label = "Repeat password",
                error = state.errors.confirmation,
                hint = null,
                imeAction = ImeAction.Done,
                onIme = onSubmit,
                tag = "confirmation",
            )
        }
        var lastError by remember { mutableStateOf("") }
        state.formError?.let { lastError = it }
        AnimatedVisibility(state.formError != null, enter = Motion.appear, exit = Motion.disappear) {
            val message = lastError
            Row(
                Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.testTag("formError"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        PrimaryButton(
            text = if (register) "Create account" else "Sign in",
            onClick = onSubmit,
            loading = state.submitting,
            modifier = Modifier.testTag("submit"),
        )
        TextButton(onClick = onSwitchMode, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (register) "I already have an account" else "Create an account")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FieldError(message: String) {
    Text(message, modifier = Modifier.semantics { error(message) })
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    hint: String?,
    imeAction: ImeAction,
    onIme: () -> Unit,
    tag: String,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = when {
            error != null -> ({ FieldError(error) })
            hint != null -> ({ Text(hint) })
            else -> null
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password",
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onIme() }),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}
