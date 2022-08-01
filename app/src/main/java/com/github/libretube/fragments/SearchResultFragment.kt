package com.github.libretube.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.libretube.R
import com.github.libretube.adapters.SearchAdapter
import com.github.libretube.databinding.FragmentSearchResultBinding
import com.github.libretube.util.RetrofitInstance
import com.github.libretube.util.hideKeyboard
import retrofit2.HttpException
import java.io.IOException

class SearchResultFragment : Fragment() {
    private val TAG = "SearchResultFragment"
    private lateinit var binding: FragmentSearchResultBinding

    private lateinit var nextPage: String
    private var query: String = ""

    private lateinit var searchAdapter: SearchAdapter
    private var apiSearchFilter: String = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        query = arguments?.getString("query").toString()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSearchResultBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // filter options
        binding.filterChipGroup.setOnCheckedStateChangeListener { _, _ ->
            apiSearchFilter = when (
                binding.filterChipGroup.checkedChipId
            ) {
                R.id.chip_all -> "all"
                R.id.chip_videos -> "videos"
                R.id.chip_channels -> "channels"
                R.id.chip_playlists -> "playlists"
                R.id.chip_music_songs -> "music_songs"
                R.id.chip_music_videos -> "music_videos"
                R.id.chip_music_albums -> "music_albums"
                R.id.chip_music_playlists -> "music_playlists"
                else -> throw IllegalArgumentException("Filter out of range")
            }
            fetchSearch()
        }

        fetchSearch()

        binding.searchRecycler.viewTreeObserver
            .addOnScrollChangedListener {
                if (!binding.searchRecycler.canScrollVertically(1)) {
                    fetchNextSearchItems()
                }
            }
    }

    private fun fetchSearch() {
        lifecycleScope.launchWhenCreated {
            view?.let { context?.hideKeyboard(it) }
            val response = try {
                RetrofitInstance.api.getSearchResults(query, apiSearchFilter)
            } catch (e: IOException) {
                println(e)
                Log.e(TAG, "IOException, you might not have internet connection $e")
                return@launchWhenCreated
            } catch (e: HttpException) {
                Log.e(TAG, "HttpException, unexpected response")
                return@launchWhenCreated
            }
            runOnUiThread {
                if (response.items?.isNotEmpty() == true) {
                    binding.searchRecycler.layoutManager = LinearLayoutManager(requireContext())
                    searchAdapter = SearchAdapter(response.items, childFragmentManager)
                    binding.searchRecycler.adapter = searchAdapter
                }
            }
            nextPage = response.nextpage!!
        }
    }

    private fun fetchNextSearchItems() {
        lifecycleScope.launchWhenCreated {
            val response = try {
                RetrofitInstance.api.getSearchResultsNextPage(
                    query,
                    apiSearchFilter,
                    nextPage
                )
            } catch (e: IOException) {
                println(e)
                Log.e(TAG, "IOException, you might not have internet connection")
                return@launchWhenCreated
            } catch (e: HttpException) {
                Log.e(TAG, "HttpException, unexpected response," + e.response())
                return@launchWhenCreated
            }
            nextPage = response.nextpage!!
            kotlin.runCatching {
                if (response.items?.isNotEmpty() == true) {
                    searchAdapter.updateItems(response.items.toMutableList())
                }
            }
        }
    }

    private fun Fragment?.runOnUiThread(action: () -> Unit) {
        this ?: return
        if (!isAdded) return // Fragment not attached to an Activity
        activity?.runOnUiThread(action)
    }
}
