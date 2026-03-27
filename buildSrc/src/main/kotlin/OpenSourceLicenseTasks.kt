import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Locale
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

private data class LicenseMetadata(
    val spdxId: String,
    val name: String,
    val url: String
)

private data class InventoryRecord(
    val coordinates: String,
    val resolvedVersion: String,
    val isDirectDependency: Boolean,
    val licenseSpdxId: String,
    val licenseName: String,
    val licenseUrl: String,
    val licenseText: String,
    val noticeText: String,
    val sourceKind: String,
    val distributionScope: String,
    val status: String
)

private data class GroupRule(
    val key: String,
    val displayName: String,
    val matchers: List<String>
)

private data class NoticeTextEntry(
    val label: String,
    val text: String
)

private val openSourceGroupRules = listOf(
    GroupRule(
        key = "androidx-core-activity-lifecycle",
        displayName = "AndroidX Core / Activity / Lifecycle",
        matchers = listOf("androidx.core:", "androidx.activity:", "androidx.lifecycle:")
    ),
    GroupRule(
        key = "androidx-navigation",
        displayName = "AndroidX Navigation",
        matchers = listOf("androidx.navigation:")
    ),
    GroupRule(
        key = "androidx-room",
        displayName = "AndroidX Room",
        matchers = listOf("androidx.room:")
    ),
    GroupRule(
        key = "androidx-datastore",
        displayName = "AndroidX DataStore",
        matchers = listOf("androidx.datastore:")
    ),
    GroupRule(
        key = "androidx-workmanager",
        displayName = "AndroidX WorkManager",
        matchers = listOf("androidx.work:")
    ),
    GroupRule(
        key = "androidx-glance-profileinstaller-metrics",
        displayName = "AndroidX Glance / Profile Installer / Metrics",
        matchers = listOf("androidx.glance:", "androidx.profileinstaller:", "androidx.metrics:")
    ),
    GroupRule(
        key = "jetpack-compose",
        displayName = "Jetpack Compose UI / Foundation / Runtime / Material3 / Material Icons",
        matchers = listOf("androidx.compose:")
    ),
    GroupRule(
        key = "material-components",
        displayName = "Material Components for Android",
        matchers = listOf("com.google.android.material:")
    ),
    GroupRule(
        key = "kotlin-standard-library",
        displayName = "Kotlin Standard Library / Kotlin Reflect",
        matchers = listOf("org.jetbrains.kotlin:")
    ),
    GroupRule(
        key = "kotlin-coroutines",
        displayName = "Kotlin Coroutines",
        matchers = listOf("org.jetbrains.kotlinx:kotlinx-coroutines")
    ),
    GroupRule(
        key = "okhttp-okio",
        displayName = "OkHttp / Okio",
        matchers = listOf("com.squareup.okhttp3:", "com.squareup.okio:")
    ),
    GroupRule(
        key = "gson",
        displayName = "Gson",
        matchers = listOf("com.google.code.gson:")
    ),
    GroupRule(
        key = "coil-compose",
        displayName = "Coil Compose",
        matchers = listOf("io.coil-kt:")
    ),
    GroupRule(
        key = "accompanist-pager",
        displayName = "Accompanist Pager",
        matchers = listOf("com.google.accompanist:")
    ),
    GroupRule(
        key = "snapper",
        displayName = "Snapper",
        matchers = listOf("dev.chrisbanes.snapper:")
    ),
    GroupRule(
        key = "jsoup",
        displayName = "jsoup",
        matchers = listOf("org.jsoup:")
    )
)

@DisableCachingByDefault(because = "Generates open source inventory from resolved artifacts.")
abstract class GenerateOpenSourceInventoryTask : DefaultTask() {
    @get:Input
    abstract val configurationName: Property<String>

    @get:Classpath
    abstract val runtimeArtifacts: ConfigurableFileCollection

    @get:Input
    abstract val directDependencyCoordinates: ListProperty<String>

