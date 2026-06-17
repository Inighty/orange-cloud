package jiamin.chen.orangecloud.core.util

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/** 用 Custom Tab 打开授权页（对应 iOS ASWebAuthenticationSession）。 */
fun Context.launchCustomTab(uri: Uri) {
    CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()
        .launchUrl(this, uri)
}
