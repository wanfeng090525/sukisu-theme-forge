package dev.sukisu.themeforge.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sukisu.themeforge.core.FileIo
import dev.sukisu.themeforge.vm.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(vm: EditorViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                val name = FileIo.displayName(context, uri)
                val result = withContext(Dispatchers.IO) {
                    runCatching { FileIo.read(context, uri, 128L * 1024 * 1024) }
                }
                result.onSuccess { vm.openPackage(it, name) }
                    .onFailure { vm.reportError(it.message ?: "读取文件失败") }
                busy = false
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Spacer(Modifier.height(18.dp))
                Text(
                    "SukiSU ULTRA",
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "ThemeForge",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "主题包开发工具",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))

                HeroCard()
                Spacer(Modifier.height(22.dp))

                Button(
                    onClick = { vm.newTheme() },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("新建主题", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("导入 .sukisu", fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(26.dp))
                Text(
                    "能做什么",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                FeatureRow("明暗双主题", "light / dark 两套颜色、图片和布局独立编辑")
                FeatureRow("全部字段", "外观令牌、首页布局、模块排序、交互开关、自定义文案")
                FeatureRow("底栏 Dock", "替换 4 个底栏图标，悬浮栏、模糊、角标与手势开关")
                FeatureRow("图片槽位", "壁纸、首页卡片、最多 5 个贴纸 + 位置/旋转/大小")
                FeatureRow("JSON 编辑", "一键载入完整字段模板，格式化、校验、应用")
                FeatureRow("一键导出", "生成 theme.json + 图片，AES-256-GCM 加密为 .sukisu")

                Spacer(Modifier.height(22.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "仅支持 SukiSU Ultra v3 - v5 自适应主题包。主题包只能改变已有颜色、图片和开关，不能新增实时组件（例如走时时钟）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(26.dp))
                Text(
                    "v2.0.0 · 非官方工具",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (busy) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("正在读取") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("解析主题包…")
                }
            }
        )
    }

    vm.error?.let { message ->
        AlertDialog(
            onDismissRequest = { vm.dismissError() },
            title = { Text("出错了") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { vm.dismissError() }) { Text("知道了") }
            }
        )
    }
}

@Composable
private fun HeroCard() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(184.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0B1220), Color(0xFF0F766E), Color(0xFF6366F1))
                )
            )
    ) {
        Box(
            Modifier
                .size(150.dp)
                .align(Alignment.TopEnd)
                .padding(18.dp)
                .background(Color.White.copy(alpha = 0.08f), CircleShape)
        )
        Box(
            Modifier
                .size(90.dp)
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .background(Color(0xFF7FF7E4).copy(alpha = 0.22f), CircleShape)
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
        ) {
            Text(
                "LIQUID GLASS · ADAPTIVE",
                fontSize = 9.sp,
                letterSpacing = 1.4.sp,
                color = Color.White.copy(alpha = 0.65f)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Build your own\ntheme package",
                fontSize = 24.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
        }
    }
}

@Composable
private fun FeatureRow(title: String, description: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(7.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
