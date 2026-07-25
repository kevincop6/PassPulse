package com.ulpro.passpulse

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import kotlinx.coroutines.launch

class SettingsFragment : PreferenceFragmentCompat() {
    private val updatePreference: Preference?
        get() = findPreference("check_updates")

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        findPreference<SeekBarPreference>("default_length")?.apply { min = 8; max = 32 }

        findPreference<Preference>("clear_history")?.setOnPreferenceClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Borrar historial")
                .setMessage("¿Eliminar todas las contraseñas guardadas?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Borrar") { _, _ -> SecurityRepository(requireContext()).clear() }
                .show()
            true
        }

        findPreference<Preference>("about")?.apply {
            title = "Acerca de PassPulse"
            summary = "Versión ${UpdateChecker.currentVersionName()} · Seguridad local y privacidad"
            setOnPreferenceClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("PassPulse")
                    .setMessage("Versión ${UpdateChecker.currentVersionName()}\nGeneración segura, cifrado local y privacidad por diseño.")
                    .setPositiveButton("Aceptar", null)
                    .show()
                true
            }
        }

        updatePreference?.setOnPreferenceClickListener {
            checkForUpdates()
            true
        }
        updatePreference?.summary = UpdateChecker.savedStatus(requireContext())
    }

    private fun checkForUpdates() {
        updatePreference?.isEnabled = false
        updatePreference?.summary = "Comprobando…"
        viewLifecycleOwner.lifecycleScope.launch {
            val result = UpdateChecker.check(requireContext())
            updatePreference?.isEnabled = true
            updatePreference?.summary = result.toStatusText()
            Toast.makeText(requireContext(), result.toUserMessage(), Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePreference?.summary = UpdateChecker.savedStatus(requireContext())
    }
}
