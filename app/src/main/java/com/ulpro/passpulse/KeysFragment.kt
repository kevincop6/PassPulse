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
import com.ulpro.passpulse.databinding.DialogVaultEntryBinding
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
        val dialogBinding = DialogVaultEntryBinding.inflate(layoutInflater)
        val isWebsite = item.kind == "website" || item.uris.isNotEmpty()
        dialogBinding.entryTypeIcon.setImageResource(if (isWebsite) R.drawable.ic_web else R.drawable.ic_app)
        dialogBinding.entryTypeLabel.setText(if (isWebsite) R.string.website_entry else R.string.application_entry)
        dialogBinding.nameEdit.setText(item.name)
        dialogBinding.websiteEdit.setText(item.uris.firstOrNull().orEmpty())
        dialogBinding.usernameEdit.setText(item.username)
        dialogBinding.passwordEdit.setText(item.password)
        dialogBinding.passwordEdit.transformationMethod = PasswordTransformationMethod.getInstance()
        dialogBinding.notesEdit.setText(item.notes)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(item.name).setView(dialogBinding.root).setNegativeButton(R.string.close, null).create()
        dialogBinding.passwordLayout.setEndIconOnClickListener {
            val visible = dialogBinding.passwordEdit.transformationMethod == null
            dialogBinding.passwordEdit.transformationMethod = if (visible) PasswordTransformationMethod.getInstance() else HideReturnsTransformationMethod.getInstance()
            dialogBinding.passwordLayout.endIconDrawable = ContextCompat.getDrawable(requireContext(), if (visible) R.drawable.ic_visibility else R.drawable.ic_visibility_off)
            dialogBinding.passwordEdit.setSelection(dialogBinding.passwordEdit.text?.length ?: 0)
        }
        dialogBinding.copyPassword.setOnClickListener { authenticateForCopy(dialogBinding.passwordEdit.text?.toString().orEmpty()) }
        dialogBinding.saveEntry.setOnClickListener {
            val title = dialogBinding.nameEdit.text?.toString()?.trim().orEmpty()
            val username = dialogBinding.usernameEdit.text?.toString()?.trim().orEmpty()
            dialogBinding.nameLayout.error = if (title.isBlank()) getString(R.string.required_field) else null
            dialogBinding.usernameLayout.error = if (username.isBlank()) getString(R.string.required_field) else null
            if (title.isBlank() || username.isBlank()) return@setOnClickListener
            val uri = dialogBinding.websiteEdit.text?.toString()?.trim().orEmpty()
            SecurityRepository(requireContext()).upsert(item.copy(
                name = title,
                username = username,
                password = dialogBinding.passwordEdit.text?.toString().orEmpty(),
                notes = dialogBinding.notesEdit.text?.toString().orEmpty(),
                uris = if (uri.isBlank()) emptyList() else listOf(uri),
                kind = if (uri.isBlank()) "app" else "website"
            ))
            Toast.makeText(requireContext(), R.string.entry_updated, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            refresh()
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
