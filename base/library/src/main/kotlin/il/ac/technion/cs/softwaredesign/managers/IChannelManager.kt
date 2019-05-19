package il.ac.technion.cs.softwaredesign.managers

import java.lang.IllegalArgumentException

interface IChannelManager {
    /**
     * Get channelId that match channelName
     * @param channelName String
     * @return Long, channel id or null if channel does not exist in the system
     */
    fun getChannelId(channelName : String) : Long?

    /**
     * Add new channel to the system
     * @param channelName String
     * @throws IllegalArgumentException if channelName is already exist
     * @return Long, channel id
     */
    fun add(channelName : String) : Long

    /**
     * Remove channel from the system
     * @param channelId Long
     */
    fun remove(channelId : Long)

    /**
     * Check if channelName already exist in the system
     * @param channelName String
     * @return Boolean - true if exist, false if not
     */
    fun isChannelNameExists(channelName : String) : Boolean

    /**
     * Check if channel id already exist in the system
     * @param channelId Long
     * @return Boolean - true if exist, false if not
     */
    fun isChannelIdExists(channelId : Long) : Boolean


    /** PROPERTIES **/
    /**
     * Get channel name
     * @param channelId Long
     * @throws IllegalArgumentException throws if channel id does not exist in the system
     * @return String, channel name
     */
    fun getName(channelId : Long) : String

    /**
     * Get the number of active members in a specific channel
     * @param channelId Long
     * @throws IllegalArgumentException throws if channel id does not exist in the system
     * @return Long, number of active members
     */
    fun getNumberOfActiveMembers(channelId : Long) : Long

    /**
     * Update the number of active members in a specific channel
     * @param channelId Long
     * @throws IllegalArgumentException throws if channel id does not exist in the system
     * @param value Long
     */
    fun updateNumberOfActiveMembers(channelId : Long, value : Long)

    /**
     * Get the number of total members in a specific channel
     * @param channelId Long
     * @throws IllegalArgumentException throws if channel id does not exist in the system
     * @return Long, number of total members
     */
    fun getNumberOfMembers(channelId : Long) : Long

    /**
     * Update the number of members in a specific channel
     * @param channelId Long
     * @throws IllegalArgumentException throws if channel id does not exist in the system
     * @param value Long
     */
    fun updateNumberOfMembers(channelId : Long, value : Long)


    /** MEMBERS LIST **/
    /**
     * gets members list of a specific channel
     * @param channelId channel Id
     * @throws IllegalArgumentException throws if channelId does not exist in the system
     * @return ids of the members of current channel
     */
    fun getMembersList(channelId: Long) : List<Long>

    /**
     * add a member to a specific channel
     * @param channelId channel Id
     * @param memberId member Id
     * @throws IllegalArgumentException throws if channel Id does not exist in the system
     */
    fun addMemberToChannel(channelId:Long, memberId:Long)

    /**
     * removes a member from a specific channel
     * @param channelId channel Id
     * @param memberId member Id
     * @throws IllegalArgumentException throws if channel Id does not exist in the system
     */
    fun removeMemberFromChannel(channelId: Long,memberId: Long)


    /** OPERATORS LIST **/
    /**
     * gets operators list of a specific channel
     * @param channelId channel Id
     * @throws IllegalArgumentException throws if channelId does not exist in the system
     * @return ids of the operators of current channel
     */
    fun getOperatorsList(channelId: Long) : List<Long>

    /**
     * add an operators to a specific channel
     * @param channelId channel Id
     * @param operatorsId operators Id
     * @throws IllegalArgumentException throws if channel Id does not exist in the system
     */
    fun addOperatorToChannel(channelId:Long, operatorsId:Long)

    /**
     * removes an operators from a specific channel
     * @param channelId channel Id
     * @param operatorsId operators Id
     * @throws IllegalArgumentException throws if channel Id does not exist in the system
     */
    fun removeOperatorFromChannel(channelId: Long,operatorsId: Long)
}