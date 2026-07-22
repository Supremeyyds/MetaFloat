package dev.codex.mihomometer.dashboard

import androidx.annotation.StringRes
import dev.codex.mihomometer.R

enum class DashboardDownloadMirror(
    @StringRes val labelResource: Int,
    private val urlFactory: (String, String) -> String,
) {
    DIRECT_GITHUB(R.string.mirror_direct_github, { directUrl, _ -> directUrl }),
    GHFAST(R.string.mirror_ghfast, { directUrl, _ -> "https://ghfast.top/$directUrl" }),
    GH_PROXY(R.string.mirror_gh_proxy, { directUrl, _ -> "https://gh-proxy.com/$directUrl" }),
    FASTLY_JSDELIVR(R.string.mirror_fastly_jsdelivr, { directUrl, _ -> "https://fastly.jsdelivr.net/$directUrl" }),
    CUSTOM(R.string.mirror_custom, { directUrl, customBaseUrl -> customMirrorUrl(directUrl, customBaseUrl) });

    fun downloadUrl(customBaseUrl: String = ""): String {
        return urlFactory(DIRECT_DOWNLOAD_URL, customBaseUrl)
    }

    companion object {
        const val DIRECT_DOWNLOAD_URL =
            "https://github.com/Zephyruso/zashboard/releases/latest/download/dist-no-fonts.zip"

        fun fromStorageValue(value: String?): DashboardDownloadMirror {
            return entries.firstOrNull { it.name == value } ?: DIRECT_GITHUB
        }

        private fun customMirrorUrl(directUrl: String, customBaseUrl: String): String {
            val trimmed = customBaseUrl.trim()
            if (trimmed.isBlank()) {
                return directUrl
            }
            if (trimmed.contains(CUSTOM_URL_PLACEHOLDER)) {
                return trimmed.replace(CUSTOM_URL_PLACEHOLDER, directUrl)
            }
            return "${trimmed.trimEnd('/')}/$directUrl"
        }

        private const val CUSTOM_URL_PLACEHOLDER = "{url}"
    }
}