    @get:Input
    abstract val gradleCacheDirPath: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val cacheDir = File(gradleCacheDirPath.get())
        val directDependencies = directDependencyCoordinates.get().toSet()
        val inventoryRecords = runtimeArtifacts.files
            .asSequence()
            .filter(File::isFile)
            .filter { artifactFile -> artifactFile.extension.lowercase(Locale.US) in setOf("aar", "jar") }
            .mapNotNull { artifactFile ->
                val component = parseModuleFromArtifactPath(
                    artifactFile = artifactFile,
                    cacheDir = cacheDir
                ) ?: return@mapNotNull null
                val coordinateKey = "${component.group}:${component.module}"
                val pomMetadata = findPomFile(
                    cacheDir = cacheDir,
                    group = component.group,
                    module = component.module,
                    version = component.version
                )?.let(::parsePomLicenses).orEmpty()
                val packagedTexts = extractPackagedTexts(artifactFile)
                val selectedLicense = selectLicenseMetadata(pomMetadata)
                    ?: inferLicenseMetadataFromTexts(
                        coordinates = coordinateKey,
                        licenseText = packagedTexts.first,
                        noticeText = packagedTexts.second
                    )
                val fallbackLicenseText = if (packagedTexts.first.isBlank()) {
                    fallbackLicenseText(
                        coordinates = coordinateKey,
                        licenseMetadata = selectedLicense
                    )
                } else {
                    ""
                }

                val sourceKind = when {
                    packagedTexts.first.isNotBlank() || packagedTexts.second.isNotBlank() -> "packaged_text"
                    fallbackLicenseText.isNotBlank() -> "fallback"
                    else -> "metadata"
                }

                val status = resolveInventoryStatus(
                    licenseMetadata = pomMetadata,
                    selectedLicense = selectedLicense,
                    packagedLicenseText = packagedTexts.first,
                    packagedNoticeText = packagedTexts.second,
                    fallbackLicenseText = fallbackLicenseText
                )

                InventoryRecord(
                    coordinates = coordinateKey,
                    resolvedVersion = component.version,
                    isDirectDependency = coordinateKey in directDependencies,
                    licenseSpdxId = selectedLicense?.spdxId.orEmpty(),
                    licenseName = selectedLicense?.name.orEmpty(),
                    licenseUrl = selectedLicense?.url.orEmpty(),
                    licenseText = packagedTexts.first.ifBlank { fallbackLicenseText },
                    noticeText = packagedTexts.second,
                    sourceKind = sourceKind,
                    distributionScope = "runtime_app",
                    status = status
                )
            }
            .distinctBy { "${it.coordinates}:${it.resolvedVersion}" }
            .sortedWith(compareBy<InventoryRecord>({ it.coordinates }, { it.resolvedVersion }))
            .toList()

        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(
            JsonOutput.prettyPrint(
                JsonOutput.toJson(
                    mapOf(
                        "schemaVersion" to 1,
                        "generatedAt" to Instant.now().toString(),
                        "configurationName" to configurationName.get(),
                        "artifacts" to inventoryRecords.map { record ->
                            mapOf(
                                "coordinates" to record.coordinates,
                                "resolvedVersion" to record.resolvedVersion,
                                "isDirectDependency" to record.isDirectDependency,
                                "licenseSpdxId" to record.licenseSpdxId,
                                "licenseName" to record.licenseName,
                                "licenseUrl" to record.licenseUrl,
                                "licenseText" to record.licenseText,
                                "noticeText" to record.noticeText,
                                "sourceKind" to record.sourceKind,
                                "distributionScope" to record.distributionScope,
                                "status" to record.status
                            )
                        }
                    )
                )
            ) + "\n"
        )
    }
}

@DisableCachingByDefault(because = "Generates grouped notices and bundled open source assets.")
abstract class GenerateOpenSourceNoticesTask : DefaultTask() {

    @get:InputFile
    abstract val inventoryFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val inventory = readInventory(inventoryFile.get().asFile)
        val outputDir = outputDirectory.get().asFile
        outputDir.mkdirs()

        val grouped = inventory
            .groupBy { resolveGroup(it.coordinates) }
            .toSortedMap(compareBy<Pair<String, String>>({ it.second }, { it.first }))

