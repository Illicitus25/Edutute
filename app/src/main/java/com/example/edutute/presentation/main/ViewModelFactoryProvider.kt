package com.example.edutute.presentation.main

import androidx.fragment.app.Fragment
import androidx.appcompat.app.AppCompatActivity
import com.example.edutute.app.EdututeApp

fun Fragment.appViewModelFactory(): AppViewModelFactory =
    AppViewModelFactory((requireActivity().application as EdututeApp).appContainer)

fun AppCompatActivity.appViewModelFactory(): AppViewModelFactory =
    AppViewModelFactory((application as EdututeApp).appContainer)
