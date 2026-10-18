package com.akrapovic.soundkit.community.wear

import javax.inject.Qualifier

/** Process-wide scope for work that must outlive a short-lived service callback. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
