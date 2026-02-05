package com.anymind.anymind.ui

import android.os.Bundle
import android.text.InputType
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.anymind.anymind.R
import com.anymind.anymind.sync.SyncClient
import com.anymind.anymind.util.Prefs
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class SettingsActivity : AppCompatActivity() {
    private lateinit var syncEnabledSwitch: SwitchMaterial
    private lateinit var syncOnLaunchSwitch: SwitchMaterial
    private lateinit var serverUrlInput: TextInputEditText
    private lateinit var spaceIdInput: TextInputEditText
    private lateinit var spaceSecretInput: TextInputEditText
    private lateinit var deviceIdInput: TextInputEditText
    private lateinit var showSecret: CheckBox
    private lateinit var createSpaceButton: MaterialButton
    private lateinit var saveButton: MaterialButton

    private val syncClient = SyncClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.settings_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        syncEnabledSwitch = findViewById(R.id.sync_enabled_switch)
        syncOnLaunchSwitch = findViewById(R.id.sync_on_launch_switch)
        serverUrlInput = findViewById(R.id.server_url_input)
        spaceIdInput = findViewById(R.id.space_id_input)
        spaceSecretInput = findViewById(R.id.space_secret_input)
        deviceIdInput = findViewById(R.id.device_id_input)
        showSecret = findViewById(R.id.show_secret)
        createSpaceButton = findViewById(R.id.create_space_button)
        saveButton = findViewById(R.id.save_settings_button)

        loadPrefs()

        showSecret.setOnCheckedChangeListener { _, isChecked ->
            spaceSecretInput.inputType = if (isChecked) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            spaceSecretInput.setSelection(spaceSecretInput.text?.length ?: 0)
        }

        createSpaceButton.setOnClickListener {
            createSpace()
        }

        saveButton.setOnClickListener {
            savePrefs()
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadPrefs() {
        val prefs = Prefs.get(this)
        syncEnabledSwitch.isChecked = prefs.getBoolean("sync_enabled", false)
        syncOnLaunchSwitch.isChecked = prefs.getBoolean("sync_on_launch", false)
        serverUrlInput.setText(prefs.getString("sync_server_url", ""))
        spaceIdInput.setText(prefs.getString("sync_space_id", ""))
        spaceSecretInput.setText(prefs.getString("sync_space_secret", ""))
        var deviceId = prefs.getString("sync_device_id", null)
        if (deviceId.isNullOrEmpty()) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString("sync_device_id", deviceId).apply()
        }
        deviceIdInput.setText(deviceId)
    }

    private fun savePrefs() {
        val prefs = Prefs.get(this)
        prefs.edit()
            .putBoolean("sync_enabled", syncEnabledSwitch.isChecked)
            .putBoolean("sync_on_launch", syncOnLaunchSwitch.isChecked)
            .putString("sync_server_url", serverUrlInput.text?.toString()?.trim())
            .putString("sync_space_id", spaceIdInput.text?.toString()?.trim())
            .putString("sync_space_secret", spaceSecretInput.text?.toString()?.trim())
            .apply()
    }

    private fun createSpace() {
        val baseUrl = serverUrlInput.text?.toString()?.trim().orEmpty()
        if (baseUrl.isEmpty()) {
            Toast.makeText(this, R.string.server_url_required, Toast.LENGTH_SHORT).show()
            return
        }
        createSpaceButton.isEnabled = false
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = syncClient.createSpace(baseUrl, null)
                withContext(Dispatchers.Main) {
                    spaceIdInput.setText(response.spaceId)
                    spaceSecretInput.setText(response.spaceSecret)
                    Toast.makeText(this@SettingsActivity, R.string.space_created, Toast.LENGTH_SHORT).show()
                }
            } catch (ex: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, R.string.space_create_failed, Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    createSpaceButton.isEnabled = true
                }
            }
        }
    }
}
