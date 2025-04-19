package com.example.diploma.api

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Настройка для возможности переключения между серверами
 */
@Composable
fun ServerSettingsDialog(
    serverConfigHelper: ServerConfigHelper,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit
) {
    var serverIp by remember { mutableStateOf(serverConfigHelper.getServerIp()) }
    var serverPort by remember { mutableStateOf(serverConfigHelper.getServerPort().toString()) }
    var isValidInput by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки сервера") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Введите IP-адрес и порт сервера.")

                OutlinedTextField(
                    value = serverIp,
                    onValueChange = { serverIp = it },
                    label = { Text("IP-адрес сервера") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                OutlinedTextField(
                    value = serverPort,
                    onValueChange = { serverPort = it },
                    label = { Text("Порт сервера") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = !isValidInput,
                    shape = RoundedCornerShape(16.dp)
                )

                if (!isValidInput) {
                    Text(
                        "Порт должен быть числом от 1 до 65535",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // При работе с телефоном
                    OutlinedButton(
                        onClick = {
                            serverIp = "192.168.0.103"
                            serverPort = "5000"
                        },
                        modifier = Modifier.fillMaxWidth(1f)
                    ) {
                        Text("Физическое устройство")
                    }

                    Spacer(modifier = Modifier.height(10.dp))


                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Absolute.SpaceBetween
                    ) {
                        // При работе с эмулятором
                        OutlinedButton(
                            onClick = {
                                serverIp = "10.0.2.2"
                                serverPort = "5000"
                            }
                        ) {
                            Text("Эмулятор")
                        }
                        // Сброс
                        OutlinedButton(
                            onClick = {
                                serverConfigHelper.resetToDefault()
                                serverIp = serverConfigHelper.getServerIp()
                                serverPort = serverConfigHelper.getServerPort().toString()
                            },
                        ) {
                            Text("Сбросить")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Проверка номера порта
                    val portNumber = serverPort.toIntOrNull()
                    if (portNumber != null && portNumber in 1..65535) {
                        isValidInput = true
                        onSave(serverIp, portNumber)
                        onDismiss()
                    } else {
                        isValidInput = false
                    }
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss
            ) {
                Text("Отмена")
            }
        }
    )
}