package com.ulpro.passpulse

import android.app.assist.AssistStructure
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.service.autofill.SaveInfo
import android.widget.RemoteViews
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.service.autofill.AutofillService

/** Android's system-mediated password save/fill integration. It is used only when selected as the default provider. */
class PassPulseAutofillService : AutofillService() {
    override fun onFillRequest(request: FillRequest, cancellationSignal: android.os.CancellationSignal, callback: FillCallback) {
        val entries = SecurityRepository(this).read()
        val ids = findFields(request.fillContexts.last().structure)
        if (entries.isEmpty() || ids.first == null && ids.second == null) { callback.onSuccess(null); return }
        val response = FillResponse.Builder()
        entries.take(20).forEach { entry ->
            val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply { setTextViewText(android.R.id.text1, entry.name) }
            val dataset = Dataset.Builder(presentation)
            ids.first?.let { dataset.setValue(it, AutofillValue.forText(entry.username), presentation) }
            ids.second?.let { dataset.setValue(it, AutofillValue.forText(entry.password), presentation) }
            response.addDataset(dataset.build())
        }
        callback.onSuccess(response.build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val values = HashMap<String, String>()
        request.fillContexts.last().structure.visit { node ->
            val id = node.autofillId ?: return@visit
            val value = node.autofillValue?.textValue?.toString() ?: return@visit
            values[id.toString()] = value
        }
        val password = values.entries.firstOrNull { it.value.length >= 8 }?.value
        if (!password.isNullOrBlank()) {
            val sourcePackage = request.fillContexts.last().structure.activityComponent?.packageName
            val sourceName = sourcePackage?.let { packageName -> runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString() }.getOrNull() }
                ?.takeIf { it.isNotBlank() } ?: sourcePackage ?: getString(R.string.autofill_entry_name)
            SecurityRepository(this).upsert(VaultEntry(name = sourceName, password = password, kind = "autofill"))
        }
        callback.onSuccess()
    }

    override fun onConnected() = Unit
    override fun onDisconnected() = Unit

    private fun findFields(structure: AssistStructure): Pair<AutofillId?, AutofillId?> {
        var user: AutofillId? = null; var password: AutofillId? = null
        structure.visit { node ->
            val hints = node.autofillHints?.joinToString(" ")?.lowercase().orEmpty()
            val id = node.autofillId ?: return@visit
            if (hints.contains("password")) password = id
            else if (hints.contains("username") || hints.contains("email")) user = id
        }
        return user to password
    }
}

private fun AssistStructure.visit(block: (AssistStructure.ViewNode) -> Unit) {
    fun walk(node: AssistStructure.ViewNode) { block(node); for (i in 0 until node.childCount) walk(node.getChildAt(i)) }
    for (i in 0 until windowNodeCount) walk(getWindowNodeAt(i).rootViewNode)
}
