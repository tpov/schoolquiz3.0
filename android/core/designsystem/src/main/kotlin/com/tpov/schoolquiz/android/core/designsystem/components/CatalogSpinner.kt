package com.tpov.schoolquiz.android.core.designsystem.components

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.tpov.schoolquiz.android.core.designsystem.model.CatalogDisplayItem
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId

/**
 * Dropdown spinner for selecting a catalog category.
 *
 * Prepends "Все категории" pseudo-item (null selectedId).
 * ADR-L3-03: no Firebase SDK dependency — operates on List<CatalogDisplayItem>.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Suppress("FunctionNaming", "UnusedPrivateMember", "ktlint:standard:function-naming")
@Composable
private fun CatalogSpinnerPreview() {
    CatalogSpinner(items = emptyList(), selectedId = null, onSelectionChanged = {})
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun CatalogSpinner(
    items: List<CatalogDisplayItem>,
    selectedId: CatalogId?,
    onSelectionChanged: (CatalogId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val expanded = remember { mutableStateOf(false) }
    val selectedLabel = items.firstOrNull { it.id == selectedId }?.name ?: "Все категории"

    ExposedDropdownMenuBox(
        expanded = expanded.value,
        onExpandedChange = { expanded.value = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded.value) },
            shape = schoolQuizDesignCardShape(),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = schoolQuizDesignDeepSurfaceColor(),
                    unfocusedContainerColor = schoolQuizDesignDeepSurfaceColor(),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = schoolQuizDesignLightBorderColor(),
                    focusedTrailingIconColor = MaterialTheme.colorScheme.primary,
                    unfocusedTrailingIconColor = MaterialTheme.colorScheme.primary,
                ),
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false },
        ) {
            DropdownMenuItem(
                text = { Text("Все категории") },
                onClick = {
                    onSelectionChanged(null)
                    expanded.value = false
                },
            )
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.name) },
                    onClick = {
                        onSelectionChanged(item.id)
                        expanded.value = false
                    },
                )
            }
        }
    }
}
