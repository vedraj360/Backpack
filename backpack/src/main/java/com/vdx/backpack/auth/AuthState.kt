package com.vdx.backpack.auth

sealed class AuthState {
    object Authenticated : AuthState()
    object NotAuthenticated : AuthState()
    data class Error(val message: String) : AuthState()
}
