package com.metafloat.app.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardDownloadMirrorTest {
    @Test
    fun entries_containsOnlySupportedMirrorsAndCustom() {
        assertEquals(
            listOf(
                DashboardDownloadMirror.DIRECT_GITHUB,
                DashboardDownloadMirror.GHFAST,
                DashboardDownloadMirror.GH_PROXY,
                DashboardDownloadMirror.FASTLY_JSDELIVR,
                DashboardDownloadMirror.CUSTOM,
            ),
            DashboardDownloadMirror.entries,
        )
    }

    @Test
    fun downloadUrl_directGithubUsesOfficialReleaseUrl() {
        assertEquals(
            DashboardDownloadMirror.DIRECT_DOWNLOAD_URL,
            DashboardDownloadMirror.DIRECT_GITHUB.downloadUrl(),
        )
    }

    @Test
    fun downloadUrl_ghfastPrefixesOfficialReleaseUrl() {
        assertEquals(
            "https://ghfast.top/${DashboardDownloadMirror.DIRECT_DOWNLOAD_URL}",
            DashboardDownloadMirror.GHFAST.downloadUrl(),
        )
    }

    @Test
    fun downloadUrl_ghProxyPrefixesOfficialReleaseUrl() {
        assertEquals(
            "https://gh-proxy.com/${DashboardDownloadMirror.DIRECT_DOWNLOAD_URL}",
            DashboardDownloadMirror.GH_PROXY.downloadUrl(),
        )
    }

    @Test
    fun downloadUrl_fastlyJsdelivrPrefixesOfficialReleaseUrl() {
        assertEquals(
            "https://fastly.jsdelivr.net/${DashboardDownloadMirror.DIRECT_DOWNLOAD_URL}",
            DashboardDownloadMirror.FASTLY_JSDELIVR.downloadUrl(),
        )
    }

    @Test
    fun downloadUrl_customMirrorUsesBaseDomainAsPrefix() {
        assertEquals(
            "https://mirror.example/${DashboardDownloadMirror.DIRECT_DOWNLOAD_URL}",
            DashboardDownloadMirror.CUSTOM.downloadUrl("https://mirror.example"),
        )
    }

    @Test
    fun downloadUrl_customMirrorSupportsUrlPlaceholder() {
        assertEquals(
            "https://mirror.example/?q=${DashboardDownloadMirror.DIRECT_DOWNLOAD_URL}",
            DashboardDownloadMirror.CUSTOM.downloadUrl("https://mirror.example/?q={url}"),
        )
    }

    @Test
    fun fromStorageValue_emptyValueFallsBackToDirectGithub() {
        assertEquals(
            DashboardDownloadMirror.DIRECT_GITHUB,
            DashboardDownloadMirror.fromStorageValue(null),
        )
    }

    @Test
    fun fromStorageValue_unknownValueFallsBackToDirectGithub() {
        assertEquals(
            DashboardDownloadMirror.DIRECT_GITHUB,
            DashboardDownloadMirror.fromStorageValue("missing"),
        )
    }

    @Test
    fun fromStorageValue_preservesSupportedNonDirectMirror() {
        assertEquals(
            DashboardDownloadMirror.GHFAST,
            DashboardDownloadMirror.fromStorageValue(DashboardDownloadMirror.GHFAST.name),
        )
    }

    @Test
    fun fromStorageValue_removedMirrorFallsBackToDirectGithub() {
        listOf("GHPS", "GH_API_99988866", "GITHUB_MOEYY").forEach { removedValue ->
            assertEquals(
                DashboardDownloadMirror.DIRECT_GITHUB,
                DashboardDownloadMirror.fromStorageValue(removedValue),
            )
        }
    }
}
