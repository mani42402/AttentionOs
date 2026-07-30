package com.attentionos.core.di

import android.content.Context
import com.attentionos.AttentionApplication

/**
 * Resolves the app's container from any [Context].
 *
 * Workers previously wrote `applicationContext as AttentionApplication`. That holds when
 * WorkManager hands the worker the real Application, and throws `ClassCastException` the moment
 * anything wraps it — which a background worker on a periodic schedule turns into a crash loop
 * with retries. Walking to the application context first makes the lookup work through wrappers,
 * and the failure message names the actual cause instead of reporting a cast.
 */
internal val Context.attentionContainer: AppContainer
    get() {
        val application = applicationContext
        return (application as? AttentionApplication)?.container
            ?: error(
                "expected AttentionApplication, found ${application.javaClass.name}; " +
                    "the container cannot be resolved from this context",
            )
    }
