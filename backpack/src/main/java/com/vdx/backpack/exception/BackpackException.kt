package com.vdx.backpack.exception

sealed class BackpackException(message: String) : Exception(message) {
    class AuthenticationException(message: String) : BackpackException(message)
    class EncryptionException(message: String) : BackpackException(message)
    class StorageException(message: String) : BackpackException(message)
    class DatabaseException(message: String) : BackpackException(message)
}
