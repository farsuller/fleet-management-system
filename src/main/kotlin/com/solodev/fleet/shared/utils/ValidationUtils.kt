package com.solodev.fleet.shared.utils

/**
 * Shared validation logic for backend DTOs.
 * Ensures data integrity across User and Driver modules.
 */
object ValidationUtils {
    private const val MAX_NAME_LENGTH = 30
    private const val MIN_PASSWORD_LENGTH = 6

    fun validateEmail(email: String) {
        val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        require(emailRegex.matches(email)) { "Valid email address required" }
    }

    fun validatePhone(phone: String?) {
        if (phone == null) return
        val phoneRegex = Regex("^\\+63\\d{10}$")
        require(phoneRegex.matches(phone)) { "Phone must start with +63 followed by 10 digits" }
    }

    fun validateName(
        name: String,
        label: String,
    ) {
        require(name.isNotBlank()) { "$label cannot be blank" }
        require(name.length <= MAX_NAME_LENGTH) { "$label cannot exceed $MAX_NAME_LENGTH characters" }
    }

    fun validatePassword(password: String) {
        val specialChars = "!@#$%^&*()_+-=[]{}|;:,.<>?"
        val usedSpecials = mutableSetOf<Char>()
        var duplicateSpecial = false
        var hasSpecial = false

        password.forEach { char ->
            if (char in specialChars) {
                hasSpecial = true
                if (char in usedSpecials) {
                    duplicateSpecial = true
                }
                usedSpecials.add(char)
            }
        }

        require(password.length >= MIN_PASSWORD_LENGTH) { "Password must be at least $MIN_PASSWORD_LENGTH characters" }
        require(password.any { it.isUpperCase() }) { "Password must contain a capital letter" }
        require(password.any { it.isLowerCase() }) { "Password must contain a small letter" }
        require(password.any { it.isDigit() }) { "Password must contain a digit" }
        require(hasSpecial) { "Password must contain a special character" }
        require(!duplicateSpecial) { "Password cannot have duplicate special characters" }
    }
}
