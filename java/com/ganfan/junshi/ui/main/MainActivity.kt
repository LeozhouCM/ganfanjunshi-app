package com.ganfan.junshi.ui.main

import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ganfan.junshi.R
import com.ganfan.junshi.component.receiver.NetworkChangeReceiver
import com.ganfan.junshi.data.GanfanRepository
import com.ganfan.junshi.databinding.ActivityMainBinding
import com.ganfan.junshi.ui.fragment.*
import com.ganfan.junshi.ui.settings.SettingsActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: GanfanRepository
    private val networkReceiver = NetworkChangeReceiver()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var selectedNavId: Int = R.id.nav_map

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "onCreate")

        repository = GanfanRepository.getInstance(this)
        setupCustomBottomNav()
        setupMessageBadge()

        if (savedInstanceState == null) {
            selectNavItem(R.id.nav_map)
            switchFragment(MapFragment(), TAG_MAP)
        } else {
            selectedNavId = savedInstanceState.getInt(KEY_SELECTED_NAV, R.id.nav_map)
            updateNavUi(selectedNavId)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED_NAV, selectedNavId)
    }

    private fun setupCustomBottomNav() {
        val nav = binding.gjBottomNav
        nav.gjNavItemMap.setOnClickListener { onNavItemSelected(R.id.nav_map, MapFragment(), TAG_MAP) }
        nav.gjNavItemSocial.setOnClickListener { onNavItemSelected(R.id.nav_social, SocialFragment(), TAG_SOCIAL) }
        nav.gjNavItemAi.setOnClickListener { onNavItemSelected(R.id.nav_ai, HomeFragment(), TAG_AI) }
        nav.gjNavItemMessages.setOnClickListener { onNavItemSelected(R.id.nav_messages, MessagesFragment(), TAG_MESSAGES) }
        nav.gjNavItemProfile.setOnClickListener { onNavItemSelected(R.id.nav_profile, ProfileFragment(), TAG_PROFILE) }
        updateNavUi(selectedNavId)
    }

    private fun onNavItemSelected(itemId: Int, fragment: Fragment, tag: String) {
        if (selectedNavId == itemId) return
        selectNavItem(itemId)
        switchFragment(fragment, tag)
    }

    private fun selectNavItem(itemId: Int) {
        selectedNavId = itemId
        updateNavUi(itemId)
    }

    private fun updateNavUi(selectedId: Int) {
        val nav = binding.gjBottomNav
        val activeColor = ContextCompat.getColor(this, R.color.gj_nav_active)
        val inactiveColor = ContextCompat.getColor(this, R.color.gj_nav_inactive)

        styleNavItem(
            icon = nav.gjNavIconMap,
            label = nav.gjNavLabelMap,
            selected = selectedId == R.id.nav_map,
            activeColor = activeColor,
            inactiveColor = inactiveColor,
            selectedIconRes = R.drawable.ic_nav_map_selected,
            unselectedIconRes = R.drawable.ic_nav_map_unselected
        )
        styleNavItem(
            icon = nav.gjNavIconSocial,
            label = nav.gjNavLabelSocial,
            selected = selectedId == R.id.nav_social,
            activeColor = activeColor,
            inactiveColor = inactiveColor,
            selectedIconRes = R.drawable.ic_nav_social,
            unselectedIconRes = R.drawable.ic_nav_social
        )
        styleNavItem(
            icon = nav.gjNavIconMessages,
            label = nav.gjNavLabelMessages,
            selected = selectedId == R.id.nav_messages,
            activeColor = activeColor,
            inactiveColor = inactiveColor,
            selectedIconRes = R.drawable.ic_nav_messages,
            unselectedIconRes = R.drawable.ic_nav_messages
        )
        styleNavItem(
            icon = nav.gjNavIconProfile,
            label = nav.gjNavLabelProfile,
            selected = selectedId == R.id.nav_profile,
            activeColor = activeColor,
            inactiveColor = inactiveColor,
            selectedIconRes = R.drawable.ic_nav_profile,
            unselectedIconRes = R.drawable.ic_nav_profile
        )

        nav.gjNavLabelAi.setTextColor(if (selectedId == R.id.nav_ai) activeColor else inactiveColor)
        nav.gjNavLabelAi.paint.isFakeBoldText = selectedId == R.id.nav_ai
    }

    private fun styleNavItem(
        icon: ImageView,
        label: TextView,
        selected: Boolean,
        activeColor: Int,
        inactiveColor: Int,
        selectedIconRes: Int,
        unselectedIconRes: Int
    ) {
        icon.setImageResource(if (selected) selectedIconRes else unselectedIconRes)
        label.setTextColor(if (selected) activeColor else inactiveColor)
        label.paint.isFakeBoldText = selected
    }

    private fun setupMessageBadge() {
        lifecycleScope.launch {
            repository.observeTotalUnreadCount().collectLatest { count ->
                updateMessageBadge(count)
            }
        }
    }

    private fun updateMessageBadge(count: Int) {
        val badge = binding.gjBottomNav.gjNavMessagesBadge
        if (count <= 0) {
            badge.visibility = View.GONE
            return
        }
        badge.visibility = View.VISIBLE
        badge.text = if (count > 99) "99+" else count.toString()
    }

    private fun switchFragment(fragment: Fragment, tag: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.gj_fragment_container, fragment, tag)
            .commit()
    }

    override fun onRestart() {
        super.onRestart()
        Log.d(TAG, "onRestart - 从设置页返回，刷新数据")
        val current = supportFragmentManager.findFragmentById(R.id.gj_fragment_container)
        if (current is Refreshable) current.onRefresh()
    }

    override fun onStart() {
        super.onStart()
        registerNetworkMonitor()
    }

    override fun onStop() {
        super.onStop()
        unregisterNetworkMonitor()
    }

    private fun registerNetworkMonitor() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                sendNetworkBroadcast(true)
            }
            override fun onLost(network: android.net.Network) {
                sendNetworkBroadcast(false)
            }
        }
        cm.registerDefaultNetworkCallback(networkCallback!!)
    }

    private fun unregisterNetworkMonitor() {
        networkCallback?.let {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(it)
        }
        networkCallback = null
    }

    private fun sendNetworkBroadcast(connected: Boolean) {
        val intent = Intent(this, NetworkChangeReceiver::class.java).apply {
            putExtra("is_connected", connected)
        }
        networkReceiver.onReceive(this, intent)
    }

    fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    interface Refreshable {
        fun onRefresh()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val KEY_SELECTED_NAV = "selected_nav_id"
        private const val TAG_MAP = "map"
        private const val TAG_SOCIAL = "social"
        private const val TAG_AI = "ai"
        private const val TAG_MESSAGES = "messages"
        private const val TAG_PROFILE = "profile"
    }
}
