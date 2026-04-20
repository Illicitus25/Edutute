package com.example.edutute.core.util

import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.tasks.await

inline fun <reified T : Any> DocumentSnapshot.toModel(): T? = toObject(T::class.java)

inline fun <reified T : Any> QuerySnapshot.toModelList(): List<T> =
    documents.mapNotNull { it.toObject(T::class.java) }

suspend fun Query.awaitCount(): Int = count().get(AggregateSource.SERVER).await().count.toInt()
