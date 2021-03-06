package il.ac.technion.cs.softwaredesign.tests

import com.authzee.kotlinguice4.getInstance
import com.google.inject.Guice
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.present
import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.exceptions.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.time.Duration

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class CourseAppTest{
    private val injector = Guice.createInjector(CourseAppTestModule())
    private val courseApp = injector.getInstance<CourseApp>()
    private val courseAppStatistics = injector.getInstance<CourseAppStatistics>()
    private val courseAppInitializer = injector.getInstance<CourseAppInitializer>()

    init {
        courseAppInitializer.setup()
    }
    @Test
    fun `after login, a user is logged in`() {
        courseApp.login("gal", "hunter2")
        courseApp.login("imaman", "31337")

        val token = courseApp.login("matan", "s3kr1t")

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseApp.isUserLoggedIn(token, "matan") },
                present(isTrue))
    }

    @Test
    fun `throws NoSuchEntityException after login with wrong password`(){
        val username="gal"
        val password="gal_password"
        val galToken1=courseApp.login(username, password)
        courseApp.login("aviad","shiber!$75")
        courseApp.logout(galToken1)

        assertThrows<NoSuchEntityException> {
            runWithTimeout(Duration.ofSeconds(10)) {courseApp.login(username, "wrong_password") }
        }
    }
    @Test
    fun `throws UserAlreadyLoggedInException after re-login`(){
        val username="gal"
        val password="gal_password"
        courseApp.login(username, password)
        courseApp.login("aviad","shiber!$75")
        assertThrows<UserAlreadyLoggedInException> {
            runWithTimeout(Duration.ofSeconds(10)) {courseApp.login(username, password) }
        }
    }

    @Test
    fun `throws InvalidTokenException after logout with invalid token`(){
        val username="aviad"
        val password="aviad_password"
        courseApp.login(username, password)
        val ronToken=courseApp.login("ron", password)
        courseApp.logout(ronToken)
        assertThrows<InvalidTokenException> { courseApp.logout("") }
        assertThrows<InvalidTokenException> { courseApp.logout("bad_token") }
        assertThrows<InvalidTokenException> {
            runWithTimeout(Duration.ofSeconds(10)) {courseApp.logout(ronToken)}
        }
    }

    /**
     * the test checks that after registering to the system we can login again after logout
     * also the test CHECKS with exhaustive search that no assumptions are made
     * regarding the password & username charSet.
     */
    @Test
    fun `login after register`(){
        val printableAsciiRange = ' '..'~'
        for(char in printableAsciiRange){
            val username= "Aviad$char"
            val password=username+"Password"
            val ronToken=courseApp.login(username,password)
            courseApp.logout(ronToken)
            courseApp.login(username,password)
        }
    }

    @Test
    fun `throws InvalidTokenException after checking user login status with invalid token`(){
        assertThrows<InvalidTokenException> {
            runWithTimeout(Duration.ofSeconds(10)) {courseApp.isUserLoggedIn("","notExistingUser")}
        }
        courseApp.login("aviad","aviad_password")
        val username="ron"
        val password="ron_password"
        val ronToken=courseApp.login(username,password)
        assertThrows<InvalidTokenException> {
            runWithTimeout(Duration.ofSeconds(10)) {courseApp.isUserLoggedIn("bad_token","notExistingUser")}
        }
        courseApp.logout(ronToken)
        courseApp.login(username,password)
        assertThrows<InvalidTokenException> {
            runWithTimeout(Duration.ofSeconds(10)) {courseApp.isUserLoggedIn(ronToken,username)}
        }
    }

    @Test
    fun `user login and then logout`(){
        val username="aviad"
        val password="aviad_password"
        val aviadToken= courseApp.login(username, password)
        val adminToken=courseApp.login("admin","123456")
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseApp.isUserLoggedIn(aviadToken, username) },
                present(equalTo(true)))
        courseApp.logout(aviadToken)

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseApp.isUserLoggedIn(adminToken, username) },
                present(equalTo(false)))

    }
    @Test
    fun `returns null if user does not exist`(){
        val token=courseApp.login("aviad","aviad_password")
        val actual =courseApp.isUserLoggedIn(token,"notExsitingUser")
        assertEquals(null, actual,"when user does not exist null expected to be returned")
    }

    @Test
    fun `test regex`() {
        val channelMatch = "#dksnsjfs287342347s7s7s_sdk__#_fdad__#"
        val channelNoMatch = "dksnsjfs287342347s7s7s_sdk__#_fdad__#"
        val channelNoMatch2 = "#@dksnsjfs287342347s7s7s_sdk__#_fdad__#"
        val empty = ""
        assertThat(CourseAppImpl.regex matches channelMatch, isTrue)
        assertThat(CourseAppImpl.regex matches channelNoMatch, isFalse)
        assertThat(CourseAppImpl.regex matches channelNoMatch2, isFalse)
        assertThat(CourseAppImpl.regex matches empty, isFalse)
    }

    @Test
    fun `login exceptions`() {
        courseApp.login("admin", "admin")

        assertThrows<NoSuchEntityException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.login("admin", "wrong_password") }
        }

        assertThrows<UserAlreadyLoggedInException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.login("admin", "admin") }
        }
    }

    @Test
    fun `logout exceptions`() {
        val adminToken = courseApp.login("admin", "admin")
        assertThrows<InvalidTokenException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.logout(adminToken + "b") }
        }
        courseApp.logout(adminToken)
    }

    @Test
    fun `isUserLoggedIn exceptions`() {
        val adminToken = courseApp.login("admin", "admin")
        val userToken = courseApp.login("user", "user_pass")
        assertThrows<InvalidTokenException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.isUserLoggedIn(adminToken+adminToken, userToken) }
        }
    }

    @Test
    fun `test number of valid users`() {
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseAppStatistics.totalUsers()
        },
                equalTo(0L))
        val adminToken = courseApp.login("admin", "admin")
        val userToken = courseApp.login("user", "user_pass")
        courseApp.makeAdministrator(adminToken, "user")
        courseApp.channelJoin(userToken, "#channel")
        courseApp.channelJoin(adminToken, "#channel")

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseAppStatistics.totalUsers()
        },
                equalTo(2L))

        val userToken2 = courseApp.login("user2", "user2_pas")

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseAppStatistics.totalUsers()
        },
                equalTo(3L))

        courseApp.channelJoin(userToken2, "#channel")
        courseApp.logout(userToken2)

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseAppStatistics.totalUsers()
        },
                equalTo(3L))
    }

    @Test
    fun `test number of valid active users`() {
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseAppStatistics.loggedInUsers()
        },
                equalTo(0L))
        val adminToken = courseApp.login("admin", "admin")
        val userToken = courseApp.login("user", "user_pass")
        courseApp.makeAdministrator(adminToken, "user")
        courseApp.channelJoin(userToken, "#channel")
        courseApp.channelJoin(adminToken, "#channel")

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseAppStatistics.loggedInUsers()
        },
                equalTo(2L))

        val userToken2 = courseApp.login("user2", "user2_pas")

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseAppStatistics.loggedInUsers()
        },
                equalTo(3L))

        courseApp.channelJoin(userToken2, "#channel")
        courseApp.logout(userToken2)

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseAppStatistics.loggedInUsers()
        },
                equalTo(2L))

        courseApp.channelPart(adminToken, "#channel")
        courseApp.channelPart(userToken, "#channel")

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseAppStatistics.loggedInUsers()
        },
                equalTo(2L))

        courseApp.logout(userToken)

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseAppStatistics.loggedInUsers()
        },
                equalTo(1L))

        courseApp.logout(adminToken)

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseAppStatistics.loggedInUsers()
        },
                equalTo(0L))
    }

    @Test
    fun `test numberOfActiveUsersInChannel exceptions`() {
        val channel = "#channel"
        val invalidToken = "invalidToken"
        assertThrows<InvalidTokenException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.numberOfActiveUsersInChannel(invalidToken, channel) }
        }
        val adminToken = courseApp.login("admin", "admin")
        assertThrows<NoSuchEntityException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.numberOfActiveUsersInChannel(adminToken, channel) }
        }
        courseApp.channelJoin(adminToken, channel)
        val userToken = courseApp.login("user", "user_pass")
        assertThrows<UserNotAuthorizedException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.numberOfActiveUsersInChannel(userToken, channel) }
        }
        courseApp.channelJoin(userToken, channel)
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseApp.numberOfActiveUsersInChannel(userToken, channel)
        },
                equalTo(2L))
        courseApp.channelPart(adminToken, channel)
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseApp.numberOfActiveUsersInChannel(userToken, channel)
        },
                equalTo(1L))
        courseApp.logout(adminToken)
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseApp.numberOfActiveUsersInChannel(userToken, channel)
        },
                equalTo(1L))

        val userToken2 = courseApp.login("second","pass")
        courseApp.channelJoin(userToken2, channel)
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseApp.numberOfActiveUsersInChannel(userToken, channel)
        },
                equalTo(2L))
        courseApp.logout(userToken)
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseApp.numberOfActiveUsersInChannel(userToken2, channel)
        },
                equalTo(1L))
    }

    @Test
    fun `test numberOfTotalUsersInChannel exceptions`() {
        val channel = "#channel"
        val invalidToken = "invalidToken"
        assertThrows<InvalidTokenException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.numberOfTotalUsersInChannel(invalidToken, channel) }
        }
        val adminToken = courseApp.login("admin", "admin")
        assertThrows<NoSuchEntityException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.numberOfTotalUsersInChannel(adminToken, channel) }
        }
        courseApp.channelJoin(adminToken, channel)
        val userToken = courseApp.login("user", "user_pass")
        assertThrows<UserNotAuthorizedException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.numberOfTotalUsersInChannel(userToken, channel) }
        }
        courseApp.channelJoin(userToken, channel)
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseApp.numberOfTotalUsersInChannel(userToken, channel)
        },
                equalTo(2L))
        courseApp.channelPart(adminToken, channel)
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseApp.numberOfTotalUsersInChannel(userToken, channel)
        },
                equalTo(1L))
        courseApp.logout(adminToken)
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseApp.numberOfTotalUsersInChannel(userToken, channel)
        },
                equalTo(1L))

        val userToken2 = courseApp.login("second","pass")
        courseApp.channelJoin(userToken2, channel)
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseApp.numberOfTotalUsersInChannel(userToken, channel)
        },
                equalTo(2L))
        courseApp.logout(userToken)
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseApp.numberOfTotalUsersInChannel(userToken2, channel)
        },
                equalTo(2L))
    }

    @Test
    fun `isUserInChannel test`(){
        val channel = "#channel"
        val adminToken = courseApp.login("admin", "admin")
        val userToken = courseApp.login("user", "user_pass")
        val userToken2 = courseApp.login("user222", "user_pass222")

        courseApp.channelJoin(adminToken, channel)
        courseApp.channelJoin(userToken, channel)

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseApp.isUserInChannel(userToken, channel, "admin")
        },
                isTrue)

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseApp.isUserInChannel(adminToken, channel, "user")
        },
                isTrue)

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseApp.isUserInChannel(adminToken, channel, "user222")
        },
                isFalse)

        courseApp.channelPart(userToken, channel)

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseApp.isUserInChannel(adminToken, channel, "user")
        },
                isFalse)

        courseApp.channelJoin(userToken2, channel)

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseApp.isUserInChannel(userToken2, channel, "user")
        },
                isFalse)

    }

    @Test
    fun `channelKick exceptions`(){
        val channel = "#channel"
        val invalidToken = "invalidToken"

        assertThrows<InvalidTokenException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.channelKick(invalidToken, channel, "bl") }
        }

        val adminToken = courseApp.login("admin", "admin")

        assertThrows<NoSuchEntityException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.channelKick(adminToken, channel, "bl") }
        }
        val userToken = courseApp.login("user", "user_pass")

        courseApp.channelJoin(adminToken, channel)
        courseApp.channelJoin(userToken, channel)

        assertThrows<UserNotAuthorizedException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.channelKick(userToken, channel, "admin") }
        }

        courseApp.login("bla", "bla")
        assertThrows<NoSuchEntityException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.channelKick(adminToken, channel, "bb") }
        }
        assertThrows<NoSuchEntityException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.channelKick(adminToken, channel, "bla") }
        }
    }

    @Test
    fun channelKickTest() {
        val channel = "#channel"
        val adminToken = courseApp.login("admin", "admin")
        courseApp.channelJoin(adminToken, channel)
        val userToken = courseApp.login("user", "user_pass")
        courseApp.channelJoin(userToken, channel)

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseApp.isUserInChannel(adminToken, channel, "user")
        },
                isTrue)
        courseApp.channelKick(adminToken, channel, "user")

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseApp.isUserInChannel(adminToken, channel, "user")
        },
                isFalse)
        courseApp.channelJoin(userToken, channel)

        assertThrows<UserNotAuthorizedException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.channelKick(userToken, channel, "admin") }
        }

        courseApp.channelMakeOperator(adminToken, channel, "user")
        courseApp.channelKick(userToken, channel, "admin")
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseApp.isUserInChannel(userToken, channel, "admin")
        },
                isFalse)
        val userToken2 = courseApp.login("user222", "user2_pass")
        courseApp.channelJoin(userToken2, channel)
        courseApp.channelKick(userToken, channel, "user")
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            courseApp.isUserInChannel(userToken2, channel, "user")
        },
                isFalse)
    }

    @Test
    fun `channelMakeOperator exceptions`() {
        val channel = "#channel"
        val invalidToken = "token"
        val invalidChannel = "channel"
        assertThrows<InvalidTokenException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.channelMakeOperator(invalidToken, channel, "user") }
        }
        val adminToken = courseApp.login("admin", "admin")
        courseApp.channelJoin(adminToken, channel)
        assertThrows<InvalidTokenException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.channelMakeOperator(invalidToken, channel, "user") }
        }
        assertThrows<NoSuchEntityException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.channelMakeOperator(adminToken, invalidChannel, "admin") }
        }
        val userToken = courseApp.login("user", "user_pass")
        val userToken2 = courseApp.login("user222", "user222_pass")
        courseApp.channelJoin(userToken, channel)
        courseApp.channelJoin(userToken2, channel)
        assertThrows<NoSuchEntityException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.channelMakeOperator(adminToken, invalidChannel, "user") }
        }
        assertThrows<UserNotAuthorizedException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.channelMakeOperator(userToken, channel, "user222") }
        }
        courseApp.makeAdministrator(adminToken, "user")
        assertThrows<UserNotAuthorizedException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.channelMakeOperator(userToken, channel, "user222") }
        }
        assertThrows<UserNotAuthorizedException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.channelMakeOperator(userToken, channel, "b") }
        }
        courseApp.channelMakeOperator(userToken, channel, "user")
        courseApp.channelMakeOperator(userToken, channel, "user222")
        courseApp.login("user22ddd2", "usedddr222_pass")
        assertThrows<NoSuchEntityException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.channelMakeOperator(userToken2, channel, "b") }
        }
        assertThrows<NoSuchEntityException> {
            runWithTimeout(Duration.ofSeconds(10)) { courseApp.channelMakeOperator(userToken2, channel, "user22ddd2") }
        }
    }

    @Test
    fun `user can join a channel and then leave`() {
        val adminToken=courseApp.login("admin","password")
        val aviadToken=courseApp.login("aviad","aviad123")
        val ronToken=courseApp.login("ron","r4123")
        courseApp.channelJoin(adminToken,"#1")
        courseApp.channelJoin(adminToken,"#2")
        courseApp.channelJoin(aviadToken,"#1")
        courseApp.channelJoin(ronToken,"#2")
        courseApp.logout(ronToken)
        //verify that the users joined
        assertThat(courseApp.isUserInChannel(aviadToken,"#1","aviad"), isTrue)
        assertThat(courseApp.isUserInChannel(adminToken,"#2","ron"), isTrue)
        courseApp.channelPart(aviadToken,"#1")
        assertThat(courseApp.isUserInChannel(adminToken,"#1","aviad"), isFalse)
    }

    @Test
    fun `users join a channel, and the channel is destroyed when empty`() {
        val adminToken=courseApp.login("admin","password")
        val aviadToken=courseApp.login("aviad","aviad123")
        val ronToken=courseApp.login("ron","r4123")
        courseApp.channelJoin(adminToken,"#1")
        courseApp.channelJoin(adminToken,"#2")
        courseApp.channelJoin(aviadToken,"#1")
        courseApp.channelJoin(ronToken,"#2")
        courseApp.logout(ronToken)
        //verify that the users joined
        assertThat(courseApp.isUserInChannel(aviadToken,"#1","aviad"), isTrue)
        assertThat(courseApp.isUserInChannel(adminToken,"#2","ron"), isTrue)
        courseApp.channelPart(aviadToken,"#1")
        assertThat(courseApp.isUserInChannel(adminToken,"#1","aviad"), isFalse)
        courseApp.channelPart(adminToken,"#1")
        //channel should have destroyed by now, let's try to re-use his name without getting exception
        courseApp.channelJoin(adminToken,"#1")

    }

    @Test
    fun `channelPart throws InvalidTokenException If the auth token is invalid`() {
        val adminToken=courseApp.login("admin","password")
        val aviadToken=courseApp.login("aviad","aviad123")
        val ronToken=courseApp.login("ron","r4123")
        courseApp.channelJoin(adminToken,"#1")
        courseApp.channelJoin(adminToken,"#2")
        courseApp.channelJoin(aviadToken,"#1")
        courseApp.channelJoin(ronToken,"#2")
        courseApp.logout(ronToken)
        //verify that the users joined
        assertThat(courseApp.isUserInChannel(aviadToken,"#1","aviad"), isTrue)
        assertThat(courseApp.isUserInChannel(adminToken,"#2","ron"), isTrue)
        assertThrowsWithTimeout<Unit, InvalidTokenException>({ courseApp.channelPart("invalidToken","#1")})
    }


    @Test
    fun `channelPart throws NoSuchEntityException If token identifies a user who is not a member of channel, or channel does exist`() {
        val adminToken=courseApp.login("admin","password")
        val aviadToken=courseApp.login("aviad","aviad123")
        val ronToken=courseApp.login("ron","r4123")
        courseApp.channelJoin(adminToken,"#1")
        courseApp.channelJoin(adminToken,"#2")
        courseApp.channelJoin(aviadToken,"#1")
        courseApp.channelJoin(ronToken,"#2")
        courseApp.logout(ronToken)
        //verify that the users joined
        assertThat(courseApp.isUserInChannel(aviadToken,"#1","aviad"), isTrue)
        assertThat(courseApp.isUserInChannel(adminToken,"#2","ron"), isTrue)
        courseApp.channelPart(aviadToken,"#1")
        assertThat(courseApp.isUserInChannel(adminToken,"#1","aviad"), isFalse)
        assertThrowsWithTimeout<Unit, NoSuchEntityException>({ courseApp.channelPart(aviadToken,"#1")})
        assertThrowsWithTimeout<Unit, NoSuchEntityException>({ courseApp.channelPart(aviadToken,"#nonExistingChannel")})

    }

    @Test
    fun channelJoinTest() {
        val adminToken=courseApp.login("admin","password")
        val aviadToken=courseApp.login("aviad","aviad123")
        val ronToken=courseApp.login("ron","r4123")
        courseApp.channelJoin(adminToken,"#1")
        courseApp.channelJoin(aviadToken,"#1")
        courseApp.channelJoin(adminToken,"#2")
        courseApp.channelJoin(ronToken,"#2")
        courseApp.channelJoin(aviadToken,"#2")
        courseApp.logout(ronToken)
        assertThrowsWithTimeout<Unit, InvalidTokenException>({ courseApp.channelJoin(ronToken,"#nonExistingChannel")})
        assertThrowsWithTimeout<Unit, NameFormatException>({ courseApp.channelJoin(adminToken,"123#nonExistingChannel")})
        assertThrowsWithTimeout<Unit, NameFormatException>({ courseApp.channelJoin(adminToken,"badNaming")})
        assertThrowsWithTimeout<Unit, UserNotAuthorizedException>({ courseApp.channelJoin(aviadToken,"#notExistingChannel")})
    }

    @Test
    fun makeAdminTest() {
       val admin= courseApp.login("admin","admin")
        val aviad=courseApp.login("aviad","aviad123")
        val ron=courseApp.login("ron","ron123")
        courseApp.makeAdministrator(admin,"aviad")
        //only admin can create a channel so let's call channel Join with the new admin
        courseApp.channelJoin(aviad,"#1")
        assertThrowsWithTimeout<Unit, InvalidTokenException>({ courseApp.makeAdministrator("INVALIDToken","#1")})
        assertThrowsWithTimeout<Unit, UserNotAuthorizedException>({ courseApp.makeAdministrator(ron,"ron")})
        assertThrowsWithTimeout<Unit, NoSuchEntityException>({ courseApp.makeAdministrator(aviad,"NotExistingUser")})
        //we can even make logout users admins
        courseApp.logout(ron)
        courseApp.makeAdministrator(admin,"ron")
    }

    @Test
    fun `numberOfTotalUsersInChannel get updated after join and part`(){

        val admin= courseApp.login("admin","admin")

        courseApp.channelJoin(admin,"#1")
        assertThat(courseApp.numberOfTotalUsersInChannel(admin,"#1"), equalTo(1L))
        (1..511).forEach{
            val token=courseApp.login("$it","password")
            courseApp.channelJoin(token,"#1")
        }
        assertThat(courseApp.numberOfTotalUsersInChannel(admin,"#1"), equalTo(512L))
        courseApp.channelPart(admin,"#1")
        assertThat(courseApp.numberOfTotalUsersInChannel(admin,"#1"), equalTo(511L))
    }

    @Test
    fun `numberOfActiveUsersInChannel get updated after join and part`(){

        val admin= courseApp.login("admin","admin")

        courseApp.channelJoin(admin,"#1")
        assertThat(runWithTimeout(Duration.ofSeconds(10)) { courseApp.numberOfActiveUsersInChannel(admin, "#1") }, equalTo(1L))
        lateinit var token:String
        (1..511).forEach{
            token=courseApp.login("$it","password")
            courseApp.channelJoin(token,"#1")
        }
        assertThat(runWithTimeout(Duration.ofSeconds(10)) { courseApp.numberOfActiveUsersInChannel(admin, "#1") }, equalTo(512L))
        courseApp.channelPart(admin,"#1")
        courseApp.logout(token)
        assertThat(runWithTimeout(Duration.ofSeconds(10)) { courseApp.numberOfActiveUsersInChannel(admin, "#1") }, equalTo(510L))

    }

    @Test
    fun `making sure cant join operator more than once`() {
        val firstUser = "aviad"
        val aviad = courseApp.login(firstUser, "shiber")
        val secondUser = "ron"
        val ron = courseApp.login(secondUser, "ron")
        val channel = "#SoftwareDesign"
        courseApp.channelJoin(aviad, channel)
        courseApp.channelJoin(ron, channel)
        courseApp.channelMakeOperator(aviad, channel, secondUser)
        courseApp.channelMakeOperator(aviad, channel, secondUser)
        courseApp.channelMakeOperator(ron, channel, secondUser)
        courseApp.channelPart(ron, channel)
        courseApp.channelJoin(ron, channel)
        //an Exception should be thrown if because
        //ron should  not an operator anymore(maybe the test were able to add it twice)
        assertThrows<UserNotAuthorizedException> { courseApp.channelMakeOperator(ron, channel, firstUser) }
    }


    @Test
    fun `top 10 channel by users`() {
        //TODO: impl

    }

    @Test
    fun `get10TopUsersTest primary order only`() {
        val tokens = (0..50).map {Pair(courseApp.login(it.toString(), it.toString()), it.toString())}
        (0..50).forEach {courseApp.makeAdministrator(tokens[0].first, it.toString())}

        val best = tokens.shuffled().take(20)
        (0..0).forEach{ courseApp.channelJoin(best[15].first, "#$it") }
        (0..40).forEach{ courseApp.channelJoin(best[0].first, "#$it") }
        (0..30).forEach{ courseApp.channelJoin(best[3].first, "#$it") }
        (0..40).forEach{ courseApp.channelJoin(best[0].first, "#$it") }
        (0..33).forEach{ courseApp.channelJoin(best[1].first, "#$it") }
        (0..12).forEach{ courseApp.channelJoin(best[13].first, "#$it") }
        (0..31).forEach{ courseApp.channelJoin(best[2].first, "#$it") }
        (0..21).forEach{ courseApp.channelJoin(best[7].first, "#$it") }
        (0..30).forEach{ courseApp.channelJoin(best[3].first, "#$it") }
        (0..25).forEach{ courseApp.channelJoin(best[4].first, "#$it") }
        (0..22).forEach{ courseApp.channelJoin(best[6].first, "#$it") }
        (0..15).forEach{ courseApp.channelJoin(best[11].first, "#$it") }
        (0..21).forEach{ courseApp.channelJoin(best[7].first, "#$it") }
        (0..20).forEach{ courseApp.channelJoin(best[8].first, "#$it") }
        (0..18).forEach{ courseApp.channelJoin(best[9].first, "#$it") }
        (0..16).forEach{ courseApp.channelJoin(best[10].first, "#$it") }
        (0..23).forEach{ courseApp.channelJoin(best[5].first, "#$it") }
        (0..13).forEach{ courseApp.channelJoin(best[12].first, "#$it") }
        (0..8).forEach{ courseApp.channelJoin(best[14].first, "#$it") }
        tokens.forEach {courseApp.logout(it.first)}
        (100..150).forEach {courseApp.login(it.toString(), it.toString())}

        val output = courseAppStatistics.top10UsersByChannels()

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            output
        },
                equalTo(best.take(10).map { it.second }))
    }
}