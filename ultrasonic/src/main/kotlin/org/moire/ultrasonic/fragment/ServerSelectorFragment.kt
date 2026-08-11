package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.ServerRowAdapter
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.OFFLINE_DB_ID
import org.moire.ultrasonic.model.ServerSettingsModel
import org.moire.ultrasonic.util.ErrorDialog
import org.moire.ultrasonic.util.Util
import timber.log.Timber

/**
 * Displays the list of configured servers, they can be selected or edited
 */
class ServerSelectorFragment : Fragment() {

    private var listView: RecyclerView? = null
    private val serverSettingsModel: ServerSettingsModel by viewModel()
    private val activeServerProvider: ActiveServerProvider by inject()
    private var serverRowAdapter: ServerRowAdapter? = null

    @Override
    override fun onCreate(savedInstanceState: Bundle?) {
        Util.applyTheme(this.context)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.server_selector, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        FragmentTitle.setTitle(this, R.string.server_selector_label)

        listView = view.findViewById(R.id.server_list)
        serverRowAdapter = ServerRowAdapter(
            view.context,
            serverSettingsModel,
            onItemClick = { server ->
                activeServerProvider.setActiveServerById(server.id)
                findNavController().popBackStack(R.id.homeFragment, false)
            },
            ::deleteServerById,
            ::editServerByIndex
        )

        listView?.layoutManager = LinearLayoutManager(requireContext())
        listView?.adapter = serverRowAdapter

        val fab = view.findViewById<FloatingActionButton>(R.id.server_add_fab)
        fab.setOnClickListener {
            editServerByIndex(-1)
        }
    }

    override fun onResume() {
        super.onResume()
        val serverList = serverSettingsModel.getServerList()
        serverList.observe(
            this
        ) { t ->
            serverRowAdapter!!.setData(t.toTypedArray())
        }
    }

    /**
     * This Callback handles the deletion of a Server Setting
     */
    private fun deleteServerById(id: Int) {
        // FIXME
        ErrorDialog.Builder(requireContext())
            .setTitle(R.string.server_menu_delete)
            .setMessage(R.string.server_selector_delete_confirmation)
            .setPositiveButton(R.string.common_delete) { dialog, _ ->
                dialog.dismiss()

                // Get the id of the current active server
                val activeServerId = ActiveServerProvider.getActiveServerId()

                // If the currently active server is deleted, go offline
                if (id == activeServerId) activeServerProvider.setActiveServerById(OFFLINE_DB_ID)

                serverSettingsModel.deleteItemById(id)

                // Clear the metadata cache
                activeServerProvider.deleteMetaDatabase(activeServerId)

                Timber.i("Server deleted, id: $id")
            }
            .setNegativeButton(R.string.common_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Starts the Edit Server Fragment to edit the details of a server
     */
    private fun editServerByIndex(index: Int) {
        val action = ServerSelectorFragmentDirections.toEditServer(index)
        findNavController().navigate(action)
    }
}