        val groups = grouped.map { (groupIdentity, items) ->
            val sortedItems = items.sortedWith(compareBy<InventoryRecord>({ it.coordinates }, { it.resolvedVersion }))
            val licenseNames = sortedItems.mapNotNull { it.licenseName.ifBlank { null } }.distinct()
            val licenseSpdxIds = sortedItems.mapNotNull { it.licenseSpdxId.ifBlank { null } }.distinct()
            val licenseUrls = sortedItems.mapNotNull { it.licenseUrl.ifBlank { null } }.distinct()
            val artifactCoordinates = sortedItems.map { "${it.coordinates}:${it.resolvedVersion}" }
            val licenseText = joinNoticeTexts(
                sortedItems.mapNotNull { item ->
                    item.licenseText
                        .takeIf(String::isNotBlank)
                        ?.let { NoticeTextEntry(label = "${item.coordinates}:${item.resolvedVersion}", text = it) }
                }
            )
            val noticeText = joinNoticeTexts(
                sortedItems.mapNotNull { item ->
                    item.noticeText
                        .takeIf(String::isNotBlank)
                        ?.let { NoticeTextEntry(label = "${item.coordinates}:${item.resolvedVersion}", text = it) }
                }
            )
            val status = resolveGroupStatus(sortedItems.map { it.status })
            val detailText = buildDetailText(
                displayName = groupIdentity.second,
                artifactCoordinates = artifactCoordinates,
                licenseNames = licenseNames,
                licenseUrls = licenseUrls,
                licenseText = licenseText,
                noticeText = noticeText,
                status = status
            )
            mapOf(
                "id" to groupIdentity.first,
                "displayName" to groupIdentity.second,
                "artifactCount" to artifactCoordinates.size,
                "artifactCoordinates" to artifactCoordinates,
                "licenseNames" to licenseNames,
                "licenseSpdxIds" to licenseSpdxIds,
                "licenseUrls" to licenseUrls,
                "licenseText" to licenseText,
                "noticeText" to noticeText,
                "detailText" to detailText,
                "status" to status
            )
        }

        val licenseCounts = inventory
            .groupingBy { it.licenseName.ifBlank { "Unknown" } }
            .eachCount()
            .toSortedMap()

        val statusCounts = inventory
            .groupingBy { it.status }
            .eachCount()
            .toSortedMap()

        val noticesJsonFile = outputDir.resolve("open_source_notices.json")
        noticesJsonFile.writeText(
            JsonOutput.prettyPrint(
                JsonOutput.toJson(
                    mapOf(
                        "schemaVersion" to 1,
                        "generatedAt" to Instant.now().toString(),
                        "summary" to mapOf(
                            "totalGroups" to groups.size,
                            "totalArtifacts" to inventory.size,
                            "licenseCounts" to licenseCounts,
                            "statusCounts" to statusCounts
                        ),
                        "groups" to groups
                    )
                )
            ) + "\n"
        )

        val bundledInventoryFile = outputDir.resolve("open_source_inventory.json")
        val sourceInventoryFile = inventoryFile.get().asFile
        if (sourceInventoryFile.absoluteFile != bundledInventoryFile.absoluteFile) {
            sourceInventoryFile.copyTo(bundledInventoryFile, overwrite = true)
        }

        outputDir.resolve("THIRD_PARTY_NOTICES.txt").writeText(
            buildThirdPartyNotices(groups),
            StandardCharsets.UTF_8
        )
    }

    private fun readInventory(file: File): List<InventoryRecord> {
        val parsed = JsonSlurper().parse(file) as? Map<*, *> ?: return emptyList()
        val artifacts = parsed["artifacts"] as? List<*> ?: return emptyList()
        return artifacts.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            InventoryRecord(
                coordinates = map["coordinates"]?.toString().orEmpty(),
                resolvedVersion = map["resolvedVersion"]?.toString().orEmpty(),
                isDirectDependency = map["isDirectDependency"] as? Boolean ?: false,
                licenseSpdxId = map["licenseSpdxId"]?.toString().orEmpty(),
                licenseName = map["licenseName"]?.toString().orEmpty(),
                licenseUrl = map["licenseUrl"]?.toString().orEmpty(),
                licenseText = map["licenseText"]?.toString().orEmpty(),
                noticeText = map["noticeText"]?.toString().orEmpty(),
                sourceKind = map["sourceKind"]?.toString().orEmpty(),
                distributionScope = map["distributionScope"]?.toString().orEmpty(),
                status = map["status"]?.toString().orEmpty()
            )
        }
    }
}

