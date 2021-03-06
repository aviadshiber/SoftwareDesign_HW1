package il.ac.technion.cs.softwaredesign.managers

import il.ac.technion.cs.softwaredesign.internals.ISequenceGenerator
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.api.IChannelManager
import il.ac.technion.cs.softwaredesign.storage.channels.IChannelStorage
import il.ac.technion.cs.softwaredesign.storage.api.IStatisticsManager
import il.ac.technion.cs.softwaredesign.storage.datastructures.CountIdKey
import il.ac.technion.cs.softwaredesign.storage.datastructures.SecureAVLTree
import il.ac.technion.cs.softwaredesign.storage.utils.DB_NAMES.TREE_CHANNELS_BY_ACTIVE_USERS_COUNT
import il.ac.technion.cs.softwaredesign.storage.utils.DB_NAMES.TREE_CHANNELS_BY_USERS_COUNT
import il.ac.technion.cs.softwaredesign.storage.utils.MANAGERS_CONSTS
import il.ac.technion.cs.softwaredesign.storage.utils.MANAGERS_CONSTS.CHANNEL_INVALID_ID
import il.ac.technion.cs.softwaredesign.storage.utils.MANAGERS_CONSTS.CHANNEL_INVALID_NAME
import il.ac.technion.cs.softwaredesign.storage.utils.MANAGERS_CONSTS.CHANNEL_NAME_PROPERTY
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelManager
@Inject constructor(private val channelStorage: IChannelStorage,
                    private val statisticsManager: IStatisticsManager,
                    @ChannelIdSeqGenerator private val channelIdGenerator: ISequenceGenerator,
                    @ChannelByUserCountStorage private val channelsByUsersCountStorage: SecureStorage,
                    @ChannelByActiveUserCountStorage private val channelsByActiveUsersCountStorage: SecureStorage
) : IChannelManager {
    private val defaultKey: () -> CountIdKey = { CountIdKey() }

    private val channelsByUsersCountTree = SecureAVLTree(channelsByUsersCountStorage, defaultKey)
    private val channelsByActiveUsersCountTree = SecureAVLTree(channelsByActiveUsersCountStorage, defaultKey)

    override fun addChannel(channelName: String): Long {
        if (channelName == CHANNEL_INVALID_NAME) throw IllegalArgumentException("channel name cannot be empty")
        if (isChannelNameExists(channelName)) throw IllegalArgumentException("channel name already exist")
        val channelId = channelIdGenerator.next()

        // id db
        channelStorage.setChannelIdToChannelName(channelName, channelId)

        // details db
        channelStorage.setPropertyStringToChannelId(channelId, CHANNEL_NAME_PROPERTY, channelName)
        channelStorage.setPropertyLongToChannelId(channelId, MANAGERS_CONSTS.CHANNEL_NR_MEMBERS, 0L)
        channelStorage.setPropertyLongToChannelId(channelId, MANAGERS_CONSTS.CHANNEL_NR_ACTIVE_MEMBERS, 0L)
        channelStorage.setPropertyListToChannelId(channelId, MANAGERS_CONSTS.CHANNEL_MEMBERS_LIST, emptyList())
        channelStorage.setPropertyListToChannelId(channelId, MANAGERS_CONSTS.CHANNEL_OPERATORS_LIST, emptyList())

        // trees
        addNewChannelToChannelTrees(channelId = channelId, count = 0L)

        statisticsManager.increaseNumberOfChannelsBy()
        return channelId
    }

    override fun removeChannel(channelId: Long) {
        removeChannelFromChannelTrees(channelId)
        invalidateChannel(channelId)
        statisticsManager.decreaseNumberOfChannelsBy()
    }

    override fun isChannelNameExists(channelName: String): Boolean {
        return isChannelValid(channelName)
    }

    override fun isChannelIdExists(channelId: Long): Boolean {
        return isChannelValid(channelId)
    }

    override fun getChannelIdByName(channelName: String): Long {
        if (!isChannelValid(channelName = channelName)) throw IllegalArgumentException("channel name is not valid")
        val id = channelStorage.getChannelIdByChannelName(channelName)
        if (isChannelValid(channelId = id)) return id!! // TODO: redundant, consider removing it
        throw IllegalArgumentException("returned channel id is not valid")
    }

    override fun getChannelNameById(channelId: Long): String {
        if (!isChannelValid(channelId = channelId)) throw IllegalArgumentException("channel id is not valid")
        val name = channelStorage.getPropertyStringByChannelId(channelId, CHANNEL_NAME_PROPERTY)
        if (isChannelValid(channelName = name)) return name!! // TODO: redundant, consider removing it
        throw IllegalArgumentException("returned channel name is not valid")
    }

    override fun getNumberOfChannels(): Long {
        return statisticsManager.getNumberOfChannels()
    }


    /** NUMBER OF ACTIVE MEMBERS **/
    /** this property should be updated regardless members list updates **/
    override fun getNumberOfActiveMembersInChannel(channelId: Long): Long {
        if (!isChannelValid(channelId = channelId)) throw IllegalArgumentException("channel id is not valid")
        return channelStorage.getPropertyLongByChannelId(channelId, MANAGERS_CONSTS.CHANNEL_NR_ACTIVE_MEMBERS)
                ?: throw IllegalArgumentException("channel id is valid but returned null")
    }

    override fun increaseNumberOfActiveMembersInChannelBy(channelId: Long, count: Long) {
        changeNumberOfActiveMembersInChannelBy(channelId, count)
    }

    override fun decreaseNumberOfActiveMembersInChannelBy(channelId: Long, count: Long) {
        changeNumberOfActiveMembersInChannelBy(channelId, -count)
    }


    /** MEMBERS LIST **/
    override fun getNumberOfMembersInChannel(channelId: Long): Long {
        if (!isChannelValid(channelId = channelId)) throw IllegalArgumentException("channel id is not valid")
        return channelStorage.getPropertyLongByChannelId(channelId, MANAGERS_CONSTS.CHANNEL_NR_MEMBERS)
                ?: throw IllegalArgumentException("channel id is valid but returned null")
    }

    override fun getChannelMembersList(channelId: Long): List<Long> {
        if (!isChannelValid(channelId = channelId)) throw IllegalArgumentException("channel id is not valid")
        return channelStorage.getPropertyListByChannelId(channelId, MANAGERS_CONSTS.CHANNEL_MEMBERS_LIST)
                ?: throw IllegalArgumentException("channel id does not exist")
    }

    override fun addMemberToChannel(channelId: Long, memberId: Long) {
        if (!isChannelValid(channelId = channelId)) throw IllegalArgumentException("channel id is not valid")
        val currentList = ArrayList<Long>(getChannelMembersList(channelId))
        if (currentList.contains(memberId)) throw IllegalAccessException("member id already exists in channel")
        currentList.add(memberId)
        channelStorage.setPropertyListToChannelId(channelId, MANAGERS_CONSTS.CHANNEL_MEMBERS_LIST, currentList)
        channelStorage.setPropertyLongToChannelId(channelId, MANAGERS_CONSTS.CHANNEL_NR_MEMBERS, currentList.size.toLong())

        val size = currentList.size.toLong()
        updateKeyInTree(TREE_CHANNELS_BY_USERS_COUNT, channelId, size - 1L, size)
    }

    override fun removeMemberFromChannel(channelId: Long, memberId: Long) {
        if (!isChannelValid(channelId = channelId)) throw IllegalArgumentException("channel id is not valid")
        val currentList = ArrayList<Long>(getChannelMembersList(channelId))
        if (!currentList.contains(memberId)) throw IllegalAccessException("member id does not exists in channel")
        currentList.remove(memberId)
        channelStorage.setPropertyListToChannelId(channelId, MANAGERS_CONSTS.CHANNEL_MEMBERS_LIST, currentList)
        channelStorage.setPropertyLongToChannelId(channelId, MANAGERS_CONSTS.CHANNEL_NR_MEMBERS, currentList.size.toLong())

        val size = currentList.size.toLong()
        updateKeyInTree(TREE_CHANNELS_BY_USERS_COUNT, channelId, size + 1L, size)
    }


    /** OPERATORS LIST **/
    override fun getChannelOperatorsList(channelId: Long): List<Long> {
        if (!isChannelValid(channelId = channelId)) throw IllegalArgumentException("channel id is not valid")
        return channelStorage.getPropertyListByChannelId(channelId, MANAGERS_CONSTS.CHANNEL_OPERATORS_LIST)
                ?: throw IllegalArgumentException("channel id does not exist")
    }

    override fun addOperatorToChannel(channelId: Long, operatorId: Long) {
        if (!isChannelValid(channelId = channelId)) throw IllegalArgumentException("channel id is not valid")
        val currentList = ArrayList<Long>(getChannelOperatorsList(channelId))
        if (currentList.contains(operatorId)) return //operator id already exists in channel
        currentList.add(operatorId)
        channelStorage.setPropertyListToChannelId(channelId, MANAGERS_CONSTS.CHANNEL_OPERATORS_LIST, currentList)
    }

    override fun removeOperatorFromChannel(channelId: Long, operatorId: Long) {
        if (!isChannelValid(channelId = channelId)) throw IllegalArgumentException("channel id is not valid")
        val currentList = ArrayList<Long>(getChannelOperatorsList(channelId))
        // if (!currentList.contains(operatorId)) throw IllegalAccessException("operator id does not exists in channel")
        currentList.remove(operatorId)
        channelStorage.setPropertyListToChannelId(channelId, MANAGERS_CONSTS.CHANNEL_OPERATORS_LIST, currentList)
    }


    /** CHANNEL COMPLEX STATISTICS **/
    override fun getTop10ChannelsByUsersCount(): List<String> {
        return getTop10FromTree(TREE_CHANNELS_BY_USERS_COUNT)
    }

    override fun getTop10ChannelsByActiveUsersCount(): List<String> {
        return getTop10FromTree(TREE_CHANNELS_BY_ACTIVE_USERS_COUNT)
    }

    /** PRIVATES **/
    // channel name exists if and only if it is mapped to a VALID channel id, i.e. channel id != CHANNEL_INVALID_ID
    // and its id_name is not mapped to CHANNEL_INVALID_NAME
    private fun isChannelValid(channelId: Long?): Boolean {
        if (channelId != null && channelId != CHANNEL_INVALID_ID) {
            val name = channelStorage.getPropertyStringByChannelId(channelId, CHANNEL_NAME_PROPERTY)
            return name != null && name != CHANNEL_INVALID_NAME
        }
        return false
    }

    private fun isChannelValid(channelName: String?): Boolean {
        if (channelName != null && channelName != CHANNEL_INVALID_NAME) {
            val id = channelStorage.getChannelIdByChannelName(channelName)
            return id != null && id != CHANNEL_INVALID_ID
        }
        return false
    }

    private fun invalidateChannel(channelId: Long) {
        try {
            val channelName = getChannelNameById(channelId)
            invalidateChannel(channelId, channelName)
        } catch (e: IllegalArgumentException) {
        }
    }

    private fun invalidateChannel(channelId: Long, channelName: String) {
        channelStorage.setChannelIdToChannelName(channelName, CHANNEL_INVALID_ID)
        channelStorage.setPropertyStringToChannelId(channelId, CHANNEL_NAME_PROPERTY, CHANNEL_INVALID_NAME)
    }

    private fun addNewChannelToChannelTrees(channelId: Long, count: Long) {
        val key = CountIdKey(count = count, id = channelId)
        channelsByUsersCountTree.put(key)
        channelsByActiveUsersCountTree.put(key)
    }

    private fun removeChannelFromChannelTrees(channelId: Long) {
        val membersCount = getNumberOfMembersInChannel(channelId)
        val membersKey = CountIdKey(id = channelId, count = membersCount)
        channelsByUsersCountTree.delete(membersKey)

        val activeMembersCount = getNumberOfActiveMembersInChannel(channelId)
        val activeMembersKey = CountIdKey(id = channelId, count = activeMembersCount)
        channelsByActiveUsersCountTree.delete(activeMembersKey)
    }

    private fun changeNumberOfActiveMembersInChannelBy(channelId: Long, count: Long) {
        // value:
        if (!isChannelValid(channelId = channelId)) throw IllegalArgumentException("channel id is not valid")
        val currentValue = getNumberOfActiveMembersInChannel(channelId)
        val newValue = currentValue + count
        channelStorage.setPropertyLongToChannelId(channelId, MANAGERS_CONSTS.CHANNEL_NR_ACTIVE_MEMBERS, newValue)

        updateKeyInTree(TREE_CHANNELS_BY_ACTIVE_USERS_COUNT, channelId, currentValue, newValue)
    }

    private fun updateKeyInTree(treeName: String, channelId: Long, currentValue: Long, newValue: Long) {
        val tree = getTreeByName(treeName)
        val oldKey = CountIdKey(count = currentValue, id = channelId)
        tree.delete(oldKey)
        val newKey = CountIdKey(count = newValue, id = channelId)
        tree.put(newKey)
    }

    private fun getTop10FromTree(treeName: String): List<String> {
        val tree = getTreeByName(treeName)
        val values = mutableListOf<String>()
        val nrChannels = getNumberOfChannels()
        val nrOutputChannels = if (nrChannels > 10) 10 else nrChannels
        for (k in 1..nrOutputChannels) {
            val kthLarger = nrChannels - k
            val channelId = tree.select(kthLarger).getId()
            val userName = getChannelNameById(channelId)
            values.add(userName)
        }
        return values
    }

    private fun getTreeByName(treeName: String): SecureAVLTree<CountIdKey> {
        return when (treeName) {
            TREE_CHANNELS_BY_USERS_COUNT -> channelsByUsersCountTree
            TREE_CHANNELS_BY_ACTIVE_USERS_COUNT -> channelsByActiveUsersCountTree
            else -> throw IllegalAccessException("tree does not exist, should not get here")
        }
    }
}