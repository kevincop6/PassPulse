package com.ulpro.passpulse
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) { setPreferencesFromResource(R.xml.preferences, rootKey); findPreference<SeekBarPreference>("default_length")?.apply { min = 8; max = 32 }; findPreference<Preference>("clear_history")?.setOnPreferenceClickListener { AlertDialog.Builder(requireContext()).setTitle("Borrar historial").setMessage("¿Eliminar todas las contraseñas guardadas?").setNegativeButton("Cancelar", null).setPositiveButton("Borrar") { _, _ -> SecurityRepository(requireContext()).clear() }.show(); true }; findPreference<Preference>("about")?.setOnPreferenceClickListener { AlertDialog.Builder(requireContext()).setTitle("PassPulse").setMessage("Versión 1.0\nGeneración segura, cifrado local y privacidad por diseño.").setPositiveButton("Aceptar", null).show(); true } }
}
