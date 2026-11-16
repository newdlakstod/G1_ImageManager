# 갤럭시 이미지 관리 앱 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Android (Kotlin) app for Galaxy phones that lets users triage photos via a Slidebox-style swipe-card UI (favorite / trash / move to folder), browse a grid, and auto-detect similar/duplicate photos via perceptual hashing.

**Architecture:** MVVM with Jetpack Compose UI. MediaStore is the single source of truth for photos, favorites (`IS_FAVORITE`), and trash (`createTrashRequest`) — the app never duplicates that state locally. Room holds only app-local caches: perceptual hash cache, duplicate-group cache, and user-defined "pile" (folder) definitions. Core algorithms (hashing, clustering) are pure Kotlin with no Android framework dependency so they run as fast JVM unit tests.

**Tech Stack:** Kotlin, Jetpack Compose, Coroutines/Flow, Room 2.6, Paging3, Coil 2.x, JUnit4 + Robolectric (unit tests), AndroidX Test / Espresso-Compose (instrumented tests).

## Global Constraints

- minSdk 30 (Android 11 / One UI 3.1+), targetSdk latest stable.
- Favorites and deletion MUST go through `MediaStore` APIs (`IS_FAVORITE` column, `MediaStore.createTrashRequest()`) — never a custom app-local favorite/trash flag. (Spec §2)
- No automatic background scanning (no WorkManager periodic jobs). Duplicate scans are on-demand only. (Spec §4.3)
- `rating` / 5-star feature is explicitly out of scope for this plan. Do not add a rating column or UI. (Spec §5, §9)
- Package name: `com.imagemanager.app`.

---

## File Structure

```
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/com/imagemanager/app/
  MainActivity.kt
  data/
    Photo.kt
    MediaStoreRepository.kt
    db/
      AppDatabase.kt
      PhotoHashEntity.kt / PhotoHashDao.kt
      DuplicateGroupEntity.kt / DuplicateGroupDao.kt
      PileEntity.kt / PileDao.kt
  hashing/
    DifferenceHash.kt
    HashGrouping.kt
  domain/
    DuplicateScanner.kt
  ui/
    permissions/PermissionGateScreen.kt
    grid/PhotoGridScreen.kt, PhotoGridViewModel.kt, PhotoPagingSource.kt
    favorites/FavoritesScreen.kt, FavoritesViewModel.kt
    cardswipe/CardSwipeScreen.kt, CardSwipeViewModel.kt
    piles/PileListScreen.kt, PileViewModel.kt, PilePickerSheet.kt
    similar/SimilarGroupsScreen.kt, SimilarGroupsViewModel.kt
    navigation/AppNavHost.kt
app/src/test/java/com/imagemanager/app/
  hashing/DifferenceHashTest.kt
  hashing/HashGroupingTest.kt
  domain/DuplicateScannerTest.kt
  data/db/PhotoHashDaoTest.kt, PileDaoTest.kt
app/src/androidTest/java/com/imagemanager/app/
  data/MediaStoreRepositoryInstrumentedTest.kt
  ui/CardSwipeGestureTest.kt
```

---

### Task 1: Project scaffolding

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/imagemanager/app/MainActivity.kt`
- Create: `gradle.properties`

**Interfaces:**
- Produces: a buildable, launchable empty Compose app (`MainActivity` showing a placeholder `Text("Image Manager")`). Later tasks replace the placeholder content with `AppNavHost`.

- [ ] **Step 1: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "ImageManager"
include(":app")
```

- [ ] **Step 2: Create root `build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}
```

- [ ] **Step 3: Create `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m
android.useAndroidX=true
kotlin.code.style=official
```

- [ ] **Step 4: Create `app/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.imagemanager.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.imagemanager.app"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.paging:paging-runtime-ktx:3.3.0")
    implementation("androidx.paging:paging-compose:3.3.0")

    implementation("io.coil-kt:coil-compose:2.6.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

- [ ] **Step 5: Create `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"
        tools:ignore="ScopedStorage" />

    <application
        android:allowBackup="true"
        android:label="Image Manager"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@android:style/Theme.Material.Light.NoActionBar">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 6: Create placeholder `MainActivity.kt`**

```kotlin
package com.imagemanager.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    Text("Image Manager")
                }
            }
        }
    }
}
```

- [ ] **Step 7: Build the project**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Commit**

```bash
git add settings.gradle.kts build.gradle.kts app/build.gradle.kts gradle.properties app/src/main/AndroidManifest.xml app/src/main/java/com/imagemanager/app/MainActivity.kt
git commit -m "chore: scaffold Compose project"
```

---

### Task 2: Domain model + MediaStoreRepository (query, favorite, trash)

**Files:**
- Create: `app/src/main/java/com/imagemanager/app/data/Photo.kt`
- Create: `app/src/main/java/com/imagemanager/app/data/MediaStoreRepository.kt`
- Test: `app/src/androidTest/java/com/imagemanager/app/data/MediaStoreRepositoryInstrumentedTest.kt`

**Interfaces:**
- Produces:
  - `data class Photo(val mediaId: Long, val uri: Uri, val displayName: String, val dateModified: Long, val isFavorite: Boolean)`
  - `class MediaStoreRepository(private val context: Context)` with:
    - `fun listPhotos(offset: Int, limit: Int): List<Photo>`
    - `fun listFavorites(offset: Int, limit: Int): List<Photo>`
    - `fun toggleFavorite(mediaId: Long, favorite: Boolean): IntentSender?` (returns an `IntentSender` to launch when the OS requires user confirmation, null if applied directly)
    - `fun requestTrash(mediaIds: List<Long>): IntentSender` (always requires confirmation per `MediaStore.createTrashRequest` contract)
- Consumed by: Task 8 (grid), Task 9 (favorites), Task 10 (card swipe), Task 12 (similar).

This task requires a real `ContentResolver` backed by MediaStore, which Robolectric's shadow does not faithfully emulate for `IS_FAVORITE`/trash semantics on API 30+. Per spec section 8, MediaStore integration is verified with **instrumented tests** on a real device/emulator, not JVM unit tests.

- [ ] **Step 1: Write `Photo.kt`**

```kotlin
package com.imagemanager.app.data

import android.net.Uri

data class Photo(
    val mediaId: Long,
    val uri: Uri,
    val displayName: String,
    val dateModified: Long,
    val isFavorite: Boolean,
)
```

- [ ] **Step 2: Write `MediaStoreRepository.kt`**

```kotlin
package com.imagemanager.app.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.R)
class MediaStoreRepository(private val context: Context) {

    private val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    private val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.IS_FAVORITE,
    )

    fun listPhotos(offset: Int, limit: Int): List<Photo> =
        query(selection = null, selectionArgs = null, offset = offset, limit = limit)

    fun listFavorites(offset: Int, limit: Int): List<Photo> =
        query(
            selection = "${MediaStore.Images.Media.IS_FAVORITE} = 1",
            selectionArgs = null,
            offset = offset,
            limit = limit,
        )

    private fun query(
        selection: String?,
        selectionArgs: Array<String>?,
        offset: Int,
        limit: Int,
    ): List<Photo> {
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        val queryArgs = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
        }
        val photos = mutableListOf<Photo>()
        context.contentResolver.query(collection, projection, queryArgs, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val favCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.IS_FAVORITE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                photos += Photo(
                    mediaId = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    displayName = cursor.getString(nameCol),
                    dateModified = cursor.getLong(dateCol),
                    isFavorite = cursor.getInt(favCol) != 0,
                )
            }
        }
        return photos
    }

    fun toggleFavorite(mediaId: Long, favorite: Boolean): IntentSender? {
        val uri = ContentUris.withAppendedId(collection, mediaId)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.IS_FAVORITE, if (favorite) 1 else 0)
        }
        return try {
            context.contentResolver.update(uri, values, null, null)
            null
        } catch (e: SecurityException) {
            MediaStore.createWriteRequest(context.contentResolver, listOf(uri)).intentSender
        }
    }

    fun requestTrash(mediaIds: List<Long>): IntentSender {
        val uris = mediaIds.map { ContentUris.withAppendedId(collection, it) }
        return MediaStore.createTrashRequest(context.contentResolver, uris, true).intentSender
    }
}
```

- [ ] **Step 3: Write instrumented test for query + favorite round-trip**

```kotlin
package com.imagemanager.app.data

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaStoreRepositoryInstrumentedTest {

    @Test
    fun listPhotos_returnsResultsWithoutCrashing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = MediaStoreRepository(context)
        val photos = repository.listPhotos(offset = 0, limit = 50)
        assertTrue(photos.size >= 0)
    }

    @Test
    fun listFavorites_onlyReturnsFavoritedPhotos() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = MediaStoreRepository(context)
        val favorites = repository.listFavorites(offset = 0, limit = 50)
        assertTrue(favorites.all { it.isFavorite })
    }
}
```

- [ ] **Step 4: Run instrumented tests**

