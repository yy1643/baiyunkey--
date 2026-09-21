package cn.huacheng.safebaiyun.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cn.huacheng.safebaiyun.unlock.DataRepo

/**
 *
 *@description:
 *@author: guangzhou
 *@create: 2024-05-10
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDialog(
    state: MutableState<Boolean>,
    initData: () -> Pair<String, String>,
    onSaved: () -> Unit = {}
) {
    val data = remember {
        initData()
    }
    val (mac, setMac) = remember {
        mutableStateOf(data.first)
    }
    val (key, setKey) = remember {
        mutableStateOf(data.second)
    }
    val error = remember { mutableStateOf<String?>(null) }
    val showKey = remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = { state.value = false },
        containerColor = Color.White,
        scrimColor = Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
            OutlinedTextField(modifier = modifier, value = mac, onValueChange = setMac, label = {
                Text(text = "MAC 地址")
            })
            OutlinedTextField(
                modifier = modifier,
                value = key,
                onValueChange = setKey,
                label = { Text(text = "Key（16 位十六进制）") },
                visualTransformation = if (showKey.value) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Button(onClick = { showKey.value = !showKey.value }) {
                        Text(if (showKey.value) "隐藏" else "显示")
                    }
                }
            )
            error.value?.let { Text(text = it) }
            Button(modifier = Modifier.padding(8.dp), onClick = {
                val validationError = DataRepo.validate(mac, key)
                if (validationError != null) {
                    error.value = validationError
                } else if (DataRepo.save(mac, key)) {
                    error.value = null
                    state.value = false
                    onSaved()
                } else {
                    error.value = "保存失败，请重试"
                }
            }) {
                Text(text = "加密保存")
            }
        }

    }
}