private fun parsePomLicenses(pomFile: File): List<LicenseMetadata> {
    if (!pomFile.exists()) return emptyList()
    val documentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }
    val document = documentBuilderFactory.newDocumentBuilder().parse(pomFile)
    val licenses = document.getElementsByTagName("license")
    return buildList {
        for (index in 0 until licenses.length) {
            val node = licenses.item(index) as? org.w3c.dom.Element ?: continue
            val name = node.childText("name")
            val url = node.childText("url")
            add(
                LicenseMetadata(
                    spdxId = normalizeSpdxId(name = name, url = url),
                    name = name,
                    url = url
                )
            )
        }
    }.filter { it.name.isNotBlank() || it.url.isNotBlank() }
}

private data class CachedModuleCoordinates(
    val group: String,
    val module: String,
    val version: String
)

private fun parseModuleFromArtifactPath(
    artifactFile: File,
    cacheDir: File
): CachedModuleCoordinates? {
    val normalizedCachePath = cacheDir.absoluteFile.normalize().path
    val normalizedArtifactPath = artifactFile.absoluteFile.normalize().path
    if (!normalizedArtifactPath.startsWith(normalizedCachePath)) return null
    val relativePath = normalizedArtifactPath.removePrefix("$normalizedCachePath${File.separator}")
    val segments = relativePath.split(File.separatorChar)
    if (segments.size < 5) return null
    val version = segments[segments.size - 3]
    val module = segments[segments.size - 4]
    val groupPathSegments = segments.dropLast(4)
    if (groupPathSegments.isEmpty()) return null
    return CachedModuleCoordinates(
        group = groupPathSegments.joinToString("."),
        module = module,
        version = version
    )
}

private fun findPomFile(
    cacheDir: File,
    group: String,
    module: String,
    version: String
): File? {
    val pomRoot = cacheDir
        .resolve(group.replace('.', File.separatorChar))
        .resolve(module)
        .resolve(version)
    if (!pomRoot.exists()) return null
    return pomRoot.walkTopDown()
        .firstOrNull { candidate -> candidate.isFile && candidate.extension.equals("pom", ignoreCase = true) }
}

private fun org.w3c.dom.Element.childText(tagName: String): String {
    val child = getElementsByTagName(tagName).item(0) as? org.w3c.dom.Element ?: return ""
    return child.textContent?.trim().orEmpty()
}

private fun normalizeSpdxId(name: String, url: String): String {
    val normalizedName = name.lowercase(Locale.US)
    val normalizedUrl = url.lowercase(Locale.US)
    return when {
        "apache" in normalizedName && "2.0" in normalizedName -> "Apache-2.0"
        "apache.org/licenses/license-2.0" in normalizedUrl -> "Apache-2.0"
        "apache.org/licenses/license-2.0.txt" in normalizedUrl -> "Apache-2.0"
        normalizedName.contains("mit") -> "MIT"
        normalizedUrl.contains("opensource.org/license/mit") -> "MIT"
        normalizedUrl.contains("jsoup.org/license") -> "MIT"
        normalizedName.contains("eclipse public license") && normalizedName.contains("1.0") -> "EPL-1.0"
        normalizedUrl.contains("epl-v10") -> "EPL-1.0"
        else -> ""
    }
}

private fun selectLicenseMetadata(licenses: List<LicenseMetadata>): LicenseMetadata? {
    if (licenses.isEmpty()) return null
    return licenses.firstOrNull { it.spdxId.isNotBlank() } ?: licenses.first()
}

