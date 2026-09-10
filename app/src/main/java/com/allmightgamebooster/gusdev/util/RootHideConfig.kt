package com.allmightgamebooster.gusdev.util

import java.io.File

object RootHideConfig {

    private const val HIDDEN_PACKAGES_FILE = "/data/local/tmp/amgb_root_hide.txt"

    fun setHiddenPackages(packages: Set<String>) {
        try {
            val file = File(HIDDEN_PACKAGES_FILE)
            file.writeText(packages.joinToString("\n"))
            ShellExecutor.execRoot("chmod 644 $HIDDEN_PACKAGES_FILE")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getHiddenPackages(): Set<String> {
        return try {
            val file = File(HIDDEN_PACKAGES_FILE)
            if (file.exists()) {
                file.readText().lines().filter { it.isNotBlank() }.toSet()
            } else {
                emptySet()
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun addPackage(pkg: String) {
        val current = getHiddenPackages().toMutableSet()
        current.add(pkg)
        setHiddenPackages(current)
    }

    fun removePackage(pkg: String) {
        val current = getHiddenPackages().toMutableSet()
        current.remove(pkg)
        setHiddenPackages(current)
    }

    fun isHidden(pkg: String): Boolean = pkg in getHiddenPackages()
}
