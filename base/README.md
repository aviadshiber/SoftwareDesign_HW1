
# CourseApp: Assignment 1

## Authors
* Ron Yitzhak
* Aviad Shiber


## Notes

### Implementation Summary
#### Storage Layer
    -UserStorage, ChannelStorage, TokenStorage
        -Wrappers for read and write operations
        -each username is mapped to unique user id
        -each channel name is mapped to unique channel id
        -each <property> of user/channel represented by key-value pair in the form: <id>_<propertyName> -> <propertyValue>
    -StatisticsStorage
        -contains information about user's count and channel's count
***
#### Managers layer
    -UserManager: User Manager is the entity that responsible to all the actions related to user
        -generate user id using Generator
        -each user has id, name, status, privilege, and channels list
        -contains avl tree of users, and responsible to update it
            -each user has a representing node in the tree
            -the key of this node is <channels count><user id> (takes care to primary and secondary required order)
    -TokenManager: Token Manager is the entity that responsible to all the actions related to tokens in the system
        -mapping from token to user id
    -ChannelManager: Channel Manager is the entity that responsible to all the actions related to channels in the system
        -generate channel id using Generator
        -each channel has id, name, active users counter and 2 lists: members list and operator list (contains user ids)
        -contains 2 avl trees of channels, and responsible to update them
            -each channel has a representing node in the trees
                -the key of the node in the first tree is <number of users in channel><channel id>
                -the key of the second node is <number of active users in channel><channel id>
    -StatisticsManager(used by User and Channel managers): API to get/set statistics values

*** 

#### App Layer
    -implements all course app logic
    -Responsible to the communication between Users and Channels
    (add/remove channel from user's list, add/remove user from channel's lists)

### Notes
* we used Interfaces as much as possible so the app will be flexible  as much as possible
* we used *Guice* as our Dependency injection framework and we used the special annotations like *@Singleton* on the Manager & *StorageLayer*  since there is no need more than one instance of them, each Storage entity is injected once by using the *SecureStorageFactory* in the *LibraryModule* which provides the different SecureStorages.
* we used the proxy pattern to add *LRU cache* to the *SecureStorage* to optimize performance of immediate actions (like *AVL tree access* and *etc...*)- can be found on *LibraryModule*

### Testing Summary
- Unit testing:
  - All managers
  - AvlTree
  - CourseApp

### Difficulties
- implementing the AVL tree was challenging- we had to think how to serialize the data in a manner that can be read by any Generic key that is given to us, and be able to reverse that serialization (deserialize).  
- Understanding how to inject with annotations using @Provides (the tutorial did not satisfy the real use case of it-(Gal helped us with it)  

### Feedback
Most of the time we spent on implementing and designing the data structure, we think that it is missing the point of the course since we already did it in  data structure course and we hope that in next assignments the focus will be on Design patterns rather than on  data structures.
We wanted to use Observer pattern on Channels & Users (channels need to listen to user login/logout, but since everything need to be persisted -so eventually we gave up on the idea since it requires too much work.
