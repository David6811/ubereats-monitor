package com.weixu.ueatsmonitor.action

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import kotlinx.coroutines.flow.StateFlow

/**
 * Action. The one connection to Supabase, where the driver's rules live so
 * that the phone and the laptop editor read and write the same copy.
 *
 * The key here is the publishable one: it is meant to be shipped in the app,
 * and on its own it can reach nothing - every row is behind a policy that
 * needs the signed-in driver's own id.
 */
object Cloud {

    private const val URL = "https://umumewxrzwqxbcvttmmf.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_ctg4zQ0W9kCNTH8IPg7fGg_8o25NAjr"

    /** Where an edge function of ours answers. */
    fun functionUrl(name: String): String = "$URL/functions/v1/$name"

    val client: SupabaseClient by lazy {
        createSupabaseClient(supabaseUrl = URL, supabaseKey = PUBLISHABLE_KEY) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }
    }

    /** Signed in, signed out, or still finding out - the login screen reads this. */
    val session: StateFlow<SessionStatus> get() = client.auth.sessionStatus

    suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    /** With email confirmation off in the project, this also signs the new driver in. */
    suspend fun signUp(email: String, password: String) {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signOut() = client.auth.signOut()

    /** The signed-in driver's id, which is also the key of their rules row. */
    fun userId(): String? = client.auth.currentUserOrNull()?.id
}
