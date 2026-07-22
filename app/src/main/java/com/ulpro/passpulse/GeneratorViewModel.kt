package com.ulpro.passpulse

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.SecureRandom

class GeneratorViewModel : ViewModel() {
    private val _password = MutableStateFlow(PasswordGenerator.create(16, true, true, true))
    val password: StateFlow<String> = _password
    fun generate(length: Int, uppercase: Boolean, numbers: Boolean, symbols: Boolean): String {
        return PasswordGenerator.create(length, uppercase, numbers, symbols).also { _password.value = it }
    }
}

object PasswordGenerator {
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val NUMBERS = "0123456789"
    private const val SYMBOLS = "!@#%&*+-_=?."
    fun create(length: Int, uppercase: Boolean, numbers: Boolean, symbols: Boolean): String {
        val pools = buildString { append(LOWER); if (uppercase) append(UPPER); if (numbers) append(NUMBERS); if (symbols) append(SYMBOLS) }
        val random = SecureRandom(); return buildString { repeat(length.coerceIn(8, 32)) { append(pools[random.nextInt(pools.length)]) } }
    }
}
