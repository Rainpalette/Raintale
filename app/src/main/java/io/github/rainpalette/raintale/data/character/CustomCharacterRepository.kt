package io.github.rainpalette.raintale.data.character

import android.content.Context
import kotlinx.coroutines.flow.Flow

/** 自定义角色数据层。上限 [MAX] 个（角色条不宜过长）。 */
class CustomCharacterRepository(context: Context) {

    private val dao = CustomCharacterDatabase.get(context).dao()

    val all: Flow<List<CustomCharacter>> = dao.all()

    suspend fun count(): Int = dao.count()

    suspend fun byId(id: Long): CustomCharacter? = dao.byId(id)

    /** 新增/更新。新增（id=0）时若已达上限返回 false（不写入）。 */
    suspend fun save(c: CustomCharacter): Boolean {
        if (c.id == 0L) {
            if (dao.count() >= MAX) return false
            dao.upsert(c.copy(createdAt = System.currentTimeMillis()))
        } else {
            dao.upsert(c)
        }
        return true
    }

    suspend fun delete(c: CustomCharacter) = dao.delete(c)

    companion object {
        const val MAX = 5
    }
}