private fun resolveInventoryStatus(
    licenseMetadata: List<LicenseMetadata>,
    selectedLicense: LicenseMetadata?,
    packagedLicenseText: String,
    packagedNoticeText: String,
    fallbackLicenseText: String
): String {
    if (selectedLicense == null) return "missing_license"
    val uniqueLicenses = licenseMetadata
        .map { (it.spdxId.ifBlank { it.name }).ifBlank { it.url } }
        .filter(String::isNotBlank)
        .distinct()
    if (uniqueLicenses.size > 1) return "review_needed"
    return if (
        packagedLicenseText.isBlank() &&
        packagedNoticeText.isBlank() &&
        fallbackLicenseText.isBlank()
    ) {
        "missing_text"
    } else {
        "ok"
    }
}

private fun extractPackagedTexts(file: File): Pair<String, String> {
    if (file.extension.lowercase(Locale.US) !in setOf("aar", "jar")) return "" to ""
    val licenseEntries = linkedMapOf<String, String>()
    val noticeEntries = linkedMapOf<String, String>()
    runCatching {
        ZipFile(file).use { zipFile ->
            zipFile.entries().asSequence().forEach { entry ->
                if (entry.isDirectory) return@forEach
                val normalizedName = entry.name.lowercase(Locale.US)
                if (!isCandidateTextFile(normalizedName)) return@forEach
                val text = zipFile.getInputStream(entry)
                    .bufferedReader(StandardCharsets.UTF_8)
                    .use { it.readText().trim() }
                if (text.isBlank()) return@forEach
                when {
                    isNoticeEntry(normalizedName) -> noticeEntries.putIfAbsent(text, entry.name)
                    isLicenseEntry(normalizedName) -> licenseEntries.putIfAbsent(text, entry.name)
                }
            }
        }
    }
    val licenseText = joinPackagedTexts(
        licenseEntries.entries.map { NoticeTextEntry(label = it.value, text = it.key) }
    )
    val noticeText = joinPackagedTexts(
        noticeEntries.entries.map { NoticeTextEntry(label = it.value, text = it.key) }
    )
    return licenseText to noticeText
}

private fun isCandidateTextFile(entryName: String): Boolean {
    return isLicenseEntry(entryName) || isNoticeEntry(entryName)
}

private fun isLicenseEntry(entryName: String): Boolean {
    return Regex("""(^|/)(license|copying)([^/]*)(\.[a-z0-9]+)?$""").containsMatchIn(entryName)
}

private fun isNoticeEntry(entryName: String): Boolean {
    return Regex("""(^|/)(notice)([^/]*)(\.[a-z0-9]+)?$""").containsMatchIn(entryName)
}

private fun joinPackagedTexts(entries: List<NoticeTextEntry>): String {
    val distinctEntries = entries.distinctBy { it.text.trim() }
    return when (distinctEntries.size) {
        0 -> ""
        1 -> distinctEntries.single().text
        else -> distinctEntries.joinToString(separator = "\n\n-----\n\n") { entry ->
            "Source: ${entry.label}\n\n${entry.text}"
        }
    }
}

private fun fallbackLicenseText(
    coordinates: String,
    licenseMetadata: LicenseMetadata?
): String {
    val effectiveLicense = licenseMetadata ?: inferLicenseMetadataByCoordinates(coordinates)
    return when (effectiveLicense?.spdxId) {
        "Apache-2.0" -> apache20LicenseText
        "EPL-1.0" -> eclipsePublicLicense10Text
        else -> ""
    }
}

private fun inferLicenseMetadataFromTexts(
    coordinates: String,
    licenseText: String,
    noticeText: String
): LicenseMetadata? {
    val normalizedText = buildString {
        appendLine(licenseText)
        appendLine(noticeText)
    }.lowercase(Locale.US)
    return when {
        normalizedText.contains("apache license") && normalizedText.contains("version 2.0") ->
            LicenseMetadata(
                spdxId = "Apache-2.0",
                name = "Apache License 2.0",
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            )

        normalizedText.contains("permission is hereby granted, free of charge") ->
            LicenseMetadata(
                spdxId = "MIT",
                name = "MIT License",
                url = "https://opensource.org/license/mit"
            )

        normalizedText.contains("eclipse public license") ->
            LicenseMetadata(
                spdxId = "EPL-1.0",
                name = "Eclipse Public License 1.0",
                url = "https://www.eclipse.org/org/documents/epl-v10.html"
            )

        else -> inferLicenseMetadataByCoordinates(coordinates)
    }
}

