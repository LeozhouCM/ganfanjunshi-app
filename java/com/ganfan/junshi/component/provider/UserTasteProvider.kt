package com.ganfan.junshi.component.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.ganfan.junshi.Constants
import com.ganfan.junshi.data.local.AppDatabase
import kotlinx.coroutines.runBlocking

class UserTasteProvider : ContentProvider() {

    companion object {
        private const val TASTE = 1
        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(Constants.AUTHORITY_TASTE, "taste/#", TASTE)
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
        val userId = uri.lastPathSegment?.toLongOrNull() ?: return null
        val db = AppDatabase.getInstance(context)
        return runBlocking {
            val taste = db.userTasteDao().getByUserId(userId)
            MatrixCursor(arrayOf("user_id", "spice_level", "budget_min", "budget_max", "cuisines", "taboos")).apply {
                taste?.let {
                    addRow(arrayOf(it.userId, it.spiceLevel, it.budgetMin, it.budgetMax, it.cuisines, it.taboos))
                }
            }
        }
    }

    override fun getType(uri: Uri): String? = "vnd.android.cursor.item/vnd.ganfan.taste"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
