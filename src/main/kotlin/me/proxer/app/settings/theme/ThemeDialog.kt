package me.proxer.app.settings.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.proxer.app.R
import me.proxer.app.ui.compose.ProxerTheme
import me.proxer.app.util.data.PreferenceHelper
import org.koin.compose.koinInject

@Composable
fun ThemeDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val preferenceHelper = koinInject<PreferenceHelper>()

    var selectedTheme by remember { mutableStateOf(preferenceHelper.themeContainer.theme) }
    var selectedVariant by remember { mutableStateOf(preferenceHelper.themeContainer.variant) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_theme_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Theme colour swatches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Theme.values().forEach { theme ->
                        val primaryColor = remember(theme) { Color(theme.primaryColor(context)) }
                        val secondaryColor = remember(theme) { Color(theme.secondaryColor(context)) }
                        val onSecondaryColor = remember(theme) { Color(theme.colorOnSecondary(context)) }
                        val isSelected = selectedTheme == theme

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { selectedTheme = theme },
                        ) {
                            Canvas(modifier = Modifier.size(48.dp).padding(4.dp)) {
                                val w = size.width
                                val h = size.height
                                drawRect(color = primaryColor, size = Size(w, h / 2))
                                drawRect(
                                    color = secondaryColor,
                                    topLeft = Offset(0f, h / 2),
                                    size = Size(w, h / 2),
                                )
                                if (isSelected) {
                                    drawCircle(
                                        color = onSecondaryColor,
                                        radius = w / 8,
                                        center = Offset(w / 4 * 3, h / 4 * 3),
                                    )
                                }
                            }
                            Text(
                                text = stringResource(theme.themeName),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.theme_variant_title),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 4.dp),
                )

                // Variant radio buttons (System / Light / Dark)
                ThemeVariant.values().forEach { variant ->
                    val nameRes = variant.variantName ?: R.string.theme_variant_system
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedVariant = variant },
                    ) {
                        RadioButton(
                            selected = selectedVariant == variant,
                            onClick = { selectedVariant = variant },
                        )
                        Text(stringResource(nameRes))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                preferenceHelper.themeContainer = ThemeContainer(selectedTheme, selectedVariant)
                onDismiss()
            }) {
                Text(stringResource(R.string.dialog_theme_positive))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun ThemeDialogPreview() {
    ProxerTheme {
        ThemeDialog(onDismiss = {})
    }
}