private fun inferLicenseMetadataByCoordinates(coordinates: String): LicenseMetadata? {
    return when {
        coordinates.startsWith("androidx.") ||
            coordinates.startsWith("com.google.android.material:") ||
            coordinates.startsWith("com.google.code.gson:") ||
            coordinates.startsWith("com.google.errorprone:error_prone_annotations") ||
            coordinates.startsWith("com.google.guava:listenablefuture") ||
            coordinates.startsWith("com.google.accompanist:") ||
            coordinates.startsWith("com.squareup.okhttp3:") ||
            coordinates.startsWith("com.squareup.okio:") ||
            coordinates.startsWith("io.github.aakira:napier") ||
            coordinates.startsWith("io.coil-kt:") ||
            coordinates.startsWith("org.jetbrains:annotations") ||
            coordinates.startsWith("org.jetbrains.kotlin:") ||
            coordinates.startsWith("org.jetbrains.kotlinx:") ||
            coordinates.startsWith("org.jspecify:jspecify") ||
            coordinates.startsWith("dev.chrisbanes.snapper:") -> LicenseMetadata(
            spdxId = "Apache-2.0",
            name = "Apache License 2.0",
            url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
        )

        coordinates.startsWith("org.jsoup:") -> LicenseMetadata(
            spdxId = "MIT",
            name = "MIT License",
            url = "https://jsoup.org/license"
        )

        coordinates == "junit:junit" -> LicenseMetadata(
            spdxId = "EPL-1.0",
            name = "Eclipse Public License 1.0",
            url = "https://www.eclipse.org/org/documents/epl-v10.html"
        )

        else -> null
    }
}

private fun resolveGroup(coordinates: String): Pair<String, String> {
    val rule = openSourceGroupRules.firstOrNull { groupRule ->
        groupRule.matchers.any { matcher -> coordinates.startsWith(matcher) }
    }
    if (rule != null) return rule.key to rule.displayName

    val group = coordinates.substringBefore(':')
    val displayName = group
        .split('.')
        .filter(String::isNotBlank)
        .joinToString(" ") { segment ->
            segment.replaceFirstChar { it.uppercase() }
        }
        .ifBlank { coordinates }
    return slugify(displayName) to displayName
}

private fun slugify(value: String): String {
    return value.lowercase(Locale.US)
        .replace(Regex("""[^a-z0-9]+"""), "-")
        .trim('-')
        .ifBlank { "open-source-group" }
}

private fun joinNoticeTexts(entries: List<NoticeTextEntry>): String {
    val distinctEntries = entries.distinctBy { it.text.trim() }
    return when (distinctEntries.size) {
        0 -> ""
        1 -> distinctEntries.single().text
        else -> distinctEntries.joinToString(separator = "\n\n===== \n\n") { entry ->
            "${entry.label}\n\n${entry.text}"
        }
    }
}

private fun resolveGroupStatus(statuses: List<String>): String {
    return when {
        "missing_license" in statuses -> "missing_license"
        "review_needed" in statuses -> "review_needed"
        "missing_text" in statuses -> "missing_text"
        else -> "ok"
    }
}

private fun buildDetailText(
    displayName: String,
    artifactCoordinates: List<String>,
    licenseNames: List<String>,
    licenseUrls: List<String>,
    licenseText: String,
    noticeText: String,
    status: String
): String {
    return buildString {
        appendLine(displayName)
        appendLine("Status: $status")
        appendLine()
        appendLine("Artifacts:")
        artifactCoordinates.forEach { artifact ->
            appendLine("- $artifact")
        }
        if (licenseNames.isNotEmpty()) {
            appendLine()
            appendLine("Licenses:")
            licenseNames.forEach { licenseName ->
                appendLine("- $licenseName")
            }
        }
        if (licenseUrls.isNotEmpty()) {
            appendLine()
            appendLine("License URLs:")
            licenseUrls.forEach { licenseUrl ->
                appendLine("- $licenseUrl")
            }
        }
        if (licenseText.isNotBlank()) {
            appendLine()
            appendLine("License Text")
            appendLine("------------")
            appendLine(licenseText)
        }
        if (noticeText.isNotBlank()) {
            appendLine()
            appendLine("NOTICE")
            appendLine("------")
            appendLine(noticeText)
        }
    }.trim()
}

