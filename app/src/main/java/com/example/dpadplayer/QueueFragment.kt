package com.example.dpadplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class QueueFragment : Fragment() {

    private val viewModel: MusicViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_queue, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val btnBack = view.findViewById<MaterialButton>(R.id.btn_back)
        val tvTitle = view.findViewById<TextView>(R.id.tv_detail_title)
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_queue)

        tvTitle.text = "Now Playing Queue"

        btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        applyPlayerControlFocusBackground(btnBack)
        btnBack.setupDpadItem(onFocusChanged = materialButtonFocusChangeHandler(btnBack)) {
            parentFragmentManager.popBackStack()
        }

        val activity = activity as? MainActivity

        val adapter = TrackAdapter(
            items = emptyList(),
            isQueue = true,
            onTrackClick = { index ->
                activity?.playQueueItem(index)
            },
            onMenuClick = { anchor, track, _ ->
                activity?.showTrackMenu(anchor, track)
            }
        )
        
        recycler.adapter = adapter
        recycler.layoutManager = FocusLinearLayoutManager(requireContext())

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                if (!adapter.moveItem(from, to)) return false
                activity?.moveQueueItem(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
            override fun isLongPressDragEnabled(): Boolean = true
        })
        touchHelper.attachToRecyclerView(recycler)

        var hasScrolled = false

        viewModel.queue.observe(viewLifecycleOwner) { q ->
            adapter.updateTracks(q)
            if (!hasScrolled && q.isNotEmpty()) {
                hasScrolled = true
                recycler.post {
                    val targetIndex = viewModel.currentIndex.value ?: 0
                    if (targetIndex in q.indices) {
                        recycler.scrollToPosition(targetIndex)
                        val first = recycler.layoutManager?.findViewByPosition(targetIndex) ?: recycler.getChildAt(0)
                        (first?.findViewById<View?>(R.id.clickable_item) ?: first)?.requestFocus()
                    }
                }
            }
        }

        // Focus the currently playing track highlight
        viewModel.currentIndex.observe(viewLifecycleOwner) { currentIndex ->
            adapter.setSelectedIndex(currentIndex)
        }
    }
}
