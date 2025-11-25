package com.vdx.backpack.exception

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes

object GoogleSignInErrorHandler {
    
    fun getErrorMessage(exception: Throwable): String {
        return when (exception) {
            is ApiException -> {
                when (exception.statusCode) {
                    CommonStatusCodes.SIGN_IN_REQUIRED -> 
                        "Please sign in to continue"
                    
                    12501 -> // SIGN_IN_CANCELLED
                        "Sign-in cancelled"
                    
                    12500 -> // SIGN_IN_FAILED
                        "Sign-in failed. Please try again"
                    
                    10 -> // DEVELOPER_ERROR
                        "Configuration error. Please check your setup"
                    
                    7 -> // NETWORK_ERROR
                        "Network error. Check your internet connection"
                    
                    8 -> // INTERNAL_ERROR
                        "An internal error occurred. Please try again"
                    
                    16 -> // INTERRUPTED
                        "Sign-in was interrupted"
                    
                    CommonStatusCodes.TIMEOUT -> 
                        "Sign-in timed out. Please try again"
                    
                    CommonStatusCodes.INVALID_ACCOUNT -> 
                        "Invalid account. Please use a different account"
                    
                    else -> 
                        "Sign-in failed (Error ${exception.statusCode})"
                }
            }
            else -> exception.message ?: "Unknown error occurred"
        }
    }
    
    fun isCancellation(exception: Throwable): Boolean {
        return exception is ApiException && exception.statusCode == 12501
    }
}
