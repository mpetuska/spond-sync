package dev.petuska.spond.sync.core.di

import dev.zacsweers.metro.Qualifier

@Qualifier annotation class Source(val name: String = "")

@Qualifier annotation class Sink(val name: String = "")
