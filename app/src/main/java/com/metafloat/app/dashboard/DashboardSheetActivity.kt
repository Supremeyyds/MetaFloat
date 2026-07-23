package com.metafloat.app.dashboard

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.metafloat.app.R
import com.metafloat.app.settings.AppThemeMode
import com.metafloat.app.ui.MainActivity
import com.metafloat.app.ui.MetaFloatTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class DashboardSheetActivity : ComponentActivity() {
    private var launchConfig by mutableStateOf(DashboardLaunchConfig())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isDashboardInstalled()) {
            showDashboardNotInstalled()
            return
        }

        launchConfig = DashboardLaunchContract.fromIntent(intent)
        configureTransparentWindow()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        setContent {
            val config = launchConfig
            MetaFloatTheme(themeMode = config.themeMode) {
                DashboardSheet(
                    dashboardUrl = config.dashboardUrl,
                    themeMode = config.themeMode,
                    onDismiss = ::finishSheet,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchConfig = DashboardLaunchContract.fromIntent(intent)
    }

    private fun configureTransparentWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = AndroidColor.TRANSPARENT
            window.navigationBarColor = AndroidColor.TRANSPARENT
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    private fun isDashboardInstalled(): Boolean {
        return DashboardInstaller.dashboardDirectory(this)
            .resolve("index.html")
            .isFile
    }

    private fun showDashboardNotInstalled() {
        Toast.makeText(this, R.string.dashboard_not_installed, Toast.LENGTH_SHORT).show()
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            },
        )
        finishSheet()
    }

    private fun finishSheet() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        fun createIntent(
            context: Context,
            dashboardUrl: String,
            themeMode: AppThemeMode,
        ): Intent {
            return DashboardLaunchContract.putInto(
                Intent(context, DashboardSheetActivity::class.java),
                dashboardUrl = dashboardUrl,
                themeMode = themeMode,
            )
        }
    }
}

@Composable
private fun DashboardSheet(
    dashboardUrl: String,
    themeMode: AppThemeMode,
    onDismiss: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val hapticFeedback = LocalHapticFeedback.current
        val defaultSheetHeight = DashboardSheetLayoutPolicy.targetHeightDp(
            widthDp = maxWidth.value,
            heightDp = maxHeight.value,
        ).dp
        val draggableHeightRange = DashboardSheetLayoutPolicy.draggableHeightRangeDp(
            heightDp = maxHeight.value,
        )
        val minimumSheetHeightPx = with(density) {
            draggableHeightRange.start.dp.toPx()
        }
        val maximumSheetHeightPx = with(density) {
            draggableHeightRange.endInclusive.dp.toPx()
        }
        val dismissThresholdPx = with(density) {
            DashboardSheetLayoutPolicy.dismissThresholdDp(maxHeight.value).dp.toPx()
        }
        var sheetHeightPx by remember(defaultSheetHeight, density) {
            mutableFloatStateOf(with(density) { defaultSheetHeight.toPx() })
        }
        var isDismissing by remember { mutableStateOf(false) }
        val sheetOffsetY = remember { Animatable(0f) }
        val coroutineScope = rememberCoroutineScope()
        val sheetHeight = with(density) { sheetHeightPx.toDp() }
        val isDismissArmed = sheetHeightPx <= dismissThresholdPx
        val contentBlurRadius by animateDpAsState(
            targetValue = if (isDismissArmed) 12.dp else 0.dp,
            animationSpec = tween(durationMillis = DISMISS_FEEDBACK_ANIMATION_MILLIS),
            label = "dashboardSheetContentBlur",
        )
        val dismissProgress = (sheetOffsetY.value / maximumSheetHeightPx)
            .coerceIn(0f, 1f)
        val dismissSheet: () -> Unit = {
            if (!isDismissing) {
                isDismissing = true
                coroutineScope.launch {
                    sheetOffsetY.animateTo(
                        targetValue = maximumSheetHeightPx,
                        animationSpec = tween(
                            durationMillis = DISMISS_ANIMATION_DURATION_MILLIS,
                            easing = FastOutLinearInEasing,
                        ),
                    )
                    onDismiss()
                }
            }
        }
        val scrimInteractionSource = remember { MutableInteractionSource() }
        val sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Color.Black.copy(alpha = 0.42f * (1f - dismissProgress)),
                )
                .clickable(
                    interactionSource = scrimInteractionSource,
                    indication = null,
                    onClick = dismissSheet,
                ),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sheetHeight)
                .offset {
                    IntOffset(
                        x = 0,
                        y = sheetOffsetY.value.roundToInt(),
                    )
                }
                .clip(sheetShape),
            shape = sheetShape,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 1.dp,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(contentBlurRadius),
                ) {
                    DashboardSheetDragHandle(
                        onDrag = { dragAmountPx ->
                            if (!isDismissing) {
                                val previousHeightPx = sheetHeightPx
                                val updatedHeightPx = (sheetHeightPx - dragAmountPx).coerceIn(
                                    minimumSheetHeightPx,
                                    maximumSheetHeightPx,
                                )
                                sheetHeightPx = updatedHeightPx
                                if (
                                    previousHeightPx > dismissThresholdPx &&
                                    updatedHeightPx <= dismissThresholdPx
                                ) {
                                    hapticFeedback.performHapticFeedback(
                                        HapticFeedbackType.TextHandleMove,
                                    )
                                }
                            }
                        },
                        onDragEnd = {
                            if (sheetHeightPx <= dismissThresholdPx) {
                                dismissSheet()
                            }
                        },
                    )
                    DashboardSheetHeader()
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DashboardWebView(
                        dashboardUrl = dashboardUrl,
                        themeMode = themeMode,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
                AnimatedVisibility(
                    visible = isDismissArmed,
                    modifier = Modifier.fillMaxSize(),
                    enter = fadeIn(
                        animationSpec = tween(DISMISS_FEEDBACK_ANIMATION_MILLIS),
                    ),
                    exit = fadeOut(
                        animationSpec = tween(DISMISS_FEEDBACK_ANIMATION_MILLIS),
                    ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            shadowElevation = 6.dp,
                        ) {
                            Text(
                                text = stringResource(R.string.dashboard_release_to_dismiss),
                                modifier = Modifier.padding(
                                    horizontal = 24.dp,
                                    vertical = 14.dp,
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardSheetDragHandle(
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = currentOnDragEnd,
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        currentOnDrag(dragAmount)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Spacer(
            modifier = Modifier
                .width(32.dp)
                .height(4.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    shape = CircleShape,
                ),
        )
    }
}

private const val DISMISS_ANIMATION_DURATION_MILLIS = 220
private const val DISMISS_FEEDBACK_ANIMATION_MILLIS = 120

@Composable
private fun DashboardSheetHeader() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {})
            },
    )
}

@Composable
private fun DashboardWebView(
    dashboardUrl: String,
    themeMode: AppThemeMode,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val webView = remember(context, themeMode) {
        DashboardWebViewFactory.create(context, themeMode)
    }

    LaunchedEffect(webView, dashboardUrl) {
        webView.doOnLayout {
            webView.loadUrl(dashboardUrl)
        }
    }
    AndroidView(
        factory = { webView },
        modifier = modifier,
        onRelease = DashboardWebViewFactory::destroy,
    )
}
