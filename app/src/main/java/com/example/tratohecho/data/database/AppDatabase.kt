package com.example.tratohecho.data.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.tratohecho.data.model.*

class Converters {
    @TypeConverter fun perfil(v: Perfil) = v.name
    @TypeConverter fun perfil(v: String) = Perfil.valueOf(v)
    @TypeConverter fun estado(v: EstadoCotizacion) = v.name
    @TypeConverter fun estado(v: String) = EstadoCotizacion.valueOf(v)
    @TypeConverter fun sync(v: EstadoSync) = v.name
    @TypeConverter fun sync(v: String) = EstadoSync.valueOf(v)
}

@Database(
    entities = [
        Cliente::class, Cotizacion::class, ItemCotizacion::class,
        TipoUnidad::class, Impuesto::class, Usuario::class,
        OrdenCompra::class, Factura::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clienteDao(): ClienteDao
    abstract fun cotizacionDao(): CotizacionDao
    abstract fun itemDao(): ItemDao
    abstract fun adminDao(): AdminDao
    abstract fun facturacionDao(): FacturacionDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clientes ADD COLUMN remoteId TEXT")
                db.execSQL("ALTER TABLE cotizaciones ADD COLUMN remoteId TEXT")
                db.execSQL("ALTER TABLE items ADD COLUMN catalogItemRemoteId TEXT")
                db.execSQL("ALTER TABLE impuestos ADD COLUMN remoteId TEXT")
                db.execSQL("ALTER TABLE usuarios ADD COLUMN remoteId TEXT")
                db.execSQL("ALTER TABLE facturas ADD COLUMN remoteId TEXT")
                db.execSQL("ALTER TABLE facturas ADD COLUMN estado TEXT NOT NULL DEFAULT 'BORRADOR'")
                db.execSQL("ALTER TABLE facturas ADD COLUMN vencimiento TEXT")
            }
        }

        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "tratohecho_database"
            )
                .addMigrations(MIGRATION_3_4)
                .build()
                .also { INSTANCE = it }
        }
    }
}
