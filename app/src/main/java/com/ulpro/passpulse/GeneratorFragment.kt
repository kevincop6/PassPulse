package com.ulpro.passpulse

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.Toast
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.ulpro.passpulse.databinding.FragmentGeneratorBinding
import kotlinx.coroutines.launch
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class GeneratorFragment : Fragment() {
    private var _binding: FragmentGeneratorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GeneratorViewModel by viewModels()
    private lateinit var preferences: SharedPreferences
    private var updatingCounts = false
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() { if (_binding != null) { generatePreview(); refreshHandler.postDelayed(this, 30_000L) } }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?) = FragmentGeneratorBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, state: Bundle?) {
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        loadSavedDefaults()
        lifecycleScope.launch { viewModel.password.collect { binding.passwordText.text = it } }
        binding.lengthSlider.addOnChangeListener { _, value, fromUser ->
            binding.lengthValue.text = getString(R.string.length_characters, value.toInt())
            if (fromUser) { distributeCounts(); updateCountControls(); persistDefaults(); generatePreview() }
        }
        val optionListener = CompoundButton.OnCheckedChangeListener { button, checked ->
            when (button.id) {
                binding.lowercaseSwitch.id -> if (checked) showOnly(binding.lowercaseCountPanel) else binding.lowercaseCountPanel.visibility = View.GONE
                binding.uppercaseSwitch.id -> if (checked) showOnly(binding.uppercaseCountPanel) else binding.uppercaseCountPanel.visibility = View.GONE
                binding.numbersSwitch.id -> if (checked) showOnly(binding.numbersCountPanel) else binding.numbersCountPanel.visibility = View.GONE
                binding.symbolsSwitch.id -> if (checked) showOnly(binding.symbolsCountPanel) else binding.symbolsCountPanel.visibility = View.GONE
            }
            updateCountTitleVisibility(); distributeCounts(); updateCountControls(); persistDefaults(); generatePreview()
        }
        binding.lowercaseSwitch.setOnCheckedChangeListener(optionListener)
        binding.uppercaseSwitch.setOnCheckedChangeListener(optionListener)
        binding.numbersSwitch.setOnCheckedChangeListener(optionListener)
        binding.symbolsSwitch.setOnCheckedChangeListener(optionListener)
        binding.lowercaseOption.setOnClickListener { togglePanel(binding.lowercaseCountPanel) }
        binding.uppercaseOption.setOnClickListener { togglePanel(binding.uppercaseCountPanel) }
        binding.numbersOption.setOnClickListener { togglePanel(binding.numbersCountPanel) }
        binding.symbolsOption.setOnClickListener { togglePanel(binding.symbolsCountPanel) }
        listOf(binding.lowercaseCountSlider, binding.uppercaseCountSlider, binding.numbersCountSlider, binding.symbolsCountSlider).forEach { slider ->
            slider.addOnChangeListener { _, _, fromUser -> if (fromUser && !updatingCounts) { updateCountControls(); persistDefaults(); generatePreview() } }
        }
        binding.generateButton.setOnClickListener { generatePreview() }
        binding.passwordText.setOnClickListener { copyAndSave(binding.passwordText.text.toString()) }
        binding.copyContainer.setOnClickListener { copyAndSave(binding.passwordText.text.toString()) }
        binding.lengthValue.text = getString(R.string.length_characters, binding.lengthSlider.value.toInt())
        listOf(binding.lowercaseCountPanel, binding.uppercaseCountPanel, binding.numbersCountPanel, binding.symbolsCountPanel).forEach { it.visibility = View.GONE }
        updateCountControls(); persistDefaults(); generatePreview(); refreshHistory(); refreshHandler.postDelayed(refreshRunnable, 30_000L)
    }

    private fun loadSavedDefaults() {
        binding.lengthSlider.value = preferences.getInt("default_length", 16).coerceIn(8, 32).toFloat()
        binding.lowercaseSwitch.isChecked = preferences.getBoolean("default_lowercase", true)
        binding.uppercaseSwitch.isChecked = preferences.getBoolean("default_uppercase", true)
        binding.numbersSwitch.isChecked = preferences.getBoolean("default_numbers", true)
        binding.symbolsSwitch.isChecked = preferences.getBoolean("default_symbols", true)
        if (preferences.contains("default_lowercase_count")) {
            binding.lowercaseCountSlider.value = preferences.getInt("default_lowercase_count", 4).toFloat()
            binding.uppercaseCountSlider.value = preferences.getInt("default_uppercase_count", 4).toFloat()
            binding.numbersCountSlider.value = preferences.getInt("default_numbers_count", 4).toFloat()
            binding.symbolsCountSlider.value = preferences.getInt("default_symbols_count", 4).toFloat()
        } else distributeCounts()
    }

    private fun distributeCounts() {
        val sliders = listOf(binding.lowercaseCountSlider, binding.uppercaseCountSlider, binding.numbersCountSlider, binding.symbolsCountSlider)
        val active = listOf(binding.lowercaseSwitch.isChecked, binding.uppercaseSwitch.isChecked, binding.numbersSwitch.isChecked, binding.symbolsSwitch.isChecked)
        val activeCount = active.count { it }
        if (activeCount == 0) return
        val base = binding.lengthSlider.value.toInt() / activeCount
        var remainder = binding.lengthSlider.value.toInt() % activeCount
        sliders.forEachIndexed { index, slider -> if (active[index]) slider.value = (base + if (remainder-- > 0) 1 else 0).coerceAtLeast(1).toFloat() }
    }

    private fun persistDefaults() {
        preferences.edit().putInt("default_length", binding.lengthSlider.value.toInt())
            .putBoolean("default_lowercase", binding.lowercaseSwitch.isChecked).putBoolean("default_uppercase", binding.uppercaseSwitch.isChecked)
            .putBoolean("default_numbers", binding.numbersSwitch.isChecked).putBoolean("default_symbols", binding.symbolsSwitch.isChecked)
            .putInt("default_lowercase_count", binding.lowercaseCountSlider.value.toInt()).putInt("default_uppercase_count", binding.uppercaseCountSlider.value.toInt())
            .putInt("default_numbers_count", binding.numbersCountSlider.value.toInt()).putInt("default_symbols_count", binding.symbolsCountSlider.value.toInt()).apply()
    }

    private fun updateCountControls() {
        if (updatingCounts) return
        updatingCounts = true
        val length = binding.lengthSlider.value.toInt()
        val sliders = listOf(binding.lowercaseCountSlider, binding.uppercaseCountSlider, binding.numbersCountSlider, binding.symbolsCountSlider)
        val active = listOf(binding.lowercaseSwitch.isChecked, binding.uppercaseSwitch.isChecked, binding.numbersSwitch.isChecked, binding.symbolsSwitch.isChecked)
        val counts = sliders.mapIndexed { index, slider -> if (active[index]) slider.value.toInt().coerceAtLeast(1) else 0 }.toMutableList()
        while (counts.sum() > length) { val index = counts.indices.reversed().firstOrNull { counts[it] > if (active[it]) 1 else 0 } ?: break; counts[index]-- }
        sliders.forEachIndexed { index, slider -> val otherMinimum = active.indices.count { it != index && active[it] }; slider.valueTo = (length - otherMinimum).coerceAtLeast(1).toFloat(); if (active[index]) slider.value = counts[index].coerceIn(1, slider.valueTo.toInt()).toFloat() }
        binding.lowercaseCountValue.text = getString(R.string.characters_count, getString(R.string.lowercase), counts[0])
        binding.uppercaseCountValue.text = getString(R.string.characters_count, getString(R.string.uppercase), counts[1])
        binding.numbersCountValue.text = getString(R.string.characters_count, getString(R.string.numbers), counts[2])
        binding.symbolsCountValue.text = getString(R.string.characters_count, getString(R.string.symbols), counts[3])
        updatingCounts = false
    }

    private fun togglePanel(panel: View) { if (panel.visibility == View.VISIBLE) panel.visibility = View.GONE else showOnly(panel); updateCountTitleVisibility() }
    private fun showOnly(panel: View) { binding.lowercaseCountPanel.visibility = if (panel === binding.lowercaseCountPanel) View.VISIBLE else View.GONE; binding.uppercaseCountPanel.visibility = if (panel === binding.uppercaseCountPanel) View.VISIBLE else View.GONE; binding.numbersCountPanel.visibility = if (panel === binding.numbersCountPanel) View.VISIBLE else View.GONE; binding.symbolsCountPanel.visibility = if (panel === binding.symbolsCountPanel) View.VISIBLE else View.GONE; updateCountTitleVisibility() }
    private fun updateCountTitleVisibility() { val hasSelectedRequirement = listOf(binding.lowercaseCountPanel, binding.uppercaseCountPanel, binding.numbersCountPanel, binding.symbolsCountPanel).any { it.visibility == View.VISIBLE }; binding.countRequirementsCard.visibility = if (hasSelectedRequirement) View.VISIBLE else View.GONE; binding.countRequirementsTitle.visibility = if (hasSelectedRequirement) View.VISIBLE else View.GONE }
    private fun generatePreview() { viewModel.generate(binding.lengthSlider.value.toInt(), binding.lowercaseSwitch.isChecked, binding.uppercaseSwitch.isChecked, binding.numbersSwitch.isChecked, binding.symbolsSwitch.isChecked, binding.lowercaseCountSlider.value.toInt(), binding.uppercaseCountSlider.value.toInt(), binding.numbersCountSlider.value.toInt(), binding.symbolsCountSlider.value.toInt()) }
    private fun copyAndSave(value: String) {
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.save_generated_title).setMessage(R.string.save_generated_message)
            .setNegativeButton(R.string.do_not_save) { _, _ -> copyOnly(value) }
            .setPositiveButton(R.string.save_password) { _, _ -> showSaveDialog(value) }.show()
    }
    private fun copyOnly(value: String) { val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), value)); Toast.makeText(requireContext(), R.string.password_copied, Toast.LENGTH_SHORT).show() }
    private fun showSaveDialog(value: String) {
        val dialogViews = VaultEntryDialogViews(layoutInflater.inflate(R.layout.dialog_vault_entry, null))
        dialogViews.entryTypeIcon.setImageResource(R.drawable.ic_app)
        dialogViews.entryTypeLabel.setText(R.string.application_entry)
        dialogViews.entryTypeSwitch.isChecked = true
        dialogViews.deleteEntry.visibility = View.GONE
        dialogViews.nameEdit.setText("")
        dialogViews.websiteEdit.setText("")
        dialogViews.usernameEdit.setText("")
        dialogViews.passwordEdit.setText(value)
        dialogViews.passwordEdit.transformationMethod = PasswordTransformationMethod.getInstance()
        dialogViews.notesEdit.setText("")
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.save_password).setView(dialogViews.root).setNegativeButton(R.string.cancel) { _, _ -> copyOnly(value) }.create()
        dialogViews.passwordLayout.setEndIconOnClickListener {
            val visible = dialogViews.passwordEdit.transformationMethod == null
            dialogViews.passwordEdit.transformationMethod = if (visible) PasswordTransformationMethod.getInstance() else HideReturnsTransformationMethod.getInstance()
            dialogViews.passwordLayout.endIconDrawable = ContextCompat.getDrawable(requireContext(), if (visible) R.drawable.ic_visibility else R.drawable.ic_visibility_off)
            dialogViews.passwordEdit.setSelection(dialogViews.passwordEdit.text?.length ?: 0)
        }
        dialogViews.copyPassword.setOnClickListener { authenticateAndCopyValue(dialogViews.passwordEdit.text?.toString().orEmpty()) }
        dialogViews.entryTypeSwitch.setOnCheckedChangeListener { _, checked ->
            dialogViews.entryTypeLabel.setText(if (checked) R.string.application_entry else R.string.website_entry)
            dialogViews.entryTypeIcon.setImageResource(if (checked) R.drawable.ic_app else R.drawable.ic_web)
        }
        dialogViews.saveEntry.setOnClickListener {
            val title = dialogViews.nameEdit.text?.toString()?.trim().orEmpty()
            val username = dialogViews.usernameEdit.text?.toString()?.trim().orEmpty()
            dialogViews.nameLayout.error = if (title.isBlank()) getString(R.string.required_field) else null
            dialogViews.usernameLayout.error = if (username.isBlank()) getString(R.string.required_field) else null
            if (title.isNotBlank() && username.isNotBlank()) {
                val address = dialogViews.websiteEdit.text?.toString()?.trim().orEmpty()
                SecurityRepository(requireContext()).upsert(VaultEntry(name = title, username = username, password = dialogViews.passwordEdit.text?.toString().orEmpty(), notes = dialogViews.notesEdit.text?.toString().orEmpty(), uris = if (address.isBlank()) emptyList() else listOf(address), kind = if (dialogViews.entryTypeSwitch.isChecked) "app" else "website"))
                val savedPassword = dialogViews.passwordEdit.text?.toString().orEmpty().ifBlank { value }
                copyOnly(savedPassword); dialog.dismiss(); refreshHistory()
            }
        }
        dialog.show()
    }
    private fun refreshHistory() { val items = SecurityRepository(requireContext()).read().take(15); binding.historyEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE; binding.historyList.layoutManager = LinearLayoutManager(requireContext()); binding.historyList.adapter = KeyAdapter(items) { authenticateAndCopy(it) } }
    private fun authenticateAndCopy(item: VaultEntry) { authenticateAndCopyValue(item.password) }
    private fun authenticateAndCopyValue(value: String) { val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL; val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(requireContext()), object : BiometricPrompt.AuthenticationCallback() { override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { copyOnly(value) } }); prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(getString(R.string.unlock_password_title)).setSubtitle(getString(R.string.unlock_password_subtitle)).setAllowedAuthenticators(authenticators).build()) }
    override fun onResume() { super.onResume(); if (_binding != null) refreshHistory() }
    override fun onDestroyView() { refreshHandler.removeCallbacks(refreshRunnable); super.onDestroyView(); _binding = null }
}
