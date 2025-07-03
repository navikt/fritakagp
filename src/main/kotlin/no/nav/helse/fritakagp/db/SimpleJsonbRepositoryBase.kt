package no.nav.helse.fritakagp.db

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource

interface SimpleJsonbEntity {
    val id: UUID
}

interface SimpleJsonbRepository<T : SimpleJsonbEntity> {
    fun getById(id: UUID): T?

    fun insert(entity: T): T

    fun delete(id: UUID): Int

    fun update(entity: T)
}

/**
 * Enkelt CRUD-base for enkle UUID-id'ede typer.
 * Tabellen må ha kun en kolonne av typen jsonb, og entitietene må ha ett felt av typen UUID som heter id.
 * SQL:
 *  CREATE TABLE $tableName (data jsonb not null);
 */
abstract class SimpleJsonbRepositoryBase<T : SimpleJsonbEntity>(
    tableName: String,
    val ds: DataSource,
    val mapper: ObjectMapper,
    val clazz: Class<T>
) : SimpleJsonbRepository<T> {

    private val getByIdStatement = """SELECT * FROM $tableName WHERE data ->> 'id' = ?"""
    private val saveStatement = "INSERT INTO $tableName (data) VALUES (?::json);"
    private val updateStatement = "UPDATE $tableName SET data = ?::json WHERE data ->> 'id' = ?"
    private val deleteStatement = """DELETE FROM $tableName WHERE data ->> 'id' = ?"""
    private val getNesteReferanseStatement = "SELECT nextval('referanse_seq')"

    override fun getById(id: UUID): T? {
        val existingList = ArrayList<T>()
        ds.connection.use { con ->
            con.prepareStatement(getByIdStatement).apply {
                setString(1, id.toString())
            }.use {
                val res = it.executeQuery()
                while (res.next()) {
                    val sg = mapper.readValue(res.getString("data"), clazz)
                    existingList.add(sg)
                }
                res.close()
            }
        }
        return existingList.firstOrNull()
    }

    private fun getNesteReferanse(connection: Connection): Int? {
        connection.prepareStatement(getNesteReferanseStatement).use {
            val res = it.executeQuery()
            if (res.next()) {
                return res.getInt(1)
            }
        }
        return null
    }

    override fun insert(entity: T): T {
        ds.connection.use { connection ->

            val referansenummer = getNesteReferanse(connection)
            val json = mapper.convertValue(entity, ObjectNode::class.java).apply {
                put("referansenummer", referansenummer)
            }.let { mapper.writeValueAsString(it) }
            connection.prepareStatement(saveStatement).use {
                it.apply {
                    setString(1, json)
                }.executeUpdate()
            }
        }
        return entity
    }

    override fun delete(id: UUID): Int {
        ds.connection.use { connection ->
            connection.prepareStatement(deleteStatement).use {
                return it.apply {
                    setString(1, id.toString())
                }.executeUpdate()
            }
        }
    }

    override fun update(entity: T) {
        val json = mapper.writeValueAsString(entity)
        ds.connection.use { connection ->
            connection.prepareStatement(updateStatement).use {
                it.apply {
                    setString(1, json)
                    setString(2, entity.id.toString())
                }.executeUpdate()
            }
        }
    }
}
