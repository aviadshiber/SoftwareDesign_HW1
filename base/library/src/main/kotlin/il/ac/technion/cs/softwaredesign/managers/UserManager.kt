package il.ac.technion.cs.softwaredesign.managers

import il.ac.technion.cs.softwaredesign.storage.api.IUserManager.LoginStatus
import il.ac.technion.cs.softwaredesign.storage.api.IUserManager.PrivilegeLevel
import il.ac.technion.cs.softwaredesign.internals.ISequenceGenerator
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.api.IStatisticsManager
import il.ac.technion.cs.softwaredesign.storage.api.IUserManager
import il.ac.technion.cs.softwaredesign.storage.datastructures.CountIdKey
import il.ac.technion.cs.softwaredesign.storage.datastructures.IdKey
import il.ac.technion.cs.softwaredesign.storage.datastructures.IdOperatorKey
import il.ac.technion.cs.softwaredesign.storage.datastructures.SecureAVLTree
import il.ac.technion.cs.softwaredesign.storage.users.IUserStorage
import il.ac.technion.cs.softwaredesign.storage.utils.ConversionUtils
import il.ac.technion.cs.softwaredesign.storage.utils.DB_NAMES
import il.ac.technion.cs.softwaredesign.storage.utils.DB_NAMES.TREE_USERS_BY_CHANNELS_COUNT
import il.ac.technion.cs.softwaredesign.storage.utils.MANAGERS_CONSTS
import il.ac.technion.cs.softwaredesign.storage.utils.MANAGERS_CONSTS.INVALID_USER_ID
import il.ac.technion.cs.softwaredesign.storage.utils.MANAGERS_CONSTS.LIST_PROPERTY
import il.ac.technion.cs.softwaredesign.storage.utils.MANAGERS_CONSTS.PASSWORD_PROPERTY
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserManager
@Inject constructor(private val userStorage: IUserStorage,
                    private val statisticsManager: IStatisticsManager,
                    @UserIdSeqGenerator private val userIdGenerator: ISequenceGenerator,
                    @UserTreesStorage private val userTreesStorage: SecureStorage,
                    @UsersByChannelCountStorage private val usersByChannelsCountStorage: SecureStorage
) : IUserManager {

    private val defaultIdKey: () -> IdKey = { IdKey() }
    private val defaultCountIdKey: () -> CountIdKey = { CountIdKey() }
    private val usersByChannelsCountTree =
            SecureAVLTree(usersByChannelsCountStorage, TREE_USERS_BY_CHANNELS_COUNT.toByteArray(), defaultCountIdKey)

    override fun addUser(username: String, password: String, status: LoginStatus, privilege: PrivilegeLevel): Long {
        var userId = getUserId(username)
        if (userId == INVALID_USER_ID) throw IllegalArgumentException("user id is not valid")
        if (userId != null) throw IllegalArgumentException("user already exist")
        userId = userIdGenerator.next()

        // id db
        userStorage.setUserIdToUsername(username, userId)

        // details db
        userStorage.setPropertyStringToUserId(userId, MANAGERS_CONSTS.USERNAME_PROPERTY, username)
        userStorage.setPropertyStringToUserId(userId, MANAGERS_CONSTS.PASSWORD_PROPERTY, password)
        userStorage.setPropertyStringToUserId(userId, MANAGERS_CONSTS.STATUS_PROPERTY, status.ordinal.toString())
        userStorage.setPropertyStringToUserId(userId, MANAGERS_CONSTS.PRIVILAGE_PROPERTY, privilege.ordinal.toString())
//        initChannelList(userId)
        userStorage.setPropertyLongToUserId(userId, MANAGERS_CONSTS.SIZE_PROPERTY, 0L)

        // tree db
        addNewUserToUserTree(userId = userId, count = 0L)

        // increase logged in users only, cause number of users was increased by id generator
        if (status == LoginStatus.IN) statisticsManager.increaseLoggedInUsersBy()

        return userId
    }


    /** GETTERS & SETTERS **/
    override fun getUserId(username: String): Long? {
        return userStorage.getUserIdByUsername(username)
    }

    override fun getUsernameById(userId: Long): String {
        return userStorage.getPropertyStringByUserId(userId, MANAGERS_CONSTS.USERNAME_PROPERTY)
                ?: throw IllegalArgumentException("user id does not exist")
    }

    override fun getUserPrivilege(userId: Long): PrivilegeLevel {
        val userPrivilege = userStorage.getPropertyStringByUserId(userId, MANAGERS_CONSTS.PRIVILAGE_PROPERTY)
                ?: throw IllegalArgumentException("user id does not exist")
        return PrivilegeLevel.values()[userPrivilege.toInt()]
    }

    override fun getUserStatus(userId: Long): LoginStatus {
        val userPrivilege = userStorage.getPropertyStringByUserId(userId, MANAGERS_CONSTS.STATUS_PROPERTY)
                ?: throw IllegalArgumentException("user id does not exist")
        return LoginStatus.values()[userPrivilege.toInt()]
    }

    override fun getUserPassword(userId: Long): String {
        val password = userStorage.getPropertyStringByUserId(userId, PASSWORD_PROPERTY)
        return password ?: throw IllegalArgumentException("user id does not exist")
    }

    override fun updateUserPrivilege(userId: Long, privilege: PrivilegeLevel) {
        userStorage.setPropertyStringToUserId(userId, MANAGERS_CONSTS.PRIVILAGE_PROPERTY, privilege.ordinal.toString())
    }

    override fun updateUserStatus(userId: Long, status: LoginStatus) {
        try {
            val oldStatus = getUserStatus(userId)
            if (oldStatus == status) return
            userStorage.setPropertyStringToUserId(userId, MANAGERS_CONSTS.STATUS_PROPERTY, status.ordinal.toString())
            if (status == LoginStatus.IN) {
                statisticsManager.increaseLoggedInUsersBy()
            } else {
                statisticsManager.decreaseLoggedInUsersBy()
            }
        } catch (e: IllegalArgumentException) { /* user id does not exist, do nothing */
        }
    }


    override fun isUsernameExists(username: String): Boolean {
        val userId = userStorage.getUserIdByUsername(username)
        return userId != null && userId != INVALID_USER_ID
    }

    override fun isUserIdExists(userId: Long): Boolean {
        val password = userStorage.getPropertyStringByUserId(userId, PASSWORD_PROPERTY)
        return password != null
    }


    /** CHANNELS OF USER **/
    override fun getNumberOfChannels(userId: Long): Long {
        return userStorage.getPropertyLongByUserId(userId, MANAGERS_CONSTS.SIZE_PROPERTY)
                ?: throw IllegalArgumentException("user id does not exist")
    }

    override fun isUserInChannel(userId: Long, channelId: Long): Boolean {
        if (!isUserIdExists(userId)) throw IllegalArgumentException("user does not exist")
        val tempTree = getTree(userId)
        val key = IdKey(id = channelId)
        return tempTree[key] != null
    }

    override fun addChannelToUser(userId: Long, channelId: Long) {
        if (!isUserIdExists(userId)) throw IllegalArgumentException("user does not exist")
        val tempTree = getTree(userId)
        val key = IdKey(channelId)
        if (isUserInChannel(userId, channelId)) throw IllegalAccessException("user already exists in channel")
        tempTree.put(key)

        numberOfChannelsOnChange(userId, diff = 1L)
    }

    override fun removeChannelFromUser(userId: Long, channelId: Long) {
        if (!isUserIdExists(userId)) throw IllegalArgumentException("user does not exist")
        val tempTree = getTree(userId)
        val key = IdKey(channelId)
        if (!isUserInChannel(userId, channelId)) throw IllegalAccessException("user does not exists in channel")
        tempTree.delete(key)

        numberOfChannelsOnChange(userId, diff = -1L)
    }

    override fun getAllChannelsOfUser(userId: Long): List<Long> {
        if (!isUserIdExists(userId)) throw IllegalArgumentException("user does not exist")
        val tempTree = getTree(userId)
        return tempTree.keys().map{it.getId()}
    }


    /** USER STATISTICS **/
    override fun getTotalUsers(): Long {
        return statisticsManager.getTotalUsers()
    }

    override fun getLoggedInUsers(): Long {
        return statisticsManager.getLoggedInUsers()
    }


    /** USER COMPLEX STATISTICS **/
    override fun getTop10UsersByChannelsCount(): List<String> {
        val values = mutableListOf<String>()
        val nrUsers = getTotalUsers()
        val nrOutputUsers = if (nrUsers > 10) 10 else nrUsers
        for (k in 1..nrOutputUsers) {
            val kthLarger = nrUsers - k
            val userId = usersByChannelsCountTree.select(kthLarger).getId()
            val userName = getUsernameById(userId)
            values.add(userName)
        }
        return values
    }

    /** PRIVATES **/
    private fun initChannelList(userId: Long) {
        userStorage.setPropertyListToUserId(userId, LIST_PROPERTY, emptyList())
        userStorage.setPropertyLongToUserId(userId, MANAGERS_CONSTS.SIZE_PROPERTY, 0L)
    }
    private fun addNewUserToUserTree(userId: Long, count: Long) {
        val key = CountIdKey(count = count, id = userId)
        usersByChannelsCountTree.put(key)
    }
    private fun updateUserNode(userId: Long, oldCount: Long, newCount: Long) {
        val oldKey = CountIdKey(count = oldCount, id = userId)
        usersByChannelsCountTree.delete(oldKey)
        val newKey = CountIdKey(count = newCount, id = userId)
        usersByChannelsCountTree.put(newKey)
    }
    private fun getTree(userIdKey: Long) : SecureAVLTree<IdKey> {
        return SecureAVLTree(userTreesStorage, ConversionUtils.longToBytes(userIdKey), defaultIdKey)
    }
    private fun numberOfChannelsOnChange(userId: Long, diff: Long) {
        val currentValue = userStorage.getPropertyLongByUserId(userId, MANAGERS_CONSTS.SIZE_PROPERTY)
        val newValue = currentValue!! + diff
        userStorage.setPropertyLongToUserId(userId, MANAGERS_CONSTS.SIZE_PROPERTY, newValue)
        updateUserNode(userId = userId, oldCount = currentValue, newCount = newValue)
    }
}