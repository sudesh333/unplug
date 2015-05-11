package co.uproot.unplug

import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import com.squareup.okhttp.RequestBody
import com.squareup.okhttp.MediaType
import java.io.IOException
import com.eclipsesource.json.JsonObject
import java.net.URLEncoder
import com.eclipsesource.json.JsonArray
import java.util.ArrayList
import java.util.LinkedList


data class AccessToken(val token:String)

data class Message(
  val id: String?,
  val ts: Long,
  val roomId: String?,
  val type: String,
  val userId: String,
  val content: JsonObject)

data class State(
  val type: String,
  val ts: Long,
  val userId: String,
  val content: JsonObject ) {
}

data class Room(val id:String, val aliases:List<String>, val messages: MutableList<Message>, val states : List<State>) {
  fun isChatMessage(message: Message):Boolean {
    return when (message.type) {
      "m.room.create" -> true
      "m.room.member" -> true
      "m.room.message" -> true
      else -> false
    }
  }

  fun chatMessages(): List<Message> {
    return messages.stream().filter({isChatMessage(it)}).toList()
  }
}

fun JsonObject.getObject(name:String):JsonObject {
  return this.get(name).asObject()
}

fun JsonObject.getArray(name:String):JsonArray {
  return this.get(name).asArray()
}

class SyncResult(val rooms: List<Room>, val presence: List<Message>)

class EventResult(val messages: List<Message>, val end: String)

class CreateRoomResult(val roomAlias: String, val roomId: String)

class JoinRoomResult(val roomId: String, val servers: String)

class LeaveRoomResult(val result:String?)

class BanRoomResult(val result:String?)
data class LoginResult(val userId:String,val accessToken: AccessToken, val api: API)

class RoomSyncResult(val al:List<Room>,val presence: List<Message>)

// TODO: Change API to be fully type-safe, and not return JSON objects
class API(val baseURL: String) {
  val apiURL = baseURL + "_matrix/client/api/v1/"
  val apiURL1= baseURL + "/_matrix/client/api/v1/"
  val mediaURL = baseURL + "_matrix/media/v1/"

  private final val client = OkHttpClient()

  init {
    client.setFollowSslRedirects(false)
    client.setFollowRedirects(false)
  }

  private final val net = Net(client)

  fun login(username: String, password: String): LoginResult? {
    try {
      val postBody = """
      {"type":"m.login.password", "user":"$username", "password":"$password"}
      """
      val responseStr = net.doPost(apiURL + "login", postBody)
      val jsonObj = JsonObject.readFrom(responseStr)
      val tokenStr = jsonObj.getString("access_token", null)
      val userId=jsonObj.getString("user_id",null)
      if(tokenStr!=null && userId!=null) {
        return LoginResult(userId, AccessToken(tokenStr), this)
      } else {
        return null
      }
    } catch(e : IOException) {
      e.printStackTrace()
      return null;
    }
  }


 fun createRoom(accessToken: AccessToken, roomname:String,visibility:String):CreateRoomResult{
   val post="""
   {"room_alias_name":"$roomname", "visibility":"$visibility"}
   """
    val responseStr= net.doPost(apiURL + "createRoom?access_token=${accessToken.token}",post)
   println(responseStr)
   val jsonObj = JsonObject.readFrom(responseStr)
   val roomAlias=jsonObj.getString("room_alias",null)
   val roomId=jsonObj.getString("room_id",null)
   return  CreateRoomResult(roomAlias,roomId)
  }

 fun joiningRoon(accessToken:AccessToken,roomName:String):JoinRoomResult{

   val nameEncode=URLEncoder.encode(roomName, "UTF-8")
   val responseStr= net.doPost(apiURL + "join/$nameEncode?access_token=${accessToken.token}","")
   val jsonObj = JsonObject.readFrom(responseStr)
   val roomId=jsonObj.getString("room_id",null)
   val servers=jsonObj.getString("servers",null)
   return JoinRoomResult(roomId,servers)
 }



