import nl.littlerobots.vcu.plugin.resolver.VersionSelectors

plugins { alias(libs.plugins.version.catalog.update) }

/**
 * Unattended update:
 * ```bash
 * gradle versionCatalogUpdate
 * ```
 *
 * Interactive update:
 * ```bash
 * gradle versionCatalogUpdate --interactive
 * # Check gradle/libs.versions.updates.toml
 * # To skip updating an entry in the TOML file it can be commented out or removed completely.
 * # It's also possible to edit the entry if that's desired.
 * gradle versionCatalogApplyUpdates
 * ```
 *
 * [Docs](https://github.com/littlerobots/version-catalog-update-plugin)
 */
versionCatalogUpdate { versionSelector(VersionSelectors.PREFER_STABLE) }

repositories {
  mavenCentral()
  google()
}
