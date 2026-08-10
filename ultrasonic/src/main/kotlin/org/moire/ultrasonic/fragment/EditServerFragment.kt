/*
 * EditServerFragment.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputLayout
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.flag.BubbleFlag
import com.skydoves.colorpickerview.flag.FlagMode
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import java.net.MalformedURLException
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ServerSetting
import org.moire.ultrasonic.model.EditServerModel
import org.moire.ultrasonic.model.ServerSettingsModel
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.util.CommunicationError.getErrorMessage
import org.moire.ultrasonic.util.ErrorDialog
import org.moire.ultrasonic.util.InfoDialog
import org.moire.ultrasonic.util.ServerColor
import org.moire.ultrasonic.util.Util
import timber.log.Timber

private const val DIALOG_PADDING = 12

/**
 * Displays a form where server settings can be created / edited
 */
class EditServerFragment : Fragment() {

    private val serverSettingsModel: ServerSettingsModel by viewModel()
    private val activeServerProvider: ActiveServerProvider by inject()

    private var currentServerSetting: ServerSetting? = null

    private var serverNameEditText: TextInputLayout? = null
    private var serverAddressEditText: TextInputLayout? = null
    private var serverColorImageView: ImageView? = null
    private var userNameEditText: TextInputLayout? = null
    private var passwordEditText: TextInputLayout? = null
    private var selfSignedSwitch: SwitchMaterial? = null
    private var plaintextSwitch: SwitchMaterial? = null
    private var jukeboxSwitch: SwitchMaterial? = null
    private var saveButton: Button? = null
    private var testButton: Button? = null
    private var isInstanceStateSaved: Boolean = false
    private var currentColor: Int = 0
    private var selectedColor: Int? = null

    private val navArgs by navArgs<EditServerFragmentArgs>()
    val model: EditServerModel by viewModels()

    @Override
    override fun onCreate(savedInstanceState: Bundle?) {
        Util.applyTheme(this.context)
        super.onCreate(savedInstanceState)
    }

