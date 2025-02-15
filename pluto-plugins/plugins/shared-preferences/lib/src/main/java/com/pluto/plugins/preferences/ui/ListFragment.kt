package com.pluto.plugins.preferences.ui

import android.os.Bundle
import android.view.View
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.pluto.plugin.share.Shareable
import com.pluto.plugin.share.lazyContentSharer
import com.pluto.plugins.preferences.R
import com.pluto.plugins.preferences.Session
import com.pluto.plugins.preferences.databinding.PlutoPrefFragmentListBinding
import com.pluto.plugins.preferences.utils.fromEditorData
import com.pluto.plugins.preferences.utils.toEditorData
import com.pluto.utilities.autoClearInitializer
import com.pluto.utilities.extensions.hideKeyboard
import com.pluto.utilities.extensions.linearLayoutManager
import com.pluto.utilities.extensions.showMoreOptions
import com.pluto.utilities.list.BaseAdapter
import com.pluto.utilities.list.CustomItemDecorator
import com.pluto.utilities.list.DiffAwareAdapter
import com.pluto.utilities.list.DiffAwareHolder
import com.pluto.utilities.list.ListItem
import com.pluto.utilities.selector.lazyDataSelector
import com.pluto.utilities.setOnDebounceClickListener
import com.pluto.utilities.viewBinding
import com.pluto.utilities.views.keyvalue.KeyValuePairEditResult
import com.pluto.utilities.views.keyvalue.edit.KeyValuePairEditor
import com.pluto.utilities.views.keyvalue.edit.lazyKeyValuePairEditor

internal class ListFragment : Fragment(R.layout.pluto_pref___fragment_list) {
    private val binding by viewBinding(PlutoPrefFragmentListBinding::bind)
    private val viewModel: SharedPrefViewModel by activityViewModels()
    private val keyValuePairEditor: KeyValuePairEditor by lazyKeyValuePairEditor()
    private val prefAdapter: BaseAdapter by autoClearInitializer { SharedPrefAdapter(onActionListener) }
    private val contentSharer by lazyContentSharer()
    private val dataSelector by lazyDataSelector()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.refresh()
        binding.list.apply {
            adapter = prefAdapter
            addItemDecoration(CustomItemDecorator(requireContext()))
        }

        binding.search.doOnTextChanged { text, _, _, _ ->
            viewLifecycleOwner.lifecycleScope.launchWhenResumed {
                text?.toString()?.let {
                    Session.searchText = it
                    prefAdapter.list = filteredPrefs(it)
                    if (it.isEmpty()) {
                        binding.list.linearLayoutManager()?.scrollToPositionWithOffset(0, 0)
                    }
                }
            }
        }
        binding.options.setOnDebounceClickListener {
            context?.showMoreOptions(it, R.menu.pluto_pref___menu_more_options) { item ->
                when (item.itemId) {
                    R.id.filter -> openFilterView()
                }
            }
        }
        binding.filter.setOnDebounceClickListener { openFilterView() }
        binding.search.setText(Session.searchText)
        viewModel.preferenceList.removeObserver(sharedPrefObserver)
        viewModel.preferenceList.observe(viewLifecycleOwner, sharedPrefObserver)
        keyValuePairEditor.result.removeObserver(keyValuePairEditObserver)
        keyValuePairEditor.result.observe(viewLifecycleOwner, keyValuePairEditObserver)

        binding.close.setOnDebounceClickListener {
            activity?.finish()
        }
    }

    private fun openFilterView() {
        dataSelector.selectMultiple(
            title = getString(R.string.pluto_pref___shared_pref_filter),
            list = viewModel.getPrefFiles(),
            preSelected = viewModel.getSelectedPrefFiles()
        ).observe(viewLifecycleOwner) {
            val listOfSharePrefFiles = arrayListOf<SharedPrefFile>()
            it.forEach { option ->
                if (option is SharedPrefFile) {
                    listOfSharePrefFiles.add(option)
                }
            }
            viewModel.setSelectedPrefFiles(listOfSharePrefFiles)
        }
    }

    private fun filteredPrefs(search: String): List<SharedPrefKeyValuePair> {
        var list = emptyList<SharedPrefKeyValuePair>()
        viewModel.preferenceList.value?.let {
            list = it.filter { pref ->
                pref.key.contains(search, true)
            }
        }
        binding.noItemText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        return list
    }

    private val keyValuePairEditObserver = Observer<KeyValuePairEditResult> {
        it.value?.let { value ->
            if (it.metaData is SharedPrefKeyValuePair) {
                val pref: SharedPrefKeyValuePair = it.metaData as SharedPrefKeyValuePair
                viewModel.setPrefData(pref, pref.fromEditorData(value))
            }
        }
    }

    private val sharedPrefObserver = Observer<List<SharedPrefKeyValuePair>> {
        prefAdapter.list = filteredPrefs(binding.search.text.toString())
    }

    private val onActionListener = object : DiffAwareAdapter.OnActionListener {
        override fun onAction(action: String, data: ListItem, holder: DiffAwareHolder) {
            if (data is SharedPrefKeyValuePair) {
                when (action) {
                    "click" -> activity?.let {
                        it.hideKeyboard(viewLifecycleOwner.lifecycleScope) {
                            keyValuePairEditor.edit(data.toEditorData())
                        }
                    }

                    "long_click" -> contentSharer.share(
                        Shareable(
                            content = "${data.key} : ${data.value}",
                            title = "Share Shared Preference",
                            fileName = "Preference data from Pluto"
                        )
                    )
                }
            }
        }
    }
}
