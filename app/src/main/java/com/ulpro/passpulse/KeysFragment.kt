package com.ulpro.passpulse

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ulpro.passpulse.databinding.FragmentKeysBinding

class KeysFragment : Fragment() {
    private var _binding: FragmentKeysBinding? = null
    private val binding get() = _binding!!
    private var allItems = emptyList<VaultEntry>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?) = FragmentKeysBinding.inflate(inflater, container, false).also { _binding = it }.root
    override fun onViewCreated(view: View, state: Bundle?) {
        binding.searchInput.doAfterTextChanged { refreshList(it?.toString().orEmpty()) }
        refresh()
    }

    private fun refresh() { allItems = SecurityRepository(requireContext()).read(); refreshList(binding.searchInput.text?.toString().orEmpty()) }
    private fun refreshList(query: String) {
        val items = allItems.filter { query.isBlank() || listOf(it.name, it.username, it.uris.joinToString()).any { value -> value.contains(query, true) } }
        binding.emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.keysList.layoutManager = LinearLayoutManager(requireContext())
        binding.keysList.adapter = KeyAdapter(items) { authenticate(it) }
    }

    private fun authenticate(item: VaultEntry) {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(requireContext()), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { super.onAuthenticationSucceeded(result); showUnlocked(item) }
        })
        val info = BiometricPrompt.PromptInfo.Builder().setTitle(getString(R.string.unlock_password_title)).setSubtitle(getString(R.string.unlock_password_subtitle)).setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL).build()
        prompt.authenticate(info)
    }

    private fun showUnlocked(item: VaultEntry) {
        val dialogViews = VaultEntryDialogViews(layoutInflater.inflate(R.layout.dialog_vault_entry, null))
        val isWebsite = item.kind == "website" || (item.kind == "login" && item.uris.isNotEmpty())
        dialogViews.entryTypeIcon.setImageResource(if (isWebsite) R.drawable.ic_web else R.drawable.ic_app)
        dialogViews.entryTypeLabel.setText(if (isWebsite) R.string.website_entry else R.string.application_entry)
        dialogViews.entryTypeSwitch.isChecked = !isWebsite
        dialogViews.deleteEntry.visibility = View.VISIBLE
        dialogViews.nameEdit.setText(item.name)
        dialogViews.websiteEdit.setText(item.uris.firstOrNull().orEmpty())
        dialogViews.usernameEdit.setText(item.username)
        dialogViews.passwordEdit.setText(item.password)
        dialogViews.passwordEdit.transformationMethod = PasswordTransformationMethod.getInstance()
        dialogViews.notesEdit.setText(item.notes)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(item.name).setView(dialogViews.root).setNegativeButton(R.string.close, null).create()
        dialogViews.passwordLayout.setEndIconOnClickListener {
            val visible = dialogViews.passwordEdit.transformationMethod == null
            dialogViews.passwordEdit.transformationMethod = if (visible) PasswordTransformationMethod.getInstance() else HideReturnsTransformationMethod.getInstance()
            dialogViews.passwordLayout.endIconDrawable = ContextCompat.getDrawable(requireContext(), if (visible) R.drawable.ic_visibility else R.drawable.ic_visibility_off)
            dialogViews.passwordEdit.setSelection(dialogViews.passwordEdit.text?.length ?: 0)
        }
        dialogViews.entryTypeSwitch.setOnCheckedChangeListener { _, checked ->
            dialogViews.entryTypeIcon.setImageResource(if (checked) R.drawable.ic_app else R.drawable.ic_web)
            dialogViews.entryTypeLabel.setText(if (checked) R.string.application_entry else R.string.website_entry)
        }
        dialogViews.copyPassword.setOnClickListener { authenticateForCopy(dialogViews.passwordEdit.text?.toString().orEmpty()) }
        dialogViews.saveEntry.setOnClickListener {
            val title = dialogViews.nameEdit.text?.toString()?.trim().orEmpty()
            val username = dialogViews.usernameEdit.text?.toString()?.trim().orEmpty()
            dialogViews.nameLayout.error = if (title.isBlank()) getString(R.string.required_field) else null
            dialogViews.usernameLayout.error = if (username.isBlank()) getString(R.string.required_field) else null
            if (title.isNotBlank() && username.isNotBlank()) {
                val uri = dialogViews.websiteEdit.text?.toString()?.trim().orEmpty()
                SecurityRepository(requireContext()).upsert(item.copy(
                    name = title,
                    username = username,
                    password = dialogViews.passwordEdit.text?.toString().orEmpty(),
                    notes = dialogViews.notesEdit.text?.toString().orEmpty(),
                    uris = if (uri.isBlank()) emptyList() else listOf(uri),
                    kind = if (dialogViews.entryTypeSwitch.isChecked) "app" else "website"
                ))
                Toast.makeText(requireContext(), R.string.entry_updated, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                refresh()
            }
        }
        dialogViews.deleteEntry.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_entry_title)
                .setMessage(R.string.delete_entry_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete) { _, _ ->
                    SecurityRepository(requireContext()).remove(item.id)
                    dialog.dismiss()
                    refresh()
                }
                .show()
        }
        dialog.show()
    }

    private fun copyToClipboard(value: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), value))
        Toast.makeText(requireContext(), R.string.password_copied, Toast.LENGTH_SHORT).show()
    }

    private fun authenticateForCopy(value: String) {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(requireContext()), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { super.onAuthenticationSucceeded(result); copyToClipboard(value) }
        })
        val info = BiometricPrompt.PromptInfo.Builder().setTitle(getString(R.string.unlock_password_title)).setSubtitle(getString(R.string.unlock_password_subtitle)).setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL).build()
        prompt.authenticate(info)
    }

    override fun onResume() { super.onResume(); if (_binding != null) refresh() }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
