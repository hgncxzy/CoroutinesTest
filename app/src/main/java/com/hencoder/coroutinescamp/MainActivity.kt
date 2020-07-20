package com.hencoder.coroutinescamp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hencoder.coroutinescamp.model.Repo
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.SingleObserver
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.BiFunction
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import retrofit2.*
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.Exception
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    val disposable = CompositeDisposable()

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化 retrofit
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava3CallAdapterFactory.createWithScheduler(Schedulers.io()))
            .build()

        val api = retrofit.create(Api::class.java)


        textView.setOnClickListener {
            startActivity(Intent(this, PracticeActivity1::class.java))
        }
        // 直接使用 Thread 切线程
        btn1.setOnClickListener {
            Thread {
                println("xzy test01,Thread io1 ${Thread.currentThread().name}")
                runOnUiThread {
                    println("xzy test01,Thread ui1 ${Thread.currentThread().name}")
                }
            }.start()
        }

        // 直接使用 Thread 切线程
        btn2.setOnClickListener {
            thread {
                println("xzy test02,Thread io2 ${Thread.currentThread().name}")
                runOnUiThread {
                    println("xzy test02,Thread ui2 ${Thread.currentThread().name}")
                }
            }
        }

        // 使用接口回调方式依次调用两个 io 接口
        btn3.setOnClickListener {
            api.listRepos("rengwuxian")
                .enqueue(object : Callback<List<Repo>?> {
                    override fun onFailure(call: Call<List<Repo>?>, t: Throwable) {

                    }

                    override fun onResponse(
                        call: Call<List<Repo>?>,
                        response: Response<List<Repo>?>
                    ) {
                        // 第一次请求后的回调结果
                        val response1 = response.body()?.get(0)?.name
                        println("使用接口回调方式依次调用两个 io 接口，第一次请求后的回调结果：$response1")
                        // 接着执行第二次请求
                        api.listRepos("google")
                            .enqueue(object : Callback<List<Repo>?> {
                                override fun onFailure(call: Call<List<Repo>?>, t: Throwable) {

                                }

                                override fun onResponse(
                                    call: Call<List<Repo>?>,
                                    response: Response<List<Repo>?>
                                ) {
                                    // 在第二次的请求回调中合并结果并更新 ui
                                    val response2 = response.body()?.get(0)?.name
                                    textView.text = response1 + response2
                                    println("使用接口回调方式依次调用两个 io 接口，在第二次的请求回调中合并结果并更新 ui：${response1 + response2}")
                                }
                            })
                    }
                })
        }

        // 启动一个协程
        btn4.setOnClickListener {
            GlobalScope.launch {
                println("xzy Coroutines Camp 0 ${Thread.currentThread().name}")
            }
        }

        // 使用协程启动 io 代码和 ui 代码
        btn5.setOnClickListener {
            GlobalScope.launch(Dispatchers.Main) {
                ioCode1()
                uiCode1()
                ioCode2()
                uiCode2()
                ioCode3()
                uiCode3()
            }
        }

        // 使用协程 + retrofit 启动 io 代码 A,和 io 代码 B,并将结果合并后更新 ui
        btn6.setOnClickListener {
            GlobalScope.launch(Dispatchers.Main) {
                val rengwuxian = async { api.listReposKt("rengwuxian") }
                val google = async { api.listReposKt("google") }
                textView.text = "${rengwuxian.await()[0].name} + ${google.await()[0].name}"
                Log.d("xzy", textView.text.toString())
            }
        }

        // 使用 ktx 工具箱封装的协程请求 io 接口
        btn7.setOnClickListener {
            lifecycleScope.launch { // 还有 viewModelScope 等
                try {
                    // 正常请求接口，
                    val repos: List<Repo> = api.listReposKt("rengwuxian")
                    textView.text = repos[0].name + "-kt"
                } catch (e: Exception) {
                    textView.text = e.message
                }
            }
        }


        // 使用 RxJava + 协程请求多个 io 接口，并合并结果，更新 ui
        btn8.setOnClickListener {
            Single.zip<List<Repo>, List<Repo>, String>(
                api.listReposRx("rengwuxian"),
                api.listReposRx("google"),
                BiFunction { repos1, repos2 -> "${repos1[0].name} - ${repos2[0].name}" }
            ).observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : SingleObserver<String> {
                    override fun onSuccess(combined: String) {
                        textView.text = combined
                    }

                    override fun onSubscribe(d: Disposable) {
                        disposable.add(d)
                    }

                    override fun onError(e: Throwable) {
                        textView.text = e.message
                    }
                })
        }

        // 使用 executor 请求 io 操作
        btn9.setOnClickListener {
            classicIoCode1 {
                uiCode1()
            }
        }


        // 使用 executor 请求 io 操作
        btn10.setOnClickListener {
            classicIoCode1(false) {
                uiCode1()
            }
        }

        // 使用协程模拟多次请求，失败时重试
        btn11.setOnClickListener {
            GlobalScope.launch(Dispatchers.Main) {
                for (i in 1..6) {
                    try {
                        val result = ioCode4(i)
                        delay(500)
                        if (i % 3 == 0) {
                            throw Exception("error")
                            uiCode4(i, "error")
                        } else {
                            uiCode4(i, result)
                        }
                    } catch (e: Exception) {
                        uiCode4(i, "发生异常：${e.message}，开始重试")
                        val result = ioCode4(i)
                        uiCode4(i, result)
                    }
                }
            }
        }

        // 使用 CoroutineExceptionHandler 处理异常 01
        btn12.setOnClickListener {
            val handler = CoroutineExceptionHandler { coroutineContext, throwable ->
                // caught exception
                println("xzy handler 处理异常，exception:" + throwable.message)
            }
            CoroutineScope(Dispatchers.Main + handler).launch {
                for (i in 1..6) {
                    val result = ioCode4(i)
                    delay(500)
                    if (i % 3 == 0) {
                        throw NullPointerException()
                        uiCode4(i, "error")
                    } else {
                        uiCode4(i, result)
                    }
                }
            }
        }

        // 使用 CoroutineExceptionHandler 处理异常 02
        btn13.setOnClickListener {
            CoroutineScope(Dispatchers.Main + CoroutineExceptionHandler { coroutineContext, throwable ->
                // caught exception
                println("xzy handler 处理异常，exception:" + throwable.message)

            }).launch {
                for (i in 1..6) {
                    val result = ioCode4(i)
                    delay(500)
                    if (i % 3 == 0) {
                        throw NullPointerException()
                        uiCode4(i, "error")
                    } else {
                        uiCode4(i, result)
                    }
                }
            }
        }
    }

    // 使用 executor 请求 io 操作
    private val executor = ThreadPoolExecutor(5, 20, 1, TimeUnit.MINUTES, LinkedBlockingDeque())

    private fun classicIoCode1(uiThread: Boolean = true, block: () -> Unit) {
        executor.execute {
            println("xzy executor classic io1 ${Thread.currentThread().name}")
            if (uiThread) {
                runOnUiThread {
                    block()
                }
            } else {
                block()
            }
        }
    }

    override fun onDestroy() {
        disposable.dispose()
        super.onDestroy()
    }

    private suspend fun ioCode1() {
        withContext(Dispatchers.IO) {
            println("xzy Coroutines Camp io1 ${Thread.currentThread().name}")
        }
    }

    private suspend fun ioCode2() {
        withContext(Dispatchers.IO) {
            println("xzy Coroutines Camp io2 ${Thread.currentThread().name}")
        }
    }

    private suspend fun ioCode3() {
        withContext(Dispatchers.IO) {
            println("xzy Coroutines Camp io3 ${Thread.currentThread().name}")
        }
    }

    private suspend fun ioCode4(times: Int): String {
        var result: String
        withContext(Dispatchers.IO) {
            try {
                println("xzy 第 $times 次执行 io 代码")
                result = "success"
            } catch (e: Exception) {
                result = "fail"
            }
        }
        return result
    }

    private fun uiCode1() {
        println("xzy Coroutines Camp ui1 ${Thread.currentThread().name}")
    }

    private fun uiCode2() {
        println("xzy Coroutines Camp ui2 ${Thread.currentThread().name}")
    }

    private fun uiCode3() {
        println("xzy Coroutines Camp ui3 ${Thread.currentThread().name}")
    }

    private fun uiCode4(times: Int, result: String?) {
        println("xzy 第 $times 次更新 UI，请求结果为 $result")
    }
}