  fun banningMember(accessToken:AccessToken,roomName:String,memId:String, appState: AppState):BanRoomResult{
    val roomId=getRoomId(accessToken,roomName)
    val ban="ban"
    val roomIdEncode=URLEncoder.encode(roomId, "UTF-8")
    val rmIdEncode=roomIdEncode.substring(3)
    val memIdEncode=URLEncoder.encode(memId, "UTF-8")

    if(roomId!=null) {
    val mediaURL1 = baseURL + "/_matrix/media/v1/"
    val badUrl=mediaURL1+"thumbnail/"


      val url = appState.getRoomUsers(roomId)
      val a = url.firstOrNull { it.id == memId }
      if (a != null) {
        val displayName = a.displayName
        val url1 = a.avatarURL.getValue()
        val url2 = url1.toString().replaceAll(badUrl, "mcx://")
        val finalUrl = url2.substringBefore("?")

        val header = """
      {"avatar_url":"$finalUrl","displayname":"$displayName","membership":"$ban"}
      """
        val responseStr = net.doPut(apiURL1 + "rooms/!$rmIdEncode/state/m.room.member/$memIdEncode?access_token=${accessToken.token}",header)
        println(responseStr)
        return BanRoomResult(responseStr)
      }
    }
    return BanRoomResult(null)
  }

  fun leavingRoom(accessToken:AccessToken,roomName:String):LeaveRoomResult{
    val roomId=getRoomId(accessToken,roomName)
    val roomIdEncode=URLEncoder.encode(roomId, "UTF-8")
    val rmIdEncode=roomIdEncode.substring(3)
    // TODO check with room name
    val responseStr=net.doPost(apiURL + "rooms/!$rmIdEncode/leave?access_token=${accessToken.token}","{}")
    return LeaveRoomResult(null)
  }

  fun getRoomId(accessToken: AccessToken, roomAlias: String): String? {
    val roomAliasEscaped = URLEncoder.encode(roomAlias, "UTF-8")
    val responseStr = net.doGet(apiURL + "directory/room/$roomAliasEscaped?access_token=${accessToken.token}")
    if (responseStr == null) {
      return null
    } else {
      val jsonObj = JsonObject.readFrom(responseStr)
      return jsonObj.getString("room_id", null)
    }
  }

  fun sendMessage(accessToken: AccessToken, roomId: String, message: String): String {
    val postBody = """
      {"msgtype":"m.text", "body":"$message"}
    """

    val responseStr = net.doPost(apiURL + "rooms/$roomId/send/m.room.message?access_token=${accessToken.token}", postBody)
    val jsonObj = JsonObject.readFrom(responseStr)
    val eventId = jsonObj.getString("event_id", null)
    return eventId
  }

  fun roomInitialSync(accessToken: AccessToken,roomId:String):SyncResult? {
    val roomIdEncode=URLEncoder.encode(roomId, "UTF-8")
    val rmIdEncode=roomIdEncode.substring(3)
    val responseStr = net.doGet(apiURL + "rooms/!$rmIdEncode/initialSync?access_token=${accessToken.token}")
    println("respon "+responseStr)
    if (responseStr == null) {
      return null
    }
    val room = JsonObject.readFrom(responseStr)
    //val rooms = jsonObj.getArray("rooms")
    //val roomList = rooms.map {room ->
      val roomObj = room.asObject()
      val messages = roomObj.getObject("messages")
      val chunks = messages.getArray("chunk").map{it.asObject()}
      val messageList = parseChunks(chunks)
      val states = roomObj.getArray("state")
      val aliasStates = states.filter {it.asObject().getString("type", null) == "m.room.aliases" }
      val aliases = aliasStates.flatMap {
        it.asObject().getObject("content").getArray("aliases").map{it.asString()}
      }
      val stateList = states.map { state ->
        val so = state.asObject()
        State(so.getString("type", null), so.getLong("origin_server_ts", 0L), so.getString("user_id", null), so.getObject("content"))
      }
      val aa=ArrayList<Room>()
       val a=Room(roomObj.getString("room_id", null), aliases, messageList.toLinkedList(), stateList)
       aa.add(a)

    val presence = parseChunks(room.getArray("presence").map{it.asObject()})
    return SyncResult(aa,presence)
  }

