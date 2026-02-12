package org.fitznet.fun.utils;

/**
 * Constants for HTTP headers used in ESP32 firmware update communication.
 */
public class Constants {
    /** Header for the latest firmware version available. */
    public static final String LATEST_VERSION_HEADER = "x-Latest-Version";

    /** Header containing the current firmware version of the ESP32 device. */
    public static final String ESP32_VERSION_HEADER = "x-ESP32-version";

    /** Header containing the MAC address of the ESP32 device. */
    public static final String ESP32_MAC_ADDRESS_HEADER = "x-ESP32-MAC";

    /** Header for firmware error messages. */
    public static final String ESP32_ERROR_HEADER = "X-Firmware-Error";
}
