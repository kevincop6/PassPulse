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
            binding.lengthValue.text = "${value.toInt()} caracteres"
            if (fromUser) { distributeCounts(); updateCountControls(); persistDefaults(); generatePreview() }
        }
        val optionListener = CompoundButton.OnCheckedChangeListener { button, checked ->
            when (button.id) {
                binding.lowercaseSwitch.id -> if (checked) showOnly(binding.lowercaseCountPanel) else binding.lowercaseCountPanel.visibility = View.GONE
                binding.uppercaseSwitch.id -> if (checked) showOnly(binding.uppercaseCountPanel) else binding.uppercaseCountPanel.visibility = View.GONE
                binding.numbersSwitch.id -> if (checked) showOnly(binding.numbersCountPanel) else binding.numbersCountPanel.visibility = View.GONE
                binding.symbolsSwitch.id -> if (checked) showOnly(binding.symbolsCountPanel) else binding.symbolsCountPanel.visibility = View.GONE
            }
            distributeCounts(); updateCountControls(); persistDefaults(); generatePreview()
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
        binding.copyButton.setOnClickListener { copyAndSave(binding.passwordText.text.toString()) }
        binding.copyContainer.setOnClickListener { copyAndSave(binding.passwordText.text.toString()) }
        binding.lengthValue.text = "${binding.lengthSlider.value.toInt()} caracteres"
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
        binding.lowercaseCountValue.text = "Minúsculas: ${counts[0]} caracteres"; binding.uppercaseCountValue.text = "Mayúsculas: ${counts[1]} caracteres"; binding.numbersCountValue.text = "Números: ${counts[2]} caracteres"; binding.symbolsCountValue.text = "Símbolos: ${counts[3]} caracteres"
        updatingCounts = false
    }

    private fun togglePanel(panel: View) { if (panel.visibility == View.VISIBLE) panel.visibility = View.GONE else showOnly(panel) }
    private fun showOnly(panel: View) { binding.lowercaseCountPanel.visibility = if (panel === binding.lowercaseCountPanel) View.VISIBLE else View.GONE; binding.uppercaseCountPanel.visibility = if (panel === binding.uppercaseCountPanel) View.VISIBLE else View.GONE; binding.numbersCountPanel.visibility = if (panel === binding.numbersCountPanel) View.VISIBLE else View.GONE; binding.symbolsCountPanel.visibility = if (panel === binding.symbolsCountPanel) View.VISIBLE else View.GONE }
    private fun generatePreview() { viewModel.generate(binding.lengthSlider.value.toInt(), binding.lowercaseSwitch.isChecked, binding.uppercaseSwitch.isChecked, binding.numbersSwitch.isChecked, binding.symbolsSwitch.isChecked, binding.lowercaseCountSlider.value.toInt(), binding.uppercaseCountSlider.value.toInt(), binding.numbersCountSlider.value.toInt(), binding.symbolsCountSlider.value.toInt()) }
    private fun copyAndSave(value: String) { SecurityRepository(requireContext()).save(value); val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; clipboard.setPrimaryClip(ClipData.newPlainText("PassPulse", value)); refreshHistory(); Toast.makeText(requireContext(), "Contraseña copiada y guardada", Toast.LENGTH_SHORT).show() }
    private fun refreshHistory() { val items = SecurityRepository(requireContext()).read().take(5); binding.historyEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE; binding.historyList.layoutManager = LinearLayoutManager(requireContext()); binding.historyList.adapter = KeyAdapter(items) { authenticateAndCopy(it) } }
    private fun authenticateAndCopy(item: StoredKey) { val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL; val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(requireContext()), object : BiometricPrompt.AuthenticationCallback() { override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; clipboard.setPrimaryClip(ClipData.newPlainText("PassPulse", item.value)); Toast.makeText(requireContext(), "Contraseña copiada", Toast.LENGTH_SHORT).show() } }); prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Desbloquear contraseña").setSubtitle("Autentícate para copiar esta clave").setAllowedAuthenticators(authenticators).build()) }
    override fun onResume() { super.onResume(); if (_binding != null) refreshHistory() }
    override fun onDestroyView() { refreshHandler.removeCallbacks(refreshRunnable); super.onDestroyView(); _binding = null }
}
