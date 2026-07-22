package com.ulpro.passpulse

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.ulpro.passpulse.databinding.FragmentGeneratorBinding
import kotlinx.coroutines.launch

class GeneratorFragment : Fragment() {
    private var _binding: FragmentGeneratorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GeneratorViewModel by viewModels()
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?) = FragmentGeneratorBinding.inflate(inflater, container, false).also { _binding = it }.root
    override fun onViewCreated(view: View, state: Bundle?) {
        lifecycleScope.launch { viewModel.password.collect { binding.passwordText.text = it } }
        binding.lengthValue.text = "16 caracteres"
        binding.lengthSlider.addOnChangeListener { _, value, _ -> binding.lengthValue.text = "${value.toInt()} caracteres" }
        binding.generateButton.setOnClickListener { generateAndSave() }
        binding.copyButton.setOnClickListener { copy(binding.passwordText.text.toString()) }
    }
    private fun generateAndSave() { val value = binding.lengthSlider.value.toInt(); val generated = viewModel.generate(value, binding.uppercaseSwitch.isChecked, binding.numbersSwitch.isChecked, binding.symbolsSwitch.isChecked); SecurityRepository(requireContext()).save(generated); Toast.makeText(requireContext(), "Contraseña guardada de forma segura", Toast.LENGTH_SHORT).show() }
    private fun copy(value: String) { (requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("PassPulse", value)); Toast.makeText(requireContext(), "Contraseña copiada", Toast.LENGTH_SHORT).show() }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
