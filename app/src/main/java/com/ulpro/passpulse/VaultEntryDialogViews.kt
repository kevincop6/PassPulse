package com.ulpro.passpulse

import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** Typed references for the vault dialog without relying on generated view binding. */
class VaultEntryDialogViews(val root: View) {
    val entryTypeIcon: ImageView = root.findViewById(R.id.entryTypeIcon)
    val entryTypeLabel: android.widget.TextView = root.findViewById(R.id.entryTypeLabel)
    val entryTypeSwitch: MaterialSwitch = root.findViewById(R.id.entryTypeSwitch)
    val nameLayout: TextInputLayout = root.findViewById(R.id.nameLayout)
    val nameEdit: TextInputEditText = root.findViewById(R.id.nameEdit)
    val websiteEdit: TextInputEditText = root.findViewById(R.id.websiteEdit)
    val usernameLayout: TextInputLayout = root.findViewById(R.id.usernameLayout)
    val usernameEdit: TextInputEditText = root.findViewById(R.id.usernameEdit)
    val passwordLayout: TextInputLayout = root.findViewById(R.id.passwordLayout)
    val passwordEdit: TextInputEditText = root.findViewById(R.id.passwordEdit)
    val notesEdit: TextInputEditText = root.findViewById(R.id.notesEdit)
    val copyPassword: ImageButton = root.findViewById(R.id.copyPassword)
    val saveEntry: MaterialButton = root.findViewById(R.id.saveEntry)
    val deleteEntry: MaterialButton = root.findViewById(R.id.deleteEntry)
}
