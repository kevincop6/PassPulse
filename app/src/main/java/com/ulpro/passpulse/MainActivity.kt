package com.ulpro.passpulse

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.ulpro.passpulse.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        binding.bottomNavigation.setupWithNavController(navHost.navController)
        SecurityRepository(this).ensureDeviceKey()
        CleanupWorker.schedule(this)
        if (savedInstanceState == null && PreferenceManager.getDefaultSharedPreferences(this).getBoolean("biometric_required", false)) {
            requestAppAuthentication()
        }
    }

    private fun requestAppAuthentication() {
        val manager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "Configura un método de bloqueo para proteger PassPulse", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Toast.makeText(this@MainActivity, errString, Toast.LENGTH_SHORT).show()
                finish()
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder().setTitle("Desbloquear PassPulse").setSubtitle("Confirma tu identidad para abrir la aplicación").setAllowedAuthenticators(authenticators).build()
        prompt.authenticate(info)
    }
}
