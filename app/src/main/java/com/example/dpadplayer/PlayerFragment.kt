package com.example.dpadplayer

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import com.bumptech.glide.Glide
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import kotlin.math.roundToInt

/**
 * Full now-playing screen.
 * Back button + D-pad BACK key return to LibraryFragment.
 * D-pad LEFT/RIGHT on seekbar scrubs by the configured seek/skip duration.
 */
class PlayerFragment : Fragment() {

    private val viewModel: MusicViewModel by activityViewModels()

    private lateinit var btnBack: MaterialButton
    private lateinit var btnQueue: MaterialButton
    private lateinit var tvTrackCounter: TextView
    private lateinit var tvSleepTimerStatus: TextView
    private lateinit var albumArt: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var tvAlbum: TextView
    private lateinit var tvPosition: TextView
    private lateinit var tvDuration: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnRepeat: MaterialButton
    private lateinit var btnPrev: MaterialButton
    private lateinit var btnPlay: MaterialButton
    private lateinit var btnNext: MaterialButton
    private lateinit var btnForward: MaterialButton
    private lateinit var btnSleepTimer: MaterialButton
    private lateinit var btnSpeed: MaterialButton
    private lateinit var btnEqualizer: MaterialButton

    private var seekBarDragging = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_player, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnBack        = view.findViewById(R.id.btn_back)
        btnQueue       = view.findViewById(R.id.btn_queue)
        tvTrackCounter = view.findViewById(R.id.tv_track_counter)
        tvSleepTimerStatus = view.findViewById(R.id.tv_sleep_timer_status)
        albumArt       = view.findViewById(R.id.album_art)
        tvTitle        = view.findViewById(R.id.tv_title)
        tvArtist       = view.findViewById(R.id.tv_artist)
        tvAlbum        = view.findViewById(R.id.tv_album)
        tvPosition     = view.findViewById(R.id.tv_position)
        tvDuration     = view.findViewById(R.id.tv_duration)
        seekBar        = view.findViewById(R.id.seek_bar)
        btnRepeat      = view.findViewById(R.id.btn_repeat)
        btnPrev        = view.findViewById(R.id.btn_prev)
        btnPlay        = view.findViewById(R.id.btn_play)
        btnNext        = view.findViewById(R.id.btn_next)
        btnForward     = view.findViewById(R.id.btn_forward)
        btnSleepTimer  = view.findViewById(R.id.btn_sleep_timer)
        btnSpeed       = view.findViewById(R.id.btn_speed)
        btnEqualizer   = view.findViewById(R.id.btn_equalizer)

        btnBack.setOnClickListener { navigateBack() }
        applyPlayerControlFocusBackground(btnBack)
        btnBack.setupDpadItem(onFocusChanged = materialButtonFocusChangeHandler(btnBack)) {
            navigateBack()
        }

        btnQueue.setOnClickListener { (activity as? MainActivity)?.openQueue() }
        applyPlayerControlFocusBackground(btnQueue)
        btnQueue.setupDpadItem(onFocusChanged = materialButtonFocusChangeHandler(btnQueue)) {
            (activity as? MainActivity)?.openQueue()
        }
        setupButtons()
        setupSeekBar()
        observeViewModel()
        updateForwardSkipDescription()
        refreshSleepTimerStatus((activity as? MainActivity)?.sleepTimerRemainingMs() ?: -1L)

