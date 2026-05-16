package com.example.ytdown.core.audio

import com.un4seen.bass.BASS

/**
 * BassErrorMapper - Mapeamento exaustivo de todos os códigos de erro oficiais do BASS.
 * Baseado na documentação oficial: https://www.un4seen.com/doc/#bass/BASS_ErrorGetCode.html
 */
object BassErrorMapper {

    fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            BASS.BASS_OK -> "BASS_OK: All is OK"
            BASS.BASS_ERROR_MEM -> "BASS_ERROR_MEM: Memory error"
            BASS.BASS_ERROR_FILEOPEN -> "BASS_ERROR_FILEOPEN: Can't open the file"
            BASS.BASS_ERROR_DRIVER -> "BASS_ERROR_DRIVER: Can't find a free/valid driver"
            BASS.BASS_ERROR_BUFLOST -> "BASS_ERROR_BUFLOST: The sample buffer was lost"
            BASS.BASS_ERROR_HANDLE -> "BASS_ERROR_HANDLE: Invalid handle"
            BASS.BASS_ERROR_FORMAT -> "BASS_ERROR_FORMAT: Unsupported sample format"
            BASS.BASS_ERROR_POSITION -> "BASS_ERROR_POSITION: Invalid position"
            BASS.BASS_ERROR_INIT -> "BASS_ERROR_INIT: BASS_Init has not been successfully called"
            BASS.BASS_ERROR_START -> "BASS_ERROR_START: BASS_Start has not been successfully called"
            BASS.BASS_ERROR_SSL -> "BASS_ERROR_SSL: SSL/HTTPS support is not available"
            BASS.BASS_ERROR_ALREADY -> "BASS_ERROR_ALREADY: Already initialized/paused/whatever"
            BASS.BASS_ERROR_NOTAUDIO -> "BASS_ERROR_NOTAUDIO: File does not contain audio"
            BASS.BASS_ERROR_NOCHAN -> "BASS_ERROR_NOCHAN: Can't get a free channel"
            BASS.BASS_ERROR_ILLTYPE -> "BASS_ERROR_ILLTYPE: An illegal type was specified"
            BASS.BASS_ERROR_ILLPARAM -> "BASS_ERROR_ILLPARAM: An illegal parameter was specified"
            BASS.BASS_ERROR_TIMEOUT -> "BASS_ERROR_TIMEOUT: The connection timed out"
            BASS.BASS_ERROR_FILEFORM -> "BASS_ERROR_FILEFORM: Unsupported file format"
            BASS.BASS_ERROR_SPEAKER -> "BASS_ERROR_SPEAKER: Unavailable speaker"
            BASS.BASS_ERROR_VERSION -> "BASS_ERROR_VERSION: Invalid BASS version (used by add-ons)"
            BASS.BASS_ERROR_CODEC -> "BASS_ERROR_CODEC: Codec is not available/supported"
            BASS.BASS_ERROR_ENDED -> "BASS_ERROR_ENDED: The channel/file has ended"
            BASS.BASS_ERROR_BUSY -> "BASS_ERROR_BUSY: The device is busy"
            BASS.BASS_ERROR_UNSTREAMABLE -> "BASS_ERROR_UNSTREAMABLE: The file is unstreamable"
            else -> "BASS_ERROR_UNKNOWN: Unknown error code ($errorCode)"
        }
    }
}
