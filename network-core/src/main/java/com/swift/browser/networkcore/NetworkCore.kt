package com.swift.browser.networkcore

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object NetworkCore {
    private const val TAG = "NetworkCore"

    private val customDnsCache = ConcurrentHashMap<String, List<InetAddress>>()

    val activeConnectionPool = ConnectionPool(30, 5, TimeUnit.MINUTES)

    val optimizedDispatcher = Dispatcher().apply {
        maxRequests = 128
        maxRequestsPerHost = 32
    }

    val smartDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val cached = customDnsCache[hostname]
            if (cached != null) {
                return cached
            }
            return try {
                val resolved = Dns.SYSTEM.lookup(hostname)
                customDnsCache[hostname] = resolved
                resolved
            } catch (e: Exception) {
                Log.w(TAG, "DNS resolution failed for $hostname, using fallback system query.")
                Dns.SYSTEM.lookup(hostname)
            }
        }
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(activeConnectionPool)
            .dispatcher(optimizedDispatcher)
            .dns(smartDns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    fun optimizeClient(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        return builder
            .connectionPool(activeConnectionPool)
            .dispatcher(optimizedDispatcher)
            .dns(smartDns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
    }

    fun prefetchDns(hosts: List<String>) {
        CoroutineScope(Dispatchers.IO).launch {
            for (host in hosts) {
                try {
                    val resolved = InetAddress.getAllByName(host).toList()
                    customDnsCache[host] = resolved
                    Log.d(TAG, "DNS prefetch success for: $host -> $resolved")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed DNS prefetching for $host")
                }
            }
        }
    }

    fun <T> createService(serviceClass: Class<T>, baseUrl: String): T {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(serviceClass)
    }
}
