package com.remotedroid.input

import com.remotedroid.protocol.ClientMessage
import com.remotedroid.protocol.ServerMessage

/**
 * Sunucu ile Erişilebilirlik Servisi arasındaki sınır. Sunucu komutları üretir;
 * servis bunları sisteme uygular. Böylece Ktor katmanı Android API'lerinden bağımsız kalır.
 */
interface CommandExecutor {
    fun execute(msg: ClientMessage)
    fun welcome(): ServerMessage.Welcome
}
