package com.inspirecoding.rxjava_newsapp_1

import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import android.widget.SearchView
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.inspirecoding.rxjava_newsapp_1.model.Article
import com.inspirecoding.rxjava_newsapp_1.model.TopHeadlines
import com.inspirecoding.rxjava_newsapp_1.repository.TopHeadlinesEndpoint
import com.jakewharton.retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.observers.DisposableObserver
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity(), SwipeRefreshLayout.OnRefreshListener {

    private val ENDPOINT_URL by lazy { "https://newsapi.org/v2/" }
    private lateinit var topHeadlinesEndpoint: TopHeadlinesEndpoint
    private lateinit var newsApiConfig: String
    private lateinit var articleAdapter: ArticleAdapter
    private lateinit var articleList: ArrayList<Article>
    private lateinit var userKeywordInput: String
    // RxJava related fields
    private lateinit var topHeadlinesObservable: Observable<TopHeadlines>
    private lateinit var compositeDisposable: CompositeDisposable


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Network request
        val retrofit: Retrofit = generateRetrofitBuilder()
        topHeadlinesEndpoint = retrofit.create(TopHeadlinesEndpoint::class.java)

        newsApiConfig = resources.getString(R.string.api_key)

        swipe_refresh.setOnRefreshListener(this)
        swipe_refresh.setColorSchemeResources(R.color.purple_500)

        articleList = ArrayList()
        articleAdapter = ArticleAdapter(articleList)
        //When the app is launched of course the user input is empty.
        userKeywordInput = ""
        //CompositeDisposable is needed to avoid memory leaks
        compositeDisposable = CompositeDisposable()
        recycler_view.setHasFixedSize(true)
        recycler_view.layoutManager = LinearLayoutManager(this)
        recycler_view.itemAnimator = DefaultItemAnimator()
        recycler_view.adapter = articleAdapter
    }

    override fun onDestroy() {
        super.onDestroy()
        compositeDisposable.clear()
    }

    override fun onRefresh() {
        checkUserKeywordInput()
    }


    private fun checkUserKeywordInput() {
        if (userKeywordInput.isEmpty()) {
            queryTopHeadlines()
        } else {
            getKeyWordQuery(userKeywordInput)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (menu != null) {
            val inflater: MenuInflater = menuInflater
            inflater.inflate(R.menu.menu_main, menu)
            //Creates input field for the user search
            setUpSearchMenuItem(menu)
        }
        return true
    }

    private fun setUpSearchMenuItem(menu: Menu) {
        val searchManager: SearchManager = (getSystemService(Context.SEARCH_SERVICE)) as SearchManager
        val searchView: SearchView = ((menu.findItem(R.id.action_search)?.actionView)) as SearchView
        val searchMenuItem: MenuItem = menu.findItem(R.id.action_search)

        searchView.setSearchableInfo(searchManager.getSearchableInfo(componentName))
        searchView.queryHint = "Type any keyword to search..."
        searchView.setOnQueryTextListener(onQueryTextListenerCallback())
        searchMenuItem.icon.setVisible(false, false)
    }

    //Gets immediately triggered when user clicks on search icon and enters something
    private fun onQueryTextListenerCallback(): SearchView.OnQueryTextListener {
        return object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(userInput: String?): Boolean {
                return checkQueryText(userInput)
            }

            override fun onQueryTextChange(userInput: String?): Boolean {
                return checkQueryText(userInput)
            }
        }
    }

    private fun checkQueryText(queryText: String?): Boolean {
        if (queryText != null && queryText.length > 1) {
            userKeywordInput = queryText
            getKeyWordQuery(queryText)
        } else if (queryText != null || queryText == "") {
            userKeywordInput = ""
            queryTopHeadlines()
        }
        return false
    }
    private fun getKeyWordQuery(userKeywordInput: String?) {
        swipe_refresh.isRefreshing = true
        if (userKeywordInput != null && userKeywordInput.isNotEmpty()) {
            topHeadlinesObservable = topHeadlinesEndpoint.getUserSearchInput(newsApiConfig, userKeywordInput)
            subscribeObservableOfArticle()
        } else {
            queryTopHeadlines()
        }
    }
    private fun queryTopHeadlines() {
        swipe_refresh.isRefreshing = true
        topHeadlinesObservable = topHeadlinesEndpoint.getTopHeadlines("us", newsApiConfig)
        subscribeObservableOfArticle()
    }
    private fun subscribeObservableOfArticle() {
        articleList.clear()
        compositeDisposable.add(
            topHeadlinesObservable.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMap {
                    Observable.fromIterable(it.articles)
                }
                .subscribeWith(createArticleObserver())
        )
    }
    private fun createArticleObserver(): DisposableObserver<Article> {
        return object : DisposableObserver<Article>() {
            override fun onNext(article: Article) {
                if (!articleList.contains(article)) {
                    articleList.add(article)
                }
                Log.d("createArticleObserver", "onNext: $article")
            }

            override fun onComplete() {
                showArticlesOnRecyclerView()
            }

            override fun onError(e: Throwable) {
                Log.e("createArticleObserver", "Article error: ${e.message}")
            }
        }
    }
    private fun showArticlesOnRecyclerView() {
        if (articleList.size > 0) {
            empty_text.visibility = View.GONE
            retry_fetch_button.visibility = View.GONE
            recycler_view.visibility = View.VISIBLE
            articleAdapter.setArticles(articleList)
        } else {
            recycler_view.visibility = View.GONE
            empty_text.visibility = View.VISIBLE
            retry_fetch_button.visibility = View.VISIBLE
            retry_fetch_button.setOnClickListener { checkUserKeywordInput() }
        }
        swipe_refresh.isRefreshing = false
    }


    private fun generateRetrofitBuilder(): Retrofit {

        return Retrofit.Builder()
            .baseUrl(ENDPOINT_URL)
            .addConverterFactory(GsonConverterFactory.create())
            //Add RxJava2CallAdapterFactory as a Call adapter when building your Retrofit instance
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .build()
    }
}