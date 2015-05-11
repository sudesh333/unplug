package co.uproot.unplug

import javafx.concurrent.Service
import javafx.beans.property.SimpleStringProperty
import javafx.concurrent.Task



class LoginService() : Service<LoginResult>() {
  val userName = SimpleStringProperty("")
  val password = SimpleStringProperty("")

  var baseURL:String = ""

  override fun createTask(): Task<LoginResult>? {
    val api = API(baseURL)

    return object : Task<LoginResult>() {
      override fun call(): LoginResult? {
        updateMessage("Logging In")
        val loginResult = api.login(userName.get(), password.get())
        if (loginResult == null) {
          updateMessage("Login Failed")
          failed()
          return null
        } else {
          updateMessage("Logged In Successfully")
          return loginResult
        }
      }
    }
  }
}

class RoomSyncService(val loginResult: LoginResult,val roomId:String) : Service<SyncResult>() {
  val userName = SimpleStringProperty("")
  val password = SimpleStringProperty("")

  override fun createTask(): Task<SyncResult>? {
    val api = loginResult.api
    return object : Task<SyncResult>() {
      override fun call(): SyncResult? {
        updateMessage("Syncing")
        val result = api.roomInitialSync(loginResult.accessToken,roomId)
        println("res1 :"+result)
        if (result == null) {
          updateMessage("Sync Failed")
          failed()
          return null
        } else {
          updateMessage("")
          return result
        }
      }
    }
  }
}


class SyncService(val loginResult: LoginResult) : Service<SyncResult>() {
  val userName = SimpleStringProperty("")
  val password = SimpleStringProperty("")

  override fun createTask(): Task<SyncResult>? {
    val api = loginResult.api
    return object : Task<SyncResult>() {
      override fun call(): SyncResult? {
        updateMessage("Syncing")
        val result = api.initialSync(loginResult.accessToken)
        if (result == null) {
          updateMessage("Sync Failed")
          failed()
          return null
        } else {
          updateMessage("")
          return result
        }
      }
    }
  }
}


class CreateRoomService(val loginResult : LoginResult, val roomname:String,val visibility:String) : Service<CreateRoomResult>() {

  override fun createTask(): Task<CreateRoomResult>? {
    return object : Task<CreateRoomResult>() {
      override fun call(): CreateRoomResult? {
        val createRoomResult = loginResult.api.createRoom(loginResult.accessToken,roomname,visibility)
        if (createRoomResult == null) {
          updateMessage("Failed")
          failed()
          return null
        } else {
          updateMessage("")
          return createRoomResult
        }
      }
    }
  }
}

class JoinRoomService(val loginResult : LoginResult,val roomName:String) : Service<JoinRoomResult>() {

  override fun createTask(): Task<JoinRoomResult>? {
    return object : Task<JoinRoomResult>() {
      override fun call(): JoinRoomResult? {
        val joinResult = loginResult.api.joiningRoon(loginResult.accessToken,roomName)
        if (joinResult == null) {
          updateMessage("Failed")
          failed()
          return null
        } else {
          updateMessage("")
          return joinResult
        }
      }
    }
  }
}

class BanRoomService(val loginResult : LoginResult, val roomname:String,val memId:String, val appState: AppState) : Service<BanRoomResult>() {

  override fun createTask(): Task<BanRoomResult>? {
    return object : Task<BanRoomResult>() {
      override fun call(): BanRoomResult? {
        val banRoomResult = loginResult.api.banningMember(loginResult.accessToken,roomname,memId,appState)
        if (banRoomResult == null) {
          updateMessage("Failed")
          failed()
          return null
        } else {
          updateMessage("")
          return banRoomResult
        }
      }
    }
  }
}

class LeaveRoomService(val loginResult : LoginResult,val roomName:String) : Service<LeaveRoomResult>() {

  override fun createTask(): Task<LeaveRoomResult>? {
    return object : Task<LeaveRoomResult>() {
      override fun call(): LeaveRoomResult? {
        val leaveResult = loginResult.api.leavingRoom(loginResult.accessToken,roomName)
        if (leaveResult == null) {
          updateMessage("Failed")
          failed()
          return null
        } else {
          updateMessage("")
          return leaveResult
        }
      }
    }
  }
}

class EventService(val loginResult : LoginResult) : Service<EventResult>() {
  private var from : String? = null

  override fun createTask(): Task<EventResult>? {
    return object : Task<EventResult>() {
      override fun call(): EventResult? {
        val eventResult = loginResult.api.getEvents(loginResult.accessToken, from)
        if (eventResult == null) {
          updateMessage("Events Failed")
          failed()
          return null
        } else {
          updateMessage("")
          from = eventResult.end
          return eventResult
        }
      }
    }
  }
}

class SendResult(eventId:String)

class SendMessageService(val loginResult : LoginResult, val roomId:String, val msg: String) : Service<SendResult>() {

  override fun createTask(): Task<SendResult>? {
    return object : Task<SendResult>() {
      override fun call(): SendResult? {
        val eventId = loginResult.api.sendMessage(loginResult.accessToken, roomId, msg)
        if (eventId == null) {
          updateMessage("Sending Failed")
          failed()
          return null
        } else {
          updateMessage("")
          return SendResult(eventId)
        }
      }
    }
  }
}