private fun buildThirdPartyNotices(groups: List<Map<String, Any>>): String {
    return buildString {
        appendLine("LLClass Third-Party Notices")
        appendLine("Generated at: ${Instant.now()}")
        appendLine()
        groups.forEachIndexed { index, group ->
            appendLine("## ${group["displayName"]}")
            appendLine("Status: ${group["status"]}")
            appendLine()
            appendLine("Artifacts:")
            @Suppress("UNCHECKED_CAST")
            val artifactCoordinates = group["artifactCoordinates"] as? List<String> ?: emptyList()
            artifactCoordinates.forEach { artifact ->
                appendLine("- $artifact")
            }
            @Suppress("UNCHECKED_CAST")
            val licenseNames = group["licenseNames"] as? List<String> ?: emptyList()
            if (licenseNames.isNotEmpty()) {
                appendLine()
                appendLine("Licenses:")
                licenseNames.forEach { licenseName ->
                    appendLine("- $licenseName")
                }
            }
            @Suppress("UNCHECKED_CAST")
            val licenseUrls = group["licenseUrls"] as? List<String> ?: emptyList()
            if (licenseUrls.isNotEmpty()) {
                appendLine()
                appendLine("License URLs:")
                licenseUrls.forEach { licenseUrl ->
                    appendLine("- $licenseUrl")
                }
            }
            val licenseText = group["licenseText"]?.toString().orEmpty()
            if (licenseText.isNotBlank()) {
                appendLine()
                appendLine("License Text")
                appendLine("------------")
                appendLine(licenseText)
            }
            val noticeText = group["noticeText"]?.toString().orEmpty()
            if (noticeText.isNotBlank()) {
                appendLine()
                appendLine("NOTICE")
                appendLine("------")
                appendLine(noticeText)
            }
            if (index != groups.lastIndex) {
                appendLine()
                appendLine()
            }
        }
    }
}