        // Apply focus styling to all transport buttons
        listOf(btnRepeat, btnPrev, btnPlay, btnNext, btnForward, btnSleepTimer, btnSpeed, btnEqualizer).forEach { btn ->
            applyPlayerControlFocusBackground(btn)
            btn.setupDpadItem(onFocusChanged = materialButtonFocusChangeHandler(btn)) {
                btn.performClick()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        btnPlay.requestFocus()
        refreshNowPlaying()
        updateForwardSkipDescription()
    }

    // ── Back navigation ───────────────────────────────────────────────────────

    fun navigateBack() {
        parentFragmentManager.popBackStack()
    }

    // ── Buttons ───────────────────────────────────────────────────────────────

    private fun setupButtons() {
        btnPlay.setOnClickListener    { (activity as? MainActivity)?.togglePlayPause() }
        btnPrev.setOnClickListener    { (activity as? MainActivity)?.sendCmd("PREV") }
        btnNext.setOnClickListener    { (activity as? MainActivity)?.sendCmd("NEXT") }
        btnRepeat.setOnClickListener  { (activity as? MainActivity)?.cycleRepeat() }
        btnForward.setOnClickListener { (activity as? MainActivity)?.sendCmd("SEEK_FWD") }
        btnSleepTimer.setOnClickListener { showSleepTimerDialog() }
        btnSpeed.setOnClickListener { showPlaybackSpeedDialog() }
        btnEqualizer.setOnClickListener { (activity as? MainActivity)?.openEqualizer() }
    }

    // ── SeekBar — touch dragging + D-pad scrubbing ────────────────────────────

    private fun setupSeekBar() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(sb: SeekBar) { seekBarDragging = true }
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) tvPosition.text = formatMs(progress.toLong())
            }
            override fun onStopTrackingTouch(sb: SeekBar) {
                seekBarDragging = false
                (activity as? MainActivity)?.seekTo(sb.progress.toLong())
            }
        })

        // D-pad LEFT/RIGHT on the seekbar scrubs by the user's configured step
        seekBar.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            val step = configuredSeekStepMs()
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    val newPos = (seekBar.progress.toLong() - step).coerceAtLeast(0L)
                    seekBar.progress = newPos.toInt()
                    tvPosition.text = formatMs(newPos)
                    (activity as? MainActivity)?.seekTo(newPos)
                    true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val newPos = (seekBar.progress.toLong() + step).coerceAtMost(seekBar.max.toLong())
                    seekBar.progress = newPos.toInt()
                    tvPosition.text = formatMs(newPos)
                    (activity as? MainActivity)?.seekTo(newPos)
                    true
                }
                else -> false
            }
        }
    }

    // ── ViewModel observation ─────────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.currentIndex.observe(viewLifecycleOwner) {
            refreshNowPlaying()
            updateTrackCounter()
        }
        viewModel.isPlaying.observe(viewLifecycleOwner) { playing ->
            updatePlayPauseIcon(playing)
        }
        viewModel.position.observe(viewLifecycleOwner) { pos ->
            if (!seekBarDragging) {
                seekBar.progress = pos.toInt()
                tvPosition.text  = formatMs(pos)
            }
        }
        viewModel.repeatMode.observe(viewLifecycleOwner) { mode ->
            updateRepeatIcon(mode)
        }
        viewModel.sleepTimerRemainingMs.observe(viewLifecycleOwner) { remaining ->
            refreshSleepTimerStatus(remaining)
        }
    }

    // ── UI refresh ────────────────────────────────────────────────────────────

    private fun refreshNowPlaying() {
        val activity = activity as? MainActivity ?: return
        val track = activity.currentTrack() ?: return

        tvTitle.text    = track.title
        tvArtist.text   = track.artist
        tvAlbum.text    = track.album
        tvDuration.text = formatMs(track.duration)
        seekBar.max     = track.duration.toInt()

        // Marquee requires the view to be selected
        tvTitle.isSelected = true

        updatePlayPauseIcon(viewModel.isPlaying.value ?: false)
        updateTrackCounter()

        Glide.with(this)
            .load(track.albumArtUri)
            .placeholder(R.drawable.ic_music_note)
            .error(R.drawable.ic_music_note)
            .fallback(R.drawable.ic_music_note)
            .into(albumArt)
    }

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        val icon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        btnPlay.icon = ContextCompat.getDrawable(requireContext(), icon)
    }

    private fun updateRepeatIcon(mode: Int) {
        val icon = when (mode) {
            MusicViewModel.REPEAT_ALL -> R.drawable.ic_repeat_all
            MusicViewModel.REPEAT_ONE -> R.drawable.ic_repeat_one
            else                      -> R.drawable.ic_repeat_off
        }
        btnRepeat.icon = ContextCompat.getDrawable(requireContext(), icon)
    }

    private fun updateForwardSkipDescription() {
        val seconds = configuredSeekStepMs() / 1000
        btnForward.contentDescription = "Skip forward ${seconds} seconds"
    }

    private fun updateTrackCounter() {
        val total = viewModel.queue.value?.size ?: 0
        val idx   = viewModel.currentIndex.value ?: -1
        tvTrackCounter.text = if (total > 0 && idx >= 0) "${idx + 1} / $total" else ""
    }

    private fun showPlaybackSpeedDialog() {
        val activity = activity as? MainActivity ?: return
        val options = arrayOf("0.75x", "1.00x", "1.25x", "1.50x")
        val values = floatArrayOf(0.75f, 1.0f, 1.25f, 1.5f)
        val current = activity.playbackSpeed()
        val selected = values.indices.minByOrNull { kotlin.math.abs(values[it] - current) } ?: 1
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Playback speed")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                activity.setPlaybackSpeed(values[which])
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSleepTimerDialog() {
        val activity = activity as? MainActivity ?: return
        val options = arrayOf("Off", "15 minutes", "30 minutes", "45 minutes", "60 minutes")
        val values = longArrayOf(0L, 15L * 60_000L, 30L * 60_000L, 45L * 60_000L, 60L * 60_000L)
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Sleep timer")
            .setItems(options) { _, which ->
                val value = values[which]
                if (value <= 0L) activity.cancelSleepTimer() else activity.startSleepTimer(value)
            }
            .show()
    }

    private fun refreshSleepTimerStatus(remainingMs: Long) {
        if (remainingMs <= 0L) {
            tvSleepTimerStatus.text = ""
            return
        }
        val totalSeconds = (remainingMs / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        tvSleepTimerStatus.text = "Sleep %d:%02d".format(minutes, seconds)
    }

    private fun configuredSeekStepMs(): Long {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        return prefs.getString("seek_step", "15000")?.toLongOrNull() ?: 15_000L
    }

    private fun formatMs(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }
}
