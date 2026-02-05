package me.ash.reader.infrastructure.db

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import me.ash.reader.domain.model.account.*
import me.ash.reader.domain.model.article.ArchivedArticle
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.model.blacklist.BlacklistKeyword
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.group.Group
import me.ash.reader.plugin.PluginRule
import me.ash.reader.infrastructure.translate.model.ArticleTranslationCache
import me.ash.reader.domain.repository.AccountDao
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.infrastructure.translate.cache.ArticleTranslationCacheDao
import me.ash.reader.domain.repository.BlacklistKeywordDao
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.repository.GroupDao
import java.util.*

@Database(
    entities = [Account::class, Feed::class, Article::class, Group::class, ArchivedArticle::class, BlacklistKeyword::class, ArticleTranslationCache::class, PluginRule::class],
    version = 22,
    autoMigrations = [
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 5, to = 7),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 11, to = 12),
    ],
    exportSchema = true
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
    abstract fun articleTranslationCacheDao(): ArticleTranslationCacheDao
    abstract fun pluginRuleDao(): me.ash.reader.plugin.PluginRuleDao

    companion object {

        private var instance: AndroidDatabase? = null

                /**
                 * 数据库迁移：从版本12到版本13
                 *
                 * 1. 为 article 表添加 isTranslated 字段
                 * 2. 删除 article_translation_cache 表的 isShowingTranslation 字段
                 *
                 * 修改日期：2026-02-01
                 * 修改原因：简化翻译状态管理，移除 isShowingTranslation 字段
                 */
                private val MIGRATION_12_13 = object : Migration(12, 13) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        // 为 article 表添加 isTranslated 列，默认值为 false
                        database.execSQL(
                            "ALTER TABLE article ADD COLUMN isTranslated INTEGER NOT NULL DEFAULT 0"
                        )
                        
                        // 删除 article_translation_cache 表的 isShowingTranslation 列
                        // SQLite 不支持直接删除列，需要重新创建表
                        database.execSQL("DROP TABLE IF EXISTS article_translation_cache_new")
                        database.execSQL("""
                            CREATE TABLE article_translation_cache_new (
                                articleId TEXT PRIMARY KEY NOT NULL,
                                feedId TEXT NOT NULL,
                                isTranslated INTEGER NOT NULL DEFAULT 0,
                                translatedTitle TEXT,
                                fullHtmlContent TEXT,
                                translateProvider TEXT,
                                translateModel TEXT,
                                translatedAt INTEGER NOT NULL
                            )
                        """.trimIndent())
                        database.execSQL("INSERT INTO article_translation_cache_new SELECT articleId, feedId, isTranslated, translatedTitle, fullHtmlContent, translateProvider, translateModel, translatedAt FROM article_translation_cache")
                        database.execSQL("DROP TABLE article_translation_cache")
                        database.execSQL("ALTER TABLE article_translation_cache_new RENAME TO article_translation_cache")
                    }
                }
        
                /**
                 * 数据库迁移：从版本13到版本14
                 *
                 * 1. 为 feed 表添加 isAutoTranslate 字段
                 *
                 * 修改日期：2026-02-02
                 * 修改原因：添加自动翻译全文功能开关
                 */
                private val MIGRATION_13_14 = object : Migration(13, 14) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        // 为 feed 表添加 isAutoTranslate 列，默认值为 false
                        database.execSQL(
                            "ALTER TABLE feed ADD COLUMN isAutoTranslate INTEGER NOT NULL DEFAULT 0"
                        )
                    }
                }

                /**
                 * 数据库迁移：从版本14到版本15
                 *
                 * 1. 为 feed 表添加 isAutoTranslateTitle 字段
                 *
                 * 修改日期：2026-02-02
                 * 修改原因：添加自动翻译文章标题功能开关（独立字段）
                 */
                private val MIGRATION_14_15 = object : Migration(14, 15) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        // 为 feed 表添加 isAutoTranslateTitle 列，默认值为 false
                        database.execSQL(
                            "ALTER TABLE feed ADD COLUMN isAutoTranslateTitle INTEGER NOT NULL DEFAULT 0"
                        )
                    }
                }

                /**
                 * 数据库迁移：从版本15到版本16
                 *
                 * 1. 为 article 表添加 translatedTitle 字段
                 *
                 * 修改日期：2026-02-03
                 * 修改原因：添加文章标题翻译缓存字段，用于存储翻译后的标题
                 */
                private val MIGRATION_15_16 = object : Migration(15, 16) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        // 为 article 表添加 translatedTitle 列，默认值为 null
                        database.execSQL(
                            "ALTER TABLE article ADD COLUMN translatedTitle TEXT"
                        )
                    }
                }

                /**
                 * 数据库迁移：从版本 16 到版本 17
                 *
                 * 1. 为 feed 表添加图片过滤相关字段
                 *
                 * 修改日期：2026-02-04
                 * 修改原因：订阅源图片过滤功能
                 */
                private val MIGRATION_16_17 = object : Migration(16, 17) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL(
                            "ALTER TABLE feed ADD COLUMN isImageFilterEnabled INTEGER NOT NULL DEFAULT 0"
                        )
                        database.execSQL(
                            "ALTER TABLE feed ADD COLUMN imageFilterResolution TEXT NOT NULL DEFAULT ''"
                        )
                        database.execSQL(
                            "ALTER TABLE feed ADD COLUMN imageFilterFileName TEXT NOT NULL DEFAULT ''"
                        )
                        database.execSQL(
                            "ALTER TABLE feed ADD COLUMN imageFilterDomain TEXT NOT NULL DEFAULT ''"
                        )
                    }
                }
                /**
                 * 鏁版嵁搴撹縼绉伙：浠庣増鏈?17 鍒扮版本?18
                 *
                 * 1. 向 feed 表添加 isDisableReferer 字段
                 *
                 * 修改日期：2026-02-04
                 * 修改原因：新增订阅源级“禁止 Referer”开关
                 */
                private val MIGRATION_17_18 = object : Migration(17, 18) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL(
                            "ALTER TABLE feed ADD COLUMN isDisableReferer INTEGER NOT NULL DEFAULT 0"
                        )
                    }
                }
                /**
                 * 数据库迁移：从版本 18 到版本 19
                 *
                 * 1. 向 feed 表添加 isDisableJavaScript 字段
                 *
                 * 修改日期：2026-02-04
                 * 修改原因：新增订阅源级“关闭JS”开关
                 */
                private val MIGRATION_18_19 = object : Migration(18, 19) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL(
                            "ALTER TABLE feed ADD COLUMN isDisableJavaScript INTEGER NOT NULL DEFAULT 0"
                        )
                    }
                }
                /**
                 * 数据库迁移：从版本 19 到版本 20
                 *
                 * 1. 新增插件规则表 plugin_rule
                 *
                 * 修改日期：2026-02-04
                 * 修改原因：新增“插件”规则存储
                 */
                private val MIGRATION_19_20 = object : Migration(19, 20) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS plugin_rule (
                                id TEXT NOT NULL,
                                accountId INTEGER NOT NULL,
                                name TEXT NOT NULL,
                                subscribeUrl TEXT NOT NULL,
                                icon TEXT NOT NULL DEFAULT '',
                                listHtmlCache TEXT NOT NULL DEFAULT '',
                                listTitleSelector TEXT NOT NULL,
                                listUrlSelector TEXT NOT NULL,
                                listImageSelector TEXT NOT NULL DEFAULT '',
                                listTimeSelector TEXT NOT NULL DEFAULT '',
                                detailTitleSelector TEXT NOT NULL DEFAULT '',
                                detailAuthorSelector TEXT NOT NULL DEFAULT '',
                                detailTimeSelector TEXT NOT NULL DEFAULT '',
                                detailContentSelector TEXT NOT NULL,
                                detailContentSelectors TEXT NOT NULL DEFAULT '',
                                detailImageSelector TEXT NOT NULL DEFAULT '',
                                detailVideoSelector TEXT NOT NULL DEFAULT '',
                                detailAudioSelector TEXT NOT NULL DEFAULT '',
                                isEnabled INTEGER NOT NULL DEFAULT 0,
                                createdAt INTEGER NOT NULL,
                                updatedAt INTEGER NOT NULL,
                                PRIMARY KEY(id)
                            )
                            """.trimIndent()
                        )
                        database.execSQL(
                            "CREATE INDEX IF NOT EXISTS index_plugin_rule_accountId ON plugin_rule(accountId)"
                        )
                    }
                }
                /**
                 * 数据库迁移：从版本 20 到版本 21
                 *
                 * 1. plugin_rule 增加 icon、listHtmlCache、detailContentSelectors 字段
                 */
                private val MIGRATION_20_21 = object : Migration(20, 21) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL(
                            "ALTER TABLE plugin_rule ADD COLUMN icon TEXT NOT NULL DEFAULT ''"
                        )
                        database.execSQL(
                            "ALTER TABLE plugin_rule ADD COLUMN listHtmlCache TEXT NOT NULL DEFAULT ''"
                        )
                        database.execSQL(
                            "ALTER TABLE plugin_rule ADD COLUMN detailContentSelectors TEXT NOT NULL DEFAULT ''"
                        )
                    }
                }
                /**
                 * 数据库迁移：从版本 21 到版本 22
                 *
                 * 1. plugin_rule 增加 detailExcludeSelector 字段
                 */
                private val MIGRATION_21_22 = object : Migration(21, 22) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL(
                            "ALTER TABLE plugin_rule ADD COLUMN detailExcludeSelector TEXT NOT NULL DEFAULT ''"
                        )
                    }
                }
        fun getInstance(context: Context): AndroidDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AndroidDatabase::class.java,
                    "Reader"
                ).addMigrations(
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18,
                    MIGRATION_18_19,
                    MIGRATION_19_20,
                    MIGRATION_20_21,
                    MIGRATION_21_22
                )
                 .build().also {
                    instance = it
                }
            }
        }
    }

    class DateConverters {
        @TypeConverter
        fun fromTimestamp(value: Long?): Date? {
            return value?.let { Date(it) }
        }

        @TypeConverter
        fun dateToTimestamp(date: Date?): Long? {
            return date?.time
        }
    }
}
