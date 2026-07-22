package com.metafloat.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.metafloat.app.R
import com.metafloat.app.model.ConnectionState
import com.metafloat.app.model.TrafficFormatter
import com.metafloat.app.model.TrafficSample

class OverlayWindowController(
    context: Context,
    private val onPositionChanged: (Int, Int) -> Unit,
    private val onWindowFailure: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private val root = OverlayRootLayout(appContext).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.overlay_background)
        setPadding(dp(2), dp(3), dp(2), dp(3))
        isClickable = true
    }
    private val uploadRateText = overlayRateText()
    private val downloadRateText = overlayRateText()
    private val totalUploadText = overlayTotalText()
    private val totalDownloadText = overlayTotalText()
    private val statusDotBackground = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
    }
    private val statusDot = View(appContext).apply {
        background = statusDotBackground
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams = LinearLayout.LayoutParams(dp(STATUS_DOT_SIZE_DP), dp(STATUS_DOT_SIZE_DP)).apply {
            marginEnd = dp(STATUS_GAP_DP)
        }
    }
    private val statusText = overlayText(SECONDARY_TEXT_SIZE_SP).apply {
        maxLines = 1
        gravity = Gravity.CENTER_VERTICAL
    }
    private val expandedContent = LinearLayout(appContext).apply {
        orientation = LinearLayout.VERTICAL
        isVisible = false
        layoutParams = LinearLayout.LayoutParams(
            dp(CONTENT_WIDTH_DP),
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        addView(totalSectionDivider())
        addView(
            totalRow(
                R.string.overlay_total_upload_label,
                totalUploadText,
            ),
        )
        addView(
            totalRow(
                R.string.overlay_total_download_label,
                totalDownloadText,
            ),
        )
        addView(statusRow())
    }
    private var expanded = false
    private var attached = false
    private var layoutParams = defaultLayoutParams()

    init {
        root.addView(rateRow(R.string.overlay_upload_label, uploadRateText))
        root.addView(rateRow(R.string.overlay_download_label, downloadRateText))
        root.addView(expandedContent)
        root.setOnClickListener {
            expanded = !expanded
            expandedContent.isVisible = expanded
            root.post { constrainAndApply(persist = true) }
        }
        root.setOnTouchListener(DragListener())
        root.addOnLayoutChangeListener { _, _, _, right, bottom, _, _, oldRight, oldBottom ->
            if (attached && (right != oldRight || bottom != oldBottom)) {
                constrainAndApply(persist = true)
            }
        }
        render(null, ConnectionState.Connecting)
    }

    fun attach(x: Int, y: Int) {
        if (attached) {
            return
        }
        layoutParams.x = x
        layoutParams.y = y
        try {
            windowManager.addView(root, layoutParams)
            attached = true
        } catch (throwable: RuntimeException) {
            attached = false
            throw throwable
        }
        root.post { constrainAndApply(persist = true) }
    }

    fun onConfigurationChanged() {
        if (attached) {
            root.post { constrainAndApply(persist = true) }
        }
    }

    fun detach() {
        if (!attached) {
            return
        }
        runCatching { windowManager.removeView(root) }
        attached = false
    }

    fun render(sample: TrafficSample?, state: ConnectionState) {
        val upload = TrafficFormatter.formatBytesPerSecondParts(sample?.upBytesPerSecond ?: 0)
        val download = TrafficFormatter.formatBytesPerSecondParts(sample?.downBytesPerSecond ?: 0)
        val uploadDisplay = formatRateDisplay(upload.number, upload.unit)
        val downloadDisplay = formatRateDisplay(download.number, download.unit)
        uploadRateText.text = uploadDisplay
        downloadRateText.text = downloadDisplay
        val totalUpload = TrafficFormatter.formatBytesParts(sample?.upTotalBytes ?: 0)
        totalUploadText.text = formatTotalDisplay(totalUpload.number, totalUpload.unit)
        val totalDownload = TrafficFormatter.formatBytesParts(sample?.downTotalBytes ?: 0)
        totalDownloadText.text = formatTotalDisplay(totalDownload.number, totalDownload.unit)
        statusText.text = when (state) {
            ConnectionState.Idle -> appContext.getString(R.string.overlay_status_idle)
            ConnectionState.Connecting -> appContext.getString(R.string.overlay_status_connecting)
            ConnectionState.Reconnecting -> appContext.getString(R.string.overlay_status_reconnecting)
            is ConnectionState.Connected -> appContext.getString(R.string.overlay_status_connected)
            is ConnectionState.Disconnected -> appContext.getString(R.string.overlay_status_disconnected)
            is ConnectionState.Failed -> appContext.getString(R.string.overlay_status_failed)
        }
        statusDotBackground.setColor(ContextCompat.getColor(appContext, statusColorResource(state)))
    }

    private fun constrainAndApply(persist: Boolean) {
        if (!attached || root.width <= 0 || root.height <= 0) {
            return
        }
        val (screenWidth, screenHeight) = screenSize()
        val (nextX, nextY) = OverlayPositionPolicy.clamp(
            x = layoutParams.x,
            y = layoutParams.y,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            overlayWidth = root.width,
            overlayHeight = root.height,
        )
        if (nextX == layoutParams.x && nextY == layoutParams.y) {
            return
        }
        layoutParams.x = nextX
        layoutParams.y = nextY
        if (!updateViewLayoutSafely()) {
            return
        }
        if (persist) {
            onPositionChanged(nextX, nextY)
        }
    }

    private fun constrainedPosition(x: Int, y: Int): Pair<Int, Int> {
        val (screenWidth, screenHeight) = screenSize()
        return OverlayPositionPolicy.clamp(
            x = x,
            y = y,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            overlayWidth = root.width.coerceAtLeast(0),
            overlayHeight = root.height.coerceAtLeast(0),
        )
    }

    private fun updateViewLayoutSafely(): Boolean {
        return try {
            windowManager.updateViewLayout(root, layoutParams)
            true
        } catch (_: RuntimeException) {
            onWindowFailure()
            false
        }
    }

    private fun screenSize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            return bounds.width() to bounds.height()
        }
        val metrics = appContext.resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    private fun overlayText(textSizeSp: Float = RATE_TEXT_SIZE_SP): TextView {
        return TextView(appContext).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = textSizeSp
            includeFontPadding = false
            typeface = android.graphics.Typeface.MONOSPACE
            setShadowLayer(3f, 0f, 1f, 0xCC000000.toInt())
        }
    }

    private fun overlayRateText(): TextView {
        return overlayText().apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(dp(RATE_DISPLAY_WIDTH_DP), dp(RATE_ROW_HEIGHT_DP))
        }
    }

    private fun overlayTotalText(): TextView {
        return overlayText(SECONDARY_TEXT_SIZE_SP).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(dp(TOTAL_DISPLAY_WIDTH_DP), dp(TOTAL_ROW_HEIGHT_DP))
        }
    }

    private fun rateRow(
        labelResource: Int,
        displayText: TextView,
    ): LinearLayout {
        return alignedRow(
            labelResource = labelResource,
            displayText = displayText,
            labelWidthDp = RATE_LABEL_WIDTH_DP,
            rowHeightDp = RATE_ROW_HEIGHT_DP,
            textSizeSp = RATE_TEXT_SIZE_SP,
        )
    }

    private fun totalRow(
        labelResource: Int,
        displayText: TextView,
    ): LinearLayout {
        return alignedRow(
            labelResource = labelResource,
            displayText = displayText,
            labelWidthDp = TOTAL_LABEL_WIDTH_DP,
            rowHeightDp = TOTAL_ROW_HEIGHT_DP,
            textSizeSp = SECONDARY_TEXT_SIZE_SP,
        )
    }

    private fun alignedRow(
        labelResource: Int,
        displayText: TextView,
        labelWidthDp: Int,
        rowHeightDp: Int,
        textSizeSp: Float,
    ): LinearLayout {
        return LinearLayout(appContext).apply {
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                dp(CONTENT_WIDTH_DP),
                dp(rowHeightDp),
            )
            addView(
                overlayText(textSizeSp).apply {
                    text = appContext.getString(labelResource)
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(dp(labelWidthDp), dp(rowHeightDp)).apply {
                        marginEnd = dp(COLUMN_GAP_DP)
                    }
                },
            )
            addView(displayText)
        }
    }

    private fun formatRateDisplay(number: String, unit: String): String {
        return appContext.getString(R.string.overlay_rate_value, number, unit.padStart(2, ' '))
    }

    private fun formatTotalDisplay(number: String, unit: String): String {
        return appContext.getString(R.string.overlay_total_value, number, unit.padStart(2, ' '))
    }

    private fun totalSectionDivider(): LinearLayout {
        return LinearLayout(appContext).apply {
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(dp(CONTENT_WIDTH_DP), dp(DIVIDER_HEIGHT_DP))
            addView(dividerLine())
            addView(
                overlayText(SECONDARY_TEXT_SIZE_SP).apply {
                    text = appContext.getString(R.string.overlay_total_heading)
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.MATCH_PARENT,
                    ).apply {
                        marginStart = dp(DIVIDER_TITLE_GAP_DP)
                        marginEnd = dp(DIVIDER_TITLE_GAP_DP)
                    }
                },
            )
            addView(dividerLine())
        }
    }

    private fun dividerLine(): View {
        return View(appContext).apply {
            setBackgroundColor(ContextCompat.getColor(appContext, R.color.overlay_divider))
            layoutParams = LinearLayout.LayoutParams(0, dp(1), 1f)
        }
    }

    private fun statusRow(): LinearLayout {
        return LinearLayout(appContext).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(CONTENT_WIDTH_DP), dp(STATUS_ROW_HEIGHT_DP))
            addView(statusDot)
            addView(statusText)
        }
    }

    private fun statusColorResource(state: ConnectionState): Int {
        return when (state) {
            ConnectionState.Idle -> R.color.overlay_status_idle
            ConnectionState.Connecting,
            ConnectionState.Reconnecting -> R.color.overlay_status_connecting
            is ConnectionState.Connected -> R.color.overlay_status_connected
            is ConnectionState.Disconnected,
            is ConnectionState.Failed -> R.color.overlay_status_failed
        }
    }

    private fun dp(value: Int): Int {
        return (value * appContext.resources.displayMetrics.density).toInt()
    }

    private fun defaultLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private inner class DragListener : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var startRawX = 0f
        private var startRawY = 0f
        private var dragging = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            return when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = layoutParams.x
                    startY = layoutParams.y
                    startRawX = event.rawX
                    startRawY = event.rawY
                    dragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - startRawX
                    val deltaY = event.rawY - startRawY
                    if (kotlin.math.abs(deltaX) > DRAG_THRESHOLD_PX ||
                        kotlin.math.abs(deltaY) > DRAG_THRESHOLD_PX
                    ) {
                        dragging = true
                    }
                    val (nextX, nextY) = constrainedPosition(
                        startX + deltaX.toInt(),
                        startY + deltaY.toInt(),
                    )
                    layoutParams.x = nextX
                    layoutParams.y = nextY
                    if (attached) {
                        updateViewLayoutSafely()
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        constrainAndApply(persist = false)
                        onPositionChanged(layoutParams.x, layoutParams.y)
                    } else {
                        view.performClick()
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        constrainAndApply(persist = false)
                        onPositionChanged(layoutParams.x, layoutParams.y)
                    }
                    true
                }

                else -> false
            }
        }
    }

    private companion object {
        const val DRAG_THRESHOLD_PX = 8
        const val RATE_TEXT_SIZE_SP = 10f
        const val SECONDARY_TEXT_SIZE_SP = 9f
        const val RATE_LABEL_WIDTH_DP = 6
        const val RATE_DISPLAY_WIDTH_DP = 50
        const val TOTAL_LABEL_WIDTH_DP = 17
        const val TOTAL_DISPLAY_WIDTH_DP = 39
        const val COLUMN_GAP_DP = 0
        const val CONTENT_WIDTH_DP = 56
        const val RATE_ROW_HEIGHT_DP = 18
        const val TOTAL_ROW_HEIGHT_DP = 14
        const val DIVIDER_HEIGHT_DP = 12
        const val DIVIDER_TITLE_GAP_DP = 3
        const val STATUS_ROW_HEIGHT_DP = 14
        const val STATUS_DOT_SIZE_DP = 5
        const val STATUS_GAP_DP = 3
    }
}

private class OverlayRootLayout(context: Context) : LinearLayout(context) {
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