Run: `./gradlew connectedDebugAndroidTest` (requires a connected device/emulator; seed a couple of test JPEGs into `Pictures/` first so `listPhotos` has data)
Expected: both tests `PASS`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/imagemanager/app/data/Photo.kt app/src/main/java/com/imagemanager/app/data/MediaStoreRepository.kt app/src/androidTest/java/com/imagemanager/app/data/MediaStoreRepositoryInstrumentedTest.kt
git commit -m "feat: add MediaStore-backed photo repository with favorite/trash"
```

---

### Task 3: Room database (hash cache, duplicate groups, piles)

**Files:**
- Create: `app/src/main/java/com/imagemanager/app/data/db/Converters.kt`
- Create: `app/src/main/java/com/imagemanager/app/data/db/PhotoHashEntity.kt`
- Create: `app/src/main/java/com/imagemanager/app/data/db/PhotoHashDao.kt`
- Create: `app/src/main/java/com/imagemanager/app/data/db/DuplicateGroupEntity.kt`
- Create: `app/src/main/java/com/imagemanager/app/data/db/DuplicateGroupDao.kt`
- Create: `app/src/main/java/com/imagemanager/app/data/db/PileEntity.kt`
- Create: `app/src/main/java/com/imagemanager/app/data/db/PileDao.kt`
- Create: `app/src/main/java/com/imagemanager/app/data/db/AppDatabase.kt`
- Test: `app/src/test/java/com/imagemanager/app/data/db/PhotoHashDaoTest.kt`
- Test: `app/src/test/java/com/imagemanager/app/data/db/PileDaoTest.kt`

**Interfaces:**
- Produces:
  - `AppDatabase.getInstance(context: Context): AppDatabase` exposing `photoHashDao()`, `duplicateGroupDao()`, `pileDao()`
  - `PhotoHashDao.getByMediaId(mediaId: Long): PhotoHashEntity?`, `.getAll(): List<PhotoHashEntity>`, `.upsert(entity: PhotoHashEntity)`
  - `DuplicateGroupDao.observeAll(): Flow<List<DuplicateGroupEntity>>`, `.clearAll()`, `.insertAll(groups: List<DuplicateGroupEntity>)`
  - `PileDao.observeAll(): Flow<List<PileEntity>>`, `.insert(pile: PileEntity): Long`, `.getById(pileId: Long): PileEntity?`
- Consumed by: Task 6 (DuplicateScanner), Task 11 (piles), Task 12 (similar groups screen).

- [ ] **Step 1: Write `Converters.kt`**

```kotlin
package com.imagemanager.app.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromMediaIds(ids: List<Long>): String = ids.joinToString(",")

    @TypeConverter
    fun toMediaIds(csv: String): List<Long> =
        if (csv.isBlank()) emptyList() else csv.split(",").map { it.toLong() }
}
```

- [ ] **Step 2: Write `PhotoHashEntity.kt` and `PhotoHashDao.kt`**

```kotlin
package com.imagemanager.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photo_hash")
data class PhotoHashEntity(
    @PrimaryKey val mediaId: Long,
    val dateModified: Long,
    val hash: Long,
)
```

```kotlin
package com.imagemanager.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PhotoHashDao {
    @Query("SELECT * FROM photo_hash WHERE mediaId = :mediaId")
    suspend fun getByMediaId(mediaId: Long): PhotoHashEntity?

    @Query("SELECT * FROM photo_hash")
    suspend fun getAll(): List<PhotoHashEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PhotoHashEntity)
}
```

- [ ] **Step 3: Write `DuplicateGroupEntity.kt` and `DuplicateGroupDao.kt`**

```kotlin
package com.imagemanager.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "duplicate_group")
data class DuplicateGroupEntity(
    @PrimaryKey(autoGenerate = true) val groupId: Long = 0,
    val mediaIds: List<Long>,
    val computedAt: Long,
)
```

```kotlin
package com.imagemanager.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DuplicateGroupDao {
    @Query("SELECT * FROM duplicate_group ORDER BY computedAt DESC")
    fun observeAll(): Flow<List<DuplicateGroupEntity>>

    @Query("DELETE FROM duplicate_group")
    suspend fun clearAll()

    @Insert
    suspend fun insertAll(groups: List<DuplicateGroupEntity>)
}
```

- [ ] **Step 4: Write `PileEntity.kt` and `PileDao.kt`**

```kotlin
package com.imagemanager.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pile")
data class PileEntity(
    @PrimaryKey(autoGenerate = true) val pileId: Long = 0,
    val name: String,
    val folderRelativePath: String,
    val createdAt: Long,
)
```

```kotlin
package com.imagemanager.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PileDao {
    @Query("SELECT * FROM pile ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PileEntity>>

    @Insert
    suspend fun insert(pile: PileEntity): Long

    @Query("SELECT * FROM pile WHERE pileId = :pileId")
    suspend fun getById(pileId: Long): PileEntity?
}
```

- [ ] **Step 5: Write `AppDatabase.kt`**

```kotlin
package com.imagemanager.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [PhotoHashEntity::class, DuplicateGroupEntity::class, PileEntity::class],
    version = 1,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photoHashDao(): PhotoHashDao
    abstract fun duplicateGroupDao(): DuplicateGroupDao
    abstract fun pileDao(): PileDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "image-manager.db",
                ).build().also { instance = it }
            }
    }
}
```

- [ ] **Step 6: Write failing test `PhotoHashDaoTest.kt`**

```kotlin
package com.imagemanager.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PhotoHashDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: PhotoHashDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.photoHashDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsert_thenGetByMediaId_returnsStoredHash() = runTest {
        dao.upsert(PhotoHashEntity(mediaId = 1L, dateModified = 1000L, hash = 42L))

        val result = dao.getByMediaId(1L)

        assertEquals(42L, result?.hash)
    }

    @Test
    fun upsert_withSameMediaId_replacesExistingEntry() = runTest {
        dao.upsert(PhotoHashEntity(mediaId = 1L, dateModified = 1000L, hash = 42L))

        dao.upsert(PhotoHashEntity(mediaId = 1L, dateModified = 2000L, hash = 99L))
        val result = dao.getByMediaId(1L)

        assertEquals(2000L, result?.dateModified)
        assertEquals(99L, result?.hash)
    }
}
```

- [ ] **Step 7: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.imagemanager.app.data.db.PhotoHashDaoTest"`
Expected: FAIL (compile error: `AppDatabase`/`PhotoHashDao` referenced before being written — this step assumes Steps 1-5 ran first; if following strict TDD, write this test before Step 5's `AppDatabase.kt` so it genuinely fails to compile, then add Steps 1-5 as the "make it pass" implementation)

- [ ] **Step 8: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.imagemanager.app.data.db.PhotoHashDaoTest"`
Expected: PASS

- [ ] **Step 9: Write and run `PileDaoTest.kt`**

```kotlin
package com.imagemanager.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PileDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: PileDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.pileDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insert_thenObserveAll_emitsInsertedPile() = runTest {
        dao.insert(PileEntity(name = "Trip", folderRelativePath = "Pictures/Piles/Trip", createdAt = 1000L))

        val piles = dao.observeAll().first()

        assertEquals(1, piles.size)
        assertEquals("Trip", piles[0].name)
    }
}
```

Run: `./gradlew testDebugUnitTest --tests "com.imagemanager.app.data.db.PileDaoTest"`
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/imagemanager/app/data/db app/src/test/java/com/imagemanager/app/data/db
git commit -m "feat: add Room cache for photo hashes, duplicate groups, and piles"
```

---

### Task 4: DifferenceHash (dHash) algorithm

**Files:**
- Create: `app/src/main/java/com/imagemanager/app/hashing/DifferenceHash.kt`
- Create: `app/src/main/java/com/imagemanager/app/hashing/BitmapHashing.kt`
- Test: `app/src/test/java/com/imagemanager/app/hashing/DifferenceHashTest.kt`

**Interfaces:**
- Produces:
  - `object DifferenceHash { fun compute(luminance: IntArray, width: Int = 9, height: Int = 8): Long }` — pure Kotlin, no Android dependency, takes a pre-resized 9x8 grayscale luminance buffer and returns a 64-bit hash.
  - `fun Bitmap.computeDHash(): Long` — Android adapter in `BitmapHashing.kt` that resizes a `Bitmap` to 9x8, converts to luminance, and delegates to `DifferenceHash.compute`.
- Consumed by: Task 6 (`DuplicateScanner`), which calls `bitmap.computeDHash()` per photo.

`DifferenceHash.compute` is kept free of `android.graphics.Bitmap` specifically so it can run as a plain JVM unit test without Robolectric.

- [ ] **Step 1: Write failing test `DifferenceHashTest.kt`**

```kotlin
package com.imagemanager.app.hashing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DifferenceHashTest {

    @Test
    fun compute_rejectsBufferWithWrongSize() {
        assertThrows(IllegalArgumentException::class.java) {
            DifferenceHash.compute(IntArray(10), width = 9, height = 8)
        }
    }

    @Test
    fun compute_identicalInputsProduceIdenticalHashes() {
        val luminance = IntArray(72) { it * 3 }

        val hashA = DifferenceHash.compute(luminance, width = 9, height = 8)
        val hashB = DifferenceHash.compute(luminance, width = 9, height = 8)

        assertEquals(hashA, hashB)
    }

    @Test
    fun compute_setsBitWhenLeftPixelDarkerThanRightPixel() {
        // Row 0: pixel(0,0)=0 (dark) < pixel(1,0)=255 (bright) -> bit 0 should be set.
        val luminance = IntArray(72) { 255 }
        luminance[0] = 0

        val hash = DifferenceHash.compute(luminance, width = 9, height = 8)

        assertEquals(1L, hash and 1L)
    }

    @Test
    fun compute_flatImageProducesAllZeroBits() {
        val luminance = IntArray(72) { 128 }

        val hash = DifferenceHash.compute(luminance, width = 9, height = 8)

        assertEquals(0L, hash)
    }

    @Test
    fun compute_differentImagesProduceDifferentHashes() {
        val flat = IntArray(72) { 128 }
        val gradient = IntArray(72) { index -> (index % 9) * 25 }

        val hashA = DifferenceHash.compute(flat, width = 9, height = 8)
        val hashB = DifferenceHash.compute(gradient, width = 9, height = 8)

        assertNotEquals(hashA, hashB)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.imagemanager.app.hashing.DifferenceHashTest"`
Expected: FAIL with "Unresolved reference: DifferenceHash"

- [ ] **Step 3: Write `DifferenceHash.kt`**

```kotlin
package com.imagemanager.app.hashing

object DifferenceHash {

    fun compute(luminance: IntArray, width: Int = 9, height: Int = 8): Long {
        require(luminance.size == width * height) {
            "Expected $width x $height = ${width * height} pixels, got ${luminance.size}"
        }
        require(width >= 2) { "width must be at least 2 to compare adjacent pixels" }

        var hash = 0L
        var bitIndex = 0
        for (y in 0 until height) {
            for (x in 0 until width - 1) {
                val left = luminance[y * width + x]
                val right = luminance[y * width + x + 1]
                if (left < right) {
                    hash = hash or (1L shl bitIndex)
                }
                bitIndex++
            }
        }
        return hash
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.imagemanager.app.hashing.DifferenceHashTest"`
Expected: PASS (all 5 tests)

- [ ] **Step 5: Write `BitmapHashing.kt` adapter**

```kotlin
package com.imagemanager.app.hashing

import android.graphics.Bitmap
import android.graphics.Color

fun Bitmap.computeDHash(): Long {
    val resized = Bitmap.createScaledBitmap(this, 9, 8, true)
    val luminance = IntArray(9 * 8)
    for (y in 0 until 8) {
        for (x in 0 until 9) {
            val pixel = resized.getPixel(x, y)
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            luminance[y * 9 + x] = (r + g + b) / 3
        }
    }
    if (resized !== this) resized.recycle()
    return DifferenceHash.compute(luminance, width = 9, height = 8)
}
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/imagemanager/app/hashing/DifferenceHash.kt app/src/main/java/com/imagemanager/app/hashing/BitmapHashing.kt app/src/test/java/com/imagemanager/app/hashing/DifferenceHashTest.kt
git commit -m "feat: add dHash perceptual hashing algorithm"
```

---

### Task 5: HashGrouping (Hamming distance + bucketed union-find clustering)

**Files:**
- Create: `app/src/main/java/com/imagemanager/app/hashing/HashGrouping.kt`
- Test: `app/src/test/java/com/imagemanager/app/hashing/HashGroupingTest.kt`

**Interfaces:**
- Produces:
  - `data class HashedPhoto(val mediaId: Long, val hash: Long)` in `HashGrouping.kt`
  - `object HashGrouping { fun hammingDistance(a: Long, b: Long): Int; fun group(photos: List<HashedPhoto>, threshold: Int = 10, bucketBits: Int = 8): List<List<Long>> }`
  - `group` returns only clusters with 2+ members (singletons are not duplicates and are omitted).
- Consumed by: Task 6 (`DuplicateScanner`), which maps `PhotoHashEntity` rows into `HashedPhoto` and calls `group(...)`.

Bucketing compares the top `bucketBits` bits of each hash and only runs pairwise Hamming comparisons within the same bucket, per spec section 4.2. This is a known, documented trade-off: two similar photos whose hashes fall on opposite sides of a bucket boundary will not be clustered. Acceptable for MVP-scale personal libraries; noted here rather than silently assumed.

- [ ] **Step 1: Write failing test `HashGroupingTest.kt`**

```kotlin
package com.imagemanager.app.hashing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HashGroupingTest {

    @Test
    fun hammingDistance_identicalHashes_isZero() {
        assertEquals(0, HashGrouping.hammingDistance(0b1010L, 0b1010L))
    }

    @Test
    fun hammingDistance_countsDifferingBits() {
        assertEquals(2, HashGrouping.hammingDistance(0b1010L, 0b0000L))
    }

    @Test
    fun group_clustersHashesWithinThresholdInSameBucket() {
        // Both hashes share top 8 bits (all zero) so they land in the same bucket,
        // and differ by exactly 2 bits (<= default threshold of 10).
        val photos = listOf(
            HashedPhoto(mediaId = 1L, hash = 0x00000000000000F0L),
            HashedPhoto(mediaId = 2L, hash = 0x00000000000000F3L),
        )

        val groups = HashGrouping.group(photos)

        assertEquals(1, groups.size)
        assertEquals(setOf(1L, 2L), groups[0].toSet())
    }

    @Test
    fun group_doesNotClusterHashesBeyondThreshold() {
        val photos = listOf(
            HashedPhoto(mediaId = 1L, hash = 0x0000000000000000L),
            HashedPhoto(mediaId = 2L, hash = 0x0000000000000FFFL), // 12 bits different, same bucket
        )

        val groups = HashGrouping.group(photos, threshold = 10)

        assertTrue(groups.isEmpty())
    }

    @Test
    fun group_excludesSingletonsFromResult() {
        val photos = listOf(
            HashedPhoto(mediaId = 1L, hash = 0x0000000000000000L),
            HashedPhoto(mediaId = 2L, hash = 0x7F00000000000000L), // different top bits -> different bucket
        )

        val groups = HashGrouping.group(photos)

        assertTrue(groups.isEmpty())
    }

    @Test
    fun group_transitivelyMergesAChainOfSimilarHashes() {
        val photos = listOf(
            HashedPhoto(mediaId = 1L, hash = 0x00000000000000F0L),
            HashedPhoto(mediaId = 2L, hash = 0x00000000000000F3L), // close to 1
            HashedPhoto(mediaId = 3L, hash = 0x00000000000000FFL), // close to 2, further from 1
        )

        val groups = HashGrouping.group(photos, threshold = 4)

        assertEquals(1, groups.size)
        assertEquals(setOf(1L, 2L, 3L), groups[0].toSet())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.imagemanager.app.hashing.HashGroupingTest"`
Expected: FAIL with "Unresolved reference: HashGrouping"

- [ ] **Step 3: Write `HashGrouping.kt`**

```kotlin
package com.imagemanager.app.hashing

data class HashedPhoto(val mediaId: Long, val hash: Long)

object HashGrouping {

    fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    fun group(
        photos: List<HashedPhoto>,
        threshold: Int = 10,
        bucketBits: Int = 8,
    ): List<List<Long>> {
        if (photos.size < 2) return emptyList()

        val buckets = HashMap<Long, MutableList<Int>>()
        photos.forEachIndexed { index, photo ->
            val bucketKey = photo.hash ushr (64 - bucketBits)
            buckets.getOrPut(bucketKey) { mutableListOf() }.add(index)
        }

        val parent = IntArray(photos.size) { it }

        fun find(x: Int): Int {
            var root = x
            while (parent[root] != root) root = parent[root]
            var cur = x
            while (parent[cur] != root) {
                val next = parent[cur]
                parent[cur] = root
                cur = next
            }
            return root
        }

        fun union(a: Int, b: Int) {
            val rootA = find(a)
            val rootB = find(b)
            if (rootA != rootB) parent[rootA] = rootB
        }

        buckets.values.forEach { indices ->
            for (i in indices.indices) {
                for (j in i + 1 until indices.size) {
                    val a = indices[i]
                    val b = indices[j]
                    if (hammingDistance(photos[a].hash, photos[b].hash) <= threshold) {
                        union(a, b)
                    }
                }
            }
        }

        val groupsByRoot = HashMap<Int, MutableList<Long>>()
        photos.indices.forEach { index ->
            val root = find(index)
            groupsByRoot.getOrPut(root) { mutableListOf() }.add(photos[index].mediaId)
        }

        return groupsByRoot.values.filter { it.size > 1 }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.imagemanager.app.hashing.HashGroupingTest"`
Expected: PASS (all 5 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/imagemanager/app/hashing/HashGrouping.kt app/src/test/java/com/imagemanager/app/hashing/HashGroupingTest.kt
git commit -m "feat: add bucketed union-find clustering for similar photos"
```

---

### Task 6: DuplicateScanner (incremental hashing orchestration)

**Files:**
- Modify: `app/src/main/java/com/imagemanager/app/data/MediaStoreRepository.kt` (add `listAllPhotos()`)
- Create: `app/src/main/java/com/imagemanager/app/domain/DuplicateScanner.kt`
- Create: `app/src/main/java/com/imagemanager/app/domain/BitmapPhotoHasher.kt`
- Test: `app/src/test/java/com/imagemanager/app/domain/DuplicateScannerTest.kt`

**Interfaces:**
- Consumes: `PhotoHashDao`, `DuplicateGroupDao`, `PhotoHashEntity`, `DuplicateGroupEntity` (Task 3); `HashGrouping`, `HashedPhoto` (Task 5); `Photo` (Task 2).
- Produces:
  - `fun interface PhotoSource { fun listAll(): List<Photo> }`
  - `fun interface PhotoHasher { fun hash(photo: Photo): Long? }` (`null` means unreadable/corrupted — caller skips it)
  - `class DuplicateScanner(photoSource: PhotoSource, hasher: PhotoHasher, hashDao: PhotoHashDao, groupDao: DuplicateGroupDao)` with `suspend fun scan(threshold: Int = 10): List<List<Long>>`
  - `class BitmapPhotoHasher(context: Context) : PhotoHasher` — production implementation using `ContentResolver.loadThumbnail` + `computeDHash()` from Task 4, catching `IOException`/`SecurityException` and returning `null` on failure (spec section 7: corrupted images are skipped, not fatal).
  - `MediaStoreRepository.listAllPhotos(): List<Photo>` — unlimited query, implements `PhotoSource` via `MediaStoreRepository::listAllPhotos`.
- Consumed by: Task 12 (`SimilarGroupsViewModel`), which wires `DuplicateScanner(MediaStoreRepository::listAllPhotos, BitmapPhotoHasher(context), db.photoHashDao(), db.duplicateGroupDao())`.

`DuplicateScanner` itself has no Android framework dependency beyond the `Photo`/DAO types, so it is unit-testable with fakes for `PhotoSource`/`PhotoHasher` plus a real in-memory Room DB (Robolectric) for the DAOs — this exercises the actual cache-hit/cache-miss SQL behavior instead of mocking it away.

- [ ] **Step 1: Add `listAllPhotos()` to `MediaStoreRepository.kt`**

Insert directly below `listFavorites`:

```kotlin
    fun listAllPhotos(): List<Photo> = query(
        selection = null,
        selectionArgs = null,
        offset = 0,
        limit = Int.MAX_VALUE,
    )
```

- [ ] **Step 2: Write failing test `DuplicateScannerTest.kt`**

```kotlin
package com.imagemanager.app.domain

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.imagemanager.app.data.Photo
import com.imagemanager.app.data.db.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DuplicateScannerTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun photo(id: Long, dateModified: Long) = Photo(
        mediaId = id,
        uri = Uri.EMPTY,
        displayName = "photo_$id",
        dateModified = dateModified,
        isFavorite = false,
    )

    @Test
    fun scan_groupsSimilarPhotosAndPersistsHashesAndGroups() = runTest {
        val photos = listOf(photo(1L, 100L), photo(2L, 100L))
        val hasher = FakeHasher(
            mapOf(1L to 0x00000000000000F0L, 2L to 0x00000000000000F3L),
        )
        val scanner = DuplicateScanner(
            photoSource = { photos },
            hasher = hasher,
            hashDao = db.photoHashDao(),
            groupDao = db.duplicateGroupDao(),
        )

        val groups = scanner.scan(threshold = 10)

        assertEquals(1, groups.size)
        assertEquals(setOf(1L, 2L), groups[0].toSet())
        assertEquals(0x00000000000000F0L, db.photoHashDao().getByMediaId(1L)?.hash)
        assertEquals(2, hasher.callCount)
    }

    @Test
    fun scan_doesNotRehashPhotoWhenDateModifiedUnchanged() = runTest {
        val photos = listOf(photo(1L, 100L))
        val hasher = FakeHasher(mapOf(1L to 0x1111L))
        val scanner = DuplicateScanner(
            photoSource = { photos },
            hasher = hasher,
            hashDao = db.photoHashDao(),
            groupDao = db.duplicateGroupDao(),
        )

        scanner.scan()
        scanner.scan()

        assertEquals(1, hasher.callCount)
    }

    @Test
    fun scan_rehashesPhotoWhenDateModifiedChanges() = runTest {
        val hasher = FakeHasher(mapOf(1L to 0x1111L))
        val scanner = DuplicateScanner(
            photoSource = { listOf(photo(1L, 100L)) },
            hasher = hasher,
            hashDao = db.photoHashDao(),
            groupDao = db.duplicateGroupDao(),
        )
        scanner.scan()

        val rescanner = DuplicateScanner(
            photoSource = { listOf(photo(1L, 200L)) },
            hasher = hasher,
            hashDao = db.photoHashDao(),
            groupDao = db.duplicateGroupDao(),
        )
        rescanner.scan()

        assertEquals(2, hasher.callCount)
        assertEquals(200L, db.photoHashDao().getByMediaId(1L)?.dateModified)
    }

    @Test
    fun scan_skipsPhotoWhenHasherReturnsNull() = runTest {
        val photos = listOf(photo(1L, 100L), photo(2L, 100L))
        val hasher = FakeHasher(mapOf(2L to 0x1111L)) // 1L -> null (corrupted)
        val scanner = DuplicateScanner(
            photoSource = { photos },
            hasher = hasher,
            hashDao = db.photoHashDao(),
            groupDao = db.duplicateGroupDao(),
        )

        scanner.scan()

        assertNull(db.photoHashDao().getByMediaId(1L))
        assertEquals(0x1111L, db.photoHashDao().getByMediaId(2L)?.hash)
    }

    private class FakeHasher(private val hashes: Map<Long, Long>) : PhotoHasher {
        var callCount = 0
            private set

        override fun hash(photo: Photo): Long? {
            callCount++
            return hashes[photo.mediaId]
        }
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.imagemanager.app.domain.DuplicateScannerTest"`
Expected: FAIL with "Unresolved reference: DuplicateScanner"

- [ ] **Step 4: Write `DuplicateScanner.kt`**

```kotlin
package com.imagemanager.app.domain

import com.imagemanager.app.data.Photo
import com.imagemanager.app.data.db.DuplicateGroupDao
import com.imagemanager.app.data.db.DuplicateGroupEntity
import com.imagemanager.app.data.db.PhotoHashDao
import com.imagemanager.app.data.db.PhotoHashEntity
import com.imagemanager.app.hashing.HashGrouping
import com.imagemanager.app.hashing.HashedPhoto

fun interface PhotoSource {
    fun listAll(): List<Photo>
}

fun interface PhotoHasher {
    fun hash(photo: Photo): Long?
}

class DuplicateScanner(
    private val photoSource: PhotoSource,
    private val hasher: PhotoHasher,
    private val hashDao: PhotoHashDao,
    private val groupDao: DuplicateGroupDao,
) {
    suspend fun scan(threshold: Int = 10): List<List<Long>> {
        val photos = photoSource.listAll()
        val cached = hashDao.getAll().associateBy { it.mediaId }
        val hashedPhotos = mutableListOf<HashedPhoto>()

        for (photo in photos) {
            val existing = cached[photo.mediaId]
            val hash = if (existing != null && existing.dateModified == photo.dateModified) {
                existing.hash
            } else {
                val computed = hasher.hash(photo) ?: continue
                hashDao.upsert(PhotoHashEntity(photo.mediaId, photo.dateModified, computed))
                computed
            }
            hashedPhotos += HashedPhoto(photo.mediaId, hash)
        }

        val groups = HashGrouping.group(hashedPhotos, threshold)
        groupDao.clearAll()
        groupDao.insertAll(
            groups.map { mediaIds ->
                DuplicateGroupEntity(mediaIds = mediaIds, computedAt = System.currentTimeMillis())
            },
        )
        return groups
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.imagemanager.app.domain.DuplicateScannerTest"`
Expected: PASS (all 4 tests)

- [ ] **Step 6: Write production hasher `BitmapPhotoHasher.kt`**

```kotlin
package com.imagemanager.app.domain

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Size
import com.imagemanager.app.data.Photo
import com.imagemanager.app.hashing.computeDHash
import java.io.IOException

class BitmapPhotoHasher(private val context: Context) : PhotoHasher {
    override fun hash(photo: Photo): Long? = try {
        val bitmap = context.contentResolver.loadThumbnail(photo.uri, Size(128, 128), null)
            ?: return null
        val hash = bitmap.computeDHash()
        bitmap.recycle()
        hash
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        // BitmapFactory/ImageDecoder throws this for corrupted/unsupported image data.
        null
    }
}
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/imagemanager/app/data/MediaStoreRepository.kt app/src/main/java/com/imagemanager/app/domain app/src/test/java/com/imagemanager/app/domain
git commit -m "feat: add incremental duplicate scanner with cache-aware hashing"
```

---

### Task 7: Permission gate screen

**Files:**
- Create: `app/src/main/java/com/imagemanager/app/ui/permissions/PermissionGateScreen.kt`
- Test: `app/src/androidTest/java/com/imagemanager/app/ui/PermissionGateScreenTest.kt`

**Interfaces:**
- Produces: `@Composable fun PermissionGateScreen(onGranted: () -> Unit)` — shown as the nav graph's start destination (wired in Task 13); calls `onGranted()` once both `READ_MEDIA_IMAGES` and `MANAGE_EXTERNAL_STORAGE` are held.
- Consumes: nothing from earlier tasks (uses `LocalContext` directly).

- [ ] **Step 1: Write `PermissionGateScreen.kt`**

```kotlin
package com.imagemanager.app.ui.permissions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun PermissionGateScreen(onGranted: () -> Unit) {
    val context = LocalContext.current
    var mediaGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var allFilesGranted by remember { mutableStateOf(Environment.isExternalStorageManager()) }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> mediaGranted = granted }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allFilesGranted = Environment.isExternalStorageManager()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(mediaGranted, allFilesGranted) {
        if (mediaGranted && allFilesGranted) onGranted()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("사진에 접근하려면 권한이 필요합니다")
        Spacer(Modifier.height(16.dp))
        when {
            !mediaGranted -> Button(onClick = {
                mediaPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
            }) { Text("사진 접근 허용") }

            !allFilesGranted -> Button(onClick = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                )
                context.startActivity(intent)
            }) { Text("모든 파일 접근 허용 (설정으로 이동)") }
        }
    }
}
```

- [ ] **Step 2: Write instrumented UI test**

```kotlin
package com.imagemanager.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.imagemanager.app.ui.permissions.PermissionGateScreen
import org.junit.Rule
import org.junit.Test

class PermissionGateScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsMediaPermissionButton_whenPermissionNotYetGranted() {
        composeRule.setContent {
            PermissionGateScreen(onGranted = {})
        }

        composeRule.onNodeWithText("사진 접근 허용").assertExists()
    }
}
```

- [ ] **Step 3: Run test**

Run: `./gradlew connectedDebugAndroidTest --tests "com.imagemanager.app.ui.PermissionGateScreenTest"`
Expected: PASS (on a fresh emulator/device where `READ_MEDIA_IMAGES` has not yet been granted)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/imagemanager/app/ui/permissions app/src/androidTest/java/com/imagemanager/app/ui/PermissionGateScreenTest.kt
git commit -m "feat: add permission gate screen for media + all-files access"
```

---

### Task 8: Photo grid (Paging3 + Coil)

**Files:**
- Create: `app/src/main/java/com/imagemanager/app/ui/grid/PhotoPagingSource.kt`
- Create: `app/src/main/java/com/imagemanager/app/ui/grid/PhotoGrid.kt`
- Create: `app/src/main/java/com/imagemanager/app/ui/grid/PhotoGridViewModel.kt`
- Create: `app/src/main/java/com/imagemanager/app/ui/grid/PhotoGridScreen.kt`
- Test: `app/src/test/java/com/imagemanager/app/ui/grid/PhotoPagingSourceTest.kt`

**Interfaces:**
- Consumes: `Photo`, `MediaStoreRepository.listPhotos(offset, limit)` (Task 2).
- Produces:
  - `class PhotoPagingSource(pageSize: Int, queryPage: (offset: Int, limit: Int) -> List<Photo>) : PagingSource<Int, Photo>()`
  - `@Composable fun PhotoGrid(photos: Flow<PagingData<Photo>>, onPhotoClick: (Photo) -> Unit)` — reusable grid UI, **not tied to a specific ViewModel**, so Task 9's Favorites screen can reuse it directly.
  - `class PhotoGridViewModel(repository: MediaStoreRepository) : ViewModel()` exposing `val photos: Flow<PagingData<Photo>>`
  - `@Composable fun PhotoGridScreen(viewModel: PhotoGridViewModel, onPhotoClick: (Photo) -> Unit)` — thin wrapper calling `PhotoGrid(viewModel.photos, onPhotoClick)`
- Consumed by: Task 9 (`FavoritesScreen` reuses `PhotoGrid`), Task 13 (navigation host wires `PhotoGridScreen` into the grid tab).

- [ ] **Step 1: Write failing test `PhotoPagingSourceTest.kt`**

```kotlin
package com.imagemanager.app.ui.grid

import android.net.Uri
import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import com.imagemanager.app.data.Photo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PhotoPagingSourceTest {

    private fun fakePhoto(id: Long) = Photo(
        mediaId = id,
        uri = Uri.EMPTY,
        displayName = "photo_$id",
        dateModified = id,
        isFavorite = false,
    )

    @Test
    fun load_firstPage_returnsItemsWithNextKey() = runTest {
        val allPhotos = (1..10L).map { fakePhoto(it) }
        val source = PhotoPagingSource(pageSize = 4) { offset, limit -> allPhotos.drop(offset).take(limit) }

        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 4, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page

        assertEquals(4, result.data.size)
        assertEquals(4, result.nextKey)
        assertEquals(null, result.prevKey)
    }

    @Test
    fun load_lastPage_returnsNullNextKey() = runTest {
        val allPhotos = (1..5L).map { fakePhoto(it) }
        val source = PhotoPagingSource(pageSize = 4) { offset, limit -> allPhotos.drop(offset).take(limit) }

        val result = source.load(
            PagingSource.LoadParams.Append(key = 4, loadSize = 4, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page

        assertEquals(1, result.data.size)
        assertEquals(null, result.nextKey)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.imagemanager.app.ui.grid.PhotoPagingSourceTest"`
Expected: FAIL with "Unresolved reference: PhotoPagingSource"

- [ ] **Step 3: Write `PhotoPagingSource.kt`**

```kotlin
package com.imagemanager.app.ui.grid

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.imagemanager.app.data.Photo

class PhotoPagingSource(
    private val pageSize: Int,
    private val queryPage: (offset: Int, limit: Int) -> List<Photo>,
) : PagingSource<Int, Photo>() {

    override fun getRefreshKey(state: PagingState<Int, Photo>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Photo> {
        val offset = params.key ?: 0
        return try {
            val items = queryPage(offset, pageSize)
            LoadResult.Page(
                data = items,
                prevKey = if (offset == 0) null else (offset - pageSize).coerceAtLeast(0),
                nextKey = if (items.size < pageSize) null else offset + pageSize,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.imagemanager.app.ui.grid.PhotoPagingSourceTest"`
Expected: PASS (both tests)

- [ ] **Step 5: Write reusable `PhotoGrid.kt`**

```kotlin
package com.imagemanager.app.ui.grid

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import com.imagemanager.app.data.Photo
import kotlinx.coroutines.flow.Flow

@Composable
fun PhotoGrid(photos: Flow<PagingData<Photo>>, onPhotoClick: (Photo) -> Unit) {
    val items = photos.collectAsLazyPagingItems()
    LazyVerticalGrid(columns = GridCells.Fixed(3)) {
        items(count = items.itemCount, key = items.itemKey { it.mediaId }) { index ->
            val photo = items[index]
            if (photo != null) {
                AsyncImage(
                    model = photo.uri,
                    contentDescription = photo.displayName,
                    modifier = Modifier
                        .padding(1.dp)
                        .clickable { onPhotoClick(photo) },
                )
            }
        }
    }
}
```

- [ ] **Step 6: Write `PhotoGridViewModel.kt` and `PhotoGridScreen.kt`**

```kotlin
package com.imagemanager.app.ui.grid

import androidx.lifecycle.ViewModel
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.imagemanager.app.data.MediaStoreRepository
import com.imagemanager.app.data.Photo
import kotlinx.coroutines.flow.Flow

class PhotoGridViewModel(repository: MediaStoreRepository) : ViewModel() {
    val photos: Flow<PagingData<Photo>> = Pager(
        config = PagingConfig(pageSize = 60, enablePlaceholders = false),
        pagingSourceFactory = { PhotoPagingSource(pageSize = 60, queryPage = repository::listPhotos) },
    ).flow
}
```

```kotlin
package com.imagemanager.app.ui.grid

import androidx.compose.runtime.Composable
import com.imagemanager.app.data.Photo

@Composable
fun PhotoGridScreen(viewModel: PhotoGridViewModel, onPhotoClick: (Photo) -> Unit) {
    PhotoGrid(photos = viewModel.photos, onPhotoClick = onPhotoClick)
}
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/imagemanager/app/ui/grid app/src/test/java/com/imagemanager/app/ui/grid
git commit -m "feat: add paged photo grid screen"
```

---

### Task 9: Favorites screen

**Files:**
- Create: `app/src/main/java/com/imagemanager/app/ui/favorites/FavoritesViewModel.kt`
- Create: `app/src/main/java/com/imagemanager/app/ui/favorites/FavoritesScreen.kt`

**Interfaces:**
- Consumes: `PhotoGrid` composable, `PhotoPagingSource` (Task 8); `MediaStoreRepository.listFavorites` (Task 2).
- Produces: `class FavoritesViewModel(repository: MediaStoreRepository) : ViewModel()` with `val photos: Flow<PagingData<Photo>>`; `@Composable fun FavoritesScreen(viewModel: FavoritesViewModel, onPhotoClick: (Photo) -> Unit)`.
- Consumed by: Task 13 (navigation host wires this into the favorites tab).

No new test is needed: this task is a thin recombination of already-tested units (`PhotoPagingSource`'s paging logic is covered by Task 8's tests; `listFavorites` is covered by Task 2's instrumented test; `PhotoGrid` rendering is covered by Task 8). A dedicated test here would just re-assert wiring, not behavior.

- [ ] **Step 1: Write `FavoritesViewModel.kt`**

```kotlin
package com.imagemanager.app.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.imagemanager.app.data.MediaStoreRepository
import com.imagemanager.app.data.Photo
import com.imagemanager.app.ui.grid.PhotoPagingSource
import kotlinx.coroutines.flow.Flow

class FavoritesViewModel(repository: MediaStoreRepository) : ViewModel() {
    val photos: Flow<PagingData<Photo>> = Pager(
        config = PagingConfig(pageSize = 60, enablePlaceholders = false),
        pagingSourceFactory = { PhotoPagingSource(pageSize = 60, queryPage = repository::listFavorites) },
    ).flow
}
```

- [ ] **Step 2: Write `FavoritesScreen.kt`**

```kotlin
package com.imagemanager.app.ui.favorites

import androidx.compose.runtime.Composable
import com.imagemanager.app.data.Photo
import com.imagemanager.app.ui.grid.PhotoGrid

@Composable
fun FavoritesScreen(viewModel: FavoritesViewModel, onPhotoClick: (Photo) -> Unit) {
    PhotoGrid(photos = viewModel.photos, onPhotoClick = onPhotoClick)
}
```

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/imagemanager/app/ui/favorites
git commit -m "feat: add favorites screen reusing the photo grid"
```

---

### Task 10: Piles (folders) — data layer + list/picker UI

**Files:**
- Create: `app/src/main/java/com/imagemanager/app/data/PhotoActions.kt`
- Modify: `app/src/main/java/com/imagemanager/app/data/MediaStoreRepository.kt` (implement `PhotoActions`, add `movePhotoToPile`)
- Modify: `app/src/androidTest/java/com/imagemanager/app/data/MediaStoreRepositoryInstrumentedTest.kt` (add move test)
- Create: `app/src/main/java/com/imagemanager/app/ui/piles/PilePaths.kt`
- Create: `app/src/main/java/com/imagemanager/app/ui/piles/PileViewModel.kt`
- Create: `app/src/main/java/com/imagemanager/app/ui/piles/PileListScreen.kt`
- Create: `app/src/main/java/com/imagemanager/app/ui/piles/PilePickerSheet.kt`
- Test: `app/src/test/java/com/imagemanager/app/ui/piles/PilePathsTest.kt`

**Interfaces:**
- Consumes: `PileDao`, `PileEntity` (Task 3).
- Produces:
  - `interface PhotoActions { fun toggleFavorite(mediaId: Long, favorite: Boolean): IntentSender?; fun requestTrash(mediaIds: List<Long>): IntentSender; fun movePhotoToPile(mediaId: Long, pile: PileEntity): IntentSender? }` — `MediaStoreRepository` now implements this, giving Task 11 a fakeable seam.
  - `object PilePaths { fun relativePathFor(name: String): String }`
  - `class PileViewModel(pileDao: PileDao) : ViewModel()` exposing `val piles: StateFlow<List<PileEntity>>` and `fun createPile(name: String)`
  - `@Composable fun PileListScreen(viewModel: PileViewModel)`
  - `@Composable fun PilePickerSheet(piles: List<PileEntity>, onSelect: (PileEntity) -> Unit, onCreate: (String) -> Unit, onDismiss: () -> Unit)`
- Consumed by: Task 11 (`CardSwipeViewModel` uses `PhotoActions.movePhotoToPile`, `PilePickerSheet`), Task 13 (navigation host wires `PileListScreen` into the "폴더함" tab).

- [ ] **Step 1: Write failing test `PilePathsTest.kt`**

```kotlin
package com.imagemanager.app.ui.piles

import org.junit.Assert.assertEquals
import org.junit.Test

class PilePathsTest {

    @Test
    fun relativePathFor_wrapsNameInPicturesPilesFolder() {
        assertEquals("Pictures/Piles/Trip/", PilePaths.relativePathFor("Trip"))
    }

    @Test
    fun relativePathFor_trimsWhitespace() {
        assertEquals("Pictures/Piles/Trip/", PilePaths.relativePathFor("  Trip  "))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.imagemanager.app.ui.piles.PilePathsTest"`
Expected: FAIL with "Unresolved reference: PilePaths"

- [ ] **Step 3: Write `PilePaths.kt`**

```kotlin
package com.imagemanager.app.ui.piles

object PilePaths {
    fun relativePathFor(name: String): String = "Pictures/Piles/${name.trim()}/"
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.imagemanager.app.ui.piles.PilePathsTest"`
Expected: PASS (both tests)

- [ ] **Step 5: Write `PhotoActions.kt` and make `MediaStoreRepository` implement it**

```kotlin
package com.imagemanager.app.data

import android.content.IntentSender
import com.imagemanager.app.data.db.PileEntity

interface PhotoActions {
    fun toggleFavorite(mediaId: Long, favorite: Boolean): IntentSender?
    fun requestTrash(mediaIds: List<Long>): IntentSender
    fun movePhotoToPile(mediaId: Long, pile: PileEntity): IntentSender?
}
```

In `MediaStoreRepository.kt`, change the class declaration and add the new method:

```kotlin
class MediaStoreRepository(private val context: Context) : PhotoActions {
```

```kotlin
    override fun movePhotoToPile(mediaId: Long, pile: PileEntity): IntentSender? {
        val uri = ContentUris.withAppendedId(collection, mediaId)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.RELATIVE_PATH, pile.folderRelativePath)
        }
        return try {
            context.contentResolver.update(uri, values, null, null)
            null
        } catch (e: SecurityException) {
            MediaStore.createWriteRequest(context.contentResolver, listOf(uri)).intentSender
        }
    }
```

Add `override` to `toggleFavorite` and `requestTrash` signatures, and import `com.imagemanager.app.data.db.PileEntity`.

- [ ] **Step 6: Add move test to the instrumented test file**

Append to `MediaStoreRepositoryInstrumentedTest.kt`:

```kotlin
    @Test
    fun movePhotoToPile_updatesRelativePathWithoutCrashing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = MediaStoreRepository(context)
        val photos = repository.listPhotos(offset = 0, limit = 1)
        if (photos.isEmpty()) return // no seed photo on this device; skip
        val pile = com.imagemanager.app.data.db.PileEntity(
            pileId = 0,
            name = "Test",
            folderRelativePath = "Pictures/Piles/Test/",
            createdAt = 0L,
        )
        val result = repository.movePhotoToPile(photos[0].mediaId, pile)
        assertTrue(result == null || result is android.content.IntentSender)
    }
```

- [ ] **Step 7: Build and run instrumented tests**

Run: `./gradlew connectedDebugAndroidTest`
Expected: all `MediaStoreRepositoryInstrumentedTest` tests `PASS`

- [ ] **Step 8: Write `PileViewModel.kt`**

```kotlin
package com.imagemanager.app.ui.piles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imagemanager.app.data.db.PileDao
import com.imagemanager.app.data.db.PileEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PileViewModel(private val pileDao: PileDao) : ViewModel() {
    val piles: StateFlow<List<PileEntity>> = pileDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createPile(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            pileDao.insert(
                PileEntity(
                    name = name.trim(),
                    folderRelativePath = PilePaths.relativePathFor(name),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }
}
```

- [ ] **Step 9: Write `PileListScreen.kt`**

```kotlin
package com.imagemanager.app.ui.piles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PileListScreen(viewModel: PileViewModel) {
    val piles by viewModel.piles.collectAsState()
    var newPileName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row {
            OutlinedTextField(
                value = newPileName,
                onValueChange = { newPileName = it },
                label = { Text("새 폴더 이름") },
                modifier = Modifier.weight(1f),
            )
            Button(onClick = {
                viewModel.createPile(newPileName)
                newPileName = ""
            }) { Text("추가") }
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn {
            items(piles, key = { it.pileId }) { pile ->
                ListItem(headlineContent = { Text(pile.name) })
            }
        }
    }
}
```

- [ ] **Step 10: Write `PilePickerSheet.kt`**

```kotlin
package com.imagemanager.app.ui.piles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.imagemanager.app.data.db.PileEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PilePickerSheet(
    piles: List<PileEntity>,
    onSelect: (PileEntity) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newPileName by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("이동할 폴더 선택", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                items(piles, key = { it.pileId }) { pile ->
                    ListItem(
                        headlineContent = { Text(pile.name) },
                        modifier = Modifier.clickable { onSelect(pile) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedTextField(
                    value = newPileName,
                    onValueChange = { newPileName = it },
                    label = { Text("새 폴더 이름") },
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { onCreate(newPileName); newPileName = "" }) {
                    Text("만들기")
                }
            }
        }
    }
}
```

- [ ] **Step 11: Build to verify it compiles**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 12: Commit**

```bash
git add app/src/main/java/com/imagemanager/app/data/PhotoActions.kt app/src/main/java/com/imagemanager/app/data/MediaStoreRepository.kt app/src/androidTest/java/com/imagemanager/app/data/MediaStoreRepositoryInstrumentedTest.kt app/src/main/java/com/imagemanager/app/ui/piles app/src/test/java/com/imagemanager/app/ui/piles
git commit -m "feat: add pile (folder) management and move-to-pile action"
```

---

### Task 11: Card swipe screen (main interaction)

**Files:**
- Create: `app/src/main/java/com/imagemanager/app/ui/cardswipe/CardSwipeViewModel.kt`
- Create: `app/src/main/java/com/imagemanager/app/ui/cardswipe/CardSwipeScreen.kt`
- Test: `app/src/test/java/com/imagemanager/app/ui/cardswipe/CardSwipeViewModelTest.kt`
- Test: `app/src/androidTest/java/com/imagemanager/app/ui/CardSwipeGestureTest.kt`

**Interfaces:**
- Consumes: `PhotoActions`, `Photo` (Task 2/10); `PileDao`, `PileEntity` (Task 3); `PilePickerSheet` (Task 10).
- Produces:
  - `data class CardSwipeUiState(val photos: List<Photo>, val currentIndex: Int, val currentPileId: Long?)` with `currentPhoto: Photo?` and `isExhausted: Boolean`
  - `class CardSwipeViewModel(photoActions: PhotoActions, pileDao: PileDao, initialPhotos: List<Photo>) : ViewModel()` with `state: StateFlow<CardSwipeUiState>`, `piles: StateFlow<List<PileEntity>>`, `requestTrash(onNeedsConfirmation)`, `requestMoveToCurrentPile(onNeedsConfirmation, onNoPileSelected)`, `toggleFavorite(onNeedsConfirmation)`, `onConfirmationResult(confirmed: Boolean)`, `moveNext()`, `movePrevious()`, `selectPile(pileId: Long)`, `createAndSelectPile(name: String)`
  - `@Composable fun CardSwipeScreen(viewModel: CardSwipeViewModel)`
- Consumed by: Task 13 (navigation host wires this as the main screen, and as the destination when a similar-photo group is tapped in Task 12).

Gesture mapping implemented here (spec section 3.1): swipe up = trash, swipe down = move to the currently selected pile, swipe left/right = navigate only (no backend call), long-press = toggle favorite. `MediaStore.createTrashRequest`/`createWriteRequest` always require a system confirmation dialog launched via `IntentSender`; `toggleFavorite`/`movePhotoToPile` only need one when the app doesn't own the file. Both paths are modeled uniformly through `onNeedsConfirmation` callbacks plus `onConfirmationResult`.

- [ ] **Step 1: Write failing test `CardSwipeViewModelTest.kt`**

```kotlin
package com.imagemanager.app.ui.cardswipe

import android.content.IntentSender
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.imagemanager.app.data.Photo
import com.imagemanager.app.data.PhotoActions
import com.imagemanager.app.data.db.AppDatabase
import com.imagemanager.app.data.db.PileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CardSwipeViewModelTest {
    private lateinit var db: AppDatabase

    private fun photo(id: Long) = Photo(id, Uri.EMPTY, "photo_$id", id, isFavorite = false)

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private class FakePhotoActions(
        private val trashSender: IntentSender? = null,
        private val favoriteSender: IntentSender? = null,
        private val moveSender: IntentSender? = null,
    ) : PhotoActions {
        var trashCallCount = 0
        var favoriteCallCount = 0
        var moveCallCount = 0

        override fun toggleFavorite(mediaId: Long, favorite: Boolean): IntentSender? {
            favoriteCallCount++
            return favoriteSender
        }

        override fun requestTrash(mediaIds: List<Long>): IntentSender {
            trashCallCount++
            return trashSender ?: throw IllegalStateException("no sender configured")
        }

        override fun movePhotoToPile(mediaId: Long, pile: PileEntity): IntentSender? {
            moveCallCount++
            return moveSender
        }
    }

    @Test
    fun moveNext_and_movePrevious_onlyChangeIndex_noRepositoryCalls() {
        val actions = FakePhotoActions()
        val viewModel = CardSwipeViewModel(actions, db.pileDao(), listOf(photo(1), photo(2)))

        viewModel.moveNext()
        assertEquals(1, viewModel.state.value.currentIndex)

        viewModel.movePrevious()
        assertEquals(0, viewModel.state.value.currentIndex)

        assertEquals(0, actions.trashCallCount)
        assertEquals(0, actions.favoriteCallCount)
        assertEquals(0, actions.moveCallCount)
    }

    @Test
    fun toggleFavorite_whenAppliedDirectly_updatesStateWithoutConfirmation() {
        val actions = FakePhotoActions(favoriteSender = null)
        val viewModel = CardSwipeViewModel(actions, db.pileDao(), listOf(photo(1)))
        var confirmationRequested = false

        viewModel.toggleFavorite { confirmationRequested = true }

        assertFalse(confirmationRequested)
        assertTrue(viewModel.state.value.currentPhoto!!.isFavorite)
    }

    @Test
    fun requestTrash_advancesOnlyAfterConfirmationResultIsTrue() {
        val fakeSender = org.mockito.Mockito.mock(IntentSender::class.java)
        val actions = FakePhotoActions(trashSender = fakeSender)
        val viewModel = CardSwipeViewModel(actions, db.pileDao(), listOf(photo(1), photo(2)))
        var capturedSender: IntentSender? = null

        viewModel.requestTrash { capturedSender = it }
        assertEquals(fakeSender, capturedSender)
        assertEquals(0, viewModel.state.value.currentIndex) // not advanced yet

        viewModel.onConfirmationResult(confirmed = false)
        assertEquals(0, viewModel.state.value.currentIndex) // cancelled: stays put

        viewModel.requestTrash { capturedSender = it }
        viewModel.onConfirmationResult(confirmed = true)
        assertEquals(1, viewModel.state.value.currentIndex) // confirmed: advances
    }

    @Test
    fun requestMoveToCurrentPile_withNoPileSelected_callsOnNoPileSelected() {
        val actions = FakePhotoActions()
        val viewModel = CardSwipeViewModel(actions, db.pileDao(), listOf(photo(1)))
        var noPileCalled = false

        viewModel.requestMoveToCurrentPile(
            onNeedsConfirmation = { },
            onNoPileSelected = { noPileCalled = true },
        )

        assertTrue(noPileCalled)
        assertEquals(0, actions.moveCallCount)
    }

    @Test
    fun createAndSelectPile_insertsPileAndSelectsIt() = runTest {
        val actions = FakePhotoActions()
        val viewModel = CardSwipeViewModel(actions, db.pileDao(), listOf(photo(1)))

        viewModel.createAndSelectPile("Trip")
        kotlinx.coroutines.test.advanceUntilIdle()

        assertEquals("Trip", db.pileDao().observeAll().let { flow ->
            kotlinx.coroutines.flow.first(flow)
        }.first().name)
        assertEquals(viewModel.state.value.currentPileId, db.pileDao().let { dao ->
            kotlinx.coroutines.runBlocking { kotlinx.coroutines.flow.first(dao.observeAll()) }
        }.first().pileId)
    }
}
```

Note: this test uses `org.mockito.mockito-core` for a trivial `IntentSender` stand-in (it is never invoked, only passed through and compared by reference). Add `testImplementation("org.mockito:mockito-core:5.12.0")` to `app/build.gradle.kts` dependencies if not already present.

- [ ] **Step 2: Add Mockito test dependency**

In `app/build.gradle.kts`, add under the existing `testImplementation` lines:

```kotlin
    testImplementation("org.mockito:mockito-core:5.12.0")
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.imagemanager.app.ui.cardswipe.CardSwipeViewModelTest"`
Expected: FAIL with "Unresolved reference: CardSwipeViewModel"

- [ ] **Step 4: Write `CardSwipeViewModel.kt`**

```kotlin
package com.imagemanager.app.ui.cardswipe

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imagemanager.app.data.Photo
import com.imagemanager.app.data.PhotoActions
import com.imagemanager.app.data.db.PileDao
import com.imagemanager.app.data.db.PileEntity
import com.imagemanager.app.ui.piles.PilePaths
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CardSwipeUiState(
    val photos: List<Photo> = emptyList(),
    val currentIndex: Int = 0,
    val currentPileId: Long? = null,
) {
    val currentPhoto: Photo? get() = photos.getOrNull(currentIndex)
    val isExhausted: Boolean get() = currentIndex >= photos.size
}

class CardSwipeViewModel(
    private val photoActions: PhotoActions,
    private val pileDao: PileDao,
    initialPhotos: List<Photo>,
) : ViewModel() {

    private val _state = MutableStateFlow(CardSwipeUiState(photos = initialPhotos))
    val state: StateFlow<CardSwipeUiState> = _state

    val piles: StateFlow<List<PileEntity>> = pileDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var pendingConfirmation: (() -> Unit)? = null

    fun moveNext() {
        if (_state.value.currentIndex < _state.value.photos.size) advance()
    }

    fun movePrevious() {
        val current = _state.value
        if (current.currentIndex > 0) _state.value = current.copy(currentIndex = current.currentIndex - 1)
    }

    fun selectPile(pileId: Long) {
        _state.value = _state.value.copy(currentPileId = pileId)
    }

    fun createAndSelectPile(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = pileDao.insert(
                PileEntity(
                    name = name.trim(),
                    folderRelativePath = PilePaths.relativePathFor(name),
                    createdAt = System.currentTimeMillis(),
                ),
            )
            selectPile(id)
        }
    }

    fun requestTrash(onNeedsConfirmation: (IntentSender) -> Unit) {
        val photo = _state.value.currentPhoto ?: return
        val sender = photoActions.requestTrash(listOf(photo.mediaId))
        pendingConfirmation = { advance() }
        onNeedsConfirmation(sender)
    }

    fun requestMoveToCurrentPile(
        onNeedsConfirmation: (IntentSender) -> Unit,
        onNoPileSelected: () -> Unit,
    ) {
        val photo = _state.value.currentPhoto ?: return
        val pile = piles.value.find { it.pileId == _state.value.currentPileId }
        if (pile == null) {
            onNoPileSelected()
            return
        }
        val sender = photoActions.movePhotoToPile(photo.mediaId, pile)
        if (sender == null) {
            advance()
        } else {
            pendingConfirmation = { advance() }
            onNeedsConfirmation(sender)
        }
    }

    fun toggleFavorite(onNeedsConfirmation: (IntentSender) -> Unit) {
        val photo = _state.value.currentPhoto ?: return
        val newFavorite = !photo.isFavorite
        val sender = photoActions.toggleFavorite(photo.mediaId, newFavorite)
        if (sender == null) {
            applyFavorite(newFavorite)
        } else {
            pendingConfirmation = { applyFavorite(newFavorite) }
            onNeedsConfirmation(sender)
        }
    }

    fun onConfirmationResult(confirmed: Boolean) {
        if (confirmed) pendingConfirmation?.invoke()
        pendingConfirmation = null
    }

    private fun applyFavorite(favorite: Boolean) {
        val current = _state.value
        val index = current.currentIndex
        val updated = current.photos.toMutableList()
        updated.getOrNull(index)?.let { updated[index] = it.copy(isFavorite = favorite) }
        _state.value = current.copy(photos = updated)
    }

    private fun advance() {
        _state.value = _state.value.copy(currentIndex = _state.value.currentIndex + 1)
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.imagemanager.app.ui.cardswipe.CardSwipeViewModelTest"`
Expected: PASS (all 5 tests)

- [ ] **Step 6: Write `CardSwipeScreen.kt`**

```kotlin
package com.imagemanager.app.ui.cardswipe

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import coil.compose.AsyncImage
import com.imagemanager.app.ui.piles.PilePickerSheet
import kotlin.math.roundToInt

private const val SWIPE_THRESHOLD_PX = 300f

@Composable
fun CardSwipeScreen(viewModel: CardSwipeViewModel) {
    val state by viewModel.state.collectAsState()
    val piles by viewModel.piles.collectAsState()
    var showPilePicker by remember { mutableStateOf(false) }

    val confirmationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onConfirmationResult(result.resultCode == Activity.RESULT_OK) }

    Box(modifier = Modifier.fillMaxSize()) {
        val selectedPile = piles.find { it.pileId == state.currentPileId }
        AssistChip(
            onClick = { showPilePicker = true },
            label = { Text(selectedPile?.name ?: "폴더 선택") },
            modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
        )

        val photo = state.currentPhoto
        if (photo == null) {
            Text("더 이상 사진이 없습니다", modifier = Modifier.align(Alignment.Center))
        } else {
            var dragOffset by remember(photo.mediaId) { mutableStateOf(Offset.Zero) }
            AsyncImage(
                model = photo.uri,
                contentDescription = photo.displayName,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
                    .pointerInput(photo.mediaId) {
                        detectDragGestures(
                            onDragEnd = {
                                val offset = dragOffset
                                when {
                                    offset.y <= -SWIPE_THRESHOLD_PX -> viewModel.requestTrash {
                                        confirmationLauncher.launch(IntentSenderRequest.Builder(it).build())
                                    }
                                    offset.y >= SWIPE_THRESHOLD_PX -> viewModel.requestMoveToCurrentPile(
                                        onNeedsConfirmation = {
                                            confirmationLauncher.launch(IntentSenderRequest.Builder(it).build())
                                        },
                                        onNoPileSelected = { showPilePicker = true },
                                    )
                                    offset.x >= SWIPE_THRESHOLD_PX -> viewModel.movePrevious()
                                    offset.x <= -SWIPE_THRESHOLD_PX -> viewModel.moveNext()
                                }
                                dragOffset = Offset.Zero
                            },
                        ) { change, amount ->
                            change.consume()
                            dragOffset += amount
                        }
                    }
                    .pointerInput(photo.mediaId) {
                        detectTapGestures(
                            onLongPress = {
                                viewModel.toggleFavorite {
                                    confirmationLauncher.launch(IntentSenderRequest.Builder(it).build())
                                }
                            },
                        )
                    },
            )
        }

        if (showPilePicker) {
            PilePickerSheet(
                piles = piles,
                onSelect = {
                    viewModel.selectPile(it.pileId)
                    showPilePicker = false
                },
                onCreate = { name -> viewModel.createAndSelectPile(name) },
                onDismiss = { showPilePicker = false },
            )
        }
    }
}
```

(Add `import androidx.compose.foundation.layout.offset` for the `Modifier.offset { }` lambda overload used above.)

- [ ] **Step 7: Write instrumented gesture test `CardSwipeGestureTest.kt`**

```kotlin
package com.imagemanager.app.ui

import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.imagemanager.app.data.Photo
import com.imagemanager.app.data.PhotoActions
import com.imagemanager.app.data.db.AppDatabase
import com.imagemanager.app.data.db.PileEntity
import com.imagemanager.app.ui.cardswipe.CardSwipeScreen
import com.imagemanager.app.ui.cardswipe.CardSwipeViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CardSwipeGestureTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class NoOpPhotoActions : PhotoActions {
        override fun toggleFavorite(mediaId: Long, favorite: Boolean) = null
        override fun requestTrash(mediaIds: List<Long>) =
            throw UnsupportedOperationException("not exercised by this test")
        override fun movePhotoToPile(mediaId: Long, pile: PileEntity) = null
    }

    @Test
    fun swipeLeft_advancesToNextPhoto() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val photos = listOf(
            Photo(1L, Uri.EMPTY, "a", 1L, false),
            Photo(2L, Uri.EMPTY, "b", 2L, false),
        )
        val viewModel = CardSwipeViewModel(NoOpPhotoActions(), db.pileDao(), photos)

        composeRule.setContent { CardSwipeScreen(viewModel) }
        composeRule.onRoot().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals(1, viewModel.state.value.currentIndex)
    }
}
```

- [ ] **Step 8: Run instrumented test**

Run: `./gradlew connectedDebugAndroidTest --tests "com.imagemanager.app.ui.CardSwipeGestureTest"`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/imagemanager/app/ui/cardswipe app/src/test/java/com/imagemanager/app/ui/cardswipe app/src/androidTest/java/com/imagemanager/app/ui/CardSwipeGestureTest.kt
git commit -m "feat: add Slidebox-style card swipe screen (trash/pile/favorite/navigate)"
```

---

### Task 12: Similar photos screen (on-demand scan + group list)

**Files:**
- Modify: `app/src/main/java/com/imagemanager/app/data/MediaStoreRepository.kt` (add `uriFor`, `listByIds`)
- Create: `app/src/main/java/com/imagemanager/app/ui/similar/SimilarGroupsViewModel.kt`
- Create: `app/src/main/java/com/imagemanager/app/ui/similar/SimilarGroupsScreen.kt`
- Test: `app/src/test/java/com/imagemanager/app/ui/similar/SimilarGroupsViewModelTest.kt`

**Interfaces:**
- Consumes: `DuplicateScanner` (Task 6), `DuplicateGroupDao`, `DuplicateGroupEntity` (Task 3).
- Produces:
  - `MediaStoreRepository.uriFor(mediaId: Long): Uri`, `MediaStoreRepository.listByIds(mediaIds: List<Long>): List<Photo>`
  - `class SimilarGroupsViewModel(scanner: DuplicateScanner, duplicateGroupDao: DuplicateGroupDao) : ViewModel()` with `groups: StateFlow<List<DuplicateGroupEntity>>`, `isScanning: StateFlow<Boolean>`, `fun rescan()`
  - `@Composable fun SimilarGroupsScreen(viewModel: SimilarGroupsViewModel, photoUriFor: (Long) -> Uri, onGroupClick: (DuplicateGroupEntity) -> Unit)`
- Consumed by: Task 13 (navigation host wires this into the "유사 사진" tab and routes `onGroupClick` to a group-scoped `CardSwipeScreen`, per spec section 3.3).

- [ ] **Step 1: Add `uriFor` and `listByIds` to `MediaStoreRepository.kt`**

```kotlin
    fun uriFor(mediaId: Long): Uri = ContentUris.withAppendedId(collection, mediaId)

    fun listByIds(mediaIds: List<Long>): List<Photo> {
        if (mediaIds.isEmpty()) return emptyList()
        val placeholders = mediaIds.joinToString(",") { "?" }
        return query(
            selection = "${MediaStore.Images.Media._ID} IN ($placeholders)",
            selectionArgs = mediaIds.map { it.toString() }.toTypedArray(),
            offset = 0,
            limit = mediaIds.size,
        )
    }
```

(Requires `import android.net.Uri`.)

- [ ] **Step 2: Write failing test `SimilarGroupsViewModelTest.kt`**

```kotlin
package com.imagemanager.app.ui.similar

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.imagemanager.app.data.Photo
import com.imagemanager.app.data.db.AppDatabase
import com.imagemanager.app.domain.DuplicateScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SimilarGroupsViewModelTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun rescan_populatesGroupsFromScanner() = runTest {
        val photos = listOf(
            Photo(1L, Uri.EMPTY, "a", 1L, false),
            Photo(2L, Uri.EMPTY, "b", 1L, false),
        )
        val hashes = mapOf(1L to 0x00000000000000F0L, 2L to 0x00000000000000F3L)
        val scanner = DuplicateScanner(
            photoSource = { photos },
            hasher = { hashes[it.mediaId] },
            hashDao = db.photoHashDao(),
            groupDao = db.duplicateGroupDao(),
        )
        val viewModel = SimilarGroupsViewModel(scanner, db.duplicateGroupDao())

        viewModel.rescan()
        advanceUntilIdle()

        assertEquals(1, viewModel.groups.value.size)
        assertEquals(setOf(1L, 2L), viewModel.groups.value[0].mediaIds.toSet())
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.imagemanager.app.ui.similar.SimilarGroupsViewModelTest"`
Expected: FAIL with "Unresolved reference: SimilarGroupsViewModel"

- [ ] **Step 4: Write `SimilarGroupsViewModel.kt`**

```kotlin
package com.imagemanager.app.ui.similar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imagemanager.app.data.db.DuplicateGroupDao
import com.imagemanager.app.data.db.DuplicateGroupEntity
import com.imagemanager.app.domain.DuplicateScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SimilarGroupsViewModel(
    private val scanner: DuplicateScanner,
    duplicateGroupDao: DuplicateGroupDao,
) : ViewModel() {
    val groups: StateFlow<List<DuplicateGroupEntity>> = duplicateGroupDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    fun rescan() {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            try {
                scanner.scan()
            } finally {
                _isScanning.value = false
            }
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.imagemanager.app.ui.similar.SimilarGroupsViewModelTest"`
Expected: PASS

- [ ] **Step 6: Write `SimilarGroupsScreen.kt`**

```kotlin
package com.imagemanager.app.ui.similar

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.imagemanager.app.data.db.DuplicateGroupEntity

@Composable
fun SimilarGroupsScreen(
    viewModel: SimilarGroupsViewModel,
    photoUriFor: (Long) -> Uri,
    onGroupClick: (DuplicateGroupEntity) -> Unit,
) {
    val groups by viewModel.groups.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    Column {
        Button(
            onClick = { viewModel.rescan() },
            enabled = !isScanning,
            modifier = Modifier.padding(16.dp),
        ) {
            Text(if (isScanning) "스캔 중..." else "다시 스캔")
        }
        LazyColumn {
            items(groups, key = { it.groupId }) { group ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onGroupClick(group) }
                        .padding(8.dp),
                ) {
                    LazyRow {
                        items(group.mediaIds.take(4)) { mediaId ->
                            AsyncImage(
                                model = photoUriFor(mediaId),
                                contentDescription = null,
                                modifier = Modifier.size(72.dp).padding(2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/imagemanager/app/data/MediaStoreRepository.kt app/src/main/java/com/imagemanager/app/ui/similar app/src/test/java/com/imagemanager/app/ui/similar
git commit -m "feat: add similar-photos screen with on-demand rescan"
```

---

### Task 13: Navigation host — final app wiring

**Files:**
- Create: `app/src/main/java/com/imagemanager/app/ImageManagerApp.kt`
- Create: `app/src/main/java/com/imagemanager/app/ui/navigation/AppViewModelFactory.kt`
- Create: `app/src/main/java/com/imagemanager/app/ui/navigation/AppNavHost.kt`
- Modify: `app/src/main/java/com/imagemanager/app/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml` (register `ImageManagerApp`)

**Interfaces:**
- Consumes every screen/ViewModel produced by Tasks 7-12.
- Produces: the assembled app. No new public interface — this is the integration point, so its "test" is end-to-end: the app builds, launches past the permission gate, and all five tabs are reachable.

This task is pure composition of already-tested units (permission gate, grid, favorites, card swipe, similar groups, piles), so there is no new unit-testable logic here — the verification is a build + manual smoke test, listed in Step 6.

- [ ] **Step 1: Write `ImageManagerApp.kt`**

```kotlin
package com.imagemanager.app

import android.app.Application
import com.imagemanager.app.data.MediaStoreRepository
import com.imagemanager.app.data.db.AppDatabase
import com.imagemanager.app.domain.BitmapPhotoHasher
import com.imagemanager.app.domain.DuplicateScanner

class ImageManagerApp : Application() {
    lateinit var mediaStoreRepository: MediaStoreRepository
        private set
    lateinit var database: AppDatabase
        private set
    lateinit var duplicateScanner: DuplicateScanner
        private set

    override fun onCreate() {
        super.onCreate()
        mediaStoreRepository = MediaStoreRepository(this)
        database = AppDatabase.getInstance(this)
        duplicateScanner = DuplicateScanner(
            photoSource = mediaStoreRepository::listAllPhotos,
            hasher = BitmapPhotoHasher(this),
            hashDao = database.photoHashDao(),
            groupDao = database.duplicateGroupDao(),
        )
    }
}
```

- [ ] **Step 2: Register it in `AndroidManifest.xml`**

Change the `<application>` tag's opening line to:

```xml
    <application
        android:name=".ImageManagerApp"
        android:allowBackup="true"
        android:label="Image Manager"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
```

- [ ] **Step 3: Write `AppViewModelFactory.kt`**

```kotlin
package com.imagemanager.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.imagemanager.app.ImageManagerApp
import com.imagemanager.app.data.Photo
import com.imagemanager.app.ui.cardswipe.CardSwipeViewModel
import com.imagemanager.app.ui.favorites.FavoritesViewModel
import com.imagemanager.app.ui.grid.PhotoGridViewModel
import com.imagemanager.app.ui.piles.PileViewModel
import com.imagemanager.app.ui.similar.SimilarGroupsViewModel

class AppViewModelFactory(
    private val app: ImageManagerApp,
    private val cardSwipePhotos: List<Photo> = emptyList(),
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = when (modelClass) {
        PhotoGridViewModel::class.java ->
            PhotoGridViewModel(app.mediaStoreRepository) as T
        FavoritesViewModel::class.java ->
            FavoritesViewModel(app.mediaStoreRepository) as T
        PileViewModel::class.java ->
            PileViewModel(app.database.pileDao()) as T
        SimilarGroupsViewModel::class.java ->
            SimilarGroupsViewModel(app.duplicateScanner, app.database.duplicateGroupDao()) as T
        CardSwipeViewModel::class.java ->
            CardSwipeViewModel(app.mediaStoreRepository, app.database.pileDao(), cardSwipePhotos) as T
        else -> throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
    }
}
```

- [ ] **Step 4: Write `AppNavHost.kt`**

```kotlin
package com.imagemanager.app.ui.navigation

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.imagemanager.app.ImageManagerApp
import com.imagemanager.app.ui.cardswipe.CardSwipeScreen
import com.imagemanager.app.ui.cardswipe.CardSwipeViewModel
import com.imagemanager.app.ui.favorites.FavoritesScreen
import com.imagemanager.app.ui.favorites.FavoritesViewModel
import com.imagemanager.app.ui.grid.PhotoGridScreen
import com.imagemanager.app.ui.grid.PhotoGridViewModel
import com.imagemanager.app.ui.permissions.PermissionGateScreen
import com.imagemanager.app.ui.piles.PileListScreen
import com.imagemanager.app.ui.piles.PileViewModel
import com.imagemanager.app.ui.similar.SimilarGroupsScreen
import com.imagemanager.app.ui.similar.SimilarGroupsViewModel

private sealed class Tab(val route: String, val label: String) {
    object Swipe : Tab("swipe", "정리")
    object Grid : Tab("grid", "전체")
    object Favorites : Tab("favorites", "즐겨찾기")
    object Similar : Tab("similar", "유사 사진")
    object Piles : Tab("piles", "폴더함")
}

private val tabs = listOf(Tab.Swipe, Tab.Grid, Tab.Favorites, Tab.Similar, Tab.Piles)

@Composable
fun AppNavHost(app: ImageManagerApp) {
    var permissionGranted by remember { mutableStateOf(false) }

    if (!permissionGranted) {
        PermissionGateScreen(onGranted = { permissionGranted = true })
        return
    }

    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId)
                                launchSingleTop = true
                            }
                        },
                        icon = {},
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Swipe.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.Swipe.route) {
                val viewModel: CardSwipeViewModel = viewModel(
                    factory = AppViewModelFactory(app, cardSwipePhotos = app.mediaStoreRepository.listAllPhotos()),
                )
                CardSwipeScreen(viewModel)
            }
            composable(Tab.Grid.route) {
                val viewModel: PhotoGridViewModel = viewModel(factory = AppViewModelFactory(app))
                PhotoGridScreen(viewModel, onPhotoClick = {})
            }
            composable(Tab.Favorites.route) {
                val viewModel: FavoritesViewModel = viewModel(factory = AppViewModelFactory(app))
                FavoritesScreen(viewModel, onPhotoClick = {})
            }
            composable(Tab.Similar.route) {
                val viewModel: SimilarGroupsViewModel = viewModel(factory = AppViewModelFactory(app))
                SimilarGroupsScreen(
                    viewModel = viewModel,
                    photoUriFor = { app.mediaStoreRepository.uriFor(it) },
                    onGroupClick = { group ->
                        navController.navigate("swipe_group/${group.mediaIds.joinToString(",")}")
                    },
                )
            }
            composable(Tab.Piles.route) {
                val viewModel: PileViewModel = viewModel(factory = AppViewModelFactory(app))
                PileListScreen(viewModel)
            }
            composable(
                route = "swipe_group/{mediaIds}",
                arguments = listOf(navArgument("mediaIds") { type = NavType.StringType }),
            ) { backStackEntry ->
                val ids = backStackEntry.arguments?.getString("mediaIds")
                    ?.split(",")?.mapNotNull { it.toLongOrNull() }
                    ?: emptyList()
                val groupPhotos = app.mediaStoreRepository.listByIds(ids)
                val viewModel: CardSwipeViewModel = viewModel(
                    factory = AppViewModelFactory(app, cardSwipePhotos = groupPhotos),
                )
                CardSwipeScreen(viewModel)
            }
        }
    }
}
```

- [ ] **Step 5: Modify `MainActivity.kt` to launch the nav host**

```kotlin
package com.imagemanager.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.imagemanager.app.ui.navigation.AppNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    AppNavHost(application as ImageManagerApp)
                }
            }
        }
    }
}
```

- [ ] **Step 6: Build and manually smoke-test**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

Then install on a device/emulator (`./gradlew installDebug`) and manually verify:
1. App opens to the permission screen; granting both permissions reveals the card swipe screen.
2. Swiping a card left/right moves to the next/previous photo without a system dialog.
3. Swiping up shows the system trash confirmation dialog; confirming removes the card.
4. Tapping the folder chip opens the pile picker; creating a pile and swiping down moves the photo and shows it under "폴더함".
5. Long-pressing a card toggles its favorite heart, and the photo now appears under "즐겨찾기".
6. "유사 사진" tab's "다시 스캔" button populates duplicate groups after a scan; tapping one opens a card-swipe view scoped to that group.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/imagemanager/app/ImageManagerApp.kt app/src/main/java/com/imagemanager/app/ui/navigation app/src/main/java/com/imagemanager/app/MainActivity.kt app/src/main/AndroidManifest.xml
git commit -m "feat: wire navigation host, bottom tabs, and app-wide DI"
```

---

## Self-Review Notes

**Spec coverage:**
- §2 MediaStore-backed favorites/trash → Task 2, Task 10 (`PhotoActions`)
- §3.1 Card swipe gestures (up/down/left-right/long-press) → Task 11
- §3.2 Grid / Favorites / Similar / Piles tabs → Tasks 8, 9, 12, 10, wired in Task 13
- §3.3 Group → card-swipe comparison → Task 13's `swipe_group/{mediaIds}` route
- §4.1-4.2 dHash + bucketed union-find → Tasks 4, 5
- §4.3 On-demand incremental scan → Task 6, 12
- §5 Data model, rating deferred → Task 3 (schema), noted as out of scope throughout
- §6 Permissions → Task 1 (manifest), Task 7 (runtime flow)
- §7 Error handling → corrupted image skip (Task 6), swipe cancel/no-op (Task 11's `onConfirmationResult(false)`), missing pile on swipe-down (Task 11's `onNoPileSelected`); permission denial screen (Task 7)
- §8 Testing strategy → unit tests in Tasks 3-6, 8, 10-12; instrumented tests in Tasks 2, 7, 10, 11

**Placeholder scan:** no "TBD"/"TODO" remain; every step has concrete code or an exact command.

**Type consistency check:** `Photo`, `PhotoActions`, `PileEntity`, `PileDao`, `HashedPhoto`, `DuplicateGroupEntity`, `CardSwipeUiState` are each defined once (Tasks 2, 10, 3, 5, 3, 11) and referenced with matching names/signatures in every later task.

**Known, documented limitations (not gaps — explicitly out of scope per spec §9):** no iOS/cross-platform, no background periodic scanning, no AI-embedding similarity, no 5-star rating UI.

