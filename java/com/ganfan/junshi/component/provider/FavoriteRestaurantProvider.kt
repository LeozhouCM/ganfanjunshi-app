package com.ganfan.junshi.component.provider

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.ganfan.junshi.Constants
import com.ganfan.junshi.data.local.AppDatabase
import kotlinx.coroutines.runBlocking

class FavoriteRestaurantProvider : ContentProvider() {

    companion object {
        private const val FAVORITES = 1
        private const val FAVORITE_ID = 2
        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(Constants.AUTHORITY_FAVORITE, "favorites", FAVORITES)
            addURI(Constants.AUTHORITY_FAVORITE, "favorites/#", FAVORITE_ID)
        }
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val context = context ?: return null
        val db = AppDatabase.getInstance(context)
        return when (uriMatcher.match(uri)) {
            FAVORITES -> runBlocking {
                val userId = selectionArgs?.firstOrNull()?.toLongOrNull() ?: 0L
                val favorites = if (userId > 0) {
                    db.favoriteDao().getByUser(userId)
                } else {
                    db.favoriteDao().getByUser(1L)
                }
                MatrixCursor(arrayOf("_id", "restaurant_id", "restaurant_name", "created_at")).apply {
                    favorites.forEach { fav ->
                        addRow(arrayOf(fav.restaurantId, fav.restaurantId, fav.restaurantName, fav.createdAt))
                    }
                }
            }
            FAVORITE_ID -> {
                val id = ContentUris.parseId(uri)
                runBlocking {
                    val fav = db.favoriteDao().getByUser(1L).find { it.restaurantId == id }
                    MatrixCursor(arrayOf("_id", "restaurant_id", "restaurant_name")).apply {
                        fav?.let { addRow(arrayOf(it.restaurantId, it.restaurantId, it.restaurantName)) }
                    }
                }
            }
            else -> null
        }
    }

    override fun getType(uri: Uri): String? = when (uriMatcher.match(uri)) {
        FAVORITES -> "vnd.android.cursor.dir/vnd.ganfan.favorite"
        FAVORITE_ID -> "vnd.android.cursor.item/vnd.ganfan.favorite"
        else -> null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
