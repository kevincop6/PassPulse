package com.ulpro.passpulse

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.ListPreference
import kotlinx.coroutines.launch

class SettingsFragment : PreferenceFragmentCompat() {
    private val updatePreference: Preference?
        get() = findPreference("check_updates")

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        findPreference<SeekBarPreference>("default_length")?.apply { min = 8; max = 32 }
        findPreference<ListPreference>("theme_mode")?.setOnPreferenceChangeListener { _, newValue ->
            ThemeManager.apply(requireContext(), newValue.toString())
            true
        }
        findPreference<Preference>("clear_history")?.setOnPreferenceClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.clear_history_dialog_title)
                .setMessage(R.string.clear_history_dialog_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete) { _, _ -> SecurityRepository(requireContext()).clear() }
                .show()
            true
        }
        findPreference<Preference>("about")?.apply {
            title = getString(R.string.about_title)
            summary = getString(R.string.about_summary)
            setOnPreferenceClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.about_title)
                    .setMessage(getString(R.string.about_message, UpdateChecker.currentVersionName()))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                true
            }
        }
        updatePreference?.setOnPreferenceClickListener { checkForUpdates(); true }
        updatePreference?.summary = UpdateChecker.savedStatus(requireContext())
    }

    private fun checkForUpdates() {
        updatePreference?.isEnabled = false
        updatePreference?.summary = getString(R.string.checking_updates)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = UpdateChecker.check(requireContext())
            updatePreference?.isEnabled = true
            updatePreference?.summary = result.toStatusText(requireContext())
            if (result.isUpdateAvailable()) {
                if (!ApkUpdateManager.installIfDownloaded(requireContext())) {
                    if (result.assetUrl == null) {
                        Toast.makeText(requireContext(), R.string.downloadable_release_missing, Toast.LENGTH_LONG).show()
                    } else {
                        AlertDialog.Builder(requireContext())
                            .setTitle(R.string.update_available_title)
                            .setMessage(getString(R.string.update_available_message, result.latestVersion))
                            .setNegativeButton(R.string.not_now, null)
                            .setPositiveButton(R.string.download) { _, _ ->
                                val started = ApkUpdateManager.startDownload(requireContext(), result)
                                Toast.makeText(requireContext(), if (started) R.string.download_started else R.string.download_start_failed, Toast.LENGTH_LONG).show()
                            }
                            .show()
                    }
                }
            } else {
                Toast.makeText(requireContext(), result.toUserMessage(requireContext()), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePreference?.summary = UpdateChecker.savedStatus(requireContext())
    }
}
