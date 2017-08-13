package me.proxer.app.bookmark

import android.app.Application
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import me.proxer.app.MainApplication.Companion.api
import me.proxer.app.base.PagedContentViewModel
import me.proxer.app.util.ErrorUtils
import me.proxer.app.util.data.ResettingMutableLiveData
import me.proxer.app.util.data.UniqueQueue
import me.proxer.app.util.extension.buildOptionalSingle
import me.proxer.library.api.PagingLimitEndpoint
import me.proxer.library.entitiy.ucp.Bookmark
import me.proxer.library.enums.Category

/**
 * @author Ruben Gees
 */
class BookmarkViewModel(application: Application) : PagedContentViewModel<Bookmark>(application) {

    override val itemsOnPage = 30
    override val isLoginRequired = true

    override val endpoint: PagingLimitEndpoint<List<Bookmark>>
        get() = api.ucp().bookmarks().category(category)

    val itemRemovalError = ResettingMutableLiveData<ErrorUtils.ErrorAction?>()

    private var category: Category? = null

    private val removalQueue = UniqueQueue<Bookmark>()
    private var removalDisposable: Disposable? = null

    override fun onCleared() {
        removalDisposable?.dispose()
        removalDisposable = null

        super.onCleared()
    }

    override fun load() {
        if (page == 0) {
            data.value = null
        }

        super.load()
    }

    override fun refresh() = reload()

    fun setCategory(value: Category?, trigger: Boolean = true) {
        if (category != value) {
            category = value

            if (trigger) reload()
        }
    }

    fun addItemToRemove(item: Bookmark) {
        removalQueue.add(item)

        doItemRemoval()
    }

    private fun doItemRemoval() {
        removalDisposable?.dispose()

        removalQueue.poll()?.let { item ->
            removalDisposable = api.ucp().deleteBookmark(item.id)
                    .buildOptionalSingle()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        data.value = data.value?.filterNot { it == item }

                        doItemRemoval()
                    }, {
                        removalQueue.clear()

                        itemRemovalError.value = ErrorUtils.handle(it)
                    })
        }
    }
}