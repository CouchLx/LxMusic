package com.example.lxmusic.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.FlowRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBottomSheet(
    currentSortType: Int,
    reverseSort: Boolean,
    currentGroupType: Int,
    onSortTypeChange: (Int) -> Unit,
    onReverseChange: (Boolean) -> Unit,
    onGroupTypeChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {}
    ) {
        BackHandler(onBack = onDismiss)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(bottom = 24.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = "排序方式",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // 排序方式 chips
            SectionLabel("排序方式")
            val sortOptions = listOf(
                0 to "标题",
                1 to "艺术家",
                2 to "专辑"
            )
            FlowRow(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sortOptions.forEach { (type, label) ->
                    FilterChip(
                        selected = currentSortType == type,
                        onClick = { onSortTypeChange(type) },
                        label = { Text(label) },
                        colors = chipColors(selected = currentSortType == type)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 正序 / 倒序
            SectionLabel("播放顺序")
            FlowRow(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !reverseSort,
                    onClick = { onReverseChange(false) },
                    label = { Text("正序(A-Z)") },
                    colors = chipColors(selected = !reverseSort)
                )
                FilterChip(
                    selected = reverseSort,
                    onClick = { onReverseChange(true) },
                    label = { Text("倒序(Z-A)") },
                    colors = chipColors(selected = reverseSort)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 分组方式
            SectionLabel("分组方式")
            val groupOptions = listOf(
                0 to "不分组",
                1 to "按艺术家",
                2 to "按专辑",
                3 to "按文件夹"
            )
            FlowRow(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupOptions.forEach { (type, label) ->
                    FilterChip(
                        selected = currentGroupType == type,
                        onClick = { onGroupTypeChange(type) },
                        label = { Text(label) },
                        colors = chipColors(selected = currentGroupType == type)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
    )
}

@Composable
private fun chipColors(selected: Boolean) = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    containerColor = MaterialTheme.colorScheme.surface,
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
)