private val apache20LicenseText = """
Apache License
Version 2.0, January 2004
http://www.apache.org/licenses/

TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

1. Definitions.

"License" shall mean the terms and conditions for use, reproduction, and distribution as defined by Sections 1 through 9 of this document.

"Licensor" shall mean the copyright owner or entity authorized by the copyright owner that is granting the License.

"Legal Entity" shall mean the union of the acting entity and all other entities that control, are controlled by, or are under common control with that entity. For the purposes of this definition, "control" means (i) the power, direct or indirect, to cause the direction or management of such entity, whether by contract or otherwise, or (ii) ownership of fifty percent (50%) or more of the outstanding shares, or (iii) beneficial ownership of such entity.

"You" (or "Your") shall mean an individual or Legal Entity exercising permissions granted by this License.

"Source" form shall mean the preferred form for making modifications, including but not limited to software source code, documentation source, and configuration files.

"Object" form shall mean any form resulting from mechanical transformation or translation of a Source form, including but not limited to compiled object code, generated documentation, and conversions to other media types.

"Work" shall mean the work of authorship, whether in Source or Object form, made available under the License, as indicated by a copyright notice that is included in or attached to the work.

"Derivative Works" shall mean any work, whether in Source or Object form, that is based on (or derived from) the Work and for which the editorial revisions, annotations, elaborations, or other modifications represent, as a whole, an original work of authorship. For the purposes of this License, Derivative Works shall not include works that remain separable from, or merely link (or bind by name) to the interfaces of, the Work and Derivative Works thereof.

"Contribution" shall mean any work of authorship, including the original version of the Work and any modifications or additions to that Work or Derivative Works thereof, that is intentionally submitted to Licensor for inclusion in the Work by the copyright owner or by an individual or Legal Entity authorized to submit on behalf of the copyright owner.

"Contributor" shall mean Licensor and any individual or Legal Entity on behalf of whom a Contribution has been received by Licensor and subsequently incorporated within the Work.

2. Grant of Copyright License. Subject to the terms and conditions of this License, each Contributor hereby grants to You a perpetual, worldwide, non-exclusive, no-charge, royalty-free, irrevocable copyright license to reproduce, prepare Derivative Works of, publicly display, publicly perform, sublicense, and distribute the Work and such Derivative Works in Source or Object form.

3. Grant of Patent License. Subject to the terms and conditions of this License, each Contributor hereby grants to You a perpetual, worldwide, non-exclusive, no-charge, royalty-free, irrevocable patent license to make, have made, use, offer to sell, sell, import, and otherwise transfer the Work.

4. Redistribution. You may reproduce and distribute copies of the Work or Derivative Works thereof in any medium, with or without modifications, and in Source or Object form, provided that You meet the following conditions:

(a) You must give any other recipients of the Work or Derivative Works a copy of this License; and
(b) You must cause any modified files to carry prominent notices stating that You changed the files; and
(c) You must retain, in the Source form of any Derivative Works that You distribute, all copyright, patent, trademark, and attribution notices from the Source form of the Work; and
(d) If the Work includes a "NOTICE" text file as part of its distribution, then any Derivative Works that You distribute must include a readable copy of the attribution notices contained within such NOTICE file.

5. Submission of Contributions. Unless You explicitly state otherwise, any Contribution intentionally submitted for inclusion in the Work by You to the Licensor shall be under the terms and conditions of this License.

6. Trademarks. This License does not grant permission to use the trade names, trademarks, service marks, or product names of the Licensor.

7. Disclaimer of Warranty. Unless required by applicable law or agreed to in writing, Licensor provides the Work on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.

8. Limitation of Liability. In no event and under no legal theory shall any Contributor be liable to You for damages arising as a result of this License or out of the use or inability to use the Work.

9. Accepting Warranty or Additional Liability. While redistributing the Work or Derivative Works thereof, You may choose to offer support, warranty, indemnity, or other liability obligations and/or rights consistent with this License.
""".trimIndent()

private val eclipsePublicLicense10Text = """
Eclipse Public License - v 1.0

THE ACCOMPANYING PROGRAM IS PROVIDED UNDER THE TERMS OF THIS ECLIPSE PUBLIC LICENSE ("AGREEMENT"). ANY USE, REPRODUCTION OR DISTRIBUTION OF THE PROGRAM CONSTITUTES RECIPIENT'S ACCEPTANCE OF THIS AGREEMENT.

1. DEFINITIONS
"Contribution" means:
a) in the case of the initial Contributor, the initial code and documentation distributed under this Agreement, and
b) in the case of each subsequent Contributor:
i) changes to the Program, and
ii) additions to the Program;
where such changes and/or additions to the Program originate from and are distributed by that particular Contributor.

2. GRANT OF RIGHTS
a) Subject to the terms of this Agreement, each Contributor hereby grants Recipient a non-exclusive, worldwide, royalty-free copyright license to reproduce, prepare derivative works of, publicly display, publicly perform, distribute and sublicense the Contribution of such Contributor.
b) Subject to the terms of this Agreement, each Contributor hereby grants Recipient a non-exclusive, worldwide, royalty-free patent license under Licensed Patents to make, use, sell, offer to sell, import and otherwise transfer the Contribution.

3. REQUIREMENTS
A Contributor may choose to distribute the Program in object code form under its own license agreement, provided that:
a) it complies with the terms and conditions of this Agreement; and
b) its license agreement effectively disclaims on behalf of all Contributors all warranties and conditions, and excludes all liability of all Contributors.

4. COMMERCIAL DISTRIBUTION
Commercial distributors of software may accept certain responsibilities with respect to end users, business partners and the like.

5. NO WARRANTY
EXCEPT AS EXPRESSLY SET FORTH IN THIS AGREEMENT, THE PROGRAM IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.

6. DISCLAIMER OF LIABILITY
EXCEPT AS EXPRESSLY SET FORTH IN THIS AGREEMENT, NEITHER RECIPIENT NOR ANY CONTRIBUTORS SHALL HAVE ANY LIABILITY FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
""".trimIndent()