    override fun onAttach(context: Context) {
        requireActivity().onBackPressedDispatcher.addCallback(
            this,
            confirmCloseCallback
        )
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.server_edit, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        serverNameEditText = view.findViewById(R.id.edit_server_name)
        serverAddressEditText = view.findViewById(R.id.edit_server_address)
        serverColorImageView = view.findViewById(R.id.edit_server_color_picker)
        userNameEditText = view.findViewById(R.id.edit_server_username)
        passwordEditText = view.findViewById(R.id.edit_server_password)
        selfSignedSwitch = view.findViewById(R.id.edit_self_signed)
        plaintextSwitch = view.findViewById(R.id.edit_plaintext)
        jukeboxSwitch = view.findViewById(R.id.edit_jukebox)
        saveButton = view.findViewById(R.id.edit_save)
        testButton = view.findViewById(R.id.edit_test)

        if (navArgs.index != -1) {
            // Editing an existing server
            FragmentTitle.setTitle(this, R.string.server_editor_label)
            val serverSetting = serverSettingsModel.getServerSetting(navArgs.index)
            serverSetting.observe(
                viewLifecycleOwner
            ) { t ->
                if (t != null) {
                    currentServerSetting = t
                    if (!isInstanceStateSaved) setFields()
                    // Remove the minimum API version so it can be detected again
                    if (currentServerSetting?.minimumApiVersion != null) {
                        currentServerSetting!!.minimumApiVersion = null
                        serverSettingsModel.updateItem(currentServerSetting)
                        if (
                            activeServerProvider.getActiveServer().id ==
                            currentServerSetting!!.id
                        ) {
                            MusicServiceFactory.resetMusicService()
                        }
                    }
                }
            }
            saveButton!!.setOnClickListener {
                if (currentServerSetting != null) {
                    if (getFields()) {
                        serverSettingsModel.updateItem(currentServerSetting)
                        // Apply modifications if the current server was modified
                        if (
                            activeServerProvider.getActiveServer().id ==
                            currentServerSetting!!.id
                        ) {
                            MusicServiceFactory.resetMusicService()
                            RxBus.activeServerChangedPublisher.onNext(currentServerSetting!!)
                        }
                        findNavController().navigateUp()
                    }
                }
            }
        } else {
            // Creating a new server
            FragmentTitle.setTitle(this, R.string.server_editor_new_label)
            updateColor(null)
            currentServerSetting = ServerSetting()
            saveButton!!.setOnClickListener {
                if (getFields()) {
                    serverSettingsModel.saveNewItem(currentServerSetting)
                    findNavController().navigateUp()
                }
            }
        }

        testButton!!.setOnClickListener {
            if (getFields()) {
                testConnection()
            }
        }

        serverColorImageView!!.setOnClickListener {
            val bubbleFlag = BubbleFlag(context)
            bubbleFlag.flagMode = FlagMode.LAST
            ColorPickerDialog.Builder(context).apply {
                this.colorPickerView.setInitialColor(currentColor)
                this.colorPickerView.flagView = bubbleFlag
            }
                .attachAlphaSlideBar(false)
                .setPositiveButton(
                    getString(R.string.common_ok),
                    ColorEnvelopeListener { envelope, _ ->
                        selectedColor = envelope.color
                        updateColor(envelope.color)
                    }
                )
                .setNegativeButton(getString(R.string.common_cancel)) { dialogInterface, _ ->
                    dialogInterface.dismiss()
                }
                .setBottomSpace(DIALOG_PADDING)
                .show()
        }

        serverAddressEditText?.editText?.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) correctServerAddress()
        }
    }

    private val confirmCloseCallback = object : OnBackPressedCallback(
        // default to enabled
        true
    ) {
        override fun handleOnBackPressed() {
            finishActivity()
        }
    }

    override fun onStop() {
        Util.hideKeyboard(activity)
        confirmCloseCallback.isEnabled = false
        super.onStop()
    }

    override fun onResume() {
        confirmCloseCallback.isEnabled = true
        super.onResume()
    }

    private fun correctServerAddress() {
        serverAddressEditText?.editText?.setText(
            serverAddressEditText?.editText?.text?.trim(' ', '/')
        )
    }

    private fun updateColor(color: Int?) {
        val image = ContextCompat.getDrawable(requireContext(), R.drawable.thumb_drawable)
        currentColor = color ?: ServerColor.getBackgroundColor(requireContext(), null)
        image?.setTint(currentColor)
        serverColorImageView?.background = image
    }
    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        savedInstanceState.putString(
            ::serverNameEditText.name,
            serverNameEditText!!.editText?.text.toString()
        )
        savedInstanceState.putString(
            ::serverAddressEditText.name,
            serverAddressEditText!!.editText?.text.toString()
        )
        savedInstanceState.putString(
            ::userNameEditText.name,
            userNameEditText!!.editText?.text.toString()
        )
        savedInstanceState.putString(
            ::passwordEditText.name,
            passwordEditText!!.editText?.text.toString()
        )
        savedInstanceState.putBoolean(
            ::selfSignedSwitch.name,
            selfSignedSwitch!!.isChecked
        )
        savedInstanceState.putBoolean(
            ::plaintextSwitch.name,
            plaintextSwitch!!.isChecked
        )
        savedInstanceState.putBoolean(
            ::jukeboxSwitch.name,
            jukeboxSwitch!!.isChecked
        )
        savedInstanceState.putInt(
            ::serverColorImageView.name,
            currentColor
        )
        if (selectedColor != null) {
            savedInstanceState.putInt(
                ::selectedColor.name,
                selectedColor!!
            )
        }
        savedInstanceState.putBoolean(
            ::isInstanceStateSaved.name,
            true
        )

        super.onSaveInstanceState(savedInstanceState)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        if (savedInstanceState == null) return

        serverNameEditText!!.editText?.setText(
            savedInstanceState.getString(::serverNameEditText.name)
        )
        serverAddressEditText!!.editText?.setText(
            savedInstanceState.getString(::serverAddressEditText.name)
        )
        userNameEditText!!.editText?.setText(
            savedInstanceState.getString(::userNameEditText.name)
        )
        passwordEditText!!.editText?.setText(
            savedInstanceState.getString(::passwordEditText.name)
        )
        selfSignedSwitch!!.isChecked = savedInstanceState.getBoolean(::selfSignedSwitch.name)
        plaintextSwitch!!.isChecked = savedInstanceState.getBoolean(::plaintextSwitch.name)
        jukeboxSwitch!!.isChecked = savedInstanceState.getBoolean(::jukeboxSwitch.name)
        updateColor(savedInstanceState.getInt(::serverColorImageView.name))
        if (savedInstanceState.containsKey(::selectedColor.name)) {
            selectedColor = savedInstanceState.getInt(::selectedColor.name)
        }
        isInstanceStateSaved = savedInstanceState.getBoolean(::isInstanceStateSaved.name)
    }

    /**
     * Sets the values of the Form from the current Server Setting instance
     */
    private fun setFields() {
        if (currentServerSetting == null) return

        serverNameEditText!!.editText?.setText(currentServerSetting!!.name)
        serverAddressEditText!!.editText?.setText(currentServerSetting!!.url)
        userNameEditText!!.editText?.setText(currentServerSetting!!.userName)
        passwordEditText!!.editText?.setText(currentServerSetting!!.password)
        selfSignedSwitch!!.isChecked = currentServerSetting!!.allowSelfSignedCertificate
        plaintextSwitch!!.isChecked = currentServerSetting!!.forcePlainTextPassword
        jukeboxSwitch!!.isChecked = currentServerSetting!!.jukeboxByDefault
        updateColor(currentServerSetting!!.color)
    }

    /**
     * Retrieves the values in the Form to the current Server Setting instance
     * This function also does some basic validation on the fields
     */
    private fun getFields(): Boolean {
        if (currentServerSetting == null) return false
        var isValid = true
        var url: URL? = null

        if (serverAddressEditText!!.editText?.text.isNullOrBlank()) {
            serverAddressEditText!!.error = getString(R.string.server_editor_required)
            isValid = false
        } else {
            try {
                correctServerAddress()
                val urlString = serverAddressEditText!!.editText?.text.toString()
                url = URL(urlString)
                if (
                    urlString != urlString.trim(' ') ||
                    url.host.isNullOrBlank()
                ) {
                    throw MalformedURLException()
                }
                serverAddressEditText!!.error = null
            } catch (exception: MalformedURLException) {
                serverAddressEditText!!.error = getString(R.string.settings_invalid_url)
                isValid = false
            }
        }

        if (serverNameEditText!!.editText?.text.isNullOrBlank()) {
            if (isValid && url != null) {
                serverNameEditText!!.editText?.setText(url.host)
            }
        }

        if (userNameEditText!!.editText?.text.isNullOrBlank()) {
            userNameEditText!!.error = getString(R.string.server_editor_required)
            isValid = false
        } else {
            userNameEditText!!.error = null
        }

        if (isValid) {
            currentServerSetting!!.name = serverNameEditText!!.editText?.text.toString()
            currentServerSetting!!.url = serverAddressEditText!!.editText?.text.toString()
            currentServerSetting!!.color = selectedColor ?: currentColor
            currentServerSetting!!.userName = userNameEditText!!.editText?.text.toString()
            currentServerSetting!!.password = passwordEditText!!.editText?.text.toString()
            currentServerSetting!!.allowSelfSignedCertificate = selfSignedSwitch!!.isChecked
            currentServerSetting!!.forcePlainTextPassword = plaintextSwitch!!.isChecked
            currentServerSetting!!.jukeboxByDefault = jukeboxSwitch!!.isChecked
        }

        return isValid
    }

    /**
     * Checks whether any value in the fields are changed according to their original values.
     */
    private fun areFieldsChanged(): Boolean {
        if (currentServerSetting == null || currentServerSetting!!.id == -1) {
            return serverNameEditText!!.editText?.text!!.isNotBlank() ||
                serverAddressEditText!!.editText?.text.toString() != "http://" ||
                userNameEditText!!.editText?.text!!.isNotBlank() ||
                passwordEditText!!.editText?.text!!.isNotBlank()
        }

        return currentServerSetting!!.name != serverNameEditText!!.editText?.text.toString() ||
            currentServerSetting!!.url != serverAddressEditText!!.editText?.text.toString() ||
            currentServerSetting!!.userName != userNameEditText!!.editText?.text.toString() ||
            currentServerSetting!!.password != passwordEditText!!.editText?.text.toString() ||
            currentServerSetting!!.allowSelfSignedCertificate != selfSignedSwitch!!.isChecked ||
            currentServerSetting!!.forcePlainTextPassword != plaintextSwitch!!.isChecked ||
            currentServerSetting!!.jukeboxByDefault != jukeboxSwitch!!.isChecked
    }

    /**
     * Tests if the network connection to the entered Server Settings can be made
     */
    @Suppress("TooGenericExceptionCaught")
    private fun testConnection() {
        val testSetting = ServerSetting()
        val builder = InfoDialog.Builder(requireContext())
        builder.setTitle(R.string.supported_server_features)
        builder.setMessage(getProgress(testSetting))
        val dialog: AlertDialog = builder.create()
        dialog.show()

        val testJob = lifecycleScope.launch {
            try {
                val flow = model.queryFeatureSupport(currentServerSetting!!).flowOn(Dispatchers.IO)

                flow.collect {
                    model.storeFeatureSupport(testSetting, it)
                    dialog.setMessage(getProgress(testSetting))
                    Timber.w("${it.type} support: ${it.supported}")
                }

                currentServerSetting!!.chatSupport = testSetting.chatSupport
                currentServerSetting!!.bookmarkSupport = testSetting.bookmarkSupport
                currentServerSetting!!.shareSupport = testSetting.shareSupport
                currentServerSetting!!.podcastSupport = testSetting.podcastSupport
                currentServerSetting!!.videoSupport = testSetting.videoSupport
                currentServerSetting!!.jukeboxSupport = testSetting.jukeboxSupport
            } catch (cancellationException: CancellationException) {
                Timber.i(cancellationException)
            } catch (exception: Exception) {
                dialog.dismiss()
                Timber.w(exception)
                ErrorDialog.Builder(requireContext())
                    .setTitle(R.string.error_label)
                    .setMessage(getErrorMessage(exception))
                    .show()
            }
        }

        dialog.setOnDismissListener { testJob.cancel() }
    }

    private fun getProgress(serverSetting: ServerSetting): String {
        val isAnyDisabled = serverSetting.jukeboxSupport == false

        var progressString = String.format(
            """
                    |%s - ${resources.getString(R.string.jukebox)}
            """.trimMargin(),
            boolToMark(serverSetting.jukeboxSupport)
        )
        if (isAnyDisabled) {
            progressString += "\n\n" + resources.getString(R.string.server_editor_disabled_feature)
        }

        return progressString
    }

    private fun boolToMark(value: Boolean?): String {
        if (value == null) {
            return "⌛"
        }
        return if (value) "✔️" else "❌"
    }

    /**
     * Finishes the Activity, after confirmation from the user if needed
     */
    private fun finishActivity() {
        if (areFieldsChanged()) {
            ErrorDialog.Builder(requireContext())
                .setTitle(R.string.common_confirm)
                .setMessage(R.string.server_editor_leave_confirmation)
                .setPositiveButton(R.string.common_ok) { dialog, _ ->
                    dialog.dismiss()
                    findNavController().navigateUp()
                }
                .setNegativeButton(R.string.common_cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        } else {
            findNavController().navigateUp()
        }
    }
}
