package com.ulpro.passpulse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.ulpro.passpulse.databinding.FragmentKeysBinding

class KeysFragment : Fragment() {
    private var _binding: FragmentKeysBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?) = FragmentKeysBinding.inflate(inflater, container, false).also { _binding = it }.root
    override fun onViewCreated(view: View, state: Bundle?) { refresh() }
    private fun refresh() { val items = SecurityRepository(requireContext()).read(); binding.emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE; binding.keysList.layoutManager = LinearLayoutManager(requireContext()); binding.keysList.adapter = KeyAdapter(items) { item -> authenticate(item) } }
    private fun authenticate(item: StoredKey) { val executor = ContextCompat.getMainExecutor(requireContext()); val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() { override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { super.onAuthenticationSucceeded(result); val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager; clipboard.setPrimaryClip(android.content.ClipData.newPlainText(getString(R.string.app_name), item.value)); Toast.makeText(requireContext(), R.string.password_copied, Toast.LENGTH_SHORT).show() } }); val info = BiometricPrompt.PromptInfo.Builder().setTitle(getString(R.string.unlock_password_title)).setSubtitle(getString(R.string.unlock_password_subtitle)).setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL).build(); prompt.authenticate(info) }
    override fun onResume() { super.onResume(); if (_binding != null) refresh() }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
