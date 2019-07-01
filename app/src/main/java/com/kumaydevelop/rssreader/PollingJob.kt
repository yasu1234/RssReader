package com.kumaydevelop.rssreader

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import com.kumaydevelop.rssreader.Interface.RssClient
import com.kumaydevelop.rssreader.Model.BlogModel
import com.kumaydevelop.rssreader.Model.SettingModel
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import io.realm.Realm
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class PollingJob() : JobService() {

    private lateinit var realm: Realm

    override fun onStartJob(params: JobParameters?): Boolean {
        realm = Realm.getDefaultInstance()

        val blogs = realm.copyFromRealm(realm.where(BlogModel::class.java).findAll())!!
        val setting = realm.copyFromRealm(realm.where(SettingModel::class.java).findFirst())!!

        val count = Constants.DisplayCount.values().filter { it.code == setting.displayCountCode }.map { it.count }.get(0)

        if (blogs.size != 0) {
            for (blog in blogs) {
                // rssのURLを作成(ブログによって/以下が違うため動的に作成)
                val url = blog.url.split("//")
                val baseUrl = url.get(1).split("/").get(0)
                val addUrl = url.get(1).split("/").get(1)
                val retrofit = Retrofit.Builder()
                        .baseUrl(url.get(0)+ "//" + baseUrl + "/")
                        .client(OkHttpClient())
                        .addConverterFactory(SimpleXmlConverterFactory.create())
                        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                        .build()

                val response = retrofit.create(RssClient::class.java).get(addUrl)

                // 非同期で記事を取得し、最新記事があれば、通知を行う
                response.observeOn(AndroidSchedulers.mainThread())
                        .subscribeOn(Schedulers.newThread())
                        .subscribe( {
                            val formatter = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
                            val formatDate = formatter.parse(it.lastBuildDate)
                            if (blog.lastUpdate.after(formatDate)) {
                                realm.executeTransactionAsync {
                                    blog.lastUpdate = formatDate
                                }
                                notifyUpdate(this, blog)
                            }
                        }, {
                            Log.e("ERROR", it.cause.toString())
                        })
                }
        }
        realm.close()

        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return false
    }
}