package me.ash.reader.infrastructure.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.ash.reader.domain.model.account.*
import me.ash.reader.domain.model.account.security.DESUtils
import me.ash.reader.domain.model.article.ArchivedArticle
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.model.blacklist.BlacklistKeyword
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.group.Group
import me.ash.reader.domain.repository.AccountDao
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.repository.BlacklistKeywordDao
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.repository.GroupDao
import me.ash.reader.infrastructure.preference.*
import me.ash.reader.ui.ext.toInt
import java.util.*

@Database(
    entities = [Account::class, Feed::class, Article::class, Group::class, ArchivedArticle::class, BlacklistKeyword::class],
    version = 11,
    autoMigrations = [
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 5, to = 7),
        AutoMigration(from = 6, to = 7),
    ]
)
@TypeConverters(
    AndroidDatabase.DateConverters::class,
    AccountTypeConverters::class,
    SyncIntervalConverters::class,
    SyncOnStartConverters::class,
    SyncOnlyOnWiFiConverters::class,
    SyncOnlyWhenChargingConverters::class,
    KeepArchivedConverters::class,
    SyncBlockListConverters::class,
)
abstract class AndroidDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun feedDao(): FeedDao
    abstract fun articleDao(): ArticleDao
    abstract fun groupDao(): GroupDao
    abstract fun blacklistKeywordDao(): BlacklistKeywordDao

    companion object {

        private var instance: AndroidDatabase? = null

        fun getInstance(context: Context): AndroidDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AndroidDatabase::class.java,
                    "Reader"
                ).addMigrations(*allMigrations).build().also {
                    instance = it
                }
            }
        }
    }

    class DateConverters {

        @TypeConverter
        fun toDate(dateLong: Long?): Date? {
            return dateLong?.let { Date(it) }
        }

        @TypeConverter
        fun fromDate(date: Date?): Long? {
            return date?.time
        }
    }
}

val allMigrations = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10,
    MIGRATION_10_11,
)

@Suppress("ClassName")
object MIGRATION_1_2 : Migration(1, 2) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            ALTER TABLE article ADD COLUMN img TEXT DEFAULT NULL
            """.trimIndent()
        )
    }
}

@Suppress("ClassName")
object MIGRATION_2_3 : Migration(2, 3) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            ALTER TABLE article ADD COLUMN updateAt INTEGER DEFAULT ${System.currentTimeMillis()}
            """.trimIndent()
        )
        database.execSQL(
            """
            ALTER TABLE account ADD COLUMN syncInterval INTEGER NOT NULL DEFAULT ${SyncIntervalPreference.default.value}
            """.trimIndent()
        )
        database.execSQL(
            """
            ALTER TABLE account ADD COLUMN syncOnStart INTEGER NOT NULL DEFAULT ${SyncOnStartPreference.default.value.toInt()}
            """.trimIndent()
        )
        database.execSQL(
            """
            ALTER TABLE account ADD COLUMN syncOnlyOnWiFi INTEGER NOT NULL DEFAULT ${SyncOnlyOnWiFiPreference.default.value.toInt()}
            """.trimIndent()
        )
        database.execSQL(
            """
            ALTER TABLE account ADD COLUMN syncOnlyWhenCharging INTEGER NOT NULL DEFAULT ${SyncOnlyWhenChargingPreference.default.value.toInt()}
            """.trimIndent()
        )
        database.execSQL(
            """
            ALTER TABLE account ADD COLUMN keepArchived INTEGER NOT NULL DEFAULT ${KeepArchivedPreference.default.value}
            """.trimIndent()
        )
        database.execSQL(
            """
            ALTER TABLE account ADD COLUMN syncBlockList TEXT NOT NULL DEFAULT ''
            """.trimIndent()
        )
    }
}

@Suppress("ClassName")
object MIGRATION_3_4 : Migration(3, 4) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            ALTER TABLE account ADD COLUMN securityKey TEXT DEFAULT '${DESUtils.empty}'
            """.trimIndent()
        )
    }
}

@Suppress("ClassName")
object MIGRATION_4_5 : Migration(4, 5) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            ALTER TABLE account ADD COLUMN lastArticleId TEXT DEFAULT NULL
            """.trimIndent()
        )
    }
}

@Suppress("ClassName")
object MIGRATION_7_8 : Migration(7, 8) {

    override fun migrate(database: SupportSQLiteDatabase) {
        // 2026-01-22: 添加 sortOrder 字段到 group 表，用于支持分组排序功能
        database.execSQL(
            """
            ALTER TABLE `group` ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0
            """.trimIndent()
        )
    }
}

@Suppress("ClassName")
object MIGRATION_8_9 : Migration(8, 9) {

    override fun migrate(database: SupportSQLiteDatabase) {
        // 2026-01-24: 创建关键词黑名单表，用于文章标题过滤
        // 与账户无关，仅和订阅源相关，支持多订阅源匹配（逗号分隔）
        database.execSQL("DROP TABLE IF EXISTS `blacklist_keyword`")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `blacklist_keyword` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `keyword` TEXT NOT NULL,
                `enabled` INTEGER NOT NULL DEFAULT 1,
                `feedUrls` TEXT,
                `feedNames` TEXT,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        // 创建索引以优化查询
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_blacklist_keyword_keyword` ON `blacklist_keyword` (`keyword`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_blacklist_keyword_enabled` ON `blacklist_keyword` (`enabled`)")
    }
}

@Suppress("ClassName")
object MIGRATION_9_10 : Migration(9, 10) {

    override fun migrate(database: SupportSQLiteDatabase) {
        // 2026-01-24: 修复数据库迁移问题
        // 之前可能存在带外键约束的表，先删除再重建正确的表结构
        database.execSQL("DROP TABLE IF EXISTS `blacklist_keyword`")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `blacklist_keyword` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `keyword` TEXT NOT NULL,
                `enabled` INTEGER NOT NULL DEFAULT 1,
                `feedUrls` TEXT,
                `feedNames` TEXT,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        // 创建索引以优化查询
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_blacklist_keyword_keyword` ON `blacklist_keyword` (`keyword`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_blacklist_keyword_enabled` ON `blacklist_keyword` (`enabled`)")
    }
}

@Suppress("ClassName")
object MIGRATION_10_11 : Migration(10, 11) {

    override fun migrate(database: SupportSQLiteDatabase) {
        // 2026-01-27: 添加 sortOrder 字段到 feed 表，用于支持订阅源排序功能
        database.execSQL(
            """
            ALTER TABLE `feed` ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0
            """.trimIndent()
        )
    }
}
