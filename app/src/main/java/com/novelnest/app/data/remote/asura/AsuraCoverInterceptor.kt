package com.novelnest.app.data.remote.asura

import okhttp3.Interceptor
import okhttp3.Response

/** Source headers for the shared poster loader; never applies to other sources or chapter downloads. */
internal class AsuraCoverInterceptor(private val debug: Boolean = false) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val url = original.url
        val cover = url.isHttps && url.host in AsuraUrls.imageHosts && url.port == 443 &&
            url.username.isEmpty() && url.password.isEmpty() &&
            (url.encodedPath.startsWith("/asura-images/covers/") || url.encodedPath.startsWith("/asura-images/banners/"))
        if (!cover) return chain.proceed(original)
        val request = original.newBuilder()
            .header("Referer", AsuraUrls.BASE + "/")
            .header("User-Agent", AsuraCachePolicy.USER_AGENT)
            .header("Accept", "image/webp,image/png,image/jpeg,image/gif,*/*;q=0.8")
            .build()
        val response = chain.proceed(request)
        if (debug && !response.isSuccessful) android.util.Log.w("AsuraCover",
            "Cover HTTP "+response.code+"; server="+response.header("Server")+
                "; mitigation="+response.header("cf-mitigated")+"; type="+response.header("Content-Type"))
        return response
    }
}
