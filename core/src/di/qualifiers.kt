package core.di

import me.tatarka.inject.annotations.Qualifier

@Qualifier annotation class Source(val name: String = "")

@Qualifier annotation class Sink(val name: String = "")
