package com.ulpro.passpulse

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.SecureRandom

class GeneratorViewModel : ViewModel() {
    private val _password = MutableStateFlow(PasswordGenerator.create(16, true, true, false, true, 2, 2, 0, 2))
    val password: StateFlow<String> = _password
    fun generate(length: Int, lowercase: Boolean, uppercase: Boolean, numbers: Boolean, symbols: Boolean, lowercaseCount: Int = 2, uppercaseCount: Int = 2, numberCount: Int = 0, symbolCount: Int = 2): String {
        return PasswordGenerator.create(length, lowercase, uppercase, numbers, symbols, lowercaseCount, uppercaseCount, numberCount, symbolCount).also { _password.value = it }
    }
}

object PasswordGenerator {
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val NUMBERS = "0123456789"
    private const val SYMBOLS = "!@#%&*+-_=?."
    fun create(length: Int, lowercase: Boolean, uppercase: Boolean, numbers: Boolean, symbols: Boolean, lowercaseCount: Int = 0, uppercaseCount: Int = 0, numberCount: Int = 0, symbolCount: Int = 0): String {
        val targetLength = length.coerceIn(8, 32)
        val random = SecureRandom()
        val result = mutableListOf<Char>()
        fun addRandom(pool: String, amount: Int) { repeat(amount.coerceAtLeast(0)) { result += pool[random.nextInt(pool.length)] } }
        if (lowercase) addRandom(LOWER, lowercaseCount)
        if (uppercase) addRandom(UPPER, uppercaseCount)
        if (numbers) addRandom(NUMBERS, numberCount)
        if (symbols) addRandom(SYMBOLS, symbolCount)
        val pools = buildString { if (lowercase) append(LOWER); if (uppercase) append(UPPER); if (numbers) append(NUMBERS); if (symbols) append(SYMBOLS) }.ifEmpty { LOWER }
        while (result.size < targetLength) result += pools[random.nextInt(pools.length)]
        result.shuffle(random)
        return result.joinToString("")
    }
}
