package com.allmightgamebooster.gusdev.util

import java.io.File

object RootHideConfig {

    private const val HIDDEN_PACKAGES_FILE = "/data/local/tmp/amgb_root_hide.txt"

    fun setHiddenPackages(packages: Set<String>) {
        try {
            val file = File(HIDDEN_PACKAGES_FILE)
            file.parentFile?.mkdirs()
            file.writeText(packages.joinToString("\n"))
            ShellExecutor.execRoot("chmod 644 $HIDDEN_PACKAGES_FILE")
        } catch (_: Exception) {}
    }

    fun getHiddenPackages(): Set<String> {
        return try {
            val file = File(HIDDEN_PACKAGES_FILE)
            if (file.exists()) {
                file.readText().lines().filter { it.isNotBlank() }.toSet()
            } else emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun addPackage(pkg: String) {
        val current = getHiddenPackages().toMutableSet()
        current.add(pkg)
        setHiddenPackages(current)
        ShellExecutor.hideRootAdd(pkg)
    }

    fun removePackage(pkg: String) {
        val current = getHiddenPackages().toMutableSet()
        current.remove(pkg)
        setHiddenPackages(current)
        ShellExecutor.hideRootRemove(pkg)
    }

    fun isHidden(pkg: String): Boolean = pkg in getHiddenPackages()
}
