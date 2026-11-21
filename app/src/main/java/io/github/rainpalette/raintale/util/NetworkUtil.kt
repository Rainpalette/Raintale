package io.github.rainpalette.raintale.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** 网络连通性判断（需要 ACCESS_NETWORK_STATE 权限）。 */
object NetworkUtil {

    /** 当前是否有可用的、具备联网能力的网络。 */
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
