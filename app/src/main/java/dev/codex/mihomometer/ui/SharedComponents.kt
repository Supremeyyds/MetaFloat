package dev.codex.mihomometer.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import dev.codex.mihomometer.R

@Composable
internal fun HeaderRow(
    title: String,
    onMenuClick: () -> Unit,
) {
    val menuDescription = stringResource(R.string.action_menu)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = menuDescription },
            onClick = onMenuClick,
        ) {
            Text(
                text = "☰",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
@Suppress("DEPRECATION")
internal fun SystemBarsColorEffect() {
    val view = LocalView.current
    val background = MaterialTheme.colorScheme.background
    val useDarkIcons = background.luminance() > 0.5f

    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        val window = activity.window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        window.statusBarColor = background.toArgb()
        window.navigationBarColor = background.toArgb()
        WindowInsetsControllerCompat(window, view).apply {
            isAppearanceLightStatusBars = useDarkIcons
            isAppearanceLightNavigationBars = useDarkIcons
        }
    }
}

@Composable
internal fun PageHeader(
    modifier: Modifier = Modifier,
    title: String,
    onBack: () -> Unit,
) {
    val backDescription = stringResource(R.string.action_back)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        role = Role.Button,
                        onClick = onBack,
                    )
                    .semantics { contentDescription = backDescription },
                contentAlignment = Alignment.Center,
            ) {
                BackArrowIcon()
            }
        }
        Text(
            modifier = Modifier.weight(1f),
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BackArrowIcon() {
    val color = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier = Modifier.size(26.dp)) {
        val stroke = Stroke(
            width = 3.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        val centerY = size.height / 2f
        drawLine(
            color = color,
            start = Offset(size.width * 0.34f, centerY),
            end = Offset(size.width * 0.82f, centerY),
            strokeWidth = stroke.width,
            cap = stroke.cap,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.36f, centerY),
            end = Offset(size.width * 0.58f, size.height * 0.28f),
            strokeWidth = stroke.width,
            cap = stroke.cap,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.36f, centerY),
            end = Offset(size.width * 0.58f, size.height * 0.72f),
            strokeWidth = stroke.width,
            cap = stroke.cap,
        )
    }
}
