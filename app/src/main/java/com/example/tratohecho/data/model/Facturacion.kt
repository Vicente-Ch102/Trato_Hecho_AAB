package com.example.tratohecho.data.model
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName="ordenes") data class OrdenCompra(@PrimaryKey(autoGenerate=true) val id:Long=0,val tenantId:Long=1,val cotizacionId:Long,val numero:String,val fecha:String,val observaciones:String="")
@Entity(tableName="facturas") data class Factura(@PrimaryKey(autoGenerate=true) val id:Long=0,val tenantId:Long=1,val cotizacionId:Long,val numero:String,val fecha:String,val observaciones:String="",val remoteId:String?=null,@ColumnInfo(defaultValue = "'BORRADOR'") val estado:String="BORRADOR",val vencimiento:String?=null)
