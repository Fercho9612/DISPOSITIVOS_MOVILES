package com.iudigital.radio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iudigital.radio.data.model.CategoryTab

@Composable
fun CategorySelector(
    selectedTab: CategoryTab,
    onTabSelected: (CategoryTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CategoryTab.values().forEach { tab ->
            FilterChip(
                selected = (selectedTab == tab),
                onClick = { onTabSelected(tab) },
                label = { Text(tab.title) },
                leadingIcon = {
                    if (selectedTab == tab) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    }
}