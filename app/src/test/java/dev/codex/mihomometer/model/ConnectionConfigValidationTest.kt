package dev.codex.mihomometer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionConfigValidationTest {
    @Test
    fun validate_rejectsBlankHost() {
        assertEquals(
            ConnectionConfigError.HOST_REQUIRED,
            ControllerConnectionConfig(host = " ").validate("9090"),
        )
    }

    @Test
    fun validate_rejectsHostContainingScheme() {
        assertEquals(
            ConnectionConfigError.HOST_INVALID,
            ControllerConnectionConfig(host = "http://127.0.0.1").validate("9090"),
        )
    }

    @Test
    fun validate_allowsPortDraftToBeEmptyButRejectsItOnSubmit() {
        assertEquals(
            ConnectionConfigError.PORT_INVALID,
            ControllerConnectionConfig().validate(""),
        )
    }

    @Test
    fun validate_rejectsPortsOutsideValidRange() {
        assertEquals(ConnectionConfigError.PORT_INVALID, ControllerConnectionConfig().validate("0"))
        assertEquals(ConnectionConfigError.PORT_INVALID, ControllerConnectionConfig().validate("65536"))
    }

    @Test
    fun validate_rejectsQueryOrFragmentInSecondaryPath() {
        assertEquals(
            ConnectionConfigError.SECONDARY_PATH_INVALID,
            ControllerConnectionConfig(secondaryPath = "api?token=1").validate("9090"),
        )
        assertEquals(
            ConnectionConfigError.SECONDARY_PATH_INVALID,
            ControllerConnectionConfig(secondaryPath = "api#fragment").validate("9090"),
        )
    }

    @Test
    fun validate_acceptsNormalizedValidConfiguration() {
        assertNull(
            ControllerConnectionConfig(
                protocol = ControllerProtocol.HTTPS,
                host = "example.com",
                secondaryPath = "/controller/api/",
            ).validate("65535"),
        )
    }
}
