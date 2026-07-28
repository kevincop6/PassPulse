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
import android.content.Intent

class SettingsFragment : PreferenceFragmentCompat() {
    private val updatePreference: Preference?
        get() = findPreference("check_updates")

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        findPreference<Preference>("backup_location")?.setOnPreferenceClickListener {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, "passpulse-vault.backup").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), 9401)
            true
        }
        findPreference<Preference>("restore_backup")?.setOnPreferenceClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/octet-stream").addCategory(Intent.CATEGORY_OPENABLE), 9402)
            true
        }
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 9401 && resultCode == android.app.Activity.RESULT_OK) data?.data?.let { uri ->
            requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            requireContext().getSharedPreferences("vault_backup", 0).edit().putString("uri", uri.toString()).apply()
            VaultBackupScheduler.schedule(requireContext())
            findPreference<Preference>("backup_location")?.summary = getString(R.string.backup_enabled)
        }
        if (requestCode == 9402 && resultCode == android.app.Activity.RESULT_OK) data?.data?.let { uri ->
            runCatching { requireContext().contentResolver.openInputStream(uri)?.use { SecurityRepository(requireContext()).importEncrypted(it.readBytes()) } ?: error("empty") }
                .onSuccess { Toast.makeText(requireContext(), R.string.backup_restored, Toast.LENGTH_LONG).show() }
                .onFailure { Toast.makeText(requireContext(), R.string.backup_restore_failed, Toast.LENGTH_LONG).show() }
        }
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