  fun initialSync(accessToken: AccessToken):SyncResult? {
    val responseStr = net.doGet(apiURL + "initialSync?access_token=${accessToken.token}")
    if (responseStr == null) {
      return null
    }
    val jsonObj = JsonObject.readFrom(responseStr)
    val rooms = jsonObj.getArray("rooms")
    val roomList = rooms.map {room ->
      val roomObj = room.asObject()
      val messages = roomObj.getObject("messages")
      val chunks = messages.getArray("chunk").map{it.asObject()}
      println("ccccccc")
      println(chunks)
      val messageList = parseChunks(chunks)
      println(messageList)
      val states = roomObj.getArray("state")
      val aliasStates = states.filter {it.asObject().getString("type", null) == "m.room.aliases" }
      val aliases = aliasStates.flatMap {
        it.asObject().getObject("content").getArray("aliases").map{it.asString()}
      }
      val stateList = states.map { state ->
        val so = state.asObject()
        State(so.getString("type", null), so.getLong("origin_server_ts", 0L), so.getString("user_id", null), so.getObject("content"))
        }
      Room(roomObj.getString("room_id", null), aliases, messageList.toLinkedList(), stateList)
    }
    val presence = parseChunks(jsonObj.getArray("presence").map{it.asObject()})
    return SyncResult(roomList, presence)
  }

  fun getEvents(accessToken: AccessToken, from: String?):EventResult? {
    val eventURL = apiURL + "events?access_token=${accessToken.token}" + (from?.let{"&from="+it}?: "")
    val responseStr = net.doGet(eventURL)
    val jsonObj = JsonObject.readFrom(responseStr)
    val chunks = jsonObj.getArray("chunk")
    return EventResult(parseChunks(chunks.map{it.asObject()}), jsonObj.getString("end", null))
  }

  private fun parseChunks(chunk: List<JsonObject>): List<Message> {
    val messageList = chunk.map { messageObj ->
      val userId = messageObj.getString("user_id", "")
      val type = messageObj.getString("type", "")
      val eventId:String? = messageObj.getString("event_id", null)
      val roomId:String? = messageObj.getString("room_id", null)
      Message(eventId, messageObj.getLong("origin_server_ts", 0L), roomId, type, userId, messageObj.getObject("content"))
    }
    return messageList
  }

  private val mxcRegex = "^mxc://(.*)/([^#]*)(#auto)?$".toRegex()

  fun getAvatarThumbnailURL(mxcURL: String):String {
    val matcher = mxcRegex.matcher(mxcURL)
    if (matcher.matches()) {
      val serverName = matcher.group(1)
      val mediaId = matcher.group(2)
      return mediaURL + "thumbnail/$serverName/$mediaId?width=24&height=24"
    } else {
      return ""
    }
  }
}

private class Net(val client: OkHttpClient) {
  private final val jsonMediaType = MediaType.parse("application/json;; charset=utf-8")

  // TODO: a non-blank UA String
  private final val uaString = ""

  fun doGet(url:String):String? {
    val request = Request.Builder()
      .url(url)
      .addHeader("User-Agent", uaString)
      .build()

    val response = client.newCall(request).execute()

    if (!response.isSuccessful()) {
      return null
    } else {
      return response.body().string()
    }
  }

  fun doPost(url:String, json:String):String {
    val request = Request.Builder()
      .url(url)
      .addHeader("User-Agent", uaString)
      .post(RequestBody.create(jsonMediaType, json))
      .build()

    val response = client.newCall(request).execute()
    if (!response.isSuccessful()) throw IOException("Unexpected code " + response)
    return response.body().string()
  }

  fun doPut(url:String, json:String):String {
    val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", uaString)
            .put(RequestBody.create(jsonMediaType, json))
            .build()

    val response = client.newCall(request).execute()
    if (!response.isSuccessful()) throw IOException("Unexpected code " + response)
    else
      return response.body().string()
  }
}
